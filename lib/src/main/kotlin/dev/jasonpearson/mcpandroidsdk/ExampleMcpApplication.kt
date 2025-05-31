package dev.jasonpearson.mcpandroidsdk

import android.app.Application
import android.util.Log
import kotlinx.coroutines.*

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

    /** Example method showing how to start the MCP server using async method. */
    fun startMcpServer() {
        // Use the async method for non-blocking startup
        try {
            Log.i(TAG, "Starting MCP server asynchronously...")
            McpServerManager.getInstance().startServerAsync()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start MCP server", e)
        }
    }

    /** Example method showing how to start the MCP server with proper coroutine handling. */
    fun startMcpServerWithCoroutines() {
        GlobalScope.launch {
            try {
                Log.i(TAG, "Starting MCP server with coroutines...")
                McpServerManager.getInstance().startServer().getOrThrow()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start MCP server", e)
            }
        }
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
