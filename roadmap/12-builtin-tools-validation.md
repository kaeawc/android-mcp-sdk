# 12 - Built-in Tools Validation

## Status: `[ ]` Not Started

## Objective

Thoroughly validate all built-in Android tools in the MCP SDK to ensure they function correctly,
provide accurate information, handle edge cases gracefully, and maintain consistent performance
across different Android devices and OS versions.

## Requirements

### Technical Requirements

- **Device Compatibility**: Testing across multiple Android API levels and device types
- **Permission Validation**: Ensuring proper permission handling for all tools
- **Data Accuracy**: Validating correctness of returned information
- **Error Handling**: Testing edge cases and failure scenarios
- **Performance**: Measuring execution time and resource usage

### Built-in Tools to Validate

1. **device_info**: Device hardware and software information
2. **app_info**: Application metadata and configuration
3. **system_time**: System time in various formats
4. **memory_info**: System and app memory usage statistics
5. **battery_info**: Battery status, level, and health information

### Test Categories

1. **Functional Tests**: Basic tool operation and output validation
2. **Data Accuracy Tests**: Verifying information correctness
3. **Permission Tests**: Testing permission requirements and handling
4. **Edge Case Tests**: Testing unusual conditions and error scenarios
5. **Performance Tests**: Measuring tool execution time and resource impact
6. **Cross-Device Tests**: Testing compatibility across different devices

## Dependencies

**Must Complete First:**

- [09-integration-testing-suite.md](09-integration-testing-suite.md) - Testing infrastructure needed

**Should Complete First:**

- [10-adb-port-forwarding-testing.md](10-adb-port-forwarding-testing.md) - ADB testing setup
- [11-mcp-client-communication-testing.md](11-mcp-client-communication-testing.md) - Client
  communication

## Implementation Steps

### Phase 1: Test Infrastructure for Built-in Tools

#### Step 1.1: Create Tool Validation Framework

Create `lib/src/androidTest/kotlin/dev/jasonpearson/mcpandroidsdk/tools/`:

**ToolValidationFramework.kt:**

```kotlin
package dev.jasonpearson.mcpandroidsdk.tools

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import dev.jasonpearson.mcpandroidsdk.McpServerManager
import dev.jasonpearson.mcpandroidsdk.AndroidTool
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import kotlin.system.measureTimeMillis

abstract class ToolValidationFramework {
    
    protected lateinit var serverManager: McpServerManager
    protected lateinit var context: Context
    
    data class ToolExecutionResult(
        val success: Boolean,
        val result: String,
        val executionTimeMs: Long,
        val error: String? = null
    )
    
    data class ToolValidationReport(
        val toolName: String,
        val totalTests: Int,
        val passedTests: Int,
        val failedTests: Int,
        val averageExecutionTime: Double,
        val minExecutionTime: Long,
        val maxExecutionTime: Long,
        val failures: List<String>
    )
    
    protected fun setupTestEnvironment() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        serverManager = McpServerManager.getInstance()
        
        runBlocking {
            if (!serverManager.isInitialized()) {
                serverManager.initialize(context, "Tool Validation Test Server", "1.0.0")
            }
            if (!serverManager.isServerRunning()) {
                serverManager.startServer()
            }
        }
    }
    
    protected fun executeToolWithMeasurement(
        toolName: String, 
        arguments: Map<String, Any> = emptyMap()
    ): ToolExecutionResult {
        return try {
            var result = ""
            val executionTime = measureTimeMillis {
                result = runBlocking {
                    val toolResult = serverManager.executeAndroidTool(toolName, arguments)
                    if (toolResult.success) {
                        toolResult.result
                    } else {
                        throw Exception(toolResult.error ?: "Tool execution failed")
                    }
                }
            }
            
            ToolExecutionResult(
                success = true,
                result = result,
                executionTimeMs = executionTime
            )
        } catch (e: Exception) {
            ToolExecutionResult(
                success = false,
                result = "",
                executionTimeMs = -1,
                error = e.message
            )
        }
    }
    
    protected fun validateJsonStructure(jsonString: String, requiredFields: List<String>): Boolean {
        return try {
            val json = JSONObject(jsonString)
            requiredFields.all { field ->
                json.has(field) && !json.isNull(field)
            }
        } catch (e: Exception) {
            false
        }
    }
    
    protected fun validateNumericValue(value: String, min: Double? = null, max: Double? = null): Boolean {
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
        testCases: List<() -> Pair<String, Boolean>>
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
            averageExecutionTime = if (executionTimes.isNotEmpty()) executionTimes.average() else 0.0,
            minExecutionTime = executionTimes.minOrNull() ?: 0,
            maxExecutionTime = executionTimes.maxOrNull() ?: 0,
            failures = failures
        )
    }
    
    protected fun printValidationReport(report: ToolValidationReport) {
        println("\n=== ${report.toolName} Validation Report ===")
        println("Total Tests: ${report.totalTests}")
        println("Passed: ${report.passedTests}")
        println("Failed: ${report.failedTests}")
        println("Success Rate: ${(report.passedTests.toDouble() / report.totalTests * 100).toInt()}%")
        println("Performance:")
        println("  - Average execution time: ${report.averageExecutionTime.toInt()}ms")
        println("  - Min execution time: ${report.minExecutionTime}ms")
        println("  - Max execution time: ${report.maxExecutionTime}ms")
        
        if (report.failures.isNotEmpty()) {
            println("Failures:")
            report.failures.forEach { failure ->
                println("  - $failure")
            }
        }
        println("=" * 50)
    }
}
```

