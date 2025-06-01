# Transport Configuration

The Android MCP SDK supports multiple transport layers for communication between your Android app
and MCP clients. This guide covers configuration and usage of the available transport options.

## Overview

The SDK provides two main transport mechanisms:

- **WebSocket Transport**: Real-time bidirectional communication (default port: 8080)
- **HTTP/SSE Transport**: HTTP-based communication with Server-Sent Events (default port: 8081)

Both transports are automatically started when the MCP server starts and can be accessed via adb
port forwarding from your development workstation.

## WebSocket Transport

### Default Configuration

WebSocket transport is automatically configured and started:

```kotlin
val manager = McpStartup.getManager()

// Start server (WebSocket transport starts automatically)
manager.startServerAsync()

// Check transport status
val transportInfo = manager.getTransportInfo()
Log.d("MCP", "WebSocket endpoint: ws://localhost:8080/mcp")
```

### Connecting from Development Machine

Set up adb port forwarding to access the WebSocket from your workstation:

```bash
# Forward WebSocket port
adb forward tcp:8080 tcp:8080

# Connect using a WebSocket client
# Endpoint: ws://localhost:8080/mcp
```

### Sending Custom Messages

You can broadcast custom messages to all connected WebSocket clients:

```kotlin
lifecycleScope.launch {
    val manager = McpStartup.getManager()
    
    // Send a custom notification
    val message = buildJsonObject {
        put("jsonrpc", JsonPrimitive("2.0"))
        put("method", JsonPrimitive("notification/custom"))
        put("params", buildJsonObject {
            put("type", JsonPrimitive("status_update"))
            put("message", JsonPrimitive("Server status changed"))
            put("timestamp", JsonPrimitive(System.currentTimeMillis()))
        })
    }
    
    manager.broadcastMessage(message.toString())
}
```

### WebSocket Client Example

Example WebSocket client code (JavaScript):

```javascript
// Connect to Android MCP server via adb forwarding
const ws = new WebSocket('ws://localhost:8080/mcp');

ws.onopen = function() {
    console.log('Connected to Android MCP server');
    
    // Send tools/list request
    ws.send(JSON.stringify({
        jsonrpc: "2.0",
        method: "tools/list",
        id: 1
    }));
};

ws.onmessage = function(event) {
    const response = JSON.parse(event.data);
    console.log('Received:', response);
};

ws.onerror = function(error) {
    console.error('WebSocket error:', error);
};
```

## HTTP/SSE Transport

### Default Configuration

HTTP/SSE transport provides HTTP endpoints for request/response and Server-Sent Events for
server-to-client communication:

```kotlin
// HTTP/SSE transport is automatically available when server starts
val manager = McpStartup.getManager()
manager.startServerAsync()

// Available endpoints:
// POST http://localhost:8081/mcp/message - Client-to-server messages
// GET  http://localhost:8081/mcp/events  - Server-to-client events (SSE)
// GET  http://localhost:8081/mcp/status  - Transport status
```

### Connecting from Development Machine

Set up adb port forwarding for HTTP/SSE:

```bash
# Forward HTTP/SSE port
adb forward tcp:8081 tcp:8081

# Test the status endpoint
curl http://localhost:8081/mcp/status

# Send a message
curl -X POST http://localhost:8081/mcp/message \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","method":"tools/list","id":1}'

# Listen to server events (SSE)
curl -N http://localhost:8081/mcp/events
```

### HTTP Client Example

Example HTTP client implementation (Python):

