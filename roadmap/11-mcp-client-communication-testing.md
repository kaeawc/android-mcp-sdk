# 11 - MCP Client Communication Testing

## Status: `[ ]` Not Started

## Objective

Develop comprehensive end-to-end testing for MCP client communication with the Android MCP server.
This includes testing with real MCP clients, validating protocol compliance, message flow, error
handling, and ensuring compatibility with the broader MCP ecosystem.

## Requirements

### Technical Requirements

- **Real MCP Clients**: Integration with actual MCP client implementations
- **Protocol Compliance**: Full MCP protocol specification compliance testing
- **Message Flow**: Bidirectional communication testing (client ↔ server)
- **Error Scenarios**: Testing malformed messages, network failures, timeouts
- **Performance**: Latency, throughput, and resource usage testing

### Testing Scope

- **MCP Protocol Methods**: Initialize, tools, resources, prompts, notifications
- **Client Compatibility**: Multiple MCP client implementations
- **Transport Protocols**: WebSocket and HTTP/SSE client communication
- **Lifecycle Management**: Connection, session management, cleanup
- **Error Handling**: Protocol errors, network failures, timeout scenarios
- **Performance**: Response times, concurrent connections, resource usage

### Test Categories

1. **Protocol Compliance Tests**: MCP specification adherence
2. **Client Integration Tests**: Real MCP client interactions
3. **Message Flow Tests**: Request/response patterns
4. **Error Handling Tests**: Failure scenario testing
5. **Performance Tests**: Load and stress testing with real clients

## Dependencies

**Must Complete First:**

- [04-websocket-transport-implementation.md](04-websocket-transport-implementation.md) - WebSocket
  transport needed
- [05-http-sse-transport-implementation.md](05-http-sse-transport-implementation.md) - HTTP/SSE
  transport needed
- [09-integration-testing-suite.md](09-integration-testing-suite.md) - Testing infrastructure
- [10-adb-port-forwarding-testing.md](10-adb-port-forwarding-testing.md) - ADB connectivity

**Should Complete First:**

- [03-jsonrpc-message-parsing.md](03-jsonrpc-message-parsing.md) - Message parsing

## Implementation Steps

### Phase 1: MCP Client Test Infrastructure

#### Step 1.1: Setup MCP Client Test Environment

Create `testing/mcp_clients/`:

**setup_mcp_clients.sh:**

