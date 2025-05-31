package dev.jasonpearson.mcpandroidsdk.features.tools

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import dev.jasonpearson.mcpandroidsdk.models.*
import io.modelcontextprotocol.kotlin.sdk.*
import java.util.concurrent.ConcurrentHashMap
import kotlinx.serialization.json.*

/**
 * Provider for MCP tools that exposes Android-specific functionality to MCP clients.
 *
 * This class manages a collection of tools that can be called by MCP clients to interact with
 * Android system functionality and application data.
 */
class ToolProvider(private val context: Context) {

    companion object {
        private const val TAG = "ToolProvider"
    }

    // Storage for custom tools
    private val customTools =
        ConcurrentHashMap<String, Pair<Tool, suspend (Map<String, Any>) -> CallToolResult>>()

    /** Get all available tools including built-in and custom tools */
    fun getAllTools(): List<Tool> {
        val builtInTools = createBuiltInTools()
        val customToolList = customTools.values.map { it.first }
        return builtInTools + customToolList
    }

    /** Call a specific tool by name with the provided arguments */
    suspend fun callTool(name: String, arguments: Map<String, Any>): CallToolResult {
        Log.d(TAG, "Calling tool: $name with arguments: $arguments")

        return when {
            customTools.containsKey(name) -> {
                val handler = customTools[name]?.second
                handler?.invoke(arguments)
                    ?: CallToolResult(
                        content =
                            listOf(TextContent(text = "Custom tool handler not found for $name")),
                        isError = true,
                    )
            }
            name in getBuiltInToolNames() -> callBuiltInTool(name, arguments)
            else ->
                CallToolResult(
                    content = listOf(TextContent(text = "Tool not found: $name")),
                    isError = true,
                )
        }
    }

    /** Add a custom tool with its handler */
    fun addTool(tool: Tool, handler: suspend (Map<String, Any>) -> CallToolResult) {
        customTools[tool.name] = Pair(tool, handler)
        Log.i(TAG, "Added custom tool: ${tool.name}")
    }

    /** Remove a custom tool */
    fun removeTool(name: String): Boolean {
        val removed = customTools.remove(name) != null
        if (removed) {
            Log.i(TAG, "Removed custom tool: $name")
        }
        return removed
    }

    /** Create built-in Android-specific tools */
    private fun createBuiltInTools(): List<Tool> {
        return listOf(
            createDeviceInfoTool(),
            createAppInfoTool(),
            createSystemTimeTool(),
            createMemoryInfoTool(),
            createBatteryInfoTool(),
        )
    }

    private fun getBuiltInToolNames(): Set<String> {
        return setOf("device_info", "app_info", "system_time", "memory_info", "battery_info")
    }

