# 14 - Integration Guides Documentation

## Status: `[ ]` Not Started

## Objective

Create comprehensive integration guides and documentation for the Android MCP SDK to help developers
understand, integrate, and effectively use the SDK in their Android applications. The documentation
should cover everything from basic setup to advanced usage patterns and troubleshooting.

## Requirements

### Documentation Scope

- **Getting Started Guide**: Quick setup and first integration
- **Architecture Overview**: Understanding the SDK design and components
- **API Reference**: Complete API documentation with examples
- **Integration Patterns**: Common use cases and implementation patterns
- **Performance Guide**: Optimization tips and best practices
- **Troubleshooting Guide**: Common issues and solutions
- **Advanced Topics**: Custom tools, resources, transport configuration
- **Migration Guide**: Upgrading between SDK versions

### Technical Requirements

- **Multi-Format**: Markdown, HTML, and in-code documentation
- **Interactive Examples**: Code samples that can be copied and run
- **Visual Aids**: Architecture diagrams, flow charts, and screenshots
- **Searchable**: Well-organized with clear navigation
- **Version Control**: Documentation versioning alongside code
- **Mobile-Friendly**: Accessible on mobile devices

### Quality Standards

- **Accuracy**: All examples tested and verified
- **Completeness**: Cover all public APIs and features
- **Clarity**: Written for developers of all skill levels
- **Currency**: Kept up-to-date with SDK changes
- **Accessibility**: Following accessibility guidelines

## Dependencies

**Must Complete First:**

- All previous tasks (01-13) should be completed for comprehensive documentation

**Should Reference:**

- [09-integration-testing-suite.md](09-integration-testing-suite.md) - Testing examples
- [12-builtin-tools-validation.md](12-builtin-tools-validation.md) - Tool validation
- [13-sample-app-enhancement.md](13-sample-app-enhancement.md) - Sample app examples

## Implementation Steps

### Phase 1: Documentation Structure and Foundation

#### Step 1.1: Create Documentation Architecture

Create `docs/` directory structure:

```
docs/
├── README.md                          # Main documentation index
├── getting-started/
│   ├── README.md                      # Getting started overview
│   ├── installation.md               # Installation and setup
│   ├── quick-start.md                # First integration tutorial
│   └── basic-usage.md                # Basic usage examples
├── architecture/
│   ├── README.md                      # Architecture overview
│   ├── sdk-components.md             # SDK component breakdown
│   ├── mcp-protocol.md               # MCP protocol explanation
│   └── android-integration.md        # Android-specific architecture
├── api-reference/
│   ├── README.md                      # API reference index
│   ├── server-manager.md             # McpServerManager API
│   ├── android-server.md             # McpAndroidServer API
│   ├── tools.md                      # Tools API reference
│   ├── resources.md                  # Resources API reference
│   ├── prompts.md                    # Prompts API reference
│   └── transport.md                  # Transport API reference
├── guides/
│   ├── README.md                      # Guides overview
│   ├── integration-patterns.md       # Common integration patterns
│   ├── custom-tools.md               # Creating custom tools
│   ├── custom-resources.md           # Creating custom resources
│   ├── transport-configuration.md    # Transport configuration
│   ├── lifecycle-management.md       # App lifecycle integration
│   ├── performance-optimization.md   # Performance best practices
│   └── security-considerations.md    # Security guidelines
├── troubleshooting/
│   ├── README.md                      # Troubleshooting overview
│   ├── common-issues.md              # Common problems and solutions
│   ├── debugging.md                  # Debugging techniques
│   ├── performance-issues.md         # Performance troubleshooting
│   └── faq.md                        # Frequently asked questions
├── examples/
│   ├── README.md                      # Examples overview
│   ├── basic-integration/            # Basic integration example
│   ├── custom-tools-example/         # Custom tools example
│   ├── file-resources-example/       # File resources example
│   └── full-featured-app/            # Complete application example
├── advanced/
│   ├── README.md                      # Advanced topics overview
│   ├── custom-transport.md           # Custom transport implementation
│   ├── protocol-extensions.md        # Extending MCP protocol
│   ├── testing-strategies.md         # Testing approaches
│   └── deployment-strategies.md      # Production deployment
└── assets/
    ├── images/                        # Documentation images
    ├── diagrams/                      # Architecture diagrams
    └── videos/                        # Tutorial videos
```

