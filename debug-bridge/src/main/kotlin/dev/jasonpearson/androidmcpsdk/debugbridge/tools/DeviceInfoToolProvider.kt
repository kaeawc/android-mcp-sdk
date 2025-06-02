package dev.jasonpearson.androidmcpsdk.debugbridge.tools

import android.content.Context
import android.os.Build
import android.util.Log
import dev.jasonpearson.androidmcpsdk.core.features.tools.McpToolProvider
import dev.jasonpearson.androidmcpsdk.core.features.tools.addTool
import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.TextContent
import kotlinx.serialization.Serializable

/** Provides device information tools for the debug bridge. */
class DeviceInfoToolProvider(private val context: Context) {

    companion object {
        private const val TAG = "DeviceInfoToolProvider"
    }

    @Serializable
    data class EmptyInput(
        val placeholder: String? = null // Empty input schema for tools that need no parameters
    )

    fun registerTools(toolProvider: McpToolProvider) {
        Log.d(TAG, "Registering device info tools")

        // Device info tool
        toolProvider.addTool<EmptyInput>(
            name = "device_info",
            description = "Get information about the Android device",
        ) { _ ->
            getDeviceInfo()
        }

        // Hardware info tool
        toolProvider.addTool<EmptyInput>(
            name = "hardware_info",
            description = "Get hardware information including CPU, display, and sensor details",
        ) { _ ->
            getHardwareInfo()
        }

        // System info tool
        toolProvider.addTool<EmptyInput>(
            name = "system_info",
            description = "Get detailed system information including build details and SDK versions",
        ) { _ ->
            getSystemInfo()
        }

        Log.d(TAG, "Device info tools registered")
    }

    private suspend fun getDeviceInfo(): CallToolResult {
        val deviceInfo = buildString {
            appendLine("=== DEVICE INFORMATION ===")
            appendLine("Model: ${Build.MODEL}")
            appendLine("Manufacturer: ${Build.MANUFACTURER}")
            appendLine("Brand: ${Build.BRAND}")
            appendLine("Device: ${Build.DEVICE}")
            appendLine("Product: ${Build.PRODUCT}")
            appendLine("Hardware: ${Build.HARDWARE}")
            appendLine("Supported ABIs: ${Build.SUPPORTED_ABIS.joinToString(", ")}")
            appendLine("Board: ${Build.BOARD}")
            appendLine("Display: ${Build.DISPLAY}")
        }

        return CallToolResult(content = listOf(TextContent(text = deviceInfo)), isError = false)
    }

    private suspend fun getHardwareInfo(): CallToolResult {
        val hardwareInfo = buildString {
            appendLine("=== HARDWARE INFORMATION ===")
            appendLine("CPU ABI: ${Build.CPU_ABI}")
            appendLine("CPU ABI2: ${Build.CPU_ABI2}")

            val windowManager =
                context.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager
            val display = windowManager.defaultDisplay
            val metrics = android.util.DisplayMetrics()
            display.getMetrics(metrics)

            appendLine("Display Info:")
            appendLine("  - Width: ${metrics.widthPixels}px")
            appendLine("  - Height: ${metrics.heightPixels}px")
            appendLine("  - Density: ${metrics.density}")
            appendLine("  - DPI: ${metrics.densityDpi}")
            appendLine("  - Scaled Density: ${metrics.scaledDensity}")
        }

        return CallToolResult(content = listOf(TextContent(text = hardwareInfo)), isError = false)
    }

    private suspend fun getSystemInfo(): CallToolResult {
        val systemInfo = buildString {
            appendLine("=== SYSTEM INFORMATION ===")
            appendLine("Android Version: ${Build.VERSION.RELEASE}")
            appendLine("API Level: ${Build.VERSION.SDK_INT}")
            appendLine("Build ID: ${Build.ID}")
            appendLine("Build Type: ${Build.TYPE}")
            appendLine("Build Tags: ${Build.TAGS}")
            appendLine("Fingerprint: ${Build.FINGERPRINT}")
            appendLine("Incremental: ${Build.VERSION.INCREMENTAL}")
            appendLine("Codename: ${Build.VERSION.CODENAME}")
            appendLine("Security Patch: ${Build.VERSION.SECURITY_PATCH}")
        }

        return CallToolResult(content = listOf(TextContent(text = systemInfo)), isError = false)
    }
}
