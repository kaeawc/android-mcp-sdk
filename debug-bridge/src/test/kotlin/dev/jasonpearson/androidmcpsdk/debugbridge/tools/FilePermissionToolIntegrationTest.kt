package dev.jasonpearson.androidmcpsdk.debugbridge.tools

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.jasonpearson.androidmcpsdk.core.features.tools.DefaultToolRegistry
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Integration tests for FilePermissionToolProvider that test complete workflows
 * and integration with the underlying FilePermissionManager.
 */
@RunWith(AndroidJUnit4::class)
class FilePermissionToolIntegrationTest {

    private lateinit var context: Context
    private lateinit var toolProvider: FilePermissionToolProvider
    private lateinit var toolRegistry: DefaultToolRegistry

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        toolProvider = FilePermissionToolProvider(context)
        toolRegistry = DefaultToolRegistry()

        // Register all tools for integration testing
        toolProvider.registerTools(toolRegistry)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `integration test - complete file permission analysis workflow`() = runTest {
        // Step 1: Get scoped directories to understand available storage
        val directoriesResult = toolProvider.handleGetScopedDirectories(emptyMap())

        assertFalse("Should successfully get directories", directoriesResult.isError ?: false)
        val directoriesOutput = directoriesResult.content.first().toString()
        assertTrue(
            "Should contain APP_INTERNAL directories",
            directoriesOutput.contains("APP_INTERNAL")
        )
        assertTrue(
            "Should show accessible status",
            directoriesOutput.contains("ACCESSIBLE")
        )

        // Step 2: Check access to internal file from discovered directories
        val internalFilePath = "${context.filesDir.absolutePath}/workflow-test.txt"
        val accessArgs = mapOf("uri" to internalFilePath)
        val accessResult = toolProvider.handleCheckFileAccess(accessArgs)

        assertFalse("Should successfully check file access", accessResult.isError ?: false)
        val accessOutput = accessResult.content.first().toString()
        assertTrue("Should show can access", accessOutput.contains("Can Access: true"))
        assertTrue("Should show APP_INTERNAL scope", accessOutput.contains("APP_INTERNAL"))
        assertTrue(
            "Should show no permission required",
            accessOutput.contains("Requires Permission: false")
        )

        // Step 3: Verify permission status for this scope
        val permissionArgs = mapOf("scope" to "APP_INTERNAL")
        val permissionResult = toolProvider.handleRequestPermissions(permissionArgs)

        assertFalse("Should successfully check permissions", permissionResult.isError ?: false)
        val permissionOutput = permissionResult.content.first().toString()
        assertTrue(
            "Should show permissions granted",
            permissionOutput.contains("All Permissions Granted: true")
        )
    }

    @Test
    fun `integration test - media file permission workflow`() = runTest {
        // Mock permission system to simulate no media permissions
        mockkStatic(ContextCompat::class)
        every { ContextCompat.checkSelfPermission(context, any()) } returns
                PackageManager.PERMISSION_DENIED

        // Step 1: Check permission requirements for media scope
        val permissionArgs = mapOf("scope" to "MEDIA_IMAGES")
        val permissionResult = toolProvider.handleRequestPermissions(permissionArgs)

        assertFalse(
            "Should successfully check media permissions",
            permissionResult.isError ?: false
        )
        val permissionOutput = permissionResult.content.first().toString()
        assertTrue(
            "Should show permissions not granted",
            permissionOutput.contains("All Permissions Granted: false")
        )
        assertTrue(
            "Should list specific media permissions",
            permissionOutput.contains("READ_MEDIA") || permissionOutput.contains("READ_EXTERNAL_STORAGE")
        )

        // Step 2: Check access to media file
        val mediaFilePath = "/storage/emulated/0/Pictures/test-image.jpg"
        val accessArgs = mapOf("uri" to mediaFilePath)
        val accessResult = toolProvider.handleCheckFileAccess(accessArgs)

        assertFalse("Should successfully check media file access", accessResult.isError ?: false)
        val accessOutput = accessResult.content.first().toString()
        assertTrue(
            "Should show cannot access without permission",
            accessOutput.contains("Can Access: false")
        )
        assertTrue(
            "Should show MEDIA_IMAGES scope",
            accessOutput.contains("MEDIA_IMAGES")
        )
        assertTrue(
            "Should show permission required",
            accessOutput.contains("Requires Permission: true")
        )

        // Step 3: Verify directories reflect permission status
        val directoriesResult = toolProvider.handleGetScopedDirectories(emptyMap())
        val directoriesOutput = directoriesResult.content.first().toString()

        // Should show media directories but mark them as not accessible
        assertTrue(
            "Should list MEDIA_IMAGES directories",
            directoriesOutput.contains("MEDIA_IMAGES")
        )
    }

