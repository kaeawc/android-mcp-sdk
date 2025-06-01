plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "dev.jasonpearson.mcpandroidsdk"
    compileSdk = 36

    defaultConfig {
        minSdk = 29

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
    compileOptions {
        val javaVer = JavaVersion.toVersion(libs.versions.javaSource.get())
        sourceCompatibility = javaVer
        targetCompatibility = javaVer
    }
    kotlinOptions { jvmTarget = libs.versions.javaSource.get() }

    packaging {
        resources {
            excludes += "META-INF/INDEX.LIST"
            excludes += "META-INF/io.netty.versions.properties"
        }
    }

    testOptions {
        unitTests {
            isReturnDefaultValues = true
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    // Remove debug-bridge dependency to fix circular dependency - core should not depend on
    // debug-bridge
    // implementation(project(":debug-bridge"))

    // MCP Kotlin SDK
    implementation(libs.mcp.kotlin.sdk)
    implementation(libs.kotlin.sdk.jvm)

    // Core Android dependencies
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.startup.runtime)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.process)

    // Kotlin coroutines and serialization
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    // Transport dependencies
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.websockets)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.okhttp)
    implementation(libs.okhttp.sse)

    testImplementation(libs.junit) // JUnit 4
    testImplementation(libs.kotlinx.coroutines.test) // For testing coroutines
    testImplementation(libs.robolectric) // Android framework mocks for unit tests
    testImplementation(libs.core.ktx) // For ApplicationProvider, etc. (using specific version)
    testImplementation(libs.androidx.junit.ktx) // For AndroidJUnit4 runner (using specific version)
    testImplementation(libs.mockk) // MockK for Kotlin

    androidTestImplementation(libs.androidx.junit) // Already present
    androidTestImplementation(libs.androidx.espresso.core) // Already present
}
