package dev.jasonpearson.mcpandroidsdk.features.tools

import android.content.Context
import io.mockk.mockk
import kotlinx.serialization.Serializable
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class ToolProviderDebugTest {

    private lateinit var mockContext: Context
    private lateinit var toolProvider: ToolProvider

    @Serializable
    data class SimpleInput(
        val name: String,
        val age: Int = 25
    )

    @Before
    fun setUp() {
        mockContext = mockk<Context>(relaxed = true)
        toolProvider = ToolProvider(mockContext)
    }

    @Test
    fun `debug field enumeration`() {
        try {
            val fields = toolProvider.getAllFieldPaths<SimpleInput>()
            println("Fields found: $fields")
            assertEquals(2, fields.size)
        } catch (e: Exception) {
            println("Exception in getAllFieldPaths: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }

    @Test
    fun `debug validation`() {
        try {
            val validFields = listOf("name", "age")
            val result = toolProvider.validateFieldPaths<SimpleInput>(validFields, "required")
            println("Validation result: $result")
            assertEquals(validFields, result)
        } catch (e: Exception) {
            println("Exception in validateFieldPaths: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }

    @Test
    fun `debug field enumeration detailed`() {
        try {
            println("=== Starting field enumeration debug ===")

            // Test 1: Try to create instance manually
            val instance = SimpleInput("test", 30)
            println("Created instance manually: $instance")

            // Test 2: Try to convert to JsonObject
            val jsonObj = with(toolProvider) { instance.toJsonObject() }
            println("JsonObject: $jsonObj")

            // Test 3: Try flattening
            val flattened = toolProvider.flattenJsonFields(jsonObj)
            println("Flattened fields: $flattened")

            // Test 4: Try with no-arg constructor
            try {
                val noArgInstance = SimpleInput::class.java.getDeclaredConstructor().newInstance()
                println("No-arg instance: $noArgInstance")
            } catch (e: Exception) {
                println("No-arg constructor failed: ${e.message}")

                // Test 5: Try with default constructor that has defaults
                try {
                    val defaultInstance = SimpleInput("default", 25)
                    println("Default instance: $defaultInstance")
                    val defaultJson = with(toolProvider) { defaultInstance.toJsonObject() }
                    println("Default JsonObject: $defaultJson")
                    val defaultFlattened = toolProvider.flattenJsonFields(defaultJson)
                    println("Default flattened: $defaultFlattened")
                } catch (e2: Exception) {
                    println("Default constructor failed: ${e2.message}")
                }
            }

            // Test 6: Call getAllFieldPaths
            val fields = toolProvider.getAllFieldPaths<SimpleInput>()
            println("getAllFieldPaths result: $fields")

        } catch (e: Exception) {
            println("Exception in detailed debug: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }
}
