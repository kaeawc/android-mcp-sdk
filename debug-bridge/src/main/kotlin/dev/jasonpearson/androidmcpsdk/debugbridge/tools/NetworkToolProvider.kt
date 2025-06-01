package dev.jasonpearson.androidmcpsdk.debugbridge.tools

import android.content.Context
import android.util.Log
import dev.jasonpearson.androidmcpsdk.core.features.tools.ToolRegistry
import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.TextContent
import io.modelcontextprotocol.kotlin.sdk.Tool
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

/**
 * Provides network tools for the debug bridge.
 * Includes network inspection, monitoring, and analysis capabilities.
 */
class NetworkToolProvider(private val context: Context) {

    companion object {
        private const val TAG = "NetworkToolProvider"
    }

    private val networkInspector = NetworkInspector(context)

    @Serializable
    data class StartMonitoringInput(
        val maxRequests: Int = 1000,
        val captureRequestBody: Boolean = false,
        val captureResponseBody: Boolean = false,
        val domains: List<String> = emptyList(),
        val methods: List<String> = emptyList()
    )

    @Serializable
    data class GetRequestsInput(
        val domain: String? = null,
        val method: String? = null,
        val statusCode: Int? = null,
        val minDuration: Long? = null,
        val maxDuration: Long? = null,
        val limit: Int = 100
    )

    @Serializable
    data class AnalyzeRequestInput(
        val requestId: String
    )

    fun registerTools(registry: ToolRegistry) {
        Log.d(TAG, "Registering network tools")

        // Network monitoring control
        registry.addTool(createStartMonitoringTool()) { arguments ->
            startNetworkMonitoring(
                arguments
            )
        }
        registry.addTool(createStopMonitoringTool()) { arguments -> stopNetworkMonitoring(arguments) }

        // Request inspection
        registry.addTool(createGetRequestsTool()) { arguments -> getNetworkRequests(arguments) }
        registry.addTool(createAnalyzeRequestTool()) { arguments -> analyzeNetworkRequest(arguments) }

        Log.d(TAG, "Network tools registered")
    }

