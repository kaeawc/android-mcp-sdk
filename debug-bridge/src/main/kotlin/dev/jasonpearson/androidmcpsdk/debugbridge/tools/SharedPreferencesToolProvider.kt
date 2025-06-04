package dev.jasonpearson.androidmcpsdk.debugbridge.tools

import android.content.Context
import android.util.Log
import dev.jasonpearson.androidmcpsdk.core.features.tools.McpToolProvider
import dev.jasonpearson.androidmcpsdk.core.features.tools.addTool
import dev.jasonpearson.androidmcpsdk.debugbridge.preferences.SharedPreferencesProvider
import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.TextContent
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Provides SharedPreferences tools for the debug bridge. */
class SharedPreferencesToolProvider(private val context: Context) {

    companion object {
        private const val TAG = "SharedPreferencesTools"
    }

    private val preferencesProvider = SharedPreferencesProvider(context)

    @Serializable data class PreferencesListInput(val placeholder: String? = null)

    @Serializable
    data class PreferencesQueryInput(
        val fileName: String,
        val key: String? = null, // If null, return all preferences
        val outputFormat: String = "json",
    )

    @Serializable
    data class PreferencesSearchInput(
        val pattern: String,
        val searchInKeys: Boolean = true,
        val searchInValues: Boolean = true,
        val outputFormat: String = "json",
    )

    @Serializable
    data class PreferencesSetInput(
        val fileName: String,
        val key: String,
        val value: String,
        val type: String = "STRING", // STRING, INT, BOOLEAN, FLOAT, LONG, STRING_SET
        val dryRun: Boolean = false,
    )

    @Serializable
    data class PreferencesRemoveInput(
        val fileName: String,
        val key: String,
        val confirm: Boolean = false,
    )

    @Serializable
    data class PreferencesClearInput(val fileName: String, val confirm: Boolean = false)

    @Serializable
    data class PreferencesExportInput(
        val fileName: String? = null, // If null, export all
        val outputFormat: String = "json",
    )

    @Serializable
    data class PreferencesBatchEditInput(
        val fileName: String,
        val operations: List<BatchOperation>,
        val dryRun: Boolean = false,
    )

    @Serializable
    data class BatchOperation(
        val action: String, // SET, REMOVE
        val key: String,
        val value: String? = null,
        val type: String = "STRING",
    )

    fun registerTools(toolProvider: McpToolProvider) {
        Log.d(TAG, "Registering SharedPreferences tools")

        // List all preferences files
        toolProvider.addTool<PreferencesListInput>(
            name = "preferences_list",
            description = "List all SharedPreferences files in the application",
        ) { _ ->
            listPreferencesFiles()
        }

        // Query preferences
        toolProvider.addTool<PreferencesQueryInput>(
            name = "preferences_query",
            description = "Query SharedPreferences data from a specific file",
        ) { input ->
            queryPreferences(input)
        }

        // Search preferences
        toolProvider.addTool<PreferencesSearchInput>(
            name = "preferences_search",
            description = "Search for preferences keys and values by pattern",
        ) { input ->
            searchPreferences(input)
        }

        // Set preference value
        toolProvider.addTool<PreferencesSetInput>(
            name = "preferences_set",
            description = "Set a preference value with type conversion",
        ) { input ->
            setPreference(input)
        }

        // Remove preference key
        toolProvider.addTool<PreferencesRemoveInput>(
            name = "preferences_remove",
            description = "Remove a preference key from a file",
        ) { input ->
            removePreference(input)
        }

        // Clear all preferences
        toolProvider.addTool<PreferencesClearInput>(
            name = "preferences_clear",
            description = "Clear all preferences from a file",
        ) { input ->
            clearPreferences(input)
        }

        // Export preferences
        toolProvider.addTool<PreferencesExportInput>(
            name = "preferences_export",
            description = "Export preferences data in various formats",
        ) { input ->
            exportPreferences(input)
        }

        // Batch edit operations
        toolProvider.addTool<PreferencesBatchEditInput>(
            name = "preferences_batch_edit",
            description = "Perform multiple preference operations in a batch",
        ) { input ->
            batchEditPreferences(input)
        }

        Log.d(TAG, "SharedPreferences tools registered")
    }

