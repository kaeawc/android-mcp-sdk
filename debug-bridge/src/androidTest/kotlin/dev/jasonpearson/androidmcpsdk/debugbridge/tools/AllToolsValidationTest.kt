import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*

@RunWith(AndroidJUnit4::class)
class AllToolsValidationTest : ToolValidationFramework() {

    private val allBuiltInTools = listOf(
        "device_info",
        "hardware_info",
        "system_info",
        "app_info",
        "system_time",
        "memory_info",
        "battery_info"
    )

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
    fun testAllToolsExist() {
        val availableTools = serverManager.getMcpTools().map { it.name }

        for (tool in allBuiltInTools) {
            assertTrue("Tool $tool should be available", availableTools.contains(tool))
        }
    }

    @Test
    fun testAllToolsExecuteSuccessfully() {
        val results = mutableMapOf<String, ToolExecutionResult>()

        for (tool in allBuiltInTools) {
            val result = executeToolWithMeasurement(tool)
            results[tool] = result

            assertTrue("Tool $tool should execute successfully", result.success)
            assertFalse("Tool $tool should return non-empty result", result.result.isEmpty())
        }

        // Print summary
        println("\n=== All Tools Execution Summary ===")
        for ((tool, result) in results) {
            println("$tool: ${if (result.success) "✓" else "✗"} (${result.executionTimeMs}ms)")
        }
    }

    @Test
    fun testAllToolsPerformance() {
        val performanceResults = mutableMapOf<String, List<Long>>()

        // Execute each tool multiple times
        for (tool in allBuiltInTools) {
            val executionTimes = mutableListOf<Long>()

            repeat(5) {
                val result = executeToolWithMeasurement(tool)
                if (result.success) {
                    executionTimes.add(result.executionTimeMs)
                }
            }

            performanceResults[tool] = executionTimes
        }

        // Validate performance
        for ((tool, times) in performanceResults) {
            assertTrue("Tool $tool should have successful executions", times.isNotEmpty())

            val averageTime = times.average()
            val maxTime = times.maxOrNull() ?: 0

            assertTrue("Tool $tool average time should be reasonable", averageTime < 2000)
            assertTrue("Tool $tool max time should be acceptable", maxTime < 5000)
        }

        // Print performance summary
        println("\n=== All Tools Performance Summary ===")
        for ((tool, times) in performanceResults) {
            if (times.isNotEmpty()) {
                println("$tool: avg=${times.average().toInt()}ms, max=${times.maxOrNull()}ms")
            }
        }
    }

    @Test
    fun testToolsWithInvalidArguments() {
        val invalidArgs = mapOf<String, Any>(
            "invalid_param" to "invalid_value",
            "another_param" to 12345
        )

        for (tool in allBuiltInTools) {
            val result = executeToolWithMeasurement(tool, invalidArgs)
            assertTrue("Tool $tool should handle invalid arguments gracefully", result.success)
        }
    }

    @Test
    fun testToolOutputFormats() {
        for (tool in allBuiltInTools) {
            val result = executeToolWithMeasurement(tool)
            assertTrue("Tool $tool should execute successfully", result.success)

            // All tools should return valid, non-empty text
            assertTrue("Tool $tool should return non-empty text", result.result.isNotEmpty())
            assertTrue(
                "Tool $tool should return meaningful content",
                result.result.trim().isNotEmpty()
            )
        }
    }

    @Test
    fun testToolsStressTest() {
        val stressTestResults = mutableMapOf<String, Int>()

        for (tool in allBuiltInTools) {
            var successCount = 0
            val iterations = 10 // Reduced for instrumented tests

            repeat(iterations) {
                val result = executeToolWithMeasurement(tool)
                if (result.success) {
                    successCount++
                }
            }

            stressTestResults[tool] = successCount

            // At least 90% should succeed in stress test
            assertTrue(
                "Tool $tool should handle stress test",
                successCount >= iterations * 0.9
            )
        }

        // Print stress test summary
        println("\n=== Stress Test Summary ===")
        for ((tool, successCount) in stressTestResults) {
            println("$tool: $successCount/10 successful (${successCount * 10}%)")
        }
    }

    @Test
    fun testComprehensiveToolValidation() {
        val overallReport = mutableMapOf<String, ToolValidationReport>()

        for (tool in allBuiltInTools) {
            val testCases = listOf<() -> Pair<String, Boolean>>(
                {
                    val result = executeToolWithMeasurement(tool)
                    "Basic execution" to result.success
                },
                {
                    val result = executeToolWithMeasurement(tool)
                    "Non-empty result" to result.result.isNotEmpty()
                },
                {
                    val result = executeToolWithMeasurement(tool)
                    "Meaningful content" to result.result.trim().isNotEmpty()
                },
                {
                    val result = executeToolWithMeasurement(tool)
                    "Reasonable execution time" to (result.executionTimeMs < 5000)
                },
                {
                    val result = executeToolWithMeasurement(tool, mapOf("invalid" to "args"))
                    "Handles invalid args" to result.success
                }
            )

            val report = runToolValidationSuite(tool, testCases)
            overallReport[tool] = report
            printValidationReport(report)
        }

        // Overall validation
        val totalTests = overallReport.values.sumOf { it.totalTests }
        val totalPassed = overallReport.values.sumOf { it.passedTests }
        val overallSuccessRate = totalPassed.toDouble() / totalTests

        println("\n=== Overall Validation Summary ===")
        println("Total tests across all tools: $totalTests")
        println("Total passed: $totalPassed")
        println("Overall success rate: ${(overallSuccessRate * 100).toInt()}%")

        assertTrue("Overall success rate should be very high", overallSuccessRate >= 0.90)
    }

    @Test
    fun testSpecificToolValidations() {
        // Test device_info specific validations
        val deviceInfoResult = executeToolWithMeasurement("device_info")
        assertTrue("Device info should execute", deviceInfoResult.success)
        assertTrue(
            "Device info should contain manufacturer",
            deviceInfoResult.result.contains("Manufacturer:")
        )
        assertTrue(
            "Device info should contain model",
            deviceInfoResult.result.contains("Model:")
        )

        // Test system_time specific validations
        val systemTimeResult = executeToolWithMeasurement("system_time")
        assertTrue("System time should execute", systemTimeResult.success)
        assertTrue(
            "System time should contain time information",
            systemTimeResult.result.contains("System Time Information:")
        )

        // Test app_info specific validations
        val appInfoResult = executeToolWithMeasurement("app_info")
        assertTrue("App info should execute", appInfoResult.success)
        assertTrue(
            "App info should contain package name",
            appInfoResult.result.contains("Package Name:")
        )

        // Test memory_info specific validations
        val memoryInfoResult = executeToolWithMeasurement("memory_info")
        assertTrue("Memory info should execute", memoryInfoResult.success)
        assertTrue(
            "Memory info should contain memory information",
            memoryInfoResult.result.contains("Memory Information:")
        )

        // Test battery_info specific validations
        val batteryInfoResult = executeToolWithMeasurement("battery_info")
        assertTrue("Battery info should execute", batteryInfoResult.success)
        assertTrue(
            "Battery info should contain battery information",
            batteryInfoResult.result.contains("Battery Information:")
        )
    }
}
