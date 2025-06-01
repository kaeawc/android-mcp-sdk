package dev.jasonpearson.androidmcpsdk.debugbridge.tools

import android.content.Context
import android.util.Log
import dev.jasonpearson.androidmcpsdk.core.features.tools.ToolRegistry

/**
 * Provides network tools for the debug bridge.
 *
 * TODO: Implement network debugging tools like connectivity status, network info, etc.
 */
class NetworkToolProvider(private val context: Context) {

    companion object {
        private const val TAG = "NetworkToolProvider"
    }

    fun registerTools(registry: ToolRegistry) {
        Log.d(TAG, "Network tools provider registered (no tools implemented yet)")

        // TODO: Add network tools
        // - network_info
        // - connectivity_status
        // - wifi_info
        // - network_stats
    }
}
