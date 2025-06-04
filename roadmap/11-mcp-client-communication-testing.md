# 11 - MCP Client Communication Testing

## Status: `[ ]` Not Started

## Objective

Develop comprehensive end-to-end testing for MCP client communication with the Android MCP server
using `fast-agent`, the only complete MCP client implementation.
This includes testing full protocol compliance, message flow, error handling, and ensuring compatibility with the complete MCP ecosystem.

## Requirements

### Technical Requirements

- **fast-agent MCP Client**: Integration with the only complete MCP client implementation (
  fast-agent)
- **Full Protocol Compliance**: Complete MCP specification compliance testing
- **Message Flow**: Bidirectional communication testing (client ↔ server)
- **Error Scenarios**: Testing malformed messages, network failures, timeouts
- **Performance**: Latency, throughput, and resource usage testing

### Testing Scope

- **MCP Protocol Methods**: Initialize, tools, resources, prompts, notifications, sampling
- **Client Compatibility**: Complete MCP specification through fast-agent
- **Transport Protocols**: WebSocket and HTTP/SSE client communication
- **Lifecycle Management**: Connection, session management, cleanup
- **Error Handling**: Protocol errors, network failures, timeout scenarios
- **Performance**: Response times, concurrent connections, resource usage

### Test Categories

1. **Protocol Compliance Tests**: Full MCP specification adherence verified through fast-agent
2. **Client Integration Tests**: fast-agent client interactions
3. **Message Flow Tests**: Request/response patterns
4. **Error Handling Tests**: Failure scenario testing
5. **Performance Tests**: Load and stress testing with fast-agent

## Dependencies

**Must Complete First:**

- [09-integration-testing-suite.md](09-integration-testing-suite.md) - Testing infrastructure
- [10-adb-port-forwarding-testing.md](10-adb-port-forwarding-testing.md) - ADB connectivity

**Already Complete:**

- Transport implementations (handled by official MCP Kotlin SDK)
- JSON-RPC message parsing (handled by official MCP Kotlin SDK)

## Implementation Steps

### Phase 1: fast-agent MCP Client Test Infrastructure

#### Step 1.1: Setup fast-agent Client Test Environment

Create `testing/fast_agent_client/`:

**setup_fast_agent_client.sh:**

