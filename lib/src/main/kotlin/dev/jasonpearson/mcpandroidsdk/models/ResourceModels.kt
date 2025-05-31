package dev.jasonpearson.mcpandroidsdk.models

/**
 * Resource-specific data models
 */

/**
 * Resource content wrapper for simple text content
 */
data class ResourceContent(
    val uri: String,
    val text: String,
    val mimeType: String = "text/plain"
)