dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()

        // R8 repo for R8/D8 releases
        exclusiveContent {
            forRepository {
                maven("https://storage.googleapis.com/r8-releases/raw") { name = "R8-releases" }
            }
            filter { includeModule("com.android.tools", "r8") }
        }
    }
}

rootProject.name = "Sample-Android-MCP"

include(":core")

include(":debug-bridge")

// Sample applications
include(":samples:simple")

include(
    ":samples:hilt-integration"
)

// enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
