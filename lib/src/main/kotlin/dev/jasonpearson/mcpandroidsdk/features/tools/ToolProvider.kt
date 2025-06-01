package dev.jasonpearson.mcpandroidsdk.features.tools

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.TextContent
import io.modelcontextprotocol.kotlin.sdk.Tool
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.serializer
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Provider for MCP tools that exposes Android-specific functionality to MCP clients.
 *
 * This class manages a collection of tools that can be called by MCP clients to interact with
 * Android system functionality and application data.
 *
 * ## Nested Object Support
 *
 * The type-safe addTool methods now support nested @Serializable objects with dot notation:
 * ```kotlin
 * @Serializable
 * data class User(val name: String, val email: String)
 *
 * @Serializable
 * data class Settings(val theme: String, val notifications: Boolean)
 *
 * @Serializable
 * data class CreateAccountInput(
 *     val user: User,
 *     val settings: Settings,
 *     val terms: Boolean
 * )
 *
 * // Field paths: ["user.name", "user.email", "settings.theme", "settings.notifications", "terms"]
 *
 * toolProvider.addTool<CreateAccountInput>(
 *     name = "create_account",
 *     description = "Create user account",
 *     optional = listOf("settings.theme").asOptional()  // All others required
 * ) { input -> ... }
 * ```
 */
class ToolProvider(private val context: Context) {

    companion object {
        const val TAG = "ToolProvider"
    }

    // Storage for custom tools
    private val customTools =
        ConcurrentHashMap<String, Pair<Tool, suspend (Map<String, Any>) -> CallToolResult>>()

    // Tool input data classes
    @Serializable
    data class DeviceInfoInput(
        val placeholder: String? = null // Empty input schema
    )

    @Serializable data class AppInfoInput(val package_name: String? = null)

    @Serializable
    data class SystemTimeInput(val format: String = "iso", val timezone: String? = null)

    @Serializable
    data class MemoryInfoInput(
        val placeholder: String? = null // Empty input schema
    )

    @Serializable
    data class BatteryInfoInput(
        val placeholder: String? = null // Empty input schema
    )

    // Utility functions for serialization
    inline fun <reified T> T.toJsonObject(): JsonObject {
        val jsonString = Json.encodeToString(this)
        return Json.parseToJsonElement(jsonString).jsonObject
    }

    // Helper function to convert Map to JsonElement recursively
    fun convertMapToJsonElement(map: Map<*, *>): JsonElement {
        return buildJsonObject {
            map.forEach { (key, value) ->
                when (value) {
                    is Map<*, *> -> put(key.toString(), convertMapToJsonElement(value))
                    is List<*> -> put(key.toString(), JsonArray(value.map { item ->
                        when (item) {
                            is Map<*, *> -> convertMapToJsonElement(item)
                            is String -> JsonPrimitive(item)
                            is Number -> JsonPrimitive(item)
                            is Boolean -> JsonPrimitive(item)
                            null -> JsonNull
                            else -> JsonPrimitive(item.toString())
                        }
                    }))

                    is String -> put(key.toString(), JsonPrimitive(value))
                    is Number -> put(key.toString(), JsonPrimitive(value))
                    is Boolean -> put(key.toString(), JsonPrimitive(value))
                    null -> put(key.toString(), JsonNull)
                    else -> put(key.toString(), JsonPrimitive(value.toString()))
                }
            }
        }
    }

    inline fun <reified T> Map<String, Any>.toDataClass(): T {
        val jsonElement = convertMapToJsonElement(this)
        return Json.decodeFromJsonElement(jsonElement)
    }

    /**
     * Recursively flatten a JsonObject to extract all field paths using dot notation.
     *
     * Example:
     * ```json
     * {
     *   "user": {
     *     "name": "John",
     *     "settings": {
     *       "theme": "dark"
     *     }
     *   },
     *   "enabled": true
     * }
     * ```
     *
     * Returns: ["user.name", "user.settings.theme", "enabled"]
     */
    public fun flattenJsonFields(jsonObject: JsonObject, prefix: String = ""): List<String> {
        val fields = mutableListOf<String>()

        jsonObject.forEach { (key, value) ->
            val fieldPath = if (prefix.isEmpty()) key else "$prefix.$key"

            when (value) {
                is JsonObject -> {
                    // Recursively process nested objects
                    fields.addAll(flattenJsonFields(value, fieldPath))
                }

                else -> {
                    // Leaf node - add the field path
                    fields.add(fieldPath)
                }
            }
        }

        return fields
    }

