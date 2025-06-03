package dev.jasonpearson.androidmcpsdk.debugbridge.tools

import android.content.Context
import android.util.Log
import dev.jasonpearson.androidmcpsdk.core.features.tools.McpToolProvider
import dev.jasonpearson.androidmcpsdk.core.features.tools.addTool
import dev.jasonpearson.androidmcpsdk.debugbridge.database.DatabaseOperations
import dev.jasonpearson.androidmcpsdk.debugbridge.database.StandardSqliteDatabaseFactory
import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.TextContent
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Provides database tools for the debug bridge. */
class DatabaseToolProvider(private val context: Context) {

    companion object {
        private const val TAG = "DatabaseToolProvider"
    }

    private val databaseOperations =
        DatabaseOperations(context = context, databaseFactory = StandardSqliteDatabaseFactory())

    @Serializable
    data class DatabaseQueryInput(
        val databasePath: String,
        val query: String,
        val parameters: List<String> = emptyList(),
        val pageSize: Int = 100,
        val pageOffset: Int = 0,
        val outputFormat: String = "json",
    )

    @Serializable
    data class DatabaseInsertInput(
        val databasePath: String,
        val tableName: String,
        val data: Map<String, String>, // Using String values for serialization simplicity
        val dryRun: Boolean = false,
    )

    @Serializable
    data class DatabaseUpdateInput(
        val databasePath: String,
        val tableName: String,
        val data: Map<String, String>,
        val whereClause: String,
        val whereArgs: List<String> = emptyList(),
        val dryRun: Boolean = false,
    )

    @Serializable
    data class DatabaseDeleteInput(
        val databasePath: String,
        val tableName: String,
        val whereClause: String,
        val whereArgs: List<String> = emptyList(),
        val confirm: Boolean = false,
    )

    @Serializable
    data class DatabaseSchemaInput(val databasePath: String, val tableName: String? = null)

    @Serializable data class EmptyInput(val placeholder: String? = null)

    fun registerTools(toolProvider: McpToolProvider) {
        Log.d(TAG, "Registering database tools")

        // Database query tool
        toolProvider.addTool<DatabaseQueryInput>(
            name = "database_query",
            description = "Execute SQL queries on application databases with safety validation",
        ) { input ->
            executeQuery(input)
        }

        // Database insert tool
        toolProvider.addTool<DatabaseInsertInput>(
            name = "database_insert",
            description = "Insert new record into database table",
        ) { input ->
            insertRecord(input)
        }

        // Database update tool
        toolProvider.addTool<DatabaseUpdateInput>(
            name = "database_update",
            description = "Update existing database records",
        ) { input ->
            updateRecords(input)
        }

        // Database delete tool
        toolProvider.addTool<DatabaseDeleteInput>(
            name = "database_delete",
            description = "Delete records from database table",
        ) { input ->
            deleteRecords(input)
        }

        // Database schema tool
        toolProvider.addTool<DatabaseSchemaInput>(
            name = "database_schema",
            description = "Get database schema information",
        ) { input ->
            getDatabaseSchema(input)
        }

        // List databases tool
        toolProvider.addTool<EmptyInput>(
            name = "database_list",
            description = "List available database files in the application",
        ) { _ ->
            listDatabases()
        }

        Log.d(TAG, "Database tools registered")
    }