    /** Handle built-in tool calls */
    private suspend fun callBuiltInTool(name: String, arguments: Map<String, Any>): CallToolResult {
        Log.d(TAG, "Calling built-in tool: $name")
        return try {
            when (name) {
                "device_info" -> getDeviceInfo()
                "app_info" -> getAppInfo(arguments)
                "system_time" -> getSystemTime(arguments)
                "memory_info" -> getMemoryInfo()
                "battery_info" -> getBatteryInfo()
                else ->
                    CallToolResult(
                        content = listOf(TextContent(text = "Unknown built-in tool: $name")),
                        isError = true,
                    )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error calling built-in tool $name", e)
            CallToolResult(
                content = listOf(TextContent(text = "Error executing tool $name: ${e.message}")),
                isError = true,
            )
        }
    }

    // Built-in tool definitions

    private fun createDeviceInfoTool(): Tool {
        return Tool(
            name = "device_info",
            description = "Get information about the Android device",
            inputSchema = Tool.Input(properties = buildJsonObject {}, required = emptyList()),
        )
    }

    private fun createAppInfoTool(): Tool {
        return Tool(
            name = "app_info",
            description = "Get information about installed applications",
            inputSchema =
                Tool.Input(
                    properties =
                        buildJsonObject {
                            put(
                                "package_name",
                                buildJsonObject {
                                    put("type", JsonPrimitive("string"))
                                    put(
                                        "description",
                                        JsonPrimitive(
                                            "Package name of the app (optional, if not provided returns current app info)"
                                        ),
                                    )
                                },
                            )
                        },
                    required = emptyList(),
                ),
        )
    }

    private fun createSystemTimeTool(): Tool {
        return Tool(
            name = "system_time",
            description = "Get current system time in various formats",
            inputSchema =
                Tool.Input(
                    properties =
                        buildJsonObject {
                            put(
                                "format",
                                buildJsonObject {
                                    put("type", JsonPrimitive("string"))
                                    put(
                                        "description",
                                        JsonPrimitive("Time format (iso, timestamp, readable)"),
                                    )
                                    put(
                                        "enum",
                                        buildJsonArray {
                                            add(JsonPrimitive("iso"))
                                            add(JsonPrimitive("timestamp"))
                                            add(JsonPrimitive("readable"))
                                        },
                                    )
                                    put("default", JsonPrimitive("iso"))
                                },
                            )
                            put(
                                "timezone",
                                buildJsonObject {
                                    put("type", JsonPrimitive("string"))
                                    put(
                                        "description",
                                        JsonPrimitive(
                                            "Timezone (optional, defaults to system timezone)"
                                        ),
                                    )
                                },
                            )
                        },
                    required = emptyList(),
                ),
        )
    }

    private fun createMemoryInfoTool(): Tool {
        return Tool(
            name = "memory_info",
            description = "Get current memory usage information",
            inputSchema = Tool.Input(properties = buildJsonObject {}, required = emptyList()),
        )
    }

    private fun createBatteryInfoTool(): Tool {
        return Tool(
            name = "battery_info",
            description = "Get current battery status and information",
            inputSchema = Tool.Input(properties = buildJsonObject {}, required = emptyList()),
        )
    }

    // Built-in tool implementations

    private fun getDeviceInfo(): CallToolResult {
        val deviceInfo = buildString {
            appendLine("Device Information:")
            appendLine("- Model: ${Build.MODEL}")
            appendLine("- Manufacturer: ${Build.MANUFACTURER}")
            appendLine("- Brand: ${Build.BRAND}")
            appendLine("- Device: ${Build.DEVICE}")
            appendLine("- Product: ${Build.PRODUCT}")
            appendLine("- Android Version: ${Build.VERSION.RELEASE}")
            appendLine("- API Level: ${Build.VERSION.SDK_INT}")
            appendLine("- Build ID: ${Build.ID}")
            appendLine("- Fingerprint: ${Build.FINGERPRINT}")
        }

        return CallToolResult(content = listOf(TextContent(text = deviceInfo)), isError = false)
    }

    private fun getAppInfo(arguments: Map<String, Any>): CallToolResult {
        val packageName = arguments["package_name"] as? String ?: context.packageName

        return try {
            val packageManager = context.packageManager
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            val appName = packageManager.getApplicationLabel(appInfo).toString()

            val info = buildString {
                appendLine("Application Information:")
                appendLine("- App Name: $appName")
                appendLine("- Package Name: $packageName")
                appendLine("- Version Name: ${packageInfo.versionName}")
                appendLine("- Version Code: ${packageInfo.longVersionCode}")
                appendLine("- Target SDK: ${appInfo.targetSdkVersion}")
                appendLine("- Min SDK: ${appInfo.minSdkVersion}")
                appendLine(
                    "- Install Time: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(java.util.Date(packageInfo.firstInstallTime))}"
                )
                appendLine(
                    "- Update Time: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(java.util.Date(packageInfo.lastUpdateTime))}"
                )
                appendLine("- Data Directory: ${appInfo.dataDir}")
            }

            CallToolResult(content = listOf(TextContent(text = info)), isError = false)
        } catch (e: PackageManager.NameNotFoundException) {
            CallToolResult(
                content = listOf(TextContent(text = "Package not found: $packageName")),
                isError = true,
            )
        }
    }

    private fun getSystemTime(arguments: Map<String, Any>): CallToolResult {
        val format = arguments["format"] as? String ?: "iso"
        val timezone = arguments["timezone"] as? String

        val currentTime = System.currentTimeMillis()
        val timeInfo = buildString {
            appendLine("System Time Information:")

            when (format.lowercase()) {
                "iso" -> {
                    val isoTime = java.time.Instant.ofEpochMilli(currentTime).toString()
                    appendLine("- ISO Format: $isoTime")
                }
                "timestamp" -> {
                    appendLine("- Timestamp: $currentTime")
                }
                "readable" -> {
                    val readableTime =
                        java.text
                            .SimpleDateFormat("yyyy-MM-dd HH:mm:ss z")
                            .format(java.util.Date(currentTime))
                    appendLine("- Readable Format: $readableTime")
                }
                else -> {
                    appendLine("- ISO Format: ${java.time.Instant.ofEpochMilli(currentTime)}")
                    appendLine("- Timestamp: $currentTime")
                    appendLine(
                        "- Readable Format: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss z").format(java.util.Date(currentTime))}"
                    )
                }
            }

            if (timezone != null) {
                appendLine("- Requested Timezone: $timezone")
                try {
                    val tz = java.util.TimeZone.getTimeZone(timezone)
                    val formatter = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss z")
                    formatter.timeZone = tz
                    appendLine(
                        "- Time in $timezone: ${formatter.format(java.util.Date(currentTime))}"
                    )
                } catch (e: Exception) {
                    appendLine("- Error with timezone $timezone: ${e.message}")
                }
            }

            appendLine("- System Timezone: ${java.util.TimeZone.getDefault().id}")
            appendLine("- Uptime: ${android.os.SystemClock.elapsedRealtime()} ms")
        }

