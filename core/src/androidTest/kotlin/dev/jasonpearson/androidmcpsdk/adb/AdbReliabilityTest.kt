package dev.jasonpearson.androidmcpsdk.adb

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.jasonpearson.androidmcpsdk.core.McpServerManager
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AdbReliabilityTest {

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
            serverManager.initialize(context, "ADB Reliability Test Server", "1.0.0")
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
    fun testServerRestartRecovery() = runBlocking {
        // Verify initial connection
        val initialResult = AdbTestUtils.testSseConnection()
        assertTrue("Initial connection should work", initialResult.success)

        // Stop and restart server
        serverManager.stopServer()
        delay(1000)

        // Verify connection fails while stopped
        val stoppedResult = AdbTestUtils.testSseConnection(timeoutMs = 2000)
        assertFalse("Connection should fail when server is stopped", stoppedResult.success)

        // Restart server
        serverManager.startServer()
        waitForCondition { serverManager.isServerRunning() }
        delay(2000) // Additional time for transport to initialize

        // Verify connection recovery
        val recoveredResult = AdbTestUtils.testSseConnection()
        assertTrue("Connection should recover after restart", recoveredResult.success)

        println("Server restart recovery test:")
        println("- Initial connection: ${initialResult.success}")
        println("- During stop: ${stoppedResult.success}")
        println("- After restart: ${recoveredResult.success}")
    }

    @Test
    fun testMultipleClientReconnection() = runBlocking {
        val clientCount = 3
        val testMessage =
            """
            {
                "jsonrpc": "2.0",
                "id": 1,
                "method": "tools/list",
                "params": {}
            }
        """
                .trimIndent()

        // Test initial connections
        val initialResults =
            (1..clientCount)
                .map { async { AdbTestUtils.sendMcpSseMessage(testMessage) } }
                .awaitAll()

        val initialSuccessCount = initialResults.count { it.success }
        assertTrue(
            "Most initial connections should succeed",
            initialSuccessCount >= clientCount * 0.5,
        )

        // Simulate connection disruption by restarting server
        serverManager.stopServer()
        delay(2000)
        serverManager.startServer()
        waitForCondition { serverManager.isServerRunning() }
        delay(3000) // Additional stabilization time

        // Test reconnections
        val reconnectResults =
            (1..clientCount)
                .map { async { AdbTestUtils.sendMcpSseMessage(testMessage) } }
                .awaitAll()

        val reconnectSuccessCount = reconnectResults.count { it.success }
        assertTrue("Most reconnections should succeed", reconnectSuccessCount >= clientCount * 0.5)

        println("Multiple client reconnection test:")
        println("- Client count: $clientCount")
        println("- Initial success: $initialSuccessCount")
        println("- Reconnect success: $reconnectSuccessCount")
    }

    @Test
    fun testLongRunningConnection() = runBlocking {
        val testDurationMs = 30000L // 30 seconds
        val pingIntervalMs = 3000L // Ping every 3 seconds
        var consecutiveFailures = 0
        var totalPings = 0
        var successfulPings = 0

        val startTime = System.currentTimeMillis()

        while (System.currentTimeMillis() - startTime < testDurationMs) {
            totalPings++

            val pingMessage =
                """
                {
                    "jsonrpc": "2.0",
                    "id": $totalPings,
                    "method": "tools/list",
                    "params": {}
                }
            """
                    .trimIndent()

            val result = AdbTestUtils.sendMcpSseMessage(pingMessage)

            if (result.success) {
                successfulPings++
                consecutiveFailures = 0
            } else {
                consecutiveFailures++

                // Fail test if too many consecutive failures
                if (consecutiveFailures > 5) {
                    fail("Too many consecutive connection failures: $consecutiveFailures")
                }
            }

            delay(pingIntervalMs)
        }

        val successRate = successfulPings.toDouble() / totalPings
        assertTrue("Long-running connection success rate should be reasonable", successRate > 0.7)

        println("Long-running connection test:")
        println("- Duration: ${testDurationMs / 1000}s")
        println("- Total pings: $totalPings")
        println("- Successful pings: $successfulPings")
        println("- Success rate: ${(successRate * 100).toInt()}%")
        println("- Max consecutive failures: $consecutiveFailures")
    }

    private suspend fun waitForCondition(timeoutMs: Long = 10000, condition: () -> Boolean) {
        val startTime = System.currentTimeMillis()
        while (!condition() && (System.currentTimeMillis() - startTime) < timeoutMs) {
            kotlinx.coroutines.delay(100)
        }
    }
}