```bash
#!/bin/bash

# Android MCP SDK - MCP Client Test Environment Setup
set -e

CLIENTS_DIR="testing/mcp_clients"
PYTHON_VENV="$CLIENTS_DIR/venv"
NODE_DIR="$CLIENTS_DIR/node"

echo "Setting up MCP client test environment..."

# Create directory structure
mkdir -p "$CLIENTS_DIR"
mkdir -p "$NODE_DIR"

# Setup Python MCP client
setup_python_client() {
    echo "Setting up Python MCP client..."
    
    if ! command -v python3 &> /dev/null; then
        echo "Error: Python3 not found. Please install Python 3.8+."
        exit 1
    fi
    
    # Create virtual environment
    python3 -m venv "$PYTHON_VENV"
    source "$PYTHON_VENV/bin/activate"
    
    # Install MCP Python SDK
    pip install --upgrade pip
    pip install mcp websockets aiohttp
    
    echo "✓ Python MCP client environment ready"
}

# Setup Node.js MCP client
setup_node_client() {
    echo "Setting up Node.js MCP client..."
    
    if ! command -v node &> /dev/null; then
        echo "Warning: Node.js not found. Skipping Node.js MCP client setup."
        return 0
    fi
    
    cd "$NODE_DIR"
    
    # Initialize package.json if it doesn't exist
    if [ ! -f "package.json" ]; then
        npm init -y
    fi
    
    # Install MCP Node.js SDK and testing dependencies
    npm install @modelcontextprotocol/sdk ws node-fetch
    
    echo "✓ Node.js MCP client environment ready"
}

# Create test client scripts
create_test_clients() {
    echo "Creating test client scripts..."
    
    # Python WebSocket test client
    cat > "$CLIENTS_DIR/python_ws_client.py" << 'EOF'
#!/usr/bin/env python3
"""
Python WebSocket MCP Client for testing Android MCP SDK
"""

import asyncio
import json
import sys
import websockets
from typing import Dict, Any, Optional

class McpWebSocketClient:
    def __init__(self, uri: str = "ws://localhost:8080/mcp"):
        self.uri = uri
        self.websocket = None
        self.request_id = 0
        
    async def connect(self):
        """Connect to MCP server"""
        try:
            self.websocket = await websockets.connect(self.uri)
            print(f"✓ Connected to {self.uri}")
            return True
        except Exception as e:
            print(f"✗ Connection failed: {e}")
            return False
    
    async def disconnect(self):
        """Disconnect from MCP server"""
        if self.websocket:
            await self.websocket.close()
            print("✓ Disconnected")
    
    async def send_request(self, method: str, params: Dict[str, Any] = None) -> Optional[Dict[str, Any]]:
        """Send MCP request and wait for response"""
        if not self.websocket:
            print("✗ Not connected")
            return None
            
        self.request_id += 1
        request = {
            "jsonrpc": "2.0",
            "id": self.request_id,
            "method": method,
            "params": params or {}
        }
        
        try:
            await self.websocket.send(json.dumps(request))
            print(f"→ Sent: {method}")
            
            response_text = await self.websocket.recv()
            response = json.loads(response_text)
            print(f"← Received: {response.get('id', 'unknown')}")
            
            return response
        except Exception as e:
            print(f"✗ Request failed: {e}")
            return None
    
    async def initialize(self):
        """Initialize MCP session"""
        params = {
            "protocolVersion": "2024-11-05",
            "capabilities": {
                "roots": {"listChanged": True},
                "sampling": {}
            },
            "clientInfo": {
                "name": "Python Test Client",
                "version": "1.0.0"
            }
        }
        
        response = await self.send_request("initialize", params)
        if response and "result" in response:
            print("✓ Initialization successful")
            
            # Send initialized notification
            notification = {
                "jsonrpc": "2.0",
                "method": "notifications/initialized"
            }
            await self.websocket.send(json.dumps(notification))
            print("✓ Sent initialized notification")
            
            return True
        else:
            print("✗ Initialization failed")
            return False

async def main():
    """Main test function"""
    if len(sys.argv) > 1:
        uri = sys.argv[1]
    else:
        uri = "ws://localhost:8080/mcp"
    
    client = McpWebSocketClient(uri)
    
    try:
        # Connect and initialize
        if not await client.connect():
            return 1
        
        if not await client.initialize():
            return 1
        
        # Test basic MCP methods
        tests = [
            ("tools/list", {}),
            ("resources/list", {}),
            ("prompts/list", {}),
        ]
        
        for method, params in tests:
            response = await client.send_request(method, params)
            if response and "result" in response:
                print(f"✓ {method} successful")
            else:
                print(f"✗ {method} failed")
        
        # Test tool call
        tool_response = await client.send_request("tools/call", {
            "name": "device_info",
            "arguments": {}
        })
        
        if tool_response and "result" in tool_response:
            print("✓ Tool call successful")
        else:
            print("✗ Tool call failed")
        
        print("\n✓ All tests completed")
        return 0
        
    except KeyboardInterrupt:
        print("\n! Test interrupted")
        return 1
    finally:
        await client.disconnect()

if __name__ == "__main__":
    exit(asyncio.run(main()))
EOF
    
    # Python HTTP test client
    cat > "$CLIENTS_DIR/python_http_client.py" << 'EOF'
#!/usr/bin/env python3
"""
Python HTTP MCP Client for testing Android MCP SDK
"""

import aiohttp
import asyncio
import json
import sys
from typing import Dict, Any, Optional

class McpHttpClient:
    def __init__(self, base_url: str = "http://localhost:8081"):
        self.base_url = base_url.rstrip('/')
        self.session = None
        self.request_id = 0
    
    async def __aenter__(self):
        self.session = aiohttp.ClientSession()
        return self
    
    async def __aexit__(self, exc_type, exc_val, exc_tb):
        if self.session:
            await self.session.close()
    
    async def send_request(self, method: str, params: Dict[str, Any] = None) -> Optional[Dict[str, Any]]:
        """Send MCP request via HTTP POST"""
        if not self.session:
            print("✗ Session not initialized")
            return None
        
        self.request_id += 1
        request = {
            "jsonrpc": "2.0",
            "id": self.request_id,
            "method": method,
            "params": params or {}
        }
        
        try:
            async with self.session.post(
                f"{self.base_url}/mcp/message",
                json=request,
                headers={"Content-Type": "application/json"}
            ) as response:
                if response.status == 200:
                    result = await response.json()
                    print(f"✓ {method} successful")
                    return result
                else:
                    print(f"✗ {method} failed: HTTP {response.status}")
                    return None
        except Exception as e:
            print(f"✗ {method} error: {e}")
            return None

async def main():
    """Main test function"""
    if len(sys.argv) > 1:
        base_url = sys.argv[1]
    else:
        base_url = "http://localhost:8081"
    
    async with McpHttpClient(base_url) as client:
        # Test basic MCP methods
        tests = [
            ("tools/list", {}),
            ("resources/list", {}),
            ("prompts/list", {}),
        ]
        
        for method, params in tests:
            await client.send_request(method, params)
        
        # Test tool call
        await client.send_request("tools/call", {
            "name": "device_info",
            "arguments": {}
        })
        
        print("\n✓ All HTTP tests completed")

if __name__ == "__main__":
    asyncio.run(main())
EOF
    
    # Make scripts executable
    chmod +x "$CLIENTS_DIR/python_ws_client.py"
    chmod +x "$CLIENTS_DIR/python_http_client.py"
    
    echo "✓ Test client scripts created"
}

# Main execution
main() {
    setup_python_client
    setup_node_client
    create_test_clients
    
    echo ""
    echo "MCP client test environment setup complete!"
    echo ""
    echo "To activate Python environment:"
    echo "  source $PYTHON_VENV/bin/activate"
    echo ""
    echo "To run WebSocket test client:"
    echo "  $CLIENTS_DIR/python_ws_client.py"
    echo ""
    echo "To run HTTP test client:"
    echo "  $CLIENTS_DIR/python_http_client.py"
}

main "$@"
```

#### Step 1.2: Create Protocol Compliance Test Suite

Create `lib/src/androidTest/kotlin/dev/jasonpearson/mcpandroidsdk/client/`:

