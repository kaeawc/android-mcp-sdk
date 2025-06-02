package dev.jasonpearson.androidmcpsdk.debugbridge.tools

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import dev.jasonpearson.androidmcpsdk.core.features.tools.McpToolProvider
import dev.jasonpearson.androidmcpsdk.core.features.tools.addTool
import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.TextContent
import java.util.Locale
import kotlinx.serialization.Serializable

/** Provides application information tools for the debug bridge. */
class ApplicationInfoToolProvider(private val context: Context) {

    companion object {
        private const val TAG = "ApplicationInfoProvider"
    }

    @Serializable data class AppInfoInput(val package_name: String? = null)

    fun registerTools(toolProvider: McpToolProvider) {
        Log.d(TAG, "Registering application info tools")

        // App info tool
        toolProvider.addTool<AppInfoInput>(
            name = "app_info",
            description = "Get information about installed applications",
        ) { input ->
            getAppInfo(input)
        }

        Log.d(TAG, "Application info tools registered")
    }

    private suspend fun getAppInfo(input: AppInfoInput): CallToolResult {
        val packageName = input.package_name ?: context.packageName

        return try {
            val packageManager = context.packageManager
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            val appName = packageManager.getApplicationLabel(appInfo).toString()

            val info = buildString {
                appendLine("Application Information:")
                appendLine("- App Name: $appName")
                appendLine("- Package Name: $packageName")
                appendLine("- Version Name: ${packageInfo.versionName}")

                val versionCode = packageInfo.longVersionCode
                appendLine("- Version Code: $versionCode")

                appendLine("- Target SDK: ${appInfo.targetSdkVersion}")
                appendLine("- Min SDK: ${appInfo.minSdkVersion}")
                appendLine(
                    "- Install Time: ${
                        java.text.SimpleDateFormat(
                            "yyyy-MM-dd HH:mm:ss",
                            Locale.US,
                        ).format(java.util.Date(packageInfo.firstInstallTime))
                    }"
                )
                appendLine(
                    "- Update Time: ${
                        java.text.SimpleDateFormat(
                            "yyyy-MM-dd HH:mm:ss",
                            Locale.US,
                        ).format(java.util.Date(packageInfo.lastUpdateTime))
                    }"
                )
                appendLine("- Data Directory: ${appInfo.dataDir}")
            }

            CallToolResult(content = listOf(TextContent(text = info)), isError = false)
        } catch (e: PackageManager.NameNotFoundException) {
            CallToolResult(
                content = listOf(TextContent(text = "Package not found: $packageName")),
                isError = true,
            )
        }
    }
}