#### Step 1.2: Main Documentation Index

Create `docs/README.md`:

```markdown
# Android MCP SDK Documentation

Welcome to the comprehensive documentation for the Android MCP SDK. This SDK enables Android applications to host Model Context Protocol (MCP) servers, allowing external clients to interact with your app's tools, resources, and prompts.

## 🚀 Quick Start

- **[Installation](getting-started/installation.md)** - Add the SDK to your project
- **[Quick Start Guide](getting-started/quick-start.md)** - Get running in 5 minutes
- **[Sample App](examples/basic-integration/)** - See a working example

## 📚 Documentation Sections

### Getting Started
- [Installation & Setup](getting-started/installation.md)
- [Quick Start Tutorial](getting-started/quick-start.md)
- [Basic Usage](getting-started/basic-usage.md)

### Architecture & Concepts
- [SDK Architecture](architecture/sdk-components.md)
- [MCP Protocol Overview](architecture/mcp-protocol.md)
- [Android Integration](architecture/android-integration.md)

### API Reference
- [McpServerManager](api-reference/server-manager.md)
- [Tools API](api-reference/tools.md)
- [Resources API](api-reference/resources.md)
- [Prompts API](api-reference/prompts.md)
- [Transport API](api-reference/transport.md)

### Integration Guides
- [Integration Patterns](guides/integration-patterns.md)
- [Custom Tools](guides/custom-tools.md)
- [Custom Resources](guides/custom-resources.md)
- [Lifecycle Management](guides/lifecycle-management.md)
- [Performance Optimization](guides/performance-optimization.md)

### Examples
- [Basic Integration](examples/basic-integration/)
- [Custom Tools Example](examples/custom-tools-example/)
- [File Resources Example](examples/file-resources-example/)
- [Full-Featured App](examples/full-featured-app/)

### Troubleshooting
- [Common Issues](troubleshooting/common-issues.md)
- [Debugging Guide](troubleshooting/debugging.md)
- [FAQ](troubleshooting/faq.md)

### Advanced Topics
- [Custom Transport](advanced/custom-transport.md)
- [Protocol Extensions](advanced/protocol-extensions.md)
- [Testing Strategies](advanced/testing-strategies.md)

## 🔗 Quick Links

- **[GitHub Repository](https://github.com/your-org/android-mcp-sdk)**
- **[Sample Application](https://github.com/your-org/android-mcp-sdk/tree/main/sample)**
- **[Issues & Support](https://github.com/your-org/android-mcp-sdk/issues)**
- **[Changelog](CHANGELOG.md)**

## 📋 Requirements

- **Android API Level**: 21+ (Android 5.0+)
- **Kotlin**: 1.8.0+
- **AndroidX**: Required
- **Network Permissions**: For transport layer

## 🎯 What is MCP?

The Model Context Protocol (MCP) is an open standard for connecting AI systems with data sources and tools. The Android MCP SDK allows your Android app to:

- **Expose Tools**: Let AI systems call functions in your app
- **Share Resources**: Provide file-like data to external systems
- **Offer Prompts**: Supply pre-created prompt templates
- **Enable Integration**: Connect with Claude Desktop, other MCP clients

## 💡 Use Cases

- **Developer Tools**: Expose device information and debugging capabilities
- **Content Apps**: Share app data with AI assistants
- **IoT Control**: Allow AI systems to control connected devices
- **Enterprise Integration**: Connect mobile apps to AI workflows
- **Educational Tools**: Provide learning resources to AI tutors

## 🚀 Get Started Now

```kotlin
// 1. Add dependency to your build.gradle.kts
implementation("dev.jasonpearson:mcp-android-sdk:1.0.0")

// 2. Initialize in your Application class
class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Automatic initialization via AndroidX Startup
        // No additional setup required!
    }
}

// 3. Start the server
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        lifecycleScope.launch {
            val manager = McpStartup.getManager()
            manager.startServer()
            
            // Your MCP server is now running!
            // Clients can connect via adb port forwarding
        }
    }
}
```

## 📞 Support

Need help? Here are your options:

- **📖 Documentation**: Check our comprehensive guides
- **💬 Discussions**: [GitHub Discussions](https://github.com/your-org/android-mcp-sdk/discussions)
- **🐛 Issues**: [Report bugs](https://github.com/your-org/android-mcp-sdk/issues)
- **📧 Email**: support@yourorg.com

---

*This documentation is always improving. Found an issue or have a
suggestion? [Contribute on GitHub](https://github.com/your-org/android-mcp-sdk/blob/main/CONTRIBUTING.md)!*

```

