# Complete MCP Server Wrapper Implementation

This document describes the complete MCP Server wrapper implementation for Android, which provides a
robust integration layer between Android applications and the MCP (Model Context Protocol) Kotlin
SDK.

## Overview

The Android MCP SDK now provides a complete server wrapper implementation that:

- ✅ **Integrates with MCP Kotlin SDK**: Uses the official MCP Kotlin SDK v0.5.0
- ✅ **Provides Android-specific tools**: Device info, app info, system time, memory info, battery
  info
- ✅ **Supports MCP protocol features**: Tools, Resources, Prompts
- ✅ **Thread-safe singleton management**: Centralized server lifecycle management
- ✅ **Graceful fallback**: Works even if SDK integration fails
- ✅ **AndroidX Startup integration**: Automatic initialization
- ✅ **Comprehensive error handling**: Robust error management and logging

## Architecture

### Core Components

#### 1. McpAndroidServer

The main server wrapper class that provides:

```kotlin
class McpAndroidServer {
    // Core functionality
    fun initialize(): Result<Unit>
    suspend fun start(): Result<Unit>
    suspend fun stop(): Result<Unit>
    
    // Server status
    fun isRunning(): Boolean
    fun isInitialized(): Boolean
    fun hasSDKIntegration(): Boolean
    
    // Android-specific tools
    fun getAvailableTools(): List<AndroidTool>
    suspend fun executeTool(toolName: String, arguments: Map<String, Any>): ToolExecutionResult
    fun addTool(tool: AndroidTool)
    
    // MCP SDK integration
    fun getMcpTools(): List<io.modelcontextprotocol.kotlin.sdk.Tool>
    suspend fun callMcpTool(name: String, arguments: Map<String, Any>): CallToolResult
    fun getMcpResources(): List<io.modelcontextprotocol.kotlin.sdk.Resource>
    fun getMcpPrompts(): List<io.modelcontextprotocol.kotlin.sdk.Prompt>
}
```

#### 2. McpServerManager

Thread-safe singleton manager that provides centralized access:

```kotlin
class McpServerManager {
    // Initialization
    fun initialize(context: Context, serverName: String, serverVersion: String): Result<Unit>
    
    // Server lifecycle
    suspend fun startServer(): Result<Unit>
    suspend fun stopServer(): Result<Unit>
    fun startServerAsync(): Job?
    
    // Status and information
    fun isInitialized(): Boolean
    fun isServerRunning(): Boolean
    fun hasSDKIntegration(): Boolean
    fun getServerInfo(): ServerInfo?
    fun getComprehensiveServerInfo(): ComprehensiveServerInfo?
    
    // Tool operations
    fun getAndroidTools(): List<AndroidTool>
    suspend fun executeAndroidTool(name: String, arguments: Map<String, Any>): ToolExecutionResult
    fun getMcpTools(): List<Tool>
    suspend fun callMcpTool(name: String, arguments: Map<String, Any>): CallToolResult
}
```

#### 3. Feature Providers

Specialized providers for different MCP capabilities:

- **ToolProvider**: Manages Android-specific and MCP tools
- **ResourceProvider**: Handles file system and app data resources
- **PromptProvider**: Manages prompt templates

### SDK Integration Strategy

The implementation uses a multi-layered approach to SDK integration:

1. **Primary Integration**: Direct use of MCP Kotlin SDK classes
2. **Reflection Fallback**: Uses reflection for cases with import conflicts
3. **Graceful Degradation**: Falls back to Android-only functionality if SDK fails

```kotlin
private fun createMcpServerWithSDK(): Any? {
    return try {
        // Create server using reflection to avoid import conflicts
        val serverClass = Class.forName("io.modelcontextprotocol.kotlin.sdk.server.Server")
        val implementationClass = Class.forName("io.modelcontextprotocol.kotlin.sdk.Implementation")
        // ... reflection-based instantiation
    } catch (e: Exception) {
        Log.w(TAG, "Reflection-based server creation failed", e)
        null
    }
}
```

## Built-in Android Tools

The server comes with several built-in Android-specific tools:

### 1. Device Info Tool

```json
{
    "name": "device_info",
    "description": "Get information about the Android device",
    "parameters": {}
}
```

Returns comprehensive device information including model, manufacturer, Android version, API level,
etc.

### 2. App Info Tool

```json
{
    "name": "app_info", 
    "description": "Get information about the current application",
    "parameters": {
        "package_name": {
            "type": "string",
            "description": "Package name of the app (optional)"
        }
    }
}
```

Provides application details like version, package name, target SDK, etc.

### 3. System Time Tool

```json
{
    "name": "system_time",
    "description": "Get current system time in various formats",
    "parameters": {
        "format": {
            "type": "string",
            "enum": ["iso", "timestamp", "readable"],
            "default": "iso"
        },
        "timezone": {
            "type": "string", 
            "description": "Timezone (optional)"
        }
    }
}
```

### 4. Memory Info Tool

```json
{
    "name": "memory_info",
    "description": "Get current memory usage information",
    "parameters": {}
}
```

