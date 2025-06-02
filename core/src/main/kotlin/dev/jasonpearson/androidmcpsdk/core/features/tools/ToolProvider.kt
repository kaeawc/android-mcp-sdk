package dev.jasonpearson.androidmcpsdk.core.features.tools

import android.content.Context
import android.util.Log
import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.Tool
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
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.serializer

/**
 * Public interface for type-safe MCP tool registration.
 *
 * This interface provides type-safe methods for adding custom tools using Kotlin @Serializable data
 * classes. External modules (like debug-bridge) should use this interface to register their tools.
 */
interface McpToolProvider {

    /** Wrapper class to distinguish optional fields specification from required fields. */
    @JvmInline value class OptionalFields(val fields: List<String>)

    /** Remove a custom tool */
    fun removeTool(name: String): Boolean

    // Utility functions for serialization
    fun convertMapToJsonElement(map: Map<*, *>): JsonElement
}

/** Convenience extension to create OptionalFields from a list. */
fun List<String>.asOptional() = McpToolProvider.OptionalFields(this)

/**
 * Type-safe tool registration methods. These are provided as extension functions to work around
 * Kotlin's reified type parameter constraints in interfaces.
 */

/**
 * Add a custom tool with type-safe input handling using optional field specification.
 *
 * This automatically calculates required fields by taking all fields and excluding the optional
 * ones.
 */
inline fun <reified T> McpToolProvider.addTool(
    name: String,
    description: String,
    optional: McpToolProvider.OptionalFields,
    noinline handler: suspend (T) -> CallToolResult,
) {
    (this as ToolProvider).addToolImpl<T>(name, description, optional, handler)
}

/** Add a custom tool with type-safe input handling. */
inline fun <reified T> McpToolProvider.addTool(
    name: String,
    description: String,
    required: List<String> = emptyList(),
    noinline handler: suspend (T) -> CallToolResult,
) {
    (this as ToolProvider).addToolImpl<T>(name, description, required, handler)
}

// Utility extensions for serialization
inline fun <reified T> McpToolProvider.toJsonObject(value: T): JsonObject {
    val jsonString = Json.encodeToString(value)
    return Json.parseToJsonElement(jsonString).jsonObject
}

inline fun <reified T> McpToolProvider.toDataClass(map: Map<String, Any>): T {
    val jsonElement = convertMapToJsonElement(map)
    return Json.decodeFromJsonElement(jsonElement)
}