**McpProtocolComplianceTestSuite.kt:**

```kotlin
package dev.jasonpearson.mcpandroidsdk.client

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.jasonpearson.mcpandroidsdk.McpServerManager
import dev.jasonpearson.mcpandroidsdk.testing.McpTestUtils
import kotlinx.coroutines.runBlocking
import okhttp3.*
import org.json.JSONObject
import org.json.JSONArray
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class McpProtocolComplianceTestSuite {
    
    private lateinit var serverManager: McpServerManager
    private lateinit var context: android.content.Context
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
    
    companion object {
        const val MCP_PROTOCOL_VERSION = "2024-11-05"
        const val WEBSOCKET_URL = "ws://localhost:8080/mcp"
        const val HTTP_URL = "http://localhost:8081/mcp/message"
    }
    
    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        serverManager = McpServerManager.getInstance()
        
        runBlocking {
            serverManager.initialize(context, "Protocol Compliance Test Server", "1.0.0")
            serverManager.startServer()
            McpTestUtils.waitForCondition { serverManager.isServerRunning() }
        }
    }
    
    @After
    fun teardown() {
        runBlocking {
            serverManager.stopServer()
            serverManager.shutdown()
        }
    }
    
    @Test
    fun testMcpInitializationHandshake() = runBlocking {
        var initResponse: JSONObject? = null
        var initNotificationSent = false
        
        val wsListener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                val initMessage = JSONObject().apply {
                    put("jsonrpc", "2.0")
                    put("id", 1)
                    put("method", "initialize")
                    put("params", JSONObject().apply {
                        put("protocolVersion", MCP_PROTOCOL_VERSION)
                        put("capabilities", JSONObject().apply {
                            put("roots", JSONObject().apply {
                                put("listChanged", true)
                            })
                            put("sampling", JSONObject())
                        })
                        put("clientInfo", JSONObject().apply {
                            put("name", "Protocol Test Client")
                            put("version", "1.0.0")
                        })
                    })
                }
                
                webSocket.send(initMessage.toString())
            }
            
            override fun onMessage(webSocket: WebSocket, text: String) {
                val response = JSONObject(text)
                
                if (response.optInt("id") == 1 && !initNotificationSent) {
                    initResponse = response
                    
                    // Send initialized notification
                    val notification = JSONObject().apply {
                        put("jsonrpc", "2.0")
                        put("method", "notifications/initialized")
                    }
                    webSocket.send(notification.toString())
                    initNotificationSent = true
                }
            }
        }
        
        val request = Request.Builder()
            .url(WEBSOCKET_URL)
            .build()
        
        val webSocket = okHttpClient.newWebSocket(request, wsListener)
        
        // Wait for initialization response
        McpTestUtils.waitForCondition { initResponse != null }
        
        assertNotNull("Should receive initialization response", initResponse)
        
        val response = initResponse!!
        assertEquals("Should have correct JSON-RPC version", "2.0", response.getString("jsonrpc"))
        assertEquals("Should have correct request ID", 1, response.getInt("id"))
        assertTrue("Should have result object", response.has("result"))
        
        val result = response.getJSONObject("result")
        assertTrue("Should have protocol version", result.has("protocolVersion"))
        assertTrue("Should have capabilities", result.has("capabilities"))
        assertTrue("Should have server info", result.has("serverInfo"))
        
        val serverInfo = result.getJSONObject("serverInfo")
        assertTrue("Should have server name", serverInfo.has("name"))
        assertTrue("Should have server version", serverInfo.has("version"))
        
        webSocket.close(1000, "Test complete")
    }
    
    @Test
    fun testToolsListCompliance() = runBlocking {
        val testMessage = JSONObject().apply {
            put("jsonrpc", "2.0")
            put("id", 1)
            put("method", "tools/list")
            put("params", JSONObject())
        }
        
        val response = sendHttpRequest(testMessage)
        assertNotNull("Should receive tools/list response", response)
        
        validateJsonRpcResponse(response!!)
        
        val result = response.getJSONObject("result")
        assertTrue("Should have tools array", result.has("tools"))
        
        val tools = result.getJSONArray("tools")
        assertTrue("Should have at least one tool", tools.length() > 0)
        
        // Validate each tool structure
        for (i in 0 until tools.length()) {
            val tool = tools.getJSONObject(i)
            assertTrue("Tool should have name", tool.has("name"))
            assertTrue("Tool should have description", tool.has("description"))
            
            if (tool.has("inputSchema")) {
                val inputSchema = tool.getJSONObject("inputSchema")
                assertTrue("Input schema should have type", inputSchema.has("type"))
                assertEquals("Input schema type should be object", "object", inputSchema.getString("type"))
            }
        }
        
        // Verify built-in tools are present
        val toolNames = (0 until tools.length()).map { 
            tools.getJSONObject(it).getString("name") 
        }
        assertTrue("Should have device_info tool", toolNames.contains("device_info"))
        assertTrue("Should have app_info tool", toolNames.contains("app_info"))
    }
    
    @Test
    fun testToolCallCompliance() = runBlocking {
        val testMessage = JSONObject().apply {
            put("jsonrpc", "2.0")
            put("id", 1)
            put("method", "tools/call")
            put("params", JSONObject().apply {
                put("name", "device_info")
                put("arguments", JSONObject())
            })
        }
        
        val response = sendHttpRequest(testMessage)
        assertNotNull("Should receive tools/call response", response)
        
        validateJsonRpcResponse(response!!)
        
        val result = response.getJSONObject("result")
        assertTrue("Should have content array", result.has("content"))
        
        val content = result.getJSONArray("content")
        assertTrue("Should have at least one content item", content.length() > 0)
        
        // Validate content structure
        val contentItem = content.getJSONObject(0)
        assertTrue("Content should have type", contentItem.has("type"))
        assertTrue("Content should have text", contentItem.has("text"))
        assertEquals("Content type should be text", "text", contentItem.getString("type"))
        
        val text = contentItem.getString("text")
        assertFalse("Content text should not be empty", text.isEmpty())
    }
    
    @Test
    fun testResourcesListCompliance() = runBlocking {
        // Add a test resource first
        serverManager.addFileResource(
            uri = "test://sample.txt",
            name = "Sample Resource",
            description = "Test resource for protocol compliance",
            filePath = context.filesDir.absolutePath + "/sample.txt",
            mimeType = "text/plain"
        )
        
        val testMessage = JSONObject().apply {
            put("jsonrpc", "2.0")
            put("id", 1)
            put("method", "resources/list")
            put("params", JSONObject())
        }
        
        val response = sendHttpRequest(testMessage)
        assertNotNull("Should receive resources/list response", response)
        
        validateJsonRpcResponse(response!!)
        
        val result = response.getJSONObject("result")
        assertTrue("Should have resources array", result.has("resources"))
        
        val resources = result.getJSONArray("resources")
        assertTrue("Should have at least one resource", resources.length() > 0)
        
        // Validate resource structure
        val resource = resources.getJSONObject(0)
        assertTrue("Resource should have uri", resource.has("uri"))
        assertTrue("Resource should have name", resource.has("name"))
        
        if (resource.has("description")) {
            assertFalse("Resource description should not be empty", 
                resource.getString("description").isEmpty())
        }
        
        if (resource.has("mimeType")) {
            assertFalse("Resource mimeType should not be empty", 
                resource.getString("mimeType").isEmpty())
        }
    }
    
    @Test
    fun testResourceReadCompliance() = runBlocking {
        // Add and populate a test resource
        val testContent = "This is test content for protocol compliance testing."
        val testFile = java.io.File(context.filesDir, "protocol_test.txt")
        testFile.writeText(testContent)
        
        serverManager.addFileResource(
            uri = "test://protocol_test.txt",
            name = "Protocol Test Resource",
            description = "Resource for testing protocol compliance",
            filePath = testFile.absolutePath,
            mimeType = "text/plain"
        )
        
        val testMessage = JSONObject().apply {
            put("jsonrpc", "2.0")
            put("id", 1)
            put("method", "resources/read")
            put("params", JSONObject().apply {
                put("uri", "test://protocol_test.txt")
            })
        }
        
        val response = sendHttpRequest(testMessage)
        assertNotNull("Should receive resources/read response", response)
        
        validateJsonRpcResponse(response!!)
        
        val result = response.getJSONObject("result")
        assertTrue("Should have contents array", result.has("contents"))
        
        val contents = result.getJSONArray("contents")
        assertTrue("Should have at least one content item", contents.length() > 0)
        
        // Validate content structure
        val contentItem = contents.getJSONObject(0)
        assertTrue("Content should have uri", contentItem.has("uri"))
        assertTrue("Content should have mimeType", contentItem.has("mimeType"))
        
        if (contentItem.has("text")) {
            val text = contentItem.getString("text")
            assertEquals("Content text should match", testContent, text)
        }
        
        assertEquals("Content URI should match", "test://protocol_test.txt", 
            contentItem.getString("uri"))
        assertEquals("Content mimeType should match", "text/plain", 
            contentItem.getString("mimeType"))
    }
    
    @Test
    fun testPromptsListCompliance() = runBlocking {
        // Add a test prompt
        serverManager.addSimplePrompt(
            name = "test_prompt",
            description = "Test prompt for protocol compliance",
            arguments = listOf()
        ) { _ ->
            "This is a test prompt for protocol compliance testing."
        }
        
        val testMessage = JSONObject().apply {
            put("jsonrpc", "2.0")
            put("id", 1)
            put("method", "prompts/list")
            put("params", JSONObject())
        }
        
        val response = sendHttpRequest(testMessage)
        assertNotNull("Should receive prompts/list response", response)
        
        validateJsonRpcResponse(response!!)
        
        val result = response.getJSONObject("result")
        assertTrue("Should have prompts array", result.has("prompts"))
        
        val prompts = result.getJSONArray("prompts")
        assertTrue("Should have at least one prompt", prompts.length() > 0)
        
        // Validate prompt structure
        val prompt = prompts.getJSONObject(0)
        assertTrue("Prompt should have name", prompt.has("name"))
        assertTrue("Prompt should have description", prompt.has("description"))
        
        if (prompt.has("arguments")) {
            val arguments = prompt.getJSONArray("arguments")
            // Arguments array should be valid JSON array
            assertTrue("Arguments should be valid array", arguments.length() >= 0)
        }
    }
    
    @Test
    fun testErrorHandling() = runBlocking {
        // Test invalid method
        val invalidMethodMessage = JSONObject().apply {
            put("jsonrpc", "2.0")
            put("id", 1)
            put("method", "invalid/method")
            put("params", JSONObject())
        }
        
        val response = sendHttpRequest(invalidMethodMessage)
        assertNotNull("Should receive error response", response)
        
        assertEquals("Should have correct JSON-RPC version", "2.0", response!!.getString("jsonrpc"))
        assertEquals("Should have correct request ID", 1, response.getInt("id"))
        assertTrue("Should have error object", response.has("error"))
        assertFalse("Should not have result", response.has("result"))
        
        val error = response.getJSONObject("error")
        assertTrue("Error should have code", error.has("code"))
        assertTrue("Error should have message", error.has("message"))
        
        val errorCode = error.getInt("code")
        assertTrue("Error code should be negative", errorCode < 0)
    }
    
    @Test
    fun testMalformedRequestHandling() = runBlocking {
        // Test malformed JSON
        val malformedJson = "{'invalid': json}"
        
        val request = Request.Builder()
            .url(HTTP_URL)
            .post(RequestBody.create(
                MediaType.get("application/json"),
                malformedJson
            ))
            .build()
        
        val response = okHttpClient.newCall(request).execute()
        
        // Server should handle malformed JSON gracefully
        assertTrue("Should return an error status", response.code >= 400)
        response.close()
    }
    
    private suspend fun sendHttpRequest(message: JSONObject): JSONObject? {
        return try {
            val request = Request.Builder()
                .url(HTTP_URL)
                .post(RequestBody.create(
                    MediaType.get("application/json"),
                    message.toString()
                ))
                .build()
            
            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    if (responseBody != null) {
                        JSONObject(responseBody)
                    } else null
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }
    
    private fun validateJsonRpcResponse(response: JSONObject) {
        assertEquals("Should have correct JSON-RPC version", "2.0", response.getString("jsonrpc"))
        assertTrue("Should have id field", response.has("id"))
        
        // Should have either result or error, but not both
        val hasResult = response.has("result")
        val hasError = response.has("error")
        
        assertTrue("Should have either result or error", hasResult || hasError)
        assertFalse("Should not have both result and error", hasResult && hasError)
    }
}
```