    private suspend fun executeQuery(input: DatabaseQueryInput): CallToolResult {
        return try {
            val result =
                databaseOperations.executeQuery(
                    databasePath = input.databasePath,
                    query = input.query,
                    parameters = input.parameters.toTypedArray(),
                    pageSize = input.pageSize,
                    pageOffset = input.pageOffset,
                )

            if (result.success && result.data != null) {
                val queryResult = result.data
                val output = buildString {
                    appendLine("Query executed successfully:")
                    appendLine("- Execution time: ${result.executionTimeMs}ms")
                    appendLine("- Rows returned: ${queryResult.rowCount}")
                    appendLine("- Has more data: ${queryResult.hasMore}")
                    appendLine()

                    when (input.outputFormat.lowercase()) {
                        "json" -> {
                            appendLine("Results (JSON):")
                            appendLine(formatAsJson(queryResult))
                        }

                        "csv" -> {
                            appendLine("Results (CSV):")
                            appendLine(formatAsCsv(queryResult))
                        }

                        "table" -> {
                            appendLine("Results (Table):")
                            appendLine(formatAsTable(queryResult))
                        }

                        else -> {
                            appendLine("Results (JSON - default):")
                            appendLine(formatAsJson(queryResult))
                        }
                    }
                }

                CallToolResult(content = listOf(TextContent(text = output)), isError = false)
            } else {
                CallToolResult(
                    content = listOf(TextContent(text = "Query failed: ${result.error}")),
                    isError = true,
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Query execution failed", e)
            CallToolResult(
                content = listOf(TextContent(text = "Query failed: ${e.message}")),
                isError = true,
            )
        }
    }

    private suspend fun insertRecord(input: DatabaseInsertInput): CallToolResult {
        return try {
            if (input.dryRun) {
                return CallToolResult(
                    content =
                        listOf(
                            TextContent(
                                text =
                                    "DRY RUN: Would insert record into ${input.tableName} with data: ${input.data}"
                            )
                        ),
                    isError = false,
                )
            }

            // Convert string values to appropriate types
            val typedData = convertStringDataToTypes(input.data)

            val result =
                databaseOperations.insertRecord(
                    databasePath = input.databasePath,
                    tableName = input.tableName,
                    data = typedData,
                )

            if (result.success && result.data != null) {
                val output = buildString {
                    appendLine("Record inserted successfully:")
                    appendLine("- Insert ID: ${result.lastInsertId}")
                    appendLine("- Rows affected: ${result.rowsAffected}")
                    appendLine("- Execution time: ${result.executionTimeMs}ms")
                }

                CallToolResult(content = listOf(TextContent(text = output)), isError = false)
            } else {
                CallToolResult(
                    content = listOf(TextContent(text = "Insert failed: ${result.error}")),
                    isError = true,
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Insert operation failed", e)
            CallToolResult(
                content = listOf(TextContent(text = "Insert failed: ${e.message}")),
                isError = true,
            )
        }
    }

    private suspend fun updateRecords(input: DatabaseUpdateInput): CallToolResult {
        return try {
            if (input.dryRun) {
                return CallToolResult(
                    content =
                        listOf(
                            TextContent(
                                text =
                                    "DRY RUN: Would update records in ${input.tableName} where ${input.whereClause} with data: ${input.data}"
                            )
                        ),
                    isError = false,
                )
            }

            // Convert string values to appropriate types
            val typedData = convertStringDataToTypes(input.data)

            val result =
                databaseOperations.updateRecords(
                    databasePath = input.databasePath,
                    tableName = input.tableName,
                    data = typedData,
                    whereClause = input.whereClause,
                    whereArgs = input.whereArgs.toTypedArray(),
                )

            if (result.success && result.data != null) {
                val output = buildString {
                    appendLine("Records updated successfully:")
                    appendLine("- Rows affected: ${result.rowsAffected}")
                    appendLine("- Execution time: ${result.executionTimeMs}ms")
                }

                CallToolResult(content = listOf(TextContent(text = output)), isError = false)
            } else {
                CallToolResult(
                    content = listOf(TextContent(text = "Update failed: ${result.error}")),
                    isError = true,
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Update operation failed", e)
            CallToolResult(
                content = listOf(TextContent(text = "Update failed: ${e.message}")),
                isError = true,
            )
        }
    }

    private suspend fun deleteRecords(input: DatabaseDeleteInput): CallToolResult {
        return try {
            if (!input.confirm) {
                return CallToolResult(
                    content =
                        listOf(
                            TextContent(
                                text =
                                    "Delete operation requires confirmation. Set 'confirm' to true to proceed."
                            )
                        ),
                    isError = true,
                )
            }

            val result =
                databaseOperations.deleteRecords(
                    databasePath = input.databasePath,
                    tableName = input.tableName,
                    whereClause = input.whereClause,
                    whereArgs = input.whereArgs.toTypedArray(),
                )

            if (result.success && result.data != null) {
                val output = buildString {
                    appendLine("Records deleted successfully:")
                    appendLine("- Rows affected: ${result.rowsAffected}")
                    appendLine("- Execution time: ${result.executionTimeMs}ms")
                }

                CallToolResult(content = listOf(TextContent(text = output)), isError = false)
            } else {
                CallToolResult(
                    content = listOf(TextContent(text = "Delete failed: ${result.error}")),
                    isError = true,
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Delete operation failed", e)
            CallToolResult(
                content = listOf(TextContent(text = "Delete failed: ${e.message}")),
                isError = true,
            )
        }
    }

    private suspend fun getDatabaseSchema(input: DatabaseSchemaInput): CallToolResult {
        return try {
            val result = databaseOperations.getDatabaseSchema(input.databasePath)

            if (result.success && result.data != null) {
                val metadata = result.data
                val output = buildString {
                    appendLine("Database Schema Information:")
                    appendLine("- Path: ${metadata.path}")
                    appendLine("- Version: ${metadata.version}")
                    appendLine("- Tables: ${metadata.tables.size}")
                    appendLine("- Views: ${metadata.views.size}")
                    appendLine("- Triggers: ${metadata.triggers.size}")
                    appendLine()

                    if (input.tableName != null) {
                        val table = metadata.tables.find { it.name == input.tableName }
                        if (table != null) {
                            appendLine("Table: ${table.name}")
                            appendLine("Columns:")
                            table.columns.forEach { column ->
                                val keyInfo = if (column.primaryKey) " (PRIMARY KEY)" else ""
                                val nullInfo = if (column.nullable) "" else " NOT NULL"
                                val defaultInfo =
                                    column.defaultValue?.let { " DEFAULT '$it'" } ?: ""
                                appendLine(
                                    "  - ${column.name}: ${column.type}$keyInfo$nullInfo$defaultInfo"
                                )
                            }

                            if (table.indexes.isNotEmpty()) {
                                appendLine("Indexes:")
                                table.indexes.forEach { index ->
                                    val uniqueInfo = if (index.unique) " (UNIQUE)" else ""
                                    appendLine(
                                        "  - ${index.name}: ${index.columns.joinToString(", ")}$uniqueInfo"
                                    )
                                }
                            }

                            if (table.foreignKeys.isNotEmpty()) {
                                appendLine("Foreign Keys:")
                                table.foreignKeys.forEach { fk ->
                                    appendLine(
                                        "  - ${fk.column} -> ${fk.referencedTable}.${fk.referencedColumn}"
                                    )
                                }
                            }
                        } else {
                            appendLine("Table '${input.tableName}' not found")
                        }
                    } else {
                        appendLine("Tables:")
                        metadata.tables.forEach { table ->
                            appendLine("  - ${table.name} (${table.columns.size} columns)")
                        }

                        if (metadata.views.isNotEmpty()) {
                            appendLine()
                            appendLine("Views:")
                            metadata.views.forEach { view -> appendLine("  - $view") }
                        }

                        if (metadata.triggers.isNotEmpty()) {
                            appendLine()
                            appendLine("Triggers:")
                            metadata.triggers.forEach { trigger -> appendLine("  - $trigger") }
                        }
                    }
                }

                CallToolResult(content = listOf(TextContent(text = output)), isError = false)
            } else {
                CallToolResult(
                    content =
                        listOf(TextContent(text = "Schema retrieval failed: ${result.error}")),
                    isError = true,
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Schema retrieval failed", e)
            CallToolResult(
                content = listOf(TextContent(text = "Schema retrieval failed: ${e.message}")),
                isError = true,
            )
        }
    }

    private suspend fun listDatabases(): CallToolResult {
        return try {
            val databases = databaseOperations.listDatabaseFiles()

            val output = buildString {
                appendLine("Available Database Files:")
                if (databases.isEmpty()) {
                    appendLine("No database files found in application directory")
                } else {
                    databases.forEach { dbPath -> appendLine("  - $dbPath") }
                }
                appendLine()
                appendLine(
                    "Note: Only databases in the application's database directory are listed."
                )
                appendLine("To query external databases, provide the full path.")
            }

            CallToolResult(content = listOf(TextContent(text = output)), isError = false)
        } catch (e: Exception) {
            Log.e(TAG, "Database listing failed", e)
            CallToolResult(
                content = listOf(TextContent(text = "Database listing failed: ${e.message}")),
                isError = true,
            )
        }
    }

    private fun convertStringDataToTypes(data: Map<String, String>): Map<String, Any?> {
        return data.mapValues { (_, value) ->
            when {
                value.equals("null", ignoreCase = true) -> null
                value.equals("true", ignoreCase = true) -> true
                value.equals("false", ignoreCase = true) -> false
                value.toLongOrNull() != null -> value.toLong()
                value.toDoubleOrNull() != null -> value.toDouble()
                else -> value
            }
        }
    }

    private fun formatAsJson(
        queryResult: dev.jasonpearson.androidmcpsdk.debugbridge.database.QueryResult
    ): String {
        return try {
            Json.encodeToString(
                kotlinx.serialization.json.JsonArray.serializer(),
                kotlinx.serialization.json.buildJsonArray {
                    queryResult.rows.forEach { row ->
                        add(
                            buildJsonObject {
                                row.forEach { (key, value) ->
                                    when (value) {
                                        null -> put(key, kotlinx.serialization.json.JsonNull)
                                        is String -> put(key, value)
                                        is Number -> put(key, value)
                                        is Boolean -> put(key, value)
                                        else -> put(key, value.toString())
                                    }
                                }
                            }
                        )
                    }
                },
            )
        } catch (e: Exception) {
            "Error formatting JSON: ${e.message}"
        }
    }

    private fun formatAsCsv(
        queryResult: dev.jasonpearson.androidmcpsdk.debugbridge.database.QueryResult
    ): String {
        return buildString {
            // Headers
            appendLine(queryResult.columnNames.joinToString(","))

            // Data rows
            queryResult.rows.forEach { row ->
                val values =
                    queryResult.columnNames.map { columnName ->
                        val value = row[columnName]?.toString() ?: ""
                        if (value.contains(",") || value.contains("\"")) {
                            "\"${value.replace("\"", "\"\"")}\""
                        } else {
                            value
                        }
                    }
                appendLine(values.joinToString(","))
            }
        }
    }

    private fun formatAsTable(
        queryResult: dev.jasonpearson.androidmcpsdk.debugbridge.database.QueryResult
    ): String {
        if (queryResult.rows.isEmpty()) {
            return "No data returned"
        }

        // Calculate column widths
        val columnWidths =
            queryResult.columnNames.associateWith { columnName ->
                maxOf(
                    columnName.length,
                    queryResult.rows.maxOfOrNull { row -> row[columnName]?.toString()?.length ?: 0 }
                        ?: 0,
                )
            }

        return buildString {
            // Header
            val header =
                queryResult.columnNames.joinToString(" | ") { columnName ->
                    columnName.padEnd(columnWidths[columnName]!!)
                }
            appendLine(header)

            // Separator
            val separator =
                queryResult.columnNames.joinToString("-|-") { columnName ->
                    "-".repeat(columnWidths[columnName]!!)
                }
            appendLine(separator)

            // Data rows
            queryResult.rows.forEach { row ->
                val rowString =
                    queryResult.columnNames.joinToString(" | ") { columnName ->
                        val value = row[columnName]?.toString() ?: ""
                        value.padEnd(columnWidths[columnName]!!)
                    }
                appendLine(rowString)
            }
        }
    }
}
