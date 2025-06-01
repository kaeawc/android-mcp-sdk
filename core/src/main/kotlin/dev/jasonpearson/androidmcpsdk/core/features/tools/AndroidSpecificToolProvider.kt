package dev.jasonpearson.androidmcpsdk.core.features.tools

import android.content.Context
import android.os.Build
import android.util.Log
import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.TextContent
import io.modelcontextprotocol.kotlin.sdk.Tool
import java.util.concurrent.ConcurrentHashMap
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

/**
 * Enhanced tool provider with comprehensive Android-specific functionality.
 *
 * This provider includes tools for:
 * - Device and system information
 * - Network connectivity
 * - Storage and file system
 * - Application management
 * - Hardware sensors
 * - Security and permissions
 */
class AndroidSpecificToolProvider(private val context: Context) {

    companion object {
        private const val TAG = "AndroidSpecificToolProvider"
    }

    // Storage for custom tools
    private val customTools =
        ConcurrentHashMap<String, Pair<Tool, suspend (Map<String, Any>) -> CallToolResult>>()

    /** Get all available tools including built-in and custom tools */
    fun getAllTools(): List<Tool> {
        val builtInTools = createComprehensiveBuiltInTools()
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

    private fun createComprehensiveBuiltInTools(): List<Tool> {
        return listOf(
            // Device Information Tools
            createDeviceInfoTool(),
            createSystemInfoTool(),
            createHardwareInfoTool(),

            // Application Tools
            createAppInfoTool(),
            createInstalledAppsListTool(),

            // Network Tools
            createNetworkInfoTool(),
            createConnectivityStatusTool(),

            // Storage Tools
            createStorageInfoTool(),
            createDirectoryListingTool(),

            // System Tools
            createSystemTimeTool(),
            createMemoryInfoTool(),
            createBatteryInfoTool(),
            createCpuInfoTool(),

            // Security Tools
            createPermissionsInfoTool(),
            createSecuritySettingsTool(),
        )
    }

    private fun getBuiltInToolNames(): Set<String> {
        return setOf(
            "device_info",
            "system_info",
            "hardware_info",
            "app_info",
            "installed_apps_list",
            "network_info",
            "connectivity_status",
            "storage_info",
            "directory_listing",
            "system_time",
            "memory_info",
            "battery_info",
            "cpu_info",
            "permissions_info",
            "security_settings",
        )
    }

    private suspend fun callBuiltInTool(name: String, arguments: Map<String, Any>): CallToolResult {
        Log.d(TAG, "Calling built-in tool: $name")
        return try {
            when (name) {
                // Device Information
                "device_info" -> getDeviceInfo()
                "system_info" -> getSystemInfo()
                "hardware_info" -> getHardwareInfo()

                // Application
                "app_info" -> getAppInfo(arguments)
                "installed_apps_list" -> getInstalledAppsList(arguments)

                // Network
                "network_info" -> getNetworkInfo()
                "connectivity_status" -> getConnectivityStatus()

                // Storage
                "storage_info" -> getStorageInfo()
                "directory_listing" -> getDirectoryListing(arguments)

                // System
                "system_time" -> getSystemTime(arguments)
                "memory_info" -> getMemoryInfo()
                "battery_info" -> getBatteryInfo()
                "cpu_info" -> getCpuInfo()

                // Security
                "permissions_info" -> getPermissionsInfo()
                "security_settings" -> getSecuritySettings()

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

    // Tool definitions

    private fun createDeviceInfoTool(): Tool {
        return Tool(
            name = "device_info",
            description =
                "Get comprehensive device information including model, manufacturer, OS version",
            inputSchema = Tool.Input(properties = buildJsonObject {}, required = emptyList()),
        )
    }

    private fun createSystemInfoTool(): Tool {
        return Tool(
            name = "system_info",
            description =
                "Get detailed system information including build details and SDK versions",
            inputSchema = Tool.Input(properties = buildJsonObject {}, required = emptyList()),
        )
    }

    private fun createHardwareInfoTool(): Tool {
        return Tool(
            name = "hardware_info",
            description = "Get hardware information including CPU, display, and sensor details",
            inputSchema = Tool.Input(properties = buildJsonObject {}, required = emptyList()),
        )
    }

    private fun createAppInfoTool(): Tool {
        return Tool(
            name = "app_info",
            description = "Get detailed information about a specific application",
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
                                        JsonPrimitive("Package name of the app (optional)"),
                                    )
                                },
                            )
                        },
                    required = emptyList(),
                ),
        )
    }

    private fun createInstalledAppsListTool(): Tool {
        return Tool(
            name = "installed_apps_list",
            description = "Get list of installed applications with filtering options",
            inputSchema =
                Tool.Input(
                    properties =
                        buildJsonObject {
                            put(
                                "include_system",
                                buildJsonObject {
                                    put("type", JsonPrimitive("boolean"))
                                    put(
                                        "description",
                                        JsonPrimitive("Include system apps in the list"),
                                    )
                                    put("default", JsonPrimitive(false))
                                },
                            )
                            put(
                                "limit",
                                buildJsonObject {
                                    put("type", JsonPrimitive("integer"))
                                    put(
                                        "description",
                                        JsonPrimitive("Maximum number of apps to return"),
                                    )
                                    put("default", JsonPrimitive(50))
                                },
                            )
                        },
                    required = emptyList(),
                ),
        )
    }

    private fun createNetworkInfoTool(): Tool {
        return Tool(
            name = "network_info",
            description = "Get current network connection information",
            inputSchema = Tool.Input(properties = buildJsonObject {}, required = emptyList()),
        )
    }

    private fun createConnectivityStatusTool(): Tool {
        return Tool(
            name = "connectivity_status",
            description = "Get detailed network connectivity status and capabilities",
            inputSchema = Tool.Input(properties = buildJsonObject {}, required = emptyList()),
        )
    }

    private fun createStorageInfoTool(): Tool {
        return Tool(
            name = "storage_info",
            description = "Get storage information for internal and external storage",
            inputSchema = Tool.Input(properties = buildJsonObject {}, required = emptyList()),
        )
    }

    private fun createDirectoryListingTool(): Tool {
        return Tool(
            name = "directory_listing",
            description =
                "List contents of a directory (restricted to app's accessible directories)",
            inputSchema =
                Tool.Input(
                    properties =
                        buildJsonObject {
                            put(
                                "path",
                                buildJsonObject {
                                    put("type", JsonPrimitive("string"))
                                    put(
                                        "description",
                                        JsonPrimitive(
                                            "Directory path to list (relative to app's files directory)"
                                        ),
                                    )
                                    put("default", JsonPrimitive("."))
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
            description = "Get current system time in various formats and timezones",
            inputSchema =
                Tool.Input(
                    properties =
                        buildJsonObject {
                            put(
                                "format",
                                buildJsonObject {
                                    put("type", JsonPrimitive("string"))
                                    put("description", JsonPrimitive("Time format"))
                                    put(
                                        "enum",
                                        buildJsonArray {
                                            add(JsonPrimitive("iso"))
                                            add(JsonPrimitive("timestamp"))
                                            add(JsonPrimitive("readable"))
                                            add(JsonPrimitive("all"))
                                        },
                                    )
                                    put("default", JsonPrimitive("all"))
                                },
                            )
                            put(
                                "timezone",
                                buildJsonObject {
                                    put("type", JsonPrimitive("string"))
                                    put("description", JsonPrimitive("Timezone (optional)"))
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
            description = "Get comprehensive memory usage information",
            inputSchema = Tool.Input(properties = buildJsonObject {}, required = emptyList()),
        )
    }

    private fun createBatteryInfoTool(): Tool {
        return Tool(
            name = "battery_info",
            description = "Get detailed battery status and health information",
            inputSchema = Tool.Input(properties = buildJsonObject {}, required = emptyList()),
        )
    }

    private fun createCpuInfoTool(): Tool {
        return Tool(
            name = "cpu_info",
            description = "Get CPU information and current load statistics",
            inputSchema = Tool.Input(properties = buildJsonObject {}, required = emptyList()),
        )
    }

    private fun createPermissionsInfoTool(): Tool {
        return Tool(
            name = "permissions_info",
            description = "Get information about app permissions and their status",
            inputSchema = Tool.Input(properties = buildJsonObject {}, required = emptyList()),
        )
    }

    private fun createSecuritySettingsTool(): Tool {
        return Tool(
            name = "security_settings",
            description = "Get information about device security settings",
            inputSchema = Tool.Input(properties = buildJsonObject {}, required = emptyList()),
        )
    }

    // Tool implementations (placeholder methods - these would be implemented with actual
    // functionality)

    private fun getDeviceInfo(): CallToolResult {
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

    private fun getSystemInfo(): CallToolResult {
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

    private fun getHardwareInfo(): CallToolResult {
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

    // Placeholder implementations for other tools - these would be fully implemented
    private fun getAppInfo(arguments: Map<String, Any>): CallToolResult {
        return CallToolResult(
            content = listOf(TextContent(text = "App info tool - implementation pending")),
            isError = false,
        )
    }

    private fun getInstalledAppsList(arguments: Map<String, Any>): CallToolResult {
        return CallToolResult(
            content =
                listOf(TextContent(text = "Installed apps list tool - implementation pending")),
            isError = false,
        )
    }

    private fun getNetworkInfo(): CallToolResult {
        return CallToolResult(
            content = listOf(TextContent(text = "Network info tool - implementation pending")),
            isError = false,
        )
    }

    private fun getConnectivityStatus(): CallToolResult {
        return CallToolResult(
            content =
                listOf(TextContent(text = "Connectivity status tool - implementation pending")),
            isError = false,
        )
    }

    private fun getStorageInfo(): CallToolResult {
        return CallToolResult(
            content = listOf(TextContent(text = "Storage info tool - implementation pending")),
            isError = false,
        )
    }

    private fun getDirectoryListing(arguments: Map<String, Any>): CallToolResult {
        return CallToolResult(
            content = listOf(TextContent(text = "Directory listing tool - implementation pending")),
            isError = false,
        )
    }

    private fun getSystemTime(arguments: Map<String, Any>): CallToolResult {
        return CallToolResult(
            content = listOf(TextContent(text = "System time tool - implementation pending")),
            isError = false,
        )
    }

    private fun getMemoryInfo(): CallToolResult {
        return CallToolResult(
            content = listOf(TextContent(text = "Memory info tool - implementation pending")),
            isError = false,
        )
    }

    private fun getBatteryInfo(): CallToolResult {
        return CallToolResult(
            content = listOf(TextContent(text = "Battery info tool - implementation pending")),
            isError = false,
        )
    }

    private fun getCpuInfo(): CallToolResult {
        return CallToolResult(
            content = listOf(TextContent(text = "CPU info tool - implementation pending")),
            isError = false,
        )
    }

    private fun getPermissionsInfo(): CallToolResult {
        return CallToolResult(
            content = listOf(TextContent(text = "Permissions info tool - implementation pending")),
            isError = false,
        )
    }

    private fun getSecuritySettings(): CallToolResult {
        return CallToolResult(
            content = listOf(TextContent(text = "Security settings tool - implementation pending")),
            isError = false,
        )
    }
}