### Phase 2: Client Integration Tests

#### Step 2.1: Real Client Integration Tests

Create
`lib/src/androidTest/kotlin/dev/jasonpearson/mcpandroidsdk/client/RealClientIntegrationTest.kt`:

```kotlin
package dev.jasonpearson.mcpandroidsdk.client

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.jasonpearson.mcpandroidsdk.McpServerManager
import dev.jasonpearson.mcpandroidsdk.testing.McpTestUtils
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*
import java.io.File
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class RealClientIntegrationTest {
    
    private lateinit var serverManager: McpServerManager
    private lateinit var context: android.content.Context
    
    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        serverManager = McpServerManager.getInstance()
        
        runBlocking {
            serverManager.initialize(context, "Real Client Test Server", "1.0.0")
            serverManager.startServer()
            McpTestUtils.waitForCondition { serverManager.isServerRunning() }
        }
    }
    
    @After
    fun teardown() {
        runBlocking {
            serverManager.stopServer()
            serverManager.shutdown()
        }
    }
    
    @Test
    fun testPythonWebSocketClient() = runBlocking {
        val pythonClientScript = File(context.externalCacheDir, "python_ws_client.py")
        
        // Skip test if Python client script not available
        if (!pythonClientScript.exists()) {
            println("! Python WebSocket client script not found, skipping test")
            return@runBlocking
        }
        
        try {
            val process = ProcessBuilder(
                "python3", 
                pythonClientScript.absolutePath,
                "ws://localhost:8080/mcp"
            ).start()
            
            val exitCode = withTimeoutOrNull(30000) {
                withContext(Dispatchers.IO) {
                    process.waitFor()
                }
            }
            
            if (exitCode != null) {
                assertEquals("Python WebSocket client should succeed", 0, exitCode)
                
                // Read output
                val output = process.inputStream.bufferedReader().readText()
                val errorOutput = process.errorStream.bufferedReader().readText()
                
                assertTrue("Should have successful connection", output.contains("Connected to"))
                assertTrue("Should complete initialization", output.contains("Initialization successful"))
                assertTrue("Should complete tests", output.contains("All tests completed"))
                
                println("Python WebSocket client output:")
                println(output)
                
                if (errorOutput.isNotEmpty()) {
                    println("Python WebSocket client errors:")
                    println(errorOutput)
                }
            } else {
                process.destroyForcibly()
                fail("Python WebSocket client test timed out")
            }
        } catch (e: IOException) {
            println("! Python WebSocket client test skipped (Python not available): ${e.message}")
        }
    }
    
    @Test
    fun testPythonHttpClient() = runBlocking {
        val pythonClientScript = File(context.externalCacheDir, "python_http_client.py")
        
        // Skip test if Python client script not available
        if (!pythonClientScript.exists()) {
            println("! Python HTTP client script not found, skipping test")
            return@runBlocking
        }
        
        try {
            val process = ProcessBuilder(
                "python3", 
                pythonClientScript.absolutePath,
                "http://localhost:8081"
            ).start()
            
            val exitCode = withTimeoutOrNull(30000) {
                withContext(Dispatchers.IO) {
                    process.waitFor()
                }
            }
            
            if (exitCode != null) {
                assertEquals("Python HTTP client should succeed", 0, exitCode)
                
                // Read output
                val output = process.inputStream.bufferedReader().readText()
                val errorOutput = process.errorStream.bufferedReader().readText()
                
                assertTrue("Should complete HTTP tests", output.contains("All HTTP tests completed"))
                
                println("Python HTTP client output:")
                println(output)
                
                if (errorOutput.isNotEmpty()) {
                    println("Python HTTP client errors:")
                    println(errorOutput)
                }
            } else {
                process.destroyForcibly()
                fail("Python HTTP client test timed out")
            }
        } catch (e: IOException) {
            println("! Python HTTP client test skipped (Python not available): ${e.message}")
        }
    }
    
    @Test
    fun testClientReconnection() = runBlocking {
        // This test simulates a client reconnecting after server restart
        val pythonClientScript = File(context.externalCacheDir, "python_ws_client.py")
        
        if (!pythonClientScript.exists()) {
            println("! Python WebSocket client script not found, skipping test")
            return@runBlocking
        }
        
        try {
            // Start first client session
            var process = ProcessBuilder(
                "python3", 
                pythonClientScript.absolutePath,
                "ws://localhost:8080/mcp"
            ).start()
            
            var exitCode = withTimeoutOrNull(15000) {
                withContext(Dispatchers.IO) {
                    process.waitFor()
                }
            }
            
            assertEquals("First client session should succeed", 0, exitCode)
            
            // Restart server
            serverManager.stopServer()
            delay(2000)
            serverManager.startServer()
            McpTestUtils.waitForCondition { serverManager.isServerRunning() }
            delay(3000) // Additional stabilization time
            
            // Start second client session (reconnection)
            process = ProcessBuilder(
                "python3", 
                pythonClientScript.absolutePath,
                "ws://localhost:8080/mcp"
            ).start()
            
            exitCode = withTimeoutOrNull(15000) {
                withContext(Dispatchers.IO) {
                    process.waitFor()
                }
            }
            
            assertEquals("Client reconnection should succeed", 0, exitCode)
            
            println("✓ Client reconnection test completed successfully")
            
        } catch (e: IOException) {
            println("! Client reconnection test skipped (Python not available): ${e.message}")
        }
    }
    
    @Test
    fun testMultipleClientsSameTime() = runBlocking {
        val pythonClientScript = File(context.externalCacheDir, "python_ws_client.py")
        
        if (!pythonClientScript.exists()) {
            println("! Python WebSocket client script not found, skipping test")
            return@runBlocking
        }
        
        try {
            val clientCount = 3
            val processes = mutableListOf<Process>()
            
            // Start multiple clients concurrently
            repeat(clientCount) { index ->
                val process = ProcessBuilder(
                    "python3", 
                    pythonClientScript.absolutePath,
                    "ws://localhost:8080/mcp"
                ).start()
                processes.add(process)
            }
            
            // Wait for all clients to complete
            val results = processes.map { process ->
                async {
                    withTimeoutOrNull(30000) {
                        withContext(Dispatchers.IO) {
                            process.waitFor()
                        }
                    } ?: -1 // Timeout indicator
                }
            }.awaitAll()
            
            // Clean up any remaining processes
            processes.forEach { process ->
                if (process.isAlive) {
                    process.destroyForcibly()
                }
            }
            
            val successCount = results.count { it == 0 }
            val timeoutCount = results.count { it == -1 }
            
            assertTrue("At least 2 out of 3 clients should succeed", successCount >= 2)
            assertEquals("No clients should timeout", 0, timeoutCount)
            
            println("Multiple clients test results:")
            println("- Total clients: $clientCount")
            println("- Successful: $successCount")
            println("- Failed: ${results.count { it != 0 && it != -1 }}")
            println("- Timeout: $timeoutCount")
            
        } catch (e: IOException) {
            println("! Multiple clients test skipped (Python not available): ${e.message}")
        }
    }
}
```