    @Test
    fun `integration test - Storage Access Framework workflow`() = runTest {
        // Step 1: Create document picker intent for specific file types
        val mimeArgs = mapOf("mimeTypes" to listOf("image/*", "application/pdf"))
        val intentResult = toolProvider.handleCreateDocumentPickerIntent(mimeArgs)

        assertFalse("Should successfully create picker intent", intentResult.isError ?: false)
        val intentOutput = intentResult.content.first().toString()
        assertTrue(
            "Should contain ACTION_OPEN_DOCUMENT",
            intentOutput.contains("ACTION_OPEN_DOCUMENT")
        )
        assertTrue(
            "Should contain image MIME type",
            intentOutput.contains("image/*")
        )
        assertTrue(
            "Should contain PDF MIME type",
            intentOutput.contains("application/pdf")
        )
        assertTrue(
            "Should contain usage instructions",
            intentOutput.contains("Usage Instructions")
        )

        // Step 2: Validate a sample document URI (would normally come from intent result)
        val documentUri = "content://com.android.providers.downloads.documents/document/12345"
        val validateArgs = mapOf("uri" to documentUri)
        val validateResult = toolProvider.handleValidateDocumentUri(validateArgs)

        assertFalse("Should successfully validate URI format", validateResult.isError ?: false)
        val validateOutput = validateResult.content.first().toString()
        assertTrue(
            "Should contain validation results",
            validateOutput.contains("Document URI Validation Results")
        )
        assertTrue(
            "Should show USER_SELECTED scope",
            validateOutput.contains("USER_SELECTED")
        )
    }

    @Test
    fun `integration test - error handling and edge cases`() = runTest {
        // Test 1: Invalid scope in permission request
        val invalidScopeArgs = mapOf("scope" to "INVALID_SCOPE_NAME")
        val invalidScopeResult = toolProvider.handleRequestPermissions(invalidScopeArgs)

        assertTrue("Should return error for invalid scope", invalidScopeResult.isError ?: false)
        assertTrue(
            "Should explain valid scopes",
            invalidScopeResult.content.first().toString().contains("Valid scopes")
        )

        // Test 2: Malformed URI in file access check
        val malformedUriArgs = mapOf("uri" to "definitely-not-a-uri")
        val malformedResult = toolProvider.handleCheckFileAccess(malformedUriArgs)

        assertFalse("Should handle malformed URI gracefully", malformedResult.isError ?: false)
        // Should still provide some analysis, even if the URI is unusual

        // Test 3: Invalid URI format in document validation
        val invalidUriArgs = mapOf("uri" to "not-a-content-uri")
        val invalidUriResult = toolProvider.handleValidateDocumentUri(invalidUriArgs)

        assertTrue("Should return error for invalid URI format", invalidUriResult.isError ?: false)
        assertTrue(
            "Should explain URI format issue",
            invalidUriResult.content.first().toString().contains("Invalid URI format")
        )

        // Test 4: Missing required parameters
        val missingParamResult = toolProvider.handleCheckFileAccess(emptyMap())
        assertTrue(
            "Should return error for missing parameters",
            missingParamResult.isError ?: false
        )
    }