```python
import requests
import json
from sseclient import SSEClient

# Base URL for Android MCP server via adb forwarding
BASE_URL = "http://localhost:8081/mcp"

# Send request to server
def send_request(method, params=None, request_id=1):
    payload = {
        "jsonrpc": "2.0",
        "method": method,
        "id": request_id
    }
    if params:
        payload["params"] = params
    
    response = requests.post(
        f"{BASE_URL}/message",
        json=payload,
        headers={"Content-Type": "application/json"}
    )
    return response.json()

# Listen to server events
def listen_to_events():
    messages = SSEClient(f"{BASE_URL}/events")
    for msg in messages:
        if msg.data:
            event = json.loads(msg.data)
            print(f"Received event: {event}")

# Example usage
if __name__ == "__main__":
    # Get available tools
    tools_response = send_request("tools/list")
    print("Available tools:", tools_response)
    
    # Call a tool
    device_info = send_request("tools/call", {
        "name": "device_info",
        "arguments": {}
    })
    print("Device info:", device_info)
    
    # Start listening to events (in a separate thread)
    import threading
    event_thread = threading.Thread(target=listen_to_events)
    event_thread.daemon = True
    event_thread.start()
```

## Transport Security

### Network Security

When using transport layers, consider these security aspects:

1. **Local Network Only**: Transports are bound to localhost and only accessible via adb forwarding
2. **Development Use**: Intended for development and debugging, not production exposure
3. **Firewall**: Ensure your development machine firewall allows the forwarded ports

### Authentication

Currently, the transport layers don't include built-in authentication. For production use, consider:

1. **VPN**: Use VPN connections for remote access
2. **Tunnel**: Use secure tunneling solutions
3. **Custom Auth**: Implement custom authentication in your tools and resources

## Advanced Configuration

### Custom Port Configuration

While the default ports work for most use cases, you can check the current configuration:

```kotlin
val manager = McpStartup.getManager()
val transportInfo = manager.getTransportInfo()

Log.d("MCP", "Transport configuration: $transportInfo")
// Will show current port assignments and status
```

### Connection Management

Monitor and manage transport connections:

```kotlin
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val manager = McpStartup.getManager()
        
        // Monitor transport status
        lifecycleScope.launch {
            while (isActive) {
                val transportInfo = manager.getTransportInfo()
                Log.d("MCP", "Transport status: $transportInfo")
                
                // Check if transports are healthy
                if (!transportInfo.contains("running")) {
                    Log.w("MCP", "Transport may be down, attempting restart")
                    manager.startServerAsync()
                }
                
                delay(30000) // Check every 30 seconds
            }
        }
    }
}
```

## Troubleshooting

### Common Issues

#### Port Forwarding Not Working

```bash
# Check if adb is connected
adb devices

# Remove existing forwards and re-add
adb forward --remove tcp:8080
adb forward --remove tcp:8081
adb forward tcp:8080 tcp:8080
adb forward tcp:8081 tcp:8081

# Verify forwards are active
adb forward --list
```

#### Connection Refused

1. **Check if server is running**:
   ```kotlin
   val manager = McpStartup.getManager()
   Log.d("MCP", "Server running: ${manager.isServerRunning()}")
   ```

2. **Check device logs**:
   ```bash
   adb logcat | grep MCP
   ```

3. **Verify app is in foreground**: Some lifecycle configurations may pause the server when app is
   backgrounded

#### WebSocket Connection Drops

WebSocket connections may drop due to:

1. **App lifecycle changes**: Configure lifecycle management to handle background states
2. **Network changes**: WiFi/mobile network switches
3. **Device sleep**: Android's Doze mode or app standby

Solution:

```kotlin
// Configure lifecycle to keep server running
manager.initializeLifecycleManagement(
    application = this,
    config = McpLifecycleManager.LifecycleConfig(
        autoStartOnAppStart = true,
        autoStopOnAppStop = false,  // Keep running in background
        restartOnAppReturn = true
    )
)
```

### Debugging Transport Issues

#### Enable Verbose Logging

```kotlin
// In your Application class
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Enable debug logging for transport issues
        if (BuildConfig.DEBUG) {
            Log.d("MCP", "Starting MCP server with debug logging")
        }
        
        val manager = McpStartup.initializeManually(this)
        manager.startServerAsync()
    }
}
```

#### Test Transport Endpoints

