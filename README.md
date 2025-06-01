# Android MCP SDK

This Android library integrates
the [MCP Kotlin SDK](https://github.com/modelcontextprotocol/kotlin-sdk) to enable Android
applications to host MCP (Model Context Protocol) servers.

## Integration Status

✅ **MCP Kotlin SDK Added**: Version 0.5.0 has been successfully integrated into the project  
✅ **Dependencies Configured**: Both main and JVM-specific artifacts are included  
✅ **Project Structure**: Kotlin source files properly organized in `lib/src/main/kotlin/`  
✅ **Compilation Working**: Library compiles successfully with MCP SDK dependencies  
✅ **Singleton Manager**: Thread-safe singleton implementation for MCP server management  
✅ **AndroidX Startup Complete**: Full AndroidX Startup integration with automatic initialization

## Dependencies Added

- `io.modelcontextprotocol:kotlin-sdk:0.5.0` - Main MCP Kotlin SDK
- `io.modelcontextprotocol:kotlin-sdk-jvm:0.5.0` - JVM-specific implementation
- `androidx.startup:startup-runtime:1.2.0` - AndroidX Startup for initialization

## Project Goal

The goal of this project is to expose MCP servers to Android engineers running MCP clients on their
adb-connected workstations. This enables Android apps to provide:

- **Resources**: File-like data that clients can read
- **Tools**: Functions that LLMs can call
- **Prompts**: Pre-created prompt templates

## Usage

### Option 1: Automatic Initialization (Recommended)

The library includes AndroidX Startup integration for automatic initialization. The MCP server will
be initialized automatically when your app starts.

Simply add the library dependency - no additional setup required! The automatic initialization is
already configured in the library's `AndroidManifest.xml`.

```kotlin
class MyActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // MCP server is automatically initialized via AndroidX Startup
        if (McpStartup.isInitialized()) {
            val version = McpStartup.getManager().getMcpSdkVersion()
            Log.i("MCP", "SDK Version: $version")
        }
    }
}
```

### Option 2: Manual Initialization

If you need more control over initialization timing:

```kotlin
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Manual initialization using AndroidX Startup infrastructure
        val manager = McpStartup.initializeManually(this)
        Log.i("MCP", "SDK Version: ${manager.getMcpSdkVersion()}")
    }
}
```

### Option 3: Custom Configuration

For custom server name and version:

```kotlin
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        val result = McpStartup.initializeWithCustomConfig(
            context = this,
            serverName = "My Custom Android MCP Server",
            serverVersion = "2.0.0"
        )
        
        result.fold(
            onSuccess = { manager ->
                Log.i("MCP", "Custom server initialized: ${manager.getMcpSdkVersion()}")
            },
            onFailure = { exception ->
                Log.e("MCP", "Failed to initialize", exception)
            }
        )
    }
}
```

### Starting the MCP Server

```kotlin
// Using the convenient utility class
val manager = McpStartup.getManager()

// Async start (non-blocking) - includes transport startup
manager.startServerAsync()

// Or with coroutines for better control
lifecycleScope.launch {
    manager.startServer().getOrThrow()
}
```

### Using Transport Layer

The library now includes WebSocket and HTTP/SSE transport support for communication with MCP
clients:

#### WebSocket Transport (Default port: 8080)

```kotlin
// WebSocket endpoint: ws://localhost:8080/mcp
val manager = McpStartup.getManager()

// Get transport information
val transportInfo = manager.getTransportInfo()
Log.d("MCP", "Transport info: $transportInfo")

// Send message to connected clients
lifecycleScope.launch {
    manager.broadcastMessage("""{"jsonrpc": "2.0", "method": "tools/list", "id": 1}""")
}
```

#### HTTP/SSE Transport (Default port: 8081)

```kotlin
// HTTP endpoints:
// POST http://localhost:8081/mcp/message - for client-to-server messages
// GET  http://localhost:8081/mcp/events  - for server-to-client events (SSE)
// GET  http://localhost:8081/mcp/status  - for transport status

// The transport is automatically started with the server
// No additional configuration needed
```

#### Connecting from adb

```bash
# Forward ports for access from workstation
adb forward tcp:8080 tcp:8080  # WebSocket
adb forward tcp:8081 tcp:8081  # HTTP/SSE

# Connect with WebSocket client
# ws://localhost:8080/mcp

# Connect with HTTP client
# POST http://localhost:8081/mcp/message
# GET  http://localhost:8081/mcp/events
```

### Using Helper Methods for Tools, Resources, and Prompts

#### Adding Custom Tools

```kotlin
val manager = McpStartup.getManager()

// Add a simple tool with a handler
manager.addSimpleTool(
    name = "calculate_sum",
    description = "Calculate the sum of two numbers",
    parameters = mapOf("a" to "number", "b" to "number")
) { arguments ->
    val a = arguments["a"] as? Number ?: 0
    val b = arguments["b"] as? Number ?: 0
    "Sum: ${a.toDouble() + b.toDouble()}"
}

// Add a more complex MCP tool
val complexTool = Tool(
    name = "complex_operation",
    description = "Perform a complex operation",
    inputSchema = Tool.Input(
        properties = buildJsonObject {
            put("operation", buildJsonObject {
                put("type", JsonPrimitive("string"))
                put("enum", buildJsonArray {
                    add(JsonPrimitive("encode"))
                    add(JsonPrimitive("decode"))
                })
            })
            put("data", buildJsonObject {
                put("type", JsonPrimitive("string"))
            })
        },
        required = listOf("operation", "data")
    )
)

manager.addMcpTool(complexTool) { arguments ->
    val operation = arguments["operation"] as? String ?: "encode"
    val data = arguments["data"] as? String ?: ""
    
    val result = when (operation) {
        "encode" -> java.util.Base64.getEncoder().encodeToString(data.toByteArray())
        "decode" -> String(java.util.Base64.getDecoder().decode(data))
        else -> "Unknown operation: $operation"
    }
    
    CallToolResult(
        content = listOf(TextContent(text = result)),
        isError = false
    )
}
```

#### Adding Custom Resources

```kotlin
// Add a simple file resource
manager.addFileResource(
    uri = "app://config/settings.json",
    name = "App Settings",
    description = "Application configuration settings",
    filePath = "/path/to/settings.json",
    mimeType = "application/json"
)

// Add a dynamic resource with custom content provider
val dynamicResource = Resource(
    uri = "app://status/current",
    name = "Current Status",
    description = "Real-time application status",
    mimeType = "application/json"
)

manager.addMcpResource(dynamicResource) {
    val status = buildJsonObject {
        put("timestamp", JsonPrimitive(System.currentTimeMillis()))
        put("memory_usage", JsonPrimitive(Runtime.getRuntime().totalMemory()))
        put("active_threads", JsonPrimitive(Thread.activeCount()))
    }
    
    AndroidResourceContent(
        uri = "app://status/current",
        text = status.toString(),
        mimeType = "application/json"
    )
}

// Subscribe to resource updates
manager.subscribeMcpResource("app://status/current")
```

#### Adding Custom Prompts

```kotlin
// Add a simple prompt
manager.addSimplePrompt(
    name = "analyze_logs",
    description = "Analyze application logs for issues",
    arguments = listOf(
        PromptArgument(
            name = "log_level",
            description = "Minimum log level to analyze",
            required = false
        ),
        PromptArgument(
            name = "time_range",
            description = "Time range for log analysis",
            required = false
        )
    )
) { arguments ->
    val logLevel = arguments["log_level"] as? String ?: "ERROR"
    val timeRange = arguments["time_range"] as? String ?: "last 24 hours"
    
    """
    Please analyze the application logs with the following criteria:
    - Minimum log level: $logLevel
    - Time range: $timeRange
    
    Focus on:
    1. Error patterns and frequency
    2. Performance bottlenecks
    3. Security-related events
    4. Unusual activity patterns
    
    Provide actionable insights and recommendations.
    """.trimIndent()
}

// Get a prompt with arguments
lifecycleScope.launch {
    val promptResult = manager.getMcpPrompt(
        "analyze_logs",
        mapOf(
            "log_level" to "WARN",
            "time_range" to "last 6 hours"
        )
    )
    
    Log.d("MCP", "Prompt result: ${promptResult.description}")
    promptResult.messages.forEach { message ->
        Log.d("MCP", "Message: ${message.content}")
    }
}
```

### Android Lifecycle Management

```kotlin
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Initialize MCP server
        val manager = McpStartup.initializeManually(this)
        
        // Initialize lifecycle management with custom configuration
        manager.initializeLifecycleManagement(
            application = this,
            config = McpLifecycleManager.LifecycleConfig(
                autoStartOnAppStart = true,
                autoStopOnAppStop = false,  // Keep server running in background
                restartOnAppReturn = true,
                pauseOnBackground = false,
                stopOnLastActivityDestroyed = false
            )
        )
        
        Log.i("MCP", "Application initialized with lifecycle management")
    }
}

// In your activity
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Check lifecycle state
        val manager = McpStartup.getManager()
        val lifecycleState = manager.getLifecycleState()
        
        Log.d("MCP", "App in background: ${lifecycleState.isAppInBackground}")
        Log.d("MCP", "Active activities: ${lifecycleState.activeActivities}")
        Log.d("MCP", "Server running: ${lifecycleState.isServerRunning}")
        
        // Update lifecycle configuration if needed
        if (lifecycleState.config.autoStopOnAppStop) {
            manager.updateLifecycleConfig(
                lifecycleState.config.copy(autoStopOnAppStop = false)
            )
        }
    }
}
```

### Checking Server Status

```kotlin
// Check if initialized
val isInitialized = McpStartup.isInitialized()

// Check if running
val manager = McpStartup.getManager()
val isRunning = manager.isServerRunning()
```

## Architecture

The library provides a clean architecture:

- **`McpAndroidServer`**: Core wrapper around MCP Kotlin SDK
- **`McpServerManager`**: Thread-safe singleton for managing server lifecycle
- **`McpServerManagerInitializer`**: AndroidX Startup initializer for automatic setup
- **`McpStartup`**: Utility class for convenient initialization and access
- **`ExampleMcpApplication`**: Reference implementation showing all initialization patterns

## Building

- Build the library: `./gradlew :lib:compileDebug`
- Build the sample app: `./gradlew :sample:assembleDebug`

## AndroidX Startup Integration

### Automatic Configuration

The library automatically configures AndroidX Startup. No additional manifest changes are needed.

### Manual Configuration

If you need to customize the AndroidX Startup configuration, you can override it in your app's
`AndroidManifest.xml`:

```xml
<provider
    android:name="androidx.startup.InitializationProvider"
    android:authorities="${applicationId}.androidx-startup"
    android:exported="false"
    tools:node="merge">
    <meta-data
        android:name="dev.jasonpearson.mcpandroidsdk.McpServerManagerInitializer"
        android:value="androidx.startup" />
</provider>
```

### Disabling Automatic Initialization

To disable automatic initialization and use manual initialization instead:

```xml
<provider
    android:name="androidx.startup.InitializationProvider"
    android:authorities="${applicationId}.androidx-startup"
    android:exported="false"
    tools:node="merge">
    <meta-data
        android:name="dev.jasonpearson.mcpandroidsdk.McpServerManagerInitializer"
        tools:node="remove" />
</provider>
```

## Current Implementation Status

- ✅ MCP Kotlin SDK integration
- ✅ Thread-safe singleton pattern
- ✅ Basic server lifecycle management
- ✅ Logging and error handling
- ✅ AndroidX Startup automatic initialization
- ✅ Manual initialization options
- ✅ Utility classes for convenient access
- ✅ Complete MCP Server wrapper implementation
- ✅ Android-specific tools (device info, app info, system time, memory info, battery info)
- ✅ MCP SDK feature integration (tools, resources, prompts)
- ✅ Graceful fallback when SDK integration fails
- ✅ Reflection-based SDK integration for import conflict resolution
- ✅ WebSocket transport for network communication
- ✅ HTTP/SSE transport for web-based communication
- ❌ STDIO transport (not feasible on Android platform)
- ✅ Helper methods for adding tools, resources, and prompts
- ✅ Android-specific lifecycle management

## Next Steps

1. Implement WebSocket transport for adb communication
2. HTTP/SSE transport for web-based communication
3. File system resources with proper Android permissions
4. Database resources for app data access
5. Sample app with working MCP server examples
6. Documentation and integration guides

## Complete MCP Server Wrapper

The library now includes a complete MCP Server wrapper implementation that provides:

### Core Features

- **Full MCP Kotlin SDK Integration**: Seamless integration with v0.5.0
- **Android-Optimized Tools**: Built-in tools for device info, app info, system time, memory, and
  battery
- **Thread-Safe Management**: Singleton pattern with proper lifecycle management
- **Graceful Fallback**: Continues working even if SDK integration fails
- **Comprehensive Error Handling**: Robust error management and detailed logging

### Built-in Android Tools

1. **device_info**: Get comprehensive Android device information
2. **app_info**: Retrieve application details and metadata
3. **system_time**: Get current system time in various formats
4. **memory_info**: Access system and app memory usage statistics
5. **battery_info**: Monitor battery status, level, and health

### Advanced Features

- **SDK Integration Check**: `hasSDKIntegration()` to verify MCP SDK availability
- **Reflection-Based Fallback**: Handles import conflicts gracefully
- **MCP Protocol Support**: Full support for tools, resources, and prompts
- **Comprehensive Information**: Detailed server status and capabilities

### Usage Examples

#### Automatic Initialization (Recommended)

```kotlin
class MyActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Server automatically initialized via AndroidX Startup
        if (McpStartup.isInitialized()) {
            val manager = McpStartup.getManager()
            
            // Start the server
            lifecycleScope.launch {
                manager.startServer()
            }
            
            // Use built-in tools
            val result = manager.executeAndroidTool("device_info", emptyMap())
            Log.i("MCP", "Device info: ${result.result}")
        }
    }
}
```

#### Manual Initialization with Custom Configuration

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
            
            // Add custom tool
            manager.addAndroidTool(
                AndroidTool(
                    name = "custom_action",
                    description = "Perform a custom action",
                    parameters = mapOf("action" to "string")
                ) { context, arguments ->
                    "Custom action executed: ${arguments["action"]}"
                }
            )
        }
    }
}
```

For complete implementation details,
see [docs/COMPLETE_MCP_SERVER_WRAPPER.md](docs/COMPLETE_MCP_SERVER_WRAPPER.md).

## MCP Resources

- [MCP Kotlin SDK Repository](https://github.com/modelcontextprotocol/kotlin-sdk)
- [Model Context Protocol Documentation](https://modelcontextprotocol.io)
- [MCP Specification](https://modelcontextprotocol.io/specification)
- [AndroidX Startup Documentation](https://developer.android.com/topic/libraries/app-startup)

