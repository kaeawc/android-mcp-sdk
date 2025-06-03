package dev.jasonpearson.androidmcpsdk.debugbridge

import android.content.Context
import android.util.Log
import dev.jasonpearson.androidmcpsdk.core.features.tools.McpToolProvider
import dev.jasonpearson.androidmcpsdk.core.features.tools.ToolContributor
import dev.jasonpearson.androidmcpsdk.debugbridge.tools.AccessibilityInspectionToolProvider
import dev.jasonpearson.androidmcpsdk.debugbridge.tools.AndroidSystemToolProvider
import dev.jasonpearson.androidmcpsdk.debugbridge.tools.ApplicationInfoToolProvider
import dev.jasonpearson.androidmcpsdk.debugbridge.tools.DatabaseToolProvider
import dev.jasonpearson.androidmcpsdk.debugbridge.tools.DeviceInfoToolProvider
import dev.jasonpearson.androidmcpsdk.debugbridge.tools.FilePermissionToolProvider
import dev.jasonpearson.androidmcpsdk.debugbridge.tools.NetworkToolProvider
import dev.jasonpearson.androidmcpsdk.debugbridge.tools.StorageToolProvider
import dev.jasonpearson.androidmcpsdk.debugbridge.tools.ViewHierarchyToolProvider

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

    override fun registerTools(toolProvider: McpToolProvider) {
        Log.i(TAG, "Registering debug-bridge tools")

        // Create tool providers
        val deviceInfoProvider = DeviceInfoToolProvider(context)
        val systemProvider = AndroidSystemToolProvider(context)
        val appProvider = ApplicationInfoToolProvider(context)
        val networkProvider = NetworkToolProvider(context)
        val storageProvider = StorageToolProvider(context)
        val filePermissionProvider = FilePermissionToolProvider(context)
        val viewHierarchyProvider = ViewHierarchyToolProvider(context)
        val accessibilityProvider = AccessibilityInspectionToolProvider(context)
        val databaseProvider = DatabaseToolProvider(context)

        // Register all tools from each provider
        deviceInfoProvider.registerTools(toolProvider)
        systemProvider.registerTools(toolProvider)
        appProvider.registerTools(toolProvider)
        networkProvider.registerTools(toolProvider)
        storageProvider.registerTools(toolProvider)
        filePermissionProvider.registerTools(toolProvider)
        viewHierarchyProvider.registerTools(toolProvider)
        accessibilityProvider.registerTools(toolProvider)
        databaseProvider.registerTools(toolProvider)

        Log.i(TAG, "Debug-bridge tools registered successfully")
    }

    override fun getProviderName(): String = "DebugBridge"
}
