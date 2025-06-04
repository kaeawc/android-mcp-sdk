package dev.jasonpearson.androidmcpsdk.debugbridge.tools

import android.content.Context
import android.util.Log
import dev.jasonpearson.androidmcpsdk.core.features.tools.McpToolProvider
import dev.jasonpearson.androidmcpsdk.core.features.tools.addTool
import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.TextContent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.serialization.Serializable

/**
 * Provides network tools for the debug bridge. Includes network inspection, monitoring, and
 * analysis capabilities.
 */
class NetworkToolProvider(private val context: Context) {

    companion object {
        private const val TAG = "NetworkToolProvider"
    }

    private val networkInspector = NetworkInspector(context)
    private val networkReplayEngine = NetworkReplayEngine(context)

    @Serializable
    data class StartMonitoringInput(
        val maxRequests: Int = 1000,
        val captureRequestBody: Boolean = false,
        val captureResponseBody: Boolean = false,
        val domains: List<String> = emptyList(),
        val methods: List<String> = emptyList(),
    )

    @Serializable data class EmptyInput(val placeholder: String? = null)

    @Serializable
    data class GetRequestsInput(
        val domain: String? = null,
        val method: String? = null,
        val statusCode: Int? = null,
        val minDuration: Long? = null,
        val maxDuration: Long? = null,
        val limit: Int = 100,
    )

    @Serializable data class AnalyzeRequestInput(val requestId: String)

    @Serializable
    data class ReplayRequestInput(
        val requestId: String,
        val modifications: NetworkReplayEngine.RequestModifications? = null
    )

    @Serializable
    data class BatchReplayInput(
        val requestIds: List<String>,
        val config: NetworkReplayEngine.BatchConfig = NetworkReplayEngine.BatchConfig(),
        val modifications: Map<String, NetworkReplayEngine.RequestModifications> = emptyMap()
    )

    @Serializable
    data class LoadTestInput(
        val requestId: String,
        val config: NetworkReplayEngine.LoadTestConfig = NetworkReplayEngine.LoadTestConfig(),
        val modifications: NetworkReplayEngine.RequestModifications? = null
    )

    fun registerTools(toolProvider: McpToolProvider) {
        Log.d(TAG, "Registering network tools")

        // Network monitoring control
        toolProvider.addTool<StartMonitoringInput>(
            name = "network_start_monitoring",
            description = "Start monitoring network requests with configurable options",
        ) { input ->
            startNetworkMonitoring(input)
        }

        toolProvider.addTool<EmptyInput>(
            name = "network_stop_monitoring",
            description = "Stop network request monitoring",
        ) { _ ->
            stopNetworkMonitoring()
        }

        // Request inspection
        toolProvider.addTool<GetRequestsInput>(
            name = "network_get_requests",
            description = "Get captured network requests with optional filtering",
        ) { input ->
            getNetworkRequests(input)
        }

        toolProvider.addTool<AnalyzeRequestInput>(
            name = "network_analyze_request",
            description =
                "Analyze a specific network request for performance and security insights",
            required = listOf("requestId"),
        ) { input ->
            analyzeNetworkRequest(input)
        }

        // Network replay tools
        toolProvider.addTool<ReplayRequestInput>(
            name = "network_replay_request",
            description = "Replay a specific network request with optional modifications",
            required = listOf("requestId"),
        ) { input ->
            replayNetworkRequest(input)
        }

        toolProvider.addTool<BatchReplayInput>(
            name = "network_batch_replay",
            description = "Replay multiple network requests in batch with configuration options",
            required = listOf("requestIds"),
        ) { input ->
            batchReplayNetworkRequests(input)
        }

        toolProvider.addTool<LoadTestInput>(
            name = "network_load_test",
            description = "Perform a load test by replaying a request multiple times",
            required = listOf("requestId"),
        ) { input ->
            loadTestNetworkRequest(input)
        }

        Log.d(TAG, "Network tools registered")
    }

