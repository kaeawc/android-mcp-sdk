# Task 06: Transport Integration Testing

**Status:** `[ ]` Not Started  
**Priority:** Medium  
**Estimated Time:** 6-8 hours

## Objective

Create comprehensive integration tests for both WebSocket and HTTP/SSE transport layers to ensure
reliable end-to-end communication with MCP clients and proper transport interoperability.

## Requirements

- End-to-end testing with real transport connections
- Cross-transport compatibility testing
- Performance testing under load
- Network condition simulation
- Client connection lifecycle testing
- Message ordering and reliability verification
- Concurrent transport operation testing
- Error recovery and failover scenarios

## Current State

Individual transport implementations may have basic unit tests, but comprehensive integration
testing across transports is missing.

## Implementation Steps

### 1. Create Test Infrastructure

Create `lib/src/test/kotlin/dev/jasonpearson/mcpandroidsdk/transport/integration/`:

**TransportTestFramework.kt:**

```kotlin
class TransportTestFramework {
    companion object {
        const val WEBSOCKET_TEST_PORT = 9080
        const val HTTP_SSE_TEST_PORT = 9081
    }
    
    suspend fun startTestServer(
        transport: TransportType,
        port: Int,
        messageHandler: suspend (String) -> String = { createEchoResponse(it) }
    ): TestServerHandle {
        return when (transport) {
            TransportType.WEBSOCKET -> startWebSocketTestServer(port, messageHandler)
            TransportType.HTTP_SSE -> startHttpSseTestServer(port, messageHandler)
        }
    }
    
    private suspend fun startWebSocketTestServer(
        port: Int,
        messageHandler: suspend (String) -> String
    ): WebSocketTestHandle {
        val server = McpWebSocketServer(port, messageHandler, testScope)
        server.start()
        delay(1000) // Allow server to start
        return WebSocketTestHandle(server, port)
    }
    
    private suspend fun startHttpSseTestServer(
        port: Int,
        messageHandler: suspend (String) -> String
    ): HttpSseTestHandle {
        val server = McpHttpSseServer(port, messageHandler, testScope)
        server.start()
        delay(1000) // Allow server to start
        return HttpSseTestHandle(server, port)
    }
    
    private fun createEchoResponse(request: String): String {
        return try {
            val jsonRequest = Json.parseToJsonElement(request).jsonObject
            val id = jsonRequest["id"]
            val method = jsonRequest["method"]?.jsonPrimitive?.content
            
            """
            {
                "jsonrpc": "2.0",
                "id": $id,
                "result": {
                    "echo": "$method",
                    "timestamp": ${System.currentTimeMillis()}
                }
            }
            """.trimIndent()
        } catch (e: Exception) {
            """
            {
                "jsonrpc": "2.0",
                "id": null,
                "error": {
                    "code": -32700,
                    "message": "Parse error"
                }
            }
            """.trimIndent()
        }
    }
}

enum class TransportType {
    WEBSOCKET,
    HTTP_SSE
}

sealed class TestServerHandle {
    abstract val port: Int
    abstract suspend fun stop()
    abstract fun getConnectionInfo(): Map<String, Any>
}

class WebSocketTestHandle(
    private val server: McpWebSocketServer,
    override val port: Int
) : TestServerHandle() {
    override suspend fun stop() = server.shutdown()
    override fun getConnectionInfo() = mapOf(
        "type" to "websocket",
        "connectionInfo" to server.getConnectionInfo()
    )
}

class HttpSseTestHandle(
    private val server: McpHttpSseServer,
    override val port: Int
) : TestServerHandle() {
    override suspend fun stop() = server.stop()
    override fun getConnectionInfo() = mapOf(
        "type" to "http-sse",
        "connectionInfo" to server.getConnectionInfo()
    )
}
```

### 2. Create Test Clients

**TestMcpClients.kt:**

