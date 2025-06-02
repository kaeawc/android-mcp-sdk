package dev.jasonpearson.androidmcpsdk.debugbridge.tools

import android.content.Context
import android.util.Log
import dev.jasonpearson.androidmcpsdk.core.features.tools.McpToolProvider

/**
 * Provides storage tools for the debug bridge.
 *
 * TODO: Implement storage debugging tools like storage info, directory listing, file operations,
 *   etc.
 */
class StorageToolProvider(private val context: Context) {

    companion object {
        private const val TAG = "StorageToolProvider"
    }

    fun registerTools(toolProvider: McpToolProvider) {
        Log.d(TAG, "Storage tools provider registered (no tools implemented yet)")

        // TODO: Add storage tools
        // - storage_info
        // - directory_listing
        // - file_info
        // - disk_usage
    }
}
