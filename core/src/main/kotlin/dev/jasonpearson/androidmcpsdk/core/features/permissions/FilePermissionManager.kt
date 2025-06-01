package dev.jasonpearson.androidmcpsdk.core.features.permissions

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import androidx.core.net.toUri

/**
 * Manages file access permissions and storage scopes for the Android MCP SDK.
 * Handles Android 10+ scoped storage compliance and runtime permissions.
 */
class FilePermissionManager(private val context: Context) {

    enum class StorageScope {
        APP_INTERNAL,           // Always accessible - no permissions needed
        APP_EXTERNAL,           // App-specific external storage - no permissions needed
        MEDIA_IMAGES,           // Requires READ_MEDIA_IMAGES (API 33+) or READ_EXTERNAL_STORAGE
        MEDIA_VIDEO,            // Requires READ_MEDIA_VIDEO (API 33+) or READ_EXTERNAL_STORAGE  
        MEDIA_AUDIO,            // Requires READ_MEDIA_AUDIO (API 33+) or READ_EXTERNAL_STORAGE
        EXTERNAL_STORAGE,       // Requires MANAGE_EXTERNAL_STORAGE (special permission)
        USER_SELECTED           // Via Storage Access Framework - no permissions needed
    }

    data class FileAccessResult(
        val canAccess: Boolean,
        val scope: StorageScope,
        val requiresPermission: Boolean,
        val missingPermissions: List<String> = emptyList(),
        val errorMessage: String? = null
    )

    data class PermissionResult(
        val granted: Boolean,
        val permissions: Map<String, Boolean>,
        val shouldShowRationale: Boolean = false
    )

    data class ScopedDirectory(
        val path: String,
        val scope: StorageScope,
        val isAccessible: Boolean,
        val description: String
    )

    /**
     * Checks if the app can access a file at the given URI or path.
     */
    suspend fun checkFileAccess(uri: String): FileAccessResult = withContext(Dispatchers.IO) {
        try {
            when {
                uri.startsWith("content://") -> checkContentUriAccess(uri.toUri())
                uri.startsWith("file://") -> checkFilePathAccess(uri.removePrefix("file://"))
                else -> checkFilePathAccess(uri)
            }
        } catch (e: Exception) {
            FileAccessResult(
                canAccess = false,
                scope = StorageScope.EXTERNAL_STORAGE,
                requiresPermission = true,
                errorMessage = "Error checking file access: ${e.message}"
            )
        }
    }

    /**
     * Requests file permissions for a specific storage scope.
     */
    suspend fun requestFilePermissions(scope: StorageScope): PermissionResult =
        withContext(Dispatchers.Main) {
            val requiredPermissions = getRequiredPermissions(scope)

            if (requiredPermissions.isEmpty()) {
                return@withContext PermissionResult(granted = true, permissions = emptyMap())
            }

            val permissionStates = requiredPermissions.associateWith { permission ->
                ContextCompat.checkSelfPermission(
                    context,
                    permission
                ) == PackageManager.PERMISSION_GRANTED
            }

            val allGranted = permissionStates.values.all { it }

            PermissionResult(
                granted = allGranted,
                permissions = permissionStates
            )
        }

