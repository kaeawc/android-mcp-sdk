# Task 05: HTTP/SSE Transport Implementation

**Status:** `[ ]` Not Started  
**Priority:** High  
**Estimated Time:** 8-10 hours

## Objective

Implement HTTP/SSE (Server-Sent Events) transport layer for the MCP Android server to enable
HTTP-based communication with MCP clients, providing an alternative to WebSocket transport.

## Requirements

- HTTP server implementation using modern Android libraries
- Server-Sent Events (SSE) for server-to-client messaging
- RESTful endpoints for client-to-server communication
- Support for CORS headers for web client compatibility
- Proper HTTP status codes and error handling
- Integration with JSON-RPC message handling
- Concurrent request handling
- Background service operation

## Current State

The README.md mentions HTTP/SSE transport on port 8081, but implementation needs to be completed and
verified.

## Implementation Steps

### 1. Add HTTP Server Dependencies

Update `lib/build.gradle.kts`:

```kotlin
dependencies {
   implementation("io.ktor:ktor-server-core:2.3.5")
   implementation("io.ktor:ktor-server-netty:2.3.5")
   implementation("io.ktor:ktor-server-cors:2.3.5")
   implementation("io.ktor:ktor-server-content-negotiation:2.3.5")
   implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.5")
   implementation("io.ktor:ktor-server-sse:2.3.5")
   implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
}
```

### 2. Create HTTP/SSE Server Infrastructure

Create `lib/src/main/kotlin/dev/jasonpearson/mcpandroidsdk/transport/http/`:

**HttpSseMcpServer.kt:**

