package dev.jasonpearson.mcpandroidsdk.models

/** Core MCP types for Android implementation */

/** Content types that can be included in MCP messages */
sealed interface McpContent

/** Text content */
data class TextContent(val type: String = "text", val text: String) : McpContent

/** Image content */
data class ImageContent(
    val type: String = "image",
    val data: String, // base64 encoded
    val mimeType: String,
) : McpContent

/** Embedded resource content */
data class EmbeddedResource(val type: String = "resource", val resource: ResourceData) : McpContent

/** Resource data structure */
data class ResourceData(
    val uri: String,
    val text: String? = null,
    val blob: String? = null, // base64 encoded binary data
    val mimeType: String? = null,
)

/** Message role in conversation */
enum class MessageRole(val value: String) {
    USER("user"),
    ASSISTANT("assistant"),
}

/** Message in a conversation */
data class PromptMessage(val role: MessageRole, val content: McpContent)

/** Result of a tool call execution */
data class ToolCallResult(val content: List<McpContent>, val isError: Boolean = false)

/** Implementation info for server identification */
data class Implementation(val name: String, val version: String)

/** Root directory definition */
data class Root(val uri: String, val name: String? = null)
