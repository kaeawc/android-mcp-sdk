package dev.jasonpearson.androidmcpsdk.debugbridge.tools

import android.content.Context
import android.util.Log
import androidx.core.net.toUri
import dev.jasonpearson.androidmcpsdk.core.features.permissions.FilePermissionManager
import dev.jasonpearson.androidmcpsdk.core.features.tools.McpToolProvider
import dev.jasonpearson.androidmcpsdk.core.features.tools.addTool
import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.TextContent
import kotlinx.serialization.Serializable

/** Tool provider for file permission management and storage access debugging. */
class FilePermissionToolProvider(private val context: Context) {

    companion object {
        private const val TAG = "FilePermissionProvider"
    }

    private val filePermissionManager = FilePermissionManager(context)

    @Serializable data class CheckFileAccessRequest(val uri: String)

    @Serializable data class RequestPermissionsRequest(val scope: String)

    @Serializable data class EmptyInput(val placeholder: String? = null)

    @Serializable
    data class CreateDocumentPickerIntentInput(val mimeTypes: List<String> = listOf("*/*"))

    @Serializable data class ValidateDocumentUriRequest(val uri: String)

    fun registerTools(toolProvider: McpToolProvider) {
        Log.d(TAG, "Registering file permission tools")

        toolProvider.addTool<CheckFileAccessRequest>(
            name = "check_file_access",
            description =
                "Check if the app can access a file at the given URI or path. Returns access status, scope, and required permissions.",
            required = listOf("uri"),
        ) { input ->
            handleCheckFileAccess(input)
        }

        toolProvider.addTool<RequestPermissionsRequest>(
            name = "request_file_permissions",
            description =
                "Check permission status for a specific storage scope. Returns current permission state and required permissions.",
            required = listOf("scope"),
        ) { input ->
            handleRequestPermissions(input)
        }

        toolProvider.addTool<EmptyInput>(
            name = "get_scoped_directories",
            description =
                "Get all accessible directories categorized by storage scope with access status.",
        ) { _ ->
            handleGetScopedDirectories()
        }

        toolProvider.addTool<CreateDocumentPickerIntentInput>(
            name = "create_document_picker_intent",
            description =
                "Create an intent for Storage Access Framework document picker. Returns intent action and extras.",
        ) { input ->
            handleCreateDocumentPickerIntent(input)
        }

        toolProvider.addTool<ValidateDocumentUriRequest>(
            name = "validate_document_uri",
            description =
                "Validate a document URI from Storage Access Framework and check if it's accessible.",
            required = listOf("uri"),
        ) { input ->
            handleValidateDocumentUri(input)
        }

        Log.d(TAG, "File permission tools registered")
    }

    internal suspend fun handleCheckFileAccess(input: CheckFileAccessRequest): CallToolResult {
        return try {
            val result = filePermissionManager.checkFileAccess(input.uri)

            val responseData = buildString {
                appendLine("File Access Check Results:")
                appendLine("- URI: ${input.uri}")
                appendLine("- Can Access: ${result.canAccess}")
                appendLine("- Storage Scope: ${result.scope.name}")
                appendLine("- Requires Permission: ${result.requiresPermission}")

                if (result.missingPermissions.isNotEmpty()) {
                    appendLine("- Missing Permissions:")
                    result.missingPermissions.forEach { permission ->
                        appendLine("  * $permission")
                    }
                }

                result.errorMessage?.let { error -> appendLine("- Error: $error") }
            }

            CallToolResult(content = listOf(TextContent(text = responseData)), isError = false)
        } catch (e: Exception) {
            CallToolResult(
                content = listOf(TextContent(text = "Error checking file access: ${e.message}")),
                isError = true,
            )
        }
    }

