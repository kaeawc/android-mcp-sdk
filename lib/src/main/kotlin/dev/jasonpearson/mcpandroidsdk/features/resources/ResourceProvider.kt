package dev.jasonpearson.mcpandroidsdk.features.resources

import android.content.Context
import android.os.Environment
import android.os.FileObserver
import android.util.Log
import androidx.core.net.toUri
import dev.jasonpearson.mcpandroidsdk.models.AndroidResourceContent
import io.modelcontextprotocol.kotlin.sdk.Resource
import io.modelcontextprotocol.kotlin.sdk.ResourceTemplate
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min
import kotlin.math.pow

/** Provider for MCP resources, allowing the server to expose Android-specific data. */
class ResourceProvider(private val context: Context) {

    companion object {
        private const val TAG = "ResourceProvider"
    }

    private val customResources =
        ConcurrentHashMap<String, Pair<Resource, suspend () -> AndroidResourceContent>>()
    private val customResourceTemplates = ConcurrentHashMap<String, ResourceTemplate>()

    private val subscriptionManager = ResourceSubscriptionManager(context)

    // Flow for resource update notifications
    @OptIn(FlowPreview::class)
    val resourceUpdates: Flow<String> =
        subscriptionManager.resourceUpdates.debounce(500) // Debounce notifications

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
        subscriptionManager.subscribeToResource(uri)
    }

    fun unsubscribe(uri: String) {
        subscriptionManager.unsubscribeFromResource(uri)
    }

    fun stopAllObservers() {
        subscriptionManager.stopAllObservers()
    }

    fun restartActiveObservers() {
        subscriptionManager.restartActiveObservers()
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
                description = "Read content of a file from app's private storage or allowed public directories.",
                mimeType = "text/plain",
            )
        )
    }

    private suspend fun readFileResource(fileUri: String): AndroidResourceContent {
        return withContext(Dispatchers.IO) {
            try {
                val requestedFile = getAndVerifyAccessibleFile(fileUri)
                    ?: return@withContext AndroidResourceContent(
                        uri = fileUri,
                        text = "Access denied or invalid file URI for read.",
                    )

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
                    mimeType = context.contentResolver.getType(fileUri.toUri()) ?: "text/plain",
                )
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

    internal fun getAndVerifyAccessibleFile(fileUri: String): File? {
        try {
            val parsedUri = fileUri.toUri()
            if (parsedUri.scheme != "file" || parsedUri.path == null) {
                Log.w(TAG, "Invalid file URI scheme or path: $fileUri")
                return null
            }

            val requestedPath = parsedUri.path!!
            val requestedFile = File(requestedPath)

            // 1. Check app-specific internal storage (filesDir)
            val appFilesDir = context.filesDir
            if (requestedFile.canonicalPath.startsWith(appFilesDir.canonicalPath)) {
                return requestedFile
            }

            // 2. Check app-specific external storage (getExternalFilesDir)
            val appExternalFilesDir = context.getExternalFilesDir(null)
            if (appExternalFilesDir != null && requestedFile.canonicalPath.startsWith(
                    appExternalFilesDir.canonicalPath
                )
            ) {
                return requestedFile
            }

            // 3. Check standard public directories (Downloads, Documents, Pictures, etc.)
            // This requires careful handling, especially on Android 10+ (API 29+)
            // For simplicity, we'll check a few common ones. Real apps might need Storage Access Framework
            // or MediaStore for broader access, or specific permissions.
            val standardPublicDirs = listOfNotNull(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                // Add other relevant public directories as needed
            )

            standardPublicDirs.forEach { publicDir ->
                if (requestedFile.canonicalPath.startsWith(publicDir.canonicalPath)) {
                    // TODO: On Android Q+, direct file path access to public dirs is restricted.
                    // This check might work for legacy storage or if proper permissions are held.
                    // For robust access, MediaStore or SAF would be needed.
                    // For FileObserver, observing these paths might also be problematic without direct access.
                    Log.i(TAG, "File $fileUri is in a standard public directory: ${publicDir.path}")
                    return requestedFile // Assuming direct access for now, needs refinement
                }
            }

            Log.w(
                TAG,
                "Access denied to file path: $fileUri. Not in app-specific or allowed public directories."
            )
            return null
        } catch (e: Exception) {
            Log.e(TAG, "Error verifying file accessibility for $fileUri", e)
            return null
        }
    }
}

/** Manages resource subscriptions and notifications. */
internal class ResourceSubscriptionManager(private val context: Context) {
    companion object {
        private const val TAG = "ResourceSubscriptionManager"
        private const val DEFAULT_POLL_INTERVAL_MS = 15000L // Increased default poll interval
        private const val MAX_POLL_INTERVAL_MS = 60000L // Max 1 minute
        private const val MIN_POLL_INTERVAL_MS = 5000L  // Min 5 seconds
        private const val POLLING_BACKOFF_FACTOR = 1.5
        private const val MAX_FILE_OBSERVERS = 50 // Limit number of active file observers
    }

    private data class ActiveSubscription(
        val uri: String,
        val type: SubscriptionType,
        var fileObserver: FileObserver? = null,
        var lastModifiedOrHash: String = "", // Store hash or last modified string
        var pollingJob: Job? = null,
        var currentPollIntervalMs: Long = DEFAULT_POLL_INTERVAL_MS,
        var pollingErrorCount: Int = 0
    )

    private enum class SubscriptionType { FILE, DYNAMIC }

    private val subscriptions = ConcurrentHashMap<String, ActiveSubscription>()
    private val _resourceUpdates = Channel<String>(Channel.BUFFERED)
    val resourceUpdates: Flow<String> = _resourceUpdates.receiveAsFlow()

    private val coroutineExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e(TAG, "Coroutine exception in ResourceSubscriptionManager", throwable)
    }
    private val coroutineScope =
        CoroutineScope(Dispatchers.IO + SupervisorJob() + coroutineExceptionHandler)

    fun subscribeToResource(uri: String) {
        if (subscriptions.containsKey(uri)) {
            Log.d(TAG, "Already subscribed to resource: $uri")
            return
        }

        Log.i(TAG, "Subscribing to resource: $uri")
        if (uri.startsWith("file://")) {
            if (subscriptions.count { it.value.type == SubscriptionType.FILE } >= MAX_FILE_OBSERVERS) {
                Log.w(
                    TAG,
                    "Max file observers limit reached ($MAX_FILE_OBSERVERS). Cannot subscribe to file: $uri"
                )
                // Optionally notify error or treat as dynamic with less frequent polling
                startDynamicResourcePolling(
                    uri,
                    pollIntervalMs = MAX_POLL_INTERVAL_MS,
                    isFallback = true
                )
                return
            }
            try {
                val filePath = (context.applicationContext as ResourceProviderContainer)
                    .getResourceProvider()
                    .getAndVerifyAccessibleFile(uri)?.absolutePath

                if (filePath != null) {
                    val file = File(filePath)
                    val parentPath =
                        if (file.exists()) filePath else file.parentFile?.absolutePath ?: filePath
                    val fileObserver =
                        createFileObserver(parentPath, uri) // Observe parent if file doesn't exist
                    fileObserver.startWatching()
                    subscriptions[uri] =
                        ActiveSubscription(uri, SubscriptionType.FILE, fileObserver)
                    Log.d(TAG, "Started FileObserver for $uri on path $parentPath")
                } else {
                    Log.e(
                        TAG,
                        "Could not get valid or accessible file path for URI: $uri. Fallback to dynamic polling."
                    )
                    startDynamicResourcePolling(uri, isFallback = true)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create FileObserver for $uri", e)
                startDynamicResourcePolling(uri, isFallback = true)
            }
        } else {
            startDynamicResourcePolling(uri)
        }
    }

    fun unsubscribeFromResource(uri: String) {
        Log.i(TAG, "Unsubscribing from resource: $uri")
        subscriptions.remove(uri)?.let { activeSubscription ->
            activeSubscription.fileObserver?.stopWatching()
            activeSubscription.pollingJob?.cancel()
            Log.d(TAG, "Stopped observer/poller for $uri")
        }
    }

    fun isSubscribed(uri: String): Boolean = subscriptions.containsKey(uri)

    fun getActiveSubscriptions(): List<String> = subscriptions.keys().toList()

    fun stopAllObservers() {
        Log.i(TAG, "Stopping all resource observers and pollers.")
        subscriptions.values.forEach { sub ->
            try {
                sub.fileObserver?.stopWatching()
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping FileObserver for ${sub.uri}", e)
            }
            sub.pollingJob?.cancel()
        }
    }

    fun restartActiveObservers() {
        Log.i(TAG, "Restarting active resource observers and pollers.")
        val currentSubs = subscriptions.toMap()
        // It's safer to stop all first, then re-evaluate and re-subscribe
        // to avoid issues with stale observers if paths changed or became inaccessible.
        stopAllObservers()
        subscriptions.clear()
        currentSubs.keys.forEach { uri ->
            subscribeToResource(uri)
        }
    }

    private fun createFileObserver(filePathToObserve: String, originalUri: String): FileObserver {
        val eventMask = FileObserver.MODIFY or
                FileObserver.DELETE or
                FileObserver.MOVED_FROM or
                FileObserver.MOVED_TO or
                FileObserver.CREATE or
                FileObserver.DELETE_SELF or
                FileObserver.MOVE_SELF

        return object : FileObserver(filePathToObserve, eventMask) {
            override fun onEvent(event: Int, path: String?) {
                Log.d(
                    TAG,
                    "FileObserver event $event for URI $originalUri, observed path $filePathToObserve, event path $path"
                )
                // If observing a directory, path will be relative name of file/dir that changed.
                // We are interested in changes to the originalUri.
                // More specific handling might be needed if originalUri is a directory itself.
                notifyResourceChanged(originalUri)
            }
        }
    }

    private fun startDynamicResourcePolling(
        uri: String,
        pollIntervalMs: Long = DEFAULT_POLL_INTERVAL_MS,
        isFallback: Boolean = false
    ) {
        val existingSub = subscriptions[uri]
        if (existingSub?.type == SubscriptionType.DYNAMIC && existingSub.pollingJob?.isActive == true) {
            Log.d(TAG, "Polling already active for dynamic resource: $uri")
            return
        }

        val initialPollInterval = if (isFallback) MAX_POLL_INTERVAL_MS else pollIntervalMs
        Log.i(TAG, "Starting dynamic polling for resource: $uri every $initialPollInterval ms")
        val currentSub = existingSub ?: ActiveSubscription(
            uri,
            SubscriptionType.DYNAMIC,
            currentPollIntervalMs = initialPollInterval
        )

        currentSub.pollingJob = coroutineScope.launch {
            try {
                val initialContentData = readAndProcessDynamicResource(uri)
                currentSub.lastModifiedOrHash = initialContentData.hashOrTimestamp

                while (isActive) {
                    delay(currentSub.currentPollIntervalMs)
                    val newContentData = readAndProcessDynamicResource(uri)
                    if (newContentData.hashOrTimestamp != currentSub.lastModifiedOrHash) {
                        Log.d(
                            TAG,
                            "Dynamic resource $uri changed (old: ${currentSub.lastModifiedOrHash}, new: ${newContentData.hashOrTimestamp}), notifying."
                        )
                        currentSub.lastModifiedOrHash = newContentData.hashOrTimestamp
                        notifyResourceChanged(uri)
                        // Reset error count and poll interval on successful change detection
                        currentSub.pollingErrorCount = 0
                        currentSub.currentPollIntervalMs = initialPollInterval
                    } else {
                        // No change, reset error count if it was > 0
                        if (currentSub.pollingErrorCount > 0) currentSub.pollingErrorCount = 0
                        currentSub.currentPollIntervalMs =
                            initialPollInterval // Reset to base on no change
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during polling for $uri", e)
                currentSub.pollingErrorCount++
                val backoffDelay =
                    (initialPollInterval * POLLING_BACKOFF_FACTOR.pow(currentSub.pollingErrorCount)).toLong()
                currentSub.currentPollIntervalMs = min(backoffDelay, MAX_POLL_INTERVAL_MS)
                Log.w(
                    TAG,
                    "Polling for $uri failed ${currentSub.pollingErrorCount} times. Next attempt in ${currentSub.currentPollIntervalMs} ms."
                )
                // Loop will continue with increased delay after this iteration's delay completes
            }
        }
        subscriptions[uri] =
            currentSub.copy(type = SubscriptionType.DYNAMIC) // Ensure type is DYNAMIC
    }

    private data class DynamicResourceData(val content: String, val hashOrTimestamp: String)

    private suspend fun readAndProcessDynamicResource(uri: String): DynamicResourceData {
        // Placeholder: Fetch and process dynamic resource content
        // In a real scenario, fetch from network/DB, then hash its content or get a last-modified header.
        val content =
            "Dynamic content for $uri at ${System.currentTimeMillis()}" // Simulate changing content
        return DynamicResourceData(
            content,
            content.hashCode().toString()
        ) // Use hash for change detection
    }

    private fun notifyResourceChanged(uri: String) {
        Log.d(TAG, "Queueing resource change notification for URI: $uri")
        val sent = _resourceUpdates.trySend(uri).isSuccess
        if (!sent) {
            Log.w(
                TAG,
                "Failed to queue resource update for $uri, channel buffer might be full or closed."
            )
        }
    }
}

// Interface to allow ResourceSubscriptionManager to access ResourceProvider's methods if needed
// This avoids a direct circular dependency if ResourceProvider needed to call into manager for complex logic.
// For current getAndVerifyAccessibleFile, it is directly in ResourceProvider to keep it simple.
interface ResourceProviderContainer {
    fun getResourceProvider(): ResourceProvider
}
