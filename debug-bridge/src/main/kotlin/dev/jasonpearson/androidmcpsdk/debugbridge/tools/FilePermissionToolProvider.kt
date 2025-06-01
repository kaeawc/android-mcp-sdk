package dev.jasonpearson.androidmcpsdk.debugbridge.tools

import android.content.Context
import android.net.Uri
import android.util.Log
import dev.jasonpearson.androidmcpsdk.core.features.permissions.FilePermissionManager
import dev.jasonpearson.androidmcpsdk.core.features.tools.ToolRegistry
import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.TextContent
import io.modelcontextprotocol.kotlin.sdk.Tool
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

/**
 * Tool provider for file permission management and storage access debugging.
 */
class FilePermissionToolProvider(private val context: Context) {

    companion object {
        private const val TAG = "FilePermissionProvider"
    }

    private val filePermissionManager = FilePermissionManager(context)

    @Serializable
    data class CheckFileAccessRequest(
        val uri: String
    )

    @Serializable
    data class RequestPermissionsRequest(
        val scope: String
    )

    @Serializable
    data class ValidateDocumentUriRequest(
        val uri: String
    )

    fun registerTools(registry: ToolRegistry) {
        Log.d(TAG, "Registering file permission tools")

        registry.addTool(createCheckFileAccessTool()) { arguments ->
            handleCheckFileAccess(arguments)
        }

        registry.addTool(createRequestPermissionsTool()) { arguments ->
            handleRequestPermissions(arguments)
        }

        registry.addTool(createGetScopedDirectoriesTool()) { arguments ->
            handleGetScopedDirectories(arguments)
        }

        registry.addTool(createCreateDocumentPickerIntentTool()) { arguments ->
            handleCreateDocumentPickerIntent(arguments)
        }

        registry.addTool(createValidateDocumentUriTool()) { arguments ->
            handleValidateDocumentUri(arguments)
        }

        Log.d(TAG, "File permission tools registered")
    }

