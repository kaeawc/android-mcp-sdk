package dev.jasonpearson.androidmcpsdk.debugbridge

import android.content.Context
import android.util.Log
import dev.jasonpearson.androidmcpsdk.core.features.tools.ToolContributor
import dev.jasonpearson.androidmcpsdk.core.features.tools.ToolRegistry
import dev.jasonpearson.androidmcpsdk.debugbridge.tools.AndroidSystemToolProvider
import dev.jasonpearson.androidmcpsdk.debugbridge.tools.ApplicationInfoToolProvider
import dev.jasonpearson.androidmcpsdk.debugbridge.tools.DeviceInfoToolProvider
import dev.jasonpearson.androidmcpsdk.debugbridge.tools.NetworkToolProvider
import dev.jasonpearson.androidmcpsdk.debugbridge.tools.StorageToolProvider

/**
 * Main tool contributor for the debug-bridge module.
 *
 * This contributor registers all Android-specific debugging tools with the core MCP server
 * registry.
 */
class DebugBridgeToolContributor(private val context: Context) : ToolContributor {

    companion object {
        private const val TAG = "DebugBridgeContrib"
    }

    override fun registerTools(registry: ToolRegistry) {
        Log.i(TAG, "Registering debug-bridge tools")

        // Create tool providers
        val deviceInfoProvider = DeviceInfoToolProvider(context)
        val systemProvider = AndroidSystemToolProvider(context)
        val appProvider = ApplicationInfoToolProvider(context)
        val networkProvider = NetworkToolProvider(context)
        val storageProvider = StorageToolProvider(context)

        // Register all tools from each provider
        deviceInfoProvider.registerTools(registry)
        systemProvider.registerTools(registry)
        appProvider.registerTools(registry)
        networkProvider.registerTools(registry)
        storageProvider.registerTools(registry)

        Log.i(TAG, "Debug-bridge tools registered successfully")
    }

    override fun getProviderName(): String = "DebugBridge"
}