```bash
#!/bin/bash

# Android MCP SDK - fast-agent Client Test Environment Setup
set -e

CLIENTS_DIR="testing/fast_agent_client"
PYTHON_VENV="$CLIENTS_DIR/venv"

echo "Setting up fast-agent MCP client test environment..."

# Create directory structure
mkdir -p "$CLIENTS_DIR"

# Setup fast-agent MCP client
setup_fast_agent_client() {
    echo "Setting up fast-agent MCP client..."
    
    if ! command -v python3 &> /dev/null; then
        echo "Error: Python3 not found. Please install Python 3.8+."
        exit 1
    fi
    
    # Create virtual environment
    python3 -m venv "$PYTHON_VENV"
    source "$PYTHON_VENV/bin/activate"
    
    # Install fast-agent - the only complete MCP client implementation
    pip install --upgrade pip
    pip install fast-agent
    
    echo "✓ fast-agent MCP client environment ready"
}

# Verify fast-agent installation
verify_fast_agent() {
    echo "Verifying fast-agent installation..."
    
    source "$PYTHON_VENV/bin/activate"
    
    if python3 -c "import fastagent" 2>/dev/null; then
        echo "✓ fast-agent import successful"
        
        # Get version info
        fast_agent_version=$(python3 -c "import fastagent; print(fastagent.__version__)" 2>/dev/null || echo "unknown")
        echo "✓ fast-agent version: $fast_agent_version"
        
        return 0
    else
        echo "✗ fast-agent import failed"
        return 1
    fi
}

# Create fast-agent test scripts
create_fast_agent_scripts() {
    echo "Creating fast-agent test scripts..."
    
    # fast-agent WebSocket test client
    cat > "$CLIENTS_DIR/fast_agent_ws_test.py" << 'EOF'
#!/usr/bin/env python3
"""
fast-agent WebSocket MCP Client for testing Android MCP SDK
"""

import asyncio
import sys
from fastagent import FastAgent
from fastagent.adapters.mcp import MCPWebSocketAdapter
from typing import Dict, Any, Optional

class FastAgentTestClient:
    def __init__(self, uri: str = "ws://localhost:8080/mcp"):
        self.uri = uri
        self.agent = None
        self.adapter = None
        
    async def connect(self):
        """Connect to MCP server using fast-agent"""
        try:
            # Create MCP WebSocket adapter
            self.adapter = MCPWebSocketAdapter(self.uri)
            
            # Create FastAgent with MCP adapter
            self.agent = FastAgent(
                adapter=self.adapter,
                client_info={
                    "name": "fast-agent Test Client",
                    "version": "1.0.0"
                }
            )
            
            # Connect and initialize
            await self.agent.connect()
            print(f"✓ Connected to {self.uri} via fast-agent")
            return True
            
        except Exception as e:
            print(f"✗ Connection failed: {e}")
            return False
    
    async def disconnect(self):
        """Disconnect from MCP server"""
        if self.agent:
            await self.agent.disconnect()
            print("✓ Disconnected")
    
    async def list_tools(self):
        """List available tools"""
        try:
            tools = await self.agent.list_tools()
            print(f"✓ Listed {len(tools)} tools")
            return tools
        except Exception as e:
            print(f"✗ List tools failed: {e}")
            return []
    
    async def call_tool(self, name: str, arguments: Dict[str, Any] = None):
        """Call a tool"""
        try:
            result = await self.agent.call_tool(name, arguments or {})
            print(f"✓ Tool {name} executed successfully")
            return result
        except Exception as e:
            print(f"✗ Tool {name} failed: {e}")
            return None
    
    async def list_resources(self):
        """List available resources"""
        try:
            resources = await self.agent.list_resources()
            print(f"✓ Listed {len(resources)} resources")
            return resources
        except Exception as e:
            print(f"✗ List resources failed: {e}")
            return []
    
    async def read_resource(self, uri: str):
        """Read a resource"""
        try:
            content = await self.agent.read_resource(uri)
            print(f"✓ Resource {uri} read successfully")
            return content
        except Exception as e:
            print(f"✗ Resource {uri} read failed: {e}")
            return None
    
    async def list_prompts(self):
        """List available prompts"""
        try:
            prompts = await self.agent.list_prompts()
            print(f"✓ Listed {len(prompts)} prompts")
            return prompts
        except Exception as e:
            print(f"✗ List prompts failed: {e}")
            return []

async def main():
    """Main test function"""
    if len(sys.argv) > 1:
        uri = sys.argv[1]
    else:
        uri = "ws://localhost:8080/mcp"
    
    client = FastAgentTestClient(uri)
    
    try:
        # Connect using fast-agent
        if not await client.connect():
            return 1
        
        # Test tools
        tools = await client.list_tools()
        if tools:
            print(f"✓ Found {len(tools)} tools")
            
            # Test calling device_info tool
            device_info = await client.call_tool("device_info")
            if device_info:
                print("✓ device_info tool call successful")
        
        # Test resources
        resources = await client.list_resources()
        if resources:
            print(f"✓ Found {len(resources)} resources")
            
            # Test reading first resource if available
            if resources:
                first_resource_uri = resources[0].get('uri')
                if first_resource_uri:
                    content = await client.read_resource(first_resource_uri)
                    if content:
                        print(f"✓ Resource {first_resource_uri} read successful")
        
        # Test prompts
        prompts = await client.list_prompts()
        if prompts:
            print(f"✓ Found {len(prompts)} prompts")
        
        print("\n✓ All fast-agent tests completed")
        return 0
        
    except KeyboardInterrupt:
        print("\n! Test interrupted")
        return 1
    finally:
        await client.disconnect()

if __name__ == "__main__":
    exit(asyncio.run(main()))
EOF
    
    # fast-agent HTTP test client
    cat > "$CLIENTS_DIR/fast_agent_http_test.py" << 'EOF'
#!/usr/bin/env python3
"""
fast-agent HTTP MCP Client for testing Android MCP SDK
"""

import asyncio
import sys
from fastagent import FastAgent
from fastagent.adapters.mcp import MCPHTTPAdapter
from typing import Dict, Any, Optional

class FastAgentHttpTestClient:
    def __init__(self, base_url: str = "http://localhost:8081"):
        self.base_url = base_url.rstrip('/')
        self.agent = None
        self.adapter = None
    
    async def connect(self):
        """Connect to MCP server using fast-agent HTTP adapter"""
        try:
            # Create MCP HTTP adapter
            self.adapter = MCPHTTPAdapter(f"{self.base_url}/mcp")
            
            # Create FastAgent with MCP adapter
            self.agent = FastAgent(
                adapter=self.adapter,
                client_info={
                    "name": "fast-agent HTTP Test Client",
                    "version": "1.0.0"
                }
            )
            
            # Connect and initialize
            await self.agent.connect()
            print(f"✓ Connected to {self.base_url} via fast-agent HTTP")
            return True
            
        except Exception as e:
            print(f"✗ HTTP Connection failed: {e}")
            return False
    
    async def disconnect(self):
        """Disconnect from MCP server"""
        if self.agent:
            await self.agent.disconnect()
            print("✓ HTTP Disconnected")
    
    async def run_tests(self):
        """Run HTTP test suite"""
        try:
            # Test tools
            tools = await self.agent.list_tools()
            print(f"✓ HTTP: Found {len(tools)} tools")
            
            if tools:
                # Test calling a tool
                result = await self.agent.call_tool("device_info")
                if result:
                    print("✓ HTTP: device_info tool call successful")
            
            # Test resources
            resources = await self.agent.list_resources()
            print(f"✓ HTTP: Found {len(resources)} resources")
            
            # Test prompts
            prompts = await self.agent.list_prompts()
            print(f"✓ HTTP: Found {len(prompts)} prompts")
            
            return True
            
        except Exception as e:
            print(f"✗ HTTP tests failed: {e}")
            return False

async def main():
    """Main test function"""
    if len(sys.argv) > 1:
        base_url = sys.argv[1]
    else:
        base_url = "http://localhost:8081"
    
    client = FastAgentHttpTestClient(base_url)
    
    try:
        if await client.connect():
            if await client.run_tests():
                print("\n✓ All fast-agent HTTP tests completed")
                return 0
            else:
                print("\n✗ fast-agent HTTP tests failed")
                return 1
        else:
            print("\n✗ fast-agent HTTP connection failed")
            return 1
    finally:
        await client.disconnect()

if __name__ == "__main__":
    asyncio.run(main())
EOF
    
    # Make scripts executable
    chmod +x "$CLIENTS_DIR/fast_agent_ws_test.py"
    chmod +x "$CLIENTS_DIR/fast_agent_http_test.py"
    
    echo "✓ fast-agent test scripts created"
}

# Main execution
main() {
    setup_fast_agent_client
    
    if verify_fast_agent; then
        create_fast_agent_scripts
        
        echo ""
        echo "fast-agent MCP client test environment setup complete!"
        echo ""
        echo "To activate Python environment:"
        echo "  source $PYTHON_VENV/bin/activate"
        echo ""
        echo "To run WebSocket test client:"
        echo "  $CLIENTS_DIR/fast_agent_ws_test.py"
        echo ""
        echo "To run HTTP test client:"
        echo "  $CLIENTS_DIR/fast_agent_http_test.py"
    else
        echo ""
        echo "✗ fast-agent setup failed"
        exit 1
    fi
}

main "$@"
```

