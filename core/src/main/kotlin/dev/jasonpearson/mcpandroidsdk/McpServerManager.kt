package dev.jasonpearson.mcpandroidsdk

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.annotation.VisibleForTesting
import dev.jasonpearson.mcpandroidsdk.lifecycle.McpLifecycleManager
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

    @Volatile private var mcpServer: McpAndroidServer? = null

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

        mcpServer =
            McpAndroidServer.createServer(
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
        arguments: Map<String, Any>,
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

    /** Get a specific MCP prompt with arguments */
    suspend fun getMcpPrompt(
        name: String,
        arguments: Map<String, Any?> = emptyMap(),
    ): io.modelcontextprotocol.kotlin.sdk.GetPromptResult {
        checkInitialized()
        return mcpServer!!.getMcpPrompt(name, arguments)
    }

    // Helper methods for adding tools, resources, and prompts

    /** Add a custom MCP tool with its handler */
    fun addMcpTool(
        tool: io.modelcontextprotocol.kotlin.sdk.Tool,
        handler: suspend (Map<String, Any>) -> io.modelcontextprotocol.kotlin.sdk.CallToolResult,
    ) {
        checkInitialized()
        mcpServer!!.addMcpTool(tool, handler)
    }

    /** Remove a custom MCP tool */
    fun removeMcpTool(name: String): Boolean {
        checkInitialized()
        return mcpServer!!.removeMcpTool(name)
    }

    /** Add a custom MCP resource with its content provider */
    fun addMcpResource(
        resource: io.modelcontextprotocol.kotlin.sdk.Resource,
        contentProvider: suspend () -> AndroidResourceContent,
    ) {
        checkInitialized()
        mcpServer!!.addMcpResource(resource, contentProvider)
    }

    /** Add a custom MCP resource template */
    fun addMcpResourceTemplate(template: io.modelcontextprotocol.kotlin.sdk.ResourceTemplate) {
        checkInitialized()
        mcpServer!!.addMcpResourceTemplate(template)
    }

    /** Read content from an MCP resource */
    suspend fun readMcpResource(uri: String): AndroidResourceContent {
        checkInitialized()
        return mcpServer!!.readMcpResource(uri)
    }

    /** Subscribe to an MCP resource for updates */
    fun subscribeMcpResource(uri: String) {
        checkInitialized()
        mcpServer!!.subscribeMcpResource(uri)
    }

    /** Unsubscribe from an MCP resource */
    fun unsubscribeMcpResource(uri: String) {
        checkInitialized()
        mcpServer!!.unsubscribeMcpResource(uri)
    }

    /** Add a custom MCP prompt with its handler */
    fun addMcpPrompt(
        prompt: io.modelcontextprotocol.kotlin.sdk.Prompt,
        handler: suspend (Map<String, Any?>) -> io.modelcontextprotocol.kotlin.sdk.GetPromptResult,
    ) {
        checkInitialized()
        mcpServer!!.addMcpPrompt(prompt, handler)
    }

    /** Remove a custom MCP prompt */
    fun removeMcpPrompt(name: String): Boolean {
        checkInitialized()
        return mcpServer!!.removeMcpPrompt(name)
    }

    // Convenience methods for creating common tool types

    /** Create and add a simple text-based tool */
    fun addSimpleTool(
        name: String,
        description: String,
        parameters: Map<String, String> = emptyMap(),
        handler: suspend (Map<String, Any>) -> String,
    ) {
        checkInitialized()
        mcpServer!!.addSimpleTool(name, description, parameters, handler)
    }

    /** Create and add a simple file-based resource */
    fun addFileResource(
        uri: String,
        name: String,
        description: String,
        filePath: String,
        mimeType: String = "text/plain",
    ) {
        checkInitialized()
        mcpServer!!.addFileResource(uri, name, description, filePath, mimeType)
    }

    /** Create and add a simple text-based prompt */
    fun addSimplePrompt(
        name: String,
        description: String,
        arguments: List<io.modelcontextprotocol.kotlin.sdk.PromptArgument> = emptyList(),
        promptGenerator: suspend (Map<String, Any?>) -> String,
    ) {
        checkInitialized()
        mcpServer!!.addSimplePrompt(name, description, arguments, promptGenerator)
    }

    // Transport operations

    /** Get transport connection information */
    fun getTransportInfo(): Map<String, Any> {
        checkInitialized()
        return mcpServer!!.getTransportInfo()
    }

    /** Send a message to all connected clients via transports */
    suspend fun broadcastMessage(message: String): Result<Unit> {
        checkInitialized()
        return mcpServer!!.broadcastMessage(message)
    }

    // Capabilities

    /** Get server capabilities */
    fun getCapabilities(): ServerCapabilities {
        checkInitialized()
        return mcpServer!!.getComprehensiveServerInfo().capabilities
    }

    // Lifecycle management integration

    /** Initialize lifecycle management with an Android Application */
    fun initializeLifecycleManagement(
        application: Application,
        config: McpLifecycleManager.LifecycleConfig = McpLifecycleManager.LifecycleConfig(),
    ) {
        McpLifecycleManager.getInstance().initialize(application, config)
        Log.i(TAG, "Lifecycle management initialized")
    }

    /** Update lifecycle configuration */
    fun updateLifecycleConfig(config: McpLifecycleManager.LifecycleConfig) {
        McpLifecycleManager.getInstance().updateConfig(config)
    }

    /** Get current lifecycle state */
    fun getLifecycleState(): McpLifecycleManager.LifecycleState {
        return McpLifecycleManager.getInstance().getLifecycleState()
    }

    // Cleanup resources when the manager is no longer needed
    fun cleanup() {
        McpLifecycleManager.getInstance().cleanup()
        isInitialized = false
        mcpServer = null
        Log.d(TAG, "McpServerManager cleaned up")
    }

    /**
     * Reset the singleton instance for testing purposes only. This method should NEVER be called in
     * production code.
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
