# Android MCP SDK

An Android library that integrates the [MCP Kotlin SDK](https://github.com/modelcontextprotocol/kotlin-sdk) to enable Android applications to host MCP (Model Context Protocol) servers.
The overall goal is to enable Android apps to provide MCP servers access to AI tools in development environments.

**⚠️ DEBUG BUILDS ONLY: This library is intended for debug builds and development environments only.
The library will crash if included in release builds to prevent accidental deployment.**

## Quick Start

Add the library to your Android project:

```kotlin
dependencies {
    debugImplementation("dev.jasonpearson:mcp-android-sdk:1.0.0")  // Debug builds only!
}
```

That's it! The MCP server automatically initializes via AndroidX Startup. Launch you app and query
adb for device information to obtain the SSE URL, drop that into your favorite MCP client and interact
with your app via an AI agent.

## Features

- ✅ Full MCP specification support (tools, resources, prompts)
- ✅ Built-in Android-specific tools and resources
- ✅ WebSocket and HTTP/SSE transport layers
- ✅ AndroidX Startup automatic initialization and startup
- ✅ DI framework integration (Hilt, Koin, Dagger)
- ✅ Thread-safe singleton management
- ✅ Comprehensive lifecycle management
- ✅ **Release build protection** - Crashes if accidentally included in production

## Safety Features

This library includes multiple safety mechanisms to prevent accidental inclusion in production
builds:

- **Gradle Configuration**: Use `debugImplementation` instead of `implementation`
- **Runtime Checks**: Automatically detects and crashes on release builds
- **Clear Error Messages**: Provides detailed instructions when misused
- **Development Focus**: All features designed for debug/development workflows

## Documentation

📚 **[Read the full documentation →](https://github.io/kaeawc/android-mcp-sdk/)**

- [Getting Started Guide](docs/getting-started.md)
- [Usage Examples](docs/usage.md)
- [API Reference](docs/api-reference.md)
- [Transport Configuration](docs/transport.md)
- [Development Roadmap](roadmap/README.md)

## License

[Apache 2.0 License](LICENSE)
