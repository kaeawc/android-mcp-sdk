package dev.jasonpearson.sampleandroidmcp

import android.app.Application
import android.util.Log
import dagger.hilt.android.HiltAndroidApp

/**
 * Sample Application demonstrating Hilt DI integration with Android MCP SDK.
 *
 * This example shows how to properly integrate MCP with Hilt dependency injection
 * for debug Android applications.
 *
 * Key features:
 * - Automatic initialization disabled in manifest
 * - MCP initialization handled by Hilt DI
 * - Clean separation of concerns
 * - Debug build patterns
 * - Safe for development environments only
 */
@HiltAndroidApp
class HiltMcpApplication : Application() {

    companion object {
        private const val TAG = "HiltMcpApplication"
    }

    override fun onCreate() {
        super.onCreate()

        Log.i(TAG, "🚀 Starting Hilt MCP Integration Sample (Debug Build)")
        Log.i(TAG, "ℹ️  MCP initialization handled by Hilt DI")
        Log.i(TAG, "ℹ️  See McpModule.kt for DI configuration")
    }
}