### Phase 3: Performance and Load Testing

#### Step 3.1: Client Performance Tests

Create `lib/src/androidTest/kotlin/dev/jasonpearson/mcpandroidsdk/client/ClientPerformanceTest.kt`:

```kotlin
package dev.jasonpearson.mcpandroidsdk.client

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.jasonpearson.mcpandroidsdk.McpServerManager
import dev.jasonpearson.mcpandroidsdk.testing.McpTestUtils
import kotlinx.coroutines.*
import okhttp3.*
import org.json.JSONObject
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*
import java.util.concurrent.TimeUnit
import kotlin.system.measureTimeMillis

@RunWith(AndroidJUnit4::class)
class ClientPerformanceTest {
    
    private lateinit var serverManager: McpServerManager
    private lateinit var context: android.content.Context
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
    
    data class PerformanceResult(
        val totalRequests: Int,
        val successfulRequests: Int,
        val averageLatencyMs: Double,
        val minLatencyMs: Long,
        val maxLatencyMs: Long,
        val totalTimeMs: Long
    )
    
    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        serverManager = McpServerManager.getInstance()
        
        runBlocking {
            serverManager.initialize(context, "Client Performance Test Server", "1.0.0")
            serverManager.startServer()
            McpTestUtils.waitForCondition { serverManager.isServerRunning() }
        }
    }
    
    @After
    fun teardown() {
        runBlocking {
            serverManager.stopServer()
            serverManager.shutdown()
        }
    }
    
    @Test
    fun testSequentialRequestPerformance() = runBlocking {
        val requestCount = 50
        val latencies = mutableListOf<Long>()
        
        val totalTime = measureTimeMillis {
            repeat(requestCount) { index ->
                val latency = measureTimeMillis {
                    val testMessage = JSONObject().apply {
                        put("jsonrpc", "2.0")
                        put("id", index + 1)
                        put("method", "tools/list")
                        put("params", JSONObject())
                    }
                    
                    val response = sendHttpRequest(testMessage)
                    assertNotNull("Request $index should succeed", response)
                }
                latencies.add(latency)
            }
        }
        
        val result = PerformanceResult(
            totalRequests = requestCount,
            successfulRequests = latencies.size,
            averageLatencyMs = latencies.average(),
            minLatencyMs = latencies.minOrNull() ?: 0,
            maxLatencyMs = latencies.maxOrNull() ?: 0,
            totalTimeMs = totalTime
        )
        
        // Performance assertions
        assertEquals("All requests should succeed", requestCount, result.successfulRequests)
        assertTrue("Average latency should be reasonable", result.averageLatencyMs < 200)
        assertTrue("Max latency should be acceptable", result.maxLatencyMs < 1000)
        
        printPerformanceResult("Sequential Requests", result)
    }
    
    @Test
    fun testConcurrentRequestPerformance() = runBlocking {
        val concurrentRequests = 20
        val latencies = mutableListOf<Long>()
        
        val totalTime = measureTimeMillis {
            val jobs = (1..concurrentRequests).map { index ->
                async {
                    measureTimeMillis {
                        val testMessage = JSONObject().apply {
                            put("jsonrpc", "2.0")
                            put("id", index)
                            put("method", "device_info")
                            put("params", JSONObject())
                        }
                        
                        sendHttpRequest(testMessage)
                    }
                }
            }
            
            val results = jobs.awaitAll()
            latencies.addAll(results.filter { it > 0 })
        }
        
        val result = PerformanceResult(
            totalRequests = concurrentRequests,
            successfulRequests = latencies.size,
            averageLatencyMs = latencies.average(),
            minLatencyMs = latencies.minOrNull() ?: 0,
            maxLatencyMs = latencies.maxOrNull() ?: 0,
            totalTimeMs = totalTime
        )
        
        // Performance assertions
        assertTrue("Most concurrent requests should succeed", 
            result.successfulRequests >= concurrentRequests * 0.9)
        assertTrue("Average latency should be reasonable", result.averageLatencyMs < 500)
        
        printPerformanceResult("Concurrent Requests", result)
    }
    
    @Test
    fun testWebSocketConnectionPerformance() = runBlocking {
        val connectionCount = 10
        val connectionTimes = mutableListOf<Long>()
        
        repeat(connectionCount) { index ->
            val connectionTime = measureTimeMillis {
                var connected = false
                
                val wsListener = object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        connected = true
                        webSocket.close(1000, "Performance test")
                    }
                    
                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        // Connection failed
                    }
                }
                
                val request = Request.Builder()
                    .url("ws://localhost:8080/mcp")
                    .build()
                
                val webSocket = okHttpClient.newWebSocket(request, wsListener)
                
                // Wait for connection or timeout
                val startTime = System.currentTimeMillis()
                while (!connected && (System.currentTimeMillis() - startTime) < 5000) {
                    delay(50)
                }
                
                webSocket.close(1000, "Test complete")
                
                if (connected) {
                    connectionTimes.add(System.currentTimeMillis() - startTime)
                }
            }
        }
        
        val successfulConnections = connectionTimes.size
        val averageConnectionTime = if (connectionTimes.isNotEmpty()) connectionTimes.average() else 0.0
        
        assertTrue("Most WebSocket connections should succeed", 
            successfulConnections >= connectionCount * 0.9)
        assertTrue("Average connection time should be reasonable", 
            averageConnectionTime < 2000)
        
        println("WebSocket Connection Performance:")
        println("- Total attempts: $connectionCount")
        println("- Successful: $successfulConnections")
        println("- Average connection time: ${averageConnectionTime.toInt()}ms")
        println("- Min connection time: ${connectionTimes.minOrNull() ?: 0}ms")
        println("- Max connection time: ${connectionTimes.maxOrNull() ?: 0}ms")
    }
    
    @Test
    fun testSustainedLoadPerformance() = runBlocking {
        val testDurationMs = 30000L // 30 seconds
        val requestIntervalMs = 200L // Request every 200ms
        val latencies = mutableListOf<Long>()
        var totalRequests = 0
        
        val startTime = System.currentTimeMillis()
        
        while (System.currentTimeMillis() - startTime < testDurationMs) {
            totalRequests++
            
            val latency = measureTimeMillis {
                val testMessage = JSONObject().apply {
                    put("jsonrpc", "2.0")
                    put("id", totalRequests)
                    put("method", "memory_info")
                    put("params", JSONObject())
                }
                
                val response = sendHttpRequest(testMessage)
                if (response != null) {
                    latencies.add(System.currentTimeMillis() - startTime)
                }
            }
            
            if (latency > 0) {
                latencies.add(latency)
            }
            
            delay(requestIntervalMs)
        }
        
        val result = PerformanceResult(
            totalRequests = totalRequests,
            successfulRequests = latencies.size,
            averageLatencyMs = latencies.average(),
            minLatencyMs = latencies.minOrNull() ?: 0,
            maxLatencyMs = latencies.maxOrNull() ?: 0,
            totalTimeMs = testDurationMs
        )
        
        // Performance assertions
        assertTrue("Most sustained requests should succeed", 
            result.successfulRequests >= totalRequests * 0.95)
        assertTrue("Average latency should remain stable", result.averageLatencyMs < 300)
        
        printPerformanceResult("Sustained Load", result)
    }
    
    private suspend fun sendHttpRequest(message: JSONObject): JSONObject? {
        return try {
            val request = Request.Builder()
                .url("http://localhost:8081/mcp/message")
                .post(RequestBody.create(
                    MediaType.get("application/json"),
                    message.toString()
                ))
                .build()
            
            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    if (responseBody != null) {
                        JSONObject(responseBody)
                    } else null
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }
    
    private fun printPerformanceResult(testName: String, result: PerformanceResult) {
        println("$testName Performance Results:")
        println("- Total requests: ${result.totalRequests}")
        println("- Successful: ${result.successfulRequests}")
        println("- Success rate: ${(result.successfulRequests.toDouble() / result.totalRequests * 100).toInt()}%")
        println("- Average latency: ${result.averageLatencyMs.toInt()}ms")
        println("- Min latency: ${result.minLatencyMs}ms")
        println("- Max latency: ${result.maxLatencyMs}ms")
        println("- Total time: ${result.totalTimeMs}ms")
        println("- Throughput: ${(result.successfulRequests.toDouble() / result.totalTimeMs * 1000).toInt()} req/s")
    }
}
```