### Phase 2: Getting Started Documentation

#### Step 2.1: Installation Guide
Create `docs/getting-started/installation.md`:

```markdown
# Installation & Setup

This guide walks you through adding the Android MCP SDK to your project and performing initial setup.

## Prerequisites

Before installing the Android MCP SDK, ensure your project meets these requirements:

- **Android API Level**: 21+ (Android 5.0 Lollipop)
- **Kotlin**: 1.8.0 or later
- **Gradle**: 7.0 or later
- **AndroidX**: Your project should use AndroidX libraries

## Installation Methods

### Method 1: Gradle Dependency (Recommended)

Add the Android MCP SDK to your app's `build.gradle.kts` file:

```kotlin
dependencies {
    implementation("dev.jasonpearson:mcp-android-sdk:1.0.0")
}
```

Or in Groovy `build.gradle`:

```groovy
dependencies {
    implementation 'dev.jasonpearson:mcp-android-sdk:1.0.0'
}
```

### Method 2: Local Library Module

If you're working with the source code or want to include the SDK as a local module:

1. Clone the repository:

```bash
git clone https://github.com/your-org/android-mcp-sdk.git
```

2. Include the library module in your `settings.gradle.kts`:

```kotlin
include(":lib")
project(":lib").projectDir = File("path/to/android-mcp-sdk/lib")
```

3. Add the dependency:

```kotlin
dependencies {
    implementation(project(":lib"))
}
```

## Permissions

The SDK requires network permissions for the transport layer. Add these to your
`AndroidManifest.xml`:

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    
    <!-- Required for network communication -->
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    
    <!-- Optional: For wake lock during server operation -->
    <uses-permission android:name="android.permission.WAKE_LOCK" />
    
    <application>
        <!-- Your app components -->
    </application>
</manifest>
```

## Automatic Initialization

The SDK uses AndroidX Startup for automatic initialization. This is enabled by default when you
include the SDK dependency.

### Verifying Automatic Initialization

The SDK is automatically initialized when your app starts. You can verify this in your main
activity:

```kotlin
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Check if SDK is initialized
        if (McpStartup.isInitialized()) {
            val manager = McpStartup.getManager()
            Log.i("MCP", "SDK Version: ${manager.getMcpSdkVersion()}")
        } else {
            Log.w("MCP", "SDK not initialized automatically")
        }
    }
}
```

### Disabling Automatic Initialization

If you prefer manual control over initialization, you can disable automatic startup:

```xml
<!-- In your AndroidManifest.xml -->
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

## Manual Initialization (Advanced)

For advanced use cases, you can manually initialize the SDK:

```kotlin
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Manual initialization
        val result = McpStartup.initializeWithCustomConfig(
            context = this,
            serverName = "My Custom MCP Server",
            serverVersion = "2.0.0"
        )
        
        result.fold(
            onSuccess = { manager ->
                Log.i("MCP", "Custom initialization successful")
            },
            onFailure = { exception ->
                Log.e("MCP", "Initialization failed", exception)
            }
        )
    }
}
```

## Verification

To verify your installation is working correctly:

1. **Build your project** - Ensure there are no compilation errors
2. **Check logs** - Look for MCP initialization messages
3. **Run the sample** - Try the basic integration example

```kotlin
// Simple verification code
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        lifecycleScope.launch {
            try {
                val manager = McpStartup.getManager()
                val isRunning = manager.isServerRunning()
                Log.i("MCP", "Server running: $isRunning")
                
                // List available tools
                val tools = manager.getAvailableTools()
                Log.i("MCP", "Available tools: $tools")
                
            } catch (e: Exception) {
                Log.e("MCP", "Error during verification", e)
            }
        }
    }
}
```

## Troubleshooting Installation

### Common Issues

**Build Error: "Cannot resolve dependency"**

- Ensure you have the correct repository configured
- Check that your Gradle version supports the SDK
- Verify network connectivity for dependency download

**Runtime Error: "Class not found"**

- Ensure ProGuard/R8 rules don't obfuscate SDK classes
- Check that you're using the correct dependency version

**AndroidX Migration Required**

- The SDK requires AndroidX libraries
- Follow the [AndroidX migration guide](https://developer.android.com/jetpack/androidx/migrate)

### ProGuard/R8 Configuration

If you're using code obfuscation, add these rules to your `proguard-rules.pro`:

```proguard
# Android MCP SDK
-keep class dev.jasonpearson.mcpandroidsdk.** { *; }
-keep interface dev.jasonpearson.mcpandroidsdk.** { *; }

