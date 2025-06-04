package dev.jasonpearson.androidmcpsdk.core.features.resources

import android.content.Context
import android.util.Log
import dev.jasonpearson.androidmcpsdk.core.models.AndroidResourceContent
import io.modelcontextprotocol.kotlin.sdk.Resource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Provides SharedPreferences as MCP resources
 */
class SharedPreferencesResourceProvider(private val context: Context) {

    companion object {
        private const val TAG = "SharedPreferencesResourceProvider"
    }

    /**
     * Create built-in SharedPreferences resources
     */
    fun createSharedPreferencesResources(): List<Resource> {
        return listOf(
            Resource(
                uri = "android://preferences/default",
                name = "Default SharedPreferences",
                description = "Application's default SharedPreferences file",
                mimeType = "application/json"
            ),

            Resource(
                uri = "android://preferences/all",
                name = "All SharedPreferences Files",
                description = "List of all SharedPreferences files in the application",
                mimeType = "application/json"
            )
        )
    }

    /**
     * Read SharedPreferences resource content
     */
    suspend fun readSharedPreferencesResource(uri: String): AndroidResourceContent =
        withContext(Dispatchers.IO) {
            return@withContext when {
                uri == "android://preferences/default" -> readDefaultPreferences()
                uri == "android://preferences/all" -> readAllPreferencesFiles()
                uri.startsWith("android://preferences/") -> readSpecificPreferencesFile(uri)
                else -> AndroidResourceContent(
                    uri = uri,
                    text = "Unknown preferences resource: $uri"
                )
            }
        }

    private suspend fun readDefaultPreferences(): AndroidResourceContent =
        withContext(Dispatchers.IO) {
            try {
                val prefs = context.getSharedPreferences("default_prefs", Context.MODE_PRIVATE)
                val prefsMap = prefs.all

                val content = buildString {
                    appendLine("Default SharedPreferences:")
                    appendLine("Keys: ${prefsMap.size}")
                    appendLine()

                    if (prefsMap.isEmpty()) {
                        appendLine("No preferences found")
                    } else {
                        prefsMap.forEach { (key, value) ->
                            val type = when (value) {
                                is String -> "String"
                                is Int -> "Int"
                                is Boolean -> "Boolean"
                                is Float -> "Float"
                                is Long -> "Long"
                                is Set<*> -> "StringSet"
                                else -> "Unknown"
                            }
                            appendLine("$key: $value ($type)")
                        }
                    }
                }

                AndroidResourceContent(
                    uri = "android://preferences/default",
                    text = content,
                    mimeType = "text/plain"
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error reading default preferences", e)
                AndroidResourceContent(
                    uri = "android://preferences/default",
                    text = "Error reading default preferences: ${e.message}"
                )
            }
        }

    private suspend fun readAllPreferencesFiles(): AndroidResourceContent =
        withContext(Dispatchers.IO) {
            try {
                val prefsFiles = mutableListOf<Map<String, Any>>()

                // Get shared_prefs directory
                val sharedPrefsDir = File(context.applicationInfo.dataDir, "shared_prefs")

                if (sharedPrefsDir.exists()) {
                    sharedPrefsDir.listFiles { _, name -> name.endsWith(".xml") }?.forEach { file ->
                        val fileName = file.nameWithoutExtension
                        try {
                            val prefs = context.getSharedPreferences(fileName, Context.MODE_PRIVATE)
                            prefsFiles.add(
                                mapOf(
                                    "fileName" to fileName,
                                    "path" to file.absolutePath,
                                    "size" to file.length(),
                                    "lastModified" to file.lastModified(),
                                    "keyCount" to prefs.all.size
                                )
                            )
                        } catch (e: Exception) {
                            Log.w(TAG, "Error reading preferences file: $fileName", e)
                        }
                    }
                }

                val jsonContent = Json.encodeToString(
                    mapOf(
                        "preferencesFiles" to prefsFiles,
                        "totalFiles" to prefsFiles.size
                    )
                )

                AndroidResourceContent(
                    uri = "android://preferences/all",
                    text = jsonContent,
                    mimeType = "application/json"
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error reading preferences files list", e)
                AndroidResourceContent(
                    uri = "android://preferences/all",
                    text = "Error reading preferences files: ${e.message}"
                )
            }
        }

    private suspend fun readSpecificPreferencesFile(uri: String): AndroidResourceContent =
        withContext(Dispatchers.IO) {
            try {
                // Extract file name from URI: android://preferences/filename
                val fileName = uri.substringAfterLast("/")

                val prefs = context.getSharedPreferences(fileName, Context.MODE_PRIVATE)
                val prefsMap = prefs.all

                val preferencesData = mutableMapOf<String, Any>()
                prefsMap.forEach { (key, value) ->
                    val valueData = mapOf(
                        "value" to (value?.toString() ?: "null"),
                        "type" to when (value) {
                            is String -> "String"
                            is Int -> "Int"
                            is Boolean -> "Boolean"
                            is Float -> "Float"
                            is Long -> "Long"
                            is Set<*> -> "StringSet"
                            else -> "Unknown"
                        }
                    )
                    preferencesData[key] = valueData
                }

                val jsonContent = Json.encodeToString(
                    mapOf(
                        "fileName" to fileName,
                        "preferences" to preferencesData,
                        "keyCount" to preferencesData.size
                    )
                )

                AndroidResourceContent(
                    uri = uri,
                    text = jsonContent,
                    mimeType = "application/json"
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error reading specific preferences file", e)
                AndroidResourceContent(
                    uri = uri,
                    text = "Error reading preferences file: ${e.message}"
                )
            }
        }
}