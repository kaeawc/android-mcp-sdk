package dev.jasonpearson.mcpandroidsdk.models

/** Prompt-specific data models */

/** Result of getting a prompt */
data class GetPromptResult(val description: String? = null, val messages: List<PromptMessage>)