    /**
     * Gets all accessible directories categorized by storage scope.
     */
    fun getScopedDirectories(): List<ScopedDirectory> {
        val directories = mutableListOf<ScopedDirectory>()

        // App internal storage - always accessible
        directories.add(
            ScopedDirectory(
                path = context.filesDir.absolutePath,
                scope = StorageScope.APP_INTERNAL,
                isAccessible = true,
                description = "App internal files directory"
            )
        )

        directories.add(
            ScopedDirectory(
                path = context.cacheDir.absolutePath,
                scope = StorageScope.APP_INTERNAL,
                isAccessible = true,
                description = "App internal cache directory"
            )
        )

        // App external storage - accessible without permissions
        context.getExternalFilesDir(null)?.let { externalFiles ->
            directories.add(
                ScopedDirectory(
                    path = externalFiles.absolutePath,
                    scope = StorageScope.APP_EXTERNAL,
                    isAccessible = true,
                    description = "App external files directory"
                )
            )
        }

        context.externalCacheDir?.let { externalCache ->
            directories.add(
                ScopedDirectory(
                    path = externalCache.absolutePath,
                    scope = StorageScope.APP_EXTERNAL,
                    isAccessible = true,
                    description = "App external cache directory"
                )
            )
        }

        // External storage directories - require permissions
        if (Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED) {
            val hasExternalPermission = checkExternalStoragePermission()

            directories.add(
                ScopedDirectory(
                    path = Environment.getExternalStorageDirectory().absolutePath,
                    scope = StorageScope.EXTERNAL_STORAGE,
                    isAccessible = hasExternalPermission,
                    description = "External storage root (requires MANAGE_EXTERNAL_STORAGE)"
                )
            )

            // Media directories
            val hasMediaPermission = checkMediaPermissions()
            arrayOf(
                Environment.DIRECTORY_PICTURES to StorageScope.MEDIA_IMAGES,
                Environment.DIRECTORY_MOVIES to StorageScope.MEDIA_VIDEO,
                Environment.DIRECTORY_MUSIC to StorageScope.MEDIA_AUDIO
            ).forEach { (dirType, scope) ->
                val dir = Environment.getExternalStoragePublicDirectory(dirType)
                directories.add(
                    ScopedDirectory(
                        path = dir.absolutePath,
                        scope = scope,
                        isAccessible = hasMediaPermission,
                        description = "Public ${dirType.lowercase()} directory"
                    )
                )
            }
        }

        return directories
    }

