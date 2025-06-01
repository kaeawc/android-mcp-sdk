package dev.jasonpearson.sampleandroidmcp;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Provider;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import dev.jasonpearson.mcpandroidsdk.McpServerManager;
import javax.annotation.processing.Generated;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata("javax.inject.Named")
@DaggerGenerated
@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes",
    "KotlinInternal",
    "KotlinInternalInJava",
    "cast",
    "deprecation",
    "nullness:initialization.field.uninitialized"
})
public final class McpConfiguration_Factory implements Factory<McpConfiguration> {
  private final Provider<McpServerManager> mcpServerManagerProvider;

  private final Provider<String> serverNameProvider;

  private final Provider<String> serverVersionProvider;

  public McpConfiguration_Factory(Provider<McpServerManager> mcpServerManagerProvider,
      Provider<String> serverNameProvider, Provider<String> serverVersionProvider) {
    this.mcpServerManagerProvider = mcpServerManagerProvider;
    this.serverNameProvider = serverNameProvider;
    this.serverVersionProvider = serverVersionProvider;
  }

  @Override
  public McpConfiguration get() {
    return newInstance(mcpServerManagerProvider.get(), serverNameProvider.get(), serverVersionProvider.get());
  }

  public static McpConfiguration_Factory create(Provider<McpServerManager> mcpServerManagerProvider,
      Provider<String> serverNameProvider, Provider<String> serverVersionProvider) {
    return new McpConfiguration_Factory(mcpServerManagerProvider, serverNameProvider, serverVersionProvider);
  }

  public static McpConfiguration newInstance(McpServerManager mcpServerManager, String serverName,
      String serverVersion) {
    return new McpConfiguration(mcpServerManager, serverName, serverVersion);
  }
}
