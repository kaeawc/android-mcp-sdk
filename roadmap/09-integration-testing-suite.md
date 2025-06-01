# 09 - Integration Testing Suite

## Status: `[ ]` Not Started

## Objective

Develop a comprehensive integration testing suite for the Android MCP SDK that validates the
complete MCP server functionality, including initialization, server lifecycle, transport layer
communication, and MCP protocol compliance. The test suite should cover both automated
unit/integration tests and manual testing procedures.

## Requirements

### Technical Requirements

- **AndroidX Test Framework**: Use AndroidX Test for instrumented tests
- **JUnit 5**: Modern testing framework with parameterized tests
- **MockK**: Kotlin-first mocking framework for Android
- **Testcontainers**: For integration testing with external MCP clients
- **Espresso**: UI testing for sample app interactions
- **Robolectric**: Unit testing with Android framework dependencies

### Testing Scope

- **Server Lifecycle**: Initialization, startup, shutdown, lifecycle management
- **Transport Layer**: WebSocket and HTTP/SSE communication
- **MCP Protocol**: Tools, resources, prompts, notifications
- **Android Integration**: Permissions, lifecycle callbacks, background behavior
- **Error Handling**: Network failures, malformed messages, edge cases
- **Performance**: Memory usage, response times, concurrent connections

### Test Categories

1. **Unit Tests**: Individual component testing
2. **Integration Tests**: Multi-component interaction testing
3. **End-to-End Tests**: Full MCP client-server communication
4. **Performance Tests**: Load testing and benchmarking
5. **UI Tests**: Sample app functionality testing

## Dependencies

**Must Complete First:**

- [03-jsonrpc-message-parsing.md](03-jsonrpc-message-parsing.md) - JSON-RPC parsing must be complete
- [04-websocket-transport-implementation.md](04-websocket-transport-implementation.md) - WebSocket
  transport needed
- [05-http-sse-transport-implementation.md](05-http-sse-transport-implementation.md) - HTTP/SSE
  transport needed

**Should Complete First:**

- [01-resource-subscription-logic.md](01-resource-subscription-logic.md) - Resource subscription
  testing
- [07-filesystem-resources-permissions.md](07-filesystem-resources-permissions.md) - File system
  testing

## Implementation Steps

### Phase 1: Test Infrastructure Setup

#### Step 1.1: Configure Test Dependencies

```kotlin
// core/build.gradle.kts
dependencies {
    // AndroidX Test
    testImplementation("androidx.test:core:1.5.0")
    testImplementation("androidx.test.ext:junit:1.1.5")
    testImplementation("androidx.test:runner:1.5.2")
    testImplementation("androidx.test:rules:1.5.0")
    
    // JUnit 5
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.10.1")
    
    // MockK
    testImplementation("io.mockk:mockk:1.13.8")
    testImplementation("io.mockk:mockk-android:1.13.8")
    
    // Robolectric
    testImplementation("org.robolectric:robolectric:4.11.1")
    
    // Coroutines Testing
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    
    // AndroidX Test for instrumented tests
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("androidx.test:core:1.5.0")
    
    // JSON Testing
    testImplementation("org.json:json:20231013")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}
```

#### Step 1.2: Create Test Base Classes

Create `lib/src/test/kotlin/dev/jasonpearson/mcpandroidsdk/testing/`:

**TestBase.kt:**

```kotlin
package dev.jasonpearson.mcpandroidsdk.testing

import android.content.Context
import io.mockk.MockKAnnotations
import io.mockk.mockk
import kotlinx.coroutines.test.TestCoroutineDispatcher
import kotlinx.coroutines.test.TestCoroutineScope
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.robolectric.RuntimeEnvironment

abstract class TestBase {
    protected lateinit var testScope: TestCoroutineScope
    protected lateinit var testDispatcher: TestCoroutineDispatcher
    protected lateinit var mockContext: Context
    
    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        testDispatcher = TestCoroutineDispatcher()
        testScope = TestCoroutineScope(testDispatcher)
        mockContext = RuntimeEnvironment.getApplication()
        
        setupTest()
    }
    
    @AfterEach
    fun tearDown() {
        testScope.cleanupTestCoroutines()
        tearDownTest()
    }
    
    protected open fun setupTest() {}
    protected open fun tearDownTest() {}
}
```

