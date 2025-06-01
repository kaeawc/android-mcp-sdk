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
import java.util.Locale

/**
 * Provides Android system tools for the debug bridge.
 */
class AndroidSystemToolProvider(private val context: Context) {

    companion object {
        private const val TAG = "AndroidSystemProvider"
    }

    @Serializable
    data class SystemTimeInput(
        val format: String = "iso",
        val timezone: String? = null
    )

    @Serializable
    data class MemoryInfoInput(
        val placeholder: String? = null
    )

    @Serializable
    data class BatteryInfoInput(
        val placeholder: String? = null
    )

    fun registerTools(registry: ToolRegistry) {
        Log.d(TAG, "Registering system tools")

        // System time tool
        registry.addTool(createSystemTimeTool()) { arguments ->
            getSystemTime(arguments)
        }

        // Memory info tool
        registry.addTool(createMemoryInfoTool()) { arguments ->
            getMemoryInfo(arguments)
        }

        // Battery info tool
        registry.addTool(createBatteryInfoTool()) { arguments ->
            getBatteryInfo(arguments)
        }

        Log.d(TAG, "System tools registered")
    }

    private fun createSystemTimeTool(): Tool {
        return Tool(
            name = "system_time",
            description = "Get current system time in various formats",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    put("type", JsonPrimitive("object"))
                    put("properties", buildJsonObject {
                        put("format", buildJsonObject {
                            put("type", JsonPrimitive("string"))
                            put(
                                "description",
                                JsonPrimitive("Time format (iso, timestamp, readable)")
                            )
                            put("enum", buildJsonArray {
                                add(JsonPrimitive("iso"))
                                add(JsonPrimitive("timestamp"))
                                add(JsonPrimitive("readable"))
                            })
                            put("default", JsonPrimitive("iso"))
                        })
                        put("timezone", buildJsonObject {
                            put("type", JsonPrimitive("string"))
                            put(
                                "description",
                                JsonPrimitive("Timezone (optional, defaults to system timezone)")
                            )
                        })
                    })
                },
                required = emptyList()
            )
        )
    }

    private fun createMemoryInfoTool(): Tool {
        return Tool(
            name = "memory_info",
            description = "Get current memory usage information",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    put("type", JsonPrimitive("object"))
                },
                required = emptyList()
            )
        )
    }

    private fun createBatteryInfoTool(): Tool {
        return Tool(
            name = "battery_info",
            description = "Get current battery status and information",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    put("type", JsonPrimitive("object"))
                },
                required = emptyList()
            )
        )
    }

    private suspend fun getSystemTime(arguments: Map<String, Any>): CallToolResult {
        // For simplicity, let's parse manually since we don't have the full type-safe infrastructure yet
        val format = arguments["format"] as? String ?: "iso"
        val timezone = arguments["timezone"] as? String

        val currentTime = System.currentTimeMillis()
        val timeInfo = buildString {
            appendLine("System Time Information:")

            when (format.lowercase()) {
                "iso" -> {
                    val isoTime =
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                            java.time.Instant.ofEpochMilli(currentTime).toString()
                        } else {
                            java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
                                .apply {
                                    timeZone = java.util.TimeZone.getTimeZone("UTC")
                                }.format(java.util.Date(currentTime))
                        }
                    appendLine("- ISO Format: $isoTime")
                }

                "timestamp" -> {
                    appendLine("- Timestamp: $currentTime")
                }

                "readable" -> {
                    val readableTime =
                        java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.US)
                            .format(java.util.Date(currentTime))
                    appendLine("- Readable Format: $readableTime")
                }

                else -> {
                    val isoTime =
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                            java.time.Instant.ofEpochMilli(currentTime).toString()
                        } else {
                            java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
                                .apply {
                                    timeZone = java.util.TimeZone.getTimeZone("UTC")
                                }.format(java.util.Date(currentTime))
                        }
                    appendLine("- ISO Format: $isoTime")
                    appendLine("- Timestamp: $currentTime")
                    appendLine(
                        "- Readable Format: ${
                            java.text.SimpleDateFormat(
                                "yyyy-MM-dd HH:mm:ss z",
                                Locale.US
                            ).format(java.util.Date(currentTime))
                        }"
                    )
                }
            }

            timezone?.let { tz ->
                appendLine("- Requested Timezone: $tz")
                try {
                    val timeZone = java.util.TimeZone.getTimeZone(tz)
                    val formatter = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.US)
                    formatter.timeZone = timeZone
                    appendLine("- Time in $tz: ${formatter.format(java.util.Date(currentTime))}")
                } catch (e: Exception) {
                    appendLine("- Error with timezone $tz: ${e.message}")
                }
            }

            appendLine("- System Timezone: ${java.util.TimeZone.getDefault().id}")
            appendLine("- Uptime: ${android.os.SystemClock.elapsedRealtime()} ms")
        }

        return CallToolResult(
            content = listOf(TextContent(text = timeInfo)),
            isError = false
        )
    }

    private suspend fun getMemoryInfo(arguments: Map<String, Any>): CallToolResult {
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

        return CallToolResult(
            content = listOf(TextContent(text = info)),
            isError = false
        )
    }

    private suspend fun getBatteryInfo(arguments: Map<String, Any>): CallToolResult {
        val batteryManager =
            context.getSystemService(Context.BATTERY_SERVICE) as android.os.BatteryManager

        val info = buildString {
            appendLine("Battery Information:")

            val level =
                batteryManager.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
            appendLine("- Battery Level: $level%")

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                val isCharging = batteryManager.isCharging
                appendLine("- Charging: $isCharging")
            }

            val chargeCounter =
                batteryManager.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
            if (chargeCounter > 0) {
                appendLine("- Charge Counter: $chargeCounter μAh")
            }

            val currentNow =
                batteryManager.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
            if (currentNow != Integer.MIN_VALUE) {
                appendLine("- Current: ${currentNow / 1000f} mA")
            }

            val energyCounter =
                batteryManager.getLongProperty(android.os.BatteryManager.BATTERY_PROPERTY_ENERGY_COUNTER)
            if (energyCounter > 0) {
                appendLine("- Energy Counter: ${energyCounter / 1000000f} Wh")
            }

            // Get battery intent info
            val batteryIntent = context.registerReceiver(
                null,
                android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED)
            )
            batteryIntent?.let { intent ->
                val status = intent.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1)
                val statusText = when (status) {
                    android.os.BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
                    android.os.BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
                    android.os.BatteryManager.BATTERY_STATUS_FULL -> "Full"
                    android.os.BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Not Charging"
                    else -> "Unknown"
                }
                appendLine("- Status: $statusText")

                val health = intent.getIntExtra(android.os.BatteryManager.EXTRA_HEALTH, -1)
                val healthText = when (health) {
                    android.os.BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
                    android.os.BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat"
                    android.os.BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
                    android.os.BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over Voltage"
                    android.os.BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
                    else -> "Unknown"
                }
                appendLine("- Health: $healthText")

                val plugged = intent.getIntExtra(android.os.BatteryManager.EXTRA_PLUGGED, -1)
                val pluggedText = when (plugged) {
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

        return CallToolResult(
            content = listOf(TextContent(text = info)),
            isError = false
        )
    }

    private fun formatBytes(bytes: Long): String {
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var size = bytes.toDouble()
        var unitIndex = 0

        while (size >= 1024 && unitIndex < units.size - 1) {
            size /= 1024
            unitIndex++
        }

        return String.format(Locale.US, "%.2f %s", size, units[unitIndex])
    }
}
