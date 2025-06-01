package dev.jasonpearson.androidmcpsdk.core

import android.app.Application
import dev.jasonpearson.androidmcpsdk.core.features.resources.ResourceProvider
import dev.jasonpearson.androidmcpsdk.core.features.resources.ResourceProviderContainer

class McpApplication : Application(), ResourceProviderContainer {

    // Simple singleton for ResourceProvider for testing purposes.
    // In a real app, use a proper DI framework.
    private val _resourceProvider: ResourceProvider by lazy { ResourceProvider(applicationContext) }

    override fun getResourceProvider(): ResourceProvider {
        return _resourceProvider
    }

    override fun onCreate() {
        super.onCreate()
        // Initialize other SDK components if necessary
        McpServerManager.getInstance()
            .initialize(
                context = this,
                serverName = "Android MCP Test Server",
                serverVersion = "1.0.0",
            )
    }
}