    /** Get all flattened field paths from a data class, including nested objects. */
    public inline fun <reified T> getAllFieldPaths(): List<String> {
        val serializer = serializer<T>()
        return flattenDescriptorFields(serializer.descriptor)
    }

    /**
     * Recursively flatten a KSerializer descriptor to extract all field paths using dot notation.
     */
    public fun flattenDescriptorFields(
        descriptor: SerialDescriptor,
        prefix: String = ""
    ): List<String> {
        val fields = mutableListOf<String>()
        for (i in 0 until descriptor.elementsCount) {
            val elementName = descriptor.getElementName(i)
            val fieldPath = if (prefix.isEmpty()) elementName else "$prefix.$elementName"
            val elementDescriptor = descriptor.getElementDescriptor(i)

            if (elementDescriptor.kind == StructureKind.CLASS || elementDescriptor.kind == SerialKind.CONTEXTUAL) {
                // Recursively process nested objects (assuming they are serializable classes)
                if (elementDescriptor.elementsCount > 0) { // Avoid infinite recursion for primitive-like classes
                    fields.addAll(flattenDescriptorFields(elementDescriptor, fieldPath))
                }
            } else {
                // Leaf node - add the field path
                fields.add(fieldPath)
            }
        }
        return fields
    }

    /**
     * Generate JsonObject schema properties from a SerialDescriptor.
     */
    public fun descriptorToJsonSchemaProperties(descriptor: SerialDescriptor): JsonObject {
        return buildJsonObject {
            for (i in 0 until descriptor.elementsCount) {
                val name = descriptor.getElementName(i)
                val elementDescriptor = descriptor.getElementDescriptor(i)
                // This is a simplified schema generation, MCP SDK might need more details
                // For now, just creating basic type info.
                when (elementDescriptor.kind) {
                    PrimitiveKind.STRING -> put(
                        name,
                        buildJsonObject { put("type", JsonPrimitive("string")) })

                    PrimitiveKind.INT, PrimitiveKind.LONG, PrimitiveKind.FLOAT, PrimitiveKind.DOUBLE ->
                        put(name, buildJsonObject { put("type", JsonPrimitive("number")) })

                    PrimitiveKind.BOOLEAN -> put(
                        name,
                        buildJsonObject { put("type", JsonPrimitive("boolean")) })

                    StructureKind.LIST -> put(name, buildJsonObject {
                        put("type", JsonPrimitive("array"))
                        // Optionally, add items schema if elementDescriptor.getElementDescriptor(0) is available
                    })

                    StructureKind.MAP -> put(
                        name,
                        buildJsonObject { put("type", JsonPrimitive("object")) }) // Simplified
                    StructureKind.CLASS, SerialKind.CONTEXTUAL -> {
                        // For nested objects, recursively generate their schema
                        put(name, buildJsonObject {
                            put("type", JsonPrimitive("object"))
                            put("properties", descriptorToJsonSchemaProperties(elementDescriptor))
                            // Determine required fields for nested objects if needed
                        })
                    }

                    else -> put(
                        name,
                        buildJsonObject { put("type", JsonPrimitive("any")) }) // Fallback
                }
            }
        }
    }

    /** Validate that all specified field paths exist in the data class. */
    public inline fun <reified T> validateFieldPaths(
        fieldPaths: List<String>,
        fieldType: String,
    ): List<String> {
        val allFields = getAllFieldPaths<T>()
        val invalidFields = fieldPaths.filterNot { it in allFields }

        if (invalidFields.isNotEmpty()) {
            val errorMessage = buildString {
                appendLine("Invalid $fieldType fields for ${T::class.simpleName}:")
                appendLine("- Invalid: ${invalidFields.joinToString(", ")}")
                appendLine("- Available: ${allFields.joinToString(", ")}")
            }
            Log.e(TAG, errorMessage)
            throw IllegalArgumentException(errorMessage)
        }

        return fieldPaths
    }

    /** Get all available tools including built-in and custom tools */
    fun getAllTools(): List<Tool> {
        val builtInTools = createBuiltInTools()
        val customToolList = customTools.values.map { it.first }
        return builtInTools + customToolList
    }