    private fun createStartMonitoringTool(): Tool {
        return Tool(
            name = "network_start_monitoring",
            description = "Start monitoring network requests with configurable options",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    put("type", JsonPrimitive("object"))
                    put("properties", buildJsonObject {
                        put("maxRequests", buildJsonObject {
                            put("type", JsonPrimitive("integer"))
                            put(
                                "description",
                                JsonPrimitive("Maximum number of requests to store (default: 1000)")
                            )
                            put("default", JsonPrimitive(1000))
                        })
                        put("captureRequestBody", buildJsonObject {
                            put("type", JsonPrimitive("boolean"))
                            put(
                                "description",
                                JsonPrimitive("Whether to capture request body content")
                            )
                            put("default", JsonPrimitive(false))
                        })
                        put("captureResponseBody", buildJsonObject {
                            put("type", JsonPrimitive("boolean"))
                            put(
                                "description",
                                JsonPrimitive("Whether to capture response body content")
                            )
                            put("default", JsonPrimitive(false))
                        })
                        put("domains", buildJsonObject {
                            put("type", JsonPrimitive("array"))
                            put("items", buildJsonObject { put("type", JsonPrimitive("string")) })
                            put(
                                "description",
                                JsonPrimitive("Filter requests by domains (empty = all domains)")
                            )
                        })
                        put("methods", buildJsonObject {
                            put("type", JsonPrimitive("array"))
                            put("items", buildJsonObject { put("type", JsonPrimitive("string")) })
                            put(
                                "description",
                                JsonPrimitive("Filter requests by HTTP methods (empty = all methods)")
                            )
                        })
                    })
                },
                required = emptyList()
            )
        )
    }

    private fun createStopMonitoringTool(): Tool {
        return Tool(
            name = "network_stop_monitoring",
            description = "Stop network request monitoring",
            inputSchema = Tool.Input(
                properties = buildJsonObject { put("type", JsonPrimitive("object")) },
                required = emptyList()
            )
        )
    }

    private fun createGetRequestsTool(): Tool {
        return Tool(
            name = "network_get_requests",
            description = "Get captured network requests with optional filtering",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    put("type", JsonPrimitive("object"))
                    put("properties", buildJsonObject {
                        put("domain", buildJsonObject {
                            put("type", JsonPrimitive("string"))
                            put("description", JsonPrimitive("Filter by domain name"))
                        })
                        put("method", buildJsonObject {
                            put("type", JsonPrimitive("string"))
                            put(
                                "description",
                                JsonPrimitive("Filter by HTTP method (GET, POST, etc.)")
                            )
                        })
                        put("statusCode", buildJsonObject {
                            put("type", JsonPrimitive("integer"))
                            put("description", JsonPrimitive("Filter by HTTP status code"))
                        })
                        put("minDuration", buildJsonObject {
                            put("type", JsonPrimitive("integer"))
                            put(
                                "description",
                                JsonPrimitive("Minimum request duration in milliseconds")
                            )
                        })
                        put("maxDuration", buildJsonObject {
                            put("type", JsonPrimitive("integer"))
                            put(
                                "description",
                                JsonPrimitive("Maximum request duration in milliseconds")
                            )
                        })
                        put("limit", buildJsonObject {
                            put("type", JsonPrimitive("integer"))
                            put(
                                "description",
                                JsonPrimitive("Maximum number of requests to return (default: 100)")
                            )
                            put("default", JsonPrimitive(100))
                        })
                    })
                },
                required = emptyList()
            )
        )
    }

    private fun createAnalyzeRequestTool(): Tool {
        return Tool(
            name = "network_analyze_request",
            description = "Analyze a specific network request for performance and security insights",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    put("type", JsonPrimitive("object"))
                    put("properties", buildJsonObject {
                        put("requestId", buildJsonObject {
                            put("type", JsonPrimitive("string"))
                            put("description", JsonPrimitive("ID of the request to analyze"))
                        })
                    })
                },
                required = listOf("requestId")
            )
        )
    }

    private suspend fun startNetworkMonitoring(arguments: Map<String, Any>): CallToolResult {
        try {
            val maxRequests = (arguments["maxRequests"] as? Number)?.toInt() ?: 1000
            val captureRequestBody = arguments["captureRequestBody"] as? Boolean ?: false
            val captureResponseBody = arguments["captureResponseBody"] as? Boolean ?: false
            val domains =
                (arguments["domains"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
            val methods =
                (arguments["methods"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()

            val config = NetworkInspector.MonitoringConfig(
                maxRequests = maxRequests,
                captureRequestBody = captureRequestBody,
                captureResponseBody = captureResponseBody,
                domains = domains,
                methods = methods
            )

            val result = networkInspector.startMonitoring(config)

            val responseText = if (result.isSuccess) {
                buildString {
                    appendLine("✅ Network monitoring started successfully")
                    appendLine()
                    appendLine("Configuration:")
                    appendLine("- Max requests: $maxRequests")
                    appendLine("- Capture request body: $captureRequestBody")
                    appendLine("- Capture response body: $captureResponseBody")
                    if (domains.isNotEmpty()) {
                        appendLine("- Filtered domains: ${domains.joinToString(", ")}")
                    }
                    if (methods.isNotEmpty()) {
                        appendLine("- Filtered methods: ${methods.joinToString(", ")}")
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
                isError = result.isFailure
            )

        } catch (e: Exception) {
            Log.e(TAG, "Error starting network monitoring", e)
            return CallToolResult(
                content = listOf(TextContent(text = "❌ Error starting network monitoring: ${e.message}")),
                isError = true
            )
        }
    }

    private suspend fun stopNetworkMonitoring(arguments: Map<String, Any>): CallToolResult {
        try {
            val result = networkInspector.stopMonitoring()

            val responseText = if (result.isSuccess) {
                "✅ Network monitoring stopped successfully"
            } else {
                "❌ Failed to stop network monitoring: ${result.exceptionOrNull()?.message}"
            }

            return CallToolResult(
                content = listOf(TextContent(text = responseText)),
                isError = result.isFailure
            )

        } catch (e: Exception) {
            Log.e(TAG, "Error stopping network monitoring", e)
            return CallToolResult(
                content = listOf(TextContent(text = "❌ Error stopping network monitoring: ${e.message}")),
                isError = true
            )
        }
    }

    private suspend fun getNetworkRequests(arguments: Map<String, Any>): CallToolResult {
        try {
            val domain = arguments["domain"] as? String
            val method = arguments["method"] as? String
            val statusCode = (arguments["statusCode"] as? Number)?.toInt()
            val minDuration = (arguments["minDuration"] as? Number)?.toLong()
            val maxDuration = (arguments["maxDuration"] as? Number)?.toLong()
            val limit = (arguments["limit"] as? Number)?.toInt() ?: 100

            val filter = NetworkInspector.RequestFilter(
                domain = domain,
                method = method,
                statusCode = statusCode,
                minDuration = minDuration,
                maxDuration = maxDuration,
                limit = limit
            )

            val requests = networkInspector.getNetworkRequests(filter)

            val responseText = buildString {
                appendLine("🌐 Network Requests")
                appendLine("================")
                appendLine()

                if (requests.isEmpty()) {
                    appendLine("No requests found matching the filter criteria.")
                    appendLine()
                    appendLine("Make sure network monitoring is active and requests are being made.")
                } else {
                    appendLine("Found ${requests.size} requests:")
                    appendLine()

                    requests.forEach { request ->
                        appendLine("📋 Request ${request.id}")
                        appendLine("   URL: ${request.url}")
                        appendLine("   Method: ${request.method}")
                        appendLine("   Status: ${request.responseCode ?: "N/A"}")
                        appendLine("   Duration: ${request.duration ?: "N/A"}ms")
                        appendLine("   Size: ${formatBytes(request.size)}")
                        if (request.error != null) {
                            appendLine("   ❌ Error: ${request.error}")
                        }
                        appendLine(
                            "   Time: ${
                                java.text.SimpleDateFormat("HH:mm:ss.SSS")
                                    .format(java.util.Date(request.startTime))
                            }"
                        )
                        appendLine()
                    }
                }

                appendLine("Filter applied:")
                domain?.let { appendLine("- Domain: $it") }
                method?.let { appendLine("- Method: $it") }
                statusCode?.let { appendLine("- Status Code: $it") }
                minDuration?.let { appendLine("- Min Duration: ${it}ms") }
                maxDuration?.let { appendLine("- Max Duration: ${it}ms") }
                appendLine("- Limit: $limit")
            }

            return CallToolResult(
                content = listOf(TextContent(text = responseText)),
                isError = false
            )

        } catch (e: Exception) {
            Log.e(TAG, "Error getting network requests", e)
            return CallToolResult(
                content = listOf(TextContent(text = "❌ Error getting network requests: ${e.message}")),
                isError = true
            )
        }
    }

    private suspend fun analyzeNetworkRequest(arguments: Map<String, Any>): CallToolResult {
        try {
            val requestId = arguments["requestId"] as? String
                ?: return CallToolResult(
                    content = listOf(TextContent(text = "❌ Missing required parameter: requestId")),
                    isError = true
                )

            val analysis = networkInspector.analyzeRequest(requestId)
                ?: return CallToolResult(
                    content = listOf(TextContent(text = "❌ Request not found: $requestId")),
                    isError = true
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
                appendLine("   Response Size: ${formatBytes(analysis.performance.responseSize)}")
                if (analysis.performance.bandwidth > 0) {
                    appendLine(
                        "   Bandwidth: ${
                            String.format(
                                "%.2f",
                                analysis.performance.bandwidth
                            )
                        } bytes/sec"
                    )
                }
                appendLine()

                appendLine("🔒 Security Analysis:")
                appendLine("   HTTPS: ${if (analysis.security.isHttps) "✅ Yes" else "❌ No"}")
                appendLine("   Has Auth Header: ${if (analysis.security.hasAuthHeader) "✅ Yes" else "❌ No"}")
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
                    val maskedValue = if (key.lowercase().contains("authorization") ||
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
                isError = false
            )

        } catch (e: Exception) {
            Log.e(TAG, "Error analyzing network request", e)
            return CallToolResult(
                content = listOf(TextContent(text = "❌ Error analyzing network request: ${e.message}")),
                isError = true
            )
        }
    }

    private fun formatBytes(bytes: Long): String {
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var size = bytes.toDouble()
        var unitIndex = 0

        while (size >= 1024 && unitIndex < units.size - 1) {
            size /= 1024
            unitIndex++
        }

        return String.format("%.2f %s", size, units[unitIndex])
    }
}
