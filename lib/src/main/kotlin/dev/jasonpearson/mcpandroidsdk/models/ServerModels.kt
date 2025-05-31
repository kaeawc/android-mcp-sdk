package dev.jasonpearson.mcpandroidsdk.models

import android.content.Context

/** Server-specific data models */

/** Information about the MCP server */
data class ServerInfo(
    val name: String,
    val version: String,
    val sdkVersion: String,
    val isRunning: Boolean,
    val toolCount: Int = 0,
)

/** Comprehensive server information */
data class ComprehensiveServerInfo(
    val name: String,
    val version: String,
    val sdkVersion: String,
    val isRunning: Boolean,
    val isInitialized: Boolean,
    val capabilities: ServerCapabilities,
    val toolCount: Int,
    val resourceCount: Int,
    val promptCount: Int,
    val rootCount: Int,
)

/** Represents an Android-specific tool that can be executed by the MCP server */
data class AndroidTool(
    val name: String,
    val description: String,
    val parameters: Map<String, String>,
    val executor: suspend (Context, Map<String, Any>) -> String,
) {
    suspend fun execute(context: Context, arguments: Map<String, Any>): String {
        return executor(context, arguments)
    }
}

/** Result of executing a tool */
data class ToolExecutionResult(val success: Boolean, val result: String?, val error: String?)
