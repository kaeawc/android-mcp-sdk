package dev.jasonpearson.mcpandroidsdk

import android.content.Context
import android.util.Log
import dev.jasonpearson.mcpandroidsdk.models.*
import io.modelcontextprotocol.kotlin.sdk.*
import kotlinx.coroutines.*

/**
 * Thread-safe singleton manager for MCP Server functionality in Android applications.
 *
 * This manager provides a centralized way to initialize, configure, and control the MCP server
 * lifecycle. It integrates all MCP capabilities including tools, resources, prompts, sampling, and
 * roots.
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

    @Volatile private var comprehensiveServer: ComprehensiveMcpServer? = null

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

        comprehensiveServer =
            ComprehensiveMcpServer.createServer(
                context = context,
                name = serverName,
                version = serverVersion,
            )

        comprehensiveServer?.initialize()?.getOrThrow()

        isInitialized = true
        Log.i(TAG, "McpServerManager initialized successfully")
    }

    /** Check if the manager is initialized */
    fun isInitialized(): Boolean = isInitialized

    /** Get the MCP SDK version */
    fun getMcpSdkVersion(): String = ComprehensiveMcpServer.getMcpSdkVersion()

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
        return comprehensiveServer!!.start()
    }

    /** Stop the MCP server */
    suspend fun stopServer(): Result<Unit> {
        checkInitialized()
        return comprehensiveServer!!.stop()
    }

    /** Check if the server is currently running */
    fun isServerRunning(): Boolean {
        return try {
            comprehensiveServer?.isRunning() ?: false
        } catch (e: Exception) {
            Log.e(TAG, "Error checking server status", e)
            false
        }
    }

    /** Get comprehensive server information */
    fun getServerInfo(): ComprehensiveServerInfo? {
        return try {
            comprehensiveServer?.getServerInfo()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting server info", e)
            null
        }
    }

    /** Get the underlying comprehensive MCP server instance */
    fun getMcpServer(): ComprehensiveMcpServer {
        checkInitialized()
        return comprehensiveServer!!
    }

    // Tool operations

    /** Get all available tools */
    fun getTools(): List<Tool> {
        checkInitialized()
        return comprehensiveServer!!.getTools()
    }

    /** Call a tool by name */
    suspend fun callTool(name: String, arguments: Map<String, Any>): ToolCallResult {
        checkInitialized()
        return comprehensiveServer!!.callTool(name, arguments)
    }

    /** Add a custom tool */
    fun addTool(tool: Tool, handler: suspend (Map<String, Any>) -> ToolCallResult) {
        checkInitialized()
        comprehensiveServer!!.addTool(tool, handler)
    }

    // Resource operations

    /** Get all available resources */
    fun getResources(): List<Resource> {
        checkInitialized()
        return comprehensiveServer!!.getResources()
    }

    /** Get all resource templates */
    fun getResourceTemplates(): List<ResourceTemplate> {
        checkInitialized()
        return comprehensiveServer!!.getResourceTemplates()
    }

    /** Read a resource by URI */
    suspend fun readResource(uri: String): ResourceContent {
        checkInitialized()
        return comprehensiveServer!!.readResource(uri)
    }

    /** Add a custom resource */
    fun addResource(resource: Resource, contentProvider: suspend () -> ResourceContent) {
        checkInitialized()
        comprehensiveServer!!.addResource(resource, contentProvider)
    }

    /** Subscribe to resource updates */
    fun subscribeToResource(uri: String) {
        checkInitialized()
        comprehensiveServer!!.subscribeToResource(uri)
    }

    /** Unsubscribe from resource updates */
    fun unsubscribeFromResource(uri: String) {
        checkInitialized()
        comprehensiveServer!!.unsubscribeFromResource(uri)
    }

    // Prompt operations

    /** Get all available prompts */
    fun getPrompts(): List<Prompt> {
        checkInitialized()
        return comprehensiveServer!!.getPrompts()
    }

    /** Get a prompt by name with arguments */
    suspend fun getPrompt(
        name: String,
        arguments: Map<String, Any?> = emptyMap(),
    ): dev.jasonpearson.mcpandroidsdk.models.GetPromptResult {
        checkInitialized()
        return comprehensiveServer!!.getPrompt(name, arguments)
    }

    /** Add a custom prompt */
    fun addPrompt(
        prompt: Prompt,
        handler:
            suspend (Map<String, Any?>) -> dev.jasonpearson.mcpandroidsdk.models.GetPromptResult,
    ) {
        checkInitialized()
        comprehensiveServer!!.addPrompt(prompt, handler)
    }

    // Root operations

    /** Get all roots */
    fun getRoots(): List<dev.jasonpearson.mcpandroidsdk.models.Root> {
        checkInitialized()
        return comprehensiveServer!!.getRoots()
    }

    /** Add a root directory */
    fun addRoot(root: dev.jasonpearson.mcpandroidsdk.models.Root) {
        checkInitialized()
        comprehensiveServer!!.addRoot(root)
    }

    /** Remove a root directory */
    fun removeRoot(uri: String): Boolean {
        checkInitialized()
        return comprehensiveServer!!.removeRoot(uri)
    }

    // Sampling operations

    /** Request sampling from client (placeholder for future implementation) */
    suspend fun requestSampling(request: SamplingRequest): Result<String> {
        checkInitialized()
        return comprehensiveServer!!.requestSampling(request)
    }

    // Capabilities

    /** Get server capabilities */
    fun getCapabilities(): dev.jasonpearson.mcpandroidsdk.models.ServerCapabilities {
        checkInitialized()
        return comprehensiveServer!!.getCapabilities()
    }

    // Cleanup resources when the manager is no longer needed
    fun cleanup() {
        isInitialized = false
        comprehensiveServer = null
        Log.d(TAG, "McpServerManager cleaned up")
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
