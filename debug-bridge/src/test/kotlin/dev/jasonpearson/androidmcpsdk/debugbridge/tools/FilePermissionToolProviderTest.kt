package dev.jasonpearson.androidmcpsdk.debugbridge.tools

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.jasonpearson.androidmcpsdk.core.features.tools.ToolRegistry
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FilePermissionToolProviderTest {

    private lateinit var context: Context
    private lateinit var toolProvider: FilePermissionToolProvider
    private lateinit var mockRegistry: ToolRegistry

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        toolProvider = FilePermissionToolProvider(context)
        mockRegistry = mockk<ToolRegistry>(relaxed = true)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `registerTools should register all file permission tools`() {
        toolProvider.registerTools(mockRegistry)

        // Verify that all 5 tools are registered
        verify(exactly = 5) { mockRegistry.addTool(any(), any()) }
    }

    // Test check_file_access tool
    @Test
    fun `handleCheckFileAccess should return success for valid internal file path`() = runTest {
        val arguments = mapOf("uri" to "${context.filesDir.absolutePath}/test.txt")

        val result = toolProvider.handleCheckFileAccess(arguments)

        assertFalse("Should not be an error", result.isError ?: false)
        assertTrue("Should have content", result.content.isNotEmpty())
        assertTrue(
            "Should contain file access information",
            result.content.first().toString().contains("File Access Check Results"),
        )
    }

    @Test
    fun `handleCheckFileAccess should return error for missing uri parameter`() = runTest {
        val arguments = emptyMap<String, Any>()

        val result = toolProvider.handleCheckFileAccess(arguments)

        assertTrue("Should be an error", result.isError ?: false)
        assertTrue(
            "Should have error message",
            result.content.first().toString().contains("Missing required parameter: uri"),
        )
    }

    @Test
    fun `handleCheckFileAccess should handle content URIs`() = runTest {
        val arguments = mapOf("uri" to "content://media/external/images/media/123")

        val result = toolProvider.handleCheckFileAccess(arguments)

        assertFalse("Should not be an error", result.isError ?: false)
        assertTrue(
            "Should contain content URI handling",
            result.content.first().toString().contains("content://"),
        )
    }

    @Test
    fun `handleCheckFileAccess should handle file URIs`() = runTest {
        val arguments = mapOf("uri" to "file://${context.filesDir.absolutePath}/test.txt")

        val result = toolProvider.handleCheckFileAccess(arguments)

        assertFalse("Should not be an error", result.isError ?: false)
        assertTrue(
            "Should contain file URI handling",
            result.content.first().toString().contains("file://"),
        )
    }

    // Test request_file_permissions tool
    @Test
    fun `handleRequestPermissions should return success for valid scope`() = runTest {
        val arguments = mapOf("scope" to "APP_INTERNAL")

        val result = toolProvider.handleRequestPermissions(arguments)

        assertFalse("Should not be an error", result.isError ?: false)
        assertTrue(
            "Should contain permission check results",
            result.content.first().toString().contains("Permission Check Results"),
        )
        assertTrue(
            "Should contain scope information",
            result.content.first().toString().contains("APP_INTERNAL"),
        )
    }

    @Test
    fun `handleRequestPermissions should return error for missing scope parameter`() = runTest {
        val arguments = emptyMap<String, Any>()

        val result = toolProvider.handleRequestPermissions(arguments)

        assertTrue("Should be an error", result.isError ?: false)
        assertTrue(
            "Should have error message",
            result.content.first().toString().contains("Missing required parameter: scope"),
        )
    }

    @Test
    fun `handleRequestPermissions should return error for invalid scope`() = runTest {
        val arguments = mapOf("scope" to "INVALID_SCOPE")

        val result = toolProvider.handleRequestPermissions(arguments)

        assertTrue("Should be an error", result.isError ?: false)
        assertTrue(
            "Should have invalid scope error",
            result.content.first().toString().contains("Invalid storage scope"),
        )
    }

    @Test
    fun `handleRequestPermissions should handle all valid scopes`() = runTest {
        val validScopes =
            listOf(
                "APP_INTERNAL",
                "APP_EXTERNAL",
                "MEDIA_IMAGES",
                "MEDIA_VIDEO",
                "MEDIA_AUDIO",
                "EXTERNAL_STORAGE",
                "USER_SELECTED",
            )

        validScopes.forEach { scope ->
            val arguments = mapOf("scope" to scope)
            val result = toolProvider.handleRequestPermissions(arguments)

            assertFalse("Should not be an error for scope $scope", result.isError ?: false)
            assertTrue(
                "Should contain scope name for $scope",
                result.content.first().toString().contains(scope),
            )
        }
    }

    // Test get_scoped_directories tool
    @Test
    fun `handleGetScopedDirectories should return directory information`() = runTest {
        val arguments = emptyMap<String, Any>()

        val result = toolProvider.handleGetScopedDirectories(arguments)

        assertFalse("Should not be an error", result.isError ?: false)
        assertTrue(
            "Should contain directory information",
            result.content.first().toString().contains("Scoped Directory Access Information"),
        )
        assertTrue(
            "Should contain at least APP_INTERNAL directories",
            result.content.first().toString().contains("APP_INTERNAL"),
        )
    }

    // Test create_document_picker_intent tool
    @Test
    fun `handleCreateDocumentPickerIntent should create default intent info`() = runTest {
        val arguments = emptyMap<String, Any>()

        val result = toolProvider.handleCreateDocumentPickerIntent(arguments)

        assertFalse("Should not be an error", result.isError ?: false)
        val content = result.content.first().toString()
        assertTrue(
            "Should contain intent information",
            content.contains("Storage Access Framework Document Picker Intent"),
        )
        assertTrue("Should contain ACTION_OPEN_DOCUMENT", content.contains("ACTION_OPEN_DOCUMENT"))
        assertTrue("Should contain usage instructions", content.contains("Usage Instructions"))
    }

    @Test
    fun `handleCreateDocumentPickerIntent should handle custom MIME types`() = runTest {
        val arguments = mapOf("mimeTypes" to listOf("image/*", "video/*"))

        val result = toolProvider.handleCreateDocumentPickerIntent(arguments)

        assertFalse("Should not be an error", result.isError ?: false)
        val content = result.content.first().toString()
        assertTrue(
            "Should contain custom MIME types",
            content.contains("image/*") && content.contains("video/*"),
        )
    }

    // Test validate_document_uri tool
    @Test
    fun `handleValidateDocumentUri should return error for missing uri parameter`() = runTest {
        val arguments = emptyMap<String, Any>()

        val result = toolProvider.handleValidateDocumentUri(arguments)

        assertTrue("Should be an error", result.isError ?: false)
        assertTrue(
            "Should have error message",
            result.content.first().toString().contains("Missing required parameter: uri"),
        )
    }

    @Test
    fun `handleValidateDocumentUri should return error for invalid URI format`() = runTest {
        val arguments = mapOf("uri" to "invalid-uri-format")

        val result = toolProvider.handleValidateDocumentUri(arguments)

        assertTrue("Should be an error", result.isError ?: false)
        assertTrue(
            "Should have invalid URI error",
            result.content.first().toString().contains("Invalid URI format"),
        )
    }

    @Test
    fun `handleValidateDocumentUri should handle valid content URI`() = runTest {
        val arguments =
            mapOf("uri" to "content://com.android.providers.downloads.documents/document/123")

        val result = toolProvider.handleValidateDocumentUri(arguments)

        assertFalse("Should not be an error", result.isError ?: false)
        assertTrue(
            "Should contain validation results",
            result.content.first().toString().contains("Document URI Validation Results"),
        )
    }

    // Error handling tests
    @Test
    fun `all tool handlers should handle exceptions gracefully`() = runTest {
        // Test with arguments that might cause exceptions
        val maliciousArguments = mapOf("uri" to "\n\r\t\\//")

        val checkAccessResult = toolProvider.handleCheckFileAccess(maliciousArguments)
        val validateUriResult = toolProvider.handleValidateDocumentUri(maliciousArguments)

        // Should not crash, should handle gracefully
        assertTrue("Check access should have result", checkAccessResult.content.isNotEmpty())
        assertTrue("Validate URI should have result", validateUriResult.content.isNotEmpty())
    }

    @Test
    fun `tool provider should handle large directory listings efficiently`() = runTest {
        // This test ensures the scoped directories tool doesn't hang on large file systems
        val start = System.currentTimeMillis()

        val result = toolProvider.handleGetScopedDirectories(emptyMap())

        val duration = System.currentTimeMillis() - start
        assertTrue("Should complete directory listing quickly (< 5 seconds)", duration < 5000)
        assertFalse("Should not be an error", result.isError ?: false)
    }
}
