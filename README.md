# Android MCP SDK

An Android library that integrates the [MCP Kotlin SDK](https://github.com/modelcontextprotocol/kotlin-sdk) to enable Android applications to host MCP (Model Context Protocol) servers.
The overall goal is to enable Android apps to provide MCP servers access to AI tools in development environments.

## Quick Start

Add the library to your Android project:

```kotlin
dependencies {
    implementation("dev.jasonpearson:mcp-android-sdk:1.0.0")
}
```

Unless you need to remove or delay the MCP server startup it'll be there in debug variants. I'm making it impossible to include in release variants for now.

## Features

- ✅ Full MCP specification support (tools, resources, prompts)
- ✅ Built-in Android-specific tools and resources
- ✅ WebSocket and HTTP/SSE transport layers
- ✅ AndroidX Startup automatic initialization
- ✅ Thread-safe singleton management
- ✅ Comprehensive lifecycle management

## Documentation

📚 **[Read the full documentation →](https://github.io/kaeawc/android-mcp-sdk/)**

- [Getting Started Guide](docs/getting-started.md)
- [Usage Examples](docs/usage.md)
- [API Reference](docs/api-reference.md)
- [Transport Configuration](docs/transport.md)
- [Development Roadmap](roadmap/README.md)

## License

[Apache 2.0 License](LICENSE)
