# Task 03: JSON-RPC Message Parsing Implementation

**Status:** `[ ]` Not Started  
**Priority:** High  
**Estimated Time:** 8-10 hours

## Objective

Implement comprehensive JSON-RPC 2.0 message parsing and handling to enable proper MCP protocol communication between the Android server and MCP clients.

## Requirements

- Full JSON-RPC 2.0 specification compliance
- Support for all MCP protocol methods
- Proper error handling and validation
- Type-safe message serialization/deserialization
- Integration with existing transport layers
- Performance-optimized parsing for high-throughput scenarios

## Current State

The `McpAndroidServer.kt` contains a TODO comment at line 525:
```kotlin
// TODO: Parse JSON-RPC message and handle MCP protocol
```

Currently, the server has placeholder message handling without actual JSON-RPC parsing implementation.

## Implementation Steps

### 1. Add JSON Processing Dependencies

Update `lib/build.gradle.kts`:
```kotlin
dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
}
```

### 2. Create JSON-RPC Core Types

Create `lib/src/main/kotlin/dev/jasonpearson/mcpandroidsdk/protocol/jsonrpc/`:

**JsonRpcTypes.kt:**
```kotlin
@Serializable
sealed class JsonRpcMessage {
    abstract val jsonrpc: String
    abstract val id: JsonElement?
}

@Serializable
data class JsonRpcRequest(
    override val jsonrpc: String = "2.0",
    override val id: JsonElement? = null,
    val method: String,
    val params: JsonElement? = null
) : JsonRpcMessage()

@Serializable
data class JsonRpcResponse(
    override val jsonrpc: String = "2.0",
    override val id: JsonElement? = null,
    val result: JsonElement? = null,
    val error: JsonRpcError? = null
) : JsonRpcMessage()

@Serializable
data class JsonRpcNotification(
    override val jsonrpc: String = "2.0",
    override val id: JsonElement? = null,
    val method: String,
    val params: JsonElement? = null
) : JsonRpcMessage()

@Serializable
data class JsonRpcError(
    val code: Int,
    val message: String,
    val data: JsonElement? = null
) {
    companion object {
        const val PARSE_ERROR = -32700
        const val INVALID_REQUEST = -32600
        const val METHOD_NOT_FOUND = -32601
        const val INVALID_PARAMS = -32602
        const val INTERNAL_ERROR = -32603
        
        // MCP-specific error codes
        const val RESOURCE_NOT_FOUND = -32001
        const val TOOL_NOT_FOUND = -32002
        const val PROMPT_NOT_FOUND = -32003
    }
}
```

### 3. Create Message Parser

**JsonRpcParser.kt:**
```kotlin
class JsonRpcParser {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }
    
    fun parseMessage(messageStr: String): Result<JsonRpcMessage> {
        return try {
            val jsonElement = json.parseToJsonElement(messageStr)
            val jsonObject = jsonElement.jsonObject
            
            when {
                jsonObject.containsKey("method") && jsonObject.containsKey("id") -> {
                    Result.success(json.decodeFromJsonElement<JsonRpcRequest>(jsonElement))
                }
                jsonObject.containsKey("method") && !jsonObject.containsKey("id") -> {
                    Result.success(json.decodeFromJsonElement<JsonRpcNotification>(jsonElement))
                }
                jsonObject.containsKey("result") || jsonObject.containsKey("error") -> {
                    Result.success(json.decodeFromJsonElement<JsonRpcResponse>(jsonElement))
                }
                else -> {
                    Result.failure(JsonRpcParseException("Invalid JSON-RPC message format"))
                }
            }
        } catch (e: Exception) {
            Result.failure(JsonRpcParseException("Failed to parse JSON-RPC message", e))
        }
    }
    
    fun serializeMessage(message: JsonRpcMessage): Result<String> {
        return try {
            val serialized = when (message) {
                is JsonRpcRequest -> json.encodeToString(message)
                is JsonRpcResponse -> json.encodeToString(message)
                is JsonRpcNotification -> json.encodeToString(message)
            }
            Result.success(serialized)
        } catch (e: Exception) {
            Result.failure(JsonRpcSerializationException("Failed to serialize JSON-RPC message", e))
        }
    }
    
    fun createErrorResponse(id: JsonElement?, error: JsonRpcError): JsonRpcResponse {
        return JsonRpcResponse(
            id = id,
            error = error
        )
    }
    
    fun createSuccessResponse(id: JsonElement?, result: JsonElement): JsonRpcResponse {
        return JsonRpcResponse(
            id = id,
            result = result
        )
    }
}

class JsonRpcParseException(message: String, cause: Throwable? = null) : Exception(message, cause)
class JsonRpcSerializationException(message: String, cause: Throwable? = null) : Exception(message, cause)
```

