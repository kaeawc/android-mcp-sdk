# Task 04: WebSocket Transport Implementation

**Status:** `[ ]` Not Started  
**Priority:** High  
**Estimated Time:** 10-12 hours

## Objective

Implement a complete WebSocket transport layer for the MCP Android server to enable real-time bidirectional communication with MCP clients over WebSocket connections.

## Requirements

- Full WebSocket server implementation using modern Android libraries
- Support for multiple concurrent client connections
- Proper connection lifecycle management
- Integration with JSON-RPC message handling
- Secure WebSocket connections (WSS) support
- Network configuration and port management
- Connection monitoring and health checks
- Graceful shutdown and cleanup

## Current State

The README.md claims WebSocket transport is implemented on port 8080, but verification shows this may be incomplete. The transport layer needs to be fully implemented and tested.

## Implementation Steps

### 1. Add WebSocket Dependencies

Update `core/build.gradle.kts`:
```kotlin
dependencies {
    implementation("org.java-websocket:Java-WebSocket:1.5.4")
    implementation("androidx.lifecycle:lifecycle-service:2.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("androidx.work:work-runtime-ktx:2.9.0")
}
```

### 2. Create WebSocket Server Infrastructure

Create `core/src/main/kotlin/dev/jasonpearson/mcpandroidsdk/transport/websocket/`:

**WebSocketServer.kt:**
```kotlin
class McpWebSocketServer(
    private val port: Int = 8080,
    private val messageHandler: suspend (String) -> String,
    private val coroutineScope: CoroutineScope
) : WebSocketServer(InetSocketAddress(port)) {
    
    private val clients = Collections.synchronizedSet(mutableSetOf<WebSocket>())
    private val clientInfo = ConcurrentHashMap<WebSocket, ClientConnectionInfo>()
    
    companion object {
        private const val TAG = "McpWebSocketServer"
    }
    
    override fun onOpen(conn: WebSocket, handshake: ClientHandshake) {
        clients.add(conn)
        clientInfo[conn] = ClientConnectionInfo(
            connectedAt = System.currentTimeMillis(),
            remoteAddress = conn.remoteSocketAddress?.toString() ?: "unknown"
        )
        
        Log.i(TAG, "WebSocket client connected: ${conn.remoteSocketAddress}")
        
        // Send initial handshake
        coroutineScope.launch {
            try {
                val initMessage = createInitializationMessage()
                conn.send(initMessage)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send initialization message", e)
            }
        }
    }
    
    override fun onClose(conn: WebSocket, code: Int, reason: String, remote: Boolean) {
        clients.remove(conn)
        clientInfo.remove(conn)
        Log.i(TAG, "WebSocket client disconnected: $reason")
    }
    
    override fun onMessage(conn: WebSocket, message: String) {
        coroutineScope.launch {
            try {
                val response = messageHandler(message)
                if (response.isNotEmpty()) {
                    conn.send(response)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to handle WebSocket message", e)
                val errorResponse = createErrorResponse(e)
                conn.send(errorResponse)
            }
        }
    }
    
    override fun onError(conn: WebSocket?, ex: Exception) {
        Log.e(TAG, "WebSocket error", ex)
        conn?.let {
            clients.remove(it)
            clientInfo.remove(it)
        }
    }
    
    override fun onStart() {
        Log.i(TAG, "WebSocket server started on port $port")
    }
    
    fun broadcastMessage(message: String): Result<Int> {
        return try {
            var sentCount = 0
            clients.forEach { client ->
                try {
                    client.send(message)
                    sentCount++
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to send message to client", e)
                }
            }
            Result.success(sentCount)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    fun getConnectionInfo(): WebSocketConnectionInfo {
        return WebSocketConnectionInfo(
            isRunning = !isClosed,
            port = port,
            connectedClients = clients.size,
            clientDetails = clientInfo.values.toList()
        )
    }
    
    private fun createInitializationMessage(): String {
        return """
        {
            "jsonrpc": "2.0",
            "method": "notifications/initialized",
            "params": {
                "serverInfo": {
                    "name": "Android MCP Server",
                    "version": "1.0.0"
                },
                "transport": "websocket"
            }
        }
        """.trimIndent()
    }
    
    private fun createErrorResponse(exception: Exception): String {
        return """
        {
            "jsonrpc": "2.0",
            "id": null,
            "error": {
                "code": -32603,
                "message": "Internal error: ${exception.message}"
            }
        }
        """.trimIndent()
    }
    
    fun shutdown() {
        try {
            clients.forEach { it.close() }
            stop()
        } catch (e: Exception) {
            Log.e(TAG, "Error during WebSocket server shutdown", e)
        }
    }
}

data class ClientConnectionInfo(
    val connectedAt: Long,
    val remoteAddress: String,
    val lastMessageAt: Long = System.currentTimeMillis()
)

data class WebSocketConnectionInfo(
    val isRunning: Boolean,
    val port: Int,
    val connectedClients: Int,
    val clientDetails: List<ClientConnectionInfo>
)
```