    private suspend fun listPreferencesFiles(): CallToolResult {
        return try {
            val files = preferencesProvider.getAllPreferenceFiles()

            val output = buildString {
                appendLine("SharedPreferences Files:")
                if (files.isEmpty()) {
                    appendLine("No preferences files found")
                } else {
                    files.forEach { file ->
                        appendLine("  - ${file.fileName}")
                        appendLine("    Path: ${file.path}")
                        appendLine("    Keys: ${file.keyCount}")
                        appendLine("    Size: ${file.size} bytes")
                        appendLine("    Last Modified: ${java.util.Date(file.lastModified)}")
                        appendLine()
                    }
                }
            }

            CallToolResult(content = listOf(TextContent(text = output)), isError = false)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to list preferences files", e)
            CallToolResult(
                content =
                    listOf(TextContent(text = "Failed to list preferences files: ${e.message}")),
                isError = true,
            )
        }
    }

    private suspend fun queryPreferences(input: PreferencesQueryInput): CallToolResult {
        return try {
            val result =
                if (input.key != null) {
                    // Query specific key
                    val value = preferencesProvider.getPreferenceValue(input.fileName, input.key)
                    if (value != null) {
                        buildString {
                            appendLine("Preference Value:")
                            appendLine("File: ${input.fileName}")
                            appendLine("Key: ${value.key}")
                            appendLine("Value: ${value.value}")
                            appendLine("Type: ${value.originalType}")
                            appendLine("Last Modified: ${java.util.Date(value.lastModified)}")
                        }
                    } else {
                        "Preference key '${input.key}' not found in file '${input.fileName}'"
                    }
                } else {
                    // Query all preferences
                    val content = preferencesProvider.getPreferencesContent(input.fileName)
                    formatPreferencesContent(content, input.outputFormat)
                }

            CallToolResult(content = listOf(TextContent(text = result)), isError = false)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to query preferences", e)
            CallToolResult(
                content = listOf(TextContent(text = "Failed to query preferences: ${e.message}")),
                isError = true,
            )
        }
    }

    private suspend fun searchPreferences(input: PreferencesSearchInput): CallToolResult {
        return try {
            val allFiles = preferencesProvider.getAllPreferenceFiles()
            val matches = mutableListOf<String>()

            for (file in allFiles) {
                val content = preferencesProvider.getPreferencesContent(file.fileName)

                content.preferences.forEach { (key, value) ->
                    val keyMatches =
                        input.searchInKeys && key.contains(input.pattern, ignoreCase = true)
                    val valueMatches =
                        input.searchInValues &&
                            value.value.contains(input.pattern, ignoreCase = true)

                    if (keyMatches || valueMatches) {
                        matches.add(
                            "${file.fileName}.$key = ${value.value} (${value.originalType})"
                        )
                    }
                }
            }

            val output = buildString {
                appendLine("Search Results for pattern: '${input.pattern}'")
                appendLine("Found ${matches.size} matches:")
                matches.forEach { match -> appendLine("  - $match") }
            }

            CallToolResult(content = listOf(TextContent(text = output)), isError = false)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to search preferences", e)
            CallToolResult(
                content = listOf(TextContent(text = "Failed to search preferences: ${e.message}")),
                isError = true,
            )
        }
    }

