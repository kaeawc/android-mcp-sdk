package dev.jasonpearson.mcpandroidsdk

import android.content.Context
import android.util.Log
import androidx.annotation.VisibleForTesting
import dev.jasonpearson.mcpandroidsdk.models.*
import kotlinx.coroutines.*

/**
 * Thread-safe singleton manager for MCP Server functionality in Android applications.
 *
 * This manager provides a centralized way to initialize, configure, and control the MCP server
 * lifecycle. It integrates all MCP capabilities including tools, resources, prompts, and provides
 * seamless integration with the MCP Kotlin SDK.
 */
class McpServerManager private constructor() {

    companion object {
        private const val TAG = "McpServerManager"

        @Volatile private var INSTANCE: McpServerManager? = null

        /** Get the singleton instance of McpServerManager */
        fun getInstance(): McpServerManager {
            return INSTANCE
                ?: synchronized(this) { INSTANCE ?: McpServerManager().also { INSTANCE = it } }
        }
    }

    @Volatile
    private var mcpServer: McpAndroidServer? = null

    @Volatile private var isInitialized = false

    /** Initialize the MCP server with the given context and server configuration */
    fun initialize(
        context: Context,
        serverName: String = "Android MCP Server",
        serverVersion: String = "1.0.0",
    ): Result<Unit> = runCatching {
        if (isInitialized) {
            Log.w(TAG, "McpServerManager is already initialized")
            return@runCatching
        }

        Log.d(TAG, "Initializing McpServerManager")

        mcpServer = McpAndroidServer.createServer(
            context = context,
            name = serverName,
            version = serverVersion,
        )

        mcpServer?.initialize()?.getOrThrow()

        isInitialized = true
        Log.i(TAG, "McpServerManager initialized successfully")
    }

    /** Check if the manager is initialized */
    fun isInitialized(): Boolean = isInitialized

    /** Get the MCP SDK version */
    fun getMcpSdkVersion(): String = McpAndroidServer.getMcpSdkVersion()

    /** Start the MCP server in a background thread */
    fun startServerAsync(coroutineScope: CoroutineScope = GlobalScope): Job? {
        if (!isInitialized) {
            Log.e(TAG, "McpServerManager not initialized")
            return null
        }

        return coroutineScope.launch {
            try {
                startServer().getOrThrow()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start MCP server", e)
            }
        }
    }

    /** Start the MCP server (blocking call) */
    suspend fun startServer(): Result<Unit> {
        checkInitialized()
        return mcpServer!!.start()
    }

    /** Stop the MCP server */
    suspend fun stopServer(): Result<Unit> {
        checkInitialized()
        return mcpServer!!.stop()
    }

    /** Check if the server is currently running */
    fun isServerRunning(): Boolean {
        return try {
            mcpServer?.isRunning() ?: false
        } catch (e: Exception) {
            Log.e(TAG, "Error checking server status", e)
            false
        }
    }

    /** Get server information */
    fun getServerInfo(): ServerInfo? {
        return try {
            mcpServer?.getServerInfo()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting server info", e)
            null
        }
    }

    /** Get comprehensive server information */
    fun getComprehensiveServerInfo(): ComprehensiveServerInfo? {
        return try {
            mcpServer?.getComprehensiveServerInfo()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting comprehensive server info", e)
            null
        }
    }

    /** Get the underlying MCP Android server instance */
    fun getMcpServer(): McpAndroidServer {
        checkInitialized()
        return mcpServer!!
    }

    /** Check if SDK integration is available */
    fun hasSDKIntegration(): Boolean {
        return try {
            mcpServer?.hasSDKIntegration() ?: false
        } catch (e: Exception) {
            Log.e(TAG, "Error checking SDK integration", e)
            false
        }
    }

    // Tool operations (Android-specific)

    /** Get all available Android tools */
    fun getAndroidTools(): List<AndroidTool> {
        checkInitialized()
        return mcpServer!!.getAvailableTools()
    }

    /** Execute an Android tool by name */
    suspend fun executeAndroidTool(name: String, arguments: Map<String, Any>): ToolExecutionResult {
        checkInitialized()
        return mcpServer!!.executeTool(name, arguments)
    }

    /** Add a custom Android tool */
    fun addAndroidTool(tool: AndroidTool) {
        checkInitialized()
        mcpServer!!.addTool(tool)
    }

    // MCP SDK Tool operations

    /** Get all available MCP tools */
    fun getMcpTools(): List<io.modelcontextprotocol.kotlin.sdk.Tool> {
        checkInitialized()
        return mcpServer!!.getMcpTools()
    }

    /** Call an MCP tool by name */
    suspend fun callMcpTool(
        name: String,
        arguments: Map<String, Any>
    ): io.modelcontextprotocol.kotlin.sdk.CallToolResult {
        checkInitialized()
        return mcpServer!!.callMcpTool(name, arguments)
    }

    // MCP SDK Resource operations

    /** Get all available MCP resources */
    fun getMcpResources(): List<io.modelcontextprotocol.kotlin.sdk.Resource> {
        checkInitialized()
        return mcpServer!!.getMcpResources()
    }

    // MCP SDK Prompt operations

    /** Get all available MCP prompts */
    fun getMcpPrompts(): List<io.modelcontextprotocol.kotlin.sdk.Prompt> {
        checkInitialized()
        return mcpServer!!.getMcpPrompts()
    }

    // Capabilities

    /** Get server capabilities */
    fun getCapabilities(): ServerCapabilities {
        checkInitialized()
        return mcpServer!!.getComprehensiveServerInfo().capabilities
    }

    // Cleanup resources when the manager is no longer needed
    fun cleanup() {
        isInitialized = false
        mcpServer = null
        Log.d(TAG, "McpServerManager cleaned up")
    }

    /**
     * Reset the singleton instance for testing purposes only.
     * This method should NEVER be called in production code.
     */
    @VisibleForTesting
    fun resetForTesting() {
        synchronized(this) {
            isInitialized = false
            mcpServer = null
            INSTANCE = null
            Log.d(TAG, "McpServerManager reset for testing")
        }
    }

    // Private helper methods

    private fun checkInitialized() {
        if (!isInitialized) {
            throw IllegalStateException(
                "McpServerManager is not initialized. Call initialize() first."
            )
        }
    }
}