```kotlin
class HttpSseMcpServer(
    private val context: Context,
    private val port: Int = 8081,
    private val host: String = "localhost"
) {
    private val mcpServer = McpAndroidServer(context)
    private var ktorServer: ApplicationEngine? = null
    private val sseClients = ConcurrentHashMap<String, SSESession>()
    private var isRunning = false
    
    data class SSESession(
        val id: String,
        val events: SendChannel<ServerSentEvent>,
        val connectedAt: Long = System.currentTimeMillis(),
        val lastActivity: AtomicLong = AtomicLong(System.currentTimeMillis())
    )
    
    suspend fun initializeAndStart(
        serverName: String = "Android MCP Server",
        serverVersion: String = "1.0.0"
    ): Result<Unit> {
        return try {
            // Initialize MCP server
            mcpServer.initialize(serverName, serverVersion).getOrThrow()
            
            // Start HTTP server
            startHttpServer()
            isRunning = true
            
            Log.i(TAG, "HTTP/SSE MCP server started on $host:$port")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start HTTP/SSE server", e)
            Result.failure(e)
        }
    }
    
    private suspend fun startHttpServer() {
        ktorServer = embeddedServer(Netty, port = port, host = host) {
            install(CORS) {
                allowMethod(HttpMethod.Get)
                allowMethod(HttpMethod.Post)
                allowMethod(HttpMethod.Options)
                allowHeader(HttpHeaders.ContentType)
                allowHeader(HttpHeaders.Authorization)
                allowHeader("Cache-Control")
                allowOriginOnPort("localhost", 3000, 8080, 8081) // Common dev ports
                allowCredentials = true
            }
            
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                })
            }
            
            install(SSE)
            
            routing {
                // Health check endpoint
                get("/mcp/status") {
                    call.respond(HttpStatusCode.OK, getServerStatus())
                }
                
                // MCP message endpoint
                post("/mcp/message") {
                    handleMcpMessage(call)
                }
                
                // Server-Sent Events endpoint
                sse("/mcp/events") {
                    handleSSEConnection(this)
                }
                
                // WebSocket upgrade endpoint (for compatibility)
                webSocket("/mcp/ws") {
                    handleWebSocketUpgrade(this)
                }
                
                // API documentation endpoint
                get("/mcp/docs") {
                    call.respondText(getApiDocumentation(), ContentType.Text.Html)
                }
            }
        }
        
        ktorServer?.start(wait = false)
    }
    
    private suspend fun handleMcpMessage(call: ApplicationCall) {
        try {
            val requestBody = call.receiveText()
            Log.d(TAG, "Received MCP message: $requestBody")
            
            // Process the message through MCP server
            val response = mcpServer.processMessage(requestBody)
            
            if (response.isNotEmpty()) {
                call.respondText(
                    response,
                    ContentType.Application.Json,
                    HttpStatusCode.OK
                )
            } else {
                // No response needed (notification)
                call.respond(HttpStatusCode.NoContent)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error processing MCP message", e)
            call.respond(
                HttpStatusCode.BadRequest,
                mapOf("error" to "Invalid request: ${e.message}")
            )
        }
    }
    
    private suspend fun handleSSEConnection(sseSession: SSESession) {
        val clientId = UUID.randomUUID().toString()
        val session = SSESession(
            id = clientId,
            events = sseSession.outgoing
        )
        
        sseClients[clientId] = session
        
        Log.i(TAG, "SSE client connected: $clientId")
        
        try {
            // Send welcome event
            sseSession.send(ServerSentEvent(
                data = Json.encodeToString(mapOf(
                    "type" to "welcome",
                    "clientId" to clientId,
                    "timestamp" to System.currentTimeMillis()
                )),
                event = "welcome",
                id = UUID.randomUUID().toString()
            ))
            
            // Keep connection alive and handle disconnection
            sseSession.incoming.consumeEach { frame ->
                // Handle any incoming SSE data if needed
                Log.d(TAG, "SSE frame received: $frame")
            }
            
        } catch (e: Exception) {
            Log.w(TAG, "SSE connection error for client $clientId", e)
        } finally {
            sseClients.remove(clientId)
            Log.i(TAG, "SSE client disconnected: $clientId")
        }
    }
    
    private suspend fun handleWebSocketUpgrade(webSocketSession: DefaultWebSocketSession) {
        // Optional: Handle WebSocket connections for clients that prefer WS over SSE
        try {
            for (frame in webSocketSession.incoming) {
                when (frame) {
                    is Frame.Text -> {
                        val message = frame.readText()
                        val response = mcpServer.processMessage(message)
                        if (response.isNotEmpty()) {
                            webSocketSession.send(Frame.Text(response))
                        }
                    }
                    else -> { /* Handle other frame types */ }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "WebSocket error", e)
        }
    }
    
    fun broadcastSSEEvent(event: String, data: Any, eventType: String = "notification") {
        val eventData = ServerSentEvent(
            data = Json.encodeToString(data),
            event = eventType,
            id = UUID.randomUUID().toString()
        )
        
        sseClients.values.forEach { session ->
            try {
                session.events.trySend(eventData)
                session.lastActivity.set(System.currentTimeMillis())
            } catch (e: Exception) {
                Log.w(TAG, "Failed to send SSE event to client ${session.id}", e)
            }
        }
    }
    
    fun sendSSEEventToClient(clientId: String, event: String, data: Any, eventType: String = "notification"): Boolean {
        val session = sseClients[clientId]
        return if (session != null) {
            try {
                val eventData = ServerSentEvent(
                    data = Json.encodeToString(data),
                    event = eventType,
                    id = UUID.randomUUID().toString()
                )
                session.events.trySend(eventData)
                session.lastActivity.set(System.currentTimeMillis())
                true
            } catch (e: Exception) {
                Log.w(TAG, "Failed to send SSE event to client $clientId", e)
                false
            }
        } else {
            false
        }
    }
    
    private fun getServerStatus(): HttpServerStatus {
        return HttpServerStatus(
            isRunning = isRunning,
            port = port,
            host = host,
            connectedSSEClients = sseClients.size,
            uptime = if (isRunning) System.currentTimeMillis() - startTime else 0,
            endpoints = listOf(
                "/mcp/status",
                "/mcp/message",
                "/mcp/events",
                "/mcp/ws",
                "/mcp/docs"
            )
        )
    }
    
    private fun getApiDocumentation(): String {
        return """
        <!DOCTYPE html>
        <html>
        <head>
            <title>Android MCP Server API</title>
            <style>
                body { font-family: Arial, sans-serif; margin: 40px; }
                .endpoint { margin: 20px 0; padding: 15px; border: 1px solid #ccc; }
                .method { font-weight: bold; color: #0066cc; }
                .path { font-family: monospace; background: #f5f5f5; padding: 2px 5px; }
                code { background: #f5f5f5; padding: 2px 5px; }
            </style>
        </head>
        <body>
            <h1>Android MCP Server HTTP/SSE API</h1>
            
            <div class="endpoint">
                <div class="method">GET</div>
                <div class="path">/mcp/status</div>
                <p>Get server status and health information</p>
            </div>
            
            <div class="endpoint">
                <div class="method">POST</div>
                <div class="path">/mcp/message</div>
                <p>Send MCP JSON-RPC messages to the server</p>
                <code>Content-Type: application/json</code>
            </div>
            
            <div class="endpoint">
                <div class="method">GET</div>
                <div class="path">/mcp/events</div>
                <p>Connect to Server-Sent Events stream for real-time updates</p>
                <code>Accept: text/event-stream</code>
            </div>
            
            <div class="endpoint">
                <div class="method">GET</div>
                <div class="path">/mcp/ws</div>
                <p>WebSocket endpoint for real-time bidirectional communication</p>
            </div>
            
            <h2>Example Usage</h2>
            <pre>
# Send MCP request
curl -X POST http://localhost:$port/mcp/message \\
  -H "Content-Type: application/json" \\
  -d '{"jsonrpc":"2.0","id":"1","method":"tools/list"}'

# Listen to events
curl -N http://localhost:$port/mcp/events
            </pre>
        </body>
        </html>
        """.trimIndent()
    }
    
    suspend fun shutdown() {
        try {
            isRunning = false
            
            // Notify SSE clients
            broadcastSSEEvent(
                "shutdown",
                mapOf("message" to "Server is shutting down"),
                "system"
            )
            
            // Close SSE connections
            sseClients.values.forEach { session ->
                session.events.close()
            }
            sseClients.clear()
            
            // Stop HTTP server
            ktorServer?.stop(1000, 5000)
            
            Log.i(TAG, "HTTP/SSE server shut down")
        } catch (e: Exception) {
            Log.e(TAG, "Error during shutdown", e)
        }
    }
    
    fun getConnectedSSEClients(): List<SSESession> {
        return sseClients.values.toList()
    }
    
    companion object {
        private const val TAG = "HttpSseMcpServer"
        private var startTime = 0L
    }
}

@Serializable
data class HttpServerStatus(
    val isRunning: Boolean,
    val port: Int,
    val host: String,
    val connectedSSEClients: Int,
    val uptime: Long,
    val endpoints: List<String>
)
```

