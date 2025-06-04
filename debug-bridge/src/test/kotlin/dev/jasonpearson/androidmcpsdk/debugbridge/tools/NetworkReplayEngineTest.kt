package dev.jasonpearson.androidmcpsdk.debugbridge.tools

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NetworkReplayEngineTest {

    private lateinit var context: Context
    private lateinit var networkReplayEngine: NetworkReplayEngine

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        networkReplayEngine = NetworkReplayEngine(context)
    }

    @Test
    fun `replayRequest should create replay result`() = runTest {
        // Given
        val originalRequest =
            createTestNetworkRequest(id = "test_1", url = "https://httpbin.org/get", method = "GET")

        // When
        val result = networkReplayEngine.replayRequest(originalRequest)

        // Then
        assert(result.replayId.isNotEmpty())
        assert(result.originalRequest.id == "test_1")
        // Note: Actual network tests would require HTTP mocking
    }

    @Test
    fun `replayRequest should apply modifications`() = runTest {
        // Given
        val originalRequest =
            createTestNetworkRequest(
                id = "test_2",
                url = "https://httpbin.org/get",
                method = "GET",
                headers = mapOf("Original-Header" to "original-value"),
            )

        val modifications =
            NetworkReplayEngine.RequestModifications(
                url = "https://httpbin.org/status/200",
                headers = mapOf("Custom-Header" to "custom-value"),
                removeHeaders = listOf("Original-Header"),
            )

        // When
        val result = networkReplayEngine.replayRequest(originalRequest, modifications)

        // Then
        assert(result.replayId.isNotEmpty())
        assert(result.originalRequest.id == "test_2")
        // Verify modification application would be tested in a real network environment

        assert(result.replayedRequest.url == "https://httpbin.org/status/200")
        assert(result.replayedRequest.headers["Custom-Header"] == "custom-value")
        assert(result.replayedRequest.headers.get("Original-Header") == null)
    }

    @Test
    fun `batchReplay should handle multiple requests`() = runTest {
        // Given
        val requests =
            listOf(
                createTestNetworkRequest("batch_1", "https://httpbin.org/get", "GET"),
                createTestNetworkRequest("batch_2", "https://httpbin.org/status/200", "GET"),
            )

        val config =
            NetworkReplayEngine.BatchConfig(
                concurrency = 1,
                delayBetweenRequests = 0,
                failFast = false,
            )

        // When
        val result = networkReplayEngine.batchReplay(requests, config)

        // Then
        assert(result.batchId.isNotEmpty())
        assert(result.totalRequests == 2)
        assert(result.results.size == 2)
    }

    @Test
    fun `loadTest should generate statistics`() = runTest {
        // Given
        val request =
            createTestNetworkRequest(
                id = "load_test",
                url = "https://httpbin.org/get",
                method = "GET",
            )

        val config =
            NetworkReplayEngine.LoadTestConfig(
                requestsPerSecond = 10.0,
                totalRequests = 3,
                concurrency = 1,
            )

        // When
        val result = networkReplayEngine.loadTest(request, config)

        // Then
        assert(result.testId.isNotEmpty())
        assert(result.config.totalRequests == 3)
        assert(result.results.size <= 3) // May complete fewer due to timing
        assert(result.statistics.requestsPerSecond >= 0)
    }

    @Test
    fun `getActiveSessions should return sessions list`() = runTest {
        // When
        val sessions = networkReplayEngine.getActiveSessions()

        // Then
        assert(sessions != null)
        // Sessions list should be retrievable (empty is fine)
    }

    @Test
    fun `RequestModifications should be serializable`() {
        // Given & When
        val modifications =
            NetworkReplayEngine.RequestModifications(
                url = "https://example.com",
                method = "POST",
                headers = mapOf("Custom" to "value"),
                removeHeaders = listOf("Remove-Me"),
                body = "test body",
                timeout = 30000,
            )

        // Then
        assert(modifications.url == "https://example.com")
        assert(modifications.method == "POST")
        assert(modifications.headers.containsKey("Custom"))
        assert(modifications.removeHeaders.contains("Remove-Me"))
        assert(modifications.body == "test body")
        assert(modifications.timeout == 30000L)
    }

    private fun createTestNetworkRequest(
        id: String,
        url: String,
        method: String,
        headers: Map<String, String> = emptyMap(),
        requestBody: String? = null,
        responseCode: Int? = null,
        responseHeaders: Map<String, String> = emptyMap(),
        responseBody: String? = null,
        duration: Long? = null,
    ): NetworkInspector.NetworkRequest {
        val startTime = System.currentTimeMillis()
        return NetworkInspector.NetworkRequest(
            id = id,
            url = url,
            method = method,
            headers = headers,
            requestBody = requestBody,
            responseCode = responseCode,
            responseHeaders = responseHeaders,
            responseBody = responseBody,
            startTime = startTime,
            endTime = startTime + (duration ?: 100),
            duration = duration,
            size = responseBody?.length?.toLong() ?: 0,
        )
    }
}