/**
 * Main tool provider for the MCP server that manages tool registration and provides type-safe tool
 * creation utilities.
 *
 * This class manages tool registration via a ToolRegistry and provides convenient methods for
 * adding type-safe custom tools.
 *
 * ## Nested Object Support
 *
 * The type-safe addTool methods support nested @Serializable objects with dot notation:
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
class ToolProvider(private val context: Context) : McpToolProvider {

    companion object {
        const val TAG = "ToolProvider"
    }

    private val registry = DefaultToolRegistry()

    // Helper function to convert Map to JsonElement recursively
    override fun convertMapToJsonElement(map: Map<*, *>): JsonElement {
        return buildJsonObject {
            map.forEach { (key, value) ->
                when (value) {
                    is Map<*, *> -> put(key.toString(), convertMapToJsonElement(value))
                    is List<*> ->
                        put(
                            key.toString(),
                            JsonArray(
                                value.map { item ->
                                    when (item) {
                                        is Map<*, *> -> convertMapToJsonElement(item)
                                        is String -> JsonPrimitive(item)
                                        is Number -> JsonPrimitive(item)
                                        is Boolean -> JsonPrimitive(item)
                                        null -> JsonNull
                                        else -> JsonPrimitive(item.toString())
                                    }
                                }
                            ),
                        )

                    is String -> put(key.toString(), JsonPrimitive(value))
                    is Number -> put(key.toString(), JsonPrimitive(value))
                    is Boolean -> put(key.toString(), JsonPrimitive(value))
                    null -> put(key.toString(), JsonNull)
                    else -> put(key.toString(), JsonPrimitive(value.toString()))
                }
            }
        }
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
        prefix: String = "",
    ): List<String> {
        val fields = mutableListOf<String>()
        for (i in 0 until descriptor.elementsCount) {
            val elementName = descriptor.getElementName(i)
            val fieldPath = if (prefix.isEmpty()) elementName else "$prefix.$elementName"
            val elementDescriptor = descriptor.getElementDescriptor(i)

            if (
                elementDescriptor.kind == StructureKind.CLASS ||
                    elementDescriptor.kind == SerialKind.CONTEXTUAL
            ) {
                // Recursively process nested objects (assuming they are serializable classes)
                if (
                    elementDescriptor.elementsCount > 0
                ) { // Avoid infinite recursion for primitive-like classes
                    fields.addAll(flattenDescriptorFields(elementDescriptor, fieldPath))
                }
            } else {
                // Leaf node - add the field path
                fields.add(fieldPath)
            }
        }
        return fields
    }

    /** Generate JsonObject schema properties from a SerialDescriptor. */
    public fun descriptorToJsonSchemaProperties(descriptor: SerialDescriptor): JsonObject {
        return buildJsonObject {
            for (i in 0 until descriptor.elementsCount) {
                val name = descriptor.getElementName(i)
                val elementDescriptor = descriptor.getElementDescriptor(i)
                // This is a simplified schema generation, MCP SDK might need more details
                // For now, just creating basic type info.
                when (elementDescriptor.kind) {
                    PrimitiveKind.STRING ->
                        put(name, buildJsonObject { put("type", JsonPrimitive("string")) })

                    PrimitiveKind.INT,
                    PrimitiveKind.LONG,
                    PrimitiveKind.FLOAT,
                    PrimitiveKind.DOUBLE ->
                        put(name, buildJsonObject { put("type", JsonPrimitive("number")) })

                    PrimitiveKind.BOOLEAN ->
                        put(name, buildJsonObject { put("type", JsonPrimitive("boolean")) })

                    StructureKind.LIST ->
                        put(
                            name,
                            buildJsonObject {
                                put("type", JsonPrimitive("array"))
                                // Optionally, add items schema if
                                // elementDescriptor.getElementDescriptor(0) is available
                            },
                        )

                    StructureKind.MAP ->
                        put(
                            name,
                            buildJsonObject { put("type", JsonPrimitive("object")) },
                        ) // Simplified
                    StructureKind.CLASS,
                    SerialKind.CONTEXTUAL -> {
                        // For nested objects, recursively generate their schema
                        put(
                            name,
                            buildJsonObject {
                                put("type", JsonPrimitive("object"))
                                put(
                                    "properties",
                                    descriptorToJsonSchemaProperties(elementDescriptor),
                                )
                                // Determine required fields for nested objects if needed
                            },
                        )
                    }

                    else ->
                        put(name, buildJsonObject { put("type", JsonPrimitive("any")) }) // Fallback
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
    fun getAllTools(): List<Tool> = registry.getAllTools()

    /** Call a specific tool by name with the provided arguments */
    internal suspend fun callTool(name: String, arguments: Map<String, Any>): CallToolResult {
        return registry.callTool(name, arguments)
    }

    /** Internal method to add a tool with its handler (used by type-safe methods) */
    public fun addToolInternal(tool: Tool, handler: suspend (Map<String, Any>) -> CallToolResult) {
        registry.addTool(tool, handler)
        Log.i(TAG, "Added custom tool: ${tool.name}")
    }

    /** Register a tool contributor with this provider */
    fun registerContributor(contributor: ToolContributor) {
        val providerName = contributor.getProviderName()
        Log.i(TAG, "Registering tool contributor: $providerName")

        // Let the contributor register its tools using the type-safe interface
        contributor.registerTools(this)

        // Track the contributor in the registry
        registry.registerContributor(contributor)

        Log.i(
            TAG,
            "Registered tool contributor: $providerName, total tools: ${registry.getAllTools().size}",
        )
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
    inline fun <reified T> addToolImpl(
        name: String,
        description: String,
        optional: McpToolProvider.OptionalFields,
        noinline handler: suspend (T) -> CallToolResult,
    ) {
        // Validate optional field paths
        val validatedOptional = validateFieldPaths<T>(optional.fields, "optional")

        // Get all field names from the data class using recursive enumeration
        val allFields = getAllFieldPaths<T>()

        // Calculate required fields as all fields minus optional ones
        val required = allFields.filterNot { it in validatedOptional }

        // Delegate to the existing required-based implementation
        addToolImpl(name = name, description = description, required = required, handler = handler)
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
    inline fun <reified T> addToolImpl(
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

        val inputSchema =
            Tool.Input(
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
                val typedInput = toDataClass<T>(arguments)
                handler(typedInput)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse tool arguments for $name", e)
                CallToolResult(
                    content =
                        listOf(
                            io.modelcontextprotocol.kotlin.sdk.TextContent(
                                text = "Invalid tool arguments: ${e.message}"
                            )
                        ),
                    isError = true,
                )
            }
        }

        addToolInternal(tool, typedHandler)
    }

    /** Remove a custom tool */
    override fun removeTool(name: String): Boolean = registry.removeTool(name)
}