#### Step 1.2: Create fast-agent Protocol Compliance Test Suite

Create `lib/src/androidTest/kotlin/dev/jasonpearson/mcpandroidsdk/client/`:

**FastAgentProtocolComplianceTestSuite.kt:**

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
class FastAgentProtocolComplianceTestSuite {
    
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
            serverManager.initialize(context, "fast-agent Protocol Compliance Test Server", "1.0.0")
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
    fun testFastAgentInitializationHandshake() = runBlocking {
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
                            put("name", "fast-agent Protocol Test Client")
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
    fun testFastAgentToolsListCompliance() = runBlocking {
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
        
        // Verify built-in tools are present (fast-agent can see all tools)
        val toolNames = (0 until tools.length()).map { 
            tools.getJSONObject(it).getString("name") 
        }
        assertTrue("Should have device_info tool", toolNames.contains("device_info"))
        assertTrue("Should have app_info tool", toolNames.contains("app_info"))
        assertTrue("Should have system_info tool", toolNames.contains("system_info"))
        assertTrue("Should have memory_info tool", toolNames.contains("memory_info"))
    }
    
    @Test
    fun testFastAgentToolCallCompliance() = runBlocking {
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
    fun testFastAgentResourcesListCompliance() = runBlocking {
        // Add a test resource first
        serverManager.addFileResource(
            uri = "test://sample.txt",
            name = "Sample Resource",
            description = "Test resource for fast-agent protocol compliance",
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
    fun testFastAgentResourceReadCompliance() = runBlocking {
        // Add and populate a test resource
        val testContent = "This is test content for fast-agent protocol compliance testing."
        val testFile = java.io.File(context.filesDir, "fast_agent_test.txt")
        testFile.writeText(testContent)
        
        serverManager.addFileResource(
            uri = "test://fast_agent_test.txt",
            name = "fast-agent Test Resource",
            description = "Resource for testing fast-agent protocol compliance",
            filePath = testFile.absolutePath,
            mimeType = "text/plain"
        )
        
        val testMessage = JSONObject().apply {
            put("jsonrpc", "2.0")
            put("id", 1)
            put("method", "resources/read")
            put("params", JSONObject().apply {
                put("uri", "test://fast_agent_test.txt")
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
        
        assertEquals("Content URI should match", "test://fast_agent_test.txt", 
            contentItem.getString("uri"))
        assertEquals("Content mimeType should match", "text/plain", 
            contentItem.getString("mimeType"))
    }
    
    @Test
    fun testFastAgentPromptsListCompliance() = runBlocking {
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
`lib/src/androidTest/kotlin/dev/jasonpearson/mcpandroidsdk/client/FastAgentIntegrationTest.kt`:

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
class FastAgentIntegrationTest {
    
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
        val pythonClientScript = File(context.externalCacheDir, "fast_agent_ws_test.py")
        
        // Skip test if Python client script not available
        if (!pythonClientScript.exists()) {
            println("! fast-agent WebSocket client script not found, skipping test")
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
              assertEquals("fast-agent WebSocket client should succeed", 0, exitCode)
                
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
          println("! Python WebSocket client test skipped (fast-agent not available): ${e.message}")
        }
    }
    
    @Test
    fun testPythonHttpClient() = runBlocking {
        val pythonClientScript = File(context.externalCacheDir, "fast_agent_http_test.py")
        
        // Skip test if Python client script not available
        if (!pythonClientScript.exists()) {
            println("! fast-agent HTTP client script not found, skipping test")
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
              assertEquals("fast-agent HTTP client should succeed", 0, exitCode)
                
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
      val pythonClientScript = File(context.externalCacheDir, "fast_agent_ws_test.py")
        
        if (!pythonClientScript.exists()) {
          println("! fast-agent WebSocket client script not found, skipping test")
            return@runBlocking
        }
        
        try {
          // Start first fast-agent client session
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

          assertEquals("First fast-agent client session should succeed", 0, exitCode)
            
            // Restart server
            serverManager.stopServer()
            delay(2000)
            serverManager.startServer()
            McpTestUtils.waitForCondition { serverManager.isServerRunning() }
            delay(3000) // Additional stabilization time

          // Start second fast-agent client session (reconnection)
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

          assertEquals("fast-agent client reconnection should succeed", 0, exitCode)

          println("✓ fast-agent client reconnection test completed successfully")
            
        } catch (e: IOException) {
          println("! fast-agent client reconnection test skipped (Python not available): ${e.message}")
        }
    }
    
    @Test
    fun testMultipleFastAgentClientsSameTime() = runBlocking {
      val pythonClientScript = File(context.externalCacheDir, "fast_agent_ws_test.py")
        
        if (!pythonClientScript.exists()) {
          println("! fast-agent WebSocket client script not found, skipping test")
            return@runBlocking
        }
        
        try {
            val clientCount = 3
            val processes = mutableListOf<Process>()

          // Start multiple fast-agent clients concurrently
            repeat(clientCount) { index ->
                val process = ProcessBuilder(
                    "python3", 
                    pythonClientScript.absolutePath,
                    "ws://localhost:8080/mcp"
                ).start()
                processes.add(process)
            }

          // Wait for all fast-agent clients to complete
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

          println("Multiple fast-agent clients test results:")
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

Create
`lib/src/androidTest/kotlin/dev/jasonpearson/mcpandroidsdk/client/FastAgentPerformanceTest.kt`:

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
class FastAgentPerformanceTest {
    
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
# Setup fast-agent test environment
chmod +x testing/fast_agent_client/setup_fast_agent_client.sh
./testing/fast_agent_client/setup_fast_agent_client.sh
```

#### Step V2: Run Protocol Compliance Tests

```bash
./gradlew :core:connectedAndroidTest --tests "*FastAgentProtocolComplianceTestSuite"
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
./testing/mcp_clients/fast_agent_http_test.py http://localhost:8081
```

#### Step M2: Test Protocol Compliance

```bash
# Use fast-agent CLI directly
fast-agent connect ws://localhost:8080/mcp

# Verify full MCP specification support
fast-agent --version
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
# Load testing with multiple concurrent fast-agent clients
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

- [ ] fast-agent client integration works
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

- [fast-agent Client Examples](https://github.com/modelcontextprotocol/python-sdk/tree/main/examples)
- [WebSocket Testing with wscat](https://github.com/websockets/wscat)
- [HTTP Testing with curl](https://curl.se/docs/manpage.html)

### Performance Testing

- [Load Testing Best Practices](https://developer.android.com/training/testing/performance)
- [WebSocket Performance Testing](https://developer.android.com/guide/topics/connectivity/cronet/testing)