### 3. Create HTTP Transport Manager

**HttpSseTransportManager.kt:**

```kotlin
class HttpSseTransportManager(
    private val context: Context
) {
    private var httpServer: HttpSseMcpServer? = null
    private val _connectionState = MutableStateFlow(TransportState.STOPPED)
    val connectionState: StateFlow<TransportState> = _connectionState.asStateFlow()
    
    enum class TransportState {
        STOPPED, STARTING, RUNNING, STOPPING, ERROR
    }
    
    suspend fun startServer(
        port: Int = 8081,
        host: String = "localhost",
        serverName: String = "Android MCP Server",
        serverVersion: String = "1.0.0"
    ): Result<HttpServerStatus> {
        return try {
            if (httpServer != null) {
                return Result.failure(IllegalStateException("Server already running"))
            }
            
            _connectionState.value = TransportState.STARTING
            
            httpServer = HttpSseMcpServer(context, port, host)
            httpServer!!.initializeAndStart(serverName, serverVersion).getOrThrow()
            
            _connectionState.value = TransportState.RUNNING
            
            Result.success(httpServer!!.getServerStatus())
        } catch (e: Exception) {
            _connectionState.value = TransportState.ERROR
            Log.e(TAG, "Failed to start HTTP/SSE server", e)
            Result.failure(e)
        }
    }
    
    suspend fun stopServer(): Result<Unit> {
        return try {
            _connectionState.value = TransportState.STOPPING
            
            httpServer?.shutdown()
            httpServer = null
            
            _connectionState.value = TransportState.STOPPED
            
            Result.success(Unit)
        } catch (e: Exception) {
            _connectionState.value = TransportState.ERROR
            Result.failure(e)
        }
    }
    
    private fun HttpSseMcpServer.getServerStatus(): HttpServerStatus {
        return HttpServerStatus(
            isRunning = true,
            port = 8081,
            host = "localhost",
            connectedSSEClients = getConnectedSSEClients().size,
            uptime = System.currentTimeMillis(),
            endpoints = listOf("/mcp/status", "/mcp/message", "/mcp/events")
        )
    }
    
    fun getServerInfo(): HttpServerStatus? {
        return httpServer?.getServerStatus()
    }
    
    fun getConnectedClients(): List<HttpSseMcpServer.SSESession> {
        return httpServer?.getConnectedSSEClients() ?: emptyList()
    }
    
    fun broadcastEvent(event: String, data: Any, eventType: String = "notification") {
        httpServer?.broadcastSSEEvent(event, data, eventType)
    }
    
    fun sendEventToClient(clientId: String, event: String, data: Any, eventType: String = "notification"): Boolean {
        return httpServer?.sendSSEEventToClient(clientId, event, data, eventType) ?: false
    }
    
    fun isRunning(): Boolean {
        return connectionState.value == TransportState.RUNNING
    }
    
    companion object {
        private const val TAG = "HttpSseTransportManager"
    }
}
```