### 3. Create WebSocket Transport Manager

**WebSocketTransportManager.kt:**
```kotlin
class WebSocketTransportManager(
    private val context: Context,
    private val messageHandler: suspend (String) -> String,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) : TransportManager {
    
    private var webSocketServer: McpWebSocketServer? = null
    private val _connectionState = MutableStateFlow(TransportState.STOPPED)
    val connectionState: StateFlow<TransportState> = _connectionState.asStateFlow()
    
    companion object {
        private const val TAG = "WebSocketTransportManager"
        private const val DEFAULT_PORT = 8080
    }
    
    override suspend fun start(port: Int): Result<TransportInfo> {
        return try {
            if (_connectionState.value == TransportState.RUNNING) {
                return Result.success(getTransportInfo())
            }
            
            _connectionState.value = TransportState.STARTING
            
            webSocketServer = McpWebSocketServer(
                port = port.takeIf { it > 0 } ?: DEFAULT_PORT,
                messageHandler = messageHandler,
                coroutineScope = coroutineScope
            )
            
            // Start server in background thread
            withContext(Dispatchers.IO) {
                webSocketServer?.start()
            }
            
            // Wait for server to start
            delay(1000) // Give server time to bind to port
            
            if (webSocketServer?.isClosed != false) {
                throw Exception("Failed to start WebSocket server")
            }
            
            _connectionState.value = TransportState.RUNNING
            
            Log.i(TAG, "WebSocket transport started on port ${webSocketServer?.port}")
            
            Result.success(getTransportInfo())
        } catch (e: Exception) {
            _connectionState.value = TransportState.ERROR
            Log.e(TAG, "Failed to start WebSocket transport", e)
            Result.failure(e)
        }
    }
    
    override suspend fun stop(): Result<Unit> {
        return try {
            _connectionState.value = TransportState.STOPPING
            
            webSocketServer?.shutdown()
            webSocketServer = null
            
            _connectionState.value = TransportState.STOPPED
            
            Log.i(TAG, "WebSocket transport stopped")
            Result.success(Unit)
        } catch (e: Exception) {
            _connectionState.value = TransportState.ERROR
            Log.e(TAG, "Failed to stop WebSocket transport", e)
            Result.failure(e)
        }
    }
    
    override suspend fun sendMessage(message: String): Result<Unit> {
        return try {
            val server = webSocketServer ?: throw IllegalStateException("WebSocket server not started")
            server.broadcastMessage(message)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send WebSocket message", e)
            Result.failure(e)
        }
    }
    
    override fun getTransportInfo(): TransportInfo {
        val server = webSocketServer
        return if (server != null && !server.isClosed) {
            val connectionInfo = server.getConnectionInfo()
            TransportInfo(
                type = "websocket",
                endpoint = "ws://localhost:${connectionInfo.port}/mcp",
                isRunning = connectionInfo.isRunning,
                port = connectionInfo.port,
                connectedClients = connectionInfo.connectedClients,
                additionalInfo = mapOf(
                    "clients" to connectionInfo.clientDetails.map { client ->
                        mapOf(
                            "remoteAddress" to client.remoteAddress,
                            "connectedAt" to client.connectedAt,
                            "lastMessageAt" to client.lastMessageAt
                        )
                    }
                )
            )
        } else {
            TransportInfo(
                type = "websocket",
                endpoint = "ws://localhost:$DEFAULT_PORT/mcp",
                isRunning = false,
                port = DEFAULT_PORT,
                connectedClients = 0
            )
        }
    }
    
    override fun isRunning(): Boolean {
        return _connectionState.value == TransportState.RUNNING && 
               webSocketServer?.isClosed == false
    }
}
```

### 4. Create Transport Common Types