    private fun createCheckFileAccessTool(): Tool {
        return Tool(
            name = "check_file_access",
            description = "Check if the app can access a file at the given URI or path. Returns access status, scope, and required permissions.",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    put("type", JsonPrimitive("object"))
                    put("properties", buildJsonObject {
                        put("uri", buildJsonObject {
                            put("type", JsonPrimitive("string"))
                            put(
                                "description",
                                JsonPrimitive("File URI or path to check access for (e.g., 'file:///path/to/file', 'content://...', or '/path/to/file')")
                            )
                        })
                    })
                },
                required = listOf("uri")
            )
        )
    }

    private fun createRequestPermissionsTool(): Tool {
        return Tool(
            name = "request_file_permissions",
            description = "Check permission status for a specific storage scope. Returns current permission state and required permissions.",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    put("type", JsonPrimitive("object"))
                    put("properties", buildJsonObject {
                        put("scope", buildJsonObject {
                            put("type", JsonPrimitive("string"))
                            put(
                                "description",
                                JsonPrimitive("Storage scope to check permissions for")
                            )
                            put("enum", buildJsonArray {
                                add(JsonPrimitive("APP_INTERNAL"))
                                add(JsonPrimitive("APP_EXTERNAL"))
                                add(JsonPrimitive("MEDIA_IMAGES"))
                                add(JsonPrimitive("MEDIA_VIDEO"))
                                add(JsonPrimitive("MEDIA_AUDIO"))
                                add(JsonPrimitive("EXTERNAL_STORAGE"))
                                add(JsonPrimitive("USER_SELECTED"))
                            })
                        })
                    })
                },
                required = listOf("scope")
            )
        )
    }

    private fun createGetScopedDirectoriesTool(): Tool {
        return Tool(
            name = "get_scoped_directories",
            description = "Get all accessible directories categorized by storage scope with access status.",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    put("type", JsonPrimitive("object"))
                },
                required = emptyList()
            )
        )
    }

    private fun createCreateDocumentPickerIntentTool(): Tool {
        return Tool(
            name = "create_document_picker_intent",
            description = "Create an intent for Storage Access Framework document picker. Returns intent action and extras.",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    put("type", JsonPrimitive("object"))
                    put("properties", buildJsonObject {
                        put("mimeTypes", buildJsonObject {
                            put("type", JsonPrimitive("array"))
                            put(
                                "description",
                                JsonPrimitive("MIME types to filter for (e.g., ['image/*', 'video/*']). Defaults to ['*/*']")
                            )
                            put("items", buildJsonObject {
                                put("type", JsonPrimitive("string"))
                            })
                        })
                    })
                },
                required = emptyList()
            )
        )
    }

    private fun createValidateDocumentUriTool(): Tool {
        return Tool(
            name = "validate_document_uri",
            description = "Validate a document URI from Storage Access Framework and check if it's accessible.",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    put("type", JsonPrimitive("object"))
                    put("properties", buildJsonObject {
                        put("uri", buildJsonObject {
                            put("type", JsonPrimitive("string"))
                            put(
                                "description",
                                JsonPrimitive("Document URI to validate (content:// URI from Storage Access Framework)")
                            )
                        })
                    })
                },
                required = listOf("uri")
            )
        )
    }

    internal suspend fun handleCheckFileAccess(arguments: Map<String, Any>): CallToolResult {
        return try {
            val uri = arguments["uri"] as? String ?: return CallToolResult(
                content = listOf(TextContent(text = "Missing required parameter: uri")),
                isError = true
            )

            val result = filePermissionManager.checkFileAccess(uri)

            val responseData = buildString {
                appendLine("File Access Check Results:")
                appendLine("- URI: $uri")
                appendLine("- Can Access: ${result.canAccess}")
                appendLine("- Storage Scope: ${result.scope.name}")
                appendLine("- Requires Permission: ${result.requiresPermission}")

                if (result.missingPermissions.isNotEmpty()) {
                    appendLine("- Missing Permissions:")
                    result.missingPermissions.forEach { permission ->
                        appendLine("  * $permission")
                    }
                }

                result.errorMessage?.let { error ->
                    appendLine("- Error: $error")
                }
            }

            CallToolResult(
                content = listOf(TextContent(text = responseData)),
                isError = false
            )
        } catch (e: Exception) {
            CallToolResult(
                content = listOf(TextContent(text = "Error checking file access: ${e.message}")),
                isError = true
            )
        }
    }

    internal suspend fun handleRequestPermissions(arguments: Map<String, Any>): CallToolResult {
        return try {
            val scopeString = arguments["scope"] as? String ?: return CallToolResult(
                content = listOf(TextContent(text = "Missing required parameter: scope")),
                isError = true
            )

            val scope = try {
                FilePermissionManager.StorageScope.valueOf(scopeString)
            } catch (e: IllegalArgumentException) {
                return CallToolResult(
                    content = listOf(
                        TextContent(
                            text = "Invalid storage scope: $scopeString. Valid scopes: ${
                                FilePermissionManager.StorageScope.values().joinToString()
                            }"
                        )
                    ),
                    isError = true
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
                    appendLine("\nNote: This tool only checks permission status. Actual permission requests require user interaction through the Android UI.")
                }
            }

            CallToolResult(
                content = listOf(TextContent(text = responseData)),
                isError = false
            )
        } catch (e: Exception) {
            CallToolResult(
                content = listOf(TextContent(text = "Error checking permissions: ${e.message}")),
                isError = true
            )
        }
    }

    internal suspend fun handleGetScopedDirectories(arguments: Map<String, Any>): CallToolResult {
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

            CallToolResult(
                content = listOf(TextContent(text = responseData)),
                isError = false
            )
        } catch (e: Exception) {
            CallToolResult(
                content = listOf(TextContent(text = "Error getting scoped directories: ${e.message}")),
                isError = true
            )
        }
    }

    internal suspend fun handleCreateDocumentPickerIntent(arguments: Map<String, Any>): CallToolResult {
        return try {
            val mimeTypes = (arguments["mimeTypes"] as? List<*>)
                ?.mapNotNull { it as? String }
                ?.toTypedArray()
                ?: arrayOf("*/*")

            val intent = filePermissionManager.createDocumentPickerIntent(mimeTypes)

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
                        val value = when (val extra = extras.get(key)) {
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

            CallToolResult(
                content = listOf(TextContent(text = responseData)),
                isError = false
            )
        } catch (e: Exception) {
            CallToolResult(
                content = listOf(TextContent(text = "Error creating document picker intent: ${e.message}")),
                isError = true
            )
        }
    }

    internal suspend fun handleValidateDocumentUri(arguments: Map<String, Any>): CallToolResult {
        return try {
            val uriString = arguments["uri"] as? String ?: return CallToolResult(
                content = listOf(TextContent(text = "Missing required parameter: uri")),
                isError = true
            )

            val uri = try {
                Uri.parse(uriString)
            } catch (e: Exception) {
                return CallToolResult(
                    content = listOf(TextContent(text = "Invalid URI format: $uriString")),
                    isError = true
                )
            }

            val result = filePermissionManager.validateDocumentUri(uri)

            val responseData = buildString {
                appendLine("Document URI Validation Results:")
                appendLine("- URI: $uriString")
                appendLine("- Valid and Accessible: ${result.canAccess}")
                appendLine("- Storage Scope: ${result.scope.name}")
                appendLine("- Requires Permission: ${result.requiresPermission}")

                result.errorMessage?.let { error ->
                    appendLine("- Error: $error")
                }

                if (result.canAccess) {
                    appendLine()
                    appendLine("✓ This URI can be used to access the selected document/directory")
                } else {
                    appendLine()
                    appendLine("✗ This URI cannot be used or the document no longer exists")
                }
            }

            CallToolResult(
                content = listOf(TextContent(text = responseData)),
                isError = false
            )
        } catch (e: Exception) {
            CallToolResult(
                content = listOf(TextContent(text = "Error validating document URI: ${e.message}")),
                isError = true
            )
        }
    }
}
