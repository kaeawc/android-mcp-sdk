package dev.jasonpearson.androidmcpsdk.debugbridge.tools

import android.content.Context
import android.util.Log
import java.io.IOException
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.Serializable
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType

/**
 * Network request replay engine that can reproduce, modify, and replay captured network requests
 * for testing and debugging purposes.
 */
class NetworkReplayEngine(private val context: Context) {

    companion object {
        private const val TAG = "NetworkReplayEngine"
        private const val DEFAULT_TIMEOUT_SECONDS = 30L
        private const val MAX_CONCURRENT_REQUESTS = 50
    }

    private val replayIdGenerator = AtomicLong(0)
    private val activeReplays = ConcurrentHashMap<String, ReplaySession>()
    private val mutex = Mutex()
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(DEFAULT_TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(DEFAULT_TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(DEFAULT_TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    @Serializable
    data class RequestModifications(
        val url: String? = null,
        val method: String? = null,
        val headers: Map<String, String> = emptyMap(),
        val removeHeaders: List<String> = emptyList(),
        val body: String? = null,
        val timeout: Long? = null
    )

    @Serializable
    data class BatchConfig(
        val concurrency: Int = 1,
        val delayBetweenRequests: Long = 0,
        val failFast: Boolean = false,
        val timeoutPerRequest: Long = DEFAULT_TIMEOUT_SECONDS * 1000
    )

    @Serializable
    data class LoadTestConfig(
        val requestsPerSecond: Double = 1.0,
        val totalRequests: Int = 10,
        val duration: Long? = null,
        val concurrency: Int = 1,
        val rampUpTime: Long = 0
    )

    @Serializable
    data class ReplayResult(
        val replayId: String,
        val originalRequest: NetworkInspector.NetworkRequest,
        val replayedRequest: ReplayedRequest,
        val success: Boolean,
        val error: String? = null,
        val comparison: ResponseComparison? = null
    )

    @Serializable
    data class BatchReplayResult(
        val batchId: String,
        val results: List<ReplayResult>,
        val totalRequests: Int,
        val successfulRequests: Int,
        val failedRequests: Int,
        val totalDuration: Long,
        val averageRequestTime: Double
    )

    @Serializable
    data class LoadTestResult(
        val testId: String,
        val config: LoadTestConfig,
        val results: List<ReplayResult>,
        val statistics: LoadTestStatistics,
        val duration: Long
    )

    @Serializable
    data class ReplayedRequest(
        val id: String,
        val url: String,
        val method: String,
        val headers: Map<String, String>,
        val body: String? = null,
        val responseCode: Int? = null,
        val responseHeaders: Map<String, String> = emptyMap(),
        val responseBody: String? = null,
        val startTime: Long,
        val endTime: Long? = null,
        val duration: Long? = null,
        val size: Long = 0,
        val error: String? = null
    )

    @Serializable
    data class ResponseComparison(
        val statusCodeMatch: Boolean,
        val headersMatch: Boolean,
        val bodyMatch: Boolean,
        val sizeChange: Long,
        val timingChange: Long,
        val differences: List<String>
    )

    @Serializable
    data class LoadTestStatistics(
        val requestsPerSecond: Double,
        val averageResponseTime: Double,
        val minResponseTime: Long,
        val maxResponseTime: Long,
        val p50ResponseTime: Double,
        val p95ResponseTime: Double,
        val p99ResponseTime: Double,
        val errorRate: Double,
        val throughput: Double
    )

    @Serializable
    data class ReplaySession(
        val id: String,
        val startTime: Long,
        val type: SessionType,
        val status: SessionStatus
    )

    enum class SessionType {
        SINGLE_REPLAY,
        BATCH_REPLAY,
        LOAD_TEST
    }

    enum class SessionStatus {
        RUNNING,
        COMPLETED,
        FAILED,
        CANCELLED
    }

    suspend fun replayRequest(
        request: NetworkInspector.NetworkRequest,
        modifications: RequestModifications? = null
    ): ReplayResult = withContext(Dispatchers.IO) {
        val replayId = "replay_${replayIdGenerator.incrementAndGet()}"
        val session = ReplaySession(
            id = replayId,
            startTime = System.currentTimeMillis(),
            type = SessionType.SINGLE_REPLAY,
            status = SessionStatus.RUNNING
        )

        mutex.withLock {
            activeReplays[replayId] = session
        }

        try {
            Log.d(TAG, "Starting replay of request ${request.id} as $replayId")

            val modifiedRequest = applyModifications(request, modifications)
            val replayedRequest = executeRequest(modifiedRequest, replayId)
            val comparison = compareResponses(request, replayedRequest)

            val result = ReplayResult(
                replayId = replayId,
                originalRequest = request,
                replayedRequest = replayedRequest,
                success = replayedRequest.error == null,
                comparison = comparison
            )

            mutex.withLock {
                activeReplays[replayId] = session.copy(status = SessionStatus.COMPLETED)
            }

            Log.d(TAG, "Completed replay $replayId successfully")
            result
        } catch (e: Exception) {
            Log.e(TAG, "Failed to replay request $replayId", e)

            val errorResult = ReplayResult(
                replayId = replayId,
                originalRequest = request,
                replayedRequest = ReplayedRequest(
                    id = replayId,
                    url = request.url,
                    method = request.method,
                    headers = request.headers,
                    startTime = System.currentTimeMillis(),
                    error = e.message
                ),
                success = false,
                error = e.message
            )

            mutex.withLock {
                activeReplays[replayId] = session.copy(status = SessionStatus.FAILED)
            }

            errorResult
        }
    }

    suspend fun batchReplay(
        requests: List<NetworkInspector.NetworkRequest>,
        config: BatchConfig,
        modifications: Map<String, RequestModifications> = emptyMap()
    ): BatchReplayResult = withContext(Dispatchers.IO) {
        val batchId = "batch_${replayIdGenerator.incrementAndGet()}"
        val session = ReplaySession(
            id = batchId,
            startTime = System.currentTimeMillis(),
            type = SessionType.BATCH_REPLAY,
            status = SessionStatus.RUNNING
        )

        mutex.withLock {
            activeReplays[batchId] = session
        }

        val startTime = System.currentTimeMillis()
        val results = mutableListOf<ReplayResult>()

        try {
            Log.d(TAG, "Starting batch replay of ${requests.size} requests")

            // Create semaphore to limit concurrency
            val semaphore = kotlinx.coroutines.sync.Semaphore(
                minOf(
                    config.concurrency,
                    MAX_CONCURRENT_REQUESTS
                )
            )

            val jobs = requests.chunked(config.concurrency).map { batch ->
                async {
                    batch.map { request ->
                        async {
                            semaphore.withPermit {
                                try {
                                    val result = replayRequest(request, modifications[request.id])

                                    if (config.delayBetweenRequests > 0) {
                                        delay(config.delayBetweenRequests)
                                    }

                                    result
                                } catch (e: Exception) {
                                    if (config.failFast) {
                                        throw e
                                    }
                                    ReplayResult(
                                        replayId = "failed_${request.id}",
                                        originalRequest = request,
                                        replayedRequest = ReplayedRequest(
                                            id = "failed_${request.id}",
                                            url = request.url,
                                            method = request.method,
                                            headers = request.headers,
                                            startTime = System.currentTimeMillis(),
                                            error = e.message
                                        ),
                                        success = false,
                                        error = e.message
                                    )
                                }
                            }
                        }
                    }.awaitAll()
                }
            }.awaitAll().flatten()

            results.addAll(jobs)

            val endTime = System.currentTimeMillis()
            val totalDuration = endTime - startTime
            val successfulRequests = results.count { it.success }
            val averageRequestTime = if (results.isNotEmpty()) {
                results.mapNotNull { it.replayedRequest.duration }.average()
            } else 0.0

            val batchResult = BatchReplayResult(
                batchId = batchId,
                results = results,
                totalRequests = requests.size,
                successfulRequests = successfulRequests,
                failedRequests = requests.size - successfulRequests,
                totalDuration = totalDuration,
                averageRequestTime = averageRequestTime
            )

            mutex.withLock {
                activeReplays[batchId] = session.copy(status = SessionStatus.COMPLETED)
            }

            Log.d(
                TAG,
                "Completed batch replay $batchId: $successfulRequests/${requests.size} successful"
            )
            batchResult
        } catch (e: Exception) {
            Log.e(TAG, "Failed batch replay $batchId", e)

            mutex.withLock {
                activeReplays[batchId] = session.copy(status = SessionStatus.FAILED)
            }

            throw e
        }
    }

    suspend fun loadTest(
        request: NetworkInspector.NetworkRequest,
        loadConfig: LoadTestConfig,
        modifications: RequestModifications? = null
    ): LoadTestResult = withContext(Dispatchers.IO) {
        val testId = "load_${replayIdGenerator.incrementAndGet()}"
        val session = ReplaySession(
            id = testId,
            startTime = System.currentTimeMillis(),
            type = SessionType.LOAD_TEST,
            status = SessionStatus.RUNNING
        )

        mutex.withLock {
            activeReplays[testId] = session
        }

        val startTime = System.currentTimeMillis()
        val results = mutableListOf<ReplayResult>()

        try {
            Log.d(TAG, "Starting load test $testId with ${loadConfig.totalRequests} requests")

            val delayBetweenRequests = (1000.0 / loadConfig.requestsPerSecond).toLong()
            val semaphore = kotlinx.coroutines.sync.Semaphore(loadConfig.concurrency)

            // Ramp up if configured
            if (loadConfig.rampUpTime > 0) {
                delay(loadConfig.rampUpTime)
            }

            val jobs = (1..loadConfig.totalRequests).map { requestNumber ->
                async {
                    semaphore.withPermit {
                        val replayResult = replayRequest(request, modifications)

                        // Control request rate
                        if (delayBetweenRequests > 0) {
                            delay(delayBetweenRequests)
                        }

                        replayResult
                    }
                }
            }

            // Wait for completion or duration limit
            val timeoutMillis =
                loadConfig.duration ?: (loadConfig.totalRequests * delayBetweenRequests + 60000)
            val completedJobs = withTimeoutOrNull(timeoutMillis) {
                jobs.awaitAll()
            }

            if (completedJobs != null) {
                results.addAll(completedJobs)
            } else {
                // Timeout occurred, collect completed results
                jobs.forEach { job ->
                    if (job.isCompleted) {
                        results.add(job.getCompleted())
                    }
                }
            }

            val endTime = System.currentTimeMillis()
            val testDuration = endTime - startTime
            val statistics = calculateLoadTestStatistics(results, testDuration, loadConfig)

            val loadTestResult = LoadTestResult(
                testId = testId,
                config = loadConfig,
                results = results,
                statistics = statistics,
                duration = testDuration
            )

            mutex.withLock {
                activeReplays[testId] = session.copy(status = SessionStatus.COMPLETED)
            }

            Log.d(TAG, "Completed load test $testId: ${results.size} requests in ${testDuration}ms")
            loadTestResult
        } catch (e: Exception) {
            Log.e(TAG, "Failed load test $testId", e)

            mutex.withLock {
                activeReplays[testId] = session.copy(status = SessionStatus.FAILED)
            }

            throw e
        }
    }

    suspend fun getActiveSessions(): List<ReplaySession> = mutex.withLock {
        activeReplays.values.toList()
    }

    suspend fun cancelSession(sessionId: String): Boolean = mutex.withLock {
        activeReplays[sessionId]?.let { session ->
            activeReplays[sessionId] = session.copy(status = SessionStatus.CANCELLED)
            Log.d(TAG, "Cancelled replay session $sessionId")
            true
        } ?: false
    }

    private fun applyModifications(
        original: NetworkInspector.NetworkRequest,
        modifications: RequestModifications?
    ): NetworkInspector.NetworkRequest {
        if (modifications == null) return original

        val modifiedHeaders = original.headers.toMutableMap()

        // Remove specified headers
        modifications.removeHeaders.forEach { header ->
            modifiedHeaders.remove(header)
        }

        // Add/update headers
        modifiedHeaders.putAll(modifications.headers)

        return original.copy(
            url = modifications.url ?: original.url,
            method = modifications.method ?: original.method,
            headers = modifiedHeaders.toMap(),
            requestBody = modifications.body ?: original.requestBody
        )
    }

    private suspend fun executeRequest(
        request: NetworkInspector.NetworkRequest,
        replayId: String
    ): ReplayedRequest {
        val startTime = System.currentTimeMillis()

        try {
            val requestBuilder = Request.Builder()
                .url(request.url)
                .method(
                    request.method,
                    request.requestBody?.let {
                        RequestBody.create("application/json".toMediaType(), it)
                    }
                )

            // Add headers
            request.headers.forEach { (key, value) ->
                requestBuilder.addHeader(key, value)
            }

            val okHttpRequest = requestBuilder.build()
            val response = okHttpClient.newCall(okHttpRequest).execute()

            val endTime = System.currentTimeMillis()
            val responseBody = response.body?.string()

            return ReplayedRequest(
                id = replayId,
                url = request.url,
                method = request.method,
                headers = request.headers,
                body = request.requestBody,
                responseCode = response.code,
                responseHeaders = response.headers.toMap(),
                responseBody = responseBody,
                startTime = startTime,
                endTime = endTime,
                duration = endTime - startTime,
                size = responseBody?.length?.toLong() ?: 0
            )
        } catch (e: Exception) {
            val endTime = System.currentTimeMillis()
            return ReplayedRequest(
                id = replayId,
                url = request.url,
                method = request.method,
                headers = request.headers,
                body = request.requestBody,
                startTime = startTime,
                endTime = endTime,
                duration = endTime - startTime,
                error = e.message
            )
        }
    }

    private fun compareResponses(
        original: NetworkInspector.NetworkRequest,
        replayed: ReplayedRequest
    ): ResponseComparison {
        val differences = mutableListOf<String>()

        val statusCodeMatch = original.responseCode == replayed.responseCode
        if (!statusCodeMatch) {
            differences.add("Status code: ${original.responseCode} → ${replayed.responseCode}")
        }

        val headersMatch = original.responseHeaders == replayed.responseHeaders
        if (!headersMatch) {
            differences.add("Response headers differ")
        }

        val bodyMatch = original.responseBody == replayed.responseBody
        if (!bodyMatch) {
            differences.add("Response body differs")
        }

        val sizeChange = (replayed.size) - original.size
        val timingChange = (replayed.duration ?: 0) - (original.duration ?: 0)

        if (sizeChange != 0L) {
            differences.add("Size change: ${if (sizeChange > 0) "+" else ""}$sizeChange bytes")
        }

        if (timingChange != 0L) {
            differences.add("Timing change: ${if (timingChange > 0) "+" else ""}${timingChange}ms")
        }

        return ResponseComparison(
            statusCodeMatch = statusCodeMatch,
            headersMatch = headersMatch,
            bodyMatch = bodyMatch,
            sizeChange = sizeChange,
            timingChange = timingChange,
            differences = differences
        )
    }

    private fun calculateLoadTestStatistics(
        results: List<ReplayResult>,
        duration: Long,
        config: LoadTestConfig
    ): LoadTestStatistics {
        val responseTimes = results.mapNotNull { it.replayedRequest.duration }
        val successfulRequests = results.count { it.success }
        val errorRate = if (results.isNotEmpty()) {
            (results.size - successfulRequests).toDouble() / results.size
        } else 0.0

        val sortedResponseTimes = responseTimes.sorted()
        val p50 = if (sortedResponseTimes.isNotEmpty()) {
            sortedResponseTimes[sortedResponseTimes.size / 2].toDouble()
        } else 0.0

        val p95 = if (sortedResponseTimes.isNotEmpty()) {
            sortedResponseTimes[(sortedResponseTimes.size * 0.95).toInt()].toDouble()
        } else 0.0

        val p99 = if (sortedResponseTimes.isNotEmpty()) {
            sortedResponseTimes[(sortedResponseTimes.size * 0.99).toInt()].toDouble()
        } else 0.0

        return LoadTestStatistics(
            requestsPerSecond = if (duration > 0) results.size.toDouble() / (duration / 1000.0) else 0.0,
            averageResponseTime = responseTimes.average(),
            minResponseTime = responseTimes.minOrNull() ?: 0,
            maxResponseTime = responseTimes.maxOrNull() ?: 0,
            p50ResponseTime = p50,
            p95ResponseTime = p95,
            p99ResponseTime = p99,
            errorRate = errorRate,
            throughput = if (duration > 0) successfulRequests.toDouble() / (duration / 1000.0) else 0.0
        )
    }

    private fun Headers.toMap(): Map<String, String> {
        val map = mutableMapOf<String, String>()
        for (i in 0 until size) {
            map[name(i)] = value(i)
        }
        return map
    }
}