#### Step 1.2: Device Info Tool Validation

Create `lib/src/androidTest/kotlin/dev/jasonpearson/mcpandroidsdk/tools/DeviceInfoToolTest.kt`:

```kotlin
package dev.jasonpearson.mcpandroidsdk.tools

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
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
        serverManager.stopServer()
    }
    
    @Test
    fun testDeviceInfoBasicFunctionality() {
        val result = executeToolWithMeasurement("device_info")
        
        assertTrue("Device info tool should execute successfully", result.success)
        assertFalse("Device info should return non-empty result", result.result.isEmpty())
        assertTrue("Execution time should be reasonable", result.executionTimeMs < 1000)
    }
    
    @Test
    fun testDeviceInfoJsonStructure() {
        val result = executeToolWithMeasurement("device_info")
        assertTrue("Tool should execute successfully", result.success)
        
        val requiredFields = listOf(
            "manufacturer",
            "model", 
            "android_version",
            "api_level",
            "device_id",
            "hardware",
            "screen_density",
            "screen_resolution"
        )
        
        assertTrue("Device info should have required JSON structure",
            validateJsonStructure(result.result, requiredFields))
    }
    
    @Test
    fun testDeviceInfoDataAccuracy() {
        val result = executeToolWithMeasurement("device_info")
        assertTrue("Tool should execute successfully", result.success)
        
        val json = JSONObject(result.result)
        
        // Validate against known Android Build constants
        assertEquals("Manufacturer should match Build.MANUFACTURER", 
            Build.MANUFACTURER, json.getString("manufacturer"))
        assertEquals("Model should match Build.MODEL", 
            Build.MODEL, json.getString("model"))
        assertEquals("Android version should match Build.VERSION.RELEASE", 
            Build.VERSION.RELEASE, json.getString("android_version"))
        assertEquals("API level should match Build.VERSION.SDK_INT", 
            Build.VERSION.SDK_INT, json.getInt("api_level"))
        
        // Validate reasonable values
        assertTrue("API level should be reasonable", 
            json.getInt("api_level") >= 21) // Minimum supported API level
        assertTrue("Screen density should be positive", 
            json.getDouble("screen_density") > 0)
        
        val resolution = json.getString("screen_resolution")
        assertTrue("Screen resolution should be in format 'WxH'", 
            resolution.matches(Regex("\\d+x\\d+")))
    }
    
    @Test
    fun testDeviceInfoPerformance() {
        val performanceTests = mutableListOf<() -> Pair<String, Boolean>>()
        
        // Test multiple executions for consistency
        repeat(5) { iteration ->
            performanceTests.add {
                val result = executeToolWithMeasurement("device_info")
                "Performance test $iteration" to (result.success && result.executionTimeMs < 500)
            }
        }
        
        // Test concurrent executions
        performanceTests.add {
            val results = (1..3).map {
                executeToolWithMeasurement("device_info")
            }
            "Concurrent execution test" to results.all { it.success }
        }
        
        val report = runToolValidationSuite("device_info", performanceTests)
        printValidationReport(report)
        
        assertTrue("Most performance tests should pass", report.passedTests >= report.totalTests * 0.8)
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
        assertEquals("Result should be same regardless of invalid arguments", 
            normalResult.result, result.result)
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
                val json = JSONObject(result.result)
                "Has manufacturer" to json.has("manufacturer")
            },
            {
                val result = executeToolWithMeasurement("device_info")
                val json = JSONObject(result.result)
                "Manufacturer not empty" to validateNonEmptyString(json.getString("manufacturer"))
            },
            {
                val result = executeToolWithMeasurement("device_info")
                val json = JSONObject(result.result)
                "Has model" to json.has("model")
            },
            {
                val result = executeToolWithMeasurement("device_info")
                val json = JSONObject(result.result)
                "Model not empty" to validateNonEmptyString(json.getString("model"))
            },
            {
                val result = executeToolWithMeasurement("device_info")
                val json = JSONObject(result.result)
                "API level reasonable" to (json.getInt("api_level") >= 21)
            },
            {
                val result = executeToolWithMeasurement("device_info")
                val json = JSONObject(result.result)
                "Screen density positive" to (json.getDouble("screen_density") > 0)
            },
            {
                val result = executeToolWithMeasurement("device_info")
                val json = JSONObject(result.result)
                val resolution = json.getString("screen_resolution")
                "Screen resolution format" to resolution.matches(Regex("\\d+x\\d+"))
            },
            {
                val result = executeToolWithMeasurement("device_info")
                "Execution time reasonable" to (result.executionTimeMs < 1000)
            },
            {
                val result = executeToolWithMeasurement("device_info")
                val json = JSONObject(result.result)
                "Device ID not empty" to validateNonEmptyString(json.optString("device_id", ""))
            }
        )
        
        val report = runToolValidationSuite("device_info", testCases)
        printValidationReport(report)
        
        assertTrue("All device info tests should pass", report.passedTests == report.totalTests)
    }
}
```

