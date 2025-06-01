package dev.jasonpearson.sampleandroidmcp

import android.util.Log
import dev.jasonpearson.androidmcpsdk.core.McpServerManager
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Configuration class for MCP behavior in the Hilt sample app.
 *
 * This demonstrates how to manage MCP configuration and behavior through dependency injection,
 * making it easy to test and modify.
 */
@Singleton
class McpConfiguration
@Inject
constructor(
    private val mcpServerManager: McpServerManager,
    @Named("mcp_server_name") private val serverName: String,
    @Named("mcp_server_version") private val serverVersion: String,
) {

    companion object {
        private const val TAG = "McpConfiguration"
    }

    /**
     * Configures custom tools specific to the Hilt sample app. This method can be called from
     * activities or other components.
     */
    fun configureCustomTools() {
        try {
            // Add Hilt-specific tool
            mcpServerManager.addSimpleTool(
                name = "hilt_sample_info",
                description = "Get information about the Hilt MCP integration sample",
            ) { arguments ->
                buildString {
                    appendLine("Hilt MCP Integration Sample")
                    appendLine("===========================")
                    appendLine("Server: $serverName v$serverVersion")
                    appendLine("DI Framework: Hilt")
                    appendLine("Initialization: Manual via DI")
                    appendLine("Server Running: ${mcpServerManager.isServerRunning()}")
                    appendLine("SDK Version: ${mcpServerManager.getMcpSdkVersion()}")
                    appendLine("")
                    appendLine("This sample demonstrates:")
                    appendLine("• Hilt dependency injection")
                    appendLine("• Manual MCP initialization")
                    appendLine("• Configuration via DI")
                    appendLine("• Production-ready patterns")
                }
            }

            // Add a tool that uses injected dependencies
            mcpServerManager.addSimpleTool(
                name = "hilt_dependency_demo",
                description = "Demonstrate dependency injection in MCP tools",
            ) { arguments ->
                val input = arguments["input"] as? String ?: "Hello from Hilt!"
                "Processed by $serverName: $input (injected dependencies work!)"
            }

            Log.i(TAG, "✅ Hilt sample custom tools configured")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to configure custom tools", e)
        }
    }

    /** Gets server information formatted for display. */
    fun getServerInfo(): String {
        return "Server: $serverName v$serverVersion (via Hilt DI)"
    }

    /** Checks if the MCP server is ready and properly configured. */
    fun isServerReady(): Boolean {
        return try {
            mcpServerManager.isServerRunning()
        } catch (e: Exception) {
            Log.e(TAG, "Error checking server status", e)
            false
        }
    }
}
