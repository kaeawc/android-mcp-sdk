package dev.jasonpearson.androidmcpsdk.core.models

/** MCP capability definitions for server and client */

/** Server capabilities */
data class ServerCapabilities(
    val experimental: Map<String, Any> = emptyMap(),
    val logging: Map<String, Any> = emptyMap(),
    val prompts: PromptsCapability? = null,
    val resources: ResourcesCapability? = null,
    val tools: ToolsCapability? = null,
)

/** Prompts capability */
data class PromptsCapability(val listChanged: Boolean = false)

/** Resources capability */
data class ResourcesCapability(val subscribe: Boolean = false, val listChanged: Boolean = false)

/** Tools capability */
data class ToolsCapability(val listChanged: Boolean = false)

/** Client capabilities */
data class ClientCapabilities(
    val experimental: Map<String, Any> = emptyMap(),
    val roots: RootsCapability? = null,
    val sampling: SamplingCapability? = null,
)

/** Roots capability */
data class RootsCapability(val listChanged: Boolean = false)

/** Sampling capability */
data class SamplingCapability(val enabled: Boolean = true)
