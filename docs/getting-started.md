# Getting Started

This guide will help you set up the Android MCP SDK in your Android project and get your first MCP
server running.

## Prerequisites

- Android Studio Arctic Fox or later
- Minimum SDK 29 (Android 10)
- Kotlin 1.9.0 or later

## Installation

### Gradle Setup

Add the library to your app's `build.gradle.kts`:

```kotlin
dependencies {
    implementation("dev.jasonpearson:mcp-android-sdk:1.0.0")
}
```

Or if using Groovy:

```groovy
dependencies {
    implementation 'dev.jasonpearson:mcp-android-sdk:1.0.0'
}
```

### Version Catalog (Recommended)

Add to your `gradle/libs.versions.toml`:

```toml
[versions]
mcpAndroidSdk = "1.0.0"

[libraries]
mcp-android-sdk = { module = "dev.jasonpearson:mcp-android-sdk", version.ref = "mcpAndroidSdk" }
```

Then in your `build.gradle.kts`:

```kotlin
dependencies {
    implementation(libs.mcp.android.sdk)
}
```

## Quick Setup

### Option 1: Automatic Initialization (Recommended)

The simplest way to get started is to let the library automatically initialize itself:

```kotlin
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // MCP server is automatically initialized via AndroidX Startup
        if (McpStartup.isInitialized()) {
            val manager = McpStartup.getManager()
            
            // Start the server when app starts
            manager.startServerAsync()
            
            Log.i("MCP", "MCP Server started automatically")
        }
    }
}
```

### Option 2: Manual Initialization

If you need more control over when the server initializes:

```kotlin
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Manual initialization
        val manager = McpStartup.initializeManually(this)
        
        // Start the server
        lifecycleScope.launch {
            manager.startServer().getOrThrow()
            Log.i("MCP", "MCP Server started manually")
        }
    }
}
```

### Option 3: Custom Configuration

For advanced configuration:

```kotlin
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        val result = McpStartup.initializeWithCustomConfig(
            context = this,
            serverName = "My Android App MCP Server",
            serverVersion = "1.0.0"
        )
        
        result.fold(
            onSuccess = { manager ->
                Log.i("MCP", "Custom MCP server initialized")
                
                // Configure lifecycle management
                manager.initializeLifecycleManagement(
                    application = this,
                    config = McpLifecycleManager.LifecycleConfig(
                        autoStartOnAppStart = true,
                        autoStopOnAppStop = false,
                        restartOnAppReturn = true
                    )
                )
                
                // Start server
                manager.startServerAsync()
            },
            onFailure = { exception ->
                Log.e("MCP", "Failed to initialize MCP server", exception)
            }
        )
    }
}
```

## Manifest Configuration

### Automatic Configuration

By default, the library automatically configures AndroidX Startup. No manifest changes are needed.

### Custom Manifest Configuration

If you need to customize the initialization:

```xml
<application>
    <provider
        android:name="androidx.startup.InitializationProvider"
        android:authorities="${applicationId}.androidx-startup"
        android:exported="false"
        tools:node="merge">
        <meta-data
            android:name="dev.jasonpearson.mcpandroidsdk.McpServerManagerInitializer"
            android:value="androidx.startup" />
    </provider>
</application>
```

### Disabling Automatic Initialization

To disable automatic initialization:

```xml
<application>
    <provider
        android:name="androidx.startup.InitializationProvider"
        android:authorities="${applicationId}.androidx-startup"
        android:exported="false"
        tools:node="merge">
        <meta-data
            android:name="dev.jasonpearson.mcpandroidsdk.McpServerManagerInitializer"
            tools:node="remove" />
    </provider>
</application>
```

## First Steps

### Check Server Status

After initialization, you can check if everything is working:

```kotlin
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Check if MCP is initialized
        if (McpStartup.isInitialized()) {
            val manager = McpStartup.getManager()
            
            Log.d("MCP", "Server running: ${manager.isServerRunning()}")
            Log.d("MCP", "SDK version: ${manager.getMcpSdkVersion()}")
            
            // Get transport information
            val transportInfo = manager.getTransportInfo()
            Log.d("MCP", "Transport info: $transportInfo")
        }
    }
}
```

### Test Built-in Tools

The SDK comes with several built-in Android tools you can test:

```kotlin
lifecycleScope.launch {
    val manager = McpStartup.getManager()
    
    // Test device info tool
    val deviceInfo = manager.executeAndroidTool("device_info", emptyMap())
    Log.d("MCP", "Device info: ${deviceInfo.result}")
    
    // Test app info tool
    val appInfo = manager.executeAndroidTool("app_info", emptyMap())
    Log.d("MCP", "App info: ${appInfo.result}")
}
```

## Testing Your Setup

### Using adb Port Forwarding

To test your MCP server from your development machine:

```bash
# Forward the default MCP ports
adb forward tcp:8080 tcp:8080  # WebSocket
adb forward tcp:8081 tcp:8081  # HTTP/SSE

# Test the HTTP endpoint
curl http://localhost:8081/mcp/status

# Test WebSocket connection (using a WebSocket client)
# Connect to: ws://localhost:8080/mcp
```

### Using the Sample App

The repository includes a sample app that demonstrates all features:

```bash
# Build and install the sample app
./gradlew :sample:assembleDebug
./gradlew :sample:installDebug

# Check logs for MCP initialization
adb logcat | grep MCP
```

## Common Issues

### Initialization Fails

If initialization fails, check:

1. **Minimum SDK**: Ensure your app targets API 29 or higher
2. **AndroidX Startup**: Make sure AndroidX Startup is properly configured
3. **Permissions**: Check if any additional permissions are needed

### Server Won't Start

If the server won't start:

1. **Check logs**: Look for error messages in Logcat
2. **Port conflicts**: Ensure ports 8080 and 8081 aren't being used by other apps
3. **Lifecycle state**: Make sure your app is in the foreground

### Transport Issues

If you can't connect to the server:

1. **Port forwarding**: Verify adb port forwarding is set up correctly
2. **Firewall**: Check if any firewall is blocking the connections
3. **Network**: Ensure your development machine can reach the Android device

## Next Steps

Once you have the basic setup working:

1. **[Add Custom Tools](usage.md#adding-custom-tools)** - Create your own MCP tools
2. **[Configure Resources](usage.md#adding-custom-resources)** - Expose app data as MCP resources
3. **[Set Up Transports](transport.md)** - Configure WebSocket and HTTP transports
4. **[Explore Examples](api-reference.md)** - See comprehensive usage examples

## Integration Status

Current integration features:

- ✅ **MCP Kotlin SDK**: Version 0.5.0 integrated
- ✅ **Dependencies**: Both main and JVM artifacts included
- ✅ **Project Structure**: Kotlin sources organized properly
- ✅ **Compilation**: Library compiles successfully
- ✅ **Singleton Manager**: Thread-safe singleton implementation
- ✅ **AndroidX Startup**: Full automatic initialization support

## Dependencies

The library includes these dependencies automatically:

- `io.modelcontextprotocol:kotlin-sdk:0.5.0` - Main MCP Kotlin SDK
- `io.modelcontextprotocol:kotlin-sdk-jvm:0.5.0` - JVM-specific implementation
- `androidx.startup:startup-runtime:1.2.0` - AndroidX Startup for initialization