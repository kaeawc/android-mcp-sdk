# Android MCP SDK

An Android library that integrates
the [MCP Kotlin SDK](https://github.com/modelcontextprotocol/kotlin-sdk) to enable Android
applications to host MCP (Model Context Protocol) servers.

## Quick Start

Add the library to your Android project:

```kotlin
dependencies {
    implementation("dev.jasonpearson:mcp-android-sdk:1.0.0")
}
```

Initialize in your Application class:

```kotlin
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // MCP server is automatically initialized via AndroidX Startup
        if (McpStartup.isInitialized()) {
            val manager = McpStartup.getManager()
            manager.startServerAsync()
        }
    }
}
```

## Features

- ✅ Full MCP specification support (tools, resources, prompts)
- ✅ Built-in Android-specific tools and resources
- ✅ WebSocket and HTTP/SSE transport layers
- ✅ AndroidX Startup automatic initialization
- ✅ Thread-safe singleton management
- ✅ Comprehensive lifecycle management

## Documentation

📚 **[Read the full documentation →](https://jasonpearson.dev/android-mcp-sdk/)**

- [Getting Started Guide](docs/getting-started.md)
- [Usage Examples](docs/usage.md)
- [API Reference](docs/api-reference.md)
- [Transport Configuration](docs/transport.md)
- [Development Roadmap](roadmap/README.md)

## Project Goal

Enable Android apps to provide MCP servers accessible to AI tools and development environments via
adb-connected workstations.

## License

[MIT License](LICENSE)