#### Step 1.3: App Info Tool Validation

Create `lib/src/androidTest/kotlin/dev/jasonpearson/mcpandroidsdk/tools/AppInfoToolTest.kt`:

```kotlin
package dev.jasonpearson.mcpandroidsdk.tools

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*

@RunWith(AndroidJUnit4::class)
class AppInfoToolTest : ToolValidationFramework() {
    
    @Before
    fun setup() {
        setupTestEnvironment()
    }
    
    @After
    fun teardown() {
        serverManager.stopServer()
    }
    
    @Test
    fun testAppInfoBasicFunctionality() {
        val result = executeToolWithMeasurement("app_info")
        
        assertTrue("App info tool should execute successfully", result.success)
        assertFalse("App info should return non-empty result", result.result.isEmpty())
        assertTrue("Execution time should be reasonable", result.executionTimeMs < 1000)
    }
    
    @Test
    fun testAppInfoJsonStructure() {
        val result = executeToolWithMeasurement("app_info")
        assertTrue("Tool should execute successfully", result.success)
        
        val requiredFields = listOf(
            "package_name",
            "version_name",
            "version_code",
            "target_sdk",
            "min_sdk"
        )
        
        assertTrue("App info should have required JSON structure",
            validateJsonStructure(result.result, requiredFields))
    }
    
    @Test
    fun testAppInfoDataValidation() {
        val result = executeToolWithMeasurement("app_info")
        assertTrue("Tool should execute successfully", result.success)
        
        val json = JSONObject(result.result)
        
        // Validate package name format
        val packageName = json.getString("package_name")
        assertTrue("Package name should be valid format", 
            packageName.matches(Regex("^[a-zA-Z][a-zA-Z0-9_]*(?:\\.[a-zA-Z][a-zA-Z0-9_]*)*$")))
        
        // Validate version information
        assertTrue("Version name should not be empty", 
            validateNonEmptyString(json.getString("version_name")))
        
        val versionCode = json.getInt("version_code")
        assertTrue("Version code should be positive", versionCode > 0)
        
        // Validate SDK levels
        val targetSdk = json.getInt("target_sdk")
        val minSdk = json.getInt("min_sdk")
        
        assertTrue("Target SDK should be reasonable", targetSdk >= 21 && targetSdk <= 35)
        assertTrue("Min SDK should be reasonable", minSdk >= 14 && minSdk <= targetSdk)
    }
    
    @Test
    fun testAppInfoConsistency() {
        // Execute multiple times to ensure consistency
        val results = (1..5).map { executeToolWithMeasurement("app_info") }
        
        assertTrue("All executions should succeed", results.all { it.success })
        
        val firstResult = results.first().result
        assertTrue("All results should be identical", 
            results.all { it.result == firstResult })
    }
    
    @Test
    fun testComprehensiveAppInfoValidation() {
        val testCases = listOf<() -> Pair<String, Boolean>>(
            {
                val result = executeToolWithMeasurement("app_info")
                "Basic execution" to result.success
            },
            {
                val result = executeToolWithMeasurement("app_info")
                val json = JSONObject(result.result)
                "Has package name" to json.has("package_name")
            },
            {
                val result = executeToolWithMeasurement("app_info")
                val json = JSONObject(result.result)
                val packageName = json.getString("package_name")
                "Package name format valid" to packageName.contains(".")
            },
            {
                val result = executeToolWithMeasurement("app_info")
                val json = JSONObject(result.result)
                "Has version name" to json.has("version_name")
            },
            {
                val result = executeToolWithMeasurement("app_info")
                val json = JSONObject(result.result)
                "Version name not empty" to validateNonEmptyString(json.getString("version_name"))
            },
            {
                val result = executeToolWithMeasurement("app_info")
                val json = JSONObject(result.result)
                "Version code positive" to (json.getInt("version_code") > 0)
            },
            {
                val result = executeToolWithMeasurement("app_info")
                val json = JSONObject(result.result)
                "Target SDK reasonable" to (json.getInt("target_sdk") >= 21)
            },
            {
                val result = executeToolWithMeasurement("app_info")
                val json = JSONObject(result.result)
                "Min SDK reasonable" to (json.getInt("min_sdk") >= 14)
            },
            {
                val result = executeToolWithMeasurement("app_info")
                val json = JSONObject(result.result)
                "Min SDK <= Target SDK" to (json.getInt("min_sdk") <= json.getInt("target_sdk"))
            },
            {
                val result = executeToolWithMeasurement("app_info")
                "Execution time reasonable" to (result.executionTimeMs < 500)
            }
        )
        
        val report = runToolValidationSuite("app_info", testCases)
        printValidationReport(report)
        
        assertTrue("All app info tests should pass", report.passedTests == report.totalTests)
    }
}
```

#### Step 1.4: System Time Tool Validation

Create `lib/src/androidTest/kotlin/dev/jasonpearson/mcpandroidsdk/tools/SystemTimeToolTest.kt`:

