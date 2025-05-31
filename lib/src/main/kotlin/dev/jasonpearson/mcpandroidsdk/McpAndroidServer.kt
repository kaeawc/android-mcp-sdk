package dev.jasonpearson.mcpandroidsdk

import android.content.Context
import android.util.Log
import dev.jasonpearson.mcpandroidsdk.models.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.*

/**
 * Android-specific wrapper for MCP Server functionality. Provides easy integration of MCP servers
 * in Android applications.
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
    private var serverJob: Job? = null
    private val serverScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Basic tool definitions for Android-specific functionality
    private val availableTools = mutableListOf<AndroidTool>()

    /** Initialize the MCP server with full capabilities */
    fun initialize(): Result<Unit> = runCatching {
        Log.d(TAG, "Initializing MCP server: $name v$version")

        // Add default Android tools
        addDefaultTools()

        Log.i(TAG, "MCP server initialized successfully with ${availableTools.size} tools")
    }

    /** Start the MCP server. This will run until stop() is called. */
    suspend fun start(): Result<Unit> = runCatching {
        if (isRunning.compareAndSet(false, true)) {
            Log.i(TAG, "Starting MCP server...")

            serverJob =
                serverScope.launch {
                    try {
                        // TODO: Implement actual MCP server startup with proper SDK integration
                        // For now, just simulate a running server that can respond to tool calls
                        while (isActive) {
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

            Log.i(TAG, "MCP server started successfully")
        } else {
            Log.w(TAG, "MCP server is already running")
        }
    }

    /** Stop the MCP server */
    suspend fun stop(): Result<Unit> = runCatching {
        Log.i(TAG, "Stopping MCP server...")

        serverJob?.cancel()
        serverJob?.join()
        isRunning.set(false)

        Log.i(TAG, "MCP server stopped successfully")
    }

    /** Check if the server is currently running */
    fun isRunning(): Boolean = isRunning.get()

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
