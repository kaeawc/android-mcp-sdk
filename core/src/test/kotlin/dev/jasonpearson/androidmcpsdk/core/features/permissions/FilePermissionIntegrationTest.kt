package dev.jasonpearson.androidmcpsdk.core.features.permissions

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Integration tests for FilePermissionManager that test real-world scenarios and integration with
 * Android system components.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.TIRAMISU])
class FilePermissionIntegrationTest {

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
    fun `integration test - complete file access workflow for internal files`() = runTest {
        // Step 1: Get scoped directories
        val directories = filePermissionManager.getScopedDirectories()
        val internalDirs =
            directories.filter { it.scope == FilePermissionManager.StorageScope.APP_INTERNAL }
        assertTrue("Should have internal directories", internalDirs.isNotEmpty())

        // Step 2: Check access to internal file
        val internalFilePath = "${context.filesDir.absolutePath}/test-integration.txt"
        val accessResult = filePermissionManager.checkFileAccess(internalFilePath)

        assertTrue("Should be able to access internal file", accessResult.canAccess)
        assertFalse("Should not require permission", accessResult.requiresPermission)

        // Step 3: Verify permission request (should be granted immediately)
        val permissionResult =
            filePermissionManager.requestFilePermissions(
                FilePermissionManager.StorageScope.APP_INTERNAL
            )
        assertTrue("Internal permissions should be granted", permissionResult.granted)
    }

    @Test
    fun `integration test - Storage Access Framework workflow`() = runTest {
        // Step 1: Create document picker intent
        val mimeTypes = arrayOf("application/pdf", "text/plain")
        val pickerIntent = filePermissionManager.createDocumentPickerIntent(mimeTypes)

        assertEquals("Should have correct action", Intent.ACTION_OPEN_DOCUMENT, pickerIntent.action)
        assertTrue(
            "Should allow multiple selection",
            pickerIntent.getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE, false),
        )

        // Step 2: Create directory picker intent
        val dirPickerIntent = filePermissionManager.createDirectoryPickerIntent()
        assertEquals(
            "Should have correct action",
            Intent.ACTION_OPEN_DOCUMENT_TREE,
            dirPickerIntent.action,
        )

        // Step 3: Validate a mock document URI
        val documentUri =
            Uri.parse("content://com.android.providers.downloads.documents/document/123")
        val validationResult = filePermissionManager.validateDocumentUri(documentUri)

