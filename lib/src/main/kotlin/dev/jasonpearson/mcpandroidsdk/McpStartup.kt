package dev.jasonpearson.mcpandroidsdk

import android.content.Context
import android.util.Log
import androidx.startup.AppInitializer

/**
 * Utility class for managing MCP server initialization with AndroidX Startup.
 *
 * This class provides convenient methods for both automatic and manual initialization
 * of the MCP server manager.
 */
object McpStartup {

    private const val TAG = "McpStartup"

    /**
     * Manually initialize the MCP server manager using AndroidX Startup.
     *
     * This method is useful if you've disabled automatic initialization in the manifest
     * but still want to use the AndroidX Startup infrastructure.
     *
     * @param context Application or other context
     * @return The initialized McpServerManager instance
     */
    fun initializeManually(context: Context): McpServerManager {
        Log.d(TAG, "Manual initialization requested")

        return try {
            AppInitializer.getInstance(context)
                .initializeComponent(McpServerManagerInitializer::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to manually initialize MCP server", e)
            throw e
        }
    }

    /**
     * Check if the MCP server manager has been initialized.
     *
     * @return true if initialized, false otherwise
     */
    fun isInitialized(): Boolean {
        return try {
            McpServerManager.getInstance().isInitialized()
        } catch (e: Exception) {
            Log.w(TAG, "Error checking initialization status", e)
            false
        }
    }

    /**
     * Get the initialized MCP server manager instance.
     *
     * @return McpServerManager instance if initialized
     * @throws IllegalStateException if not initialized
     */
    fun getManager(): McpServerManager {
        val manager = McpServerManager.getInstance()
        if (!manager.isInitialized()) {
            throw IllegalStateException(
                "McpServerManager is not initialized. " +
                        "Ensure AndroidX Startup is configured correctly or call initializeManually()."
            )
        }
        return manager
    }

    /**
     * Initialize with custom configuration.
     *
     * This bypasses AndroidX Startup and directly initializes the manager with
     * custom parameters.
     *
     * @param context Application context
     * @param serverName Custom server name
     * @param serverVersion Custom server version
     * @return Result indicating success or failure
     */
    fun initializeWithCustomConfig(
        context: Context,
        serverName: String,
        serverVersion: String
    ): Result<McpServerManager> = runCatching {
        Log.d(TAG, "Custom initialization with name: $serverName, version: $serverVersion")

        val manager = McpServerManager.getInstance()
        manager.initialize(
            context = context.applicationContext,
            serverName = serverName,
            serverVersion = serverVersion
        ).getOrThrow()

        manager
    }
}