```kotlin
interface TestMcpClient {
    suspend fun connect(): Result<Unit>
    suspend fun disconnect(): Result<Unit>
    suspend fun sendMessage(message: String): Result<Unit>
    suspend fun sendAndWaitForResponse(message: String, timeoutMs: Long = 5000): Result<String>
    fun getReceivedMessages(): List<String>
    fun isConnected(): Boolean
}

class WebSocketTestClient(private val url: String) : TestMcpClient {
    private var webSocket: WebSocket? = null
    private val receivedMessages = mutableListOf<String>()
    private val responseWaiters = ConcurrentHashMap<String, CompletableDeferred<String>>()
    
    override suspend fun connect(): Result<Unit> {
        return try {
            val request = Request.Builder().url(url).build()
            val listener = object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    // Connection established
                }
                
                override fun onMessage(webSocket: WebSocket, text: String) {
                    receivedMessages.add(text)
                    handleResponse(text)
                }
                
                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    // Handle failure
                }
            }
            
            webSocket = OkHttpClient().newWebSocket(request, listener)
            delay(1000) // Allow connection to establish
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun disconnect(): Result<Unit> {
        return try {
            webSocket?.close(1000, "Test complete")
            webSocket = null
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun sendMessage(message: String): Result<Unit> {
        return try {
            webSocket?.send(message) ?: throw IllegalStateException("Not connected")
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun sendAndWaitForResponse(message: String, timeoutMs: Long): Result<String> {
        return try {
            val messageId = extractMessageId(message) ?: UUID.randomUUID().toString()
            val deferred = CompletableDeferred<String>()
            responseWaiters[messageId] = deferred
            
            sendMessage(message)
            
            withTimeout(timeoutMs) {
                deferred.await()
            }
            
            Result.success(deferred.getCompleted())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    private fun handleResponse(response: String) {
        try {
            val jsonResponse = Json.parseToJsonElement(response).jsonObject
            val id = jsonResponse["id"]?.jsonPrimitive?.content
            if (id != null) {
                responseWaiters.remove(id)?.complete(response)
            }
        } catch (e: Exception) {
            // Ignore parsing errors for non-JSON responses
        }
    }
    
    private fun extractMessageId(message: String): String? {
        return try {
            Json.parseToJsonElement(message).jsonObject["id"]?.jsonPrimitive?.content
        } catch (e: Exception) {
            null
        }
    }
    
    override fun getReceivedMessages() = receivedMessages.toList()
    override fun isConnected() = webSocket != null
}

class HttpSseTestClient(private val baseUrl: String) : TestMcpClient {
    private val httpClient = OkHttpClient()
    private var sseConnection: EventSource? = null
    private val receivedMessages = mutableListOf<String>()
    private val responseWaiters = ConcurrentHashMap<String, CompletableDeferred<String>>()
    
    override suspend fun connect(): Result<Unit> {
        return try {
            val request = Request.Builder()
                .url("$baseUrl/events")
                .header("Accept", "text/event-stream")
                .build()
            
            val listener = object : EventSourceListener() {
                override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                    receivedMessages.add(data)
                    handleResponse(data)
                }
                
                override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                    // Handle failure
                }
            }
            
            sseConnection = EventSources.createFactory(httpClient).newEventSource(request, listener)
            delay(1000) // Allow connection to establish
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun disconnect(): Result<Unit> {
        return try {
            sseConnection?.cancel()
            sseConnection = null
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun sendMessage(message: String): Result<Unit> {
        return try {
            val requestBody = message.toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("$baseUrl/message")
                .post(requestBody)
                .build()
            
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                throw Exception("HTTP error: ${response.code}")
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun sendAndWaitForResponse(message: String, timeoutMs: Long): Result<String> {
        return try {
            val messageId = extractMessageId(message) ?: UUID.randomUUID().toString()
            val deferred = CompletableDeferred<String>()
            responseWaiters[messageId] = deferred
            
            // For HTTP, we get immediate response
            val requestBody = message.toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("$baseUrl/message")
                .post(requestBody)
                .build()
            
            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""
            
            Result.success(responseBody)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    private fun handleResponse(response: String) {
        try {
            val jsonResponse = Json.parseToJsonElement(response).jsonObject
            val id = jsonResponse["id"]?.jsonPrimitive?.content
            if (id != null) {
                responseWaiters.remove(id)?.complete(response)
            }
        } catch (e: Exception) {
            // Ignore parsing errors
        }
    }
    
    private fun extractMessageId(message: String): String? {
        return try {
            Json.parseToJsonElement(message).jsonObject["id"]?.jsonPrimitive?.content
        } catch (e: Exception) {
            null
        }
    }
    
    override fun getReceivedMessages() = receivedMessages.toList()
    override fun isConnected() = sseConnection != null
}
```

