package dev.jasonpearson.androidmcpsdk.core

import android.content.Context
import android.util.Log
import dev.jasonpearson.androidmcpsdk.core.features.prompts.PromptProvider
import dev.jasonpearson.androidmcpsdk.core.features.resources.ResourceProvider
import dev.jasonpearson.androidmcpsdk.core.features.tools.ToolProvider
import dev.jasonpearson.androidmcpsdk.core.models.AndroidResourceContent
import dev.jasonpearson.androidmcpsdk.core.models.AndroidTool
import dev.jasonpearson.androidmcpsdk.core.models.ComprehensiveServerInfo
import dev.jasonpearson.androidmcpsdk.core.models.ServerInfo
import dev.jasonpearson.androidmcpsdk.core.models.ToolExecutionResult
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.routing.*
import io.ktor.server.sse.*
import io.modelcontextprotocol.kotlin.sdk.*
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.mcp
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.*

/**
 * Android-specific wrapper for MCP Server functionality. Provides easy integration of MCP servers
 * in Android applications with MCP Kotlin SDK integration and transport support.
 *
 * This library integrates the MCP Kotlin SDK (io.modelcontextprotocol:kotlin-sdk:0.5.0) to enable
 * Android apps to host MCP servers and expose them to MCP clients via the official SDK transports.
 *
 * KEY IMPLEMENTATION NOTES:
 * - Uses the official MCP Kotlin SDK for protocol handling and transport
 * - JSON-RPC message parsing and protocol handling is managed by the SDK automatically
 * - Server creation uses proper SDK constructors with ServerCapabilities, Implementation, etc.
 * - All MCP types (Tool, Resource, Prompt, etc.) come from the official SDK
 * - Transport integration uses the SDK's built-in Ktor SSE extension
 * - Removed custom transport classes in favor of SDK's transport implementations
 */
