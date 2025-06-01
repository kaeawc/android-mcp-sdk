# Task 02: Sampling Requests Implementation

## Status: `[ ]` Not Started

## Priority: Low

## Estimated Time: 6-8 hours

## Objective

Implement the MCP sampling requests functionality to enable the Android MCP server to make sampling requests to connected MCP clients, allowing for AI model interactions and responses.

## Requirements

- Implement client-to-server sampling request handling
- Support model preference specifications
- Handle sampling responses from clients
- Integrate with transport layer for bidirectional communication
- Follow MCP specification for sampling protocol
- Provide async/await API for sampling operations

## Current State

The `AndroidMcpServerImpl.kt` contains a TODO comment at line 240:
```kotlin
// TODO: Implement sampling requests to clients
```

The MCP types already include sampling data structures (`SamplingRequest`, `ModelPreferences`, etc.) but the actual implementation is missing.

## Implementation Steps

### 1. Review MCP Sampling Specification

Study the MCP specification for sampling:
- Request/response format
- Model preference handling
- Error handling for sampling failures
- Timeout and retry logic

### 2. Create Sampling Infrastructure

Create `lib/src/main/kotlin/dev/jasonpearson/mcpandroidsdk/features/sampling/`:

**SamplingManager.kt:**
```kotlin
class SamplingManager(
    private val transportManager: TransportManager,
    private val coroutineScope: CoroutineScope
) {
    private val pendingSamples = ConcurrentHashMap<String, CompletableDeferred<SamplingResponse>>()
    
    suspend fun requestSample(
        request: SamplingRequest,
        timeoutMs: Long = 30000
    ): Result<SamplingResponse> {
        return try {
            val requestId = generateRequestId()
            val deferred = CompletableDeferred<SamplingResponse>()
            
            pendingSamples[requestId] = deferred
            
            // Send request via transport
            val message = createSamplingMessage(requestId, request)
            transportManager.sendMessage(message)
            
            // Wait for response with timeout
            withTimeout(timeoutMs) {
                deferred.await()
            }
            
            Result.success(deferred.getCompleted())
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            pendingSamples.remove(requestId)
        }
    }
    
    fun handleSamplingResponse(requestId: String, response: SamplingResponse) {
        pendingSamples[requestId]?.complete(response)
    }
    
    private fun createSamplingMessage(requestId: String, request: SamplingRequest): String {
        return buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", requestId)
            put("method", "sampling/createMessage")
            put("params", request.toJsonObject())
        }.toString()
    }
}
```

### 3. Add Sampling Response Types

Create `SamplingTypes.kt`:
```kotlin
data class SamplingResponse(
    val model: String,
    val content: List<McpContent>,
    val stopReason: String? = null,
    val usage: TokenUsage? = null
)

data class TokenUsage(
    val inputTokens: Int,
    val outputTokens: Int
)

data class SamplingError(
    val code: Int,
    val message: String,
    val data: JsonElement? = null
)
```

### 4. Integrate with Transport Layer

Update transport implementations to handle bidirectional communication:

**WebSocketTransport.kt:**
```kotlin
class WebSocketTransport {
    private val samplingManager = SamplingManager(this, transportScope)
    
    override fun handleIncomingMessage(message: String) {
        val jsonMessage = Json.parseToJsonElement(message).jsonObject
        
        when (jsonMessage["method"]?.jsonPrimitive?.content) {
            "sampling/response" -> {
                val id = jsonMessage["id"]?.jsonPrimitive?.content
                val response = parseSamplingResponse(jsonMessage["result"])
                if (id != null) {
                    samplingManager.handleSamplingResponse(id, response)
                }
            }
            else -> handleMcpMessage(message)
        }
    }
    
    suspend fun requestSample(request: SamplingRequest): Result<SamplingResponse> {
        return samplingManager.requestSample(request)
    }
}
```

### 5. Add High-Level API

Update `McpServerManager.kt`:
```kotlin
class McpServerManager {
    suspend fun requestModelSample(
        prompt: String,
        modelPreferences: ModelPreferences? = null,
        maxTokens: Int = 1000
    ): Result<SamplingResponse> {
        return try {
            ensureInitialized()
            
            val request = SamplingRequest(
                messages = listOf(
                    PromptMessage(
                        role = MessageRole.USER,
                        content = TextContent(text = prompt)
                    )
                ),
                modelPreferences = modelPreferences,
                maxTokens = maxTokens
            )
            
            transport.requestSample(request)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun requestCodeGeneration(
        codeRequest: String,
        language: String = "kotlin",
        includeTests: Boolean = false
    ): Result<String> {
        val prompt = buildString {
            appendLine("Generate $language code for the following request:")
            appendLine(codeRequest)
            if (includeTests) {
                appendLine("\nAlso include comprehensive unit tests.")
            }
        }
        
        val modelPrefs = ModelPreferences(
            hints = listOf(
                ModelHint(name = "language", value = language),
                ModelHint(name = "task", value = "code_generation")
            )
        )
        
        return requestModelSample(prompt, modelPrefs).map { response ->
            response.content.filterIsInstance<TextContent>()
                .joinToString("\n") { it.text }
        }
    }
}
```

### 6. Add Convenience Methods

Create helper methods for common sampling scenarios:

**SamplingHelpers.kt:**
```kotlin
object SamplingHelpers {
    suspend fun McpServerManager.requestLogAnalysis(
        logContent: String,
        logLevel: String = "ERROR"
    ): Result<String> {
        val prompt = """
            Analyze the following Android log content for issues:
            
            Log Level: $logLevel
            Content:
            $logContent
            
            Please provide:
            1. Identified issues and their severity
            2. Root causes
            3. Recommended solutions
            4. Prevention strategies
        """.trimIndent()
        
        return requestModelSample(prompt).map { response ->
            response.content.filterIsInstance<TextContent>()
                .joinToString("\n") { it.text }
        }
    }
    
    suspend fun McpServerManager.requestCodeReview(
        code: String,
        language: String = "kotlin"
    ): Result<String> {
        val prompt = """
            Please review the following $language code:
            
            ```$language
            $code
            ```
            
            Focus on:
            - Code quality and best practices
            - Potential bugs or issues
            - Performance considerations
            - Security concerns
            - Suggestions for improvement
        """.trimIndent()
        
        return requestModelSample(prompt).map { response ->
            response.content.filterIsInstance<TextContent>()
                .joinToString("\n") { it.text }
        }
    }
}
```

### 7. Error Handling and Retry Logic

Implement robust error handling:

```kotlin
class SamplingManager {
    private val retryPolicy = ExponentialBackoffRetry(
        maxRetries = 3,
        baseDelayMs = 1000,
        maxDelayMs = 10000
    )
    
    suspend fun requestSampleWithRetry(
        request: SamplingRequest,
        timeoutMs: Long = 30000
    ): Result<SamplingResponse> {
        return retryPolicy.execute {
            requestSample(request, timeoutMs)
        }
    }
}
```

## Verification Steps

### 1. Unit Tests

Create `SamplingManagerTest.kt`:
```kotlin
@Test
fun `sampling request creates proper JSON-RPC message`() = runTest {
    val request = SamplingRequest(
        messages = listOf(
            PromptMessage(role = MessageRole.USER, content = TextContent("test"))
        )
    )
    
    // Mock transport and verify message format
    val manager = SamplingManager(mockTransport, testScope)
    // Test message creation and format
}

@Test
fun `sampling response handling completes deferred`() = runTest {
    val manager = SamplingManager(mockTransport, testScope)
    
    // Start sample request
    val requestJob = launch {
        manager.requestSample(testRequest)
    }
    
    // Simulate response
    manager.handleSamplingResponse("test-id", testResponse)
    
    // Verify completion
    assert(requestJob.isCompleted)
}

@Test
fun `sampling timeout properly fails request`() = runTest {
    val manager = SamplingManager(mockTransport, testScope)
    
    val result = manager.requestSample(testRequest, timeoutMs = 100)
    
    assert(result.isFailure)
    assert(result.exceptionOrNull() is TimeoutCancellationException)
}
```

### 2. Integration Tests

```kotlin
@Test
fun `end to end sampling request through manager`() = runTest {
    val manager = McpServerManager.getInstance()
    manager.initialize(context)
    manager.startServer()
    
    // Mock client response
    setupMockClient { request ->
        SamplingResponse(
            model = "test-model",
            content = listOf(TextContent("Generated response")),
            stopReason = "stop"
        )
    }
    
    val result = manager.requestModelSample("Generate a test function")
    
    assert(result.isSuccess)
    assert(result.getOrNull()?.content?.isNotEmpty() == true)
}
```

### 3. Transport Integration Tests

```kotlin
@Test
fun `websocket transport handles sampling bidirectionally`() = runTest {
    val transport = WebSocketTransport(context, testScope)
    
    // Test outgoing sampling request
    val request = SamplingRequest(...)
    transport.requestSample(request)
    
    // Verify WebSocket message sent
    // Simulate incoming response
    // Verify proper response handling
}
```

### 4. Manual Testing

1. **Client Simulation:**
   ```bash
   # Use a test MCP client that supports sampling
   # Connect to the Android server
   # Send sampling requests and verify responses
   ```

2. **Integration with Real Clients:**
   ```bash
   # Test with Claude Desktop or other MCP clients
   # Verify bidirectional sampling works
   # Test error conditions and timeouts
   ```

### 5. Performance Testing

```kotlin
@Test
fun `concurrent sampling requests handled correctly`() = runTest {
    val manager = SamplingManager(mockTransport, testScope)
    
    // Send multiple concurrent requests
    val jobs = (1..10).map { i ->
        async {
            manager.requestSample(createTestRequest(i))
        }
    }
    
    // Verify all complete successfully
    jobs.awaitAll().forEach { result ->
        assert(result.isSuccess)
    }
}
```

## Dependencies

- **03-jsonrpc-message-parsing.md** - Required for proper message handling
- **04-websocket-transport-implementation.md** - Required for bidirectional communication
- **05-http-sse-transport-implementation.md** - Required for HTTP-based sampling

## Resources

- [MCP Sampling Specification](https://modelcontextprotocol.io/specification/server/sampling)
- [Kotlin Coroutines Documentation](https://kotlinlang.org/docs/coroutines-overview.html)
- [Android Network Security](https://developer.android.com/training/articles/security-network)
- [JSON-RPC 2.0 Specification](https://www.jsonrpc.org/specification)

## Acceptance Criteria

- [ ] Sampling requests can be sent to connected clients
- [ ] Sampling responses are properly handled and returned
- [ ] Timeout and retry logic works correctly
- [ ] Model preferences are properly transmitted
- [ ] Concurrent sampling requests are handled safely
- [ ] Error conditions are properly propagated
- [ ] Integration tests pass with mock and real clients
- [ ] Performance is acceptable under concurrent load
- [ ] High-level convenience methods work as expected
- [ ] Transport layer integration is seamless