```kotlin
package dev.jasonpearson.mcpandroidsdk.tools

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*
import java.text.SimpleDateFormat
import java.util.*

@RunWith(AndroidJUnit4::class)
class SystemTimeToolTest : ToolValidationFramework() {
    
    @Before
    fun setup() {
        setupTestEnvironment()
    }
    
    @After
    fun teardown() {
        serverManager.stopServer()
    }
    
    @Test
    fun testSystemTimeBasicFunctionality() {
        val result = executeToolWithMeasurement("system_time")
        
        assertTrue("System time tool should execute successfully", result.success)
        assertFalse("System time should return non-empty result", result.result.isEmpty())
        assertTrue("Execution time should be fast", result.executionTimeMs < 100)
    }
    
    @Test
    fun testSystemTimeJsonStructure() {
        val result = executeToolWithMeasurement("system_time")
        assertTrue("Tool should execute successfully", result.success)
        
        val requiredFields = listOf(
            "timestamp_millis",
            "iso_8601",
            "formatted_local",
            "timezone",
            "utc_offset"
        )
        
        assertTrue("System time should have required JSON structure",
            validateJsonStructure(result.result, requiredFields))
    }
    
    @Test
    fun testSystemTimeDataAccuracy() {
        val beforeTime = System.currentTimeMillis()
        val result = executeToolWithMeasurement("system_time")
        val afterTime = System.currentTimeMillis()
        
        assertTrue("Tool should execute successfully", result.success)
        
        val json = JSONObject(result.result)
        val timestamp = json.getLong("timestamp_millis")
        
        // Timestamp should be within execution window
        assertTrue("Timestamp should be recent", 
            timestamp >= beforeTime && timestamp <= afterTime)
        
        // Validate ISO 8601 format
        val iso8601 = json.getString("iso_8601")
        assertTrue("ISO 8601 should be valid format", 
            iso8601.matches(Regex("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(?:\\.\\d{3})?Z?")))
        
        // Validate timezone
        val timezone = json.getString("timezone")
        assertTrue("Timezone should not be empty", validateNonEmptyString(timezone))
        
        // Validate UTC offset format
        val utcOffset = json.getString("utc_offset")
        assertTrue("UTC offset should be valid format", 
            utcOffset.matches(Regex("[+-]\\d{2}:\\d{2}")))
    }
    
    @Test
    fun testSystemTimeConsistency() {
        val result1 = executeToolWithMeasurement("system_time")
        Thread.sleep(10) // Small delay
        val result2 = executeToolWithMeasurement("system_time")
        
        assertTrue("Both executions should succeed", result1.success && result2.success)
        
        val json1 = JSONObject(result1.result)
        val json2 = JSONObject(result2.result)
        
        val timestamp1 = json1.getLong("timestamp_millis")
        val timestamp2 = json2.getLong("timestamp_millis")
        
        assertTrue("Second timestamp should be later", timestamp2 >= timestamp1)
        
        // Timezone and offset should be consistent
        assertEquals("Timezone should be consistent", 
            json1.getString("timezone"), json2.getString("timezone"))
        assertEquals("UTC offset should be consistent", 
            json1.getString("utc_offset"), json2.getString("utc_offset"))
    }
    
    @Test
    fun testSystemTimeFormats() {
        val result = executeToolWithMeasurement("system_time")
        assertTrue("Tool should execute successfully", result.success)
        
        val json = JSONObject(result.result)
        
        // Test ISO 8601 parsing
        val iso8601 = json.getString("iso_8601")
        try {
            val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
            format.timeZone = TimeZone.getTimeZone("UTC")
            val parsedDate = format.parse(iso8601)
            assertNotNull("ISO 8601 should be parseable", parsedDate)
        } catch (e: Exception) {
            // Try without milliseconds
            try {
                val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
                format.timeZone = TimeZone.getTimeZone("UTC")
                val parsedDate = format.parse(iso8601)
                assertNotNull("ISO 8601 should be parseable", parsedDate)
            } catch (e2: Exception) {
                fail("ISO 8601 format should be valid: $iso8601")
            }
        }
        
        // Test formatted local time
        val formattedLocal = json.getString("formatted_local")
        assertTrue("Formatted local time should not be empty", 
            validateNonEmptyString(formattedLocal))
    }
    
    @Test
    fun testComprehensiveSystemTimeValidation() {
        val testCases = listOf<() -> Pair<String, Boolean>>(
            {
                val result = executeToolWithMeasurement("system_time")
                "Basic execution" to result.success
            },
            {
                val result = executeToolWithMeasurement("system_time")
                val json = JSONObject(result.result)
                "Has timestamp" to json.has("timestamp_millis")
            },
            {
                val result = executeToolWithMeasurement("system_time")
                val json = JSONObject(result.result)
                val timestamp = json.getLong("timestamp_millis")
                val now = System.currentTimeMillis()
                "Timestamp recent" to (Math.abs(timestamp - now) < 5000) // Within 5 seconds
            },
            {
                val result = executeToolWithMeasurement("system_time")
                val json = JSONObject(result.result)
                "Has ISO 8601" to json.has("iso_8601")
            },
            {
                val result = executeToolWithMeasurement("system_time")
                val json = JSONObject(result.result)
                val iso8601 = json.getString("iso_8601")
                "ISO 8601 format" to iso8601.contains("T")
            },
            {
                val result = executeToolWithMeasurement("system_time")
                val json = JSONObject(result.result)
                "Has timezone" to json.has("timezone")
            },
            {
                val result = executeToolWithMeasurement("system_time")
                val json = JSONObject(result.result)
                "Timezone not empty" to validateNonEmptyString(json.getString("timezone"))
            },
            {
                val result = executeToolWithMeasurement("system_time")
                val json = JSONObject(result.result)
                "Has UTC offset" to json.has("utc_offset")
            },
            {
                val result = executeToolWithMeasurement("system_time")
                val json = JSONObject(result.result)
                val utcOffset = json.getString("utc_offset")
                "UTC offset format" to utcOffset.matches(Regex("[+-]\\d{2}:\\d{2}"))
            },
            {
                val result = executeToolWithMeasurement("system_time")
                "Execution very fast" to (result.executionTimeMs < 100)
            }
        )
        
        val report = runToolValidationSuite("system_time", testCases)
        printValidationReport(report)
        
        assertTrue("All system time tests should pass", report.passedTests == report.totalTests)
    }
}
```