    private suspend fun setPreference(input: PreferencesSetInput): CallToolResult {
        return try {
            if (input.dryRun) {
                return CallToolResult(
                    content =
                        listOf(
                            TextContent(
                                text =
                                    "DRY RUN: Would set ${input.fileName}.${input.key} = ${input.value} (${input.type})"
                            )
                        ),
                    isError = false,
                )
            }

            val type = SharedPreferencesProvider.PreferenceType.valueOf(input.type.uppercase())
            val result =
                preferencesProvider.setPreferenceValue(input.fileName, input.key, input.value, type)

            if (result.isSuccess) {
                val output = buildString {
                    appendLine("Preference set successfully:")
                    appendLine("File: ${input.fileName}")
                    appendLine("Key: ${input.key}")
                    appendLine("Value: ${input.value}")
                    appendLine("Type: ${input.type}")
                }
                CallToolResult(content = listOf(TextContent(text = output)), isError = false)
            } else {
                CallToolResult(
                    content =
                        listOf(
                            TextContent(
                                text =
                                    "Failed to set preference: ${result.exceptionOrNull()?.message}"
                            )
                        ),
                    isError = true,
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set preference", e)
            CallToolResult(
                content = listOf(TextContent(text = "Failed to set preference: ${e.message}")),
                isError = true,
            )
        }
    }

    private suspend fun removePreference(input: PreferencesRemoveInput): CallToolResult {
        return try {
            if (!input.confirm) {
                return CallToolResult(
                    content =
                        listOf(
                            TextContent(
                                text =
                                    "Remove operation requires confirmation. Set 'confirm' to true to proceed."
                            )
                        ),
                    isError = true,
                )
            }

            val result = preferencesProvider.removePreferenceKey(input.fileName, input.key)

            if (result.isSuccess) {
                val output = buildString {
                    appendLine("Preference removed successfully:")
                    appendLine("File: ${input.fileName}")
                    appendLine("Key: ${input.key}")
                }
                CallToolResult(content = listOf(TextContent(text = output)), isError = false)
            } else {
                CallToolResult(
                    content =
                        listOf(
                            TextContent(
                                text =
                                    "Failed to remove preference: ${result.exceptionOrNull()?.message}"
                            )
                        ),
                    isError = true,
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to remove preference", e)
            CallToolResult(
                content = listOf(TextContent(text = "Failed to remove preference: ${e.message}")),
                isError = true,
            )
        }
    }

    private suspend fun clearPreferences(input: PreferencesClearInput): CallToolResult {
        return try {
            if (!input.confirm) {
                return CallToolResult(
                    content =
                        listOf(
                            TextContent(
                                text =
                                    "Clear operation requires confirmation. Set 'confirm' to true to proceed."
                            )
                        ),
                    isError = true,
                )
            }

            val result = preferencesProvider.clearPreferences(input.fileName)

            if (result.isSuccess) {
                val output = buildString {
                    appendLine("Preferences cleared successfully:")
                    appendLine("File: ${input.fileName}")
                }
                CallToolResult(content = listOf(TextContent(text = output)), isError = false)
            } else {
                CallToolResult(
                    content =
                        listOf(
                            TextContent(
                                text =
                                    "Failed to clear preferences: ${result.exceptionOrNull()?.message}"
                            )
                        ),
                    isError = true,
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear preferences", e)
            CallToolResult(
                content = listOf(TextContent(text = "Failed to clear preferences: ${e.message}")),
                isError = true,
            )
        }
    }

    private suspend fun exportPreferences(input: PreferencesExportInput): CallToolResult {
        return try {
            val files =
                if (input.fileName != null) {
                    listOf(input.fileName)
                } else {
                    preferencesProvider.getAllPreferenceFiles().map { it.fileName }
                }

            val output = buildString {
                appendLine("Preferences Export:")

                for (fileName in files) {
                    try {
                        val content = preferencesProvider.getPreferencesContent(fileName)
                        appendLine()
                        appendLine("=== $fileName ===")
                        appendLine(formatPreferencesContent(content, input.outputFormat))
                    } catch (e: Exception) {
                        appendLine("Error exporting $fileName: ${e.message}")
                    }
                }
            }

            CallToolResult(content = listOf(TextContent(text = output)), isError = false)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to export preferences", e)
            CallToolResult(
                content = listOf(TextContent(text = "Failed to export preferences: ${e.message}")),
                isError = true,
            )
        }
    }

    private suspend fun batchEditPreferences(input: PreferencesBatchEditInput): CallToolResult {
        return try {
            if (input.dryRun) {
                val output = buildString {
                    appendLine("DRY RUN: Batch operations for ${input.fileName}:")
                    input.operations.forEach { op ->
                        when (op.action.uppercase()) {
                            "SET" -> appendLine("  SET ${op.key} = ${op.value} (${op.type})")
                            "REMOVE" -> appendLine("  REMOVE ${op.key}")
                        }
                    }
                }
                return CallToolResult(content = listOf(TextContent(text = output)), isError = false)
            }

            val results = mutableListOf<String>()
            var successCount = 0
            var errorCount = 0

            for (operation in input.operations) {
                try {
                    when (operation.action.uppercase()) {
                        "SET" -> {
                            if (operation.value != null) {
                                val type =
                                    SharedPreferencesProvider.PreferenceType.valueOf(
                                        operation.type.uppercase()
                                    )
                                val result =
                                    preferencesProvider.setPreferenceValue(
                                        input.fileName,
                                        operation.key,
                                        operation.value,
                                        type,
                                    )
                                if (result.isSuccess) {
                                    results.add("✓ SET ${operation.key} = ${operation.value}")
                                    successCount++
                                } else {
                                    results.add(
                                        "✗ SET ${operation.key} failed: ${result.exceptionOrNull()?.message}"
                                    )
                                    errorCount++
                                }
                            } else {
                                results.add("✗ SET ${operation.key} failed: value is required")
                                errorCount++
                            }
                        }

                        "REMOVE" -> {
                            val result =
                                preferencesProvider.removePreferenceKey(
                                    input.fileName,
                                    operation.key,
                                )
                            if (result.isSuccess) {
                                results.add("✓ REMOVE ${operation.key}")
                                successCount++
                            } else {
                                results.add(
                                    "✗ REMOVE ${operation.key} failed: ${result.exceptionOrNull()?.message}"
                                )
                                errorCount++
                            }
                        }

                        else -> {
                            results.add("✗ Unknown action: ${operation.action}")
                            errorCount++
                        }
                    }
                } catch (e: Exception) {
                    results.add("✗ ${operation.action} ${operation.key} failed: ${e.message}")
                    errorCount++
                }
            }

            val output = buildString {
                appendLine("Batch operation results for ${input.fileName}:")
                appendLine("Success: $successCount, Errors: $errorCount")
                appendLine()
                results.forEach { result -> appendLine("  $result") }
            }

            CallToolResult(content = listOf(TextContent(text = output)), isError = errorCount > 0)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to perform batch operations", e)
            CallToolResult(
                content =
                    listOf(TextContent(text = "Failed to perform batch operations: ${e.message}")),
                isError = true,
            )
        }
    }

    private fun formatPreferencesContent(
        content: SharedPreferencesProvider.PreferencesContent,
        format: String,
    ): String {
        return when (format.lowercase()) {
            "json" -> formatAsJson(content)
            "table" -> formatAsTable(content)
            "csv" -> formatAsCsv(content)
            else -> formatAsJson(content)
        }
    }

    private fun formatAsJson(content: SharedPreferencesProvider.PreferencesContent): String {
        return try {
            Json.encodeToString(
                kotlinx.serialization.json.JsonObject.serializer(),
                buildJsonObject {
                    put("fileName", content.fileName)
                    put("lastModified", content.lastModified)
                    put("size", content.size)
                    put(
                        "preferences",
                        buildJsonObject {
                            content.preferences.forEach { (key, value) ->
                                put(
                                    key,
                                    buildJsonObject {
                                        put("value", value.value)
                                        put("type", value.originalType.name)
                                        put("lastModified", value.lastModified)
                                    },
                                )
                            }
                        },
                    )
                },
            )
        } catch (e: Exception) {
            "Error formatting JSON: ${e.message}"
        }
    }

    private fun formatAsTable(content: SharedPreferencesProvider.PreferencesContent): String {
        return buildString {
            appendLine("Preferences for ${content.fileName}:")
            appendLine("Last Modified: ${java.util.Date(content.lastModified)}")
            appendLine("Total Keys: ${content.size}")
            appendLine()

            if (content.preferences.isEmpty()) {
                appendLine("No preferences found")
            } else {
                appendLine("Key".padEnd(30) + "Type".padEnd(15) + "Value")
                appendLine("-".repeat(75))

                content.preferences.forEach { (_, value) ->
                    val truncatedValue =
                        if (value.value.length > 30) {
                            value.value.take(27) + "..."
                        } else {
                            value.value
                        }

                    appendLine(
                        value.key.padEnd(30) + value.originalType.name.padEnd(15) + truncatedValue
                    )
                }
            }
        }
    }

    private fun formatAsCsv(content: SharedPreferencesProvider.PreferencesContent): String {
        return buildString {
            appendLine("Key,Type,Value,LastModified")
            content.preferences.forEach { (_, value) ->
                val csvValue = value.value.replace("\"", "\"\"")
                appendLine(
                    "${value.key},${value.originalType.name},\"$csvValue\",${value.lastModified}"
                )
            }
        }
    }
}
