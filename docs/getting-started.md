# Getting Started

This guide will help you set up the Android MCP SDK in your Android project and get your first MCP
server running.

**⚠️ This library is intended for debug builds and development environments only. Do not include in
production apps.**

## Prerequisites

- Android Studio Arctic Fox or later
- Minimum SDK 29 (Android 10)
- Kotlin 1.9.0 or later

## Installation

### Gradle Setup

Add the library to your app module's `build.gradle.kts`:

```kotlin
dependencies {
    debugImplementation("dev.jasonpearson:mcp-android-sdk:1.0.0")  // Debug builds only
}
```

Then in your `build.gradle.kts`:

```kotlin
dependencies {
    debugImplementation(libs.mcp.android.sdk)
}
```

## Quick Setup

### Option 1: Automatic Initialization and Startup

The simplest way to get started - the library automatically initializes and starts the server
without any code needed. Since this is intended for debug variants only, it's less common you'd want
this SDK to be deferred to some DI framework startup initializer.

```kotlin
// No code needed! Just add the dependency and the MCP server
// automatically initializes and starts via AndroidX Startup
```

**📱 [See Complete Example →](../samples/simple/)**

The [Simple Sample](../samples/simple/) demonstrates:

- Zero-configuration setup
- Automatic initialization and startup
- Basic custom tools
- Transport testing

### Option 2: Deferred startup, probably via Dependency Injection

For apps using Hilt, integrate MCP through dependency injection for better testability
and configuration management.

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object McpModule {
    @Provides @Singleton
    fun provideMcpServerManager(@ApplicationContext context: Context): McpServerManager {
        return McpStartup.initializeManually(context)
    }
}
```

**🔧 [See Complete Implementation →](../samples/hilt-integration/)**

The [Hilt Integration Sample](../samples/hilt-integration/) demonstrates:
- Manual initialization via Hilt DI
- Configuration management through dependency injection
- Debug environment patterns
- Easy testing and mocking

### Option 3: Other DI Frameworks

Similar patterns work with Koin, Dagger, and other dependency injection frameworks.

**🏗️ More DI samples coming soon!**

## Sample Applications

Instead of maintaining potentially outdated code examples in this documentation, we provide working
sample applications that you can build, run, and learn from:

| Sample                                               | Purpose               | Target Use Case         |
|------------------------------------------------------|-----------------------|-------------------------|
| **[Simple](../samples/simple/)**                     | Basic automatic setup | Prototypes, simple apps |
| **[Hilt Integration](../samples/hilt-integration/)** | Debug DI patterns     | Debug builds with Hilt  |

### Building and Running Samples

```bash
# Build all samples
./gradlew assembleDebug

# Install and test a specific sample
./gradlew :samples:simple:installDebug
adb logcat | grep MCP
```

## First Steps

### Check Server Status

After setup, verify everything is working:

```kotlin
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Check if MCP is initialized
        if (McpStartup.isInitialized()) {
            val manager = McpStartup.getManager()
            Log.d("MCP", "Server running: ${manager.isServerRunning()}")
        }
    }
}
```

**📖 [See complete MainActivity examples in samples →](../samples/)**

### Test Built-in Tools

The SDK includes several built-in Android tools:

```kotlin
lifecycleScope.launch {
    val manager = McpStartup.getManager()
    val deviceInfo = manager.executeAndroidTool("device_info", emptyMap())
    Log.d("MCP", "Device info: ${deviceInfo.result}")
}
```

## Testing Your Setup

### Using adb Port Forwarding

```bash
# Forward the default MCP ports
adb forward tcp:8080 tcp:8080  # WebSocket
adb forward tcp:8081 tcp:8081  # HTTP/SSE

# Test the HTTP endpoint
curl http://localhost:8081/mcp/status
```

### Sample Apps for Testing

The samples include comprehensive testing features:

```bash
# Build and install any sample
./gradlew :samples:simple:assembleDebug
./gradlew :samples:simple:installDebug

# Check logs for MCP initialization and sample app behavior
adb logcat | grep -E "(MCP|SampleMcp|HiltMcp)"
```

**🔍 [Complete testing guide in samples →](../samples/README.md)**

## Configuration Options

### Automatic Configuration

By default, the library automatically configures AndroidX Startup. No manifest changes needed.

### Disabling Automatic Initialization

To disable automatic initialization (for DI framework integration):

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

**📋 [See complete manifest examples in samples →](../samples/)**

## Common Issues

### Initialization Fails

1. **Minimum SDK**: Ensure your app targets API 29 or higher
2. **AndroidX Startup**: Make sure AndroidX Startup is properly configured
3. **Permissions**: Check if any additional permissions are needed

### Server Won't Start

1. **Check logs**: Look for error messages in Logcat
2. **Port conflicts**: Ensure ports 8080 and 8081 aren't being used by other apps
3. **Lifecycle state**: Make sure your app is in the foreground

### Transport Issues

1. **Port forwarding**: Verify adb port forwarding is set up correctly
2. **Firewall**: Check if any firewall is blocking the connections
3. **Network**: Ensure your development machine can reach the Android device

**🔧 [See troubleshooting examples in samples →](../samples/README.md)**

## Next Steps

Once you have the basic setup working:

1. **[Choose a Sample App](../samples/)** - Find the pattern that fits your use case
2. **[Add Custom Tools](usage.md#adding-custom-tools)** - Create your own MCP tools
3. **[Configure Resources](usage.md#adding-custom-resources)** - Expose app data as MCP resources
4. **[Set Up Transports](transport.md)** - Configure WebSocket and HTTP transports
5. **[Explore API Reference](api-reference.md)** - Complete API documentation

## Dependencies

The library includes these dependencies automatically:

- `io.modelcontextprotocol:kotlin-sdk:0.5.0` - Main MCP Kotlin SDK
- `io.modelcontextprotocol:kotlin-sdk-jvm:0.5.0` - JVM-specific implementation
- `androidx.startup:startup-runtime:1.2.0` - AndroidX Startup for initialization
