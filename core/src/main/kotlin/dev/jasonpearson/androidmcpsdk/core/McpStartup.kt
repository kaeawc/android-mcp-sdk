package dev.jasonpearson.androidmcpsdk.core

import android.content.Context
import android.util.Log
import androidx.startup.AppInitializer

/**
 * Utility class for managing MCP server initialization with AndroidX Startup.
 *
 * This class provides convenient methods for both automatic and manual initialization of the MCP
 * server manager.
 *
 * ⚠️ DEBUG BUILDS ONLY: This library will crash if used in release builds.
 */
object McpStartup {

    private const val TAG = "McpStartup"

    /**
     * Manually initialize the MCP server manager using AndroidX Startup.
     *
     * This method is useful if you've disabled automatic initialization in the manifest but still
     * want to use the AndroidX Startup infrastructure.
     *
     * @param context Application or other context
     * @return The initialized McpServerManager instance
     * @throws IllegalStateException if called in a release build
     */
    fun initializeManually(context: Context): McpServerManager {
        // SAFETY CHECK: Crash if this is running in a release build
        checkDebugBuildOrCrash(context)

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
     * @throws IllegalStateException if not initialized or if called in release build
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
     * This bypasses AndroidX Startup and directly initializes and starts the manager with custom
     * parameters.
     *
     * @param context Application context
     * @param serverName Custom server name
     * @param serverVersion Custom server version
     * @return Result indicating success or failure
     * @throws IllegalStateException if called in a release build
     */
    fun initializeWithCustomConfig(
        context: Context,
        serverName: String,
        serverVersion: String,
    ): Result<McpServerManager> = runCatching {
        // SAFETY CHECK: Crash if this is running in a release build
        checkDebugBuildOrCrash(context)

        Log.d(TAG, "Custom initialization with name: $serverName, version: $serverVersion")

        val manager = McpServerManager.getInstance()
        manager
            .initialize(
                context = context.applicationContext,
                serverName = serverName,
                serverVersion = serverVersion,
            )
            .getOrThrow()

        // Automatically start the server
        val startJob = manager.startServerAsync()
        if (startJob != null) {
            Log.i(TAG, "MCP Server started automatically with custom config")
        } else {
            Log.w(TAG, "Failed to start MCP Server automatically with custom config")
        }

        manager
    }

    /**
     * Check if this is a debug build and crash if it's a release build. This provides a safety
     * mechanism to prevent accidental inclusion in production.
     */
    private fun checkDebugBuildOrCrash(context: Context) {
        if (!isDebugBuild(context)) {
            throw IllegalStateException(
                "❌ ANDROID MCP SDK ERROR: This library is for DEBUG BUILDS ONLY!\n" +
                    "The MCP SDK should NEVER be included in release/production builds.\n" +
                    "Use 'debugImplementation' instead of 'implementation' in your build.gradle:\n" +
                    "debugImplementation(\"dev.jasonpearson:mcp-android-sdk:1.0.0\")\n" +
                    "\n" +
                    "This library exposes internal app data via network protocols and is intended\n" +
                    "ONLY for development and debugging purposes."
            )
        }
    }

    /** Check if this is a debug build by examining the application info flags. */
    private fun isDebugBuild(context: Context): Boolean {
        return try {
            val appInfo =
                context.applicationContext.packageManager.getApplicationInfo(context.packageName, 0)
            (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
        } catch (e: Exception) {
            // If we can't determine, assume it's release and fail safe
            Log.e(TAG, "Unable to determine build type, assuming release build", e)
            false
        }
    }
}
