package dev.jasonpearson.sampleandroidmcp

import android.app.Application
import android.util.Log
import dev.jasonpearson.mcpandroidsdk.McpStartup

/**
 * Sample Application class demonstrating different ways to initialize the MCP Server Manager.
 *
 * This class demonstrates multiple initialization patterns:
 * 1. Automatic initialization and startup via AndroidX Startup (recommended)
 * 2. DI framework integration (Hilt, Koin, Dagger) - preferred for production apps
 * 3. Manual initialization for special cases
 *
 * IMPORTANT: For production apps, prefer DI framework integration over Application.onCreate()
 * initialization to avoid blocking the main thread during app startup.
 */
class SampleMcpApplication : Application() {

    companion object {
        private const val TAG = "SampleMcpApplication"
    }

    override fun onCreate() {
        super.onCreate()

        Log.i(TAG, "🚀 Starting Sample Android MCP App")

        // Example 1: Check automatic initialization (recommended for simple apps)
        // The MCP server will be automatically initialized and started via AndroidX Startup
        demonstrateAutomaticInitialization()

        // Example 2: For production apps, see DI framework examples in getting-started.md
        // demonstrateDIFrameworkPattern()

        // Example 3: Manual initialization (only for special cases)
        // demonstrateManualInitialization()

        // Example 4: Configure custom behavior after automatic initialization
        configureCustomMcpBehavior()
    }

    /**
     * Example showing how to check if automatic initialization and startup worked. This is the
     * recommended approach for simple apps when using AndroidX Startup.
     *
     * For production apps, prefer DI framework integration (Hilt, Koin, Dagger).
     */
    private fun demonstrateAutomaticInitialization() {
        Log.d(TAG, "Checking automatic initialization via AndroidX Startup...")

        try {
            if (McpStartup.isInitialized()) {
                val manager = McpStartup.getManager()
                Log.i(TAG, "✅ MCP Server Manager automatically initialized via AndroidX Startup")
                Log.i(TAG, "✅ MCP Server running: ${manager.isServerRunning()}")
                val sdkVersion = manager.getMcpSdkVersion()
                Log.i(TAG, "ℹ️  MCP SDK Version: $sdkVersion")

                // Get transport info for development
                val transportInfo = manager.getTransportInfo()
                Log.i(TAG, "ℹ️  Transport info: $transportInfo")
            } else {
                Log.w(TAG, "⚠️  Automatic initialization not completed yet or failed")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error checking automatic initialization", e)
        }
    }

    /**
     * Example showing DI framework pattern (preferred for production apps).
     *
     * This method demonstrates the concept, but actual DI integration should be done in your DI
     * modules (Hilt @Module, Koin module, etc.) rather than in Application.onCreate().
     *
     * See getting-started.md for complete DI framework examples.
     */
    private fun demonstrateDIFrameworkPattern() {
        Log.d(TAG, "DI Framework Pattern Example...")

        // In real apps, this would be handled by your DI framework:
        //
        // @Module
        // @InstallIn(SingletonComponent::class)
        // object McpModule {
        //     @Provides @Singleton
        //     fun provideMcpServerManager(@ApplicationContext context: Context): McpServerManager {
        //         return McpStartup.initializeManually(context)
        //     }
        // }

        Log.i(
            TAG,
            "ℹ️  For production apps, use DI framework integration instead of Application.onCreate()",
        )
        Log.i(TAG, "ℹ️  See getting-started.md for Hilt, Koin, and Dagger examples")
    }

    /**
     * Example showing manual initialization (only for special cases).
     *
     * This approach is NOT recommended for production apps as it blocks the main thread during
     * application startup. Use DI framework integration instead.
     *
     * Only use this for special cases where you need very specific control over initialization
     * timing.
     */
    private fun demonstrateManualInitialization() {
        Log.d(TAG, "Manual initialization (special cases only)...")

        try {
            // NOTE: This blocks the main thread - not recommended for production
            val manager = McpStartup.initializeManually(this)
            Log.i(
                TAG,
                "⚠️  MCP Server Manager manually initialized (not recommended for production)",
            )
            Log.i(TAG, "✅ MCP Server running: ${manager.isServerRunning()}")

            val sdkVersion = manager.getMcpSdkVersion()
            Log.i(TAG, "ℹ️  MCP SDK Version: $sdkVersion")

            Log.w(TAG, "⚠️  Consider using DI framework integration for production apps")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to manually initialize MCP Server Manager", e)
        }
    }

    /**
     * Check if the MCP server is ready and running.
     *
     * This method can be called from anywhere in your app to check MCP status. In production apps
     * with DI, you'd typically inject McpServerManager instead.
     */
    fun isMcpServerReady(): Boolean {
        return try {
            McpStartup.isInitialized() && McpStartup.getManager().isServerRunning()
        } catch (e: Exception) {
            Log.e(TAG, "Error checking MCP server status", e)
            false
        }
    }

    /**
     * Example configuration that could be done in a DI-provided configuration class instead of in
     * Application.onCreate().
     */
    private fun configureCustomMcpBehavior() {
        if (!McpStartup.isInitialized()) {
            Log.w(TAG, "MCP not initialized, skipping custom configuration")
            return
        }

        try {
            val manager = McpStartup.getManager()

            // Add sample-specific tools
            manager.addSimpleTool(
                name = "sample_app_info",
                description = "Get information about this sample Android MCP app",
            ) { arguments ->
                buildString {
                    appendLine("Sample Android MCP App")
                    appendLine("====================")
                    appendLine("Package: ${packageName}")
                    appendLine(
                        "Version: ${
                            packageManager.getPackageInfo(
                                packageName,
                                0,
                            ).versionName
                        }"
                    )
                    appendLine(
                        "Build: ${
                            packageManager.getPackageInfo(
                                packageName,
                                0,
                            ).longVersionCode
                        }"
                    )
                    appendLine("MCP SDK: ${manager.getMcpSdkVersion()}")
                    appendLine("Server Running: ${manager.isServerRunning()}")
                    appendLine("Transport: Available on ports 8080 (WebSocket) and 8081 (HTTP/SSE)")
                    appendLine("")
                    appendLine("To connect from your workstation:")
                    appendLine("1. adb forward tcp:8080 tcp:8080")
                    appendLine("2. adb forward tcp:8081 tcp:8081")
                    appendLine(
                        "3. Connect to ws://localhost:8080/mcp or http://localhost:8081/mcp/"
                    )
                }
            }

            manager.addSimpleTool(
                name = "sample_demo_tool",
                description = "Demo tool that echoes input with timestamp",
            ) { arguments ->
                val input = arguments["message"] as? String ?: "Hello from Android MCP!"
                val timestamp = System.currentTimeMillis()
                "Echo from Sample App at $timestamp: $input"
            }

            // Add a sample resource
            manager.addFileResource(
                uri = "sample://app/info",
                name = "Sample App Information",
                description = "Information about this sample application",
                filePath = "", // We'll provide content dynamically
                mimeType = "application/json",
            )

            Log.i(TAG, "✅ Sample app MCP tools and resources configured")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to configure sample app MCP behavior", e)
        }
    }
}