```bash
#!/bin/bash
# test_transport.sh - Script to test all transport endpoints

echo "Testing Android MCP Transport Endpoints"
echo "======================================="

# Setup port forwarding
adb forward tcp:8080 tcp:8080
adb forward tcp:8081 tcp:8081

echo "1. Testing HTTP Status Endpoint"
curl -s http://localhost:8081/mcp/status | jq .

echo -e "\n2. Testing WebSocket Connection"
# Note: Requires websocat tool (cargo install websocat)
echo '{"jsonrpc":"2.0","method":"tools/list","id":1}' | \
  timeout 5s websocat ws://localhost:8080/mcp

echo -e "\n3. Testing HTTP Message Endpoint"
curl -s -X POST http://localhost:8081/mcp/message \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","method":"tools/list","id":1}' | jq .

echo -e "\nTransport test complete!"
```

## Performance Considerations

### Connection Limits

The transport layer can handle multiple simultaneous connections, but consider:

1. **Memory usage**: Each connection consumes memory
2. **CPU overhead**: Message processing for multiple clients
3. **Battery impact**: Network activity affects battery life

### Optimization Tips

1. **Batch operations**: Group multiple requests when possible
2. **Efficient JSON**: Minimize JSON payload sizes
3. **Connection reuse**: Reuse WebSocket connections for multiple requests
4. **Background management**: Properly handle app lifecycle transitions

### Monitoring Performance

```kotlin
class TransportMonitor {
    private val messageCount = AtomicInteger(0)
    private val lastResetTime = AtomicLong(System.currentTimeMillis())
    
    fun onMessageReceived() {
        messageCount.incrementAndGet()
        
        val now = System.currentTimeMillis()
        val elapsed = now - lastResetTime.get()
        
        // Log stats every minute
        if (elapsed > 60000) {
            val messagesPerMinute = messageCount.get()
            Log.d("MCP", "Transport stats: $messagesPerMinute messages/minute")
            
            messageCount.set(0)
            lastResetTime.set(now)
        }
    }
}
```

## Integration Examples

### Claude Desktop Integration

Configure Claude Desktop to connect to your Android MCP server:

```json
{
  "mcpServers": {
    "android-app": {
      "command": "node",
      "args": ["path/to/websocket-mcp-client.js"],
      "env": {
        "ANDROID_MCP_URL": "ws://localhost:8080/mcp"
      }
    }
  }
}
```

### Custom MCP Client

Build a custom MCP client that connects to your Android server:

```javascript
// mcp-android-client.js
const WebSocket = require('ws');

class AndroidMCPClient {
    constructor(url = 'ws://localhost:8080/mcp') {
        this.url = url;
        this.ws = null;
        this.requestId = 1;
        this.pendingRequests = new Map();
    }
    
    async connect() {
        this.ws = new WebSocket(this.url);
        
        this.ws.on('message', (data) => {
            const message = JSON.parse(data.toString());
            this.handleMessage(message);
        });
        
        return new Promise((resolve, reject) => {
            this.ws.on('open', resolve);
            this.ws.on('error', reject);
        });
    }
    
    async callTool(name, arguments = {}) {
        const id = this.requestId++;
        const request = {
            jsonrpc: "2.0",
            method: "tools/call",
            params: { name, arguments },
            id
        };
        
        return this.sendRequest(request);
    }
    
    async listTools() {
        const id = this.requestId++;
        const request = {
            jsonrpc: "2.0",
            method: "tools/list",
            id
        };
        
        return this.sendRequest(request);
    }
    
    sendRequest(request) {
        return new Promise((resolve, reject) => {
            this.pendingRequests.set(request.id, { resolve, reject });
            this.ws.send(JSON.stringify(request));
        });
    }
    
    handleMessage(message) {
        if (message.id && this.pendingRequests.has(message.id)) {
            const { resolve, reject } = this.pendingRequests.get(message.id);
            this.pendingRequests.delete(message.id);
            
            if (message.error) {
                reject(new Error(message.error.message));
            } else {
                resolve(message.result);
            }
        }
    }
}

module.exports = AndroidMCPClient;
```

This transport configuration guide provides comprehensive coverage of both WebSocket and HTTP/SSE
transports, including practical examples, troubleshooting tips, and integration patterns for
connecting various MCP clients to your Android MCP server.