**TransportTypes.kt:**
```kotlin
interface TransportManager {
    suspend fun start(port: Int = 0): Result<TransportInfo>
    suspend fun stop(): Result<Unit>
    suspend fun sendMessage(message: String): Result<Unit>
    fun getTransportInfo(): TransportInfo
    fun isRunning(): Boolean
}

enum class TransportState {
    STOPPED,
    STARTING,
    RUNNING,
    STOPPING,
    ERROR
}

data class TransportInfo(
    val type: String,
    val endpoint: String,
    val isRunning: Boolean,
    val port: Int,
    val connectedClients: Int,
    val additionalInfo: Map<String, Any> = emptyMap()
)
```

### 5. Create WebSocket Service for Background Operation

**WebSocketService.kt:**
```kotlin
class WebSocketService : LifecycleService() {
    
    private lateinit var transportManager: WebSocketTransportManager
    private lateinit var notificationManager: NotificationManager
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    companion object {
        private const val TAG = "WebSocketService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "mcp_websocket_channel"
        
        fun start(context: Context) {
            val intent = Intent(context, WebSocketService::class.java)
            context.startForegroundService(intent)
        }
        
        fun stop(context: Context) {
            val intent = Intent(context, WebSocketService::class.java)
            context.stopService(intent)
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        
        createNotificationChannel()
        
        transportManager = WebSocketTransportManager(
            context = this,
            messageHandler = { message -> handleMcpMessage(message) },
            coroutineScope = serviceScope
        )
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        
        startForeground(NOTIFICATION_ID, createNotification())
        
        serviceScope.launch {
            val result = transportManager.start()
            if (result.isFailure) {
                Log.e(TAG, "Failed to start WebSocket transport", result.exceptionOrNull())
                stopSelf()
            }
        }
        
        return START_STICKY
    }
    
    override fun onDestroy() {
        serviceScope.launch {
            transportManager.stop()
        }
        serviceScope.cancel()
        super.onDestroy()
    }
    
    private suspend fun handleMcpMessage(message: String): String {
        return try {
            if (McpStartup.isInitialized()) {
                val server = McpStartup.getManager().getServer()
                server?.handleMessage(message) ?: ""
            } else {
                createErrorResponse("MCP server not initialized")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to handle MCP message", e)
            createErrorResponse(e.message ?: "Unknown error")
        }
    }
    
    private fun createErrorResponse(error: String): String {
        return """
        {
            "jsonrpc": "2.0",
            "id": null,
            "error": {
                "code": -32603,
                "message": "$error"
            }
        }
        """.trimIndent()
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "MCP WebSocket Server",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "MCP WebSocket server background service"
                setShowBadge(false)
            }
            
            notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MCP Server Running")
            .setContentText("WebSocket server active on port 8080")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
```

### 6. Add Permissions and Service Declaration

Update `core/src/main/AndroidManifest.xml`:
```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

<service
    android:name=".transport.websocket.WebSocketService"
    android:enabled="true"
    android:exported="false"
    android:foregroundServiceType="dataSync" />
```

### 7. Integrate with McpServerManager

Update `McpServerManager.kt`:
```kotlin
class McpServerManager {
    private var webSocketTransport: WebSocketTransportManager? = null
    
    suspend fun startWebSocketTransport(port: Int = 8080): Result<TransportInfo> {
        return try {
            ensureInitialized()
            
            if (webSocketTransport == null) {
                webSocketTransport = WebSocketTransportManager(
                    context = context,
                    messageHandler = { message -> 
                        mcpServer?.handleMessage(message) ?: ""
                    }
                )
            }
            
            webSocketTransport!!.start(port)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun stopWebSocketTransport(): Result<Unit> {
        return webSocketTransport?.stop() ?: Result.success(Unit)
    }
    
    fun getWebSocketTransportInfo(): TransportInfo? {
        return webSocketTransport?.getTransportInfo()
    }
    
    suspend fun broadcastWebSocketMessage(message: String): Result<Unit> {
        return webSocketTransport?.sendMessage(message) ?: Result.failure(
            IllegalStateException("WebSocket transport not started")
        )
    }
}
```

## Verification Steps

### 1. Unit Tests

Create `WebSocketServerTest.kt`:
```kotlin
@Test
fun `websocket server starts and binds to port`() = runTest {
    val server = McpWebSocketServer(
        port = 8081,
        messageHandler = { "test response" },
        coroutineScope = testScope
    )
    
    server.start()
    delay(1000)
    
    assert(!server.isClosed)
    assert(server.port == 8081)
    
    server.shutdown()
}

@Test
fun `websocket server handles multiple clients`() = runTest {
    val server = McpWebSocketServer(8082, { "response" }, testScope)
    server.start()
    
    // Connect multiple test clients
    val client1 = TestWebSocketClient("ws://localhost:8082")
    val client2 = TestWebSocketClient("ws://localhost:8082")
    
    client1.connect()
    client2.connect()
    
    delay(1000)
    
    val info = server.getConnectionInfo()
    assertEquals(2, info.connectedClients)
    
    server.shutdown()
}
```

