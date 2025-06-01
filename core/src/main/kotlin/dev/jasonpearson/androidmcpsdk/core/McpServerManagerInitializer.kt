package dev.jasonpearson.androidmcpsdk.core

import android.content.Context
import android.util.Log
import androidx.startup.Initializer

/**
 * AndroidX Startup initializer for McpServerManager.
 *
 * This initializer automatically initializes and starts the MCP server when the app starts,
 * eliminating the need for any manual initialization or startup calls in Application.onCreate().
 *
 * ⚠️ DEBUG BUILDS ONLY: This library will crash if included in release builds.
 *
 * To enable automatic initialization, add the following to your AndroidManifest.xml:
 * ```xml
 * <provider
 *     android:name="androidx.startup.InitializationProvider"
 *     android:authorities="${applicationId}.androidx-startup"
 *     android:exported="false"
 *     tools:node="merge">
 *     <meta-data
 *         android:name="dev.jasonpearson.androidmcpsdk.core.McpServerManagerInitializer"
 *         android:value="androidx.startup" />
 * </provider>
 * ```
 */
class McpServerManagerInitializer : Initializer<McpServerManager> {

    companion object {
        private const val TAG = "McpServerManagerInit"
    }

    override fun create(context: Context): McpServerManager {
        // SAFETY CHECK: Crash if this is running in a release build
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

        Log.d(TAG, "Initializing and starting McpServerManager via AndroidX Startup")

        val manager = McpServerManager.getInstance()

        // Initialize with default configuration
        val initResult =
            manager.initialize(
                context = context.applicationContext,
                serverName = "Android MCP Server",
                serverVersion = "1.0.0",
            )

        initResult.fold(
            onSuccess = {
                Log.i(TAG, "McpServerManager initialized successfully via AndroidX Startup")

                // Automatically start the server
                val startJob = manager.startServerAsync()
                if (startJob != null) {
                    Log.i(TAG, "MCP Server started automatically via AndroidX Startup")
                } else {
                    Log.w(TAG, "Failed to start MCP Server automatically")
                }
            },
            onFailure = { exception ->
                Log.e(TAG, "Failed to initialize McpServerManager via AndroidX Startup", exception)
            },
        )

        return manager
    }

    override fun dependencies(): List<Class<out Initializer<*>>> {
        // No dependencies on other initializers
        return emptyList()
    }

    /**
     * Check if this is a debug build by examining the application info flags. This is more reliable
     * than BuildConfig.DEBUG since it checks the actual build configuration of the app using the
     * library.
     */
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