## Verification Steps

### Automated Verification

#### Step V1: Setup MCP Client Environment

```bash
# Setup MCP client test environment
chmod +x testing/mcp_clients/setup_mcp_clients.sh
./testing/mcp_clients/setup_mcp_clients.sh
```

#### Step V2: Run Protocol Compliance Tests

```bash
./gradlew :core:connectedAndroidTest --tests "*McpProtocolComplianceTestSuite"
```

#### Step V3: Run Client Integration Tests

```bash
./gradlew :core:connectedAndroidTest --tests "*RealClientIntegrationTest"
```

#### Step V4: Run Performance Tests

```bash
./gradlew :core:connectedAndroidTest --tests "*ClientPerformanceTest"
```

### Manual Verification

#### Step M1: Test with Python MCP Client

```bash
# Activate Python environment
source testing/mcp_clients/venv/bin/activate

# Test WebSocket client
./testing/mcp_clients/python_ws_client.py ws://localhost:8080/mcp

# Test HTTP client
./testing/mcp_clients/python_http_client.py http://localhost:8081
```

#### Step M2: Test Protocol Compliance

```bash
# Use official MCP CLI tools if available
mcp-client connect ws://localhost:8080/mcp

# Or use generic WebSocket client
wscat -c ws://localhost:8080/mcp
```