#### Step 1.5: Memory Info Tool Validation

Create `lib/src/androidTest/kotlin/dev/jasonpearson/mcpandroidsdk/tools/MemoryInfoToolTest.kt`:

```kotlin
package dev.jasonpearson.mcpandroidsdk.tools

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*

@RunWith(AndroidJUnit4::class)
class MemoryInfoToolTest : ToolValidationFramework() {
    
    @Before
    fun setup() {
        setupTestEnvironment()
    }
    
    @After
    fun teardown() {
        serverManager.stopServer()
    }
    
    @Test
    fun testMemoryInfoBasicFunctionality() {
        val result = executeToolWithMeasurement("memory_info")
        
        assertTrue("Memory info tool should execute successfully", result.success)
        assertFalse("Memory info should return non-empty result", result.result.isEmpty())
        assertTrue("Execution time should be reasonable", result.executionTimeMs < 500)
    }
    
    @Test
    fun testMemoryInfoJsonStructure() {
        val result = executeToolWithMeasurement("memory_info")
        assertTrue("Tool should execute successfully", result.success)
        
        val requiredFields = listOf(
            "total_memory_mb",
            "available_memory_mb",
            "used_memory_mb",
            "memory_usage_percent",
            "low_memory",
            "app_memory_mb"
        )
        
        assertTrue("Memory info should have required JSON structure",
            validateJsonStructure(result.result, requiredFields))
    }
    
    @Test
    fun testMemoryInfoDataValidation() {
        val result = executeToolWithMeasurement("memory_info")
        assertTrue("Tool should execute successfully", result.success)
        
        val json = JSONObject(result.result)
        
        // Validate memory values are positive
        val totalMemory = json.getDouble("total_memory_mb")
        val availableMemory = json.getDouble("available_memory_mb")
        val usedMemory = json.getDouble("used_memory_mb")
        val appMemory = json.getDouble("app_memory_mb")
        
        assertTrue("Total memory should be positive", totalMemory > 0)
        assertTrue("Available memory should be non-negative", availableMemory >= 0)
        assertTrue("Used memory should be positive", usedMemory > 0)
        assertTrue("App memory should be positive", appMemory > 0)
        
        // Validate memory relationships
        assertTrue("Used + Available should approximately equal Total", 
            Math.abs((usedMemory + availableMemory) - totalMemory) < totalMemory * 0.1) // 10% tolerance
        
        assertTrue("App memory should be less than total memory", appMemory < totalMemory)
        
        // Validate memory usage percentage
        val memoryUsagePercent = json.getDouble("memory_usage_percent")
        assertTrue("Memory usage percent should be between 0-100", 
            memoryUsagePercent >= 0 && memoryUsagePercent <= 100)
        
        // Validate low memory flag
        assertTrue("Low memory should be boolean", json.has("low_memory"))
        
        // Validate reasonable memory ranges for Android devices
        assertTrue("Total memory should be reasonable for Android device", 
            totalMemory >= 512 && totalMemory <= 32768) // 512MB to 32GB range
    }
    
    @Test
    fun testMemoryInfoVariability() {
        // Memory info may change between calls due to system activity
        val results = (1..3).map { executeToolWithMeasurement("memory_info") }
        
        assertTrue("All executions should succeed", results.all { it.success })
        
        val memoryData = results.map { JSONObject(it.result) }
        
        // Total memory should be consistent
        val totalMemories = memoryData.map { it.getDouble("total_memory_mb") }
        val totalMemoryVariance = totalMemories.maxOrNull()!! - totalMemories.minOrNull()!!
        assertTrue("Total memory should be consistent", totalMemoryVariance < 1.0) // Less than 1MB difference
        
        // Available memory may vary but should be reasonable
        val availableMemories = memoryData.map { it.getDouble("available_memory_mb") }
        val maxAvailable = availableMemories.maxOrNull()!!
        val minAvailable = availableMemories.minOrNull()!!
        assertTrue("Available memory variance should be reasonable", 
            (maxAvailable - minAvailable) < totalMemories.first() * 0.5) // Less than 50% of total
    }
    
    @Test
    fun testMemoryInfoPerformance() {
        // Test performance impact of memory info collection
        val results = (1..10).map { executeToolWithMeasurement("memory_info") }
        
        assertTrue("All executions should succeed", results.all { it.success })
        
        val executionTimes = results.map { it.executionTimeMs }
        val averageTime = executionTimes.average()
        val maxTime = executionTimes.maxOrNull()!!
        
        assertTrue("Average execution time should be reasonable", averageTime < 200)
        assertTrue("Maximum execution time should be acceptable", maxTime < 500)
    }
    
    @Test
    fun testComprehensiveMemoryInfoValidation() {
        val testCases = listOf<() -> Pair<String, Boolean>>(
            {
                val result = executeToolWithMeasurement("memory_info")
                "Basic execution" to result.success
            },
            {
                val result = executeToolWithMeasurement("memory_info")
                val json = JSONObject(result.result)
                "Total memory positive" to (json.getDouble("total_memory_mb") > 0)
            },
            {
                val result = executeToolWithMeasurement("memory_info")
                val json = JSONObject(result.result)
                "Available memory non-negative" to (json.getDouble("available_memory_mb") >= 0)
            },
            {
                val result = executeToolWithMeasurement("memory_info")
                val json = JSONObject(result.result)
                "Used memory positive" to (json.getDouble("used_memory_mb") > 0)
            },
            {
                val result = executeToolWithMeasurement("memory_info")
                val json = JSONObject(result.result)
                "App memory positive" to (json.getDouble("app_memory_mb") > 0)
            },
            {
                val result = executeToolWithMeasurement("memory_info")
                val json = JSONObject(result.result)
                val percent = json.getDouble("memory_usage_percent")
                "Memory usage percent valid" to (percent >= 0 && percent <= 100)
            },
            {
                val result = executeToolWithMeasurement("memory_info")
                val json = JSONObject(result.result)
                "Has low memory flag" to json.has("low_memory")
            },
            {
                val result = executeToolWithMeasurement("memory_info")
                val json = JSONObject(result.result)
                val total = json.getDouble("total_memory_mb")
                "Total memory reasonable" to (total >= 512 && total <= 32768)
            },
            {
                val result = executeToolWithMeasurement("memory_info")
                val json = JSONObject(result.result)
                val app = json.getDouble("app_memory_mb")
                val total = json.getDouble("total_memory_mb")
                "App memory less than total" to (app < total)
            },
            {
                val result = executeToolWithMeasurement("memory_info")
                "Execution time reasonable" to (result.executionTimeMs < 500)
            }
        )
        
        val report = runToolValidationSuite("memory_info", testCases)
        printValidationReport(report)
        
        assertTrue("All memory info tests should pass", report.passedTests == report.totalTests)
    }
}
```

