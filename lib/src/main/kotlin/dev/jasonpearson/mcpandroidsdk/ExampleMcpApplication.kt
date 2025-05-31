package dev.jasonpearson.mcpandroidsdk

import android.app.Application
import android.util.Log

/**
 * Example Application class showing how to initialize the MCP Server Manager. This class can be
 * used as a reference for integrating MCP server functionality into your Android application.
 */
class ExampleMcpApplication : Application() {

    companion object {
        private const val TAG = "ExampleMcpApplication"
    }

    override fun onCreate() {
        super.onCreate()

        Log.d(TAG, "Initializing MCP Server Manager...")

        // Initialize the MCP Server Manager singleton
        try {
            McpServerManager.getInstance().initialize(this)
            Log.d(TAG, "MCP Server Manager initialized successfully")

            // Log the MCP SDK version
            val sdkVersion = McpServerManager.getInstance().getMcpSdkVersion()
            Log.i(TAG, "MCP SDK Version: $sdkVersion")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize MCP Server Manager", e)
        }
    }

    /**
     * Example method showing how to start the MCP server. Note: This should typically be called
     * from a background thread as it will block until the server stops.
     */
    fun startMcpServer() {
        Thread {
                try {
                    Log.i(TAG, "Starting MCP server in background thread...")
                    McpServerManager.getInstance().startServer()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to start MCP server", e)
                }
            }
            .start()
    }

    /** Check if the MCP server is ready. */
    fun isMcpServerReady(): Boolean {
        return try {
            McpServerManager.getInstance().isInitialized()
        } catch (e: Exception) {
            Log.e(TAG, "Error checking MCP server status", e)
            false
        }
    }
}
