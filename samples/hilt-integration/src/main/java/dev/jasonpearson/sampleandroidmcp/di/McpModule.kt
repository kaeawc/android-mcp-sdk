package dev.jasonpearson.sampleandroidmcp.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.jasonpearson.androidmcpsdk.core.McpServerManager
import dev.jasonpearson.androidmcpsdk.core.McpStartup
import javax.inject.Named
import javax.inject.Singleton

/**
 * Hilt module for Android MCP SDK integration.
 *
 * This module demonstrates the recommended pattern for integrating MCP with Hilt dependency
 * injection in debug Android applications.
 *
 * Key benefits:
 * - Lazy initialization (only when first injected)
 * - Proper dependency management
 * - Easy testing and mocking
 * - Configuration management via DI
 * - Safe for debug environments only
 */
@Module
@InstallIn(SingletonComponent::class)
object McpModule {

    /**
     * Provides the MCP server name for configuration. In a real debug app, this might come from
     * BuildConfig, SharedPreferences, or remote config.
     */
    @Provides @Named("mcp_server_name") fun provideServerName(): String = "Hilt MCP Sample"

    /**
     * Provides the MCP server version for configuration. In a real debug app, this would typically
     * come from BuildConfig.VERSION_NAME.
     */
    @Provides @Named("mcp_server_version") fun provideServerVersion(): String = "1.0.0"

    /**
     * Provides the MCP Server Manager with custom configuration.
     *
     * This uses initializeWithCustomConfig which automatically starts the server. For more control,
     * you could use initializeManually and handle startup separately.
     *
     * Note: The MCP SDK will automatically check that this is a debug build and crash if
     * accidentally included in a release build.
     */
    @Provides
    @Singleton
    fun provideMcpServerManager(
        @ApplicationContext context: Context,
        @Named("mcp_server_name") serverName: String,
        @Named("mcp_server_version") serverVersion: String,
    ): McpServerManager {
        val result =
            McpStartup.initializeWithCustomConfig(
                context = context,
                serverName = serverName,
                serverVersion = serverVersion,
            )

        return result.getOrThrow()
    }
}