    /** Call a specific tool by name with the provided arguments */
    internal suspend fun callTool(name: String, arguments: Map<String, Any>): CallToolResult {
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

    /** Internal method to add a tool with its handler (used by type-safe methods) */
    public fun addToolInternal(tool: Tool, handler: suspend (Map<String, Any>) -> CallToolResult) {
        customTools[tool.name] = Pair(tool, handler)
        Log.i(TAG, "Added custom tool: ${tool.name}")
    }

    /** Wrapper class to distinguish optional fields specification from required fields. */
    @JvmInline value class OptionalFields(val fields: List<String>)

    /** Convenience extension to create OptionalFields from a list. */
    fun List<String>.asOptional() = OptionalFields(this)

    /**
     * Add a custom tool with type-safe input handling using optional field specification.
     *
     * This automatically calculates required fields by taking all fields and excluding the optional
     * ones.
     *
     * Example usage:
     * ```kotlin
     * @Serializable
     * data class UserSettings(
     *     val theme: String = "light",
     *     val notifications: Boolean = true
     * )
     *
     * @Serializable
     * data class CreateUserInput(
     *     val name: String,
     *     val email: String,
     *     val age: Int? = null,
     *     val settings: UserSettings = UserSettings()
     * )
     *
     * // Using OptionalFields constructor - nested fields use dot notation
     * toolProvider.addTool<CreateUserInput>(
     *     name = "create_user",
     *     description = "Create a new user account",
     *     optional = OptionalFields(listOf("age", "settings.theme", "settings.notifications"))
     *     // name and email become required, nested settings fields are optional
     * ) { input ->
     *     CallToolResult(content = listOf(TextContent(text = "Created user: ${input.name}")))
     * }
     *
     * // Or using the convenience extension
     * toolProvider.addTool<CreateUserInput>(
     *     name = "create_user_alt",
     *     description = "Alternative user creation",
     *     optional = listOf("age", "settings.theme").asOptional()  // settings.notifications becomes required
     * ) { input ->
     *     CallToolResult(content = listOf(TextContent(text = "Created user: ${input.name}")))
     * }
     * ```
     *
     * @param name The name of the tool
     * @param description A human-readable description of the tool
     * @param optional Wrapper containing list of optional field names (all others become required)
     * @param handler The tool handler that receives typed input
     */
    inline fun <reified T> addTool(
        name: String,
        description: String,
        optional: OptionalFields,
        noinline handler: suspend (T) -> CallToolResult,
    ) {
        // Validate optional field paths
        val validatedOptional = validateFieldPaths<T>(optional.fields, "optional")

        // Get all field names from the data class using recursive enumeration
        val allFields = getAllFieldPaths<T>()

        // Calculate required fields as all fields minus optional ones
        val required = allFields.filterNot { it in validatedOptional }

        // Delegate to the existing required-based implementation
        addTool(name = name, description = description, required = required, handler = handler)
    }

    /**
     * Add a custom tool with type-safe input handling.
     *
     * Example usage:
     * ```kotlin
     * @Serializable
     * data class DatabaseConfig(
     *     val host: String,
     *     val port: Int = 5432,
     *     val ssl: Boolean = false
     * )
     *
     * @Serializable
     * data class CalculateInput(
     *     val operation: String,
     *     val a: Double,
     *     val b: Double,
     *     val config: DatabaseConfig? = null
     * )
     *
     * toolProvider.addTool<CalculateInput>(
     *     name = "calculate",
     *     description = "Perform arithmetic operations with optional database logging",
     *     required = listOf("operation", "a", "b", "config.host")  // config.port and config.ssl use defaults
     * ) { input ->
     *     val result = when (input.operation) {
     *         "add" -> input.a + input.b
     *         "subtract" -> input.a - input.b
     *         "multiply" -> input.a * input.b
     *         "divide" -> if (input.b != 0.0) input.a / input.b else Double.NaN
     *         else -> Double.NaN
     *     }
     *     CallToolResult(
     *         content = listOf(TextContent(text = "Result: $result")),
     *         isError = result.isNaN()
     *     )
     * }
     * ```
     *
     * @param name The name of the tool
     * @param description A human-readable description of the tool
     * @param required List of required field names (optional)
     * @param handler The tool handler that receives typed input
     */
    inline fun <reified T> addTool(
        name: String,
        description: String,
        required: List<String> = emptyList(),
        noinline handler: suspend (T) -> CallToolResult,
    ) {
        // Validate field paths
        val validatedRequired = validateFieldPaths<T>(required, "required")

        // Get the serializer for the data class
        val serializer = serializer<T>()
        val properties = descriptorToJsonSchemaProperties(serializer.descriptor)

        val inputSchema = Tool.Input(
            properties =
                buildJsonObject {
                    put("type", JsonPrimitive("object"))
                    put("properties", properties)
                },
            required = validatedRequired,
        )

        val tool = Tool(name = name, description = description, inputSchema = inputSchema)

        // Wrapper handler that converts Map to typed input
        val typedHandler: suspend (Map<String, Any>) -> CallToolResult = { arguments ->
            try {
                val typedInput = arguments.toDataClass<T>()
                handler(typedInput)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse tool arguments for $name", e)
                CallToolResult(
                    content = listOf(TextContent(text = "Invalid tool arguments: ${e.message}")),
                    isError = true,
                )
            }
        }

        addToolInternal(tool, typedHandler)
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
                "device_info" -> getDeviceInfo(arguments)
                "app_info" -> getAppInfo(arguments)
                "system_time" -> getSystemTime(arguments)
                "memory_info" -> getMemoryInfo(arguments)
                "battery_info" -> getBatteryInfo(arguments)
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
            inputSchema =
                Tool.Input(
                    properties = buildJsonObject { put("type", JsonPrimitive("object")) },
                    required = emptyList(),
                ),
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
                            put("type", JsonPrimitive("object"))
                            put(
                                "properties",
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
                            put("type", JsonPrimitive("object"))
                            put(
                                "properties",
                                buildJsonObject {
                                    put(
                                        "format",
                                        buildJsonObject {
                                            put("type", JsonPrimitive("string"))
                                            put(
                                                "description",
                                                JsonPrimitive(
                                                    "Time format (iso, timestamp, readable)"
                                                ),
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
            inputSchema =
                Tool.Input(
                    properties = buildJsonObject { put("type", JsonPrimitive("object")) },
                    required = emptyList(),
                ),
        )
    }

    private fun createBatteryInfoTool(): Tool {
        return Tool(
            name = "battery_info",
            description = "Get current battery status and information",
            inputSchema =
                Tool.Input(
                    properties = buildJsonObject { put("type", JsonPrimitive("object")) },
                    required = emptyList(),
                ),
        )
    }

    // Built-in tool implementations

    private fun getDeviceInfo(arguments: Map<String, Any>): CallToolResult {
        val input =
            try {
                arguments.toDataClass<DeviceInfoInput>()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse device_info arguments, using defaults", e)
                DeviceInfoInput()
            }

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
        val input =
            try {
                arguments.toDataClass<AppInfoInput>()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse app_info arguments, using defaults", e)
                AppInfoInput()
            }

        val packageName = input.package_name ?: context.packageName

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
                    "- Install Time: ${
                        java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                            .format(java.util.Date(packageInfo.firstInstallTime))
                    }"
                )
                appendLine(
                    "- Update Time: ${
                        java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                            .format(java.util.Date(packageInfo.lastUpdateTime))
                    }"
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
        val input =
            try {
                arguments.toDataClass<SystemTimeInput>()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse system_time arguments, using defaults", e)
                SystemTimeInput()
            }

        val currentTime = System.currentTimeMillis()
        val timeInfo = buildString {
            appendLine("System Time Information:")

            when (input.format.lowercase()) {
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
                    appendLine("- ISO Format: ${java.time.Instant.ofEpochMilli(currentTime)}")
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

            input.timezone?.let { timezone ->
                appendLine("- Requested Timezone: $timezone")
                try {
                    val tz = java.util.TimeZone.getTimeZone(timezone)
                    val formatter = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.US)
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

    private fun getMemoryInfo(arguments: Map<String, Any>): CallToolResult {
        val input =
            try {
                arguments.toDataClass<MemoryInfoInput>()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse memory_info arguments, using defaults", e)
                MemoryInfoInput()
            }

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

    private fun getBatteryInfo(arguments: Map<String, Any>): CallToolResult {
        val input =
            try {
                arguments.toDataClass<BatteryInfoInput>()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse battery_info arguments, using defaults", e)
                BatteryInfoInput()
            }

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
