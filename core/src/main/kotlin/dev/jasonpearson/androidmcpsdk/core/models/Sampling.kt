package dev.jasonpearson.androidmcpsdk.core.models

import io.modelcontextprotocol.kotlin.sdk.*

/** Sampling-related data models for MCP */

/** Model preferences for sampling */
data class ModelPreferences(
    val hints: List<ModelHint> = emptyList(),
    val costPriority: Float? = null,
    val speedPriority: Float? = null,
    val intelligencePriority: Float? = null,
)

/** Model hint for sampling */
data class ModelHint(val name: String)

/** Sampling request */
data class SamplingRequest(
    val messages: List<PromptMessage>,
    val modelPreferences: ModelPreferences? = null,
    val systemPrompt: String? = null,
    val includeContext: String? = null,
    val maxTokens: Int? = null,
    val temperature: Float? = null,
    val stopSequences: List<String>? = null,
    val metadata: Map<String, Any>? = null,
)
