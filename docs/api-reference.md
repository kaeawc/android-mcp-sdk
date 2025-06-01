# API Reference

Complete API reference for the Android MCP SDK, covering all public classes, methods, and
interfaces.

## Core Classes

### McpStartup

Utility class providing convenient access to MCP server functionality.

#### Static Methods

##### `isInitialized(): Boolean`

Check if the MCP server has been initialized.

```kotlin
if (McpStartup.isInitialized()) {
    // Server is ready to use
}
```

##### `getManager(): McpServerManager`

Get the singleton instance of the MCP server manager.

```kotlin
val manager = McpStartup.getManager()
```

**Throws**: `IllegalStateException` if not initialized

##### `initializeManually(context: Context): McpServerManager`

Manually initialize the MCP server with default configuration.

```kotlin
val manager = McpStartup.initializeManually(context)
```

**Parameters**:

- `context`: Android Context (Application or Activity)

**Returns**: Configured McpServerManager instance

#####
`initializeWithCustomConfig(context: Context, serverName: String, serverVersion: String): Result<McpServerManager>`

Initialize with custom server configuration.

```kotlin
val result = McpStartup.initializeWithCustomConfig(
    context = this,
    serverName = "My Custom Server",
    serverVersion = "2.0.0"
)
```

**Parameters**:

- `context`: Android Context
- `serverName`: Custom server name
- `serverVersion`: Custom server version

**Returns**: Result containing McpServerManager or error

### McpServerManager

Thread-safe singleton for managing MCP server lifecycle and operations.

#### Server Lifecycle

##### `initialize(context: Context, serverName: String?, serverVersion: String?): Result<Unit>`

Initialize the server with optional custom configuration.

```kotlin
manager.initialize(
    context = applicationContext,
    serverName = "My App Server",
    serverVersion = "1.0.0"
).onSuccess {
    Log.d("MCP", "Server initialized")
}.onFailure { error ->
    Log.e("MCP", "Initialization failed", error)
}
```

##### `startServer(): Result<Unit>`

Start the MCP server (suspending function).

```kotlin
lifecycleScope.launch {
    manager.startServer().getOrThrow()
}
```

##### `startServerAsync()`

Start the MCP server asynchronously (non-blocking).

```kotlin
manager.startServerAsync()
```

##### `stopServer(): Result<Unit>`

Stop the MCP server (suspending function).

```kotlin
lifecycleScope.launch {
    manager.stopServer().getOrThrow()
}
```

##### `isServerRunning(): Boolean`

Check if the server is currently running.

```kotlin
val running = manager.isServerRunning()
```

#### Server Information

##### `getMcpSdkVersion(): String`

Get the version of the integrated MCP SDK.

```kotlin
val version = manager.getMcpSdkVersion()
```

##### `getTransportInfo(): String`

Get information about transport layer configuration.

```kotlin
val info = manager.getTransportInfo()
Log.d("MCP", "Transport: $info")
```

##### `hasSDKIntegration(): Boolean`

Check if MCP SDK integration is available.

```kotlin
if (manager.hasSDKIntegration()) {
    // Full MCP features available
}
```

#### Tool Management

#####
`addSimpleTool(name: String, description: String, parameters: Map<String, String>, handler: (Map<String, Any>) -> String)`

Add a simple tool with minimal configuration.

```kotlin
manager.addSimpleTool(
    name = "calculate",
    description = "Perform calculations",
    parameters = mapOf("operation" to "string", "a" to "number", "b" to "number")
) { args ->
    val op = args["operation"] as String
    val a = (args["a"] as Number).toDouble()
    val b = (args["b"] as Number).toDouble()
    
    when (op) {
        "add" -> "Result: ${a + b}"
        "multiply" -> "Result: ${a * b}"
        else -> "Unknown operation"
    }
}
```

##### `addMcpTool(tool: Tool, handler: (Map<String, Any>) -> CallToolResult)`

Add a full MCP tool with complete schema support.

```kotlin
val tool = Tool(
    name = "advanced_tool",
    description = "Advanced tool with full schema",
    inputSchema = Tool.Input(
        properties = buildJsonObject { /* schema */ },
        required = listOf("param1")
    )
)

manager.addMcpTool(tool) { args ->
    CallToolResult(
        content = listOf(TextContent(text = "Result")),
        isError = false
    )
}
```

##### `executeAndroidTool(name: String, arguments: Map<String, Any>): AndroidToolResult`

Execute a built-in Android tool.

```kotlin
val result = manager.executeAndroidTool("device_info", emptyMap())
Log.d("MCP", "Device info: ${result.result}")
```

#### Resource Management

#####
`addFileResource(uri: String, name: String, description: String, filePath: String, mimeType: String)`

Add a file-based resource.