        // Note: Validation will fail in test environment, but should not crash
        assertNotNull("Should return validation result", validationResult)
        assertEquals(
            "Should be USER_SELECTED scope",
            FilePermissionManager.StorageScope.USER_SELECTED,
            validationResult.scope,
        )
    }

    @Test
    fun `integration test - media access permission workflow`() = runTest {
        // Mock permission system
        mockkStatic(ContextCompat::class)
        every { ContextCompat.checkSelfPermission(context, any()) } returns
            PackageManager.PERMISSION_DENIED

        // Step 1: Check required permissions for media access
        val permissionResult =
            filePermissionManager.requestFilePermissions(
                FilePermissionManager.StorageScope.MEDIA_IMAGES
            )

        assertFalse("Should not be granted without permission", permissionResult.granted)
        assertTrue("Should have permissions to check", permissionResult.permissions.isNotEmpty())

        // Step 2: Check file access for media file
        val mediaFilePath = "/storage/emulated/0/Pictures/test.jpg"
        val accessResult = filePermissionManager.checkFileAccess(mediaFilePath)

        assertFalse("Should not be able to access without permission", accessResult.canAccess)
        assertTrue("Should require permission", accessResult.requiresPermission)
        assertTrue("Should have missing permissions", accessResult.missingPermissions.isNotEmpty())

        // Step 3: Verify scoped directories reflect permission status
        val directories = filePermissionManager.getScopedDirectories()
        val mediaDirs =
            directories.filter { it.scope == FilePermissionManager.StorageScope.MEDIA_IMAGES }

        if (mediaDirs.isNotEmpty()) {
            assertFalse(
                "Media directories should not be accessible without permission",
                mediaDirs.all { it.isAccessible },
            )
        }
    }

    @Test
    fun `integration test - app external storage workflow`() = runTest {
        val externalFilesDir = context.getExternalFilesDir(null)
        if (externalFilesDir == null) {
            println("SKIPPING TEST: External storage not available")
            return@runTest
        }

        // Step 1: Verify app external directories are in scoped directories
        val directories = filePermissionManager.getScopedDirectories()
        val appExternalDirs =
            directories.filter { it.scope == FilePermissionManager.StorageScope.APP_EXTERNAL }
        assertTrue("Should have app external directories", appExternalDirs.isNotEmpty())

        // Step 2: Check access to app external file
        val externalFilePath = "${externalFilesDir.absolutePath}/test-external.txt"
        val accessResult = filePermissionManager.checkFileAccess(externalFilePath)

        assertTrue("Should be able to access app external file", accessResult.canAccess)
        assertFalse("Should not require permission", accessResult.requiresPermission)

        // Step 3: Verify permission request (should be granted immediately)
        val permissionResult =
            filePermissionManager.requestFilePermissions(
                FilePermissionManager.StorageScope.APP_EXTERNAL
            )
        assertTrue("App external permissions should be granted", permissionResult.granted)
    }

    @Test
    fun `integration test - MIME type detection and categorization`() = runTest {
        // Test file extension to storage scope mapping
        val testFiles =
            mapOf(
                "/storage/emulated/0/Pictures/photo.jpg" to
                    FilePermissionManager.StorageScope.MEDIA_IMAGES,
                "/storage/emulated/0/Pictures/photo.png" to
                    FilePermissionManager.StorageScope.MEDIA_IMAGES,
                "/storage/emulated/0/Pictures/photo.gif" to
                    FilePermissionManager.StorageScope.MEDIA_IMAGES,
                "/storage/emulated/0/Movies/video.mp4" to
                    FilePermissionManager.StorageScope.MEDIA_VIDEO,
                "/storage/emulated/0/Movies/video.avi" to
                    FilePermissionManager.StorageScope.MEDIA_VIDEO,
                "/storage/emulated/0/Music/song.mp3" to
                    FilePermissionManager.StorageScope.MEDIA_AUDIO,
                "/storage/emulated/0/Music/song.flac" to
                    FilePermissionManager.StorageScope.MEDIA_AUDIO,
                "/storage/emulated/0/Documents/doc.pdf" to
                    FilePermissionManager.StorageScope.EXTERNAL_STORAGE,
                "/storage/emulated/0/Documents/doc.txt" to
                    FilePermissionManager.StorageScope.EXTERNAL_STORAGE,
            )

        mockkStatic(ContextCompat::class)
        every { ContextCompat.checkSelfPermission(context, any()) } returns
            PackageManager.PERMISSION_DENIED

        testFiles.forEach { (filePath, expectedScope) ->
            val result = filePermissionManager.checkFileAccess(filePath)
            assertEquals(
                "File $filePath should be categorized as $expectedScope",
                expectedScope,
                result.scope,
            )
        }
    }

    @Test
    fun `integration test - API level compatibility`() = runTest {
        // Test that permission requirements change based on API level
        val mediaScopes =
            listOf(
                FilePermissionManager.StorageScope.MEDIA_IMAGES,
                FilePermissionManager.StorageScope.MEDIA_VIDEO,
                FilePermissionManager.StorageScope.MEDIA_AUDIO,
            )

        mediaScopes.forEach { scope ->
            val result = filePermissionManager.requestFilePermissions(scope)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // API 33+: Should use granular media permissions
                assertTrue(
                    "Should use READ_MEDIA_* permissions on API 33+",
                    result.permissions.keys.any { it.startsWith("android.permission.READ_MEDIA_") },
                )
            } else {
                // API < 33: Should use READ_EXTERNAL_STORAGE
                assertTrue(
                    "Should use READ_EXTERNAL_STORAGE on API < 33",
                    result.permissions.keys.any { it == "android.permission.READ_EXTERNAL_STORAGE" },
                )
            }
        }
    }

    @Test
    fun `integration test - error handling and recovery`() = runTest {
        // Test various error conditions and ensure graceful handling

        // Test with null/empty paths
        val emptyPathResult = filePermissionManager.checkFileAccess("")
        assertNotNull("Should handle empty path", emptyPathResult)

        // Test with malformed URIs
        val malformedUriResult = filePermissionManager.checkFileAccess("not-a-valid-uri")
        assertNotNull("Should handle malformed URI", malformedUriResult)

        // Test with very long paths
        val longPath = context.filesDir.absolutePath + "/" + "a".repeat(1000) + ".txt"
        val longPathResult = filePermissionManager.checkFileAccess(longPath)
        assertNotNull("Should handle long paths", longPathResult)

        // Test with special characters
        val specialCharPath =
            context.filesDir.absolutePath + "/file with spaces & symbols!@#\$%^&*().txt"
        val specialCharResult = filePermissionManager.checkFileAccess(specialCharPath)
        assertNotNull("Should handle special characters", specialCharResult)
    }

    @Test
    fun `integration test - performance under load`() = runTest {
        val startTime = System.currentTimeMillis()

        // Perform many operations to test performance
        repeat(100) { index ->
            val path = "${context.filesDir.absolutePath}/test_$index.txt"
            filePermissionManager.checkFileAccess(path)
        }

        val directories = filePermissionManager.getScopedDirectories()
        assertTrue("Should return directories quickly", directories.isNotEmpty())

        repeat(50) { index ->
            filePermissionManager.requestFilePermissions(
                FilePermissionManager.StorageScope.values()[
                        index % FilePermissionManager.StorageScope.values().size]
            )
        }

        val totalTime = System.currentTimeMillis() - startTime
        assertTrue(
            "Should complete 150+ operations in reasonable time (< 3 seconds)",
            totalTime < 3000,
        )
    }

    @Test
    fun `integration test - real file system interaction`() = runTest {
        // Create a real file in internal storage and test access
        val testFile = File(context.filesDir, "real-integration-test.txt")

        try {
            // Create the file
            testFile.writeText("Test content for integration test")
            assertTrue("Test file should exist", testFile.exists())

            // Test file access through FilePermissionManager
            val result = filePermissionManager.checkFileAccess(testFile.absolutePath)

            assertTrue("Should be able to access real file", result.canAccess)
            assertEquals(
                "Should be APP_INTERNAL scope",
                FilePermissionManager.StorageScope.APP_INTERNAL,
                result.scope,
            )
            assertFalse("Should not require permission", result.requiresPermission)
        } finally {
            // Clean up
            if (testFile.exists()) {
                testFile.delete()
            }
        }
    }
}