### 2. Integration Tests

```kotlin
@Test
fun `end to end websocket mcp communication`() = runTest {
    val manager = McpServerManager.getInstance()
    manager.initialize(context)
    
    val transportResult = manager.startWebSocketTransport(8083)
    assert(transportResult.isSuccess)
    
    // Connect test client
    val client = TestMcpClient("ws://localhost:8083/mcp")
    client.connect()
    
    // Send MCP request
    val toolsListRequest = """{"jsonrpc":"2.0","id":1,"method":"tools/list"}"""
    val response = client.sendAndWaitForResponse(toolsListRequest)
    
    // Verify response
    val jsonResponse = Json.parseToJsonElement(response).jsonObject
    assert(jsonResponse["result"] != null)
    assert(jsonResponse["error"] == null)
}
```

### 3. Performance Tests

```kotlin
@Test
fun `websocket handles concurrent connections`() = runTest {
    val server = McpWebSocketServer(8084, { "response" }, testScope)
    server.start()
    
    val clients = (1..50).map { TestWebSocketClient("ws://localhost:8084") }
    
    // Connect all clients simultaneously
    clients.forEach { it.connect() }
    delay(2000)
    
    val info = server.getConnectionInfo()
    assertEquals(50, info.connectedClients)
    
    // Send messages from all clients
    clients.forEach { client ->
        repeat(10) {
            client.send("test message $it")
        }
    }
    
    delay(5000)
    
    // Verify server is still responsive
    assert(!server.isClosed)
    
    server.shutdown()
}
```

### 4. Manual Testing

1. **Port Forwarding Test:**
   ```bash
   # Forward port from device to workstation
   adb forward tcp:8080 tcp:8080
   
   # Test connection with wscat or similar
   wscat -c ws://localhost:8080/mcp
   
   # Send MCP request
   {"jsonrpc":"2.0","id":1,"method":"tools/list"}
   ```

2. **Multiple Client Test:**
   ```bash
   # Open multiple WebSocket connections
   # Verify all receive broadcasts
   # Test connection stability
   ```

3. **Network Conditions Test:**
   ```bash
   # Test with poor network conditions
   # Test reconnection scenarios
   # Test message ordering
   ```

### 5. Security Testing

```kotlin
@Test
fun `websocket rejects malformed requests`() = runTest {
    val server = McpWebSocketServer(8085, { "response" }, testScope)
    server.start()
    
    val client = TestWebSocketClient("ws://localhost:8085")
    client.connect()
    
    // Send malformed JSON
    client.send("invalid json")
    
    val response = client.waitForResponse()
    val jsonResponse = Json.parseToJsonElement(response).jsonObject
    
    assert(jsonResponse["error"] != null)
    assertEquals(-32700, jsonResponse["error"]?.jsonObject?.get("code")?.jsonPrimitive?.int)
}
```

## Dependencies

- **03-jsonrpc-message-parsing.md** - Required for proper message handling

## Resources

- [Java-WebSocket Documentation](https://github.com/TooTallNate/Java-WebSocket)
- [Android Foreground Services](https://developer.android.com/guide/components/foreground-services)
- [Android Network Security](https://developer.android.com/training/articles/security-network)
- [WebSocket Protocol RFC 6455](https://tools.ietf.org/html/rfc6455)
- [AndroidX Lifecycle Services](https://developer.android.com/topic/libraries/architecture/lifecycle)

## Acceptance Criteria

- [ ] WebSocket server starts and binds to configurable port
- [ ] Multiple concurrent client connections are supported
- [ ] JSON-RPC messages are properly handled bidirectionally
- [ ] Connection lifecycle is properly managed
- [ ] Background service keeps server running when app is not active
- [ ] Port forwarding with adb works correctly
- [ ] Error handling is robust for network issues
- [ ] Performance is acceptable under load
- [ ] Memory usage is stable with many connections
- [ ] Integration with MCP server manager is seamless
- [ ] Unit and integration tests pass
- [ ] Manual testing scenarios work as expected