```kotlin
manager.addFileResource(
    uri = "app://config/settings.json",
    name = "App Settings",
    description = "Application settings",
    filePath = File(context.filesDir, "settings.json").absolutePath,
    mimeType = "application/json"
)
```

##### `addMcpResource(resource: Resource, provider: () -> AndroidResourceContent)`

Add a dynamic MCP resource.

```kotlin
val resource = Resource(
    uri = "app://status",
    name = "App Status",
    description = "Real-time app status"
)

manager.addMcpResource(resource) {
    AndroidResourceContent(
        uri = "app://status",
        text = getCurrentStatus(),
        mimeType = "application/json"
    )
}
```

##### `subscribeMcpResource(uri: String)`

Subscribe to resource updates.

```kotlin
manager.subscribeMcpResource("app://status")
```

#### Prompt Management

#####
`addSimplePrompt(name: String, description: String, arguments: List<PromptArgument>, generator: (Map<String, Any>) -> String)`

Add a simple prompt template.

```kotlin
manager.addSimplePrompt(
    name = "code_review",
    description = "Generate code review",
    arguments = listOf(
        PromptArgument("code", "Code to review", required = true),
        PromptArgument("language", "Programming language", required = false)
    )
) { args ->
    val code = args["code"] as String
    val language = args["language"] as? String ?: "kotlin"
    
    "Please review this $language code:\n\n```$language\n$code\n```"
}
```

##### `addMcpPrompt(prompt: Prompt, generator: (Map<String, Any>) -> GetPromptResult)`

Add a full MCP prompt.

```kotlin
val prompt = Prompt(
    name = "advanced_prompt",
    description = "Advanced prompt with multiple messages",
    arguments = listOf(/* arguments */)
)

manager.addMcpPrompt(prompt) { args ->
    GetPromptResult(
        description = "Generated prompt",
        messages = listOf(
            PromptMessage(
                role = MessageRole.USER,
                content = TextContent(text = "Generated content")
            )
        )
    )
}
```

##### `getMcpPrompt(name: String, arguments: Map<String, Any>): GetPromptResult`

Get a prompt with specified arguments (suspending function).

```kotlin
lifecycleScope.launch {
    val result = manager.getMcpPrompt("code_review", mapOf("code" to sourceCode))
    Log.d("MCP", "Prompt: ${result.description}")
}
```

#### Lifecycle Management

#####
`initializeLifecycleManagement(application: Application, config: McpLifecycleManager.LifecycleConfig)`

Initialize lifecycle management.

```kotlin
manager.initializeLifecycleManagement(
    application = this,
    config = McpLifecycleManager.LifecycleConfig(
        autoStartOnAppStart = true,
        autoStopOnAppStop = false,
        restartOnAppReturn = true,
        pauseOnBackground = false,
        stopOnLastActivityDestroyed = false
    )
)
```

##### `getLifecycleState(): McpLifecycleManager.LifecycleState`

Get current lifecycle state.

```kotlin
val state = manager.getLifecycleState()
Log.d("MCP", "In background: ${state.isAppInBackground}")
Log.d("MCP", "Active activities: ${state.activeActivities}")
```

##### `updateLifecycleConfig(config: McpLifecycleManager.LifecycleConfig)`

Update lifecycle configuration.

```kotlin
manager.updateLifecycleConfig(
    manager.getLifecycleState().config.copy(
        autoStopOnAppStop = false
    )
)
```

#### Transport Layer

##### `broadcastMessage(message: String)`

Broadcast a message to all connected clients (suspending function).

```kotlin
lifecycleScope.launch {
    manager.broadcastMessage("""{"jsonrpc":"2.0","method":"notification","params":{}}""")
}
```

## Data Classes

### Tool

Represents an MCP tool definition.

```kotlin
data class Tool(
    val name: String,
    val description: String,
    val inputSchema: Input
) {
    data class Input(
        val type: String = "object",
        val properties: JsonObject,
        val required: List<String> = emptyList()
    )
}
```

**Example**:

```kotlin
val tool = Tool(
    name = "my_tool",
    description = "Tool description",
    inputSchema = Tool.Input(
        properties = buildJsonObject {
            put("param1", buildJsonObject {
                put("type", JsonPrimitive("string"))
                put("description", JsonPrimitive("Parameter description"))
            })
        },
        required = listOf("param1")
    )
)
```

### Resource

Represents an MCP resource.

```kotlin
data class Resource(
    val uri: String,
    val name: String,
    val description: String,
    val mimeType: String? = null
)
```

### Prompt

Represents an MCP prompt template.

```kotlin
data class Prompt(
    val name: String,
    val description: String,
    val arguments: List<PromptArgument> = emptyList()
)
```

### PromptArgument

Represents a prompt argument.