    private suspend fun startNetworkMonitoring(input: StartMonitoringInput): CallToolResult {
        try {
            val config =
                NetworkInspector.MonitoringConfig(
                    maxRequests = input.maxRequests,
                    captureRequestBody = input.captureRequestBody,
                    captureResponseBody = input.captureResponseBody,
                    domains = input.domains,
                    methods = input.methods,
                )

            val result = networkInspector.startMonitoring(config)

            val responseText =
                if (result.isSuccess) {
                    buildString {
                        appendLine("✅ Network monitoring started successfully")
                        appendLine()
                        appendLine("Configuration:")
                        appendLine("- Max requests: ${input.maxRequests}")
                        appendLine("- Capture request body: ${input.captureRequestBody}")
                        appendLine("- Capture response body: ${input.captureResponseBody}")
                        if (input.domains.isNotEmpty()) {
                            appendLine("- Filtered domains: ${input.domains.joinToString(", ")}")
                        }
                        if (input.methods.isNotEmpty()) {
                            appendLine("- Filtered methods: ${input.methods.joinToString(", ")}")
                        }
                        appendLine()
                        appendLine("🔗 To use the interceptor, add it to your OkHttpClient:")
                        appendLine("```kotlin")
                        appendLine("val client = OkHttpClient.Builder()")
                        appendLine("    .addInterceptor(networkInspector.createInterceptor())")
                        appendLine("    .build()")
                        appendLine("```")
                    }
                } else {
                    "❌ Failed to start network monitoring: ${result.exceptionOrNull()?.message}"
                }

            return CallToolResult(
                content = listOf(TextContent(text = responseText)),
                isError = result.isFailure,
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error starting network monitoring", e)
            return CallToolResult(
                content =
                    listOf(TextContent(text = "❌ Error starting network monitoring: ${e.message}")),
                isError = true,
            )
        }
    }

    private suspend fun stopNetworkMonitoring(): CallToolResult {
        try {
            val result = networkInspector.stopMonitoring()

            val responseText =
                if (result.isSuccess) {
                    "✅ Network monitoring stopped successfully"
                } else {
                    "❌ Failed to stop network monitoring: ${result.exceptionOrNull()?.message}"
                }

            return CallToolResult(
                content = listOf(TextContent(text = responseText)),
                isError = result.isFailure,
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping network monitoring", e)
            return CallToolResult(
                content =
                    listOf(TextContent(text = "❌ Error stopping network monitoring: ${e.message}")),
                isError = true,
            )
        }
    }

    private suspend fun getNetworkRequests(input: GetRequestsInput): CallToolResult {
        try {
            val filter =
                NetworkInspector.RequestFilter(
                    domain = input.domain,
                    method = input.method,
                    statusCode = input.statusCode,
                    minDuration = input.minDuration,
                    maxDuration = input.maxDuration,
                    limit = input.limit,
                )

            val requests = networkInspector.getNetworkRequests(filter)

            val responseText = buildString {
                appendLine("🌐 Network Requests")
                appendLine("================")
                appendLine()

                if (requests.isEmpty()) {
                    appendLine("No requests found matching the filter criteria.")
                    appendLine()
                    appendLine(
                        "Make sure network monitoring is active and requests are being made."
                    )
                } else {
                    appendLine("Found ${requests.size} requests:")
                    appendLine()

                    requests.forEach { request ->
                        appendLine("📋 Request ${request.id}")
                        appendLine("   URL: ${request.url}")
                        appendLine("   Method: ${request.method}")
                        appendLine("   Status: ${request.responseCode ?: "N/A"}")
                        appendLine("   Duration: ${request.duration ?: "N/A"}ms")
                        appendLine("   Size: ${formatFileSize(request.size)}")
                        if (request.error != null) {
                            appendLine("   ❌ Error: ${request.error}")
                        }
                        appendLine(
                            "   Time: ${
                                SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
                                    .format(Date(request.startTime))
                            }"
                        )
                        appendLine()
                    }
                }

                appendLine("Filter applied:")
                input.domain?.let { appendLine("- Domain: $it") }
                input.method?.let { appendLine("- Method: $it") }
                input.statusCode?.let { appendLine("- Status Code: $it") }
                input.minDuration?.let { appendLine("- Min Duration: ${it}ms") }
                input.maxDuration?.let { appendLine("- Max Duration: ${it}ms") }
                appendLine("- Limit: ${input.limit}")
            }

            return CallToolResult(
                content = listOf(TextContent(text = responseText)),
                isError = false,
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error getting network requests", e)
            return CallToolResult(
                content =
                    listOf(TextContent(text = "❌ Error getting network requests: ${e.message}")),
                isError = true,
            )
        }
    }

    private suspend fun analyzeNetworkRequest(input: AnalyzeRequestInput): CallToolResult {
        try {
            val analysis =
                networkInspector.analyzeRequest(input.requestId)
                    ?: return CallToolResult(
                        content =
                            listOf(TextContent(text = "❌ Request not found: ${input.requestId}")),
                        isError = true,
                    )

            val responseText = buildString {
                appendLine("🔍 Network Request Analysis")
                appendLine("==========================")
                appendLine()

                appendLine("📋 Request Details:")
                appendLine("   ID: ${analysis.request.id}")
                appendLine("   URL: ${analysis.request.url}")
                appendLine("   Method: ${analysis.request.method}")
                appendLine("   Status: ${analysis.request.responseCode ?: "N/A"}")
                appendLine()

                appendLine("⚡ Performance Metrics:")
                appendLine("   Total Time: ${analysis.performance.totalTime}ms")
                appendLine("   Response Size: ${formatFileSize(analysis.performance.responseSize)}")
                if (analysis.performance.bandwidth > 0) {
                    appendLine(
                        "   Bandwidth: ${
                            String.format(
                                Locale.US,
                                "%.2f",
                                analysis.performance.bandwidth,
                            )
                        } bytes/sec"
                    )
                }
                appendLine()

                appendLine("🔒 Security Analysis:")
                appendLine("   HTTPS: ${if (analysis.security.isHttps) "✅ Yes" else "❌ No"}")
                appendLine(
                    "   Has Auth Header: ${if (analysis.security.hasAuthHeader) "✅ Yes" else "❌ No"}"
                )
                if (analysis.security.sensitiveHeaders.isNotEmpty()) {
                    appendLine(
                        "   Sensitive Headers: ${
                            analysis.security.sensitiveHeaders.joinToString(
                                ", "
                            )
                        }"
                    )
                }
                appendLine()

                appendLine("📤 Request Headers:")
                analysis.request.headers.forEach { (key, value) ->
                    val maskedValue =
                        if (
                            key.lowercase().contains("authorization") ||
                                key.lowercase().contains("cookie") ||
                                key.lowercase().contains("token")
                        ) {
                            "***MASKED***"
                        } else value
                    appendLine("   $key: $maskedValue")
                }
                appendLine()

                if (analysis.request.responseHeaders.isNotEmpty()) {
                    appendLine("📥 Response Headers:")
                    analysis.request.responseHeaders.forEach { (key, value) ->
                        appendLine("   $key: $value")
                    }
                    appendLine()
                }

                if (analysis.request.error != null) {
                    appendLine("❌ Error: ${analysis.request.error}")
                }
            }

            return CallToolResult(
                content = listOf(TextContent(text = responseText)),
                isError = false,
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error analyzing network request", e)
            return CallToolResult(
                content =
                    listOf(TextContent(text = "❌ Error analyzing network request: ${e.message}")),
                isError = true,
            )
        }
    }

    private suspend fun replayNetworkRequest(input: ReplayRequestInput): CallToolResult {
        try {
            // Get the original request from NetworkInspector
            val originalRequest = networkInspector.getStoredRequest(input.requestId)
                ?: return CallToolResult(
                    content = listOf(TextContent(text = "❌ Request not found: ${input.requestId}")),
                    isError = true,
                )

            val result = networkReplayEngine.replayRequest(originalRequest, input.modifications)

            val responseText = buildString {
                if (result.success) {
                    appendLine("✅ Request ${input.requestId} replayed successfully")
                    appendLine()
                    appendLine("🔄 Replay Details:")
                    appendLine("   Replay ID: ${result.replayId}")
                    appendLine("   Original Status: ${result.originalRequest.responseCode}")
                    appendLine("   Replay Status: ${result.replayedRequest.responseCode}")
                    appendLine("   Original Duration: ${result.originalRequest.duration}ms")
                    appendLine("   Replay Duration: ${result.replayedRequest.duration}ms")

                    result.comparison?.let { comparison ->
                        appendLine()
                        appendLine("📊 Comparison:")
                        appendLine("   Status Match: ${if (comparison.statusCodeMatch) "✅" else "❌"}")
                        appendLine("   Headers Match: ${if (comparison.headersMatch) "✅" else "❌"}")
                        appendLine("   Body Match: ${if (comparison.bodyMatch) "✅" else "❌"}")
                        if (comparison.differences.isNotEmpty()) {
                            appendLine("   Differences:")
                            comparison.differences.forEach { diff ->
                                appendLine("     - $diff")
                            }
                        }
                    }
                } else {
                    appendLine("❌ Failed to replay request: ${result.error}")
                }
            }

            return CallToolResult(
                content = listOf(TextContent(text = responseText)),
                isError = !result.success,
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error replaying network request", e)
            return CallToolResult(
                content =
                    listOf(TextContent(text = "❌ Error replaying network request: ${e.message}")),
                isError = true,
            )
        }
    }

    private suspend fun batchReplayNetworkRequests(input: BatchReplayInput): CallToolResult {
        try {
            // Get the original requests from NetworkInspector
            val originalRequests = mutableListOf<NetworkInspector.NetworkRequest>()
            val missingRequestIds = mutableListOf<String>()

            input.requestIds.forEach { requestId ->
                val request: NetworkInspector.NetworkRequest? =
                    networkInspector.getStoredRequest(requestId)
                if (request != null) {
                    originalRequests.add(request)
                } else {
                    missingRequestIds.add(requestId)
                }
            }

            if (missingRequestIds.isNotEmpty()) {
                return CallToolResult(
                    content = listOf(
                        TextContent(
                            text = "❌ Requests not found: ${missingRequestIds.joinToString(", ")}"
                        )
                    ),
                    isError = true,
                )
            }

            val result = networkReplayEngine.batchReplay(
                originalRequests,
                input.config,
                input.modifications
            )

            val responseText = buildString {
                appendLine("🔄 Batch Replay Results")
                appendLine("=====================")
                appendLine()
                appendLine("📊 Summary:")
                appendLine("   Batch ID: ${result.batchId}")
                appendLine("   Total Requests: ${result.totalRequests}")
                appendLine("   Successful: ${result.successfulRequests}")
                appendLine("   Failed: ${result.failedRequests}")
                appendLine("   Total Duration: ${result.totalDuration}ms")
                appendLine(
                    "   Average Request Time: ${
                        String.format(
                            "%.2f",
                            result.averageRequestTime
                        )
                    }ms"
                )
                appendLine()

                if (result.results.isNotEmpty()) {
                    appendLine("📋 Individual Results:")
                    result.results.forEach { replayResult ->
                        val status = if (replayResult.success) "✅" else "❌"
                        appendLine("   $status ${replayResult.originalRequest.id}: ${replayResult.replayedRequest.responseCode} (${replayResult.replayedRequest.duration}ms)")
                        if (!replayResult.success && replayResult.error != null) {
                            appendLine("      Error: ${replayResult.error}")
                        }
                    }
                }
            }

            return CallToolResult(
                content = listOf(TextContent(text = responseText)),
                isError = result.failedRequests > 0,
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error batch replaying network requests", e)
            return CallToolResult(
                content =
                    listOf(TextContent(text = "❌ Error batch replaying network requests: ${e.message}")),
                isError = true,
            )
        }
    }

    private suspend fun loadTestNetworkRequest(input: LoadTestInput): CallToolResult {
        try {
            // Get the original request from NetworkInspector
            val originalRequest = networkInspector.getStoredRequest(input.requestId)
                ?: return CallToolResult(
                    content = listOf(TextContent(text = "❌ Request not found: ${input.requestId}")),
                    isError = true,
                )

            val result =
                networkReplayEngine.loadTest(originalRequest, input.config, input.modifications)

            val responseText = buildString {
                appendLine("⚡ Load Test Results")
                appendLine("==================")
                appendLine()
                appendLine("🎯 Test Configuration:")
                appendLine("   Test ID: ${result.testId}")
                appendLine("   Requests per Second: ${result.config.requestsPerSecond}")
                appendLine("   Total Requests: ${result.config.totalRequests}")
                appendLine("   Concurrency: ${result.config.concurrency}")
                appendLine("   Duration: ${result.duration}ms")
                appendLine()

                appendLine("📊 Performance Statistics:")
                appendLine(
                    "   Actual RPS: ${
                        String.format(
                            "%.2f",
                            result.statistics.requestsPerSecond
                        )
                    }"
                )
                appendLine(
                    "   Throughput: ${
                        String.format(
                            "%.2f",
                            result.statistics.throughput
                        )
                    } req/sec"
                )
                appendLine(
                    "   Error Rate: ${
                        String.format(
                            "%.2f%%",
                            result.statistics.errorRate * 100
                        )
                    }"
                )
                appendLine()

                appendLine("⏱️ Response Times:")
                appendLine(
                    "   Average: ${
                        String.format(
                            "%.2f",
                            result.statistics.averageResponseTime
                        )
                    }ms"
                )
                appendLine("   Min: ${result.statistics.minResponseTime}ms")
                appendLine("   Max: ${result.statistics.maxResponseTime}ms")
                appendLine("   P50: ${String.format("%.2f", result.statistics.p50ResponseTime)}ms")
                appendLine("   P95: ${String.format("%.2f", result.statistics.p95ResponseTime)}ms")
                appendLine("   P99: ${String.format("%.2f", result.statistics.p99ResponseTime)}ms")
                appendLine()

                val successCount = result.results.count { it.success }
                val failureCount = result.results.size - successCount
                appendLine("✅ Successful Requests: $successCount")
                if (failureCount > 0) {
                    appendLine("❌ Failed Requests: $failureCount")
                }
            }

            return CallToolResult(
                content = listOf(TextContent(text = responseText)),
                isError = result.statistics.errorRate > 0.5, // Error if more than 50% failed
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error performing load test", e)
            return CallToolResult(
                content =
                    listOf(TextContent(text = "❌ Error performing load test: ${e.message}")),
                isError = true,
            )
        }
    }

    private fun formatFileSize(sizeInBytes: Long): String {
        if (sizeInBytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB")
        val digitGroups = (Math.log10(sizeInBytes.toDouble()) / Math.log10(1024.0)).toInt()
        val unitIndex = minOf(digitGroups, units.size - 1)
        val size = sizeInBytes / Math.pow(1024.0, unitIndex.toDouble())
        return String.format(Locale.US, "%.2f %s", size, units[unitIndex])
    }
}
