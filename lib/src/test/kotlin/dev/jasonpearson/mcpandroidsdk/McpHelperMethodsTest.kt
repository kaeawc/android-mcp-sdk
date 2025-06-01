package dev.jasonpearson.mcpandroidsdk

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.jasonpearson.mcpandroidsdk.lifecycle.McpLifecycleManager
import dev.jasonpearson.mcpandroidsdk.models.AndroidResourceContent
import io.modelcontextprotocol.kotlin.sdk.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Test suite for the new helper methods for adding tools, resources, and prompts.
 */
@RunWith(RobolectricTestRunner::class)
class McpHelperMethodsTest {

    private lateinit var manager: McpServerManager
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        manager = McpServerManager.getInstance()
        manager.resetForTesting()
        
        // Initialize the manager
        manager.initialize(context, "Test Server", "1.0.0").getOrThrow()
    }

    @After
    fun tearDown() {
        manager.resetForTesting()
    }

    @Test
    fun `test addMcpTool adds custom tool successfully`() {
        val tool = Tool(
            name = "test_tool",
            description = "A test tool",
            inputSchema = Tool.Input(
                properties = buildJsonObject {},
                required = emptyList()
            )
        )
        
        val handler: suspend (Map<String, Any>) -> CallToolResult = { _ ->
            CallToolResult(
                content = listOf(TextContent(text = "Test result")),
                isError = false
            )
        }
        
        // Add the tool
        manager.addMcpTool(tool, handler)
        
        // Verify it was added
        val tools = manager.getMcpTools()
        assertTrue("Tool should be added to the list", tools.any { it.name == "test_tool" })
    }

    @Test
    fun `test removeMcpTool removes custom tool successfully`() {
        val tool = Tool(
            name = "removable_tool",
            description = "A removable test tool",
            inputSchema = Tool.Input(
                properties = buildJsonObject {},
                required = emptyList()
            )
        )
        
        val handler: suspend (Map<String, Any>) -> CallToolResult = { _ ->
            CallToolResult(
                content = listOf(TextContent(text = "Test result")),
                isError = false
            )
        }
        
        // Add the tool
        manager.addMcpTool(tool, handler)
        
        // Verify it was added
        assertTrue("Tool should be added", manager.getMcpTools().any { it.name == "removable_tool" })
        
        // Remove the tool
        val removed = manager.removeMcpTool("removable_tool")
        assertTrue("Tool should be removed successfully", removed)
        
        // Verify it was removed
        assertFalse("Tool should be removed from the list", manager.getMcpTools().any { it.name == "removable_tool" })
    }

    @Test
    fun `test addMcpResource adds custom resource successfully`() {
        val resource = Resource(
            uri = "test://resource/1",
            name = "Test Resource",
            description = "A test resource",
            mimeType = "text/plain"
        )
        
        val contentProvider: suspend () -> AndroidResourceContent = {
            AndroidResourceContent(
                uri = "test://resource/1",
                text = "Test content",
                mimeType = "text/plain"
            )
        }
        
        // Add the resource
        manager.addMcpResource(resource, contentProvider)
        
        // Verify it was added
        val resources = manager.getMcpResources()
        assertTrue("Resource should be added to the list", resources.any { it.uri == "test://resource/1" })
    }

    @Test
    fun `test readMcpResource reads content correctly`() = runBlocking {
        val resource = Resource(
            uri = "test://resource/readable",
            name = "Readable Resource",
            description = "A readable test resource",
            mimeType = "text/plain"
        )
        
        val testContent = "This is test content"
        val contentProvider: suspend () -> AndroidResourceContent = {
            AndroidResourceContent(
                uri = "test://resource/readable",
                text = testContent,
                mimeType = "text/plain"
            )
        }
        
        // Add the resource
        manager.addMcpResource(resource, contentProvider)
        
        // Read the resource
        val content = manager.readMcpResource("test://resource/readable")
        
        assertEquals("Content should match", testContent, content.text)
        assertEquals("URI should match", "test://resource/readable", content.uri)
        assertEquals("MIME type should match", "text/plain", content.mimeType ?: "text/plain")
    }

    @Test
    fun `test addMcpPrompt adds custom prompt successfully`() {
        val prompt = Prompt(
            name = "test_prompt",
            description = "A test prompt",
            arguments = listOf(
                PromptArgument(
                    name = "input",
                    description = "Input parameter",
                    required = true
                )
            )
        )
        
        val handler: suspend (Map<String, Any?>) -> GetPromptResult = { args ->
            val input = args["input"] as? String ?: "default"
            GetPromptResult(
                description = "Test prompt response",
                messages = listOf(
                    PromptMessage(
                        role = Role.user,
                        content = TextContent(text = "Test prompt with input: $input")
                    )
                )
            )
        }
        
        // Add the prompt
        manager.addMcpPrompt(prompt, handler)
        
        // Verify it was added
        val prompts = manager.getMcpPrompts()
        assertTrue("Prompt should be added to the list", prompts.any { it.name == "test_prompt" })
    }

    @Test
    fun `test getMcpPrompt retrieves prompt with arguments`() = runBlocking {
        val prompt = Prompt(
            name = "parameterized_prompt",
            description = "A prompt with parameters",
            arguments = listOf(
                PromptArgument(
                    name = "message",
                    description = "Message parameter",
                    required = true
                )
            )
        )
        
        val handler: suspend (Map<String, Any?>) -> GetPromptResult = { args ->
            val message = args["message"] as? String ?: "no message"
            GetPromptResult(
                description = "Parameterized prompt response",
                messages = listOf(
                    PromptMessage(
                        role = Role.user,
                        content = TextContent(text = "Received message: $message")
                    )
                )
            )
        }
        
        // Add the prompt
        manager.addMcpPrompt(prompt, handler)
        
        // Get the prompt with arguments
        val result = manager.getMcpPrompt("parameterized_prompt", mapOf("message" to "Hello, World!"))
        
        assertEquals("Description should match", "Parameterized prompt response", result.description)
        assertTrue("Should contain expected message", 
            result.messages.any { message ->
                message.content is TextContent &&
                        (message.content as TextContent).text?.contains("Hello, World!") ?: false
            }
        )
    }

    @Test
    fun `test addSimpleTool creates and adds tool successfully`() {
        val toolName = "simple_test_tool"
        val description = "A simple test tool"
        val parameters = mapOf("input" to "string")
        
        val handler: suspend (Map<String, Any>) -> String = { args ->
            val input = args["input"] as? String ?: "no input"
            "Processed: $input"
        }
        
        // Add the simple tool
        manager.addSimpleTool(toolName, description, parameters, handler)
        
        // Verify it was added to Android tools
        val androidTools = manager.getAndroidTools()
        assertTrue("Simple tool should be added as Android tool", 
            androidTools.any { it.name == toolName }
        )
    }

    @Test
    fun `test addSimplePrompt creates and adds prompt successfully`() {
        val promptName = "simple_test_prompt"
        val description = "A simple test prompt"
        val arguments = listOf(
            PromptArgument(
                name = "topic",
                description = "Topic to discuss",
                required = true
            )
        )
        
        val promptGenerator: suspend (Map<String, Any?>) -> String = { args ->
            val topic = args["topic"] as? String ?: "general"
            "Let's discuss the topic: $topic"
        }
        
        // Add the simple prompt
        manager.addSimplePrompt(promptName, description, arguments, promptGenerator)
        
        // Verify it was added
        val prompts = manager.getMcpPrompts()
        assertTrue("Simple prompt should be added to the list", 
            prompts.any { it.name == promptName }
        )
    }

    @Test
    fun `test lifecycle management integration`() {
        // Test that lifecycle methods don't throw exceptions
        assertNotNull("Lifecycle state should be available", manager.getLifecycleState())
        
        // Test updating lifecycle config
        val config = McpLifecycleManager.LifecycleConfig(
            autoStartOnAppStart = false,
            autoStopOnAppStop = true
        )
        
        // Should not throw exception
        manager.updateLifecycleConfig(config)
        
        val state = manager.getLifecycleState()
        assertEquals("Config should be updated", config, state.config)
    }
}
