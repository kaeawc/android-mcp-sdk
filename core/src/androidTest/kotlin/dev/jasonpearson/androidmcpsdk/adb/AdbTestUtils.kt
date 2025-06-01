package dev.jasonpearson.androidmcpsdk.adb

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.system.measureTimeMillis
import kotlinx.coroutines.delay
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

object AdbTestUtils {

    data class ConnectionTestResult(
        val success: Boolean,
        val latencyMs: Long,
        val error: String? = null,
    )

    data class PortForwardingStatus(val ssePort: Int, val sseReachable: Boolean)

    private val okHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()

    /** Test SSE endpoint connectivity and measure latency */
    suspend fun testSseConnection(port: Int = 8080, timeoutMs: Long = 5000): ConnectionTestResult {
        return try {
            val latency = measureTimeMillis {
                val request = Request.Builder().url("http://localhost:$port/mcp").get().build()

                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("HTTP request failed: ${response.code}")
                    }
                }
            }

            ConnectionTestResult(success = true, latencyMs = latency)
        } catch (e: Exception) {
            ConnectionTestResult(success = false, latencyMs = -1, error = e.message)
        }
    }

    /** Send an MCP message via HTTP SSE and measure round-trip time */
    suspend fun sendMcpSseMessage(message: String, port: Int = 8080): ConnectionTestResult {
        return try {
            val latency = measureTimeMillis {
                val request =
                    Request.Builder()
                        .url("http://localhost:$port/mcp")
                        .post(message.toRequestBody("application/json".toMediaType()))
                        .build()

                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("MCP SSE request failed: ${response.code}")
                    }

                    // Validate response format
                    val responseBody = response.body?.string()
                    if (responseBody != null && responseBody.isNotEmpty()) {
                        val json = JSONObject(responseBody)
                        if (!json.has("jsonrpc")) {
                            throw IOException("Invalid MCP response format")
                        }
                    }
                }
            }

            ConnectionTestResult(success = true, latencyMs = latency)
        } catch (e: Exception) {
            ConnectionTestResult(success = false, latencyMs = -1, error = e.message)
        }
    }

    /** Test port forwarding status for SSE */
    suspend fun checkPortForwardingStatus(ssePort: Int = 8080): PortForwardingStatus {
        val sseResult = testSseConnection(ssePort)

        return PortForwardingStatus(ssePort = ssePort, sseReachable = sseResult.success)
    }

    /** Perform comprehensive latency testing */
    suspend fun performLatencyTest(iterations: Int = 10, ssePort: Int = 8080): LatencyTestResults {
        val sseLatencies = mutableListOf<Long>()

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

        // Test SSE latency
        repeat(iterations) {
            val result = sendMcpSseMessage(testMessage, ssePort)
            if (result.success) {
                sseLatencies.add(result.latencyMs)
            }
            delay(100) // Small delay between tests
        }

        return LatencyTestResults(sseLatencies = sseLatencies, iterations = iterations)
    }

    data class LatencyTestResults(val sseLatencies: List<Long>, val iterations: Int) {
        val sseAverage: Double
            get() = if (sseLatencies.isNotEmpty()) sseLatencies.average() else -1.0

        val sseMin: Long
            get() = sseLatencies.minOrNull() ?: -1

        val sseMax: Long
            get() = sseLatencies.maxOrNull() ?: -1

        val sseSuccessRate: Double
            get() = sseLatencies.size.toDouble() / iterations
    }
}
