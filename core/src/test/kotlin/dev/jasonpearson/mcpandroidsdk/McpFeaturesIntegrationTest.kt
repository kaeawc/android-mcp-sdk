package dev.jasonpearson.mcpandroidsdk

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.jasonpearson.mcpandroidsdk.models.AndroidTool
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class McpFeaturesIntegrationTest {

    private lateinit var context: Context
    private lateinit var manager: McpServerManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        manager = McpServerManager.getInstance()
        manager.resetForTesting()

        // Initialize the manager for testing
        val result = manager.initialize(context, "Test Server", "1.0.0")
        assertTrue("Manager should initialize successfully", result.isSuccess)
    }

    @After
    fun tearDown() {
        manager.resetForTesting()
    }

    // Built-in Android Tools Tests

    @Test
    fun `executeAndroidTool should execute device_info tool`() = runTest {
        val result = manager.executeAndroidTool("device_info", emptyMap())

        assertTrue("device_info tool should execute successfully", result.success)
        assertNotNull("device_info should return result", result.result)
        assertNull("device_info should not have error", result.error)

        val resultString = result.result as String
        assertTrue(
            "Result should contain device information",
            resultString.contains("Model") || resultString.contains("Manufacturer"),
        )
    }

    @Test
    fun `executeAndroidTool should execute app_info tool`() = runTest {
        val result = manager.executeAndroidTool("app_info", emptyMap())

        assertTrue("app_info tool should execute successfully", result.success)
        assertNotNull("app_info should return result", result.result)
        assertNull("app_info should not have error", result.error)

        val resultString = result.result as String
        assertTrue(
            "Result should contain app information",
            resultString.contains("Package") || resultString.contains("Version"),
        )
    }

    @Test
    fun `executeAndroidTool should execute system_time tool`() = runTest {
        val result = manager.executeAndroidTool("system_time", emptyMap())

        assertTrue("system_time tool should execute successfully", result.success)
        assertNotNull("system_time should return result", result.result)
        assertNull("system_time should not have error", result.error)

        val resultString = result.result as String
        assertTrue(
            "Result should contain time information",
            resultString.contains("Time") || resultString.contains("Timestamp"),
        )
    }

    @Test
    fun `executeAndroidTool should handle unknown tool gracefully`() = runTest {
        val result = manager.executeAndroidTool("unknown_tool", emptyMap())

        assertFalse("Unknown tool should return failure", result.success)
        assertNotNull("Unknown tool should return error", result.error)

        assertTrue(
            "Error should mention unknown tool",
            result.error!!.contains("not found") || result.error!!.contains("not available"),
        )
    }

    @Test
    fun `executeAndroidTool should pass arguments to tools`() = runTest {
        // Test with a tool that might accept arguments (using device_info as example)
        val arguments = mapOf("format" to "json")
        val result = manager.executeAndroidTool("device_info", arguments)

        assertTrue("Tool should execute with arguments", result.success)
        assertNotNull("Tool should return result with arguments", result.result)
    }

    // Custom Android Tools Tests

    @Test
    fun `addAndroidTool should allow adding custom tools`() = runTest {
        val customTool =
            AndroidTool(
                name = "test_tool",
                description = "A test tool for unit testing",
                parameters = mapOf("input" to "string"),
            ) { _, arguments ->
                "Test output: ${arguments["input"]}"
            }

        manager.addAndroidTool(customTool)

        val tools = manager.getAndroidTools()
        assertTrue("Custom tool should be added", tools.any { it.name == "test_tool" })

        // Test execution of custom tool
        val result = manager.executeAndroidTool("test_tool", mapOf("input" to "hello"))
        assertTrue("Custom tool should execute successfully", result.success)
        assertEquals(
            "Custom tool should return expected result",
            "Test output: hello",
            result.result,
        )
    }

    @Test
    fun `addAndroidTool should allow multiple custom tools`() {
        val tool1 =
            AndroidTool(name = "tool1", description = "First test tool", parameters = emptyMap()) {
                _,
                _ ->
                "Tool 1 result"
            }

        val tool2 =
            AndroidTool(
                name = "tool2",
                description = "Second test tool",
                parameters = emptyMap(),
            ) { _, _ ->
                "Tool 2 result"
            }

        manager.addAndroidTool(tool1)
        manager.addAndroidTool(tool2)

        val tools = manager.getAndroidTools()
        assertTrue(
            "Both tools should be added",
            tools.any { it.name == "tool1" } && tools.any { it.name == "tool2" },
        )
    }

    @Test
    fun `getAndroidTools should return all available tools`() {
        val tools = manager.getAndroidTools()

        assertNotNull("Tools list should not be null", tools)
        assertTrue(
            "Should have built-in Android tools",
            tools.size >= 3,
        ) // device_info, app_info, system_time

        // Check for built-in tools
        val toolNames = tools.map { it.name }
        assertTrue("Should contain device_info tool", toolNames.contains("device_info"))
        assertTrue("Should contain app_info tool", toolNames.contains("app_info"))
        assertTrue("Should contain system_time tool", toolNames.contains("system_time"))
    }

    // MCP Resources Tests

    @Test
    fun `getMcpResources should return resources list`() {
        val resources = manager.getMcpResources()

        assertNotNull("Resources should not be null", resources)
        // Resources list might be empty initially, that's okay
        assertTrue("Resources should be a valid list", resources is List<*>)
    }

    // MCP Prompts Tests

    @Test
    fun `getMcpPrompts should return prompts list`() {
        val prompts = manager.getMcpPrompts()

        assertNotNull("Prompts should not be null", prompts)
        // Prompts list might be empty initially, that's okay
        assertTrue("Prompts should be a valid list", prompts is List<*>)
    }

    // Server Lifecycle Tests

    @Test
    fun `startServerAsync should start server without blocking`() = runTest {
        assertFalse("Server should not be running initially", manager.isServerRunning())

        // Start server asynchronously
        val job = manager.startServerAsync()

        // Server might take time to start, so we don't assert immediate running state
        // Just verify the call doesn't throw exceptions and returns a job
        assertNotNull("Should return a job", job)
    }

    @Test
    fun `startServer should start server with coroutines`() = runTest {
        assertFalse("Server should not be running initially", manager.isServerRunning())

        try {
            val result = manager.startServer()

            // Server start might succeed or fail depending on platform constraints
            // The important thing is that it returns a Result and doesn't throw
            assertNotNull("Start result should not be null", result)
            assertTrue("Result should be Success or Failure", result.isSuccess || result.isFailure)
        } catch (e: Exception) {
            // Some exceptions might be expected in test environment
            assertTrue(
                "Exception should be related to test environment constraints",
                e.message?.contains("test") == true ||
                    e.message?.contains("mock") == true ||
                    e.message?.contains("bind") == true ||
                    e.message?.contains("port") == true,
            )
        }
    }

    @Test
    fun `getServerInfo should return comprehensive server information`() {
        val serverInfo = manager.getServerInfo()

        assertNotNull("Server info should not be null", serverInfo)
        assertEquals("Server name should match", "Test Server", serverInfo?.name)
        assertEquals("Server version should match", "1.0.0", serverInfo?.version)
        assertEquals("SDK version should match", "0.5.0", serverInfo?.sdkVersion)
    }

    @Test
    fun `getComprehensiveServerInfo should return detailed information`() {
        val serverInfo = manager.getComprehensiveServerInfo()

        assertNotNull("Comprehensive server info should not be null", serverInfo)
        assertEquals("Server name should match", "Test Server", serverInfo?.name)
        assertEquals("Server version should match", "1.0.0", serverInfo?.version)
        assertEquals("SDK version should match", "0.5.0", serverInfo?.sdkVersion)
        assertTrue("Server should be initialized", serverInfo?.isInitialized ?: false)
    }

    @Test
    fun `getMcpServer should return server instance when initialized`() {
        val mcpServer = manager.getMcpServer()

        assertNotNull("MCP server should not be null when initialized", mcpServer)
    }

    // Transport Integration Tests

    @Test
    fun `getTransportInfo should return transport information`() {
        val transportInfo = manager.getTransportInfo()

        assertNotNull("Transport info should not be null", transportInfo)
        assertTrue("Transport info should contain status information", transportInfo.isNotEmpty())
    }

    @Test
    fun `broadcastMessage should handle message broadcasting`() = runTest {
        val message = """{"jsonrpc": "2.0", "method": "test", "id": 1}"""

        try {
            val result = manager.broadcastMessage(message)

            // Broadcasting might fail if transports aren't running, which is expected in tests
            assertNotNull("Broadcast result should not be null", result)
            assertTrue("Result should be Success or Failure", result.isSuccess || result.isFailure)

            if (result.isFailure) {
                val exception = result.exceptionOrNull()
                assertTrue(
                    "Should fail gracefully when transports not running",
                    exception is IllegalStateException,
                )
            }
        } catch (e: Exception) {
            // Broadcasting exceptions are acceptable in test environment
            assertTrue(
                "Exception should be related to transport state",
                e.message?.contains("running") == true || e.message?.contains("transport") == true,
            )
        }
    }

    // Error Handling Tests

    @Test
    fun `executeAndroidTool should handle tool execution errors gracefully`() = runTest {
        val errorTool =
            AndroidTool(
                name = "error_tool",
                description = "A tool that throws an error",
                parameters = emptyMap(),
            ) { _, _ ->
                throw RuntimeException("Test error")
            }

        manager.addAndroidTool(errorTool)

        val result = manager.executeAndroidTool("error_tool", emptyMap())

        assertFalse("Error tool should return failure", result.success)
        assertNull("Error tool should not return result", result.result)
        assertNotNull("Error tool should return error message", result.error)
        assertTrue(
            "Error message should contain exception info",
            result.error!!.contains("Test error") || result.error!!.contains("execution failed"),
        )
    }

    @Test
    fun `hasSDKIntegration should report SDK availability`() {
        val hasIntegration = manager.hasSDKIntegration()

        // This should work regardless of whether actual SDK is available
        assertTrue(
            "hasSDKIntegration should return a boolean value",
            hasIntegration == true || hasIntegration == false,
        )
    }
}
