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

// Async start (non-blocking)
manager.startServerAsync()

// Or with coroutines for better control
lifecycleScope.launch {
    manager.startServer().getOrThrow()
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
- ⏳ Complete MCP Server wrapper implementation
- ⏳ STDIO transport configuration for adb communication
- ⏳ Helper methods for adding tools, resources, and prompts
- ⏳ Android-specific lifecycle management

## Next Steps

1. Full MCP Server wrapper with proper transport configuration
2. Helper APIs for registering tools, resources, and prompts
3. Sample app with working MCP server examples
4. STDIO transport for adb communication
5. Documentation and integration guides

## MCP Resources

- [MCP Kotlin SDK Repository](https://github.com/modelcontextprotocol/kotlin-sdk)
- [Model Context Protocol Documentation](https://modelcontextprotocol.io)
- [MCP Specification](https://modelcontextprotocol.io/specification)
- [AndroidX Startup Documentation](https://developer.android.com/topic/libraries/app-startup)