        return CallToolResult(content = listOf(TextContent(text = timeInfo)), isError = false)
    }

    private fun getMemoryInfo(): CallToolResult {
        val activityManager =
            context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val memoryInfo = android.app.ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)

        val runtime = Runtime.getRuntime()
        val maxMemory = runtime.maxMemory()
        val totalMemory = runtime.totalMemory()
        val freeMemory = runtime.freeMemory()
        val usedMemory = totalMemory - freeMemory

        val info = buildString {
            appendLine("Memory Information:")
            appendLine("System Memory:")
            appendLine("- Available Memory: ${formatBytes(memoryInfo.availMem)}")
            appendLine("- Total Memory: ${formatBytes(memoryInfo.totalMem)}")
            appendLine("- Low Memory: ${memoryInfo.lowMemory}")
            appendLine("- Memory Threshold: ${formatBytes(memoryInfo.threshold)}")
            appendLine()
            appendLine("App Memory (Heap):")
            appendLine("- Max Heap Size: ${formatBytes(maxMemory)}")
            appendLine("- Total Heap: ${formatBytes(totalMemory)}")
            appendLine("- Used Heap: ${formatBytes(usedMemory)}")
            appendLine("- Free Heap: ${formatBytes(freeMemory)}")
            appendLine("- Heap Usage: ${(usedMemory * 100 / maxMemory)}%")
        }

        return CallToolResult(content = listOf(TextContent(text = info)), isError = false)
    }

    private fun getBatteryInfo(): CallToolResult {
        val batteryManager =
            context.getSystemService(Context.BATTERY_SERVICE) as android.os.BatteryManager

        val info = buildString {
            appendLine("Battery Information:")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val level =
                    batteryManager.getIntProperty(
                        android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY
                    )
                appendLine("- Battery Level: $level%")

                val isCharging = batteryManager.isCharging
                appendLine("- Charging: $isCharging")

                val chargeCounter =
                    batteryManager.getIntProperty(
                        android.os.BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER
                    )
                if (chargeCounter > 0) {
                    appendLine("- Charge Counter: $chargeCounter μAh")
                }

                val currentNow =
                    batteryManager.getIntProperty(
                        android.os.BatteryManager.BATTERY_PROPERTY_CURRENT_NOW
                    )
                if (currentNow != Integer.MIN_VALUE) {
                    appendLine("- Current: ${currentNow / 1000f} mA")
                }

                val energyCounter =
                    batteryManager.getLongProperty(
                        android.os.BatteryManager.BATTERY_PROPERTY_ENERGY_COUNTER
                    )
                if (energyCounter > 0) {
                    appendLine("- Energy Counter: ${energyCounter / 1000000f} Wh")
                }
            } else {
                appendLine("- Detailed battery info requires Android 5.0+")
            }

            // Get battery intent info
            val batteryIntent =
                context.registerReceiver(
                    null,
                    android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED),
                )
            batteryIntent?.let { intent ->
                val status = intent.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1)
                val statusText =
                    when (status) {
                        android.os.BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
                        android.os.BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
                        android.os.BatteryManager.BATTERY_STATUS_FULL -> "Full"
                        android.os.BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Not Charging"
                        else -> "Unknown"
                    }
                appendLine("- Status: $statusText")

                val health = intent.getIntExtra(android.os.BatteryManager.EXTRA_HEALTH, -1)
                val healthText =
                    when (health) {
                        android.os.BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
                        android.os.BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat"
                        android.os.BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
                        android.os.BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over Voltage"
                        android.os.BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
                        else -> "Unknown"
                    }
                appendLine("- Health: $healthText")

                val plugged = intent.getIntExtra(android.os.BatteryManager.EXTRA_PLUGGED, -1)
                val pluggedText =
                    when (plugged) {
                        android.os.BatteryManager.BATTERY_PLUGGED_AC -> "AC"
                        android.os.BatteryManager.BATTERY_PLUGGED_USB -> "USB"
                        android.os.BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
                        else -> "Not Plugged"
                    }
                appendLine("- Power Source: $pluggedText")

                val temperature =
                    intent.getIntExtra(android.os.BatteryManager.EXTRA_TEMPERATURE, -1)
                if (temperature > 0) {
                    appendLine("- Temperature: ${temperature / 10f}°C")
                }

                val voltage = intent.getIntExtra(android.os.BatteryManager.EXTRA_VOLTAGE, -1)
                if (voltage > 0) {
                    appendLine("- Voltage: ${voltage / 1000f}V")
                }
            }
        }

        return CallToolResult(content = listOf(TextContent(text = info)), isError = false)
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
