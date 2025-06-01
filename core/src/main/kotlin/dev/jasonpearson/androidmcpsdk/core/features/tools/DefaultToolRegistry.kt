package dev.jasonpearson.androidmcpsdk.core.features.tools

import android.util.Log
import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.TextContent
import io.modelcontextprotocol.kotlin.sdk.Tool
import java.util.concurrent.ConcurrentHashMap

/**
 * Default implementation of ToolRegistry that manages tool registration and delegation to tool
 * handlers.
 */
class DefaultToolRegistry : ToolRegistry {

    companion object {
        private const val TAG = "DefaultToolRegistry"
    }

    // Storage for tools and their handlers
    private val tools =
        ConcurrentHashMap<String, Pair<Tool, suspend (Map<String, Any>) -> CallToolResult>>()

    // Track contributors for debugging
    private val contributors = mutableSetOf<String>()

    override fun getAllTools(): List<Tool> {
        return tools.values.map { it.first }
    }

    override suspend fun callTool(name: String, arguments: Map<String, Any>): CallToolResult {
        Log.d(TAG, "Calling tool: $name with arguments: $arguments")

        val toolHandler = tools[name]?.second
        return if (toolHandler != null) {
            try {
                toolHandler.invoke(arguments)
            } catch (e: Exception) {
                Log.e(TAG, "Error calling tool $name", e)
                CallToolResult(
                    content =
                        listOf(TextContent(text = "Error executing tool $name: ${e.message}")),
                    isError = true,
                )
            }
        } else {
            CallToolResult(
                content = listOf(TextContent(text = "Tool not found: $name")),
                isError = true,
            )
        }
    }

    override fun addTool(tool: Tool, handler: suspend (Map<String, Any>) -> CallToolResult) {
        tools[tool.name] = Pair(tool, handler)
        Log.i(TAG, "Added tool: ${tool.name}")
    }

    override fun removeTool(name: String): Boolean {
        val removed = tools.remove(name) != null
        if (removed) {
            Log.i(TAG, "Removed tool: $name")
        }
        return removed
    }

    /** Register a tool contributor with this registry */
    fun registerContributor(contributor: ToolContributor) {
        val providerName = contributor.getProviderName()
        Log.i(TAG, "Registering tool contributor: $providerName")

        contributor.registerTools(this)
        contributors.add(providerName)

        Log.i(TAG, "Registered tool contributor: $providerName, total tools: ${tools.size}")
    }

    /** Get the names of all registered contributors */
    fun getContributors(): Set<String> = contributors.toSet()
}
