package dev.jasonpearson.mcpandroidsdk

import android.app.Application
import android.util.Log
import kotlinx.coroutines.*

/**
 * Example Application class showing different ways to initialize the MCP Server Manager.
 *
 * This class demonstrates multiple initialization patterns:
 * 1. Automatic initialization via AndroidX Startup (recommended)
 * 2. Manual initialization in Application.onCreate()
 * 3. Custom configuration initialization
 *
 * Choose the pattern that best fits your application's needs.
 */
class ExampleMcpApplication : Application() {

    companion object {
        private const val TAG = "ExampleMcpApplication"
    }

    override fun onCreate() {
        super.onCreate()

        // Example 1: Using AndroidX Startup (automatic initialization)
        // If you have the provider configured in AndroidManifest.xml,
        // the MCP server will be automatically initialized.
        demonstrateAutomaticInitialization()

        // Example 2: Manual initialization (if you disabled automatic initialization)
        // demonstrateManualInitialization()

        // Example 3: Custom configuration initialization
        // demonstrateCustomInitialization()
    }

    /**
     * Example showing how to check if automatic initialization worked. This is the recommended
     * approach when using AndroidX Startup.
     */
    private fun demonstrateAutomaticInitialization() {
        Log.d(TAG, "Checking automatic initialization via AndroidX Startup...")

        try {
            if (McpStartup.isInitialized()) {
                Log.i(TAG, "MCP Server Manager automatically initialized via AndroidX Startup")
                val sdkVersion = McpStartup.getManager().getMcpSdkVersion()
                Log.i(TAG, "MCP SDK Version: $sdkVersion")
            } else {
                Log.w(TAG, "Automatic initialization not completed yet or failed")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking automatic initialization", e)
        }
    }

    /**
     * Example showing manual initialization using AndroidX Startup infrastructure. Use this if you
     * disabled automatic initialization in the manifest.
     */
    private fun demonstrateManualInitialization() {
        Log.d(TAG, "Performing manual initialization...")

        try {
            val manager = McpStartup.initializeManually(this)
            Log.i(TAG, "MCP Server Manager manually initialized successfully")

            val sdkVersion = manager.getMcpSdkVersion()
            Log.i(TAG, "MCP SDK Version: $sdkVersion")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to manually initialize MCP Server Manager", e)
        }
    }

    /**
     * Example showing initialization with custom configuration. Use this when you need specific
     * server name/version settings.
     */
    private fun demonstrateCustomInitialization() {
        Log.d(TAG, "Performing custom initialization...")

        val result =
            McpStartup.initializeWithCustomConfig(
                context = this,
                serverName = "My Custom Android MCP Server",
                serverVersion = "2.0.0",
            )

        result.fold(
            onSuccess = { manager ->
                Log.i(TAG, "Custom MCP Server Manager initialized successfully")
                val sdkVersion = manager.getMcpSdkVersion()
                Log.i(TAG, "MCP SDK Version: $sdkVersion")
            },
            onFailure = { exception ->
                Log.e(TAG, "Failed to initialize with custom config", exception)
            },
        )
    }

    /** Example method showing how to start the MCP server using async method. */
    fun startMcpServer() {
        // Use the async method for non-blocking startup
        try {
            Log.i(TAG, "Starting MCP server asynchronously...")
            val manager = McpStartup.getManager()
            manager.startServerAsync()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start MCP server", e)
        }
    }

    /** Example method showing how to start the MCP server with proper coroutine handling. */
    fun startMcpServerWithCoroutines() {
        GlobalScope.launch {
            try {
                Log.i(TAG, "Starting MCP server with coroutines...")
                val manager = McpStartup.getManager()
                manager.startServer().getOrThrow()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start MCP server", e)
            }
        }
    }

    /** Check if the MCP server is ready. */
    fun isMcpServerReady(): Boolean {
        return try {
            McpStartup.isInitialized()
        } catch (e: Exception) {
            Log.e(TAG, "Error checking MCP server status", e)
            false
        }
    }
}
