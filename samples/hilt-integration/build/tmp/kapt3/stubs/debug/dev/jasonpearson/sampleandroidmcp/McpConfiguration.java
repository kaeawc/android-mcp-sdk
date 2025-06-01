package dev.jasonpearson.sampleandroidmcp;

/**
 * Configuration class for MCP behavior in the Hilt sample app.
 *
 * This demonstrates how to manage MCP configuration and behavior
 * through dependency injection, making it easy to test and modify.
 */
@javax.inject.Singleton()
@kotlin.Metadata(mv = {2, 1, 0}, k = 1, xi = 48, d1 = {"\u0000(\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0004\n\u0002\u0010\u0002\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0002\b\u0002\b\u0007\u0018\u0000 \u000e2\u00020\u0001:\u0001\u000eB%\b\u0007\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\b\b\u0001\u0010\u0004\u001a\u00020\u0005\u0012\b\b\u0001\u0010\u0006\u001a\u00020\u0005\u00a2\u0006\u0004\b\u0007\u0010\bJ\u0006\u0010\t\u001a\u00020\nJ\u0006\u0010\u000b\u001a\u00020\u0005J\u0006\u0010\f\u001a\u00020\rR\u000e\u0010\u0002\u001a\u00020\u0003X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0004\u001a\u00020\u0005X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0006\u001a\u00020\u0005X\u0082\u0004\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u000f"}, d2 = {"Ldev/jasonpearson/sampleandroidmcp/McpConfiguration;", "", "mcpServerManager", "Ldev/jasonpearson/mcpandroidsdk/McpServerManager;", "serverName", "", "serverVersion", "<init>", "(Ldev/jasonpearson/mcpandroidsdk/McpServerManager;Ljava/lang/String;Ljava/lang/String;)V", "configureCustomTools", "", "getServerInfo", "isServerReady", "", "Companion", "hilt-integration_debug"})
public final class McpConfiguration {
    @org.jetbrains.annotations.NotNull()
    private final dev.jasonpearson.mcpandroidsdk.McpServerManager mcpServerManager = null;
    @org.jetbrains.annotations.NotNull()
    private final java.lang.String serverName = null;
    @org.jetbrains.annotations.NotNull()
    private final java.lang.String serverVersion = null;
    @org.jetbrains.annotations.NotNull()
    private static final java.lang.String TAG = "McpConfiguration";
    @org.jetbrains.annotations.NotNull()
    public static final dev.jasonpearson.sampleandroidmcp.McpConfiguration.Companion Companion = null;
    
    @javax.inject.Inject()
    public McpConfiguration(@org.jetbrains.annotations.NotNull()
    dev.jasonpearson.mcpandroidsdk.McpServerManager mcpServerManager, @javax.inject.Named(value = "mcp_server_name")
    @org.jetbrains.annotations.NotNull()
    java.lang.String serverName, @javax.inject.Named(value = "mcp_server_version")
    @org.jetbrains.annotations.NotNull()
    java.lang.String serverVersion) {
        super();
    }
    
    /**
     * Configures custom tools specific to the Hilt sample app.
     * This method can be called from activities or other components.
     */
    public final void configureCustomTools() {
    }
    
    /**
     * Gets server information formatted for display.
     */
    @org.jetbrains.annotations.NotNull()
    public final java.lang.String getServerInfo() {
        return null;
    }
    
    /**
     * Checks if the MCP server is ready and properly configured.
     */
    public final boolean isServerReady() {
        return false;
    }
    
    @kotlin.Metadata(mv = {2, 1, 0}, k = 1, xi = 48, d1 = {"\u0000\u0012\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0000\b\u0086\u0003\u0018\u00002\u00020\u0001B\t\b\u0002\u00a2\u0006\u0004\b\u0002\u0010\u0003R\u000e\u0010\u0004\u001a\u00020\u0005X\u0082T\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u0006"}, d2 = {"Ldev/jasonpearson/sampleandroidmcp/McpConfiguration$Companion;", "", "<init>", "()V", "TAG", "", "hilt-integration_debug"})
    public static final class Companion {
        
        private Companion() {
            super();
        }
    }
}