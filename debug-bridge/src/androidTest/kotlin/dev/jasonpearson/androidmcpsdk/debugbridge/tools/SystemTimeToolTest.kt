import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SystemTimeToolTest : ToolValidationFramework() {

    @Before
    fun setup() {
        setupTestEnvironment()
    }

    @After
    fun teardown() {
        runBlocking { serverManager.stopServer() }
    }

    @Test
    fun testSystemTimeBasicFunctionality() {
        val result = executeToolWithMeasurement("system_time")

        assertTrue("System time tool should execute successfully", result.success)
        assertFalse("System time should return non-empty result", result.result.isEmpty())
        assertTrue("Execution time should be fast", result.executionTimeMs < 200)
    }

    @Test
    fun testSystemTimeIsoFormat() {
        val result = executeToolWithMeasurement("system_time", mapOf("format" to "iso"))
        assertTrue("Tool should execute successfully", result.success)

        val resultText = result.result
        assertTrue("Result should contain ISO format", resultText.contains("ISO Format:"))

        // Extract and validate ISO timestamp
        val lines = resultText.split("\n")
        val isoLine = lines.find { it.contains("ISO Format:") }
        assertNotNull("Should have ISO format line", isoLine)

        val isoTimestamp = isoLine?.substring(isoLine.indexOf(":") + 1)?.trim()
        assertTrue("ISO timestamp should not be empty", !isoTimestamp.isNullOrBlank())

        // Should contain T and Z for proper ISO format
        assertTrue("ISO timestamp should contain T", isoTimestamp!!.contains("T"))
        assertTrue("ISO timestamp should end with Z", isoTimestamp.endsWith("Z"))
    }

    @Test
    fun testSystemTimeTimestampFormat() {
        val beforeTime = System.currentTimeMillis()
        val result = executeToolWithMeasurement("system_time", mapOf("format" to "timestamp"))
        val afterTime = System.currentTimeMillis()

        assertTrue("Tool should execute successfully", result.success)

        val resultText = result.result
        assertTrue("Result should contain timestamp", resultText.contains("Timestamp:"))

        // Extract timestamp value
        val lines = resultText.split("\n")
        val timestampLine = lines.find { it.contains("Timestamp:") }
        assertNotNull("Should have timestamp line", timestampLine)

        val timestampStr = timestampLine?.substring(timestampLine.indexOf(":") + 1)?.trim()
        assertNotNull("Timestamp should not be null", timestampStr)

        val timestamp = timestampStr!!.toLongOrNull()
        assertNotNull("Timestamp should be a valid long", timestamp)

        // Timestamp should be within execution window
        assertTrue(
            "Timestamp should be recent",
            timestamp!! >= beforeTime && timestamp <= afterTime,
        )
    }

    @Test
    fun testSystemTimeReadableFormat() {
        val result = executeToolWithMeasurement("system_time", mapOf("format" to "readable"))
        assertTrue("Tool should execute successfully", result.success)

        val resultText = result.result
        assertTrue("Result should contain readable format", resultText.contains("Readable Format:"))

        // Extract readable time
        val lines = resultText.split("\n")
        val readableLine = lines.find { it.contains("Readable Format:") }
        assertNotNull("Should have readable format line", readableLine)

        val readableTime = readableLine?.substring(readableLine.indexOf(":") + 1)?.trim()
        assertTrue("Readable time should not be empty", !readableTime.isNullOrBlank())

        // Should contain year, time, and timezone
        assertTrue("Readable time should contain year", readableTime!!.contains("20"))
        assertTrue("Readable time should contain colon", readableTime.contains(":"))
    }

    @Test
    fun testSystemTimeDefaultFormat() {
        val result = executeToolWithMeasurement("system_time")
        assertTrue("Tool should execute successfully", result.success)

        val resultText = result.result

        // Default should include all formats
        assertTrue("Result should contain ISO format", resultText.contains("ISO Format:"))
        assertTrue("Result should contain timestamp", resultText.contains("Timestamp:"))
        assertTrue("Result should contain readable format", resultText.contains("Readable Format:"))
        assertTrue("Result should contain system timezone", resultText.contains("System Timezone:"))
        assertTrue("Result should contain uptime", resultText.contains("Uptime:"))
    }

    @Test
    fun testSystemTimeWithTimezone() {
        val result =
            executeToolWithMeasurement("system_time", mapOf("format" to "iso", "timezone" to "UTC"))
        assertTrue("Tool should execute successfully", result.success)

        val resultText = result.result
        assertTrue(
            "Result should contain requested timezone",
            resultText.contains("Requested Timezone: UTC"),
        )
        assertTrue("Result should contain time in UTC", resultText.contains("Time in UTC:"))
    }

    @Test
    fun testSystemTimeConsistency() {
        // System time should be relatively stable over short periods
        val result1 = executeToolWithMeasurement("system_time", mapOf("format" to "timestamp"))
        Thread.sleep(50)
        val result2 = executeToolWithMeasurement("system_time", mapOf("format" to "timestamp"))

        assertTrue("Both executions should succeed", result1.success && result2.success)

        // Extract timestamps
        val timestamp1 = extractTimestamp(result1.result)
        val timestamp2 = extractTimestamp(result2.result)

        assertTrue("Second timestamp should be later or equal", timestamp2 >= timestamp1)
        assertTrue(
            "Timestamps should be close",
            (timestamp2 - timestamp1) < 5000,
        ) // Less than 5 seconds
    }

    @Test
    fun testComprehensiveSystemTimeValidation() {
        val testCases =
            listOf<() -> Pair<String, Boolean>>(
                {
                    val result = executeToolWithMeasurement("system_time")
                    "Basic execution" to result.success
                },
                {
                    val result = executeToolWithMeasurement("system_time")
                    "Contains system time information" to
                        result.result.contains("System Time Information:")
                },
                {
                    val result = executeToolWithMeasurement("system_time", mapOf("format" to "iso"))
                    "ISO format works" to (result.success && result.result.contains("ISO Format:"))
                },
                {
                    val result =
                        executeToolWithMeasurement("system_time", mapOf("format" to "timestamp"))
                    "Timestamp format works" to
                        (result.success && result.result.contains("Timestamp:"))
                },
                {
                    val result =
                        executeToolWithMeasurement("system_time", mapOf("format" to "readable"))
                    "Readable format works" to
                        (result.success && result.result.contains("Readable Format:"))
                },
                {
                    val result = executeToolWithMeasurement("system_time")
                    "Contains system timezone" to result.result.contains("System Timezone:")
                },
                {
                    val result = executeToolWithMeasurement("system_time")
                    "Contains uptime" to result.result.contains("Uptime:")
                },
                {
                    val result =
                        executeToolWithMeasurement("system_time", mapOf("timezone" to "UTC"))
                    "Timezone parameter works" to
                        (result.success && result.result.contains("Requested Timezone: UTC"))
                },
                {
                    val result =
                        executeToolWithMeasurement("system_time", mapOf("format" to "invalid"))
                    "Handles invalid format gracefully" to result.success
                },
                {
                    val result = executeToolWithMeasurement("system_time")
                    "Execution very fast" to (result.executionTimeMs < 200)
                },
            )

        val report = runToolValidationSuite("system_time", testCases)
        printValidationReport(report)

        assertTrue("All system time tests should pass", report.passedTests == report.totalTests)
    }

    private fun extractTimestamp(resultText: String): Long {
        val lines = resultText.split("\n")
        val timestampLine = lines.find { it.contains("Timestamp:") }
        val timestampStr = timestampLine?.substring(timestampLine.indexOf(":") + 1)?.trim()
        return timestampStr?.toLongOrNull() ?: 0L
    }
}