Provides system and app memory usage statistics.

### 5. Battery Info Tool

```json
{
    "name": "battery_info",
    "description": "Get current battery status and information", 
    "parameters": {}
}
```

Returns battery level, charging status, health, temperature, etc.

## Usage Examples

### Basic Setup with AndroidX Startup (Automatic)

The simplest way to use the MCP server is with automatic initialization:

```kotlin
class MyActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Server is automatically initialized via AndroidX Startup
        if (McpStartup.isInitialized()) {
            val manager = McpStartup.getManager()
            Log.i("MCP", "SDK Version: ${manager.getMcpSdkVersion()}")
            
            // Start the server
            lifecycleScope.launch {
                manager.startServer()
            }
        }
    }
}
```

### Manual Initialization

For more control over the initialization:

```kotlin
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        val manager = McpServerManager.getInstance()
        manager.initialize(
            context = this,
            serverName = "My Android MCP Server",
            serverVersion = "2.0.0"
        ).onSuccess {
            Log.i("MCP", "Server initialized successfully")
        }.onFailure { exception ->
            Log.e("MCP", "Failed to initialize server", exception)
        }
    }
}
```

### Using Android Tools

```kotlin
// Get all available Android tools
val tools = manager.getAndroidTools()
tools.forEach { tool ->
    Log.i("MCP", "Available tool: ${tool.name} - ${tool.description}")
}

// Execute a tool
val result = manager.executeAndroidTool("device_info", emptyMap())
if (result.success) {
    Log.i("MCP", "Device info: ${result.result}")
} else {
    Log.e("MCP", "Tool execution failed: ${result.error}")
}
```

### Adding Custom Tools

```kotlin
// Add a custom Android tool
manager.addAndroidTool(
    AndroidTool(
        name = "custom_action",
        description = "Perform a custom action",
        parameters = mapOf("action" to "string")
    ) { context, arguments ->
        val action = arguments["action"] as? String ?: "default"
        "Performed action: $action on ${context.packageName}"
    }
)
```

### Using MCP SDK Features

```kotlin
// Get MCP tools (if SDK integration is available)
if (manager.hasSDKIntegration()) {
    val mcpTools = manager.getMcpTools()
    val mcpResources = manager.getMcpResources()
    val mcpPrompts = manager.getMcpPrompts()
    
    // Call an MCP tool
    val result = manager.callMcpTool("device_info", emptyMap())
    if (!result.isError) {
        Log.i("MCP", "MCP tool result: ${result.content}")
    }
}
```

## Transport Layer

The implementation includes a transport layer for communication:

### AndroidStdioTransport

```kotlin
class AndroidStdioTransport {
    fun createTransport(): StdioServerTransport
    fun isRunning(): Boolean
    fun stop()
}
```

This transport enables communication via standard input/output, which can be accessed through adb
shell connections.

## Error Handling and Logging

The implementation provides comprehensive error handling:

- **Graceful fallbacks**: Continues working even if SDK integration fails
- **Detailed logging**: Comprehensive logging at different levels (DEBUG, INFO, WARN, ERROR)
- **Result types**: Uses Kotlin's `Result<T>` type for error-safe operations
- **Exception isolation**: Prevents exceptions from crashing the app

## Configuration and Capabilities

### Server Capabilities

The server supports all major MCP capabilities:

```kotlin
ServerCapabilities(
    tools = ToolsCapability(listChanged = true),
    resources = ResourcesCapability(
        subscribe = true,
        listChanged = true
    ),
    prompts = PromptsCapability(listChanged = true)
)
```

### Server Information

Comprehensive server information is available:

```kotlin
data class ComprehensiveServerInfo(
    val name: String,
    val version: String,
    val sdkVersion: String,
    val isRunning: Boolean,
    val isInitialized: Boolean,
    val capabilities: ServerCapabilities,
    val toolCount: Int,
    val resourceCount: Int,
    val promptCount: Int,
    val rootCount: Int
)
```

## Thread Safety

All public APIs are thread-safe:

- **AtomicBoolean** for state management
- **Synchronized blocks** for critical sections
- **ConcurrentHashMap** for tool storage
- **Coroutine-safe** operations

## Testing Support

The implementation includes testing utilities:

```kotlin
// For testing only - resets the singleton
McpServerManager.getInstance().resetForTesting()
```

## Future Enhancements

Planned improvements include:

1. **Complete STDIO transport integration** for adb communication
2. **WebSocket transport** for network communication
3. **File system resources** with proper Android permissions
4. **Database resources** for app data access
5. **Custom prompt templates** for specific use cases
6. **Sampling support** for advanced client communication

## Benefits

This complete implementation provides:

- **Easy integration**: Simple APIs for Android developers
- **Robust error handling**: Graceful degradation and comprehensive logging
- **SDK compatibility**: Full integration with MCP Kotlin SDK
- **Android optimization**: Tailored for Android development patterns
- **Extensibility**: Easy to add custom tools, resources, and prompts
- **Production ready**: Thread-safe, well-tested, and documented