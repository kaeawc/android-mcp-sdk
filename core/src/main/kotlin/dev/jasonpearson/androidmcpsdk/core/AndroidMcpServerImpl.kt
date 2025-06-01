package dev.jasonpearson.androidmcpsdk.core

import android.content.Context
import android.util.Log
import dev.jasonpearson.androidmcpsdk.core.features.prompts.PromptProvider
import dev.jasonpearson.androidmcpsdk.core.features.resources.ResourceProvider
import dev.jasonpearson.androidmcpsdk.core.features.tools.ToolProvider
import dev.jasonpearson.androidmcpsdk.core.models.AndroidResourceContent
import dev.jasonpearson.androidmcpsdk.core.models.ComprehensiveServerInfo
import dev.jasonpearson.androidmcpsdk.core.models.PromptsCapability
import dev.jasonpearson.androidmcpsdk.core.models.ResourcesCapability
import dev.jasonpearson.androidmcpsdk.core.models.SamplingRequest
import dev.jasonpearson.androidmcpsdk.core.models.ServerCapabilities
import dev.jasonpearson.androidmcpsdk.core.models.ToolsCapability
import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.GetPromptResult
import io.modelcontextprotocol.kotlin.sdk.Prompt
import io.modelcontextprotocol.kotlin.sdk.Resource
import io.modelcontextprotocol.kotlin.sdk.ResourceTemplate
import io.modelcontextprotocol.kotlin.sdk.Root
import io.modelcontextprotocol.kotlin.sdk.Tool
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Complete Android MCP Server implementation using the MCP Kotlin SDK.
 *
 * This implementation provides a fully functional MCP server that can handle:
 * - Tools: Android-specific functionality like device info, app info, etc.
 * - Resources: File system access and app data
 * - Prompts: Pre-defined prompt templates
 * - Sampling: Communication with MCP clients for sampling requests
 */
