package dev.jasonpearson.androidmcpsdk.core.features.permissions

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.TIRAMISU])
class FilePermissionManagerTest {

    private lateinit var context: Context
    private lateinit var filePermissionManager: FilePermissionManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        filePermissionManager = FilePermissionManager(context)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `checkFileAccess should return success for app internal files`() = runTest {
        val internalFilePath = "${context.filesDir.absolutePath}/test.txt"

        val result = filePermissionManager.checkFileAccess(internalFilePath)

        assertTrue("Should be able to access internal files", result.canAccess)
        assertEquals(
            "Should be APP_INTERNAL scope",
            FilePermissionManager.StorageScope.APP_INTERNAL,
            result.scope,
        )
        assertFalse("Should not require permission", result.requiresPermission)
        assertTrue("Should have no missing permissions", result.missingPermissions.isEmpty())
    }

    @Test
    fun `checkFileAccess should return success for app cache files`() = runTest {
        val cacheFilePath = "${context.cacheDir.absolutePath}/cache.txt"

        val result = filePermissionManager.checkFileAccess(cacheFilePath)

        assertTrue("Should be able to access cache files", result.canAccess)
        assertEquals(
            "Should be APP_INTERNAL scope",
            FilePermissionManager.StorageScope.APP_INTERNAL,
            result.scope,
        )
        assertFalse("Should not require permission", result.requiresPermission)
    }

    @Test
    fun `checkFileAccess should return success for app external files`() = runTest {
        val externalFilesDir = context.getExternalFilesDir(null)
        assumeNotNull("External files dir should be available", externalFilesDir)

        val externalFilePath = "${externalFilesDir!!.absolutePath}/external.txt"

        val result = filePermissionManager.checkFileAccess(externalFilePath)

        assertTrue("Should be able to access app external files", result.canAccess)
        assertEquals(
            "Should be APP_EXTERNAL scope",
            FilePermissionManager.StorageScope.APP_EXTERNAL,
            result.scope,
        )
        assertFalse("Should not require permission", result.requiresPermission)
    }

    @Test
    fun `checkFileAccess should require permission for media files`() = runTest {
        val mediaFilePath =
            "${Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)}/test.jpg"

        mockkStatic(ContextCompat::class)
        every { ContextCompat.checkSelfPermission(context, any()) } returns
            PackageManager.PERMISSION_DENIED

        val result = filePermissionManager.checkFileAccess(mediaFilePath)

        assertFalse("Should not be able to access media files without permission", result.canAccess)
        assertEquals(
            "Should be MEDIA_IMAGES scope",
            FilePermissionManager.StorageScope.MEDIA_IMAGES,
            result.scope,
        )
        assertTrue("Should require permission", result.requiresPermission)
        assertFalse("Should have missing permissions", result.missingPermissions.isEmpty())
    }

    @Test
    fun `checkFileAccess should handle content URIs`() = runTest {
        val contentUri = "content://media/external/images/media/123"

        mockkStatic(ContextCompat::class)
        every { ContextCompat.checkSelfPermission(context, any()) } returns
            PackageManager.PERMISSION_GRANTED

        val result = filePermissionManager.checkFileAccess(contentUri)

        assertTrue("Should be able to access content URIs with permission", result.canAccess)
        assertEquals(
            "Should be MEDIA_IMAGES scope",
            FilePermissionManager.StorageScope.MEDIA_IMAGES,
            result.scope,
        )
        assertFalse(
            "Should not require additional permission when already granted",
            result.requiresPermission,
        )
    }

    @Test
    fun `checkFileAccess should handle file URIs`() = runTest {
        val fileUri = "file://${context.filesDir.absolutePath}/test.txt"

        val result = filePermissionManager.checkFileAccess(fileUri)

        assertTrue("Should be able to access file URIs for internal files", result.canAccess)
        assertEquals(
            "Should be APP_INTERNAL scope",
            FilePermissionManager.StorageScope.APP_INTERNAL,
            result.scope,
        )
        assertFalse("Should not require permission", result.requiresPermission)
    }