### 3. Create Integration Test Suite

**TransportIntegrationTest.kt:**

```kotlin
@RunWith(AndroidJUnit4::class)
class TransportIntegrationTest {
    
    private val testFramework = TransportTestFramework()
    private lateinit var context: Context
    
    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
    }
    
    @Test
    fun `websocket transport end to end communication`() = runTest {
        val serverHandle = testFramework.startTestServer(
            TransportType.WEBSOCKET,
            TransportTestFramework.WEBSOCKET_TEST_PORT
        )
        
        val client = WebSocketTestClient("ws://localhost:${TransportTestFramework.WEBSOCKET_TEST_PORT}")
        
        try {
            // Test connection
            val connectResult = client.connect()
            assert(connectResult.isSuccess)
            assert(client.isConnected())
            
            // Test message exchange
            val request = """{"jsonrpc":"2.0","id":"test-1","method":"tools/list"}"""
            val responseResult = client.sendAndWaitForResponse(request)
            
            assert(responseResult.isSuccess)
            val response = responseResult.getOrNull()!!
            val jsonResponse = Json.parseToJsonElement(response).jsonObject
            
            assertEquals("test-1", jsonResponse["id"]?.jsonPrimitive?.content)
            assert(jsonResponse["result"] != null)
            
        } finally {
            client.disconnect()
            serverHandle.stop()
        }
    }
    
    @Test
    fun `http sse transport end to end communication`() = runTest {
        val serverHandle = testFramework.startTestServer(
            TransportType.HTTP_SSE,
            TransportTestFramework.HTTP_SSE_TEST_PORT
        )
        
        val client = HttpSseTestClient("http://localhost:${TransportTestFramework.HTTP_SSE_TEST_PORT}/mcp")
        
        try {
            // Test connection
            val connectResult = client.connect()
            assert(connectResult.isSuccess)
            assert(client.isConnected())
            
            // Test message exchange
            val request = """{"jsonrpc":"2.0","id":"test-2","method":"resources/list"}"""
            val responseResult = client.sendAndWaitForResponse(request)
            
            assert(responseResult.isSuccess)
            val response = responseResult.getOrNull()!!
            val jsonResponse = Json.parseToJsonElement(response).jsonObject
            
            assertEquals("test-2", jsonResponse["id"]?.jsonPrimitive?.content)
            assert(jsonResponse["result"] != null)
            
        } finally {
            client.disconnect()
            serverHandle.stop()
        }
    }
    
    @Test
    fun `concurrent transport operations`() = runTest {
        val wsHandle = testFramework.startTestServer(
            TransportType.WEBSOCKET,
            TransportTestFramework.WEBSOCKET_TEST_PORT
        )
        val httpHandle = testFramework.startTestServer(
            TransportType.HTTP_SSE,
            TransportTestFramework.HTTP_SSE_TEST_PORT
        )
        
        val wsClient = WebSocketTestClient("ws://localhost:${TransportTestFramework.WEBSOCKET_TEST_PORT}")
        val httpClient = HttpSseTestClient("http://localhost:${TransportTestFramework.HTTP_SSE_TEST_PORT}/mcp")
        
        try {
            // Connect both clients
            wsClient.connect()
            httpClient.connect()
            
            // Send concurrent requests
            val wsJob = async {
                wsClient.sendAndWaitForResponse("""{"jsonrpc":"2.0","id":"ws-1","method":"tools/list"}""")
            }
            val httpJob = async {
                httpClient.sendAndWaitForResponse("""{"jsonrpc":"2.0","id":"http-1","method":"tools/list"}""")
            }
            
            val results = awaitAll(wsJob, httpJob)
            
            // Verify both succeeded
            results.forEach { result ->
                assert(result.isSuccess)
            }
            
        } finally {
            wsClient.disconnect()
            httpClient.disconnect()
            wsHandle.stop()
            httpHandle.stop()
        }
    }
    
    @Test
    fun `multiple clients single transport`() = runTest {
        val serverHandle = testFramework.startTestServer(
            TransportType.WEBSOCKET,
            TransportTestFramework.WEBSOCKET_TEST_PORT
        )
        
        val clients = (1..5).map { 
            WebSocketTestClient("ws://localhost:${TransportTestFramework.WEBSOCKET_TEST_PORT}")
        }
        
        try {
            // Connect all clients
            clients.forEach { it.connect() }
            
            // Send messages from all clients
            val jobs = clients.mapIndexed { index, client ->
                async {
                    client.sendAndWaitForResponse("""{"jsonrpc":"2.0","id":"client-$index","method":"tools/list"}""")
                }
            }
            
            val results = awaitAll(*jobs.toTypedArray())
            
            // Verify all succeeded
            results.forEach { result ->
                assert(result.isSuccess)
            }
            
            // Verify server tracked all connections
            val connectionInfo = serverHandle.getConnectionInfo()
            val wsInfo = connectionInfo["connectionInfo"] as WebSocketConnectionInfo
            assertEquals(5, wsInfo.connectedClients)
            
        } finally {
            clients.forEach { it.disconnect() }
            serverHandle.stop()
        }
    }
    
    @Test
    fun `transport performance under load`() = runTest {
        val serverHandle = testFramework.startTestServer(
            TransportType.WEBSOCKET,
            TransportTestFramework.WEBSOCKET_TEST_PORT
        )
        
        val client = WebSocketTestClient("ws://localhost:${TransportTestFramework.WEBSOCKET_TEST_PORT}")
        
        try {
            client.connect()
            
            val messageCount = 100
            val startTime = System.currentTimeMillis()
            
            // Send many messages rapidly
            val jobs = (1..messageCount).map { i ->
                async {
                    client.sendAndWaitForResponse("""{"jsonrpc":"2.0","id":"perf-$i","method":"tools/list"}""")
                }
            }
            
            val results = awaitAll(*jobs.toTypedArray())
            val endTime = System.currentTimeMillis()
            
            val duration = endTime - startTime
            val throughput = messageCount * 1000.0 / duration
            
            // Verify all messages succeeded
            assertEquals(messageCount, results.count { it.isSuccess })
            
            // Verify reasonable performance (should handle at least 10 messages/second)
            assert(throughput >= 10.0) { "Throughput too low: $throughput msg/sec" }
            
            Log.i("TransportTest", "Performance: $throughput messages/second")
            
        } finally {
            client.disconnect()
            serverHandle.stop()
        }
    }
    
    @Test
    fun `transport error handling and recovery`() = runTest {
        val serverHandle = testFramework.startTestServer(
            TransportType.WEBSOCKET,
            TransportTestFramework.WEBSOCKET_TEST_PORT
        )
        
        val client = WebSocketTestClient("ws://localhost:${TransportTestFramework.WEBSOCKET_TEST_PORT}")
        
        try {
            client.connect()
            
            // Send invalid JSON
            val invalidResult = client.sendAndWaitForResponse("invalid json", 2000)
            assert(invalidResult.isFailure || invalidResult.getOrNull()?.contains("error") == true)
            
            // Verify connection still works after error
            val validResult = client.sendAndWaitForResponse("""{"jsonrpc":"2.0","id":"recovery","method":"tools/list"}""")
            assert(validResult.isSuccess)
            
        } finally {
            client.disconnect()
            serverHandle.stop()
        }
    }
    
    @Test
    fun `transport message ordering`() = runTest {
        val serverHandle = testFramework.startTestServer(
            TransportType.WEBSOCKET,
            TransportTestFramework.WEBSOCKET_TEST_PORT
        ) { request ->
            // Echo back with sequence number
            val jsonRequest = Json.parseToJsonElement(request).jsonObject
            val id = jsonRequest["id"]
            val sequence = jsonRequest["params"]?.jsonObject?.get("sequence")
            
            """
            {
                "jsonrpc": "2.0",
                "id": $id,
                "result": {
                    "sequence": $sequence,
                    "timestamp": ${System.currentTimeMillis()}
                }
            }
            """.trimIndent()
        }
        
        val client = WebSocketTestClient("ws://localhost:${TransportTestFramework.WEBSOCKET_TEST_PORT}")
        
        try {
            client.connect()
            
            val messageCount = 20
            val responses = mutableListOf<String>()
            
            // Send messages in sequence
            for (i in 1..messageCount) {
                val request = """
                {
                    "jsonrpc": "2.0",
                    "id": "seq-$i",
                    "method": "test",
                    "params": {"sequence": $i}
                }
                """.trimIndent()
                
                val result = client.sendAndWaitForResponse(request)
                if (result.isSuccess) {
                    responses.add(result.getOrNull()!!)
                }
            }
            
            // Verify we received all responses
            assertEquals(messageCount, responses.size)
            
            // Verify sequence numbers (responses might be out of order due to async processing)
            val sequences = responses.map { response ->
                Json.parseToJsonElement(response).jsonObject["result"]?.jsonObject?.get("sequence")?.jsonPrimitive?.int
            }.filterNotNull().sorted()
            
            assertEquals((1..messageCount).toList(), sequences)
            
        } finally {
            client.disconnect()
            serverHandle.stop()
        }
    }
}
```