    internal suspend fun handleRequestPermissions(
        input: RequestPermissionsRequest
    ): CallToolResult {
        return try {
            val scope =
                try {
                    FilePermissionManager.StorageScope.valueOf(input.scope)
                } catch (e: IllegalArgumentException) {
                    return CallToolResult(
                        content =
                            listOf(
                                TextContent(
                                    text =
                                        "Invalid storage scope: ${input.scope}. Valid scopes: ${
                                FilePermissionManager.StorageScope.values().joinToString()
                            }"
                                )
                            ),
                        isError = true,
                    )
                }

            val result = filePermissionManager.requestFilePermissions(scope)

            val responseData = buildString {
                appendLine("Permission Check Results:")
                appendLine("- Storage Scope: ${scope.name}")
                appendLine("- All Permissions Granted: ${result.granted}")
                appendLine("- Should Show Rationale: ${result.shouldShowRationale}")
                appendLine("- Individual Permissions:")

                result.permissions.forEach { (permission, granted) ->
                    val status = if (granted) "✓ GRANTED" else "✗ DENIED"
                    appendLine("  * $permission: $status")
                }

                if (!result.granted) {
                    appendLine(
                        "\nNote: This tool only checks permission status. Actual permission requests require user interaction through the Android UI."
                    )
                }
            }

            CallToolResult(content = listOf(TextContent(text = responseData)), isError = false)
        } catch (e: Exception) {
            CallToolResult(
                content = listOf(TextContent(text = "Error checking permissions: ${e.message}")),
                isError = true,
            )
        }
    }

    internal suspend fun handleGetScopedDirectories(): CallToolResult {
        return try {
            val directories = filePermissionManager.getScopedDirectories()

            val responseData = buildString {
                appendLine("Scoped Directory Access Information:")
                appendLine()

                val groupedDirectories = directories.groupBy { it.scope }

                groupedDirectories.forEach { (scope, dirs) ->
                    appendLine("${scope.name}:")
                    dirs.forEach { dir ->
                        val status = if (dir.isAccessible) "✓ ACCESSIBLE" else "✗ NO ACCESS"
                        appendLine("  [$status] ${dir.path}")
                        appendLine("    Description: ${dir.description}")
                    }
                    appendLine()
                }
            }

            CallToolResult(content = listOf(TextContent(text = responseData)), isError = false)
        } catch (e: Exception) {
            CallToolResult(
                content =
                    listOf(TextContent(text = "Error getting scoped directories: ${e.message}")),
                isError = true,
            )
        }
    }

    internal suspend fun handleCreateDocumentPickerIntent(
        input: CreateDocumentPickerIntentInput
    ): CallToolResult {
        return try {
            val intent =
                filePermissionManager.createDocumentPickerIntent(input.mimeTypes.toTypedArray())

            val responseData = buildString {
                appendLine("Storage Access Framework Document Picker Intent:")
                appendLine("- Action: ${intent.action}")
                appendLine("- Type: ${intent.type}")

                intent.categories?.let { categories ->
                    appendLine("- Categories: ${categories.joinToString()}")
                }

                intent.extras?.let { extras ->
                    appendLine("- Extras:")
                    extras.keySet().forEach { key ->
                        val value =
                            when (val extra = extras.get(key)) {
                                is Array<*> -> extra.joinToString(", ", "[", "]")
                                else -> extra?.toString()
                            }
                        appendLine("  * $key: $value")
                    }
                }

                appendLine()
                appendLine("Usage Instructions:")
                appendLine("1. Use startActivityForResult() with this intent")
                appendLine("2. Handle the result in onActivityResult()")
                appendLine("3. Parse the returned URI from the result data")
                appendLine("4. Use validate_document_uri tool to check the returned URI")
            }

            CallToolResult(content = listOf(TextContent(text = responseData)), isError = false)
        } catch (e: Exception) {
            CallToolResult(
                content =
                    listOf(
                        TextContent(text = "Error creating document picker intent: ${e.message}")
                    ),
                isError = true,
            )
        }
    }

    internal suspend fun handleValidateDocumentUri(
        input: ValidateDocumentUriRequest
    ): CallToolResult {
        return try {
            val uri =
                try {
                    input.uri.toUri()
                } catch (e: Exception) {
                    return CallToolResult(
                        content = listOf(TextContent(text = "Invalid URI format: ${input.uri}")),
                        isError = true,
                    )
                }

            val result = filePermissionManager.validateDocumentUri(uri)

            val responseData = buildString {
                appendLine("Document URI Validation Results:")
                appendLine("- URI: ${input.uri}")
                appendLine("- Valid and Accessible: ${result.canAccess}")
                appendLine("- Storage Scope: ${result.scope.name}")
                appendLine("- Requires Permission: ${result.requiresPermission}")

                result.errorMessage?.let { error -> appendLine("- Error: $error") }

                if (result.canAccess) {
                    appendLine()
                    appendLine("✓ This URI can be used to access the selected document/directory")
                } else {
                    appendLine()
                    appendLine("✗ This URI cannot be used or the document no longer exists")
                }
            }

            CallToolResult(content = listOf(TextContent(text = responseData)), isError = false)
        } catch (e: Exception) {
            CallToolResult(
                content = listOf(TextContent(text = "Error validating document URI: ${e.message}")),
                isError = true,
            )
        }
    }
}