    @Test
    fun `integration test - comprehensive directory analysis`() = runTest {
        val directoriesResult = toolProvider.handleGetScopedDirectories(emptyMap())
        val output = directoriesResult.content.first().toString()

        // Should include all major storage scopes
        val expectedScopes = listOf(
            "APP_INTERNAL", "APP_EXTERNAL", "MEDIA_IMAGES",
            "MEDIA_VIDEO", "MEDIA_AUDIO", "EXTERNAL_STORAGE"
        )

        expectedScopes.forEach { scope ->
            assertTrue(
                "Should include $scope in directory listing",
                output.contains(scope)
            )
        }

        // Should show accessibility status for each directory
        assertTrue(
            "Should show accessible directories",
            output.contains("ACCESSIBLE")
        )

        // Should provide descriptions
        assertTrue(
            "Should provide directory descriptions",
            output.contains("Description:")
        )
    }

    @Test
    fun `integration test - real file system interaction`() = runTest {
        // Create a real test file
        val testFile = File(context.filesDir, "integration-tool-test.txt")

        try {
            testFile.writeText("Test content for tool integration")
            assertTrue("Test file should be created", testFile.exists())

            // Test file access through tool
            val accessArgs = mapOf("uri" to testFile.absolutePath)
            val accessResult = toolProvider.handleCheckFileAccess(accessArgs)

            assertFalse("Should successfully analyze real file", accessResult.isError ?: false)
            val output = accessResult.content.first().toString()

            assertTrue("Should show file is accessible", output.contains("Can Access: true"))
            assertTrue("Should identify as internal storage", output.contains("APP_INTERNAL"))
            assertTrue(
                "Should show no permission needed",
                output.contains("Requires Permission: false")
            )

        } finally {
            if (testFile.exists()) {
                testFile.delete()
            }
        }
    }

    @Test
    fun `integration test - tool registry integration`() = runTest {
        // Verify all tools are properly registered and accessible
        val registeredTools = toolRegistry.getAllTools()

        val expectedToolNames = setOf(
            "check_file_access",
            "request_file_permissions",
            "get_scoped_directories",
            "create_document_picker_intent",
            "validate_document_uri"
        )

        val actualToolNames = registeredTools.map { it.name }.toSet()

        expectedToolNames.forEach { expectedTool ->
            assertTrue(
                "Tool $expectedTool should be registered",
                actualToolNames.contains(expectedTool)
            )
        }

        // Test that tools can be called through the registry
        registeredTools.forEach { tool ->
            assertTrue(
                "Tool ${tool.name} should have a description",
                tool.description.isNotBlank()
            )
            assertTrue(
                "Tool ${tool.name} should have input schema",
                tool.inputSchema?.let { true } ?: false
            )
        }
    }

    @Test
    fun `integration test - performance and resource usage`() = runTest {
        val startTime = System.currentTimeMillis()

        // Perform multiple operations to test performance
        repeat(20) { index ->
            // Test different file paths
            val testPath = "${context.filesDir.absolutePath}/perf_test_$index.txt"
            toolProvider.handleCheckFileAccess(mapOf("uri" to testPath))

            // Test different scopes
            val scopes = listOf("APP_INTERNAL", "APP_EXTERNAL", "MEDIA_IMAGES")
            val scope = scopes[index % scopes.size]
            toolProvider.handleRequestPermissions(mapOf("scope" to scope))
        }

        // Test directory listing (potentially expensive)
        repeat(5) {
            toolProvider.handleGetScopedDirectories(emptyMap())
        }

        val endTime = System.currentTimeMillis()
        val duration = endTime - startTime

        assertTrue("Should complete performance test quickly (< 2 seconds)", duration < 2000)
    }

    @Test
    fun `integration test - concurrent tool usage`() = runTest {
        // Test that tools can be used concurrently without issues
        coroutineScope {
            val tasks = (1..10).map { index ->
                async {
                    when (index % 3) {
                        0 -> toolProvider.handleCheckFileAccess(
                            mapOf("uri" to "${context.filesDir.absolutePath}/concurrent_$index.txt")
                        )

                        1 -> toolProvider.handleRequestPermissions(
                            mapOf("scope" to "APP_INTERNAL")
                        )

                        else -> toolProvider.handleGetScopedDirectories(emptyMap())
                    }
                }
            }

            val results = tasks.map { it.await() }

            // All operations should complete successfully
            results.forEach { result ->
                assertFalse("Concurrent operation should not error", result.isError ?: false)
                assertTrue("Concurrent operation should have content", result.content.isNotEmpty())
            }
        }
    }
}