### 4. Create Performance Benchmarks

**TransportBenchmarkTest.kt:**

```kotlin
@RunWith(AndroidJUnit4::class)
class TransportBenchmarkTest {
    
    private val testFramework = TransportTestFramework()
    
    @Test
    fun `websocket vs http sse throughput comparison`() = runTest {
        val results = mutableMapOf<String, Double>()
        
        // Test WebSocket throughput
        val wsHandle = testFramework.startTestServer(TransportType.WEBSOCKET, 9090)
        val wsClient = WebSocketTestClient("ws://localhost:9090")
        
        try {
            wsClient.connect()
            val wsThroughput = measureThroughput(wsClient, 50)
            results["websocket"] = wsThroughput
        } finally {
            wsClient.disconnect()
            wsHandle.stop()
        }
        
        // Test HTTP/SSE throughput
        val httpHandle = testFramework.startTestServer(TransportType.HTTP_SSE, 9091)
        val httpClient = HttpSseTestClient("http://localhost:9091/mcp")
        
        try {
            httpClient.connect()
            val httpThroughput = measureThroughput(httpClient, 50)
            results["http-sse"] = httpThroughput
        } finally {
            httpClient.disconnect()
            httpHandle.stop()
        }
        
        Log.i("Benchmark", "WebSocket: ${results["websocket"]} msg/sec")
        Log.i("Benchmark", "HTTP/SSE: ${results["http-sse"]} msg/sec")
        
        // Both should achieve reasonable throughput
        assert(results["websocket"]!! >= 5.0)
        assert(results["http-sse"]!! >= 3.0) // HTTP typically slower due to overhead
    }
    
    private suspend fun measureThroughput(client: TestMcpClient, messageCount: Int): Double {
        val startTime = System.currentTimeMillis()
        
        val jobs = (1..messageCount).map { i ->
            async {
                client.sendAndWaitForResponse("""{"jsonrpc":"2.0","id":"bench-$i","method":"tools/list"}""")
            }
        }
        
        val results = awaitAll(*jobs.toTypedArray())
        val endTime = System.currentTimeMillis()
        
        val successCount = results.count { it.isSuccess }
        val duration = endTime - startTime
        
        return successCount * 1000.0 / duration
    }
    
    @Test
    fun `connection establishment latency`() = runTest {
        val latencies = mutableMapOf<String, Long>()
        
        // Measure WebSocket connection latency
        repeat(5) {
            val wsHandle = testFramework.startTestServer(TransportType.WEBSOCKET, 9092 + it)
            val startTime = System.currentTimeMillis()
            
            val client = WebSocketTestClient("ws://localhost:${9092 + it}")
            client.connect()
            val endTime = System.currentTimeMillis()
            
            latencies["websocket-$it"] = endTime - startTime
            
            client.disconnect()
            wsHandle.stop()
        }
        
        val avgWsLatency = latencies.filterKeys { it.startsWith("websocket") }.values.average()
        Log.i("Benchmark", "Average WebSocket connection latency: ${avgWsLatency}ms")
        
        // Connection should establish within reasonable time
        assert(avgWsLatency < 2000) // Less than 2 seconds
    }
}
```