### 4. Create HTTP Test Client

**HttpSseMcpClient.kt:**

```kotlin
class HttpSseMcpClient(
    private val baseUrl: String = "http://localhost:8081"
) {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()
    
    private var sseEventSource: EventSource? = null
    private val _sseEvents = MutableSharedFlow<SSEEvent>()
    val sseEvents: SharedFlow<SSEEvent> = _sseEvents.asSharedFlow()
    
    data class SSEEvent(
        val event: String?,
        val data: String,
        val id: String?
    )
    
    suspend fun sendMcpRequest(request: String): Result<String> {
        return try {
            val requestBody = request.toRequestBody("application/json".toMediaType())
            val httpRequest = Request.Builder()
                .url("$baseUrl/mcp/message")
                .post(requestBody)
                .build()
            
            val response = httpClient.newCall(httpRequest).execute()
            
            if (response.isSuccessful) {
                val responseBody = response.body?.string() ?: ""
                Result.success(responseBody)
            } else {
                Result.failure(Exception("HTTP ${response.code}: ${response.message}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun sendMcpMethod(
        method: String,
        params: Any? = null,
        id: String = UUID.randomUUID().toString()
    ): Result<String> {
        val request = JsonRpcTestUtils.createTestRequest(method, params, id)
        return sendMcpRequest(request)
    }
    
    fun connectToSSE(): Result<Unit> {
        return try {
            val request = Request.Builder()
                .url("$baseUrl/mcp/events")
                .header("Accept", "text/event-stream")
                .header("Cache-Control", "no-cache")
                .build()
            
            sseEventSource = EventSource.Factory(httpClient)
                .newEventSource(request, object : EventSourceListener() {
                    override fun onOpen(eventSource: EventSource, response: Response) {
                        Log.i(TAG, "SSE connection opened")
                    }
                    
                    override fun onEvent(
                        eventSource: EventSource,
                        id: String?,
                        type: String?,
                        data: String
                    ) {
                        _sseEvents.tryEmit(SSEEvent(type, data, id))
                        Log.d(TAG, "SSE event received: type=$type, data=$data")
                    }
                    
                    override fun onClosed(eventSource: EventSource) {
                        Log.i(TAG, "SSE connection closed")
                    }
                    
                    override fun onFailure(
                        eventSource: EventSource,
                        t: Throwable?,
                        response: Response?
                    ) {
                        Log.e(TAG, "SSE connection failed", t)
                    }
                })
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    fun disconnectSSE() {
        sseEventSource?.cancel()
        sseEventSource = null
    }
    
    suspend fun getServerStatus(): Result<HttpServerStatus> {
        return try {
            val request = Request.Builder()
                .url("$baseUrl/mcp/status")
                .get()
                .build()
            
            val response = httpClient.newCall(request).execute()
            
            if (response.isSuccessful) {
                val responseBody = response.body?.string() ?: ""
                val status = Json.decodeFromString<HttpServerStatus>(responseBody)
                Result.success(status)
            } else {
                Result.failure(Exception("HTTP ${response.code}: ${response.message}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    companion object {
        private const val TAG = "HttpSseMcpClient"
    }
}
```

