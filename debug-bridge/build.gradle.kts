plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "dev.jasonpearson.androidmcpsdk.debugbridge"
    compileSdk = 35

    defaultConfig {
        minSdk = 29
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        debug { isMinifyEnabled = false }
        release {
            // Intentionally crash on release builds for security
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

    testOptions {
        unitTests {
            isReturnDefaultValues = true
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    // MCP Kotlin SDK
    implementation(libs.mcp.kotlin.sdk)

    // Kotlin
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    // Android
    implementation(libs.androidx.core.ktx)
    //    implementation(libs.androidx.security.crypto)

    // Network
    implementation(libs.okhttp)

    // Core module
    implementation(project(":core"))

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.core.ktx)
    testImplementation(libs.androidx.junit.ktx)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
