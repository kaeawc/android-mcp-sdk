package dev.jasonpearson.mcpandroidsdk

import android.content.Context
import android.util.Log
import dev.jasonpearson.mcpandroidsdk.features.prompts.PromptProvider
import dev.jasonpearson.mcpandroidsdk.features.resources.ResourceProvider
import dev.jasonpearson.mcpandroidsdk.features.tools.ToolProvider
import dev.jasonpearson.mcpandroidsdk.models.*
import dev.jasonpearson.mcpandroidsdk.transport.AndroidStdioTransport
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.*
import kotlinx.serialization.json.*

/**
 * Android-specific wrapper for MCP Server functionality. Provides easy integration of MCP servers
 * in Android applications with MCP Kotlin SDK integration.
 *
 * This library integrates the MCP Kotlin SDK (io.modelcontextprotocol:kotlin-sdk:0.5.0) to enable
 * Android apps to host MCP servers and expose them to MCP clients running on adb-connected
 * workstations.
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
    private var stdioTransport: AndroidStdioTransport? = null

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

        // Try to create MCP server with SDK
        try {
            mcpServer = createMcpServerWithSDK()
            Log.i(TAG, "MCP server created with SDK integration")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to create MCP server with SDK, using fallback", e)
            mcpServer = null
        }

        // Initialize STDIO transport
        try {
            stdioTransport = AndroidStdioTransport.createStandardTransport()
            stdioTransport?.setMessageHandler { message ->
                handleIncomingMessage(message)
            }
            Log.i(TAG, "STDIO transport initialized")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to initialize STDIO transport", e)
            stdioTransport = null
        }

        // Add default Android tools
        addDefaultTools()

        isInitialized.set(true)
        Log.i(TAG, "MCP server initialized successfully with ${availableTools.size} tools")
    }

    /** Start the MCP server. This will run until stop() is called. */
    suspend fun start(): Result<Unit> = runCatching {
        if (!isInitialized.get()) {
            throw IllegalStateException("Server must be initialized before starting")
        }

        if (isRunning.compareAndSet(false, true)) {
            Log.i(TAG, "Starting MCP server...")

            // Start STDIO transport if available
            stdioTransport?.let { transport ->
                transport.start().fold(
                    onSuccess = { Log.i(TAG, "STDIO transport started") },
                    onFailure = { e -> Log.w(TAG, "Failed to start STDIO transport", e) }
                )
            }

            serverJob =
                serverScope.launch {
                    try {
                        mcpServer?.let {
                            Log.i(
                                TAG,
                                "Starting MCP server with SDK integration and STDIO transport"
                            )
                            // Full transport integration would connect the SDK server to STDIO here
                            while (isActive && isRunning.get()) {
                                delay(1000)
                            }
                        }
                            ?: run {
                                Log.i(TAG, "Running in fallback mode with basic STDIO transport")
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

            Log.i(TAG, "MCP server started successfully")
        } else {
            Log.w(TAG, "MCP server is already running")
        }
    }

    /** Stop the MCP server */
    suspend fun stop(): Result<Unit> = runCatching {
        Log.i(TAG, "Stopping MCP server...")

        // Stop STDIO transport
        stdioTransport?.stop()?.fold(
            onSuccess = { Log.i(TAG, "STDIO transport stopped") },
            onFailure = { e -> Log.w(TAG, "Error stopping STDIO transport", e) }
        )

        serverJob?.cancel()
        serverJob?.join()
        isRunning.set(false)

        Log.i(TAG, "MCP server stopped successfully")
    }

    /** Send a message via STDIO transport */
    suspend fun sendStdioMessage(message: String): Result<Unit> {
        return stdioTransport?.sendMessage(message)
            ?: Result.failure(IllegalStateException("STDIO transport not available"))
    }

    /** Check if the server is currently running */
    fun isRunning(): Boolean = isRunning.get()

    /** Check if the server is initialized */
    fun isInitialized(): Boolean = isInitialized.get()

    /** Check if STDIO transport is available */
    fun hasStdioTransport(): Boolean = stdioTransport != null

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

    /** Get comprehensive server information */
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
            transportInfo = mapOf(
                "stdio" to hasStdioTransport(),
                "sdk_integration" to hasSDKIntegration()
            )
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

    /**
     * Handle incoming messages from STDIO transport
     */
    private fun handleIncomingMessage(message: String) {
        Log.d(TAG, "Handling incoming STDIO message: $message")

        try {
            // Parse JSON-RPC message
            val json = Json.parseToJsonElement(message)
            val jsonObject = json.jsonObject

            val method = jsonObject["method"]?.jsonPrimitive?.content
            val id = jsonObject["id"]?.jsonPrimitive?.content

            when (method) {
                "initialize" -> handleInitializeRequest(id, jsonObject)
                "tools/list" -> handleToolsListRequest(id)
                "tools/call" -> handleToolCallRequest(id, jsonObject)
                "resources/list" -> handleResourcesListRequest(id)
                "prompts/list" -> handlePromptsListRequest(id)
                else -> {
                    Log.w(TAG, "Unknown method: $method")
                    sendErrorResponse(id, -32601, "Method not found")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling message", e)
            sendErrorResponse(null, -32700, "Parse error")
        }
    }

    private fun handleInitializeRequest(id: String?, request: JsonObject) {
        Log.d(TAG, "Handling initialize request")

        val response = buildJsonObject {
            put("jsonrpc", "2.0")
            if (id != null) put("id", id)
            put("result", buildJsonObject {
                put("protocolVersion", "2024-11-05")
                put("capabilities", buildJsonObject {
                    put("tools", buildJsonObject {
                        put("listChanged", true)
                    })
                    put("resources", buildJsonObject {
                        put("subscribe", true)
                        put("listChanged", true)
                    })
                    put("prompts", buildJsonObject {
                        put("listChanged", true)
                    })
                })
                put("serverInfo", buildJsonObject {
                    put("name", name)
                    put("version", version)
                })
            })
        }

        sendStdioResponse(response.toString())
    }

    private fun handleToolsListRequest(id: String?) {
        Log.d(TAG, "Handling tools/list request")

        val toolsArray = buildJsonArray {
            for (tool in availableTools) {
                add(buildJsonObject {
                    put("name", tool.name)
                    put("description", tool.description)
                    put("inputSchema", buildJsonObject {
                        put("type", "object")
                        put("properties", buildJsonObject {
                            for ((key, type) in tool.parameters) {
                                put(key, buildJsonObject {
                                    put("type", type)
                                })
                            }
                        })
                    })
                })
            }
        }

        val response = buildJsonObject {
            put("jsonrpc", "2.0")
            if (id != null) put("id", id)
            put("result", buildJsonObject {
                put("tools", toolsArray)
            })
        }

        sendStdioResponse(response.toString())
    }

    private fun handleToolCallRequest(id: String?, request: JsonObject) {
        Log.d(TAG, "Handling tools/call request")

        serverScope.launch {
            try {
                val params = request["params"]?.jsonObject
                val toolName = params?.get("name")?.jsonPrimitive?.content
                val arguments = params?.get("arguments")?.jsonObject?.let { argsObj ->
                    argsObj.mapValues { (_, value) ->
                        when {
                            value is JsonPrimitive && value.isString -> value.content
                            value is JsonPrimitive -> value.content
                            else -> value.toString()
                        }
                    }
                } ?: emptyMap()

                if (toolName != null) {
                    val result = executeTool(toolName, arguments)

                    val response = buildJsonObject {
                        put("jsonrpc", "2.0")
                        if (id != null) put("id", id)
                        put("result", buildJsonObject {
                            put("content", buildJsonArray {
                                add(buildJsonObject {
                                    put("type", "text")
                                    put("text", result.result ?: result.error ?: "Unknown error")
                                })
                            })
                            put("isError", !result.success)
                        })
                    }

                    sendStdioResponse(response.toString())
                } else {
                    sendErrorResponse(id, -32602, "Invalid params")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in tool call", e)
                sendErrorResponse(id, -32603, "Internal error")
            }
        }
    }

    private fun handleResourcesListRequest(id: String?) {
        Log.d(TAG, "Handling resources/list request")

        val response = buildJsonObject {
            put("jsonrpc", "2.0")
            if (id != null) put("id", id)
            put("result", buildJsonObject {
                put("resources", buildJsonArray {
                    // Add resources here when implemented
                })
            })
        }

        sendStdioResponse(response.toString())
    }

    private fun handlePromptsListRequest(id: String?) {
        Log.d(TAG, "Handling prompts/list request")

        val response = buildJsonObject {
            put("jsonrpc", "2.0")
            if (id != null) put("id", id)
            put("result", buildJsonObject {
                put("prompts", buildJsonArray {
                    // Add prompts here when implemented
                })
            })
        }

        sendStdioResponse(response.toString())
    }

    private fun sendErrorResponse(id: String?, code: Int, message: String) {
        val response = buildJsonObject {
            put("jsonrpc", "2.0")
            if (id != null) put("id", id)
            put("error", buildJsonObject {
                put("code", code)
                put("message", message)
            })
        }

        sendStdioResponse(response.toString())
    }

    private fun sendStdioResponse(response: String) {
        serverScope.launch {
            stdioTransport?.sendMessage(response)?.fold(
                onSuccess = { Log.d(TAG, "Response sent: $response") },
                onFailure = { e -> Log.e(TAG, "Failed to send response", e) }
            )
        }
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