### 5. Integrate with MCP Server Manager

Update `McpServerManager.kt`:

```kotlin
class McpServerManager private constructor() {
    private var httpSseTransport: HttpSseTransportManager? = null
    
    fun initializeHttpSseTransport(context: Context): HttpSseTransportManager {
        if (httpSseTransport == null) {
            httpSseTransport = HttpSseTransportManager(context)
        }
        return httpSseTransport!!
    }
    
    suspend fun startHttpSseServer(
        port: Int = 8081,
        host: String = "localhost"
    ): Result<HttpServerStatus> {
        return httpSseTransport?.startServer(
            port = port,
            host = host,
            serverName = getServerName(),
            serverVersion = getServerVersion()
        ) ?: Result.failure(IllegalStateException("HTTP/SSE transport not initialized"))
    }
    
    suspend fun stopHttpSseServer(): Result<Unit> {
        return httpSseTransport?.stopServer() ?: Result.success(Unit)
    }
    
    fun getHttpSseServerInfo(): HttpServerStatus? {
        return httpSseTransport?.getServerInfo()
    }
    
    fun isHttpSseServerRunning(): Boolean {
        return httpSseTransport?.isRunning() ?: false
    }
    
    fun broadcastHttpSseEvent(event: String, data: Any, eventType: String = "notification") {
        httpSseTransport?.broadcastEvent(event, data, eventType)
    }
    
    // Enhanced startup with both transports
    suspend fun startServerWithAllTransports(
        enableWebSocket: Boolean = true,
        enableHttpSse: Boolean = true,
        webSocketPort: Int = 8080,
        httpSsePort: Int = 8081
    ): Result<Unit> {
        return try {
            // Start core server
            startServer().getOrThrow()
            
            // Start transports
            if (enableWebSocket) {
                initializeWebSocketTransport(context)
                startWebSocketServer(port = webSocketPort).getOrThrow()
            }
            
            if (enableHttpSse) {
                initializeHttpSseTransport(context)
                startHttpSseServer(port = httpSsePort).getOrThrow()
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
```

### 6. Add CORS Support for Web Clients

Create `CorsConfiguration.kt`:

```kotlin
object CorsConfiguration {
    fun configureForDevelopment(): CORSConfig.() -> Unit = {
        // Allow common development origins
        allowHost("localhost", schemes = listOf("http", "https"))
        allowHost("127.0.0.1", schemes = listOf("http", "https"))
        allowHost("0.0.0.0", schemes = listOf("http", "https"))
        
        // Allow common ports
        allowOriginOnPort("localhost", 3000, 8080, 8081, 3001, 5173)
        
        // Allow all HTTP methods
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Head)
        
        // Allow common headers
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Accept)
        allowHeader(HttpHeaders.Origin)
        allowHeader(HttpHeaders.UserAgent)
        allowHeader("X-Requested-With")
        allowHeader("Cache-Control")
        
        // Allow credentials for authenticated requests
        allowCredentials = true
        
        // Cache preflight requests
        maxAgeInSeconds = 86400 // 24 hours
    }
    
    fun configureForProduction(allowedOrigins: List<String>): CORSConfig.() -> Unit = {
        // Only allow specified origins in production
        allowedOrigins.forEach { origin ->
            allowOrigin(origin)
        }
        
        // Restrict methods to what's needed
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Options)
        
        // Restrict headers
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        
        allowCredentials = false
        maxAgeInSeconds = 3600 // 1 hour
    }
}
```

### 7. Add Rate Limiting and Security

Create `HttpSecurityMiddleware.kt`:

```kotlin
class HttpSecurityMiddleware {
    private val requestCounts = ConcurrentHashMap<String, AtomicInteger>()
    private val requestTimes = ConcurrentHashMap<String, Long>()
    private val maxRequestsPerMinute = 60
    
    fun checkRateLimit(clientIp: String): Boolean {
        val currentTime = System.currentTimeMillis()
        val windowStart = currentTime - 60000 // 1 minute window
        
        // Clean old entries
        requestTimes.entries.removeAll { it.value < windowStart }
        requestCounts.keys.removeAll { !requestTimes.containsKey(it) }
        
        val count = requestCounts.computeIfAbsent(clientIp) { AtomicInteger(0) }
        requestTimes[clientIp] = currentTime
        
        return count.incrementAndGet() <= maxRequestsPerMinute
    }
    
    fun validateRequest(call: ApplicationCall): Boolean {
        val clientIp = call.request.header("X-Forwarded-For")
            ?: call.request.header("X-Real-IP")
            ?: call.request.origin.remoteHost
        
        if (!checkRateLimit(clientIp)) {
            Log.w(TAG, "Rate limit exceeded for client: $clientIp")
            return false
        }
        
        return true
    }
    
    companion object {
        private const val TAG = "HttpSecurityMiddleware"
    }
}
```

## Verification Steps

### Unit Tests

1. **HTTP Server Tests**
   ```kotlin
   @Test
   fun `HTTP server handles MCP requests correctly`() = runTest {
       val manager = HttpSseTransportManager(context)
       manager.startServer(port = 9999).getOrThrow()
       
       val client = HttpSseMcpClient("http://localhost:9999")
       val response = client.sendMcpMethod("tools/list")
       
       assertTrue(response.isSuccess)
       assertTrue(response.getOrNull()?.contains("tools") == true)
       
       manager.stopServer()
   }
   ```

2. **SSE Connection Tests**
   ```kotlin
   @Test
   fun `SSE connection receives events correctly`() = runTest {
       val manager = HttpSseTransportManager(context)
       manager.startServer(port = 9998).getOrThrow()
       
       val client = HttpSseMcpClient("http://localhost:9998")
       client.connectToSSE().getOrThrow()
       
       // Send test event
       manager.broadcastEvent("test", mapOf("message" to "hello"))
       
       // Verify event received
       val event = client.sseEvents.first()
       assertEquals("hello", Json.parseToJsonElement(event.data).jsonObject["message"]?.jsonPrimitive?.content)
       
       client.disconnectSSE()
       manager.stopServer()
   }
   ```

3. **CORS Tests**
   ```kotlin
   @Test
   fun `CORS headers are properly set`() = runTest {
       val manager = HttpSseTransportManager(context)
       manager.startServer(port = 9997).getOrThrow()
       
       val client = OkHttpClient()
       val request = Request.Builder()
           .url("http://localhost:9997/mcp/status")
           .header("Origin", "http://localhost:3000")
           .build()
       
       val response = client.newCall(request).execute()
       
       assertTrue(response.header("Access-Control-Allow-Origin") != null)
       
       manager.stopServer()
   }
   ```