# MCP Protocol classes
-keep class io.modelcontextprotocol.** { *; }

# Reflection usage
-keepattributes Signature
-keepattributes *Annotation*
```

## Next Steps

Now that you have the SDK installed:

1. **[Quick Start Guide](quick-start.md)** - Create your first MCP server
2. **[Basic Usage](basic-usage.md)** - Learn the fundamental concepts
3. **[Sample App](../examples/basic-integration/)** - Explore a working example

## Version History

| Version | Release Date | Key Features |
|---------|--------------|--------------|
| 1.0.0   | 2024-01-XX   | Initial release with basic MCP support |
| 0.9.0   | 2024-01-XX   | Beta release with transport layer |
| 0.8.0   | 2024-01-XX   | Alpha release with core functionality |

For detailed changes, see the [Changelog](../CHANGELOG.md).

```

#### Step 2.2: Quick Start Guide
Create `docs/getting-started/quick-start.md`:

```markdown
# Quick Start Guide

Get your first MCP server running in just 5 minutes! This guide covers the absolute basics to get you started quickly.

## Step 1: Add the SDK

Add the dependency to your `build.gradle.kts`:

```kotlin
dependencies {
    implementation("dev.jasonpearson:mcp-android-sdk:1.0.0")
}
```

## Step 2: Start the Server

In your main activity, start the MCP server:

```kotlin
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // Start MCP server
        lifecycleScope.launch {
            val manager = McpStartup.getManager()
            val result = manager.startServer()
            
            if (result.isSuccess) {
                Log.i("MCP", "Server started successfully!")
                showStatus("MCP Server is running")
            } else {
                Log.e("MCP", "Failed to start server")
                showStatus("Failed to start MCP server")
            }
        }
    }
    
    private fun showStatus(message: String) {
        // Update your UI to show the status
        findViewById<TextView>(R.id.statusText)?.text = message
    }
}
```

## Step 3: Test with Built-in Tools

The SDK includes several built-in tools you can test immediately:

```kotlin
// Test the device_info tool
lifecycleScope.launch {
    val manager = McpStartup.getManager()
    val result = manager.executeAndroidTool("device_info", emptyMap())
    
    if (result.success) {
        Log.i("MCP", "Device info: ${result.result}")
    } else {
        Log.e("MCP", "Tool execution failed: ${result.error}")
    }
}
```

## Step 4: Connect External Client

1. **Setup ADB port forwarding**:

```bash
adb forward tcp:8080 tcp:8080  # WebSocket
adb forward tcp:8081 tcp:8081  # HTTP/SSE
```

2. **Test with curl**:

```bash
curl -X POST http://localhost:8081/mcp/message \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 1,
    "method": "tools/list",
    "params": {}
  }'
```

3. **Expected response**:

```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "result": {
    "tools": [
      {
        "name": "device_info",
        "description": "Get Android device information"
      },
      {
        "name": "app_info", 
        "description": "Get application information"
      }
    ]
  }
}
```

## Step 5: Add a Custom Tool

Create your first custom tool:

```kotlin
// In your Activity or Application
lifecycleScope.launch {
    val manager = McpStartup.getManager()
    
    // Add a simple tool
    manager.addSimpleTool(
        name = "get_time",
        description = "Get current timestamp",
        parameters = emptyMap()
    ) { _ ->
        "Current time: ${System.currentTimeMillis()}"
    }
    
    Log.i("MCP", "Custom tool added!")
}
```

Test your custom tool:

```bash
curl -X POST http://localhost:8081/mcp/message \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 2,
    "method": "tools/call",
    "params": {
      "name": "get_time",
      "arguments": {}
    }
  }'