### 4. Create MCP Method Handlers

**McpMethodHandler.kt:**
```kotlin
class McpMethodHandler(
    private val toolProvider: ToolProvider,
    private val resourceProvider: ResourceProvider,
    private val promptProvider: PromptProvider,
    private val serverInfo: ServerInfo
) {
    private val json = Json { ignoreUnknownKeys = true }
    
    suspend fun handleRequest(request: JsonRpcRequest): JsonRpcResponse {
        return try {
            val result = when (request.method) {
                "initialize" -> handleInitialize(request.params)
                "tools/list" -> handleToolsList()
                "tools/call" -> handleToolCall(request.params)
                "resources/list" -> handleResourcesList()
                "resources/read" -> handleResourceRead(request.params)
                "resources/subscribe" -> handleResourceSubscribe(request.params)
                "resources/unsubscribe" -> handleResourceUnsubscribe(request.params)
                "prompts/list" -> handlePromptsList()
                "prompts/get" -> handlePromptGet(request.params)
                "roots/list" -> handleRootsList()
                "sampling/createMessage" -> handleSamplingRequest(request.params)
                else -> throw MethodNotFoundException("Method ${request.method} not found")
            }
            
            JsonRpcResponse(
                id = request.id,
                result = result
            )
        } catch (e: Exception) {
            JsonRpcResponse(
                id = request.id,
                error = mapExceptionToError(e)
            )
        }
    }
    
    private suspend fun handleInitialize(params: JsonElement?): JsonElement {
        val initParams = params?.let { 
            json.decodeFromJsonElement<InitializeParams>(it) 
        } ?: throw InvalidParamsException("Initialize requires params")
        
        return json.encodeToJsonElement(InitializeResult(
            protocolVersion = "2024-11-05",
            capabilities = serverInfo.capabilities,
            serverInfo = Implementation(
                name = serverInfo.name,
                version = serverInfo.version
            )
        ))
    }
    
    private suspend fun handleToolsList(): JsonElement {
        val tools = toolProvider.listTools()
        return json.encodeToJsonElement(ListToolsResult(tools = tools))
    }
    
    private suspend fun handleToolCall(params: JsonElement?): JsonElement {
        val callParams = params?.let {
            json.decodeFromJsonElement<CallToolParams>(it)
        } ?: throw InvalidParamsException("Tool call requires params")
        
        val result = toolProvider.callTool(callParams.name, callParams.arguments ?: emptyMap())
        return json.encodeToJsonElement(result)
    }
    
    private suspend fun handleResourcesList(): JsonElement {
        val resources = resourceProvider.listResources()
        return json.encodeToJsonElement(ListResourcesResult(resources = resources))
    }
    
    private suspend fun handleResourceRead(params: JsonElement?): JsonElement {
        val readParams = params?.let {
            json.decodeFromJsonElement<ReadResourceParams>(it)
        } ?: throw InvalidParamsException("Resource read requires params")
        
        val content = resourceProvider.readResource(readParams.uri)
        return json.encodeToJsonElement(ReadResourceResult(contents = listOf(content)))
    }
    
    private suspend fun handleResourceSubscribe(params: JsonElement?): JsonElement {
        val subscribeParams = params?.let {
            json.decodeFromJsonElement<SubscribeParams>(it)
        } ?: throw InvalidParamsException("Resource subscribe requires params")
        
        resourceProvider.subscribeToResource(subscribeParams.uri)
        return JsonElement.serializer().descriptor.let { JsonPrimitive("{}") }
    }
    
    private suspend fun handleResourceUnsubscribe(params: JsonElement?): JsonElement {
        val unsubscribeParams = params?.let {
            json.decodeFromJsonElement<UnsubscribeParams>(it)
        } ?: throw InvalidParamsException("Resource unsubscribe requires params")
        
        resourceProvider.unsubscribeFromResource(unsubscribeParams.uri)
        return JsonElement.serializer().descriptor.let { JsonPrimitive("{}") }
    }
    
    private suspend fun handlePromptsList(): JsonElement {
        val prompts = promptProvider.listPrompts()
        return json.encodeToJsonElement(ListPromptsResult(prompts = prompts))
    }
    
    private suspend fun handlePromptGet(params: JsonElement?): JsonElement {
        val getParams = params?.let {
            json.decodeFromJsonElement<GetPromptParams>(it)
        } ?: throw InvalidParamsException("Prompt get requires params")
        
        val result = promptProvider.getPrompt(getParams.name, getParams.arguments ?: emptyMap())
        return json.encodeToJsonElement(result)
    }
    
    private suspend fun handleRootsList(): JsonElement {
        val roots = listOf(
            Root(
                uri = "file://${android.os.Environment.getExternalStorageDirectory()}/Android/data/${serverInfo.packageName}",
                name = "App Data Directory"
            )
        )
        return json.encodeToJsonElement(ListRootsResult(roots = roots))
    }
    
    private suspend fun handleSamplingRequest(params: JsonElement?): JsonElement {
        // Implementation depends on Task 02
        throw MethodNotFoundException("Sampling not yet implemented")
    }
    
    private fun mapExceptionToError(e: Exception): JsonRpcError {
        return when (e) {
            is MethodNotFoundException -> JsonRpcError(
                code = JsonRpcError.METHOD_NOT_FOUND,
                message = e.message ?: "Method not found"
            )
            is InvalidParamsException -> JsonRpcError(
                code = JsonRpcError.INVALID_PARAMS,
                message = e.message ?: "Invalid params"
            )
            is ResourceNotFoundException -> JsonRpcError(
                code = JsonRpcError.RESOURCE_NOT_FOUND,
                message = e.message ?: "Resource not found"
            )
            is ToolNotFoundException -> JsonRpcError(
                code = JsonRpcError.TOOL_NOT_FOUND,
                message = e.message ?: "Tool not found"
            )
            is PromptNotFoundException -> JsonRpcError(
                code = JsonRpcError.PROMPT_NOT_FOUND,
                message = e.message ?: "Prompt not found"
            )
            else -> JsonRpcError(
                code = JsonRpcError.INTERNAL_ERROR,
                message = e.message ?: "Internal error"
            )
        }
    }
}

// Exception classes
class MethodNotFoundException(message: String) : Exception(message)
class InvalidParamsException(message: String) : Exception(message)
class ResourceNotFoundException(message: String) : Exception(message)
class ToolNotFoundException(message: String) : Exception(message)
class PromptNotFoundException(message: String) : Exception(message)
```