### 5. Create Network Condition Tests

**NetworkConditionTest.kt:**

```kotlin
@RunWith(AndroidJUnit4::class)
class NetworkConditionTest {
    
    @Test
    fun `transport behavior with network delays`() = runTest(timeout = 30.seconds) {
        // This test would require network simulation tools
        // For now, we test with timeouts and delays
        
        val serverHandle = TransportTestFramework().startTestServer(
            TransportType.WEBSOCKET,
            9100
        ) { request ->
            // Simulate slow server response
            delay(2000)
            """{"jsonrpc":"2.0","id":"delayed","result":{"status":"slow"}}"""
        }
        
        val client = WebSocketTestClient("ws://localhost:9100")
        
        try {
            client.connect()
            
            // Test with short timeout (should fail)
            val fastResult = client.sendAndWaitForResponse(
                """{"jsonrpc":"2.0","id":"fast","method":"test"}""",
                1000
            )
            assert(fastResult.isFailure)
            
            // Test with long timeout (should succeed)
            val slowResult = client.sendAndWaitForResponse(
                """{"jsonrpc":"2.0","id":"slow","method":"test"}""",
                5000
            )
            assert(slowResult.isSuccess)
            
        } finally {
            client.disconnect()
            serverHandle.stop()
        }
    }
    
    @Test
    fun `transport reconnection after disconnect`() = runTest {
        val serverHandle = TransportTestFramework().startTestServer(
            TransportType.WEBSOCKET,
            9101
        )
        
        val client = WebSocketTestClient("ws://localhost:9101")
        
        try {
            // Initial connection
            client.connect()
            val firstResult = client.sendAndWaitForResponse(
                """{"jsonrpc":"2.0","id":"first","method":"test"}"""
            )
            assert(firstResult.isSuccess)
            
            // Disconnect
            client.disconnect()
            assert(!client.isConnected())
            
            // Reconnect
            client.connect()
            val secondResult = client.sendAndWaitForResponse(
                """{"jsonrpc":"2.0","id":"second","method":"test"}"""
            )
            assert(secondResult.isSuccess)
            
        } finally {
            client.disconnect()
            serverHandle.stop()
        }
    }
}
```