```kotlin
data class PromptArgument(
    val name: String,
    val description: String,
    val required: Boolean = false
)
```

### CallToolResult

Result of a tool execution.

```kotlin
data class CallToolResult(
    val content: List<McpContent>,
    val isError: Boolean = false
)
```

### AndroidResourceContent

Content for Android-specific resources.

```kotlin
data class AndroidResourceContent(
    val uri: String,
    val text: String? = null,
    val blob: ByteArray? = null,
    val mimeType: String? = null
)
```

### GetPromptResult

Result of prompt generation.

```kotlin
data class GetPromptResult(
    val description: String,
    val messages: List<PromptMessage>
)
```

### PromptMessage

Message within a prompt.

```kotlin
data class PromptMessage(
    val role: MessageRole,
    val content: McpContent
)
```

## Enums

### MessageRole

Roles for prompt messages.

```kotlin
enum class MessageRole {
    USER,
    ASSISTANT
}
```

## Content Types

### McpContent

Base interface for MCP content.

```kotlin
sealed interface McpContent
```

### TextContent

Text-based content.

```kotlin
data class TextContent(
    val text: String
) : McpContent
```

### ImageContent

Image-based content.

```kotlin
data class ImageContent(
    val data: String,
    val mimeType: String
) : McpContent
```

## Configuration Classes

### McpLifecycleManager.LifecycleConfig

Configuration for lifecycle management.

```kotlin
data class LifecycleConfig(
    val autoStartOnAppStart: Boolean = true,
    val autoStopOnAppStop: Boolean = true,
    val restartOnAppReturn: Boolean = false,
    val pauseOnBackground: Boolean = false,
    val stopOnLastActivityDestroyed: Boolean = true
)
```

### McpLifecycleManager.LifecycleState

Current lifecycle state information.

```kotlin
data class LifecycleState(
    val isAppInBackground: Boolean,
    val activeActivities: Int,
    val isServerRunning: Boolean,
    val config: LifecycleConfig
)
```

## Built-in Tools

The SDK provides several built-in Android tools:

### device_info

Get comprehensive device information.

```kotlin
val result = manager.executeAndroidTool("device_info", emptyMap())
```

**Returns**: JSON with device model, manufacturer, Android version, etc.

### app_info

Get application information.

```kotlin
val result = manager.executeAndroidTool("app_info", emptyMap())
```

**Returns**: JSON with app name, version, package name, etc.

### system_time

Get current system time in various formats.

```kotlin
val result = manager.executeAndroidTool("system_time", emptyMap())
```

**Returns**: JSON with timestamp, formatted time, timezone, etc.

### memory_info

Get system and app memory information.

```kotlin
val result = manager.executeAndroidTool("memory_info", emptyMap())
```

**Returns**: JSON with memory usage statistics.

### battery_info

Get battery status and information.

```kotlin
val result = manager.executeAndroidTool("battery_info", emptyMap())
```

**Returns**: JSON with battery level, charging status, health, etc.

## Built-in Resources

### android://app/info

Application information resource.

### android://device/info

Device information resource.

## Built-in Prompts

### analyze_android_log

Analyze Android logs for issues.

**Arguments**:

- `log_content` (required): Log content to analyze
- `focus_area` (optional): Specific area to focus on

### generate_android_code

Generate Android code with best practices.

**Arguments**:

- `description` (required): What to generate
- `language` (optional): Programming language (default: kotlin)

### explain_android_error

Explain and provide solutions for Android errors.

**Arguments**:

- `error_message` (required): Error message or stack trace
- `context` (optional): Additional context

### create_android_test

Create comprehensive test suites.

**Arguments**:

- `component` (required): Component to test
- `test_type` (optional): Type of test (unit, integration, ui)

### review_android_code

Review code for quality and best practices.

**Arguments**:

- `code` (required): Code to review
- `focus` (optional): Areas to focus on

## Error Handling

All async operations return `Result<T>` for safe error handling:

```kotlin
manager.startServer().fold(
    onSuccess = { 
        Log.d("MCP", "Server started successfully")
    },
    onFailure = { error ->
        Log.e("MCP", "Failed to start server", error)
    }
)
```

Common exceptions:

- `IllegalStateException`: Server not initialized
- `IllegalArgumentException`: Invalid parameters
- `IOException`: Network or file I/O errors

## Threading

- All manager methods are thread-safe
- Suspending functions should be called from coroutines
- Async methods handle threading internally

## Best Practices

1. **Use Result handling**: Always handle success/failure cases
2. **Lifecycle management**: Configure appropriate lifecycle behavior
3. **Resource cleanup**: Stop server when appropriate
4. **Error logging**: Log errors for debugging
5. **Validation**: Validate tool inputs and resource URIs