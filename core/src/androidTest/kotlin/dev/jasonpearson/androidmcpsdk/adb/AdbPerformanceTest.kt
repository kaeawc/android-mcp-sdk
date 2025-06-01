package dev.jasonpearson.androidmcpsdk.adb

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.jasonpearson.androidmcpsdk.core.McpServerManager
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AdbPerformanceTest {

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
            serverManager.initialize(context, "ADB Performance Test Server", "1.0.0")
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
    fun testLatencyPerformance() = runBlocking {
        val results = AdbTestUtils.performLatencyTest(iterations = 10)

        // SSE performance assertions
        assertTrue(
            "SSE success rate should be high",
            results.sseSuccessRate > 0.8
        )
        assertTrue(
            "SSE average latency should be acceptable",
            results.sseAverage < 1000
        )

        println("Latency Performance Results:")
        println("SSE:")
        println("  - Success Rate: ${(results.sseSuccessRate * 100).toInt()}%")
        println("  - Average: ${results.sseAverage.toInt()}ms")
        println("  - Min: ${results.sseMin}ms")
        println("  - Max: ${results.sseMax}ms")
    }

    @Test
    fun testConcurrentConnections() = runBlocking {
        val concurrentClients = 5
        val results = mutableListOf<AdbTestUtils.ConnectionTestResult>()

        val jobs = (1..concurrentClients).map { clientId ->
            async {
                val testMessage = """
                    {
                        "jsonrpc": "2.0",
                        "id": $clientId,
                        "method": "tools/list",
                        "params": {}
                    }
                """.trimIndent()

                AdbTestUtils.sendMcpSseMessage(testMessage)
            }
        }

        val connectionResults = jobs.awaitAll()
        results.addAll(connectionResults)

        val successCount = results.count { it.success }
        val successRate = successCount.toDouble() / concurrentClients
        val averageLatency = results.filter { it.success }.map { it.latencyMs }.average()

        assertTrue("Most concurrent connections should succeed", successRate > 0.6)
        assertTrue("Average latency should remain reasonable", averageLatency < 2000)

        println("Concurrent connections test:")
        println("- Clients: $concurrentClients")
        println("- Success rate: ${(successRate * 100).toInt()}%")
        println("- Average latency: ${averageLatency.toInt()}ms")
        println("- Successful connections: $successCount")
    }

    @Test
    fun testSustainedLoad() = runBlocking {
        val testDurationMs = 15000L // 15 seconds for faster testing
        val requestIntervalMs = 1000L // Request every second
        val results = mutableListOf<AdbTestUtils.ConnectionTestResult>()

        val startTime = System.currentTimeMillis()
        var requestCount = 0

        while (System.currentTimeMillis() - startTime < testDurationMs) {
            requestCount++
            val testMessage = """
                {
                    "jsonrpc": "2.0",
                    "id": $requestCount,
                    "method": "device_info",
                    "params": {}
                }
            """.trimIndent()

            val result = AdbTestUtils.sendMcpSseMessage(testMessage)
            results.add(result)

            delay(requestIntervalMs)
        }

        val successCount = results.count { it.success }
        val successRate = successCount.toDouble() / results.size
        val averageLatency = results.filter { it.success }.map { it.latencyMs }.average()

        assertTrue("Sustained load success rate should be reasonable", successRate > 0.7)
        assertTrue("Average latency should remain stable", averageLatency < 2000)

        println("Sustained load test:")
        println("- Duration: ${testDurationMs / 1000}s")
        println("- Total requests: ${results.size}")
        println("- Success rate: ${(successRate * 100).toInt()}%")
        println("- Average latency: ${averageLatency.toInt()}ms")
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