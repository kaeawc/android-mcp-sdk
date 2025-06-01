package dev.jasonpearson.sampleandroidmcp;

import dagger.MembersInjector;
import dagger.internal.DaggerGenerated;
import dagger.internal.InjectedFieldSignature;
import dagger.internal.Provider;
import dagger.internal.QualifierMetadata;
import dev.jasonpearson.mcpandroidsdk.McpServerManager;
import javax.annotation.processing.Generated;

@QualifierMetadata
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
public final class MainActivity_MembersInjector implements MembersInjector<MainActivity> {
  private final Provider<McpServerManager> mcpServerManagerProvider;

  private final Provider<McpConfiguration> mcpConfigurationProvider;

  public MainActivity_MembersInjector(Provider<McpServerManager> mcpServerManagerProvider,
      Provider<McpConfiguration> mcpConfigurationProvider) {
    this.mcpServerManagerProvider = mcpServerManagerProvider;
    this.mcpConfigurationProvider = mcpConfigurationProvider;
  }

  public static MembersInjector<MainActivity> create(
      Provider<McpServerManager> mcpServerManagerProvider,
      Provider<McpConfiguration> mcpConfigurationProvider) {
    return new MainActivity_MembersInjector(mcpServerManagerProvider, mcpConfigurationProvider);
  }

  @Override
  public void injectMembers(MainActivity instance) {
    injectMcpServerManager(instance, mcpServerManagerProvider.get());
    injectMcpConfiguration(instance, mcpConfigurationProvider.get());
  }

  @InjectedFieldSignature("dev.jasonpearson.sampleandroidmcp.MainActivity.mcpServerManager")
  public static void injectMcpServerManager(MainActivity instance,
      McpServerManager mcpServerManager) {
    instance.mcpServerManager = mcpServerManager;
  }

  @InjectedFieldSignature("dev.jasonpearson.sampleandroidmcp.MainActivity.mcpConfiguration")
  public static void injectMcpConfiguration(MainActivity instance,
      McpConfiguration mcpConfiguration) {
    instance.mcpConfiguration = mcpConfiguration;
  }
}
