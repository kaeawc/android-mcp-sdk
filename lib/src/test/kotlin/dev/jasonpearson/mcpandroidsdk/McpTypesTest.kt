package dev.jasonpearson.mcpandroidsdk

import dev.jasonpearson.mcpandroidsdk.models.*
import org.junit.Assert.*
import org.junit.Test

/** Unit tests for MCP types and data structures */
class McpTypesTest {

    @Test
    fun `TextContent should create correct instance`() {
        val content = TextContent(text = "Hello World")

        assertEquals("text", content.type)
        assertEquals("Hello World", content.text)
    }

    @Test
    fun `ImageContent should create correct instance`() {
        val content = ImageContent(data = "base64data", mimeType = "image/png")

        assertEquals("image", content.type)
        assertEquals("base64data", content.data)
        assertEquals("image/png", content.mimeType)
    }

    @Test
    fun `EmbeddedResource should create correct instance`() {
        val resource =
            ResourceData(uri = "file://test.txt", text = "test content", mimeType = "text/plain")
        val content = EmbeddedResource(resource = resource)

        assertEquals("resource", content.type)
        assertEquals("file://test.txt", content.resource.uri)
        assertEquals("test content", content.resource.text)
        assertEquals("text/plain", content.resource.mimeType)
    }

    @Test
    fun `ResourceData should handle text content`() {
        val resource =
            ResourceData(uri = "file://test.txt", text = "test content", mimeType = "text/plain")

        assertEquals("file://test.txt", resource.uri)
        assertEquals("test content", resource.text)
        assertNull(resource.blob)
        assertEquals("text/plain", resource.mimeType)
    }

    @Test
    fun `ResourceData should handle binary content`() {
        val resource =
            ResourceData(
                uri = "file://test.png",
                blob = "base64encodeddata",
                mimeType = "image/png",
            )

        assertEquals("file://test.png", resource.uri)
        assertNull(resource.text)
        assertEquals("base64encodeddata", resource.blob)
        assertEquals("image/png", resource.mimeType)
    }

    @Test
    fun `MessageRole should have correct values`() {
        assertEquals("user", MessageRole.USER.value)
        assertEquals("assistant", MessageRole.ASSISTANT.value)
    }

    @Test
    fun `PromptMessage should create correct instance`() {
        val content = TextContent(text = "Test message")
        val message = PromptMessage(role = MessageRole.USER, content = content)

        assertEquals(MessageRole.USER, message.role)
        assertEquals(content, message.content)
    }

    @Test
    fun `ToolCallResult should handle success case`() {
        val content = listOf(TextContent(text = "Success"))
        val result = ToolCallResult(content = content, isError = false)

        assertEquals(content, result.content)
        assertFalse(result.isError)
    }

    @Test
    fun `ToolCallResult should handle error case`() {
        val content = listOf(TextContent(text = "Error occurred"))
        val result = ToolCallResult(content = content, isError = true)

        assertEquals(content, result.content)
        assertTrue(result.isError)
    }

    @Test
    fun `ServerCapabilities should create with default values`() {
        val capabilities = ServerCapabilities()

        assertTrue(capabilities.experimental.isEmpty())
        assertTrue(capabilities.logging.isEmpty())
        assertNull(capabilities.prompts)
        assertNull(capabilities.resources)
        assertNull(capabilities.tools)
    }

    @Test
    fun `ServerCapabilities should create with all capabilities`() {
        val capabilities =
            ServerCapabilities(
                prompts = PromptsCapability(listChanged = true),
                resources = ResourcesCapability(subscribe = true, listChanged = true),
                tools = ToolsCapability(listChanged = true),
            )

        assertTrue(capabilities.prompts?.listChanged ?: false)
        assertTrue(capabilities.resources?.subscribe ?: false)
        assertTrue(capabilities.resources?.listChanged ?: false)
        assertTrue(capabilities.tools?.listChanged ?: false)
    }

    @Test
    fun `ModelPreferences should create with hints and priorities`() {
        val hints = listOf(ModelHint("claude-3"), ModelHint("gpt-4"))
        val preferences =
            ModelPreferences(
                hints = hints,
                costPriority = 0.8f,
                speedPriority = 0.6f,
                intelligencePriority = 0.9f,
            )

        assertEquals(2, preferences.hints.size)
        assertEquals("claude-3", preferences.hints[0].name)
        assertEquals("gpt-4", preferences.hints[1].name)
        assertEquals(0.8f, preferences.costPriority)
        assertEquals(0.6f, preferences.speedPriority)
        assertEquals(0.9f, preferences.intelligencePriority)
    }

    @Test
    fun `SamplingRequest should create with all parameters`() {
        val messages = listOf(PromptMessage(MessageRole.USER, TextContent(text = "Hello")))
        val preferences = ModelPreferences(hints = listOf(ModelHint("claude-3")))
        val request =
            SamplingRequest(
                messages = messages,
                modelPreferences = preferences,
                systemPrompt = "You are helpful",
                includeContext = "thisServer",
                maxTokens = 1000,
                temperature = 0.7f,
                stopSequences = listOf("STOP"),
                metadata = mapOf("key" to "value"),
            )

        assertEquals(messages, request.messages)
        assertEquals(preferences, request.modelPreferences)
        assertEquals("You are helpful", request.systemPrompt)
        assertEquals("thisServer", request.includeContext)
        assertEquals(1000, request.maxTokens)
        assertEquals(0.7f, request.temperature)
        assertEquals(listOf("STOP"), request.stopSequences)
        assertEquals(mapOf("key" to "value"), request.metadata)
    }

    @Test
    fun `Implementation should create correct instance`() {
        val impl = Implementation(name = "Android MCP SDK", version = "1.0.0")

        assertEquals("Android MCP SDK", impl.name)
        assertEquals("1.0.0", impl.version)
    }

    @Test
    fun `Root should create correct instance`() {
        val root = Root(uri = "file:///app/files", name = "App Files")

        assertEquals("file:///app/files", root.uri)
        assertEquals("App Files", root.name)
    }

    @Test
    fun `Root should create with minimal parameters`() {
        val root = Root(uri = "file:///app/cache")

        assertEquals("file:///app/cache", root.uri)
        assertNull(root.name)
    }
}
