package dev.jasonpearson.androidmcpsdk.debugbridge.tools

import android.content.Context
import android.util.Log
import dev.jasonpearson.androidmcpsdk.core.features.tools.McpToolProvider
import dev.jasonpearson.androidmcpsdk.core.features.tools.addTool
import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.TextContent
import java.util.Locale
import kotlinx.serialization.Serializable

/** Provides Android system tools for the debug bridge. */
class AndroidSystemToolProvider(private val context: Context) {

    companion object {
        private const val TAG = "AndroidSystemProvider"
    }

    @Serializable
    data class SystemTimeInput(val format: String = "iso", val timezone: String? = null)

    @Serializable data class EmptyInput(val placeholder: String? = null)

    fun registerTools(toolProvider: McpToolProvider) {
        Log.d(TAG, "Registering system tools")

        // System time tool
        toolProvider.addTool<SystemTimeInput>(
            name = "system_time",
            description = "Get current system time in various formats",
        ) { input ->
            getSystemTime(input)
        }

        // Memory info tool
        toolProvider.addTool<EmptyInput>(
            name = "memory_info",
            description = "Get current memory usage information",
        ) { _ ->
            getMemoryInfo()
        }

        // Battery info tool
        toolProvider.addTool<EmptyInput>(
            name = "battery_info",
            description = "Get current battery status and information",
        ) { _ ->
            getBatteryInfo()
        }

        Log.d(TAG, "System tools registered")
    }

    private suspend fun getSystemTime(input: SystemTimeInput): CallToolResult {
        val format = input.format
        val timezone = input.timezone

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
                            .SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.US)
                            .format(java.util.Date(currentTime))
                    appendLine("- Readable Format: $readableTime")
                }

                else -> {
                    val isoTime = java.time.Instant.ofEpochMilli(currentTime).toString()
                    appendLine("- ISO Format: $isoTime")
                    appendLine("- Timestamp: $currentTime")
                    appendLine(
                        "- Readable Format: ${
                            java.text.SimpleDateFormat(
                                "yyyy-MM-dd HH:mm:ss z",
                                Locale.US,
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

        return CallToolResult(content = listOf(TextContent(text = timeInfo)), isError = false)
    }

    private suspend fun getMemoryInfo(): CallToolResult {
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

    private suspend fun getBatteryInfo(): CallToolResult {
        val batteryManager =
            context.getSystemService(Context.BATTERY_SERVICE) as android.os.BatteryManager

        val info = buildString {
            appendLine("Battery Information:")

            val level =
                batteryManager.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
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

        return String.format(Locale.US, "%.2f %s", size, units[unitIndex])
    }
}