### Integration Tests

1. **End-to-End HTTP MCP Flow**
   ```kotlin
   @Test
   fun `complete MCP workflow over HTTP`() = runTest {
       // Start server with HTTP transport
       val manager = McpServerManager.getInstance()
       manager.initialize(context)
       manager.startServerWithAllTransports(
           enableWebSocket = false,
           enableHttpSse = true,
           httpSsePort = 9996
       )
       
       val client = HttpSseMcpClient("http://localhost:9996")
       
       // Test various MCP methods
       val toolsResponse = client.sendMcpMethod("tools/list")
       assertTrue(toolsResponse.isSuccess)
       
       val deviceInfoResponse = client.sendMcpMethod("tools/call", 
           mapOf("name" to "device_info"))
       assertTrue(deviceInfoResponse.isSuccess)
       
       manager.stopHttpSseServer()
   }
   ```

### Manual Testing

1. **HTTP Request Testing**
   ```bash
   # Test MCP requests with curl
   curl -X POST http://localhost:8081/mcp/message \
     -H "Content-Type: application/json" \
     -d '{"jsonrpc":"2.0","id":"1","method":"tools/list"}'
   ```

2. **SSE Testing**
   ```bash
   # Connect to SSE stream
   curl -N -H "Accept: text/event-stream" http://localhost:8081/mcp/events
   ```

3. **Web Client Testing**
   ```javascript
   // JavaScript client test
   const eventSource = new EventSource('http://localhost:8081/mcp/events');
   
   eventSource.onmessage = function(event) {
       console.log('Received:', event.data);
   };
   
   fetch('http://localhost:8081/mcp/message', {
       method: 'POST',
       headers: { 'Content-Type': 'application/json' },
       body: JSON.stringify({
           jsonrpc: '2.0',
           id: '1',
           method: 'tools/list'
       })
   }).then(response => response.json())
     .then(data => console.log(data));
   ```

### Verification Commands

```bash
# Run HTTP/SSE tests
./gradlew :lib:testDebugUnitTest --tests "*HttpSse*"

# Test with sample app
./gradlew :sample:assembleDebug
./gradlew :sample:installDebug

# Test HTTP endpoints
adb forward tcp:8081 tcp:8081
curl http://localhost:8081/mcp/status

# Test CORS
curl -H "Origin: http://localhost:3000" http://localhost:8081/mcp/status
```

## Dependencies

- **Task 03**: JSON-RPC Message Parsing - Required for processing HTTP requests
- **Task 01**: Resource Subscription Logic - Enhanced by SSE notifications

## Resources

### HTTP/SSE Specifications
- [Server-Sent Events Specification](https://html.spec.whatwg.org/multipage/server-sent-events.html)
- [HTTP/1.1 Specification](https://tools.ietf.org/html/rfc7231)
- [CORS Specification](https://www.w3.org/TR/cors/)

### Ktor Documentation

- [Ktor Server Documentation](https://ktor.io/docs/server.html)
- [Ktor SSE Documentation](https://ktor.io/docs/server-sent-events.html)
- [Ktor CORS Documentation](https://ktor.io/docs/cors.html)

### Alternative Libraries

- [NanoHTTPD](https://github.com/NanoHttpd/nanohttpd) - Lightweight HTTP server
- [OkHttp](https://square.github.io/okhttp/) - HTTP client for testing

### MCP Protocol

- [MCP Transport Layer](https://modelcontextprotocol.io/docs/specification/transport)

## Notes

- HTTP/SSE transport provides excellent compatibility with web-based MCP clients
- SSE offers unidirectional real-time communication (server-to-client)
- HTTP POST provides reliable request/response pattern
- CORS configuration is essential for web browser clients
- Consider implementing HTTP/2 support for improved performance
- Rate limiting and security middleware protect against abuse
- Documentation endpoint helps developers understand the API
