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
✅ **AndroidX Startup Ready**: Infrastructure ready for AndroidX Startup integration

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

### Basic Initialization

Initialize the MCP server in your Application class:

```kotlin
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Initialize the MCP server manager
        McpServerManager.getInstance().initialize(this)
        
        // Check SDK version
        val version = McpServerManager.getInstance().getMcpSdkVersion()
        Log.i("MCP", "SDK Version: $version")
    }
}
```

### Starting the MCP Server

```kotlin
// Start the server (this blocks, so run in background thread)
Thread {
    McpServerManager.getInstance().startServer()
}.start()
```

### Checking Server Status

```kotlin
val isReady = McpServerManager.getInstance().isInitialized()
```

## Architecture

The library provides a clean architecture:

- **`McpAndroidServer`**: Core wrapper around MCP Kotlin SDK
- **`McpServerManager`**: Thread-safe singleton for managing server lifecycle
- **`ExampleMcpApplication`**: Reference implementation for proper initialization

## Building

- Build the library: `./gradlew :lib:compileDebug`
- Build the sample app: `./gradlew :sample:assembleDebug`

## AndroidX Startup Integration

The library is ready for AndroidX Startup integration. To enable automatic initialization, you would
add to your `AndroidManifest.xml`:

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

## Current Implementation Status

- ✅ MCP Kotlin SDK integration
- ✅ Thread-safe singleton pattern
- ✅ Basic server lifecycle management
- ✅ Logging and error handling
- ⏳ AndroidX Startup automatic initialization
- ⏳ Complete MCP Server wrapper implementation
- ⏳ STDIO transport configuration for adb communication
- ⏳ Helper methods for adding tools, resources, and prompts
- ⏳ Android-specific lifecycle management

## Next Steps

1. Complete AndroidX Startup initializer implementation
2. Full MCP Server wrapper with proper transport configuration
3. Helper APIs for registering tools, resources, and prompts
4. Sample app with working MCP server examples
5. Documentation and integration guides

## MCP Resources

- [MCP Kotlin SDK Repository](https://github.com/modelcontextprotocol/kotlin-sdk)
- [Model Context Protocol Documentation](https://modelcontextprotocol.io)
- [MCP Specification](https://modelcontextprotocol.io/specification)
- [AndroidX Startup Documentation](https://developer.android.com/topic/libraries/app-startup)
