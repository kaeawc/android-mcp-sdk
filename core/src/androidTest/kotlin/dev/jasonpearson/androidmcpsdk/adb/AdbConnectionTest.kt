package dev.jasonpearson.androidmcpsdk.adb

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.jasonpearson.androidmcpsdk.core.McpServerManager
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AdbConnectionTest {

    private lateinit var serverManager: McpServerManager
    private lateinit var context: android.content.Context

    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        serverManager = McpServerManager.getInstance()

        runBlocking {
            // Reset any previous state
            try {
                if (serverManager.isInitialized()) {
                    serverManager.stopServer()
                }
            } catch (e: Exception) {
                // Ignore cleanup errors
            }

            // Initialize and start server
            serverManager.initialize(context, "ADB Test Server", "1.0.0")
            serverManager.startServer()

            // Wait for server to be ready
            waitForCondition { serverManager.isServerRunning() }
        }
    }

    @After
    fun teardown() {
        runBlocking {
            try {
                serverManager.stopServer()
            } catch (e: Exception) {
                // Ignore teardown errors
            }
        }
    }

    @Test
    fun testBasicSseConnection() = runBlocking {
        val result = AdbTestUtils.testSseConnection()

        assertTrue("SSE connection should succeed", result.success)
        assertTrue("Latency should be reasonable", result.latencyMs < 2000)
        assertNull("No connection errors", result.error)

        println("SSE connection test:")
        println("- Success: ${result.success}")
        println("- Latency: ${result.latencyMs}ms")
    }

    @Test
    fun testPortForwardingStatus() = runBlocking {
        val status = AdbTestUtils.checkPortForwardingStatus()

        assertTrue("SSE should be reachable", status.sseReachable)

        println("Port forwarding status:")
        println("- SSE (${status.ssePort}): ${status.sseReachable}")
    }

    @Test
    fun testMcpSseMessage() = runBlocking {
        val testMessage = """
            {
                "jsonrpc": "2.0",
                "id": 1,
                "method": "tools/list",
                "params": {}
            }
        """.trimIndent()

        val result = AdbTestUtils.sendMcpSseMessage(testMessage)

        assertTrue("MCP SSE message should succeed", result.success)
        assertTrue("Round-trip time should be reasonable", result.latencyMs < 2000)
        assertNull("No message errors", result.error)

        println("MCP SSE message test:")
        println("- Success: ${result.success}")
        println("- Round-trip time: ${result.latencyMs}ms")
    }

    private suspend fun waitForCondition(
        timeoutMs: Long = 10000,
        condition: () -> Boolean
    ) {
        val startTime = System.currentTimeMillis()
        while (!condition() && (System.currentTimeMillis() - startTime) < timeoutMs) {
            kotlinx.coroutines.delay(100)
        }
    }
}