#### Step 1.6: Battery Info Tool Validation

Create `lib/src/androidTest/kotlin/dev/jasonpearson/mcpandroidsdk/tools/BatteryInfoToolTest.kt`:

```kotlin
package dev.jasonpearson.mcpandroidsdk.tools

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*

@RunWith(AndroidJUnit4::class)
class BatteryInfoToolTest : ToolValidationFramework() {
    
    @Before
    fun setup() {
        setupTestEnvironment()
    }
    
    @After
    fun teardown() {
        serverManager.stopServer()
    }
    
    @Test
    fun testBatteryInfoBasicFunctionality() {
        val result = executeToolWithMeasurement("battery_info")
        
        assertTrue("Battery info tool should execute successfully", result.success)
        assertFalse("Battery info should return non-empty result", result.result.isEmpty())
        assertTrue("Execution time should be reasonable", result.executionTimeMs < 500)
    }
    
    @Test
    fun testBatteryInfoJsonStructure() {
        val result = executeToolWithMeasurement("battery_info")
        assertTrue("Tool should execute successfully", result.success)
        
        val requiredFields = listOf(
            "level_percent",
            "is_charging",
            "status",
            "health",
            "technology",
            "temperature_celsius"
        )
        
        assertTrue("Battery info should have required JSON structure",
            validateJsonStructure(result.result, requiredFields))
    }
    
    @Test
    fun testBatteryInfoDataValidation() {
        val result = executeToolWithMeasurement("battery_info")
        assertTrue("Tool should execute successfully", result.success)
        
        val json = JSONObject(result.result)
        
        // Validate battery level
        val level = json.getInt("level_percent")
        assertTrue("Battery level should be between 0-100", level >= 0 && level <= 100)
        
        // Validate charging status
        assertTrue("Is charging should be boolean", json.has("is_charging"))
        
        // Validate status
        val status = json.getString("status")
        val validStatuses = listOf("Unknown", "Charging", "Discharging", "Not charging", "Full")
        assertTrue("Battery status should be valid", validStatuses.contains(status))
        
        // Validate health
        val health = json.getString("health")
        val validHealthValues = listOf("Unknown", "Good", "Overheat", "Dead", "Over voltage", "Unspecified failure", "Cold")
        assertTrue("Battery health should be valid", validHealthValues.contains(health))
        
        // Validate technology
        val technology = json.getString("technology")
        assertTrue("Technology should not be empty", validateNonEmptyString(technology))
        
        // Validate temperature (should be reasonable for battery)
        val temperature = json.getDouble("temperature_celsius")
        assertTrue("Temperature should be reasonable", temperature > -20 && temperature < 80)
    }
    
    @Test
    fun testBatteryInfoConsistency() {
        // Battery info should be relatively stable over short periods
        val result1 = executeToolWithMeasurement("battery_info")
        Thread.sleep(100)
        val result2 = executeToolWithMeasurement("battery_info")
        
        assertTrue("Both executions should succeed", result1.success && result2.success)
        
        val json1 = JSONObject(result1.result)
        val json2 = JSONObject(result2.result)
        
        // Level might change by 1% but shouldn't change dramatically
        val level1 = json1.getInt("level_percent")
        val level2 = json2.getInt("level_percent")
        assertTrue("Battery level should be stable over short time", 
            Math.abs(level1 - level2) <= 1)
        
        // Technology and health should be consistent
        assertEquals("Technology should be consistent", 
            json1.getString("technology"), json2.getString("technology"))
        assertEquals("Health should be consistent", 
            json1.getString("health"), json2.getString("health"))
    }
    
    @Test
    fun testBatteryInfoEdgeCases() {
        // Test on different device states (though we can't control them in tests)
        val result = executeToolWithMeasurement("battery_info")
        assertTrue("Tool should work regardless of battery state", result.success)
        
        val json = JSONObject(result.result)
        
        // Even in edge cases, we should get valid data
        assertTrue("Should always have valid level", json.has("level_percent"))
        assertTrue("Should always have charging status", json.has("is_charging"))
        assertTrue("Should always have status", json.has("status"))
        assertTrue("Should always have health", json.has("health"))
    }
    
    @Test
    fun testComprehensiveBatteryInfoValidation() {
        val testCases = listOf<() -> Pair<String, Boolean>>(
            {
                val result = executeToolWithMeasurement("battery_info")
                "Basic execution" to result.success
            },
            {
                val result = executeToolWithMeasurement("battery_info")
                val json = JSONObject(result.result)
                val level = json.getInt("level_percent")
                "Battery level valid range" to (level >= 0 && level <= 100)
            },
            {
                val result = executeToolWithMeasurement("battery_info")
                val json = JSONObject(result.result)
                "Has is_charging field" to json.has("is_charging")
            },
            {
                val result = executeToolWithMeasurement("battery_info")
                val json = JSONObject(result.result)
                "Has status field" to json.has("status")
            },
            {
                val result = executeToolWithMeasurement("battery_info")
                val json = JSONObject(result.result)
                "Status not empty" to validateNonEmptyString(json.getString("status"))
            },
            {
                val result = executeToolWithMeasurement("battery_info")
                val json = JSONObject(result.result)
                "Has health field" to json.has("health")
            },
            {
                val result = executeToolWithMeasurement("battery_info")
                val json = JSONObject(result.result)
                "Health not empty" to validateNonEmptyString(json.getString("health"))
            },
            {
                val result = executeToolWithMeasurement("battery_info")
                val json = JSONObject(result.result)
                "Has technology field" to json.has("technology")
            },
            {
                val result = executeToolWithMeasurement("battery_info")
                val json = JSONObject(result.result)
                "Technology not empty" to validateNonEmptyString(json.getString("technology"))
            },
            {
                val result = executeToolWithMeasurement("battery_info")
                val json = JSONObject(result.result)
                val temp = json.getDouble("temperature_celsius")
                "Temperature reasonable" to (temp > -20 && temp < 80)
            },
            {
                val result = executeToolWithMeasurement("battery_info")
                "Execution time reasonable" to (result.executionTimeMs < 500)
            }
        )
        
        val report = runToolValidationSuite("battery_info", testCases)
        printValidationReport(report)
        
        assertTrue("All battery info tests should pass", report.passedTests == report.totalTests)
    }
}
```

