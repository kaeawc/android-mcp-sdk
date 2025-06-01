package dev.jasonpearson.sampleandroidmcp.di;

/**
 * Hilt module for Android MCP SDK integration.
 *
 * This module demonstrates the recommended pattern for integrating MCP
 * with Hilt dependency injection in production applications.
 *
 * Key benefits:
 * - Lazy initialization (only when first injected)
 * - Proper dependency management
 * - Easy testing and mocking
 * - Configuration management via DI
 */
@dagger.Module()
@kotlin.Metadata(mv = {2, 1, 0}, k = 1, xi = 48, d1 = {"\u0000\"\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\b\u00c7\u0002\u0018\u00002\u00020\u0001B\t\b\u0002\u00a2\u0006\u0004\b\u0002\u0010\u0003J\b\u0010\u0004\u001a\u00020\u0005H\u0007J\b\u0010\u0006\u001a\u00020\u0005H\u0007J&\u0010\u0007\u001a\u00020\b2\b\b\u0001\u0010\t\u001a\u00020\n2\b\b\u0001\u0010\u000b\u001a\u00020\u00052\b\b\u0001\u0010\f\u001a\u00020\u0005H\u0007\u00a8\u0006\r"}, d2 = {"Ldev/jasonpearson/sampleandroidmcp/di/McpModule;", "", "<init>", "()V", "provideServerName", "", "provideServerVersion", "provideMcpServerManager", "Ldev/jasonpearson/mcpandroidsdk/McpServerManager;", "context", "Landroid/content/Context;", "serverName", "serverVersion", "hilt-integration_debug"})
@dagger.hilt.InstallIn(value = {dagger.hilt.components.SingletonComponent.class})
public final class McpModule {
    @org.jetbrains.annotations.NotNull()
    public static final dev.jasonpearson.sampleandroidmcp.di.McpModule INSTANCE = null;
    
    private McpModule() {
        super();
    }
    
    /**
     * Provides the MCP server name for configuration.
     * In a real app, this might come from BuildConfig, SharedPreferences, or remote config.
     */
    @dagger.Provides()
    @javax.inject.Named(value = "mcp_server_name")
    @org.jetbrains.annotations.NotNull()
    public final java.lang.String provideServerName() {
        return null;
    }
    
    /**
     * Provides the MCP server version for configuration.
     * In a real app, this would typically come from BuildConfig.VERSION_NAME.
     */
    @dagger.Provides()
    @javax.inject.Named(value = "mcp_server_version")
    @org.jetbrains.annotations.NotNull()
    public final java.lang.String provideServerVersion() {
        return null;
    }
    
    /**
     * Provides the MCP Server Manager with custom configuration.
     *
     * This uses initializeWithCustomConfig which automatically starts the server.
     * For more control, you could use initializeManually and handle startup separately.
     */
    @dagger.Provides()
    @javax.inject.Singleton()
    @org.jetbrains.annotations.NotNull()
    public final dev.jasonpearson.mcpandroidsdk.McpServerManager provideMcpServerManager(@dagger.hilt.android.qualifiers.ApplicationContext()
    @org.jetbrains.annotations.NotNull()
    android.content.Context context, @javax.inject.Named(value = "mcp_server_name")
    @org.jetbrains.annotations.NotNull()
    java.lang.String serverName, @javax.inject.Named(value = "mcp_server_version")
    @org.jetbrains.annotations.NotNull()
    java.lang.String serverVersion) {
        return null;
    }
}