    /**
     * Creates an intent for Storage Access Framework document picker.
     */
    fun createDocumentPickerIntent(mimeTypes: Array<String> = arrayOf("*/*")): Intent {
        return Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = if (mimeTypes.size == 1) mimeTypes[0] else "*/*"
            if (mimeTypes.size > 1) {
                putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes)
            }
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
    }

    /**
     * Creates an intent for Storage Access Framework directory picker.
     */
    fun createDirectoryPickerIntent(): Intent {
        return Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
    }

    /**
     * Validates a document URI from Storage Access Framework.
     */
    suspend fun validateDocumentUri(uri: Uri): FileAccessResult = withContext(Dispatchers.IO) {
        try {
            val documentFile = DocumentFile.fromSingleUri(context, uri)
                ?: return@withContext FileAccessResult(
                    canAccess = false,
                    scope = StorageScope.USER_SELECTED,
                    requiresPermission = false,
                    errorMessage = "Invalid document URI"
                )

            FileAccessResult(
                canAccess = documentFile.exists(),
                scope = StorageScope.USER_SELECTED,
                requiresPermission = false
            )
        } catch (e: Exception) {
            FileAccessResult(
                canAccess = false,
                scope = StorageScope.USER_SELECTED,
                requiresPermission = false,
                errorMessage = "Error validating document URI: ${e.message}"
            )
        }
    }

    private fun checkContentUriAccess(uri: Uri): FileAccessResult {
        return when {
            uri.authority == MediaStore.AUTHORITY -> {
                val hasPermission = checkMediaPermissions()
                FileAccessResult(
                    canAccess = hasPermission,
                    scope = StorageScope.MEDIA_IMAGES, // Could be more specific based on URI
                    requiresPermission = !hasPermission,
                    missingPermissions = if (!hasPermission) getRequiredPermissions(StorageScope.MEDIA_IMAGES) else emptyList()
                )
            }

            uri.authority?.contains("documents") == true -> {
                // Storage Access Framework URIs
                FileAccessResult(
                    canAccess = true,
                    scope = StorageScope.USER_SELECTED,
                    requiresPermission = false
                )
            }

            else -> {
                FileAccessResult(
                    canAccess = false,
                    scope = StorageScope.EXTERNAL_STORAGE,
                    requiresPermission = true,
                    errorMessage = "Unknown content URI authority: ${uri.authority}"
                )
            }
        }
    }

    private fun checkFilePathAccess(path: String): FileAccessResult {
        return when {
            path.startsWith(context.filesDir.absolutePath) ||
                    path.startsWith(context.cacheDir.absolutePath) -> {
                FileAccessResult(
                    canAccess = true,
                    scope = StorageScope.APP_INTERNAL,
                    requiresPermission = false
                )
            }

            context.getExternalFilesDir(null)?.let { path.startsWith(it.absolutePath) } == true ||
                    context.externalCacheDir?.let { path.startsWith(it.absolutePath) } == true -> {
                FileAccessResult(
                    canAccess = true,
                    scope = StorageScope.APP_EXTERNAL,
                    requiresPermission = false
                )
            }

            path.contains("/Android/data/${context.packageName}/") -> {
                FileAccessResult(
                    canAccess = true,
                    scope = StorageScope.APP_EXTERNAL,
                    requiresPermission = false
                )
            }

            isMediaFile(path) -> {
                val hasPermission = checkMediaPermissions()
                val scope = getMediaScope(path)
                FileAccessResult(
                    canAccess = hasPermission,
                    scope = scope,
                    requiresPermission = !hasPermission,
                    missingPermissions = if (!hasPermission) getRequiredPermissions(scope) else emptyList()
                )
            }

            else -> {
                val hasPermission = checkExternalStoragePermission()
                FileAccessResult(
                    canAccess = hasPermission,
                    scope = StorageScope.EXTERNAL_STORAGE,
                    requiresPermission = !hasPermission,
                    missingPermissions = if (!hasPermission) getRequiredPermissions(StorageScope.EXTERNAL_STORAGE) else emptyList()
                )
            }
        }
    }

    private fun getRequiredPermissions(scope: StorageScope): List<String> {
        return when (scope) {
            StorageScope.APP_INTERNAL,
            StorageScope.APP_EXTERNAL,
            StorageScope.USER_SELECTED -> emptyList()

            StorageScope.MEDIA_IMAGES -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    listOf(Manifest.permission.READ_MEDIA_IMAGES)
                } else {
                    listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
                }
            }

            StorageScope.MEDIA_VIDEO -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    listOf(Manifest.permission.READ_MEDIA_VIDEO)
                } else {
                    listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
                }
            }

            StorageScope.MEDIA_AUDIO -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    listOf(Manifest.permission.READ_MEDIA_AUDIO)
                } else {
                    listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
                }
            }

            StorageScope.EXTERNAL_STORAGE -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    listOf(Manifest.permission.MANAGE_EXTERNAL_STORAGE)
                } else {
                    listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
                }
            }
        }
    }

    private fun checkExternalStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun checkMediaPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Check individual media permissions on Android 13+
            listOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO
            ).any { permission ->
                ContextCompat.checkSelfPermission(
                    context,
                    permission
                ) == PackageManager.PERMISSION_GRANTED
            }
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun isMediaFile(path: String): Boolean {
        val mediaExtensions = setOf(
            "jpg", "jpeg", "png", "gif", "webp", "bmp",
            "mp4", "avi", "mkv", "mov", "wmv", "flv",
            "mp3", "wav", "flac", "aac", "ogg", "m4a"
        )
        val extension = File(path).extension.lowercase()
        return mediaExtensions.contains(extension)
    }

    private fun getMediaScope(path: String): StorageScope {
        val extension = File(path).extension.lowercase()
        return when {
            setOf(
                "jpg",
                "jpeg",
                "png",
                "gif",
                "webp",
                "bmp"
            ).contains(extension) -> StorageScope.MEDIA_IMAGES

            setOf(
                "mp4",
                "avi",
                "mkv",
                "mov",
                "wmv",
                "flv"
            ).contains(extension) -> StorageScope.MEDIA_VIDEO

            setOf(
                "mp3",
                "wav",
                "flac",
                "aac",
                "ogg",
                "m4a"
            ).contains(extension) -> StorageScope.MEDIA_AUDIO

            else -> StorageScope.EXTERNAL_STORAGE
        }
    }
}