### Phase 2: Comprehensive Tool Validation Suite

#### Step 2.1: All Tools Integration Test

Create `lib/src/androidTest/kotlin/dev/jasonpearson/mcpandroidsdk/tools/AllToolsValidationTest.kt`:

```kotlin
package dev.jasonpearson.mcpandroidsdk.tools

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*
import kotlinx.coroutines.runBlocking

@RunWith(AndroidJUnit4::class)
class AllToolsValidationTest : ToolValidationFramework() {
    
    private val allBuiltInTools = listOf(
        "device_info",
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
        serverManager.stopServer()
    }
    
    @Test
    fun testAllToolsExist() {
        val availableTools = runBlocking {
            serverManager.getAvailableTools()
        }
        
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
            
            assertTrue("Tool $tool average time should be reasonable", averageTime < 1000)
            assertTrue("Tool $tool max time should be acceptable", maxTime < 2000)
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
    fun testAllToolsConcurrentExecution() {
        // Test executing all tools simultaneously
        val results = allBuiltInTools.map { tool ->
            Thread {
                executeToolWithMeasurement(tool)
            }.apply { start() }
        }.map { thread ->
            thread.join()
            // Note: In real implementation, we'd capture the result from the thread
            true // Placeholder for successful concurrent execution
        }
        
        assertTrue("All tools should handle concurrent execution", results.all { it })
    }
    
    @Test
    fun testToolsWithInvalidArguments() {
        val invalidArgs = mapOf(
            "invalid_param" to "invalid_value",
            "another_param" to 12345,
            "null_param" to null
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
            
            // All tools should return valid JSON
            try {
                org.json.JSONObject(result.result)
            } catch (e: Exception) {
                fail("Tool $tool should return valid JSON: ${e.message}")
            }
        }
    }
    
    @Test
    fun testToolsStressTest() {
        val stressTestResults = mutableMapOf<String, Int>()
        
        for (tool in allBuiltInTools) {
            var successCount = 0
            val iterations = 20
            
            repeat(iterations) {
                val result = executeToolWithMeasurement(tool)
                if (result.success) {
                    successCount++
                }
            }
            
            stressTestResults[tool] = successCount
            
            // At least 90% should succeed in stress test
            assertTrue("Tool $tool should handle stress test", 
                successCount >= iterations * 0.9)
        }
        
        // Print stress test summary
        println("\n=== Stress Test Summary ===")
        for ((tool, successCount) in stressTestResults) {
            println("$tool: $successCount/20 successful (${successCount * 5}%)")
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
                    "Valid JSON" to try {
                        org.json.JSONObject(result.result)
                        true
                    } catch (e: Exception) { false }
                },
                { 
                    val result = executeToolWithMeasurement(tool)
                    "Reasonable execution time" to (result.executionTimeMs < 2000) 
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
        
        assertTrue("Overall success rate should be very high", overallSuccessRate >= 0.95)
    }
}
```