class McpAndroidServer
private constructor(
    private val context: Context,
    private val name: String,
    private val version: String,
) {

    companion object {
        private const val TAG = "McpAndroidServer"

        /** Get the MCP SDK version. */
        fun getMcpSdkVersion(): String {
            return "0.5.0"
        }

        /** Create a basic MCP server instance. */
        fun createServer(context: Context, name: String, version: String): McpAndroidServer {
            return McpAndroidServer(context, name, version)
        }
    }

    private val isRunning = AtomicBoolean(false)
    private val isInitialized = AtomicBoolean(false)
    private var serverJob: Job? = null
    private val serverScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // MCP SDK server instance - now using proper types
    private var mcpServer: Server? = null

    // Ktor server for SSE transport
    private var ktorServer: EmbeddedServer<*, *>? = null
    private val ssePort = 8080
    private val sseHost = "0.0.0.0"

    // Feature providers
    private lateinit var toolProvider: ToolProvider
    private lateinit var resourceProvider: ResourceProvider
    private lateinit var promptProvider: PromptProvider

    // Server capabilities (using SDK model type for SDK interaction)
    private val sdkServerCapabilities =
        ServerCapabilities(
            tools = ServerCapabilities.Tools(listChanged = true),
            resources = ServerCapabilities.Resources(subscribe = true, listChanged = true),
            prompts = ServerCapabilities.Prompts(listChanged = true),
        )

    // Server capabilities (using local model type for ComprehensiveServerInfo)
    private val serverCapabilitiesModel =
        dev.jasonpearson.androidmcpsdk.core.models.ServerCapabilities(
            tools = dev.jasonpearson.androidmcpsdk.core.models.ToolsCapability(listChanged = true),
            resources =
                dev.jasonpearson.androidmcpsdk.core.models.ResourcesCapability(
                    subscribe = true,
                    listChanged = true,
                ),
            prompts =
                dev.jasonpearson.androidmcpsdk.core.models.PromptsCapability(listChanged = true),
        )

    // Basic tool definitions for Android-specific functionality
    private val availableTools = mutableListOf<AndroidTool>()

    /** Initialize the MCP server with full capabilities */
    fun initialize(): Result<Unit> = runCatching {
        if (isInitialized.get()) {
            Log.w(TAG, "Server already initialized")
            return@runCatching
        }

        Log.d(TAG, "Initializing MCP server: $name v$version")

        // Initialize feature providers
        toolProvider = ToolProvider(context)
        resourceProvider = ResourceProvider(context)
        promptProvider = PromptProvider(context)

        // Create MCP server with proper SDK integration
        try {
            mcpServer = createMcpServerWithSDK()
            Log.i(TAG, "MCP server created with SDK integration")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to create MCP server with SDK", e)
            mcpServer = null
        }

        // Add default Android tools
        addDefaultTools()

        isInitialized.set(true)
        Log.i(TAG, "MCP server initialized successfully with ${availableTools.size} tools")
    }

    /** Start the MCP server with SDK transport support. */
    suspend fun start(): Result<Unit> = runCatching {
        if (!isInitialized.get()) {
            throw IllegalStateException("Server must be initialized before starting")
        }

        if (isRunning.compareAndSet(false, true)) {
            Log.i(TAG, "Starting MCP server with official SDK transport...")

            // Start Ktor server with MCP SDK integration
            startKtorServerWithMcp()

            serverJob =
                serverScope.launch {
                    try {
                        mcpServer?.let { server ->
                            Log.i(
                                TAG,
                                "MCP server running with official SDK transport on http://$sseHost:$ssePort/mcp",
                            )

                            while (isActive && isRunning.get()) {
                                delay(1000)
                            }
                        }
                            ?: run {
                                Log.i(TAG, "Running in fallback mode")
                                while (isActive && isRunning.get()) {
                                    delay(1000)
                                }
                            }
                    } catch (e: Exception) {
                        Log.e(TAG, "Server error", e)
                        throw e
                    } finally {
                        isRunning.set(false)
                        Log.i(TAG, "MCP server stopped")
                    }
                }

            Log.i(TAG, "MCP server started successfully with official SDK transport")
        } else {
            Log.w(TAG, "MCP server is already running")
        }
    }

    /** Stop the MCP server and transport */
    suspend fun stop(): Result<Unit> = runCatching {
        Log.i(TAG, "Stopping MCP server and transport...")

        // Stop Ktor server
        ktorServer?.stop(1000, 5000)
        ktorServer = null

        // Stop server
        serverJob?.cancel()
        serverJob?.join()
        isRunning.set(false)

        Log.i(TAG, "MCP server and transport stopped successfully")
    }

    /** Check if the server is currently running */
    fun isRunning(): Boolean = isRunning.get()

    /** Check if the server is initialized */
    fun isInitialized(): Boolean = isInitialized.get()

    /** Check if SDK integration is available */
    fun hasSDKIntegration(): Boolean = mcpServer != null

    /** Get server information */
    fun getServerInfo(): ServerInfo {
        return ServerInfo(
            name = name,
            version = version,
            sdkVersion = getMcpSdkVersion(),
            isRunning = isRunning(),
            toolCount = availableTools.size,
        )
    }

    /** Get comprehensive server information including transport details */
    fun getComprehensiveServerInfo(): ComprehensiveServerInfo {
        return ComprehensiveServerInfo(
            name = name,
            version = version,
            sdkVersion = getMcpSdkVersion(),
            isRunning = isRunning(),
            isInitialized = isInitialized(),
            capabilities = serverCapabilitiesModel, // Use the local model here
            toolCount = availableTools.size,
            resourceCount = if (isInitialized()) resourceProvider.getAllResources().size else 0,
            promptCount = if (isInitialized()) promptProvider.getAllPrompts().size else 0,
            rootCount = 0, // Update if roots are implemented from SDK
            transportInfo = getTransportInfo(),
        )
    }

    /** Get transport connection information */
    fun getTransportInfo(): Map<String, Any> {
        return mapOf(
            "sdk_integration" to hasSDKIntegration(),
            "transport_type" to "official_sdk_sse",
            "sse_endpoint" to "http://$sseHost:$ssePort/mcp",
            "is_running" to isRunning(),
            "server_info" to mapOf("host" to sseHost, "port" to ssePort, "protocol" to "http_sse"),
        )
    }

    /** Add a custom tool to the server */
    fun addTool(tool: AndroidTool) {
        availableTools.add(tool)
        Log.d(TAG, "Added tool: ${tool.name}")
    }

    /** Get all available tools */
    fun getAvailableTools(): List<AndroidTool> = availableTools.toList()

    /** Execute a tool by name with the provided arguments */
    suspend fun executeTool(toolName: String, arguments: Map<String, Any>): ToolExecutionResult {
        Log.d(TAG, "Executing tool: $toolName with arguments: $arguments")

        val tool = availableTools.find { it.name == toolName }
        if (tool == null) {
            return ToolExecutionResult(
                success = false,
                result = "Tool not found: $toolName",
                error = "Tool '$toolName' is not available",
            )
        }

        return try {
            val result = tool.execute(context, arguments)
            ToolExecutionResult(success = true, result = result, error = null)
        } catch (e: Exception) {
            Log.e(TAG, "Error executing tool $toolName", e)
            ToolExecutionResult(
                success = false,
                result = null,
                error = "Tool execution failed: ${e.message}",
            )
        }
    }

    /** Send a message to all connected clients via SDK transport */
    suspend fun broadcastMessage(message: String): Result<Unit> {
        return if (isRunning() && mcpServer != null) {
            Log.d(TAG, "Message broadcasting is handled by the MCP SDK transport layer")
            Result.success(Unit)
        } else {
            Result.failure(IllegalStateException("Server not running or SDK not available"))
        }
    }

    /** Get all MCP tools from the tool provider */
    fun getMcpTools(): List<Tool> {
        return if (isInitialized()) toolProvider.getAllTools() else emptyList()
    }

    /** Call an MCP tool */
    suspend fun callMcpTool(name: String, arguments: Map<String, Any>): CallToolResult {
        return if (isInitialized()) {
            toolProvider.callTool(name, arguments)
        } else {
            CallToolResult(
                content = listOf(TextContent(text = "Server not initialized")),
                isError = true,
            )
        }
    }

    /** Get all MCP resources */
    fun getMcpResources(): List<Resource> {
        return if (isInitialized()) resourceProvider.getAllResources() else emptyList()
    }

    /** Get all MCP prompts */
    fun getMcpPrompts(): List<Prompt> {
        return if (isInitialized()) promptProvider.getAllPrompts() else emptyList()
    }

    /** Get a specific MCP prompt with arguments */
    suspend fun getMcpPrompt(
        name: String,
        arguments: Map<String, Any?> = emptyMap(),
    ): GetPromptResult {
        return if (isInitialized()) {
            promptProvider.getPrompt(name, arguments)
        } else {
            GetPromptResult(
                description = "Server not initialized",
                messages =
                    listOf(
                        PromptMessage(
                            role = Role.user,
                            content = TextContent(text = "Server not initialized"),
                        )
                    ),
            )
        }
    }

    // Helper methods for adding tools, resources, and prompts

    /** Add a custom MCP tool with its handler */
    fun addMcpTool(tool: Tool, handler: suspend (Map<String, Any>) -> CallToolResult) {
        if (isInitialized()) {
            toolProvider.addToolInternal(tool, handler)
            Log.i(TAG, "Added custom MCP tool: ${tool.name}")
        } else {
            Log.w(TAG, "Cannot add tool, server not initialized")
        }
    }

    /** Remove a custom MCP tool */
    fun removeMcpTool(name: String): Boolean {
        return if (isInitialized()) {
            val result = toolProvider.removeTool(name)
            if (result) {
                Log.i(TAG, "Removed custom MCP tool: $name")
            }
            result
        } else {
            Log.w(TAG, "Cannot remove tool, server not initialized")
            false
        }
    }

    /** Add a custom MCP resource with its content provider */
    fun addMcpResource(resource: Resource, contentProvider: suspend () -> AndroidResourceContent) {
        if (isInitialized()) {
            resourceProvider.addResource(resource, contentProvider)
            Log.i(TAG, "Added custom MCP resource: ${resource.uri}")
        } else {
            Log.w(TAG, "Cannot add resource, server not initialized")
        }
    }

    /** Add a custom MCP resource template */
    fun addMcpResourceTemplate(template: ResourceTemplate) {
        if (isInitialized()) {
            resourceProvider.addResourceTemplate(template)
            Log.i(TAG, "Added custom MCP resource template: ${template.uriTemplate}")
        } else {
            Log.w(TAG, "Cannot add resource template, server not initialized")
        }
    }

    /** Read content from an MCP resource */
    suspend fun readMcpResource(uri: String): AndroidResourceContent {
        return if (isInitialized()) {
            resourceProvider.readResource(uri)
        } else {
            AndroidResourceContent(uri = uri, text = "Server not initialized")
        }
    }

    /** Subscribe to an MCP resource for updates */
    fun subscribeMcpResource(uri: String) {
        if (isInitialized()) {
            resourceProvider.subscribe(uri)
            Log.i(TAG, "Subscribed to MCP resource: $uri")
        } else {
            Log.w(TAG, "Cannot subscribe to resource, server not initialized")
        }
    }

    /** Unsubscribe from an MCP resource */
    fun unsubscribeMcpResource(uri: String) {
        if (isInitialized()) {
            resourceProvider.unsubscribe(uri)
            Log.i(TAG, "Unsubscribed from MCP resource: $uri")
        } else {
            Log.w(TAG, "Cannot unsubscribe from resource, server not initialized")
        }
    }

    /** Add a custom MCP prompt with its handler */
    fun addMcpPrompt(prompt: Prompt, handler: suspend (Map<String, Any?>) -> GetPromptResult) {
        if (isInitialized()) {
            promptProvider.addPrompt(prompt, handler)
            Log.i(TAG, "Added custom MCP prompt: ${prompt.name}")
        } else {
            Log.w(TAG, "Cannot add prompt, server not initialized")
        }
    }

    /** Remove a custom MCP prompt */
    fun removeMcpPrompt(name: String): Boolean {
        return if (isInitialized()) {
            val result = promptProvider.removePrompt(name)
            if (result) {
                Log.i(TAG, "Removed custom MCP prompt: $name")
            }
            result
        } else {
            Log.w(TAG, "Cannot remove prompt, server not initialized")
            false
        }
    }

    // Convenience methods for creating common tool types

    /** Create and add a simple text-based tool */
    fun addSimpleTool(
        name: String,
        description: String,
        parameters: Map<String, String> = emptyMap(),
        handler: suspend (Map<String, Any>) -> String,
    ) {
        val tool =
            AndroidTool(name = name, description = description, parameters = parameters) {
                context,
                arguments ->
                handler(arguments)
            }
        addTool(tool)
    }

    /** Create and add a simple file-based resource */
    fun addFileResource(
        uri: String,
        name: String,
        description: String,
        filePath: String,
        mimeType: String = "text/plain",
    ) {
        val resource =
            Resource(uri = uri, name = name, description = description, mimeType = mimeType)
        addMcpResource(resource) {
            try {
                val file = java.io.File(filePath)
                if (file.exists() && file.isFile) {
                    AndroidResourceContent(uri = uri, text = file.readText(), mimeType = mimeType)
                } else {
                    AndroidResourceContent(uri = uri, text = "File not found: $filePath")
                }
            } catch (e: Exception) {
                AndroidResourceContent(uri = uri, text = "Error reading file: ${e.message}")
            }
        }
    }

    /** Create and add a simple text-based prompt */
    fun addSimplePrompt(
        name: String,
        description: String,
        arguments: List<PromptArgument> = emptyList(),
        promptGenerator: suspend (Map<String, Any?>) -> String,
    ) {
        val prompt = Prompt(name = name, description = description, arguments = arguments)
        addMcpPrompt(prompt) { args ->
            val promptText = promptGenerator(args)
            GetPromptResult(
                description = description,
                messages =
                    listOf(
                        PromptMessage(role = Role.user, content = TextContent(text = promptText))
                    ),
            )
        }
    }

    // Private helper methods

    private fun startKtorServerWithMcp() {
        ktorServer =
            embeddedServer(Netty, port = ssePort, host = sseHost) {
                install(SSE)

                routing { route("mcp") { mcp { mcpServer ?: createMcpServerWithSDK() } } }
            }

        ktorServer?.start(wait = false)
        Log.i(TAG, "Ktor server started with MCP SDK integration on http://$sseHost:$ssePort/mcp")
    }

    private fun createMcpServerWithSDK(): Server {
        // Create the MCP server using the official SDK
        val implementation = Implementation(name = name, version = version)

        // Use the sdkServerCapabilities defined at class level for SDK Server instance
        val serverOptions = ServerOptions(capabilities = sdkServerCapabilities)

        return Server(serverInfo = implementation, options = serverOptions).apply {
            // Register all tools from the tool provider
            toolProvider.getAllTools().forEach { tool ->
                addTool(
                    name = tool.name,
                    description = tool.description ?: "",
                    inputSchema = tool.inputSchema,
                ) { request ->
                    toolProvider.callTool(request.name, request.arguments ?: emptyMap())
                }
            }

            // Register all resources from the resource provider
            resourceProvider.getAllResources().forEach { resource ->
                addResource(
                    uri = resource.uri,
                    name = resource.name ?: "",
                    description = resource.description ?: "",
                    mimeType = resource.mimeType ?: "text/plain",
                ) { request ->
                    val content = resourceProvider.readResource(request.uri)
                    ReadResourceResult(
                        contents =
                            listOf(
                                TextResourceContents(
                                    text = content.text ?: "",
                                    uri = content.uri,
                                    mimeType = content.mimeType ?: "text/plain",
                                )
                            )
                    )
                }
            }

            // Register all prompts from the prompt provider
            promptProvider.getAllPrompts().forEach { prompt ->
                addPrompt(
                    name = prompt.name,
                    description = prompt.description ?: "",
                    arguments = prompt.arguments ?: emptyList(),
                ) { request ->
                    promptProvider.getPrompt(request.name, request.arguments ?: emptyMap())
                }
            }
        }
    }

    // This creates the local model type, used for ComprehensiveServerInfo DTO.
    private fun createAndroidServerCapabilities():
        dev.jasonpearson.androidmcpsdk.core.models.ServerCapabilities {
        return serverCapabilitiesModel // Simply return the pre-defined local model instance
    }

    /** Add default Android-specific tools */
    private fun addDefaultTools() {
        // Device information tool
        addTool(
            AndroidTool(
                name = "device_info",
                description = "Get information about the Android device",
                parameters = emptyMap(),
            ) { context, _ ->
                buildString {
                    appendLine("Device Information:")
                    appendLine("- Model: ${android.os.Build.MODEL}")
                    appendLine("- Manufacturer: ${android.os.Build.MANUFACTURER}")
                    appendLine("- Brand: ${android.os.Build.BRAND}")
                    appendLine("- Android Version: ${android.os.Build.VERSION.RELEASE}")
                    appendLine("- API Level: ${android.os.Build.VERSION.SDK_INT}")
                    appendLine("- Package Name: ${context.packageName}")
                }
            }
        )

        // App information tool
        addTool(
            AndroidTool(
                name = "app_info",
                description = "Get information about the current application",
                parameters = emptyMap(),
            ) { context, _ ->
                try {
                    val packageManager = context.packageManager
                    val packageInfo = packageManager.getPackageInfo(context.packageName, 0)
                    val appInfo = packageManager.getApplicationInfo(context.packageName, 0)
                    val appName = packageManager.getApplicationLabel(appInfo).toString()

                    buildString {
                        appendLine("Application Information:")
                        appendLine("- App Name: $appName")
                        appendLine("- Package Name: ${context.packageName}")
                        appendLine("- Version Name: ${packageInfo.versionName}")
                        appendLine("- Version Code: ${packageInfo.longVersionCode}")
                        appendLine("- Target SDK: ${appInfo.targetSdkVersion}")
                    }
                } catch (e: Exception) {
                    "Error getting app info: ${e.message}"
                }
            }
        )

        // System time tool
        addTool(
            AndroidTool(
                name = "system_time",
                description = "Get current system time",
                parameters = emptyMap(),
            ) { _, _ ->
                buildString {
                    appendLine("System Time:")
                    appendLine("- Current Time: ${java.util.Date()}")
                    appendLine("- Timestamp: ${System.currentTimeMillis()}")
                    appendLine("- Timezone: ${java.util.TimeZone.getDefault().id}")
                    appendLine("- Uptime: ${android.os.SystemClock.elapsedRealtime()} ms")
                }
            }
        )
    }
}
