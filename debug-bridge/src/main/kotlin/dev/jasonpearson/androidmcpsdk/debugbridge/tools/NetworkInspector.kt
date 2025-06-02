package dev.jasonpearson.androidmcpsdk.debugbridge.tools

import android.content.Context
import android.util.Log
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Network request inspection and monitoring system. Provides capabilities for intercepting,
 * analyzing, and storing network traffic.
 */
class NetworkInspector(private val context: Context) {

    companion object {
        private const val TAG = "NetworkInspector"
        private const val MAX_STORED_REQUESTS = 1000
    }

    private val requestIdGenerator = AtomicLong(0)
    private val storedRequests = ConcurrentHashMap<String, NetworkRequest>()
    private val mutex = Mutex()
    private var isMonitoring = false

    @Serializable
    data class MonitoringConfig(
        val maxRequests: Int = MAX_STORED_REQUESTS,
        val captureRequestBody: Boolean = false,
        val captureResponseBody: Boolean = false,
        val domains: List<String> = emptyList(),
        val methods: List<String> = emptyList(),
    )

    @Serializable
    data class RequestFilter(
        val domain: String? = null,
        val method: String? = null,
        val statusCode: Int? = null,
        val minDuration: Long? = null,
        val maxDuration: Long? = null,
        val limit: Int = 100,
    )

    @Serializable
    data class NetworkRequest(
        val id: String,
        val url: String,
        val method: String,
        val headers: Map<String, String>,
        val requestBody: String? = null,
        val responseCode: Int? = null,
        val responseHeaders: Map<String, String> = emptyMap(),
        val responseBody: String? = null,
        val startTime: Long,
        val endTime: Long? = null,
        val duration: Long? = null,
        val size: Long = 0,
        val error: String? = null,
    )

    @Serializable
    data class RequestAnalysis(
        val request: NetworkRequest,
        val performance: PerformanceMetrics,
        val security: SecurityAnalysis,
    )

    @Serializable
    data class PerformanceMetrics(
        val dnsLookupTime: Long = 0,
        val connectionTime: Long = 0,
        val tlsHandshakeTime: Long = 0,
        val requestSentTime: Long = 0,
        val responseTime: Long = 0,
        val totalTime: Long,
        val requestSize: Long,
        val responseSize: Long,
        val bandwidth: Double = 0.0,
    )

    @Serializable
    data class SecurityAnalysis(
        val isHttps: Boolean,
        val tlsVersion: String? = null,
        val certificateInfo: String? = null,
        val hasAuthHeader: Boolean = false,
        val sensitiveHeaders: List<String> = emptyList(),
    )

    suspend fun startMonitoring(config: MonitoringConfig): Result<Unit> =
        mutex.withLock {
            return try {
                if (isMonitoring) {
                    Log.w(TAG, "Network monitoring already active")
                    return Result.success(Unit)
                }

                isMonitoring = true
                storedRequests.clear()
                Log.i(TAG, "Network monitoring started with config: $config")
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start network monitoring", e)
                Result.failure(e)
            }
        }

    suspend fun stopMonitoring(): Result<Unit> =
        mutex.withLock {
            return try {
                isMonitoring = false
                Log.i(TAG, "Network monitoring stopped")
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop network monitoring", e)
                Result.failure(e)
            }
        }

    suspend fun getNetworkRequests(filter: RequestFilter): List<NetworkRequest> {
        val requests = storedRequests.values.toList()

        return requests
            .filter { request ->
                (filter.domain == null || request.url.contains(filter.domain, ignoreCase = true)) &&
                    (filter.method == null ||
                        request.method.equals(filter.method, ignoreCase = true)) &&
                    (filter.statusCode == null || request.responseCode == filter.statusCode) &&
                    (filter.minDuration == null || (request.duration ?: 0) >= filter.minDuration) &&
                    (filter.maxDuration == null || (request.duration ?: 0) <= filter.maxDuration)
            }
            .sortedByDescending { it.startTime }
            .take(filter.limit)
    }

    suspend fun analyzeRequest(requestId: String): RequestAnalysis? {
        val request = storedRequests[requestId] ?: return null

        val performance =
            PerformanceMetrics(
                totalTime = request.duration ?: 0,
                requestSize = 0, // TODO: Calculate actual sizes
                responseSize = request.size,
                bandwidth =
                    if (request.duration != null && request.duration > 0) {
                        request.size.toDouble() / request.duration * 1000
                    } else 0.0,
            )

        val security =
            SecurityAnalysis(
                isHttps = request.url.startsWith("https://"),
                hasAuthHeader =
                    request.headers.keys.any {
                        it.lowercase().contains("authorization") || it.lowercase().contains("auth")
                    },
                sensitiveHeaders =
                    request.headers.keys.filter { header ->
                        header.lowercase().let { h ->
                            h.contains("authorization") ||
                                h.contains("cookie") ||
                                h.contains("token") ||
                                h.contains("api-key")
                        }
                    },
            )

        return RequestAnalysis(request = request, performance = performance, security = security)
    }

    fun createInterceptor(): Interceptor {
        return McpNetworkInterceptor()
    }

    private fun storeRequest(request: NetworkRequest) {
        if (!isMonitoring) return

        // Remove oldest requests if we're at capacity
        if (storedRequests.size >= MAX_STORED_REQUESTS) {
            val oldestKey = storedRequests.values.minByOrNull { it.startTime }?.id
            oldestKey?.let { storedRequests.remove(it) }
        }

        storedRequests[request.id] = request
        Log.d(TAG, "Stored network request: ${request.method} ${request.url}")
    }

    private inner class McpNetworkInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            val requestId = "req_${requestIdGenerator.incrementAndGet()}"
            val startTime = System.currentTimeMillis()

            val networkRequest =
                NetworkRequest(
                    id = requestId,
                    url = request.url.toString(),
                    method = request.method,
                    headers = request.headers.toMap(),
                    requestBody = null, // TODO: Capture request body if needed
                    startTime = startTime,
                )

            var response: Response? = null
            var error: String? = null

            try {
                response = chain.proceed(request)

                val endTime = System.currentTimeMillis()
                val updatedRequest =
                    networkRequest.copy(
                        responseCode = response.code,
                        responseHeaders = response.headers.toMap(),
                        endTime = endTime,
                        duration = endTime - startTime,
                        size = response.body?.contentLength() ?: 0,
                    )

                storeRequest(updatedRequest)
                return response
            } catch (e: IOException) {
                error = e.message
                val endTime = System.currentTimeMillis()
                val updatedRequest =
                    networkRequest.copy(
                        endTime = endTime,
                        duration = endTime - startTime,
                        error = error,
                    )

                storeRequest(updatedRequest)
                throw e
            }
        }

        private fun okhttp3.Headers.toMap(): Map<String, String> {
            val map = mutableMapOf<String, String>()
            for (i in 0 until size) {
                map[name(i)] = value(i)
            }
            return map
        }
    }
}