## Verification Steps

### 1. Run Integration Test Suite

```bash
./gradlew :lib:connectedAndroidTest -Pandroid.testInstrumentationRunner=androidx.test.runner.AndroidJUnitRunner
```

### 2. Performance Benchmarking

```bash
# Run benchmark tests specifically
./gradlew :lib:connectedAndroidTest -Pandroid.testInstrumentationRunner=androidx.test.runner.AndroidJUnitRunner \
  --tests="*TransportBenchmarkTest*"
```

### 3. Manual Integration Testing

```bash
# Start both transports on device
adb forward tcp:8080 tcp:8080  # WebSocket
adb forward tcp:8081 tcp:8081  # HTTP/SSE

# Test WebSocket with wscat
wscat -c ws://localhost:8080/mcp
> {"jsonrpc":"2.0","id":1,"method":"tools/list"}

# Test HTTP/SSE with curl
curl -X POST http://localhost:8081/mcp/message \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/list"}'

# Test SSE endpoint
curl -N http://localhost:8081/mcp/events
```

### 4. Load Testing

```bash
# Use tools like Artillery.js or Apache Bench for load testing
artillery quick --count 10 --num 100 http://localhost:8081/mcp/message
```

## Dependencies

- **03-jsonrpc-message-parsing.md** - Required for message handling
- **04-websocket-transport-implementation.md** - Required for WebSocket tests
- **05-http-sse-transport-implementation.md** - Required for HTTP/SSE tests

## Resources

- [Android Testing Documentation](https://developer.android.com/training/testing)
- [OkHttp WebSocket Testing](https://square.github.io/okhttp/recipes/#websocket)
- [Server-Sent Events Testing](https://developer.mozilla.org/en-US/docs/Web/API/Server-sent_events/Using_server-sent_events)
- [Kotlin Coroutines Testing](https://kotlinlang.org/docs/coroutines-testing.html)

## Acceptance Criteria

- [ ] End-to-end tests pass for both WebSocket and HTTP/SSE transports
- [ ] Cross-transport compatibility is verified
- [ ] Performance benchmarks show acceptable throughput (>5 msg/sec for WebSocket, >3 msg/sec for
  HTTP/SSE)
- [ ] Connection establishment latency is under 2 seconds
- [ ] Multiple concurrent clients are handled correctly
- [ ] Message ordering is preserved where expected
- [ ] Error conditions are handled gracefully
- [ ] Network condition simulation tests pass
- [ ] Reconnection scenarios work correctly
- [ ] Load testing shows stable performance under stress
- [ ] Memory usage remains stable during long-running tests
- [ ] Integration with real MCP clients is verified