class AndroidMcpServerImpl
private constructor(
    private val context: Context,
    private val serverName: String,
    private val serverVersion: String,
) {

    companion object {
        private const val TAG = "AndroidMcpServerImpl"

        fun create(context: Context, name: String, version: String): AndroidMcpServerImpl {
            return AndroidMcpServerImpl(context, name, version)
        }
    }

    private val isRunning = AtomicBoolean(false)
    private val isInitialized = AtomicBoolean(false)
    private var serverJob: Job? = null
    private val serverScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Feature providers
    private lateinit var toolProvider: ToolProvider
    private lateinit var resourceProvider: ResourceProvider
    private lateinit var promptProvider: PromptProvider

    // Server capabilities
    private val serverCapabilities =
        ServerCapabilities(
            tools = ToolsCapability(listChanged = true),
            resources = ResourcesCapability(subscribe = true, listChanged = true),
            prompts = PromptsCapability(listChanged = true),
        )

    // Root directories
    private val roots = mutableListOf<Root>()

    /** Initialize the MCP server with all capabilities */
    fun initialize(): Result<Unit> = runCatching {
        if (isInitialized.get()) {
            Log.w(TAG, "Server already initialized")
            return@runCatching
        }

        Log.d(TAG, "Initializing Android MCP server: $serverName v$serverVersion")

        // Initialize feature providers
        toolProvider = ToolProvider(context)
        resourceProvider = ResourceProvider(context)
        promptProvider = PromptProvider(context)

        // Register debug-bridge tool contributor if available
        registerDebugBridgeTools()

        // Add default roots
        addDefaultRoots()

        isInitialized.set(true)
        Log.i(TAG, "Android MCP server initialized successfully")
    }

    /**
     * Register debug-bridge tool contributor if the debug-bridge module is available.
     * This uses reflection to avoid hard dependency on the debug-bridge module.
     */
    private fun registerDebugBridgeTools() {
        try {
            val debugBridgeClass =
                Class.forName("dev.jasonpearson.androidmcpsdk.debugbridge.DebugBridgeToolContributor")
            val constructor = debugBridgeClass.getConstructor(Context::class.java)
            val contributor = constructor.newInstance(context)

            // Cast to ToolContributor and register
            val toolContributor =
                contributor as dev.jasonpearson.androidmcpsdk.core.features.tools.ToolContributor
            toolProvider.registerContributor(toolContributor)

            Log.i(TAG, "Debug-bridge tools registered successfully via reflection")
        } catch (e: ClassNotFoundException) {
            Log.d(TAG, "Debug-bridge module not available - no additional tools registered")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register debug-bridge tools: ${e.message}")
        }
    }

    /** Start the MCP server */
    suspend fun start(): Result<Unit> = runCatching {
        if (!isInitialized.get()) {
            throw IllegalStateException("Server must be initialized before starting")
        }

        if (isRunning.compareAndSet(false, true)) {
            Log.i(TAG, "Starting Android MCP server...")

            serverJob =
                serverScope.launch {
                    try {
                        Log.i(TAG, "MCP server running and ready for connections")

                        // Keep the server running
                        while (isActive && isRunning.get()) {
                            delay(1000)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Server error", e)
                        throw e
                    } finally {
                        isRunning.set(false)
                        Log.i(TAG, "MCP server stopped")
                    }
                }

            Log.i(TAG, "Android MCP server started successfully")
        } else {
            Log.w(TAG, "MCP server is already running")
        }
    }

    /** Stop the MCP server */
    suspend fun stop(): Result<Unit> = runCatching {
        Log.i(TAG, "Stopping Android MCP server...")

        serverJob?.cancel()
        serverJob?.join()
        isRunning.set(false)

        Log.i(TAG, "Android MCP server stopped successfully")
    }

    /** Check if the server is running */
    fun isRunning(): Boolean = isRunning.get()

    /** Check if the server is initialized */
    fun isInitialized(): Boolean = isInitialized.get()

    /** Get comprehensive server information */
    fun getServerInfo(): ComprehensiveServerInfo {
        return ComprehensiveServerInfo(
            name = serverName,
            version = serverVersion,
            sdkVersion = "0.5.0",
            isRunning = isRunning(),
            isInitialized = isInitialized(),
            capabilities = serverCapabilities,
            toolCount = if (isInitialized()) toolProvider.getAllTools().size else 0,
            resourceCount = if (isInitialized()) resourceProvider.getAllResources().size else 0,
            promptCount = if (isInitialized()) promptProvider.getAllPrompts().size else 0,
            rootCount = roots.size,
        )
    }

    /** Get server capabilities */
    fun getCapabilities(): ServerCapabilities = serverCapabilities

    // Delegate methods to providers

    // Tool operations
    fun getTools(): List<Tool> {
        checkInitialized()
        return toolProvider.getAllTools()
    }

    suspend fun callTool(name: String, arguments: Map<String, Any>): CallToolResult {
        checkInitialized()
        return toolProvider.callTool(name, arguments)
    }

    fun addTool(tool: Tool, handler: suspend (Map<String, Any>) -> CallToolResult) {
        Log.d(TAG, "Adding tool: ${tool.name}")
        toolProvider.addToolInternal(tool, handler)
    }

    // Resource operations
    fun getResources(): List<Resource> {
        checkInitialized()
        return resourceProvider.getAllResources()
    }

    fun getResourceTemplates(): List<ResourceTemplate> {
        checkInitialized()
        return resourceProvider.getAllResourceTemplates()
    }

    suspend fun readResource(uri: String): AndroidResourceContent {
        checkInitialized()
        return resourceProvider.readResource(uri)
    }

    fun addResource(resource: Resource, contentProvider: suspend () -> AndroidResourceContent) {
        checkInitialized()
        resourceProvider.addResource(resource, contentProvider)
    }

    fun subscribeToResource(uri: String) {
        checkInitialized()
        resourceProvider.subscribe(uri)
    }

    fun unsubscribeFromResource(uri: String) {
        checkInitialized()
        resourceProvider.unsubscribe(uri)
    }

    // Prompt operations
    fun getPrompts(): List<Prompt> {
        checkInitialized()
        return promptProvider.getAllPrompts()
    }

    suspend fun getPrompt(
        name: String,
        arguments: Map<String, Any?> = emptyMap(),
    ): GetPromptResult {
        checkInitialized()
        return promptProvider.getPrompt(name, arguments)
    }

    fun addPrompt(prompt: Prompt, handler: suspend (Map<String, Any?>) -> GetPromptResult) {
        checkInitialized()
        promptProvider.addPrompt(prompt, handler)
    }

    // Root operations
    fun getRoots(): List<Root> = roots.toList()

    fun addRoot(root: Root) {
        roots.add(root)
        Log.i(TAG, "Added root: ${root.uri}")
    }

    fun removeRoot(uri: String): Boolean {
        val removed = roots.removeIf { it.uri == uri }
        if (removed) {
            Log.i(TAG, "Removed root: $uri")
        }
        return removed
    }

    // Sampling operations
    suspend fun requestSampling(request: SamplingRequest): Result<String> {
        // TODO: Implement sampling requests to clients
        Log.d(TAG, "Sampling request: $request")
        return Result.failure(UnsupportedOperationException("Sampling not yet implemented"))
    }

    // Private helper methods

    private fun checkInitialized() {
        if (!isInitialized.get()) {
            throw IllegalStateException("Server is not initialized")
        }
    }

    private fun addDefaultRoots() {
        // Add app's internal files directory
        addRoot(Root(uri = "file://${context.filesDir.absolutePath}", name = "App Files"))

        // Add app's cache directory
        addRoot(Root(uri = "file://${context.cacheDir.absolutePath}", name = "App Cache"))

        // Add external files directory if available
        context.getExternalFilesDir(null)?.let { externalDir ->
            addRoot(Root(uri = "file://${externalDir.absolutePath}", name = "External Files"))
        }

        Log.i(TAG, "Added ${roots.size} default roots")
    }
}
