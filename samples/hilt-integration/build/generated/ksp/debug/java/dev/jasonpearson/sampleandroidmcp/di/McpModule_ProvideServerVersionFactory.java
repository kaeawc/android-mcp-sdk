package dev.jasonpearson.sampleandroidmcp.di;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

@ScopeMetadata
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
public final class McpModule_ProvideServerVersionFactory implements Factory<String> {
  @Override
  public String get() {
    return provideServerVersion();
  }

  public static McpModule_ProvideServerVersionFactory create() {
    return InstanceHolder.INSTANCE;
  }

  public static String provideServerVersion() {
    return Preconditions.checkNotNullFromProvides(McpModule.INSTANCE.provideServerVersion());
  }

  private static final class InstanceHolder {
    static final McpModule_ProvideServerVersionFactory INSTANCE = new McpModule_ProvideServerVersionFactory();
  }
}
