package dev.jasonpearson.mcpandroidsdk

import android.content.Context
import android.util.Log
import androidx.startup.Initializer

/**
 * AndroidX Startup initializer for McpServerManager.
 *
 * This initializer automatically initializes the MCP server manager when the app starts,
 * eliminating the need for manual initialization in Application.onCreate().
 *
 * To enable automatic initialization, add the following to your AndroidManifest.xml:
 *
 * ```xml
 * <provider
 *     android:name="androidx.startup.InitializationProvider"
 *     android:authorities="${applicationId}.androidx-startup"
 *     android:exported="false"
 *     tools:node="merge">
 *     <meta-data
 *         android:name="dev.jasonpearson.mcpandroidsdk.McpServerManagerInitializer"
 *         android:value="androidx.startup" />
 * </provider>
 * ```
 */
class McpServerManagerInitializer : Initializer<McpServerManager> {

    companion object {
        private const val TAG = "McpServerManagerInit"
    }

    override fun create(context: Context): McpServerManager {
        Log.d(TAG, "Initializing McpServerManager via AndroidX Startup")

        val manager = McpServerManager.getInstance()

        // Initialize with default configuration
        val result = manager.initialize(
            context = context.applicationContext,
            serverName = "Android MCP Server",
            serverVersion = "1.0.0"
        )

        result.fold(
            onSuccess = {
                Log.i(TAG, "McpServerManager initialized successfully via AndroidX Startup")
            },
            onFailure = { exception ->
                Log.e(TAG, "Failed to initialize McpServerManager via AndroidX Startup", exception)
            }
        )

        return manager
    }

    override fun dependencies(): List<Class<out Initializer<*>>> {
        // No dependencies on other initializers
        return emptyList()
    }
}