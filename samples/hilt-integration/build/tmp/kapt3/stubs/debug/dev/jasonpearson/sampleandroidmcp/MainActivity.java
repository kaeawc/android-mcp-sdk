package dev.jasonpearson.sampleandroidmcp;

@dagger.hilt.android.AndroidEntryPoint()
@kotlin.Metadata(mv = {2, 1, 0}, k = 1, xi = 48, d1 = {"\u0000*\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\b\u0007\u0018\u0000 \u00152\u00020\u0001:\u0001\u0015B\u0007\u00a2\u0006\u0004\b\u0002\u0010\u0003J\u0012\u0010\u0010\u001a\u00020\u00112\b\u0010\u0012\u001a\u0004\u0018\u00010\u0013H\u0014J\b\u0010\u0014\u001a\u00020\u0011H\u0002R\u001e\u0010\u0004\u001a\u00020\u00058\u0006@\u0006X\u0087.\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u0006\u0010\u0007\"\u0004\b\b\u0010\tR\u001e\u0010\n\u001a\u00020\u000b8\u0006@\u0006X\u0087.\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\f\u0010\r\"\u0004\b\u000e\u0010\u000f\u00a8\u0006\u0016"}, d2 = {"Ldev/jasonpearson/sampleandroidmcp/MainActivity;", "Landroidx/activity/ComponentActivity;", "<init>", "()V", "mcpServerManager", "Ldev/jasonpearson/mcpandroidsdk/McpServerManager;", "getMcpServerManager", "()Ldev/jasonpearson/mcpandroidsdk/McpServerManager;", "setMcpServerManager", "(Ldev/jasonpearson/mcpandroidsdk/McpServerManager;)V", "mcpConfiguration", "Ldev/jasonpearson/sampleandroidmcp/McpConfiguration;", "getMcpConfiguration", "()Ldev/jasonpearson/sampleandroidmcp/McpConfiguration;", "setMcpConfiguration", "(Ldev/jasonpearson/sampleandroidmcp/McpConfiguration;)V", "onCreate", "", "savedInstanceState", "Landroid/os/Bundle;", "configureHiltMcp", "Companion", "hilt-integration_debug"})
public final class MainActivity extends androidx.activity.ComponentActivity {
    @org.jetbrains.annotations.NotNull()
    private static final java.lang.String TAG = "HiltMcpActivity";
    @javax.inject.Inject()
    public dev.jasonpearson.mcpandroidsdk.McpServerManager mcpServerManager;
    @javax.inject.Inject()
    public dev.jasonpearson.sampleandroidmcp.McpConfiguration mcpConfiguration;
    @org.jetbrains.annotations.NotNull()
    public static final dev.jasonpearson.sampleandroidmcp.MainActivity.Companion Companion = null;
    
    public MainActivity() {
        super(0);
    }
    
    @org.jetbrains.annotations.NotNull()
    public final dev.jasonpearson.mcpandroidsdk.McpServerManager getMcpServerManager() {
        return null;
    }
    
    public final void setMcpServerManager(@org.jetbrains.annotations.NotNull()
    dev.jasonpearson.mcpandroidsdk.McpServerManager p0) {
    }
    
    @org.jetbrains.annotations.NotNull()
    public final dev.jasonpearson.sampleandroidmcp.McpConfiguration getMcpConfiguration() {
        return null;
    }
    
    public final void setMcpConfiguration(@org.jetbrains.annotations.NotNull()
    dev.jasonpearson.sampleandroidmcp.McpConfiguration p0) {
    }
    
    @java.lang.Override()
    protected void onCreate(@org.jetbrains.annotations.Nullable()
    android.os.Bundle savedInstanceState) {
    }
    
    private final void configureHiltMcp() {
    }
    
    @kotlin.Metadata(mv = {2, 1, 0}, k = 1, xi = 48, d1 = {"\u0000\u0012\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0000\b\u0086\u0003\u0018\u00002\u00020\u0001B\t\b\u0002\u00a2\u0006\u0004\b\u0002\u0010\u0003R\u000e\u0010\u0004\u001a\u00020\u0005X\u0082T\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u0006"}, d2 = {"Ldev/jasonpearson/sampleandroidmcp/MainActivity$Companion;", "", "<init>", "()V", "TAG", "", "hilt-integration_debug"})
    public static final class Companion {
        
        private Companion() {
            super();
        }
    }
}