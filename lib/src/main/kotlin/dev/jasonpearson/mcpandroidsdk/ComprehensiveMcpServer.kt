package dev.jasonpearson.mcpandroidsdk

import android.content.Context
import android.util.Log
import dev.jasonpearson.mcpandroidsdk.models.*
import io.modelcontextprotocol.kotlin.sdk.*
import kotlinx.coroutines.*

/**
 * Comprehensive MCP Server implementation that provides full MCP specification support including
 * tools, resources, prompts, sampling, and roots.
 *
 * This class acts as a facade that delegates to AndroidMcpServerImpl for the actual implementation.
 */
class ComprehensiveMcpServer
private constructor(
    private val context: Context,
    private val name: String,
    private val version: String,
) {

    companion object {
        private const val TAG = "ComprehensiveMcpServer"

        fun createServer(context: Context, name: String, version: String): ComprehensiveMcpServer {
            return ComprehensiveMcpServer(context, name, version)
        }

        fun getMcpSdkVersion(): String = "0.5.0"
    }

    // Delegate to the actual implementation
    private val serverImpl = AndroidMcpServerImpl.create(context, name, version)

    /** Initialize the MCP server with all capabilities */
    fun initialize(): Result<Unit> {
        Log.d(TAG, "Initializing comprehensive MCP server: $name v$version")
        return serverImpl.initialize()
    }

    /** Start the MCP server */
    suspend fun start(): Result<Unit> {
        Log.i(TAG, "Starting comprehensive MCP server...")
        return serverImpl.start()
    }

    /** Stop the MCP server */
    suspend fun stop(): Result<Unit> {
        Log.i(TAG, "Stopping comprehensive MCP server...")
        return serverImpl.stop()
    }

    /** Check if the server is currently running */
    fun isRunning(): Boolean = serverImpl.isRunning()

    /** Check if the server is initialized */
    fun isInitialized(): Boolean = serverImpl.isInitialized()

    /** Get comprehensive server information */
    fun getServerInfo(): ComprehensiveServerInfo = serverImpl.getServerInfo()

    /** Get server capabilities */
    fun getCapabilities(): dev.jasonpearson.mcpandroidsdk.models.ServerCapabilities =
        serverImpl.getCapabilities()

    // Tool operations

    /** Get all available tools */
    fun getTools(): List<Tool> = serverImpl.getTools()

    /** Call a tool by name */
    suspend fun callTool(name: String, arguments: Map<String, Any>): CallToolResult =
        serverImpl.callTool(name, arguments)

    /** Add a custom tool */
    fun addTool(tool: Tool, handler: suspend (Map<String, Any>) -> CallToolResult) =
        serverImpl.addTool(tool, handler)

    // Resource operations

    /** Get all available resources */
    fun getResources(): List<Resource> = serverImpl.getResources()

    /** Get all resource templates */
    fun getResourceTemplates(): List<ResourceTemplate> = serverImpl.getResourceTemplates()

    /** Read a resource by URI */
    suspend fun readResource(uri: String): AndroidResourceContent = serverImpl.readResource(uri)

    /** Add a custom resource */
    fun addResource(resource: Resource, contentProvider: suspend () -> AndroidResourceContent) =
        serverImpl.addResource(resource, contentProvider)

    /** Subscribe to resource updates */
    fun subscribeToResource(uri: String) = serverImpl.subscribeToResource(uri)

    /** Unsubscribe from resource updates */
    fun unsubscribeFromResource(uri: String) = serverImpl.unsubscribeFromResource(uri)

    // Prompt operations

    /** Get all available prompts */
    fun getPrompts(): List<Prompt> = serverImpl.getPrompts()

    /** Get a prompt by name with arguments */
    suspend fun getPrompt(
        name: String,
        arguments: Map<String, Any?> = emptyMap(),
    ): GetPromptResult = serverImpl.getPrompt(name, arguments)

    /** Add a custom prompt */
    fun addPrompt(prompt: Prompt, handler: suspend (Map<String, Any?>) -> GetPromptResult) =
        serverImpl.addPrompt(prompt, handler)

    // Root operations

    /** Get all roots */
    fun getRoots(): List<Root> = serverImpl.getRoots()

    /** Add a root directory */
    fun addRoot(root: Root) = serverImpl.addRoot(root)

    /** Remove a root directory */
    fun removeRoot(uri: String): Boolean = serverImpl.removeRoot(uri)

    // Sampling operations (for future implementation)

    /** Request sampling from client (placeholder for future implementation) */
    suspend fun requestSampling(request: SamplingRequest): Result<String> =
        serverImpl.requestSampling(request)
}