```

## Complete Quick Start Example

Here's a complete MainActivity that demonstrates the basics:

```kotlin
class MainActivity : AppCompatActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        setupMcpServer()
    }
    
    private fun setupMcpServer() {
        lifecycleScope.launch {
            val manager = McpStartup.getManager()
            
            // Start the server
            val startResult = manager.startServer()
            if (!startResult.isSuccess) {
                showError("Failed to start MCP server")
                return@launch
            }
            
            // Add custom tools
            addCustomTools(manager)
            
            // Show server info
            showServerInfo(manager)
            
            Log.i("MCP", "MCP server setup complete!")
        }
    }
    
    private suspend fun addCustomTools(manager: McpServerManager) {
        // Simple timestamp tool
        manager.addSimpleTool(
            name = "timestamp",
            description = "Get current Unix timestamp",
            parameters = emptyMap()
        ) { _ ->
            System.currentTimeMillis().toString()
        }
        
        // Simple calculator
        manager.addSimpleTool(
            name = "add",
            description = "Add two numbers",
            parameters = mapOf(
                "a" to "number",
                "b" to "number"
            )
        ) { args ->
            val a = (args["a"] as? Number)?.toDouble() ?: 0.0
            val b = (args["b"] as? Number)?.toDouble() ?: 0.0
            (a + b).toString()
        }
    }
    
    private suspend fun showServerInfo(manager: McpServerManager) {
        val info = manager.getServerInfo()
        val tools = manager.getAvailableTools()
        
        val statusText = """
            MCP Server Running
            
            Server: ${info.name} v${info.version}
            SDK: ${manager.getMcpSdkVersion()}
            Tools: ${tools.size} available
            
            Available Tools:
            ${tools.joinToString("\n") { "• $it" }}
            
            Connect via:
            • WebSocket: ws://localhost:8080/mcp
            • HTTP: http://localhost:8081/mcp/*
        """.trimIndent()
        
        findViewById<TextView>(R.id.statusText)?.text = statusText
    }
    
    private fun showError(message: String) {
        findViewById<TextView>(R.id.statusText)?.text = "Error: $message"
        Log.e("MCP", message)
    }
}
```

## Layout File

Add this simple layout to `res/layout/activity_main.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:padding="16dp">
    
    <TextView
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="Android MCP SDK Quick Start"
        android:textSize="24sp"
        android:textStyle="bold"
        android:gravity="center"
        android:layout_marginBottom="16dp" />
    
    <ScrollView
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1">
        
        <TextView
            android:id="@+id/statusText"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:text="Starting MCP server..."
            android:textSize="14sp"
            android:fontFamily="monospace"
            android:padding="8dp"
            android:background="#f5f5f5" />
    </ScrollView>
    
</LinearLayout>
```

## What's Next?

🎉 **Congratulations!** You now have a working MCP server running on your Android device.

**Next steps:**

- **[Basic Usage Guide](basic-usage.md)** - Learn core concepts
- **[Custom Tools Guide](../guides/custom-tools.md)** - Create more sophisticated tools
- **[Integration Patterns](../guides/integration-patterns.md)** - Learn best practices
- **[Sample App](../examples/basic-integration/)** - Explore a complete example

**Test your server:**

- Try connecting with Claude Desktop or other MCP clients
- Experiment with the built-in tools
- Create more custom tools for your app's specific functionality

**Need help?**

- Check the [Troubleshooting Guide](../troubleshooting/common-issues.md)
- Browse the [FAQ](../troubleshooting/faq.md)
- Join our [GitHub Discussions](https://github.com/your-org/android-mcp-sdk/discussions)

```

### Phase 3: API Reference Documentation

#### Step 3.1: Server Manager API Reference
Create `docs/api-reference/server-manager.md`:

```markdown
# McpServerManager API Reference

The `McpServerManager` is the main entry point for managing MCP server lifecycle and operations. It provides a thread-safe singleton interface for server management.

## Class Overview

```kotlin
class McpServerManager private constructor() {
    companion object {
        fun getInstance(): McpServerManager
    }
}
```

## Getting the Instance

### `getInstance()`

Returns the singleton instance of the McpServerManager.

```kotlin
val manager = McpServerManager.getInstance()
```

**Returns:** `McpServerManager` - The singleton instance

**Thread Safety:** ✅ This method is thread-safe

## Initialization Methods

### `initialize(context: Context, serverName: String, serverVersion: String)`

Initializes the MCP server with custom configuration.

```kotlin
suspend fun initialize(
    context: Context,
    serverName: String = "Android MCP Server",
    serverVersion: String = "1.0.0"
): Result<Unit>
```

**Parameters:**

- `context: Context` - Android application context
- `serverName: String` - Custom name for the MCP server (default: "Android MCP Server")
- `serverVersion: String` - Custom version for the MCP server (default: "1.0.0")

**Returns:** `Result<Unit>` - Success or failure result

**Example:**

```kotlin
lifecycleScope.launch {
    val result = manager.initialize(
        context = this@MainActivity,
        serverName = "My App MCP Server",
        serverVersion = "2.1.0"
    )
    
    result.fold(
        onSuccess = { 
            Log.i("MCP", "Server initialized successfully") 
        },
        onFailure = { error -> 
            Log.e("MCP", "Initialization failed", error) 
        }
    )
}
```

**Throws:**

- `IllegalStateException` - If the server is already initialized
- `SecurityException` - If required permissions are missing

## Server Lifecycle Methods

### `startServer()`

Starts the MCP server and begins listening for connections.

```kotlin
suspend fun startServer(): Result<Unit>
```

**Returns:** `Result<Unit>` - Success or failure result

**Example:**

```kotlin
lifecycleScope.launch {
    val result = manager.startServer()
    if (result.isSuccess) {
        Log.i("MCP", "Server started on ports 8080 (WS) and 8081 (HTTP)")
    }
}
```

**Preconditions:**

- Server must be initialized
- Network permissions must be granted

### `stopServer()`

Stops the MCP server and closes all connections.

```kotlin
suspend fun stopServer(): Result<Unit>
```

**Returns:** `Result<Unit>` - Success or failure result

**Example:**

```kotlin
lifecycleScope.launch {
    val result = manager.stopServer()
    if (result.isSuccess) {
        Log.i("MCP", "Server stopped successfully")
    }
}
```

### `startServerAsync()`

Non-blocking version of `startServer()` that includes transport initialization.

```kotlin
fun startServerAsync(): Result<Unit>
```

**Returns:** `Result<Unit>` - Immediate result of starting the async operation

**Example:**

```kotlin
val result = manager.startServerAsync()
if (result.isSuccess) {
    Log.i("MCP", "Server start initiated")
}
```

## State Query Methods

### `isInitialized()`

Checks if the MCP server has been initialized.

```kotlin
fun isInitialized(): Boolean
```

**Returns:** `Boolean` - `true` if initialized, `false` otherwise

### `isServerRunning()`

Checks if the MCP server is currently running.

```kotlin
fun isServerRunning(): Boolean
```

**Returns:** `Boolean` - `true` if running, `false` otherwise

**Example:**

```kotlin
if (manager.isInitialized() && !manager.isServerRunning()) {
    // Server is initialized but not running
    manager.startServer()
}
```

## Information Methods

### `getServerInfo()`

Gets information about the server configuration.

```kotlin
fun getServerInfo(): ServerInfo
```

**Returns:** `ServerInfo` - Object containing server details

```kotlin
data class ServerInfo(
    val name: String,
    val version: String
)
```

**Example:**

```kotlin
val info = manager.getServerInfo()
Log.i("MCP", "Server: ${info.name} v${info.version}")
```

### `getMcpSdkVersion()`

Gets the version of the MCP Kotlin SDK being used.

```kotlin
fun getMcpSdkVersion(): String
```

**Returns:** `String` - SDK version string

### `getTransportInfo()`

Gets information about the transport layer configuration.

```kotlin
fun getTransportInfo(): String
```

**Returns:** `String` - Human-readable transport information

**Example:**

```kotlin
val transportInfo = manager.getTransportInfo()
Log.i("MCP", "Transport: $transportInfo")
// Output: "WebSocket: ws://localhost:8080/mcp, HTTP/SSE: http://localhost:8081/mcp/*"
```

## Tools Management

### `getAvailableTools()`

Gets a list of all available tool names.

```kotlin
fun getAvailableTools(): List<String>
```

**Returns:** `List<String>` - List of tool names

### `executeAndroidTool(name: String, arguments: Map<String, Any>)`

Executes a tool with the given name and arguments.

```kotlin
suspend fun executeAndroidTool(
    name: String, 
    arguments: Map<String, Any> = emptyMap()
): AndroidToolResult
```

**Parameters:**

- `name: String` - Name of the tool to execute
- `arguments: Map<String, Any>` - Tool arguments (default: empty map)

**Returns:** `AndroidToolResult` - Result of tool execution

```kotlin
data class AndroidToolResult(
    val success: Boolean,
    val result: String,
    val error: String? = null
)
```

**Example:**

```kotlin
lifecycleScope.launch {
    val result = manager.executeAndroidTool("device_info")
    if (result.success) {
        Log.i("MCP", "Device info: ${result.result}")
    } else {
        Log.e("MCP", "Tool failed: ${result.error}")
    }
}
```

###
`addSimpleTool(name: String, description: String, parameters: Map<String, String>, handler: (Map<String, Any>) -> String)`

Adds a simple custom tool with a string result.

```kotlin
fun addSimpleTool(
    name: String,
    description: String,
    parameters: Map<String, String> = emptyMap(),
    handler: suspend (Map<String, Any>) -> String
)
```

**Parameters:**

- `name: String` - Unique tool name
- `description: String` - Tool description
- `parameters: Map<String, String>` - Parameter definitions (name -> type)
- `handler: suspend (Map<String, Any>) -> String` - Tool execution handler

**Example:**

```kotlin
manager.addSimpleTool(
    name = "calculate_sum",
    description = "Calculate the sum of two numbers",
    parameters = mapOf("a" to "number", "b" to "number")
) { args ->
    val a = (args["a"] as? Number)?.toDouble() ?: 0.0
    val b = (args["b"] as? Number)?.toDouble() ?: 0.0
    "Sum: ${a + b}"
}
```

## Resources Management

###
`addFileResource(uri: String, name: String, description: String, filePath: String, mimeType: String)`

Adds a file-based resource.

```kotlin
fun addFileResource(
    uri: String,
    name: String,
    description: String,
    filePath: String,
    mimeType: String = "text/plain"
)
```

**Parameters:**

- `uri: String` - Unique resource URI
- `name: String` - Human-readable resource name
- `description: String` - Resource description
- `filePath: String` - Path to the file on the device
- `mimeType: String` - MIME type of the file content

**Example:**

```kotlin
manager.addFileResource(
    uri = "app://config/settings.json",
    name = "App Settings",
    description = "Application configuration file",
    filePath = "${context.filesDir}/settings.json",
    mimeType = "application/json"
)
```

## Prompts Management

###
`addSimplePrompt(name: String, description: String, arguments: List<PromptArgument>, handler: (Map<String, Any>) -> String)`

Adds a simple prompt template.

```kotlin
fun addSimplePrompt(
    name: String,
    description: String,
    arguments: List<PromptArgument> = emptyList(),
    handler: suspend (Map<String, Any>) -> String
)
```

**Parameters:**

- `name: String` - Unique prompt name
- `description: String` - Prompt description
- `arguments: List<PromptArgument>` - Prompt arguments
- `handler: suspend (Map<String, Any>) -> String` - Prompt handler

**Example:**

```kotlin
manager.addSimplePrompt(
    name = "analyze_log",
    description = "Analyze application log file",
    arguments = listOf(
        PromptArgument(
            name = "severity",
            description = "Minimum log severity",
            required = false
        )
    )
) { args ->
    val severity = args["severity"] as? String ?: "INFO"
    "Please analyze the app logs with minimum severity: $severity"
}
```

## Lifecycle Management

###
`initializeLifecycleManagement(application: Application, config: McpLifecycleManager.LifecycleConfig)`

Initialize automatic lifecycle management.

```kotlin
fun initializeLifecycleManagement(
    application: Application,
    config: McpLifecycleManager.LifecycleConfig = McpLifecycleManager.LifecycleConfig()
)
```

**Parameters:**

- `application: Application` - The application instance
- `config: McpLifecycleManager.LifecycleConfig` - Lifecycle configuration

**Example:**

```kotlin
manager.initializeLifecycleManagement(
    application = this,
    config = McpLifecycleManager.LifecycleConfig(
        autoStartOnAppStart = true,
        autoStopOnAppStop = false,
        restartOnAppReturn = true
    )
)
```

## Error Handling

All async methods return `Result<T>` objects. Handle errors appropriately:

```kotlin
lifecycleScope.launch {
    manager.startServer().fold(
        onSuccess = {
            // Server started successfully
            updateUI("Server running")
        },
        onFailure = { error ->
            // Handle the error
            when (error) {
                is SecurityException -> {
                    // Missing permissions
                    requestPermissions()
                }
                is IllegalStateException -> {
                    // Server not initialized
                    initializeServer()
                }
                else -> {
                    // Other errors
                    Log.e("MCP", "Unexpected error", error)
                }
            }
        }
    )
}
```

## Thread Safety

The `McpServerManager` is designed to be thread-safe:

- ✅ **Safe**: All public methods can be called from any thread
- ✅ **Safe**: Multiple coroutines can call methods concurrently
- ✅ **Safe**: Singleton instance access is thread-safe

## Performance Considerations

- **Initialization**: One-time cost, typically <100ms
- **Server Start/Stop**: ~200-500ms depending on device
- **Tool Execution**: Varies by tool complexity
- **Memory Usage**: ~5-10MB baseline, varies with active connections

## Best Practices

1. **Initialize Early**: Initialize in `Application.onCreate()`
2. **Handle Errors**: Always check `Result` return values
3. **Lifecycle Management**: Use automatic lifecycle management for production apps
4. **Resource Cleanup**: Stop the server when your app is destroyed
5. **Permission Handling**: Request network permissions before starting

## See Also

- [Tools API Reference](tools.md)
- [Resources API Reference](resources.md)
- [Lifecycle Management Guide](../guides/lifecycle-management.md)
- [Performance Optimization](../guides/performance-optimization.md)

```