**McpTestUtils.kt:**

```kotlin
package dev.jasonpearson.mcpandroidsdk.testing

import kotlinx.coroutines.delay
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.MockResponse
import java.net.ServerSocket

object McpTestUtils {
    
    fun findAvailablePort(): Int {
        ServerSocket(0).use { socket ->
            return socket.localPort
        }
    }
    
    suspend fun waitForCondition(
        timeoutMs: Long = 5000,
        intervalMs: Long = 100,
        condition: suspend () -> Boolean
    ): Boolean {
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            if (condition()) return true
            delay(intervalMs)
        }
        return false
    }
    
    fun createMockMcpResponse(id: String, result: String): MockResponse {
        return MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody("""
                {
                    "jsonrpc": "2.0",
                    "id": "$id",
                    "result": $result
                }
            """.trimIndent())
    }
}
```

### Phase 2: Unit Tests

#### Step 2.1: Server Manager Tests

Create `lib/src/test/kotlin/dev/jasonpearson/mcpandroidsdk/McpServerManagerTest.kt`:

```kotlin
package dev.jasonpearson.mcpandroidsdk

import dev.jasonpearson.mcpandroidsdk.testing.TestBase
import io.mockk.*
import kotlinx.coroutines.test.runBlockingTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach

class McpServerManagerTest : TestBase() {
    
    private lateinit var serverManager: McpServerManager
    
    @BeforeEach
    override fun setupTest() {
        serverManager = McpServerManager.getInstance()
        // Reset singleton state for testing
        serverManager.shutdown()
    }
    
    @Test
    fun `initialize creates server with default configuration`() = testScope.runBlockingTest {
        val result = serverManager.initialize(mockContext)
        
        assertTrue(result.isSuccess)
        assertTrue(serverManager.isInitialized())
        assertEquals("Android MCP Server", serverManager.getServerInfo().name)
    }
    
    @Test
    fun `initialize with custom configuration sets correct values`() = testScope.runBlockingTest {
        val customName = "Custom Test Server"
        val customVersion = "2.0.0"
        
        val result = serverManager.initialize(
            context = mockContext,
            serverName = customName,
            serverVersion = customVersion
        )
        
        assertTrue(result.isSuccess)
        val serverInfo = serverManager.getServerInfo()
        assertEquals(customName, serverInfo.name)
        assertEquals(customVersion, serverInfo.version)
    }
    
    @Test
    fun `startServer starts successfully when initialized`() = testScope.runBlockingTest {
        serverManager.initialize(mockContext)
        
        val result = serverManager.startServer()
        
        assertTrue(result.isSuccess)
        assertTrue(serverManager.isServerRunning())
    }
    
    @Test
    fun `startServer fails when not initialized`() = testScope.runBlockingTest {
        val result = serverManager.startServer()
        
        assertTrue(result.isFailure)
        assertFalse(serverManager.isServerRunning())
    }
    
    @Test
    fun `stopServer stops running server`() = testScope.runBlockingTest {
        serverManager.initialize(mockContext)
        serverManager.startServer()
        
        val result = serverManager.stopServer()
        
        assertTrue(result.isSuccess)
        assertFalse(serverManager.isServerRunning())
    }
    
    @Test
    fun `double initialization throws exception`() = testScope.runBlockingTest {
        serverManager.initialize(mockContext)
        
        assertThrows<IllegalStateException> {
            runBlockingTest {
                serverManager.initialize(mockContext)
            }
        }
    }
}
```

#### Step 2.2: Android Server Tests

Create `lib/src/test/kotlin/dev/jasonpearson/mcpandroidsdk/McpAndroidServerTest.kt`:

```kotlin
package dev.jasonpearson.mcpandroidsdk

import dev.jasonpearson.mcpandroidsdk.testing.TestBase
import dev.jasonpearson.mcpandroidsdk.testing.McpTestUtils
import io.mockk.*
import kotlinx.coroutines.test.runBlockingTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class McpAndroidServerTest : TestBase() {
    
    private lateinit var mcpServer: McpAndroidServer
    
    @BeforeEach
    override fun setupTest() {
        mcpServer = McpAndroidServer(
            context = mockContext,
            serverName = "Test Server",
            serverVersion = "1.0.0"
        )
    }
    
    @Test
    fun `server initializes with correct information`() {
        assertEquals("Test Server", mcpServer.getServerInfo().name)
        assertEquals("1.0.0", mcpServer.getServerInfo().version)
        assertFalse(mcpServer.isRunning())
    }
    
    @Test
    fun `start server begins transport listeners`() = testScope.runBlockingTest {
        val result = mcpServer.start()
        
        assertTrue(result.isSuccess)
        assertTrue(mcpServer.isRunning())
        
        // Verify transport is listening
        val transportInfo = mcpServer.getTransportInfo()
        assertTrue(transportInfo.contains("WebSocket"))
        assertTrue(transportInfo.contains("HTTP"))
    }
    
    @Test
    fun `stop server shuts down transport`() = testScope.runBlockingTest {
        mcpServer.start()
        assertTrue(mcpServer.isRunning())
        
        val result = mcpServer.stop()
        
        assertTrue(result.isSuccess)
        assertFalse(mcpServer.isRunning())
    }
    
    @Test
    fun `built-in tools are available`() {
        val tools = mcpServer.getAvailableTools()
        
        assertTrue(tools.contains("device_info"))
        assertTrue(tools.contains("app_info"))
        assertTrue(tools.contains("system_time"))
        assertTrue(tools.contains("memory_info"))
        assertTrue(tools.contains("battery_info"))
    }
    
    @ParameterizedTest
    @ValueSource(strings = ["device_info", "app_info", "system_time", "memory_info", "battery_info"])
    fun `built-in tools execute successfully`(toolName: String) = testScope.runBlockingTest {
        val result = mcpServer.executeAndroidTool(toolName, emptyMap())
        
        assertTrue(result.success)
        assertNotNull(result.result)
        assertFalse(result.result.isEmpty())
    }
    
    @Test
    fun `custom tools can be added and executed`() = testScope.runBlockingTest {
        val customTool = AndroidTool(
            name = "test_tool",
            description = "Test tool",
            parameters = mapOf("input" to "string")
        ) { _, args ->
            "Test result: ${args["input"]}"
        }
        
        mcpServer.addAndroidTool(customTool)
        
        val tools = mcpServer.getAvailableTools()
        assertTrue(tools.contains("test_tool"))
        
        val result = mcpServer.executeAndroidTool("test_tool", mapOf("input" to "hello"))
        assertTrue(result.success)
        assertEquals("Test result: hello", result.result)
    }
}
```

### Phase 3: Integration Tests

#### Step 3.1: Transport Integration Tests

Create
`lib/src/androidTest/kotlin/dev/jasonpearson/mcpandroidsdk/transport/TransportIntegrationTest.kt`:

```kotlin
package dev.jasonpearson.mcpandroidsdk.transport

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.jasonpearson.mcpandroidsdk.McpServerManager
import dev.jasonpearson.mcpandroidsdk.testing.McpTestUtils
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import okhttp3.*
import okio.ByteString
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class TransportIntegrationTest {
    
    private lateinit var serverManager: McpServerManager
    private lateinit var context: android.content.Context
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()
    
    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        serverManager = McpServerManager.getInstance()
        
        runBlocking {
            serverManager.initialize(context, "Test Server", "1.0.0")
            serverManager.startServer()
            
            // Wait for server to be ready
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
    fun testWebSocketConnection() = runBlocking {
        val wsListener = object : WebSocketListener() {
            var connected = false
            var messageReceived = false
            var lastMessage = ""
            
            override fun onOpen(webSocket: WebSocket, response: Response) {
                connected = true
                
                // Send initialize request
                val initMessage = """
                    {
                        "jsonrpc": "2.0",
                        "id": 1,
                        "method": "initialize",
                        "params": {
                            "protocolVersion": "2024-11-05",
                            "capabilities": {},
                            "clientInfo": {"name": "test-client", "version": "1.0.0"}
                        }
                    }
                """.trimIndent()
                
                webSocket.send(initMessage)
            }
            
            override fun onMessage(webSocket: WebSocket, text: String) {
                messageReceived = true
                lastMessage = text
            }
            
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                fail("WebSocket connection failed: ${t.message}")
            }
        }
        
        val request = Request.Builder()
            .url("ws://localhost:8080/mcp")
            .build()
        
        val webSocket = okHttpClient.newWebSocket(request, wsListener)
        
        // Wait for connection and response
        assertTrue("WebSocket should connect", 
            McpTestUtils.waitForCondition { wsListener.connected })
        assertTrue("Should receive initialization response", 
            McpTestUtils.waitForCondition { wsListener.messageReceived })
        
        // Verify response format
        assertTrue("Response should contain jsonrpc", 
            wsListener.lastMessage.contains("\"jsonrpc\":\"2.0\""))
        
        webSocket.close(1000, "Test complete")
    }
    
    @Test
    fun testHttpSseConnection() = runBlocking {
        // Test HTTP POST for sending messages
        val postRequest = Request.Builder()
            .url("http://localhost:8081/mcp/message")
            .post(RequestBody.create(
                MediaType.get("application/json"),
                """
                {
                    "jsonrpc": "2.0",
                    "id": 1,
                    "method": "tools/list",
                    "params": {}
                }
                """.trimIndent()
            ))
            .build()
        
        val response = okHttpClient.newCall(postRequest).execute()
        assertTrue("HTTP POST should succeed", response.isSuccessful)
        
        // Test SSE endpoint for receiving events
        val sseRequest = Request.Builder()
            .url("http://localhost:8081/mcp/events")
            .header("Accept", "text/event-stream")
            .build()
        
        val sseResponse = okHttpClient.newCall(sseRequest).execute()
        assertTrue("SSE connection should succeed", sseResponse.isSuccessful)
        assertEquals("text/event-stream", sseResponse.header("Content-Type"))
        
        sseResponse.close()
    }
    
    @Test
    fun testToolsListRequest() = runBlocking {
        // Test via WebSocket
        val wsListener = object : WebSocketListener() {
            var response = ""
            
            override fun onOpen(webSocket: WebSocket, response: Response) {
                val toolsListMessage = """
                    {
                        "jsonrpc": "2.0",
                        "id": 2,
                        "method": "tools/list",
                        "params": {}
                    }
                """.trimIndent()
                
                webSocket.send(toolsListMessage)
            }
            
            override fun onMessage(webSocket: WebSocket, text: String) {
                response = text
            }
        }
        
        val request = Request.Builder()
            .url("ws://localhost:8080/mcp")
            .build()
        
        val webSocket = okHttpClient.newWebSocket(request, wsListener)
        
        // Wait for response
        assertTrue("Should receive tools list response",
            McpTestUtils.waitForCondition { wsListener.response.isNotEmpty() })
        
        // Verify built-in tools are present
        assertTrue("Response should contain device_info tool",
            wsListener.response.contains("device_info"))
        assertTrue("Response should contain app_info tool",
            wsListener.response.contains("app_info"))
        
        webSocket.close(1000, "Test complete")
    }
}
```

### Phase 4: End-to-End Tests

#### Step 4.1: MCP Protocol Compliance Tests

Create
`lib/src/androidTest/kotlin/dev/jasonpearson/mcpandroidsdk/protocol/McpProtocolComplianceTest.kt`:

```kotlin
package dev.jasonpearson.mcpandroidsdk.protocol

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.jasonpearson.mcpandroidsdk.McpServerManager
import dev.jasonpearson.mcpandroidsdk.testing.McpTestUtils
import kotlinx.coroutines.runBlocking
import okhttp3.*
import org.json.JSONObject
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class McpProtocolComplianceTest {
    
    private lateinit var serverManager: McpServerManager
    private lateinit var context: android.content.Context
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
    
    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        serverManager = McpServerManager.getInstance()
        
        runBlocking {
            serverManager.initialize(context, "Protocol Test Server", "1.0.0")
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
    fun testMcpInitializeHandshake() = runBlocking {
        val wsListener = object : WebSocketListener() {
            var initResponse = ""
            var initialized = false
            
            override fun onOpen(webSocket: WebSocket, response: Response) {
                val initMessage = """
                    {
                        "jsonrpc": "2.0",
                        "id": 1,
                        "method": "initialize",
                        "params": {
                            "protocolVersion": "2024-11-05",
                            "capabilities": {
                                "roots": {
                                    "listChanged": true
                                },
                                "sampling": {}
                            },
                            "clientInfo": {
                                "name": "test-client",
                                "version": "1.0.0"
                            }
                        }
                    }
                """.trimIndent()
                
                webSocket.send(initMessage)
            }
            
            override fun onMessage(webSocket: WebSocket, text: String) {
                if (!initialized) {
                    initResponse = text
                    initialized = true
                    
                    // Send initialized notification
                    webSocket.send("""
                        {
                            "jsonrpc": "2.0",
                            "method": "notifications/initialized"
                        }
                    """.trimIndent())
                }
            }
        }
        
        val request = Request.Builder()
            .url("ws://localhost:8080/mcp")
            .build()
        
        val webSocket = okHttpClient.newWebSocket(request, wsListener)
        
        assertTrue("Should receive initialize response",
            McpTestUtils.waitForCondition { wsListener.initialized })
        
        val response = JSONObject(wsListener.initResponse)
        assertEquals("2.0", response.getString("jsonrpc"))
        assertEquals(1, response.getInt("id"))
        assertTrue("Should have result", response.has("result"))
        
        val result = response.getJSONObject("result")
        assertTrue("Should have protocolVersion", result.has("protocolVersion"))
        assertTrue("Should have capabilities", result.has("capabilities"))
        assertTrue("Should have serverInfo", result.has("serverInfo"))
        
        webSocket.close(1000, "Test complete")
    }
    
    @Test
    fun testToolsLifecycle() = runBlocking {
        var toolsListResponse = ""
        var toolCallResponse = ""
        
        val wsListener = object : WebSocketListener() {
            var step = 0
            
            override fun onOpen(webSocket: WebSocket, response: Response) {
                // Step 1: List tools
                step = 1
                webSocket.send("""
                    {
                        "jsonrpc": "2.0",
                        "id": 1,
                        "method": "tools/list",
                        "params": {}
                    }
                """.trimIndent())
            }
            
            override fun onMessage(webSocket: WebSocket, text: String) {
                when (step) {
                    1 -> {
                        toolsListResponse = text
                        step = 2
                        
                        // Step 2: Call a tool
                        webSocket.send("""
                            {
                                "jsonrpc": "2.0",
                                "id": 2,
                                "method": "tools/call",
                                "params": {
                                    "name": "device_info",
                                    "arguments": {}
                                }
                            }
                        """.trimIndent())
                    }
                    2 -> {
                        toolCallResponse = text
                    }
                }
            }
        }
        
        val request = Request.Builder()
            .url("ws://localhost:8080/mcp")
            .build()
        
        val webSocket = okHttpClient.newWebSocket(request, wsListener)
        
        // Wait for both responses
        assertTrue("Should receive tools list",
            McpTestUtils.waitForCondition { toolsListResponse.isNotEmpty() })
        assertTrue("Should receive tool call response",
            McpTestUtils.waitForCondition { toolCallResponse.isNotEmpty() })
        
        // Verify tools list response
        val listResponse = JSONObject(toolsListResponse)
        assertEquals("2.0", listResponse.getString("jsonrpc"))
        assertTrue("Should have tools array", 
            listResponse.getJSONObject("result").has("tools"))
        
        // Verify tool call response
        val callResponse = JSONObject(toolCallResponse)
        assertEquals("2.0", callResponse.getString("jsonrpc"))
        assertTrue("Should have content", 
            callResponse.getJSONObject("result").has("content"))
        
        webSocket.close(1000, "Test complete")
    }
    
    @Test
    fun testResourcesLifecycle() = runBlocking {
        // Add a test resource first
        serverManager.addFileResource(
            uri = "test://sample.txt",
            name = "Sample Resource",
            description = "Test resource for integration testing",
            filePath = context.filesDir.absolutePath + "/sample.txt",
            mimeType = "text/plain"
        )
        
        var resourcesListResponse = ""
        var resourceReadResponse = ""
        
        val wsListener = object : WebSocketListener() {
            var step = 0
            
            override fun onOpen(webSocket: WebSocket, response: Response) {
                // Step 1: List resources
                step = 1
                webSocket.send("""
                    {
                        "jsonrpc": "2.0",
                        "id": 1,
                        "method": "resources/list",
                        "params": {}
                    }
                """.trimIndent())
            }
            
            override fun onMessage(webSocket: WebSocket, text: String) {
                when (step) {
                    1 -> {
                        resourcesListResponse = text
                        step = 2
                        
                        // Step 2: Read a resource
                        webSocket.send("""
                            {
                                "jsonrpc": "2.0",
                                "id": 2,
                                "method": "resources/read",
                                "params": {
                                    "uri": "test://sample.txt"
                                }
                            }
                        """.trimIndent())
                    }
                    2 -> {
                        resourceReadResponse = text
                    }
                }
            }
        }
        
        val request = Request.Builder()
            .url("ws://localhost:8080/mcp")
            .build()
        
        val webSocket = okHttpClient.newWebSocket(request, wsListener)
        
        // Wait for both responses
        assertTrue("Should receive resources list",
            McpTestUtils.waitForCondition { resourcesListResponse.isNotEmpty() })
        assertTrue("Should receive resource read response",
            McpTestUtils.waitForCondition { resourceReadResponse.isNotEmpty() })
        
        // Verify responses
        val listResponse = JSONObject(resourcesListResponse)
        assertTrue("Should have resources array",
            listResponse.getJSONObject("result").has("resources"))
        
        val readResponse = JSONObject(resourceReadResponse)
        assertTrue("Should have contents array",
            readResponse.getJSONObject("result").has("contents"))
        
        webSocket.close(1000, "Test complete")
    }
}
```