### 5. Create MCP Parameter Types

**McpParameterTypes.kt:**
```kotlin
@Serializable
data class InitializeParams(
    val protocolVersion: String,
    val capabilities: ClientCapabilities,
    val clientInfo: Implementation
)

@Serializable
data class InitializeResult(
    val protocolVersion: String,
    val capabilities: ServerCapabilities,
    val serverInfo: Implementation
)

@Serializable
data class CallToolParams(
    val name: String,
    val arguments: Map<String, JsonElement>? = null
)

@Serializable
data class ReadResourceParams(
    val uri: String
)

@Serializable
data class SubscribeParams(
    val uri: String
)

@Serializable
data class UnsubscribeParams(
    val uri: String
)

@Serializable
data class GetPromptParams(
    val name: String,
    val arguments: Map<String, JsonElement>? = null
)

@Serializable
data class ListToolsResult(
    val tools: List<Tool>
)

@Serializable
data class ListResourcesResult(
    val resources: List<Resource>
)

@Serializable
data class ReadResourceResult(
    val contents: List<ResourceContent>
)

@Serializable
data class ListPromptsResult(
    val prompts: List<Prompt>
)

@Serializable
data class ListRootsResult(
    val roots: List<Root>
)
```

### 6. Update McpAndroidServer

Replace the TODO section in `McpAndroidServer.kt`:

```kotlin
class McpAndroidServer {
    private val jsonRpcParser = JsonRpcParser()
    private val methodHandler = McpMethodHandler(toolProvider, resourceProvider, promptProvider, serverInfo)
    
    suspend fun handleMessage(messageStr: String): String {
        val parseResult = jsonRpcParser.parseMessage(messageStr)
        
        return when (val message = parseResult.getOrNull()) {
            is JsonRpcRequest -> {
                val response = methodHandler.handleRequest(message)
                jsonRpcParser.serializeMessage(response).getOrThrow()
            }
            is JsonRpcNotification -> {
                handleNotification(message)
                "" // Notifications don't expect responses
            }
            is JsonRpcResponse -> {
                handleResponse(message)
                "" // Responses are handled internally
            }
            null -> {
                val error = JsonRpcResponse(
                    id = null,
                    error = JsonRpcError(
                        code = JsonRpcError.PARSE_ERROR,
                        message = "Parse error: ${parseResult.exceptionOrNull()?.message}"
                    )
                )
                jsonRpcParser.serializeMessage(error).getOrThrow()
            }
        }
    }
    
    private suspend fun handleNotification(notification: JsonRpcNotification) {
        when (notification.method) {
            "notifications/initialized" -> handleInitializedNotification()
            "notifications/cancelled" -> handleCancelledNotification(notification.params)
            "notifications/progress" -> handleProgressNotification(notification.params)
            else -> {
                Log.w(TAG, "Unknown notification method: ${notification.method}")
            }
        }
    }
    
    private suspend fun handleResponse(response: JsonRpcResponse) {
        // Handle responses to requests we sent (e.g., sampling responses)
        // This will be implemented in Task 02
    }
}
```

### 7. Add Message Validation

**MessageValidator.kt:**
```kotlin
object MessageValidator {
    fun validateJsonRpcMessage(message: JsonRpcMessage): List<ValidationError> {
        val errors = mutableListOf<ValidationError>()
        
        // Validate JSON-RPC version
        if (message.jsonrpc != "2.0") {
            errors.add(ValidationError("Invalid JSON-RPC version: ${message.jsonrpc}"))
        }
        
        when (message) {
            is JsonRpcRequest -> {
                if (message.method.isBlank()) {
                    errors.add(ValidationError("Method cannot be empty"))
                }
                // Validate method-specific parameters
                errors.addAll(validateMethodParams(message.method, message.params))
            }
            is JsonRpcResponse -> {
                if (message.result == null && message.error == null) {
                    errors.add(ValidationError("Response must have either result or error"))
                }
                if (message.result != null && message.error != null) {
                    errors.add(ValidationError("Response cannot have both result and error"))
                }
            }
        }
        
        return errors
    }
    
    private fun validateMethodParams(method: String, params: JsonElement?): List<ValidationError> {
        // Method-specific parameter validation
        return when (method) {
            "tools/call" -> validateToolCallParams(params)
            "resources/read" -> validateResourceReadParams(params)
            // Add more method validations
            else -> emptyList()
        }
    }
}

data class ValidationError(val message: String)
```

## Verification Steps

### 1. Unit Tests

