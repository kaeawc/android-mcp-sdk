package dev.jasonpearson.androidmcpsdk.core.features.tools

import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.Tool

/**
 * Registry for MCP tools that can be implemented by different modules.
 *
 * This interface allows the core module to manage tools without knowing
 * about specific implementations, enabling the debug-bridge module
 * to provide its own tool implementations.
 */
interface ToolRegistry {
    /**
     * Get all available tools
     */
    fun getAllTools(): List<Tool>

    /**
     * Call a specific tool by name
     */
    suspend fun callTool(name: String, arguments: Map<String, Any>): CallToolResult

    /**
     * Add a tool with its handler
     */
    fun addTool(tool: Tool, handler: suspend (Map<String, Any>) -> CallToolResult)

    /**
     * Remove a tool by name
     */
    fun removeTool(name: String): Boolean
}

/**
 * Contributes tools to the registry. This is implemented by modules
 * that want to contribute tools to the MCP server.
 */
interface ToolContributor {
    /**
     * Register all tools provided by this module
     */
    fun registerTools(registry: ToolRegistry)

    /**
     * Get the name of this tool provider
     */
    fun getProviderName(): String
}
