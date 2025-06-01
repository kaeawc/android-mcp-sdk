package dev.jasonpearson.sampleandroidmcp.di;

import android.content.Context;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.Provider;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import dev.jasonpearson.mcpandroidsdk.McpServerManager;
import javax.annotation.processing.Generated;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata({
    "dagger.hilt.android.qualifiers.ApplicationContext",
    "javax.inject.Named"
})
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
public final class McpModule_ProvideMcpServerManagerFactory implements Factory<McpServerManager> {
  private final Provider<Context> contextProvider;

  private final Provider<String> serverNameProvider;

  private final Provider<String> serverVersionProvider;

  public McpModule_ProvideMcpServerManagerFactory(Provider<Context> contextProvider,
      Provider<String> serverNameProvider, Provider<String> serverVersionProvider) {
    this.contextProvider = contextProvider;
    this.serverNameProvider = serverNameProvider;
    this.serverVersionProvider = serverVersionProvider;
  }

  @Override
  public McpServerManager get() {
    return provideMcpServerManager(contextProvider.get(), serverNameProvider.get(), serverVersionProvider.get());
  }

  public static McpModule_ProvideMcpServerManagerFactory create(Provider<Context> contextProvider,
      Provider<String> serverNameProvider, Provider<String> serverVersionProvider) {
    return new McpModule_ProvideMcpServerManagerFactory(contextProvider, serverNameProvider, serverVersionProvider);
  }

  public static McpServerManager provideMcpServerManager(Context context, String serverName,
      String serverVersion) {
    return Preconditions.checkNotNullFromProvides(McpModule.INSTANCE.provideMcpServerManager(context, serverName, serverVersion));
  }
}