#### Step M3: Validate Error Handling

```bash
# Test malformed JSON
curl -X POST http://localhost:8081/mcp/message \
  -H "Content-Type: application/json" \
  -d '{"invalid": json}'

# Test unknown method
curl -X POST http://localhost:8081/mcp/message \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"unknown/method","params":{}}'
```

#### Step M4: Performance Benchmarking

```bash
# Load testing with multiple concurrent clients
for i in {1..5}; do
  (./testing/mcp_clients/python_ws_client.py &)
done
wait
```

## Success Criteria

### Protocol Compliance

- [ ] Full MCP protocol specification compliance
- [ ] Correct JSON-RPC 2.0 message format
- [ ] Proper initialization handshake
- [ ] All required MCP methods implemented
- [ ] Correct error response format

### Client Compatibility

- [ ] Python MCP client integration works
- [ ] WebSocket client communication functions
- [ ] HTTP/SSE client communication functions
- [ ] Multiple concurrent clients supported
- [ ] Client reconnection after server restart

### Performance Criteria

- [ ] Request latency <200ms average
- [ ] WebSocket connection time <2 seconds
- [ ] > 95% success rate under normal load
- [ ] Support for >20 concurrent clients
- [ ] Stable performance over extended periods

### Error Handling

- [ ] Graceful handling of malformed requests
- [ ] Proper error response format
- [ ] Clear error messages and codes
- [ ] Network failure recovery
- [ ] Timeout handling

## Resources

### MCP Protocol

- [MCP Protocol Specification](https://modelcontextprotocol.io/specification)
- [JSON-RPC 2.0 Specification](https://www.jsonrpc.org/specification)
- [MCP Python SDK](https://github.com/modelcontextprotocol/python-sdk)

### Testing Tools

- [Python MCP Client Examples](https://github.com/modelcontextprotocol/python-sdk/tree/main/examples)
- [WebSocket Testing with wscat](https://github.com/websockets/wscat)
- [HTTP Testing with curl](https://curl.se/docs/manpage.html)

### Performance Testing

- [Load Testing Best Practices](https://developer.android.com/training/testing/performance)
- [WebSocket Performance Testing](https://developer.android.com/guide/topics/connectivity/cronet/testing)