### Phase 5: Performance Tests

#### Step 5.1: Load Testing

Create `lib/src/test/kotlin/dev/jasonpearson/mcpandroidsdk/performance/LoadTest.kt`:

```kotlin
package dev.jasonpearson.mcpandroidsdk.performance

import dev.jasonpearson.mcpandroidsdk.testing.TestBase
import dev.jasonpearson.mcpandroidsdk.McpServerManager
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runBlockingTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Assertions.*
import kotlin.system.measureTimeMillis

class LoadTest : TestBase() {
    
    private lateinit var serverManager: McpServerManager
    
    @BeforeEach
    override fun setupTest() {
        serverManager = McpServerManager.getInstance()
        runBlocking {
            serverManager.initialize(mockContext, "Load Test Server", "1.0.0")
            serverManager.startServer()
        }
    }
    
    @Test
    fun `concurrent tool execution performance`() = testScope.runBlockingTest {
        val concurrentRequests = 50
        val executionTimes = mutableListOf<Long>()
        
        val jobs = (1..concurrentRequests).map { index ->
            async {
                val time = measureTimeMillis {
                    serverManager.executeAndroidTool("device_info", emptyMap())
                }
                executionTimes.add(time)
            }
        }
        
        jobs.awaitAll()
        
        // Verify all requests completed
        assertEquals(concurrentRequests, executionTimes.size)
        
        // Performance assertions
        val averageTime = executionTimes.average()
        val maxTime = executionTimes.maxOrNull() ?: 0L
        
        assertTrue("Average execution time should be under 100ms", averageTime < 100)
        assertTrue("Max execution time should be under 500ms", maxTime < 500)
        
        println("Load test results:")
        println("- Concurrent requests: $concurrentRequests")
        println("- Average time: ${averageTime.toInt()}ms")
        println("- Max time: ${maxTime}ms")
        println("- Min time: ${executionTimes.minOrNull()}ms")
    }
    
    @Test
    fun `memory usage during extended operation`() = testScope.runBlockingTest {
        val runtime = Runtime.getRuntime()
        val initialMemory = runtime.totalMemory() - runtime.freeMemory()
        
        // Execute tools repeatedly
        repeat(100) {
            serverManager.executeAndroidTool("memory_info", emptyMap())
            serverManager.executeAndroidTool("device_info", emptyMap())
            serverManager.executeAndroidTool("app_info", emptyMap())
        }
        
        // Force garbage collection
        runtime.gc()
        delay(100)
        
        val finalMemory = runtime.totalMemory() - runtime.freeMemory()
        val memoryIncrease = finalMemory - initialMemory
        val memoryIncreasePercent = (memoryIncrease.toDouble() / initialMemory) * 100
        
        println("Memory usage test:")
        println("- Initial memory: ${initialMemory / 1024 / 1024}MB")
        println("- Final memory: ${finalMemory / 1024 / 1024}MB")
        println("- Memory increase: ${memoryIncrease / 1024 / 1024}MB (${memoryIncreasePercent.toInt()}%)")
        
        // Memory should not increase by more than 50%
        assertTrue("Memory increase should be reasonable", memoryIncreasePercent < 50)
    }
}
```

