import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import dev.jasonpearson.androidmcpsdk.core.McpServerManager
import kotlin.system.measureTimeMillis
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*

abstract class ToolValidationFramework {

    protected lateinit var serverManager: McpServerManager
    protected lateinit var context: Context

    data class ToolExecutionResult(
        val success: Boolean,
        val result: String,
        val executionTimeMs: Long,
        val error: String? = null,
    )

    data class ToolValidationReport(
        val toolName: String,
        val totalTests: Int,
        val passedTests: Int,
        val failedTests: Int,
        val averageExecutionTime: Double,
        val minExecutionTime: Long,
        val maxExecutionTime: Long,
        val failures: List<String>,
    )

    protected fun setupTestEnvironment() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        serverManager = McpServerManager.getInstance()

        runBlocking {
            if (!serverManager.isInitialized()) {
                serverManager.initialize(context, "Test MCP Server", "1.0.0")
            }
            if (!serverManager.isServerRunning()) {
                serverManager.startServer()
            }
        }
    }

    protected fun executeToolWithMeasurement(
        toolName: String,
        arguments: Map<String, Any> = emptyMap(),
    ): ToolExecutionResult {
        return try {
            var result = ""
            val executionTime = measureTimeMillis {
                result = runBlocking {
                    val toolResult = serverManager.callMcpTool(toolName, arguments)
                    if (toolResult.isError != true) {
                        toolResult.content.joinToString("\n") { content ->
                            when (content) {
                                is io.modelcontextprotocol.kotlin.sdk.TextContent ->
                                    content.text ?: ""

                                else -> content.toString()
                            }
                        }
                    } else {
                        throw Exception(
                            "Tool execution failed: ${toolResult.content.joinToString { it.toString() }}"
                        )
                    }
                }
            }

            ToolExecutionResult(success = true, result = result, executionTimeMs = executionTime)
        } catch (e: Exception) {
            ToolExecutionResult(
                success = false,
                result = "",
                executionTimeMs = -1,
                error = e.message,
            )
        }
    }

    protected fun validateJsonStructure(jsonString: String, requiredFields: List<String>): Boolean {
        return try {
            val json = JSONObject(jsonString)
            requiredFields.all { field -> json.has(field) && !json.isNull(field) }
        } catch (e: Exception) {
            false
        }
    }

    protected fun validateNumericValue(
        value: String,
        min: Double? = null,
        max: Double? = null,
    ): Boolean {
        return try {
            val numValue = value.toDouble()
            (min == null || numValue >= min) && (max == null || numValue <= max)
        } catch (e: Exception) {
            false
        }
    }

    protected fun validateNonEmptyString(value: String): Boolean {
        return value.isNotBlank()
    }

    protected fun runToolValidationSuite(
        toolName: String,
        testCases: List<() -> Pair<String, Boolean>>,
    ): ToolValidationReport {
        val failures = mutableListOf<String>()
        var totalTests = 0
        var passedTests = 0
        val executionTimes = mutableListOf<Long>()

        for (testCase in testCases) {
            totalTests++
            try {
                val (testName, passed) = testCase()
                if (passed) {
                    passedTests++
                } else {
                    failures.add(testName)
                }
            } catch (e: Exception) {
                failures.add("Exception in test: ${e.message}")
            }
        }

        // Measure performance with multiple executions
        repeat(10) {
            val result = executeToolWithMeasurement(toolName)
            if (result.success && result.executionTimeMs > 0) {
                executionTimes.add(result.executionTimeMs)
            }
        }

        return ToolValidationReport(
            toolName = toolName,
            totalTests = totalTests,
            passedTests = passedTests,
            failedTests = failures.size,
            averageExecutionTime =
                if (executionTimes.isNotEmpty()) executionTimes.average() else 0.0,
            minExecutionTime = executionTimes.minOrNull() ?: 0,
            maxExecutionTime = executionTimes.maxOrNull() ?: 0,
            failures = failures,
        )
    }

    protected fun printValidationReport(report: ToolValidationReport) {
        println("\n=== ${report.toolName} Validation Report ===")
        println("Total Tests: ${report.totalTests}")
        println("Passed: ${report.passedTests}")
        println("Failed: ${report.failedTests}")
        println(
            "Success Rate: ${(report.passedTests.toDouble() / report.totalTests * 100).toInt()}%"
        )
        println("Performance:")
        println("  - Average execution time: ${report.averageExecutionTime.toInt()}ms")
        println("  - Min execution time: ${report.minExecutionTime}ms")
        println("  - Max execution time: ${report.maxExecutionTime}ms")

        if (report.failures.isNotEmpty()) {
            println("Failures:")
            report.failures.forEach { failure -> println("  - $failure") }
        }
        println("=" + ("=".repeat(49)))
    }
}
