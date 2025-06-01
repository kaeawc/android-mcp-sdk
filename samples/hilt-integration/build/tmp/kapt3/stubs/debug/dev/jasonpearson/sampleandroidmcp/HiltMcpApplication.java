package dev.jasonpearson.sampleandroidmcp;

/**
 * Sample Application demonstrating Hilt DI integration with Android MCP SDK.
 *
 * This example shows how to properly integrate MCP with Hilt dependency injection
 * for production Android applications.
 *
 * Key features:
 * - Automatic initialization disabled in manifest
 * - MCP initialization handled by Hilt DI
 * - Clean separation of concerns
 * - Production-ready patterns
 */
@dagger.hilt.android.HiltAndroidApp()
@kotlin.Metadata(mv = {2, 1, 0}, k = 1, xi = 48, d1 = {"\u0000\u0014\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u0002\n\u0002\b\u0002\b\u0007\u0018\u0000 \u00062\u00020\u0001:\u0001\u0006B\u0007\u00a2\u0006\u0004\b\u0002\u0010\u0003J\b\u0010\u0004\u001a\u00020\u0005H\u0016\u00a8\u0006\u0007"}, d2 = {"Ldev/jasonpearson/sampleandroidmcp/HiltMcpApplication;", "Landroid/app/Application;", "<init>", "()V", "onCreate", "", "Companion", "hilt-integration_debug"})
public final class HiltMcpApplication extends android.app.Application {
    @org.jetbrains.annotations.NotNull()
    private static final java.lang.String TAG = "HiltMcpApplication";
    @org.jetbrains.annotations.NotNull()
    public static final dev.jasonpearson.sampleandroidmcp.HiltMcpApplication.Companion Companion = null;
    
    public HiltMcpApplication() {
        super();
    }
    
    @java.lang.Override()
    public void onCreate() {
    }
    
    @kotlin.Metadata(mv = {2, 1, 0}, k = 1, xi = 48, d1 = {"\u0000\u0012\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0000\b\u0086\u0003\u0018\u00002\u00020\u0001B\t\b\u0002\u00a2\u0006\u0004\b\u0002\u0010\u0003R\u000e\u0010\u0004\u001a\u00020\u0005X\u0082T\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u0006"}, d2 = {"Ldev/jasonpearson/sampleandroidmcp/HiltMcpApplication$Companion;", "", "<init>", "()V", "TAG", "", "hilt-integration_debug"})
    public static final class Companion {
        
        private Companion() {
            super();
        }
    }
}