import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*

@RunWith(AndroidJUnit4::class)
class DeviceInfoToolTest : ToolValidationFramework() {

    @Before
    fun setup() {
        setupTestEnvironment()
    }

    @After
    fun teardown() {
        runBlocking {
            serverManager.stopServer()
        }
    }

    @Test
    fun testDeviceInfoBasicFunctionality() {
        val result = executeToolWithMeasurement("device_info")

        assertTrue("Device info tool should execute successfully", result.success)
        assertFalse("Device info should return non-empty result", result.result.isEmpty())
        assertTrue("Execution time should be reasonable", result.executionTimeMs < 1000)
    }

    @Test
    fun testDeviceInfoDataAccuracy() {
        val result = executeToolWithMeasurement("device_info")
        assertTrue("Tool should execute successfully", result.success)

        val resultText = result.result

        // Validate against known Android Build constants
        assertTrue(
            "Result should contain manufacturer",
            resultText.contains("Manufacturer: ${Build.MANUFACTURER}")
        )
        assertTrue(
            "Result should contain model",
            resultText.contains("Model: ${Build.MODEL}")
        )
        assertTrue(
            "Result should contain brand",
            resultText.contains("Brand: ${Build.BRAND}")
        )
        assertTrue(
            "Result should contain device",
            resultText.contains("Device: ${Build.DEVICE}")
        )
        assertTrue(
            "Result should contain hardware",
            resultText.contains("Hardware: ${Build.HARDWARE}")
        )
    }

    @Test
    fun testDeviceInfoConsistency() {
        // Execute multiple times to ensure consistency
        val results = (1..3).map { executeToolWithMeasurement("device_info") }

        assertTrue("All executions should succeed", results.all { it.success })

        val firstResult = results.first().result
        assertTrue(
            "All results should be identical",
            results.all { it.result == firstResult })
    }

    @Test
    fun testDeviceInfoWithInvalidArguments() {
        // Test with invalid arguments (should ignore them gracefully)
        val invalidArgs = mapOf(
            "invalid_param" to "invalid_value",
            "another_param" to 12345
        )

        val result = executeToolWithMeasurement("device_info", invalidArgs)
        assertTrue("Tool should handle invalid arguments gracefully", result.success)

        // Result should be the same as without arguments
        val normalResult = executeToolWithMeasurement("device_info")
        assertEquals(
            "Result should be same regardless of invalid arguments",
            normalResult.result, result.result
        )
    }

    @Test
    fun testComprehensiveDeviceInfoValidation() {
        val testCases = listOf<() -> Pair<String, Boolean>>(
            {
                val result = executeToolWithMeasurement("device_info")
                "Basic execution" to result.success
            },
            {
                val result = executeToolWithMeasurement("device_info")
                "Contains manufacturer" to result.result.contains("Manufacturer:")
            },
            {
                val result = executeToolWithMeasurement("device_info")
                "Contains model" to result.result.contains("Model:")
            },
            {
                val result = executeToolWithMeasurement("device_info")
                "Contains brand" to result.result.contains("Brand:")
            },
            {
                val result = executeToolWithMeasurement("device_info")
                "Contains device" to result.result.contains("Device:")
            },
            {
                val result = executeToolWithMeasurement("device_info")
                "Contains hardware" to result.result.contains("Hardware:")
            },
            {
                val result = executeToolWithMeasurement("device_info")
                "Contains supported ABIs" to result.result.contains("Supported ABIs:")
            },
            {
                val result = executeToolWithMeasurement("device_info")
                "Contains board" to result.result.contains("Board:")
            },
            {
                val result = executeToolWithMeasurement("device_info")
                "Contains display" to result.result.contains("Display:")
            },
            {
                val result = executeToolWithMeasurement("device_info")
                "Execution time reasonable" to (result.executionTimeMs < 1000)
            }
        )

        val report = runToolValidationSuite("device_info", testCases)
        printValidationReport(report)

        assertTrue("All device info tests should pass", report.passedTests == report.totalTests)
    }
}