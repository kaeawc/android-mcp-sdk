# ADB Port Forwarding Testing

This directory contains scripts and documentation for testing the Android MCP SDK's connectivity
through ADB port forwarding.

## Overview

The Android MCP SDK exposes an HTTP/SSE endpoint on port 8080 that allows MCP clients running on a
workstation to connect to the Android app. The ADB port forwarding setup enables this connection by
forwarding localhost:8080 on the workstation to port 8080 on the Android device.

## Quick Start

### 1. Setup Port Forwarding

```bash
# Setup port forwarding (ensure device is connected)
./scripts/adb_testing/setup_port_forwarding.sh
```

### 2. Run Your Android App

Launch your Android app that includes the MCP SDK. The server should automatically start.

### 3. Run ADB Tests

```bash
# Run basic connection tests
./gradlew :core:connectedAndroidTest --tests "*AdbConnectionTest"

# Run performance tests
./gradlew :core:connectedAndroidTest --tests "*AdbPerformanceTest"

# Run reliability tests
./gradlew :core:connectedAndroidTest --tests "*AdbReliabilityTest"

# Run all ADB tests
./gradlew :core:connectedAndroidTest --tests "*Adb*"
```

### 4. Cleanup

```bash
# Clean up port forwarding when done
./scripts/adb_testing/cleanup_port_forwarding.sh
```

## Manual Testing

You can also test the connection manually using curl:

```bash
# Test basic connectivity
curl -v http://localhost:8080/mcp

# Send an MCP message
curl -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/list","params":{}}'
```

## Scripts

### setup_port_forwarding.sh

Sets up ADB port forwarding for the MCP server endpoint.

**Environment Variables:**

- `SSE_PORT`: Port to forward (default: 8080)
- `DEVICE_SERIAL`: Specific device serial number (optional)

**Example:**

```bash
# Use custom port
SSE_PORT=8081 ./scripts/adb_testing/setup_port_forwarding.sh

# Target specific device
DEVICE_SERIAL=emulator-5554 ./scripts/adb_testing/setup_port_forwarding.sh
```

### cleanup_port_forwarding.sh

Removes all ADB port forwarding rules.

**Environment Variables:**

- `DEVICE_SERIAL`: Specific device serial number (optional)

## Test Classes

### AdbConnectionTest

Basic connectivity tests that verify:

- SSE endpoint is reachable through port forwarding
- MCP messages can be sent and responses received
- Port forwarding status is correct

### AdbPerformanceTest

Performance validation tests:

- Latency measurements for SSE connections
- Concurrent connection handling
- Sustained load testing

### AdbReliabilityTest

Reliability and recovery tests:

- Server restart recovery
- Multiple client reconnection
- Long-running connection stability

## Troubleshooting

### Connection Refused

If you get "Connection refused" errors:

1. Verify device is connected: `adb devices`
2. Check port forwarding: `adb forward --list`
3. Ensure your Android app is running with MCP server started
4. Try restarting port forwarding

### Multiple Devices

If you have multiple devices connected:

```bash
# List devices
adb devices

# Use specific device
DEVICE_SERIAL=your-device-serial ./scripts/adb_testing/setup_port_forwarding.sh
```

### Port Already in Use

If port 8080 is busy:

```bash
# Use different port
SSE_PORT=8081 ./scripts/adb_testing/setup_port_forwarding.sh

# Update your tests accordingly
./gradlew :core:connectedAndroidTest --tests "*AdbConnectionTest" -Ptest.sse.port=8081
```

## Integration with MCP Clients

Once port forwarding is set up, you can connect any MCP client to `http://localhost:8080/mcp`.

Example with a hypothetical MCP client:

```bash
mcp-client connect http://localhost:8080/mcp
```

The Android app will receive MCP protocol messages and respond with available tools, resources, and
prompts.