    @Test
    fun `requestFilePermissions should return granted for no-permission scopes`() = runTest {
        val result =
            filePermissionManager.requestFilePermissions(
                FilePermissionManager.StorageScope.APP_INTERNAL
            )

        assertTrue("Should be granted for APP_INTERNAL", result.granted)
        assertTrue("Should have no permissions to check", result.permissions.isEmpty())
        assertFalse("Should not show rationale", result.shouldShowRationale)
    }

    @Test
    fun `requestFilePermissions should check permissions for media scopes`() = runTest {
        mockkStatic(ContextCompat::class)
        every { ContextCompat.checkSelfPermission(context, any()) } returns
            PackageManager.PERMISSION_DENIED

        val result =
            filePermissionManager.requestFilePermissions(
                FilePermissionManager.StorageScope.MEDIA_IMAGES
            )

        assertFalse("Should not be granted when permission denied", result.granted)
        assertFalse("Should have permissions to check", result.permissions.isEmpty())

        // Check that the correct permission is being checked based on API level
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            assertTrue(
                "Should check READ_MEDIA_IMAGES on API 33+",
                result.permissions.containsKey(android.Manifest.permission.READ_MEDIA_IMAGES),
            )
        } else {
            assertTrue(
                "Should check READ_EXTERNAL_STORAGE on API < 33",
                result.permissions.containsKey(android.Manifest.permission.READ_EXTERNAL_STORAGE),
            )
        }
    }

    @Test
    fun `getScopedDirectories should return all accessible directories`() {
        val directories = filePermissionManager.getScopedDirectories()

        assertFalse("Should return some directories", directories.isEmpty())

        // Check that internal directories are always present and accessible
        val internalDirs =
            directories.filter { it.scope == FilePermissionManager.StorageScope.APP_INTERNAL }
        assertFalse("Should have internal directories", internalDirs.isEmpty())
        assertTrue(
            "Internal directories should be accessible",
            internalDirs.all { it.isAccessible },
        )

        // Check that app external directories are present if available
        val externalDirs =
            directories.filter { it.scope == FilePermissionManager.StorageScope.APP_EXTERNAL }
        if (context.getExternalFilesDir(null) != null) {
            assertFalse("Should have external directories when available", externalDirs.isEmpty())
            assertTrue(
                "App external directories should be accessible",
                externalDirs.all { it.isAccessible },
            )
        }
    }

    @Test
    fun `createDocumentPickerIntent should create valid intent`() {
        val intent = filePermissionManager.createDocumentPickerIntent()

        assertEquals(
            "Should have correct action",
            android.content.Intent.ACTION_OPEN_DOCUMENT,
            intent.action,
        )
        assertTrue(
            "Should have OPENABLE category",
            intent.categories?.contains(android.content.Intent.CATEGORY_OPENABLE) == true,
        )
        assertEquals("Should default to all MIME types", "*/*", intent.type)
    }

    @Test
    fun `createDocumentPickerIntent should handle custom MIME types`() {
        val mimeTypes = arrayOf("image/*", "video/*")
        val intent = filePermissionManager.createDocumentPickerIntent(mimeTypes)

        assertEquals(
            "Should have correct action",
            android.content.Intent.ACTION_OPEN_DOCUMENT,
            intent.action,
        )
        assertEquals("Should set type to wildcard for multiple types", "*/*", intent.type)

        val extraMimeTypes = intent.getStringArrayExtra(android.content.Intent.EXTRA_MIME_TYPES)
        assertNotNull("Should have extra MIME types", extraMimeTypes)
        assertArrayEquals("Should match provided MIME types", mimeTypes, extraMimeTypes)
    }

    @Test
    fun `createDirectoryPickerIntent should create valid intent`() {
        val intent = filePermissionManager.createDirectoryPickerIntent()

        assertEquals(
            "Should have correct action",
            android.content.Intent.ACTION_OPEN_DOCUMENT_TREE,
            intent.action,
        )
    }

    @Test
    fun `validateDocumentUri should handle valid content URI`() = runTest {
        val validUri = Uri.parse("content://com.android.providers.downloads.documents/document/123")

        // Mock DocumentFile.fromSingleUri to return a valid document
        mockkStatic("androidx.documentfile.provider.DocumentFile")
        val mockDocumentFile = mockk<androidx.documentfile.provider.DocumentFile>()
        every {
            androidx.documentfile.provider.DocumentFile.fromSingleUri(context, validUri)
        } returns mockDocumentFile
        every { mockDocumentFile.exists() } returns true

        val result = filePermissionManager.validateDocumentUri(validUri)

        assertTrue("Should be able to access valid document URI", result.canAccess)
        assertEquals(
            "Should be USER_SELECTED scope",
            FilePermissionManager.StorageScope.USER_SELECTED,
            result.scope,
        )
        assertFalse("Should not require permission", result.requiresPermission)
        assertNull("Should have no error message", result.errorMessage)
    }

    @Test
    fun `validateDocumentUri should handle invalid URI`() = runTest {
        val invalidUri = Uri.parse("content://invalid/document/123")

        // Mock DocumentFile.fromSingleUri to return null
        mockkStatic("androidx.documentfile.provider.DocumentFile")
        every {
            androidx.documentfile.provider.DocumentFile.fromSingleUri(context, invalidUri)
        } returns null

        val result = filePermissionManager.validateDocumentUri(invalidUri)

        assertFalse("Should not be able to access invalid document URI", result.canAccess)
        assertEquals(
            "Should be USER_SELECTED scope",
            FilePermissionManager.StorageScope.USER_SELECTED,
            result.scope,
        )
        assertFalse("Should not require permission", result.requiresPermission)
        assertNotNull("Should have error message", result.errorMessage)
    }

    @Test
    fun `checkFileAccess should handle path traversal attempts safely`() = runTest {
        val maliciousPath = "${context.filesDir.absolutePath}/../../../etc/passwd"

        val result = filePermissionManager.checkFileAccess(maliciousPath)

        // The result depends on whether the resolved path is still within app boundaries
        // This test ensures it doesn't crash and handles the path appropriately
        assertNotNull("Should handle malicious paths without crashing", result)
        assertNotNull("Should have a scope assigned", result.scope)
    }

    @Test
    fun `checkFileAccess should identify media file types correctly`() = runTest {
        val testCases =
            mapOf(
                "/storage/emulated/0/Pictures/test.jpg" to
                    FilePermissionManager.StorageScope.MEDIA_IMAGES,
                "/storage/emulated/0/Movies/test.mp4" to
                    FilePermissionManager.StorageScope.MEDIA_VIDEO,
                "/storage/emulated/0/Music/test.mp3" to
                    FilePermissionManager.StorageScope.MEDIA_AUDIO,
                "/storage/emulated/0/Documents/test.pdf" to
                    FilePermissionManager.StorageScope.EXTERNAL_STORAGE,
            )

        mockkStatic(ContextCompat::class)
        every { ContextCompat.checkSelfPermission(context, any()) } returns
            PackageManager.PERMISSION_DENIED

        testCases.forEach { (path, expectedScope) ->
            val result = filePermissionManager.checkFileAccess(path)
            assertEquals("Should identify correct scope for $path", expectedScope, result.scope)
        }
    }

    @Test
    fun `requestFilePermissions should handle all storage scopes correctly`() = runTest {
        val allScopes = FilePermissionManager.StorageScope.values()

        allScopes.forEach { scope ->
            val result = filePermissionManager.requestFilePermissions(scope)

            assertNotNull("Should return result for scope ${scope.name}", result)

            when (scope) {
                FilePermissionManager.StorageScope.APP_INTERNAL,
                FilePermissionManager.StorageScope.APP_EXTERNAL,
                FilePermissionManager.StorageScope.USER_SELECTED -> {
                    assertTrue("No-permission scopes should be granted", result.granted)
                    assertTrue(
                        "No-permission scopes should have empty permissions map",
                        result.permissions.isEmpty(),
                    )
                }

                else -> {
                    assertFalse(
                        "Permission scopes should have permissions to check",
                        result.permissions.isEmpty(),
                    )
                }
            }
        }
    }

    @Test
    fun `getScopedDirectories should include all expected directory types`() {
        val directories = filePermissionManager.getScopedDirectories()

        // Should have at least internal files and cache
        val scopeTypes = directories.map { it.scope }.toSet()
        assertTrue(
            "Should include APP_INTERNAL",
            scopeTypes.contains(FilePermissionManager.StorageScope.APP_INTERNAL),
        )

        // Verify internal directories have expected paths
        val internalDirs =
            directories.filter { it.scope == FilePermissionManager.StorageScope.APP_INTERNAL }
        val internalPaths = internalDirs.map { it.path }

        assertTrue(
            "Should include files directory",
            internalPaths.any { it.contains(context.filesDir.name) },
        )
        assertTrue(
            "Should include cache directory",
            internalPaths.any { it.contains(context.cacheDir.name) },
        )
    }

    @Test
    fun `getScopedDirectories should mark accessibility correctly`() {
        val directories = filePermissionManager.getScopedDirectories()

        // Internal directories should always be accessible
        val internalDirs =
            directories.filter { it.scope == FilePermissionManager.StorageScope.APP_INTERNAL }
        assertTrue(
            "All internal directories should be accessible",
            internalDirs.all { it.isAccessible },
        )

        // App external directories should be accessible (no permissions needed)
        val appExternalDirs =
            directories.filter { it.scope == FilePermissionManager.StorageScope.APP_EXTERNAL }
        assertTrue(
            "All app external directories should be accessible",
            appExternalDirs.all { it.isAccessible },
        )
    }

    @Test
    fun `createDocumentPickerIntent should handle single MIME type`() {
        val mimeType = "image/jpeg"
        val intent = filePermissionManager.createDocumentPickerIntent(arrayOf(mimeType))

        assertEquals("Should set single MIME type", mimeType, intent.type)
        assertNull(
            "Should not have extra MIME types for single type",
            intent.getStringArrayExtra(Intent.EXTRA_MIME_TYPES),
        )
    }

    @Test
    fun `createDocumentPickerIntent should enable multiple selection`() {
        val intent = filePermissionManager.createDocumentPickerIntent()

        assertTrue(
            "Should allow multiple selection",
            intent.getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE, false),
        )
    }

    @Test
    fun `validateDocumentUri should handle non-existent document`() = runTest {
        val validUri = Uri.parse("content://com.android.providers.downloads.documents/document/999")

        mockkStatic("androidx.documentfile.provider.DocumentFile")
        val mockDocumentFile = mockk<androidx.documentfile.provider.DocumentFile>()
        every {
            androidx.documentfile.provider.DocumentFile.fromSingleUri(context, validUri)
        } returns mockDocumentFile
        every { mockDocumentFile.exists() } returns false

        val result = filePermissionManager.validateDocumentUri(validUri)

        assertFalse("Should not be able to access non-existent document", result.canAccess)
        assertEquals(
            "Should be USER_SELECTED scope",
            FilePermissionManager.StorageScope.USER_SELECTED,
            result.scope,
        )
        assertFalse("Should not require permission", result.requiresPermission)
    }

    @Test
    fun `validateDocumentUri should handle exceptions gracefully`() = runTest {
        val validUri = Uri.parse("content://com.android.providers.downloads.documents/document/123")

        mockkStatic("androidx.documentfile.provider.DocumentFile")
        every {
            androidx.documentfile.provider.DocumentFile.fromSingleUri(context, validUri)
        } throws SecurityException("Access denied")

        val result = filePermissionManager.validateDocumentUri(validUri)

        assertFalse("Should handle exceptions gracefully", result.canAccess)
        assertNotNull("Should have error message", result.errorMessage)
        assertTrue(
            "Should mention the exception",
            result.errorMessage!!.contains("Error validating document URI"),
        )
    }

    @Test
    fun `checkFileAccess should handle complex app external paths`() = runTest {
        // Test various app external path formats
        val externalDir = context.getExternalFilesDir(null)
        assumeNotNull("External files dir should be available", externalDir)

        val testPaths =
            listOf(
                "${externalDir!!.absolutePath}/subdir/file.txt",
                "${externalDir.absolutePath}/../cache/temp.txt",
                "/Android/data/${context.packageName}/files/test.txt",
                "/Android/data/${context.packageName}/cache/cache.txt",
            )

        testPaths.forEach { path ->
            val result = filePermissionManager.checkFileAccess(path)

            assertTrue(
                "Path $path should be accessible or APP_EXTERNAL scope",
                result.canAccess || result.scope == FilePermissionManager.StorageScope.APP_EXTERNAL,
            )
        }
    }

    @Test
    fun `checkFileAccess should detect MediaStore authority correctly`() = runTest {
        val mediaStoreUris =
            listOf(
                "content://media/external/images/media/123",
                "content://media/external/video/media/456",
                "content://media/external/audio/media/789",
            )

        mediaStoreUris.forEach { uri ->
            val result = filePermissionManager.checkFileAccess(uri)

            assertEquals(
                "MediaStore URI should be MEDIA_IMAGES scope",
                FilePermissionManager.StorageScope.MEDIA_IMAGES,
                result.scope,
            )
        }
    }

    @Test
    fun `requestFilePermissions should return appropriate permissions for API level`() = runTest {
        // Test that different API levels return correct permissions
        val mediaImageResult =
            filePermissionManager.requestFilePermissions(
                FilePermissionManager.StorageScope.MEDIA_IMAGES
            )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            assertTrue(
                "Should check READ_MEDIA_IMAGES on API 33+",
                mediaImageResult.permissions.keys.any {
                    it == android.Manifest.permission.READ_MEDIA_IMAGES
                },
            )
        } else {
            assertTrue(
                "Should check READ_EXTERNAL_STORAGE on API < 33",
                mediaImageResult.permissions.keys.any {
                    it == android.Manifest.permission.READ_EXTERNAL_STORAGE
                },
            )
        }
    }

    @Test
    fun `getScopedDirectories should provide meaningful descriptions`() {
        val directories = filePermissionManager.getScopedDirectories()

        directories.forEach { dir ->
            assertFalse(
                "Description should not be empty for ${dir.path}",
                dir.description.isBlank(),
            )
            assertTrue(
                "Description should be informative for ${dir.path}",
                dir.description.length > 10,
            )
        }
    }

    @Test
    fun `filePermissionManager should handle concurrent access`() = runTest {
        // Test that FilePermissionManager is thread-safe
        val results =
            (1..10)
                .map { index ->
                    async {
                        filePermissionManager.checkFileAccess(
                            "${context.filesDir.absolutePath}/test$index.txt"
                        )
                    }
                }
                .awaitAll()

        assertEquals("Should handle all concurrent requests", 10, results.size)
        results.forEach { result ->
            assertTrue("All concurrent results should be successful", result.canAccess)
        }
    }

    @Test
    fun `checkFileAccess should normalize paths correctly`() = runTest {
        val basePath = context.filesDir.absolutePath
        val testPaths =
            listOf(
                "$basePath/./test.txt", // Same directory reference
                "$basePath/../${context.filesDir.name}/test.txt", // Parent then back down
                "$basePath//test.txt", // Double slash
                "$basePath/test.txt/", // Trailing slash
            )

        testPaths.forEach { path ->
            val result = filePermissionManager.checkFileAccess(path)
            assertTrue("Normalized path $path should be accessible", result.canAccess)
            assertEquals(
                "Should be APP_INTERNAL scope",
                FilePermissionManager.StorageScope.APP_INTERNAL,
                result.scope,
            )
        }
    }

    private fun assumeNotNull(message: String, value: Any?) {
        if (value == null) {
            println("SKIPPING TEST: $message")
            return
        }
    }
}