## Verification Steps

### Automated Verification

#### Step V1: Documentation Build
```bash
# If using a documentation generator like MkDocs
mkdocs build

# Or verify Markdown syntax
markdownlint docs/**/*.md
```

#### Step V2: Link Validation

```bash
# Check for broken internal links
markdown-link-check docs/**/*.md
```

#### Step V3: Code Example Testing

```bash
# Extract and test code examples
./scripts/test-documentation-examples.sh
```

### Manual Verification

#### Step M1: Content Review

- [ ] All sections are complete and accurate
- [ ] Code examples are tested and working
- [ ] API references match actual implementation
- [ ] Screenshots and diagrams are up-to-date
- [ ] Navigation links work correctly

#### Step M2: User Experience Testing

- [ ] Documentation is easy to navigate
- [ ] Getting started guide works for new users
- [ ] Examples can be copied and run successfully
- [ ] Troubleshooting guide addresses common issues
- [ ] Search functionality works (if implemented)

#### Step M3: Cross-Platform Compatibility

- [ ] Documentation renders correctly on mobile devices
- [ ] All formats (Markdown, HTML) display properly
- [ ] Code blocks have proper syntax highlighting
- [ ] Images scale appropriately

## Success Criteria

### Content Quality

- [ ] Complete API reference for all public methods
- [ ] Working examples for all major features
- [ ] Comprehensive troubleshooting guide
- [ ] Clear getting started path for new developers
- [ ] Advanced topics for experienced users

### Technical Quality

- [ ] All code examples compile and run
- [ ] Documentation builds without errors
- [ ] No broken links or missing references
- [ ] Proper syntax highlighting and formatting
- [ ] Mobile-responsive design

### User Experience

- [ ] Clear navigation structure
- [ ] Searchable content (if applicable)
- [ ] Progressive complexity (basic → advanced)
- [ ] Multiple learning paths for different needs
- [ ] Regular updates with SDK changes

## Resources

### Documentation Tools

- [MkDocs](https://www.mkdocs.org/) - Static site generator
- [GitBook](https://www.gitbook.com/) - Documentation platform
- [Docusaurus](https://docusaurus.io/) - Documentation website framework

### Writing Guidelines

- [Google Developer Documentation Style Guide](https://developers.google.com/style)
- [Microsoft Writing Style Guide](https://docs.microsoft.com/en-us/style-guide/)
- [GitLab Documentation Guidelines](https://docs.gitlab.com/ee/development/documentation/)

### Accessibility

- [Web Content Accessibility Guidelines](https://www.w3.org/WAI/WCAG21/quickref/)
- [Accessible Documentation](https://developers.google.com/style/accessibility)