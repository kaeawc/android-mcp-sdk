package dev.jasonpearson.mcpandroidsdk

import android.content.Context
import android.util.Log

/**
 * Singleton manager for the MCP Android Server. This class provides a thread-safe singleton
 * instance and manages the MCP server lifecycle.
 */
class McpServerManager private constructor() {

    companion object {
        private const val TAG = "McpServerManager"

        @Volatile private var INSTANCE: McpServerManager? = null

        /** Get the singleton instance of McpServerManager. */
        fun getInstance(): McpServerManager {
            return INSTANCE
                ?: synchronized(this) { INSTANCE ?: McpServerManager().also { INSTANCE = it } }
        }
    }

    @Volatile private var mcpServer: McpAndroidServer? = null
    private var isInitialized = false

    /**
     * Initialize the MCP server manager. This should be called from Application.onCreate() or using
     * AndroidX Startup.
     */
    fun initialize(context: Context) {
        synchronized(this) {
            if (isInitialized) {
                Log.d(TAG, "McpServerManager already initialized")
                return
            }

            Log.d(TAG, "Initializing McpServerManager")

            try {
                // Create the MCP server instance
                mcpServer =
                    McpAndroidServer.createServer(name = "android-mcp-server", version = "1.0.0")

                isInitialized = true
                Log.d(TAG, "McpServerManager initialized successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize McpServerManager", e)
                throw e
            }
        }
    }

    /**
     * Get the MCP server instance.
     *
     * @throws IllegalStateException if the manager is not initialized
     */
    fun getMcpServer(): McpAndroidServer {
        return mcpServer
            ?: throw IllegalStateException(
                "McpServerManager not initialized. Call initialize(context) first."
            )
    }

    /** Check if the MCP server manager is initialized. */
    fun isInitialized(): Boolean = isInitialized

    /** Start the MCP server. This will block the current thread until the server stops. */
    fun startServer() {
        Log.d(TAG, "Starting MCP server...")
        getMcpServer().start()
    }

    /** Get the MCP SDK version. */
    fun getMcpSdkVersion(): String {
        return McpAndroidServer.getMcpSdkVersion()
    }
}
