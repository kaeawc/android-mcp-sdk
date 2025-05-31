package dev.jasonpearson.mcpandroidsdk

import android.content.Context
import android.util.Log
import dev.jasonpearson.mcpandroidsdk.features.prompts.PromptProvider
import dev.jasonpearson.mcpandroidsdk.features.resources.ResourceProvider
import dev.jasonpearson.mcpandroidsdk.features.tools.ToolProvider
import dev.jasonpearson.mcpandroidsdk.models.*
import dev.jasonpearson.mcpandroidsdk.transport.TransportManager
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.*

/**
 * Android-specific wrapper for MCP Server functionality. Provides easy integration of MCP servers
 * in Android applications with MCP Kotlin SDK integration and transport support.
 *
 * This library integrates the MCP Kotlin SDK (io.modelcontextprotocol:kotlin-sdk:0.5.0) to enable
 * Android apps to host MCP servers and expose them to MCP clients via WebSocket and HTTP/SSE
 * transports.
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

    // MCP SDK server instance (using Any to avoid import conflicts)
    private var mcpServer: Any? = null

    // Transport layer
    private val transportManager = TransportManager()
    private var messageHandlerJob: Job? = null

    // Feature providers
    private lateinit var toolProvider: ToolProvider
    private lateinit var resourceProvider: ResourceProvider
    private lateinit var promptProvider: PromptProvider

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

        // Setup default transports (WebSocket on 8080, HTTP/SSE on 8081)
        transportManager.setupDefaultTransports()

        // Try to create MCP server with SDK
        try {
            mcpServer = createMcpServerWithSDK()
            Log.i(TAG, "MCP server created with SDK integration")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to create MCP server with SDK, using fallback", e)
            mcpServer = null
        }

        // Add default Android tools
        addDefaultTools()

        isInitialized.set(true)
        Log.i(
            TAG,
            "MCP server initialized successfully with ${availableTools.size} tools and transports",
        )
    }

    /** Start the MCP server with transport support. */
    suspend fun start(): Result<Unit> = runCatching {
        if (!isInitialized.get()) {
            throw IllegalStateException("Server must be initialized before starting")
        }

        if (isRunning.compareAndSet(false, true)) {
            Log.i(TAG, "Starting MCP server with transports...")

            // Start transport layer
            transportManager.startAll().onFailure { exception ->
                Log.e(TAG, "Failed to start transports", exception)
                isRunning.set(false)
                throw exception
            }

            // Start message handling
            startMessageHandling()

            serverJob =
                serverScope.launch {
                    try {
                        mcpServer?.let {
                            Log.i(
                                TAG,
                                "Starting MCP server with SDK integration and transport support",
                            )
                            while (isActive && isRunning.get()) {
                                delay(1000)
                            }
                        }
                            ?: run {
                                Log.i(TAG, "Running in fallback mode with transport support")
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

            Log.i(TAG, "MCP server started successfully with transport support")
        } else {
            Log.w(TAG, "MCP server is already running")
        }
    }

    /** Stop the MCP server and all transports */
    suspend fun stop(): Result<Unit> = runCatching {
        Log.i(TAG, "Stopping MCP server and transports...")

        // Stop message handling
        messageHandlerJob?.cancel()
        messageHandlerJob?.join()

        // Stop transport layer
        transportManager.stopAll()

        // Stop server
        serverJob?.cancel()
        serverJob?.join()
        isRunning.set(false)

        Log.i(TAG, "MCP server and transports stopped successfully")
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
            capabilities = createAndroidServerCapabilities(),
            toolCount = availableTools.size,
            resourceCount = if (isInitialized()) resourceProvider.getAllResources().size else 0,
            promptCount = if (isInitialized()) promptProvider.getAllPrompts().size else 0,
            rootCount = 0,
            transportInfo = getTransportInfo(),
        )
    }

    /** Get transport connection information */
    fun getTransportInfo(): Map<String, Any> {
        return mapOf(
            "sdk_integration" to hasSDKIntegration(),
            "transport_manager_running" to transportManager.isRunning,
            "available_transports" to listOf("websocket", "http_sse"),
            "transport_details" to transportManager.getConnectionInfo(),
            "transport_statuses" to transportManager.getTransportStatuses(),
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

    /** Send a message to all connected clients via transports */
    suspend fun broadcastMessage(message: String): Result<Unit> {
        return if (isRunning() && transportManager.isRunning) {
            transportManager.broadcast(message)
        } else {
            Result.failure(IllegalStateException("Server or transports not running"))
        }
    }

    /** Get all MCP tools from the tool provider */
    fun getMcpTools(): List<io.modelcontextprotocol.kotlin.sdk.Tool> {
        return if (isInitialized()) toolProvider.getAllTools() else emptyList()
    }

    /** Call an MCP tool */
    suspend fun callMcpTool(
        name: String,
        arguments: Map<String, Any>,
    ): io.modelcontextprotocol.kotlin.sdk.CallToolResult {
        return if (isInitialized()) {
            toolProvider.callTool(name, arguments)
        } else {
            io.modelcontextprotocol.kotlin.sdk.CallToolResult(
                content =
                    listOf(
                        io.modelcontextprotocol.kotlin.sdk.TextContent(
                            text = "Server not initialized"
                        )
                    ),
                isError = true,
            )
        }
    }

    /** Get all MCP resources */
    fun getMcpResources(): List<io.modelcontextprotocol.kotlin.sdk.Resource> {
        return if (isInitialized()) resourceProvider.getAllResources() else emptyList()
    }

    /** Get all MCP prompts */
    fun getMcpPrompts(): List<io.modelcontextprotocol.kotlin.sdk.Prompt> {
        return if (isInitialized()) promptProvider.getAllPrompts() else emptyList()
    }

    // Private helper methods

    private fun startMessageHandling() {
        messageHandlerJob =
            serverScope.launch {
                transportManager.getIncomingMessages()?.collect { message ->
                    Log.d(TAG, "Received message from transport: $message")
                    handleIncomingMessage(message)
                }
            }
    }

    private suspend fun handleIncomingMessage(message: String) {
        try {
            // TODO: Parse JSON-RPC message and handle MCP protocol
            Log.d(TAG, "Processing MCP message: $message")

            // For now, echo the message back as a simple response
            val response = """{"jsonrpc": "2.0", "result": "Message received", "id": 1}"""
            broadcastMessage(response)
        } catch (e: Exception) {
            Log.e(TAG, "Error handling incoming message", e)
        }
    }

    private fun createMcpServerWithSDK(): Any? {
        return try {
            // Create server using reflection to avoid import conflicts
            val serverClass = Class.forName("io.modelcontextprotocol.kotlin.sdk.server.Server")
            val implementationClass =
                Class.forName("io.modelcontextprotocol.kotlin.sdk.Implementation")
            val serverOptionsClass =
                Class.forName("io.modelcontextprotocol.kotlin.sdk.server.ServerOptions")
            val serverCapabilitiesClass =
                Class.forName("io.modelcontextprotocol.kotlin.sdk.ServerCapabilities")

            // Create Implementation
            val implementationConstructor =
                implementationClass.getConstructor(String::class.java, String::class.java)
            val implementation = implementationConstructor.newInstance(name, version)

            // Create basic ServerOptions (simplified for now)
            val serverOptionsConstructor =
                serverOptionsClass.getConstructor(serverCapabilitiesClass)
            val serverCapabilitiesConstructor = serverCapabilitiesClass.getConstructor()
            val capabilities = serverCapabilitiesConstructor.newInstance()
            val options = serverOptionsConstructor.newInstance(capabilities)

            // Create Server
            val serverConstructor =
                serverClass.getConstructor(implementationClass, serverOptionsClass)
            serverConstructor.newInstance(implementation, options)
        } catch (e: Exception) {
            Log.w(TAG, "Reflection-based server creation failed", e)
            null
        }
    }

    private fun createAndroidServerCapabilities(): ServerCapabilities {
        return ServerCapabilities(
            tools = ToolsCapability(listChanged = true),
            resources = ResourcesCapability(subscribe = true, listChanged = true),
            prompts = PromptsCapability(listChanged = true),
        )
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