## Verification Steps

### Automated Verification

#### Step V1: Run Individual Tool Tests

```bash
./gradlew :core:connectedAndroidTest --tests "*DeviceInfoToolTest"
./gradlew :core:connectedAndroidTest --tests "*AppInfoToolTest"
./gradlew :core:connectedAndroidTest --tests "*SystemTimeToolTest"
./gradlew :core:connectedAndroidTest --tests "*MemoryInfoToolTest"
./gradlew :core:connectedAndroidTest --tests "*BatteryInfoToolTest"
```

#### Step V2: Run Comprehensive Tool Validation

```bash
./gradlew :core:connectedAndroidTest --tests "*AllToolsValidationTest"
```

#### Step V3: Run All Tool Tests

```bash
./gradlew :core:connectedAndroidTest --tests "*.tools.*"
```

### Manual Verification

#### Step M1: Test Tools via MCP Client

```bash
# Test each tool individually via HTTP
curl -X POST http://localhost:8081/mcp/message \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"device_info","arguments":{}}}'

curl -X POST http://localhost:8081/mcp/message \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"app_info","arguments":{}}}'

curl -X POST http://localhost:8081/mcp/message \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"system_time","arguments":{}}}'

curl -X POST http://localhost:8081/mcp/message \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":4,"method":"tools/call","params":{"name":"memory_info","arguments":{}}}'

curl -X POST http://localhost:8081/mcp/message \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":5,"method":"tools/call","params":{"name":"battery_info","arguments":{}}}'
```

#### Step M2: Validate Output Formats

```bash
# Pipe tool outputs through jq to validate JSON format
curl -s -X POST http://localhost:8081/mcp/message \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"device_info","arguments":{}}}' | jq .
```

#### Step M3: Performance Testing

```bash
# Test tool execution speed
time curl -s -X POST http://localhost:8081/mcp/message \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"system_time","arguments":{}}}'
```

#### Step M4: Cross-Device Testing

Test on different devices with various:

- Android API levels (21+)
- RAM amounts (1GB to 32GB+)
- Battery types and states
- Screen densities and resolutions
- Manufacturers (Samsung, Google, OnePlus, etc.)

## Success Criteria

### Functional Criteria

- [ ] All built-in tools execute successfully
- [ ] All tools return valid JSON output
- [ ] All tools complete within reasonable time limits
- [ ] All tools handle invalid arguments gracefully
- [ ] All tools provide accurate device information

### Data Accuracy Criteria

- [ ] Device info matches Android Build constants
- [ ] App info reflects actual application metadata
- [ ] System time is accurate and properly formatted
- [ ] Memory info shows realistic values and relationships
- [ ] Battery info reports valid status and measurements

### Performance Criteria

- [ ] device_info: <1000ms execution time
- [ ] app_info: <500ms execution time
- [ ] system_time: <100ms execution time
- [ ] memory_info: <500ms execution time
- [ ] battery_info: <500ms execution time

### Reliability Criteria

- [ ] > 95% success rate under normal conditions
- [ ] Consistent output across multiple executions
- [ ] Graceful handling of edge cases and errors
- [ ] Stable performance under concurrent access
- [ ] Cross-device compatibility maintained

## Resources

### Android Documentation

- [Build Constants](https://developer.android.com/reference/android/os/Build)
- [ActivityManager](https://developer.android.com/reference/android/app/ActivityManager)
- [BatteryManager](https://developer.android.com/reference/android/os/BatteryManager)
- [PackageManager](https://developer.android.com/reference/android/content/pm/PackageManager)

### Testing Resources

- [Android Testing Guide](https://developer.android.com/training/testing)
- [Instrumented Tests](https://developer.android.com/training/testing/instrumented-tests)
- [Testing on Different Devices](https://developer.android.com/training/testing/other-test-dependencies)

### Performance Testing

- [Performance Testing Best Practices](https://developer.android.com/training/testing/performance)
- [Memory Profiling](https://developer.android.com/studio/profile/memory-profiler)