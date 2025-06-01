package dev.jasonpearson.mcpandroidsdk.features.resources

import android.content.Context
import android.util.Log
import androidx.core.net.toUri
import dev.jasonpearson.mcpandroidsdk.models.AndroidResourceContent
import io.modelcontextprotocol.kotlin.sdk.Resource
import io.modelcontextprotocol.kotlin.sdk.ResourceTemplate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/** Provider for MCP resources, allowing the server to expose Android-specific data. */
class ResourceProvider(private val context: Context) {

    companion object {
        private const val TAG = "ResourceProvider"
    }

    private val customResources =
        ConcurrentHashMap<String, Pair<Resource, suspend () -> AndroidResourceContent>>()
    private val customResourceTemplates = ConcurrentHashMap<String, ResourceTemplate>()
    private val subscriptions = ConcurrentHashMap<String, Boolean>()

    fun getAllResources(): List<Resource> {
        val builtIn = createBuiltInResources()
        val custom = customResources.values.map { it.first }
        return builtIn + custom
    }

    fun getAllResourceTemplates(): List<ResourceTemplate> {
        val builtIn = createBuiltInResourceTemplates()
        val custom = customResourceTemplates.values.toList()
        return builtIn + custom
    }

    suspend fun readResource(uri: String): AndroidResourceContent {
        Log.d(TAG, "Reading resource: $uri")
        customResources[uri]?.let {
            return it.second()
        }

        // Handle built-in resources or templates if any
        // Example: file URI
        if (uri.startsWith("file://")) {
            return readFileResource(uri)
        }

        return AndroidResourceContent(uri = uri, text = "Resource not found: $uri")
    }

    fun addResource(resource: Resource, contentProvider: suspend () -> AndroidResourceContent) {
        customResources[resource.uri] = Pair(resource, contentProvider)
        Log.i(TAG, "Added custom resource: ${resource.uri}")
    }

    fun addResourceTemplate(template: ResourceTemplate) {
        customResourceTemplates[template.uriTemplate] = template
        Log.i(TAG, "Added custom resource template: ${template.uriTemplate}")
    }

    fun subscribe(uri: String) {
        subscriptions[uri] = true
        Log.d(TAG, "Subscribed to resource: $uri")
        // TODO: Implement actual subscription logic (e.g., file observers)
    }

    fun unsubscribe(uri: String) {
        subscriptions.remove(uri)
        Log.d(TAG, "Unsubscribed from resource: $uri")
    }

    private fun createBuiltInResources(): List<Resource> {
        return listOf(
            Resource(
                uri = "android://app/info",
                name = "Application Information",
                description = "Basic information about the host application.",
                mimeType = "text/plain",
            ),
            Resource(
                uri = "android://device/info",
                name = "Device Information",
                description = "Basic information about the Android device.",
                mimeType = "text/plain",
            ),
        )
    }

    private fun createBuiltInResourceTemplates(): List<ResourceTemplate> {
        return listOf(
            ResourceTemplate(
                uriTemplate = "file://{path}",
                name = "File Content",
                description = "Read content of a file from app's private storage.",
                mimeType = "text/plain",
            )
        )
    }

    private suspend fun readFileResource(fileUri: String): AndroidResourceContent {
        return withContext(Dispatchers.IO) {
            try {
                val parsedUri = fileUri.toUri()
                if (parsedUri.scheme != "file" || parsedUri.path == null) {
                    return@withContext AndroidResourceContent(
                        uri = fileUri,
                        text = "Invalid file URI scheme or path.",
                    )
                }

                // Restrict to app's internal files directory for security
                val appFilesDir = context.filesDir
                val requestedFile = File(appFilesDir, parsedUri.path!!)

                // Security check: Ensure the path is within the app's filesDir
                if (!requestedFile.canonicalPath.startsWith(appFilesDir.canonicalPath)) {
                    Log.w(TAG, "Attempt to access file outside app's private directory: $fileUri")
                    return@withContext AndroidResourceContent(
                        uri = fileUri,
                        text = "Access denied to file path.",
                    )
                }

                if (!requestedFile.exists() || !requestedFile.isFile) {
                    return@withContext AndroidResourceContent(
                        uri = fileUri,
                        text = "File not found or is not a regular file: ${requestedFile.path}",
                    )
                }

                val content = requestedFile.readText()
                AndroidResourceContent(
                    uri = fileUri,
                    text = content,
                    mimeType = "text/plain",
                ) // Infer mime type for real use cases
            } catch (e: IOException) {
                Log.e(TAG, "Error reading file resource $fileUri", e)
                AndroidResourceContent(uri = fileUri, text = "Error reading file: ${e.message}")
            } catch (e: SecurityException) {
                Log.e(TAG, "Security error reading file resource $fileUri", e)
                AndroidResourceContent(
                    uri = fileUri,
                    text = "Security error reading file: ${e.message}",
                )
            }
        }
    }
}
