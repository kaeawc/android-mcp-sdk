# Android MCP SDK Samples

This directory contains multiple sample applications demonstrating different ways to integrate the
Android MCP SDK into your projects.

**⚠️ DEBUG BUILDS ONLY: This library is intended for debug builds and development environments only.
The library will crash if included in release builds.**

## Sample Applications

### 📱 [Simple Sample](simple/)

**Purpose**: Basic automatic initialization demo  
**Target Audience**: Beginners, prototyping, simple apps  
**Key Features**:

- Automatic initialization via AndroidX Startup
- No DI framework required
- Basic MCP tool examples
- Minimal setup code

**When to Use**: When you want the simplest possible setup or are building a prototype.

**Source Code**: [`samples/simple/`](simple/)

### 🔧 [Hilt Integration Sample](hilt-integration/)

**Purpose**: Debug build Hilt dependency injection integration  
**Target Audience**: Debug builds using Hilt  
**Key Features**:

- Manual initialization via Hilt DI
- Automatic initialization disabled
- Configuration management through DI
- Clean separation of concerns
- Easy testing and mocking

**When to Use**: When building debug builds with Hilt dependency injection.

**Source Code**: [`samples/hilt-integration/`](hilt-integration/)

## Planned Samples

### 🏗️ Koin Integration Sample *(Coming Soon)*

**Purpose**: Demonstrate Koin DI integration for debug builds  
**Features**: Manual initialization, Koin modules, configuration via DI

### ⚙️ Dagger Integration Sample *(Coming Soon)*

**Purpose**: Demonstrate Dagger DI integration for debug builds  
**Features**: Manual initialization, Dagger modules, component integration

### 🧪 Custom Tools Sample *(Coming Soon)*

**Purpose**: Advanced custom tools and resources for development  
**Features**: Complex tool examples, resource subscriptions, prompt templates

## Building and Running

### Build All Samples

```bash
./gradlew assembleDebug
```

### Build Specific Sample

```bash
# Simple sample
./gradlew :samples:simple:assembleDebug

# Hilt integration sample
./gradlew :samples:hilt-integration:assembleDebug
```

### Install and Test

```bash
# Install specific sample
./gradlew :samples:simple:installDebug

# Check logs
adb logcat | grep -E "(MCP|SampleMcp|HiltMcp)"

# Set up port forwarding for testing
adb forward tcp:8080 tcp:8080  # WebSocket
adb forward tcp:8081 tcp:8081  # HTTP/SSE
```

## Sample Comparison

| Feature               | Simple    | Hilt Integration | Koin *(Planned)* | Dagger *(Planned)* |
|-----------------------|-----------|------------------|------------------|--------------------|
| **Initialization**    | Automatic | Manual via DI    | Manual via DI    | Manual via DI      |
| **DI Framework**      | None      | Hilt             | Koin             | Dagger             |
| **Setup Complexity**  | Minimal   | Medium           | Medium           | High               |
| **Debug Environment** | Yes       | Yes              | Yes              | Yes                |
| **Testing Support**   | Basic     | Excellent        | Excellent        | Excellent          |
| **Configuration**     | Hardcoded | DI-managed       | DI-managed       | DI-managed         |

## Documentation Integration

Instead of maintaining potentially outdated code examples in markdown documentation, our docs
reference these working sample applications:

- **[Getting Started Guide](../docs/getting-started.md)** → Links to `samples/simple/`
- *
  *[Hilt Integration](../docs/getting-started.md#option-2-manual-initialization-with-di-framework-startup-initializer)
  ** → Links to `samples/hilt-integration/`
- **[Usage Examples](../docs/usage.md)** → Links to relevant sample code

This ensures all code examples are:

- ✅ **Always working** - Samples are built and tested
- ✅ **Up to date** - No manual maintenance required
- ✅ **Complete** - Full context available
- ✅ **Runnable** - Can be installed and tested immediately
- ✅ **Debug-safe** - Will crash if accidentally used in release builds

## Contributing

When adding new samples:

1. Create a new directory under `samples/`
2. Copy an existing sample as a base
3. Update the sample for your specific use case
4. Add the sample to `settings.gradle.kts`
5. Update this README.md
6. Update relevant documentation to link to your sample
7. Ensure the sample builds and runs correctly in debug mode
8. Verify the sample crashes appropriately in release mode

## Quick Start

1. **Choose a sample** based on your use case
2. **Review the source code** in the sample directory
3. **Build and install** the sample app (debug builds only)
4. **Check the logs** to see MCP initialization
5. **Test connectivity** using adb port forwarding
6. **Adapt the patterns** to your own debug builds