Create `JsonRpcParserTest.kt`:
```kotlin
@Test
fun `parse valid JSON-RPC request`() {
    val json = """{"jsonrpc":"2.0","id":1,"method":"tools/list"}"""
    val parser = JsonRpcParser()
    
    val result = parser.parseMessage(json)
    
    assert(result.isSuccess)
    val message = result.getOrNull() as JsonRpcRequest
    assertEquals("tools/list", message.method)
    assertEquals(JsonPrimitive(1), message.id)
}

@Test
fun `parse JSON-RPC notification without id`() {
    val json = """{"jsonrpc":"2.0","method":"notifications/initialized"}"""
    val parser = JsonRpcParser()
    
    val result = parser.parseMessage(json)
    
    assert(result.isSuccess)
    assert(result.getOrNull() is JsonRpcNotification)
}

@Test
fun `parse invalid JSON returns error`() {
    val json = """invalid json"""
    val parser = JsonRpcParser()
    
    val result = parser.parseMessage(json)
    
    assert(result.isFailure)
    assert(result.exceptionOrNull() is JsonRpcParseException)
}
```

Create `McpMethodHandlerTest.kt`:
```kotlin
@Test
fun `handle tools list request`() = runTest {
    val handler = McpMethodHandler(mockToolProvider, mockResourceProvider, mockPromptProvider, mockServerInfo)
    val request = JsonRpcRequest(method = "tools/list", id = JsonPrimitive(1))
    
    val response = handler.handleRequest(request)
    
    assert(response.error == null)
    assert(response.result != null)
}

@Test
fun `handle unknown method returns method not found error`() = runTest {
    val handler = McpMethodHandler(mockToolProvider, mockResourceProvider, mockPromptProvider, mockServerInfo)
    val request = JsonRpcRequest(method = "unknown/method", id = JsonPrimitive(1))
    
    val response = handler.handleRequest(request)
    
    assert(response.error != null)
    assertEquals(JsonRpcError.METHOD_NOT_FOUND, response.error?.code)
}
```

### 2. Integration Tests

```kotlin
@Test
fun `end to end message handling`() = runTest {
    val server = McpAndroidServer(context)
    val requestJson = """{"jsonrpc":"2.0","id":1,"method":"tools/list"}"""
    
    val responseJson = server.handleMessage(requestJson)
    
    val parser = JsonRpcParser()
    val response = parser.parseMessage(responseJson).getOrThrow() as JsonRpcResponse
    
    assert(response.error == null)
    assert(response.id == JsonPrimitive(1))
}
```

### 3. Performance Tests

```kotlin
@Test
fun `message parsing performance under load`() = runTest {
    val parser = JsonRpcParser()
    val json = """{"jsonrpc":"2.0","id":1,"method":"tools/list"}"""
    
    val startTime = System.currentTimeMillis()
    repeat(10000) {
        parser.parseMessage(json)
    }
    val duration = System.currentTimeMillis() - startTime
    
    assert(duration < 1000) // Should parse 10k messages in under 1 second
}
```

### 4. Manual Testing

1. **Valid Message Testing:**
   ```bash
   # Test with various MCP clients
   # Send different types of JSON-RPC messages
   # Verify correct responses
   ```

2. **Error Condition Testing:**
   ```bash
   # Send malformed JSON
   # Send invalid method names
   # Send missing required parameters
   # Verify proper error responses
   ```

## Dependencies

- None (independent task, but required by transport tasks)

## Resources

- [JSON-RPC 2.0 Specification](https://www.jsonrpc.org/specification)
- [MCP Protocol Specification](https://modelcontextprotocol.io/specification)
- [Kotlinx Serialization Documentation](https://kotlinlang.org/docs/serialization.html)
- [Kotlin Coroutines Documentation](https://kotlinlang.org/docs/coroutines-overview.html)

## Acceptance Criteria

- [ ] All JSON-RPC 2.0 message types are properly parsed
- [ ] All MCP protocol methods are handled correctly
- [ ] Error responses follow JSON-RPC 2.0 specification
- [ ] Message validation prevents invalid requests
- [ ] Performance is acceptable for high-throughput scenarios
- [ ] Integration with existing providers works seamlessly
- [ ] Unit tests cover all parsing scenarios
- [ ] Integration tests verify end-to-end message handling
- [ ] Error handling is robust and informative
- [ ] Code is type-safe and leverages Kotlin features