## Verification Steps

### Automated Verification

#### Step V1: Run Unit Tests

```bash
./gradlew :core:testDebugUnitTest --tests "*McpServerManagerTest"
./gradlew :core:testDebugUnitTest --tests "*McpAndroidServerTest"
```

#### Step V2: Run Integration Tests

```bash
./gradlew :core:connectedAndroidTest --tests "*TransportIntegrationTest"
./gradlew :core:connectedAndroidTest --tests "*McpProtocolComplianceTest"
```

#### Step V3: Run Performance Tests

```bash
./gradlew :core:testDebugUnitTest --tests "*LoadTest"
```

#### Step V4: Generate Test Reports

```bash
./gradlew :core:testDebugUnitTest
# Reports available at: lib/build/reports/tests/testDebugUnitTest/index.html

./gradlew :core:connectedAndroidTest  
# Reports available at: lib/build/reports/androidTests/connected/index.html
```

### Manual Verification

#### Step M1: Verify Test Coverage

- **Unit Test Coverage**: Should cover >90% of McpServerManager and McpAndroidServer
- **Integration Coverage**: Should test all transport endpoints and MCP methods
- **Performance Coverage**: Should validate memory usage and response times

#### Step M2: Review Test Results

- **All unit tests pass**: ✓
- **All integration tests pass**: ✓
- **All performance tests pass**: ✓
- **No memory leaks detected**: ✓
- **Response times within acceptable limits**: ✓

#### Step M3: Test with Real MCP Client

```bash
# Forward ports for external testing
adb forward tcp:8080 tcp:8080
adb forward tcp:8081 tcp:8081

# Test with wscat (WebSocket)
npx wscat -c ws://localhost:8080/mcp

# Test with curl (HTTP)
curl -X POST http://localhost:8081/mcp/message \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/list","params":{}}'
```

#### Step M4: Validate Test Documentation

- **Test README exists**: Documents how to run tests
- **Test configuration documented**: Coverage thresholds, CI integration
- **Performance benchmarks documented**: Expected response times and memory usage
- **Troubleshooting guide exists**: Common test failures and solutions

## Success Criteria

### Functional Criteria

- [ ] All unit tests pass with >90% code coverage
- [ ] All integration tests pass consistently
- [ ] All performance tests meet defined benchmarks
- [ ] MCP protocol compliance verified with real clients
- [ ] Transport layer communication tested end-to-end
- [ ] Error handling paths tested and verified

### Performance Criteria

- [ ] Tool execution <100ms average response time
- [ ] Memory usage increase <50% during extended operation
- [ ] Server supports >50 concurrent connections
- [ ] Startup time <2 seconds on average device

### Quality Criteria

- [ ] Test suite runs in CI/CD pipeline
- [ ] Test reports generated automatically
- [ ] Performance regression detection configured
- [ ] Test documentation complete and up-to-date

## Resources

### Testing Documentation

- [AndroidX Test Guide](https://developer.android.com/training/testing)
- [JUnit 5 User Guide](https://junit.org/junit5/docs/current/user-guide/)
- [MockK Documentation](https://mockk.io/)
- [Robolectric Documentation](http://robolectric.org/)

### MCP Testing

- [MCP Protocol Specification](https://modelcontextprotocol.io/specification)
- [MCP Test Examples](https://github.com/modelcontextprotocol/kotlin-sdk/tree/main/src/test)

### Performance Testing

- [Android Performance Testing](https://developer.android.com/training/testing/performance)
- [Memory Profiling](https://developer.android.com/studio/profile/memory-profiler)

### CI/CD Integration

- [GitHub Actions Android](https://github.com/actions/setup-java)
- [Gradle Test Reports](https://docs.gradle.org/current/userguide/java_testing.html#test_reporting)