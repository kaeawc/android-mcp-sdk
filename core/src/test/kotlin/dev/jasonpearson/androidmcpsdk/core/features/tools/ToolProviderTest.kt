package dev.jasonpearson.androidmcpsdk.core.features.tools

import android.content.Context
import io.mockk.mockk
import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.TextContent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ToolProviderTest {

    private lateinit var mockContext: Context
    private lateinit var toolProvider: ToolProvider

    // Test data classes for nested object testing
    @Serializable
    data class UserSettings(
        val theme: String = "light",
        val notifications: Boolean = true,
        val language: String = "en",
    )

    @Serializable
    data class DatabaseConfig(val host: String, val port: Int = 5432, val ssl: Boolean = false)

    @Serializable data class SimpleInput(val name: String, val age: Int = 25)

    @Serializable
    data class NestedInput(val user: UserSettings, val enabled: Boolean, val count: Int = 10)

    @Serializable
    data class DeepNestedInput(
        val user: UserSettings,
        val database: DatabaseConfig,
        val metadata: Map<String, String> = emptyMap(),
    )

    @Serializable
    data class ComplexInput(
        val id: String,
        val settings: UserSettings = UserSettings(),
        val config: DatabaseConfig? = null,
        val active: Boolean = true,
    )

    @Before
    fun setUp() {
        mockContext = mockk<Context>(relaxed = true)
        toolProvider = ToolProvider(mockContext)
    }

    // Field Path Enumeration Tests
    @Test
    fun `should flatten simple object fields`() {
        val fields = toolProvider.getAllFieldPaths<SimpleInput>()

        assertEquals(2, fields.size)
        assertTrue(fields.contains("name"))
        assertTrue(fields.contains("age"))
    }

    @Test
    fun `should flatten nested object fields with dot notation`() {
        val fields = toolProvider.getAllFieldPaths<NestedInput>()

        assertEquals(5, fields.size)
        assertTrue(fields.contains("user.theme"))
        assertTrue(fields.contains("user.notifications"))
        assertTrue(fields.contains("user.language"))
        assertTrue(fields.contains("enabled"))
        assertTrue(fields.contains("count"))
    }

    @Test
    fun `should flatten deeply nested object fields`() {
        val fields = toolProvider.getAllFieldPaths<DeepNestedInput>()

        assertEquals(7, fields.size)
        assertTrue(fields.contains("user.theme"))
        assertTrue(fields.contains("user.notifications"))
        assertTrue(fields.contains("user.language"))
        assertTrue(fields.contains("database.host"))
        assertTrue(fields.contains("database.port"))
        assertTrue(fields.contains("database.ssl"))
        assertTrue(fields.contains("metadata"))
    }

    @Test
    fun `should handle complex nested structures`() {
        val fields = toolProvider.getAllFieldPaths<ComplexInput>()

        assertEquals(8, fields.size)
        assertTrue(fields.contains("id"))
        assertTrue(fields.contains("settings.theme"))
        assertTrue(fields.contains("settings.notifications"))
        assertTrue(fields.contains("settings.language"))
        assertTrue(fields.contains("config.host"))
        assertTrue(fields.contains("config.port"))
        assertTrue(fields.contains("config.ssl"))
        assertTrue(fields.contains("active"))
    }

    @Test
    fun `should flatten JsonObject recursively`() {
        val jsonObject = buildJsonObject {
            put("name", JsonPrimitive("test"))
            put(
                "user",
                buildJsonObject {
                    put("id", JsonPrimitive(123))
                    put(
                        "profile",
                        buildJsonObject {
                            put("bio", JsonPrimitive("hello"))
                            put("age", JsonPrimitive(30))
                        },
                    )
                },
            )
            put("active", JsonPrimitive(true))
        }

        val fields = toolProvider.flattenJsonFields(jsonObject)

        assertEquals(5, fields.size)
        assertTrue(fields.contains("name"))
        assertTrue(fields.contains("user.id"))
        assertTrue(fields.contains("user.profile.bio"))
        assertTrue(fields.contains("user.profile.age"))
        assertTrue(fields.contains("active"))
    }

    // Field Validation Tests
    @Test
    fun `should validate required fields successfully`() {
        val validFields = listOf("name", "age")

        val result = toolProvider.validateFieldPaths<SimpleInput>(validFields, "required")

        assertEquals(validFields, result)
    }

    @Test
    fun `should validate nested required fields successfully`() {
        val validFields = listOf("user.theme", "enabled", "count")

        val result = toolProvider.validateFieldPaths<NestedInput>(validFields, "required")

        assertEquals(validFields, result)
    }

    @Test
    fun `should throw exception for invalid required fields`() {
        val invalidFields = listOf("name", "invalid_field", "age")

        val exception =
            assertThrows(IllegalArgumentException::class.java) {
                toolProvider.validateFieldPaths<SimpleInput>(invalidFields, "required")
            }

        assertTrue(exception.message?.contains("Invalid required fields for SimpleInput") ?: false)
        assertTrue(exception.message?.contains("invalid_field") ?: false)
        assertTrue(exception.message?.contains("Available: name, age") ?: false)
    }

    @Test
    fun `should throw exception for invalid nested fields`() {
        val invalidFields = listOf("user.invalid", "enabled", "user.bad_field")

        val exception =
            assertThrows(IllegalArgumentException::class.java) {
                toolProvider.validateFieldPaths<NestedInput>(invalidFields, "optional")
            }

        assertTrue(exception.message?.contains("Invalid optional fields for NestedInput") ?: false)
        assertTrue(exception.message?.contains("user.invalid") ?: false)
        assertTrue(exception.message?.contains("user.bad_field") ?: false)
    }

    @Test
    fun `should validate optional fields successfully`() {
        val validFields = listOf("settings.theme", "active")

        val result = toolProvider.validateFieldPaths<ComplexInput>(validFields, "optional")

        assertEquals(validFields, result)
    }

    // Type-Safe Tool Registration Tests - Required Fields
    @Test
    fun `should register simple tool with required fields`() = runTest {
        var receivedInput: SimpleInput? = null

        toolProvider.addTool<SimpleInput>(
            name = "simple_tool",
            description = "A simple test tool",
            required = listOf("name"),
        ) { input ->
            receivedInput = input
            CallToolResult(content = listOf(TextContent(text = "OK")))
        }

        val arguments = mapOf("name" to "test", "age" to 30)
        val result = toolProvider.callTool("simple_tool", arguments)

        assertEquals("test", receivedInput?.name)
        assertEquals(30, receivedInput?.age)
        assertFalse(result.isError ?: true)
    }

    @Test
    fun `should register nested tool with required fields`() = runTest {
        var receivedInput: NestedInput? = null

        toolProvider.addTool<NestedInput>(
            name = "nested_tool",
            description = "A nested test tool",
            required = listOf("user.theme", "enabled"),
        ) { input ->
            receivedInput = input
            CallToolResult(content = listOf(TextContent(text = "OK")))
        }

        val arguments =
            mapOf(
                "user" to mapOf("theme" to "dark", "notifications" to false, "language" to "es"),
                "enabled" to true,
                "count" to 5,
            )
        val result = toolProvider.callTool("nested_tool", arguments)

        assertEquals("dark", receivedInput?.user?.theme)
        assertEquals(false, receivedInput?.user?.notifications)
        assertEquals(true, receivedInput?.enabled)
        assertEquals(5, receivedInput?.count)
        assertFalse(result.isError ?: true)
    }

    @Test
    fun `should fail to register tool with invalid required fields`() {
        assertThrows(IllegalArgumentException::class.java) {
            toolProvider.addTool<SimpleInput>(
                name = "invalid_tool",
                description = "Tool with invalid fields",
                required = listOf("name", "invalid_field"),
            ) {
                CallToolResult(content = listOf(TextContent(text = "OK")))
            }
        }
    }

    // Type-Safe Tool Registration Tests - Optional Fields
    @Test
    fun `should register simple tool with optional fields`() = runTest {
        var receivedInput: SimpleInput? = null

        toolProvider.addTool<SimpleInput>(
            name = "optional_simple",
            description = "Simple tool with optional fields",
            optional = listOf("age").asOptional(),
        ) { input ->
            receivedInput = input
            CallToolResult(content = listOf(TextContent(text = "OK")))
        }

        val arguments = mapOf("name" to "test")
        toolProvider.callTool("optional_simple", arguments)

        assertEquals("test", receivedInput?.name)
        assertEquals(25, receivedInput?.age) // default value
    }

    @Test
    fun `should register nested tool with optional fields`() = runTest {
        var receivedInput: ComplexInput? = null

        toolProvider.addTool<ComplexInput>(
            name = "optional_complex",
            description = "Complex tool with optional nested fields",
            optional =
                ToolProvider.OptionalFields(listOf("settings.theme", "config.port", "active")),
        ) { input ->
            receivedInput = input
            CallToolResult(content = listOf(TextContent(text = "OK")))
        }

        val arguments =
            mapOf(
                "id" to "test123",
                "settings" to mapOf("notifications" to false, "language" to "fr"),
                "config" to mapOf("host" to "localhost", "ssl" to true),
            )
        toolProvider.callTool("optional_complex", arguments)

        assertEquals("test123", receivedInput?.id)
        assertEquals("light", receivedInput?.settings?.theme) // default
        assertEquals(false, receivedInput?.settings?.notifications)
        assertEquals("localhost", receivedInput?.config?.host)
        assertEquals(5432, receivedInput?.config?.port) // default
        assertEquals(true, receivedInput?.active) // default
    }

    @Test
    fun `should fail to register tool with invalid optional fields`() {
        assertThrows(IllegalArgumentException::class.java) {
            toolProvider.addTool<NestedInput>(
                name = "invalid_optional",
                description = "Tool with invalid optional fields",
                optional = listOf("user.invalid", "bad_field").asOptional(),
            ) {
                CallToolResult(content = listOf(TextContent(text = "OK")))
            }
        }
    }

    @Test
    fun `should calculate required fields correctly from optional`() = runTest {
        // If we make user.theme and count optional, then user.notifications, user.language, and
        // enabled should be required
        toolProvider.addTool<NestedInput>(
            name = "calculated_required",
            description = "Tool with calculated required fields",
            optional = listOf("user.theme", "count").asOptional(),
        ) { input ->
            CallToolResult(content = listOf(TextContent(text = "OK")))
        }

        // This should succeed with required fields provided
        val validArgs =
            mapOf("user" to mapOf("notifications" to true, "language" to "en"), "enabled" to true)
        val result = toolProvider.callTool("calculated_required", validArgs)
        assertFalse(result.isError ?: true)
    }

    // Tool Execution Tests
    @Test
    fun `should execute tool with correct type conversion`() = runTest {
        var executedInput: ComplexInput? = null

        toolProvider.addTool<ComplexInput>(
            name = "execution_test",
            description = "Test tool execution",
            required = listOf("id"),
        ) { input ->
            executedInput = input
            CallToolResult(content = listOf(TextContent(text = "Executed with id: ${input.id}")))
        }

        val arguments =
            mapOf("id" to "test123", "settings" to mapOf("theme" to "dark"), "active" to false)

        val result = toolProvider.callTool("execution_test", arguments)

        assertEquals("test123", executedInput?.id)
        assertEquals("dark", executedInput?.settings?.theme)
        assertEquals(false, executedInput?.active)
        assertFalse(result.isError ?: true)
        val message = (result.content.first() as TextContent).text ?: ""
        assertTrue(message.contains("Executed with id: test123"))
    }

    @Test
    fun `should handle tool execution errors gracefully`() = runTest {
        toolProvider.addTool<SimpleInput>(
            name = "error_test",
            description = "Tool that throws error",
            required = listOf("name"),
        ) { input ->
            throw RuntimeException("Test error")
        }

        val arguments = mapOf("name" to "test")
        val result = toolProvider.callTool("error_test", arguments)

        assertTrue(result.isError ?: false)
        val message = (result.content.first() as TextContent).text ?: ""
        assertTrue(message.contains("Invalid tool arguments"))
    }

    @Test
    fun `should handle invalid arguments gracefully`() = runTest {
        toolProvider.addTool<SimpleInput>(
            name = "invalid_args_test",
            description = "Tool with invalid args test",
            required = listOf("name"),
        ) { input ->
            CallToolResult(content = listOf(TextContent(text = "OK")))
        }

        // Pass arguments that can't be converted to SimpleInput
        val invalidArguments = mapOf("wrong_field" to "test")
        val result = toolProvider.callTool("invalid_args_test", invalidArguments)

        assertTrue(result.isError ?: false)
        val message = (result.content.first() as TextContent).text ?: ""
        assertTrue(message.contains("Invalid tool arguments"))
    }

    // Tool Management Tests
    @Test
    fun `should remove custom tools`() = runTest {
        toolProvider.addTool<SimpleInput>(
            name = "removable_tool",
            description = "Tool to be removed",
            required = listOf("name"),
        ) {
            CallToolResult(content = listOf(TextContent(text = "OK")))
        }

        val removed = toolProvider.removeTool("removable_tool")
        assertTrue(removed)

        val result = toolProvider.callTool("removable_tool", mapOf("name" to "test"))
        assertTrue(result.isError ?: false)
        val message = (result.content.first() as TextContent).text ?: ""
        assertTrue(message.contains("Tool not found"))
    }

    @Test
    fun `should return false when removing non-existent tool`() {
        val removed = toolProvider.removeTool("non_existent_tool")
        assertFalse(removed)
    }

    // Extension function for creating OptionalFields in tests
    private fun List<String>.asOptional() = ToolProvider.OptionalFields(this)
}
