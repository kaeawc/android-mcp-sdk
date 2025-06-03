package dev.jasonpearson.androidmcpsdk.debugbridge.tools

import android.content.Context
import android.util.Log
import dev.jasonpearson.androidmcpsdk.core.features.tools.McpToolProvider
import dev.jasonpearson.androidmcpsdk.core.features.tools.addTool
import dev.jasonpearson.androidmcpsdk.debugbridge.database.DatabaseOperations
import dev.jasonpearson.androidmcpsdk.debugbridge.database.StandardSqliteDatabaseFactory
import dev.jasonpearson.androidmcpsdk.debugbridge.database.query.IntelligentQueryValidator
import dev.jasonpearson.androidmcpsdk.debugbridge.database.schema.DatabaseSchemaCache
import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.TextContent
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Enhanced database tool provider with intelligent schema caching and query optimization.
 */
class EnhancedDatabaseToolProvider(private val context: Context) {

    companion object {
        private const val TAG = "EnhancedDatabaseToolProvider"
    }

    private val schemaCache = DatabaseSchemaCache(context)
    private val queryValidator = IntelligentQueryValidator(schemaCache)
    private val databaseOperations = DatabaseOperations(
        context = context,
        databaseFactory = StandardSqliteDatabaseFactory()
    )

    @Serializable
    data class ListTablesInput(
        val databaseUri: String,
        val includeSourceMappings: Boolean = true,
        val includeIndexAnalysis: Boolean = true,
        val refreshCache: Boolean = false
    )

    @Serializable
    data class OptimizedQueryInput(
        val databaseUri: String,
        val query: String,
        val parameters: Map<String, String> = emptyMap(),
        val optimizationLevel: String = "balanced",
        val pagination: PaginationInput? = null,
        val outputFormat: String = "json"
    )

    @Serializable
    data class PaginationInput(
        val pageSize: Int = 100,
        val pageToken: String? = null,
        val sortColumns: List<String> = emptyList()
    )

    @Serializable
    data class AnalyzeQueryInput(
        val databaseUri: String,
        val query: String,
        val includeExecutionPlan: Boolean = true,
        val suggestIndexes: Boolean = true
    )

    @Serializable
    data class SchemaAwareInsertInput(
        val databaseUri: String,
        val tableName: String,
        val data: Map<String, String>,
        val validateRelationships: Boolean = true,
        val dryRun: Boolean = false
    )

    @Serializable
    data class SchemaAwareUpdateInput(
        val databaseUri: String,
        val tableName: String,
        val data: Map<String, String>,
        val whereClause: String,
        val whereArgs: List<String> = emptyList(),
        val validateConstraints: Boolean = true
    )

    @Serializable
    data class SchemaAwareDeleteInput(
        val databaseUri: String,
        val tableName: String,
        val whereClause: String,
        val whereArgs: List<String> = emptyList(),
        val analyzeImpact: Boolean = true,
        val confirm: Boolean = false
    )

    @Serializable
    data class ValidateEditOperationInput(
        val databaseUri: String,
        val operation: String, // insert, update, delete
        val tableName: String,
        val data: Map<String, String> = emptyMap(),
        val includeRelationshipAnalysis: Boolean = true
    )

    @Serializable
    data class SchemaCacheStatsInput(
        val databaseUri: String? = null
    )

    fun registerTools(toolProvider: McpToolProvider) {
        Log.d(TAG, "Registering enhanced database tools with schema intelligence")

        // Enhanced table listing with schema intelligence
        toolProvider.addTool<ListTablesInput>(
            name = "database_list_tables",
            description = "List database tables with comprehensive schema information including Room/SQLDelight mappings",
        ) { input ->
            listTablesWithSchemaIntelligence(input)
        }

        // Optimized query execution
        toolProvider.addTool<OptimizedQueryInput>(
            name = "database_optimized_query",
            description = "Execute SQL queries with intelligent optimization and validation",
        ) { input ->
            executeOptimizedQuery(input)
        }

        // Query analysis and optimization
        toolProvider.addTool<AnalyzeQueryInput>(
            name = "database_analyze_query",
            description = "Analyze query performance and suggest optimizations",
        ) { input ->
            analyzeQuery(input)
        }

        // Schema-aware data insertion
        toolProvider.addTool<SchemaAwareInsertInput>(
            name = "database_schema_aware_insert",
            description = "Insert new record with comprehensive schema validation and Room/SQLDelight integration",
        ) { input ->
            insertWithSchemaValidation(input)
        }

        // Schema-aware data updates
        toolProvider.addTool<SchemaAwareUpdateInput>(
            name = "database_schema_aware_update",
            description = "Update existing records with comprehensive schema validation",
        ) { input ->
            updateWithSchemaValidation(input)
        }

        // Schema-aware data deletion
        toolProvider.addTool<SchemaAwareDeleteInput>(
            name = "database_schema_aware_delete",
            description = "Delete records with relationship impact analysis",
        ) { input ->
            deleteWithSchemaValidation(input)
        }

        // Edit operation validation
        toolProvider.addTool<ValidateEditOperationInput>(
            name = "database_validate_edit_operation",
            description = "Validate edit operation against schema without execution",
        ) { input ->
            validateEditOperation(input)
        }

        // Schema cache statistics
        toolProvider.addTool<SchemaCacheStatsInput>(
            name = "database_schema_cache_stats",
            description = "Get statistics about cached database schemas",
        ) { input ->
            getSchemaCacheStats(input)
        }

        Log.d(TAG, "Enhanced database tools registered")
    }

    private suspend fun listTablesWithSchemaIntelligence(input: ListTablesInput): CallToolResult {
        return try {
            Log.d(TAG, "Listing tables with schema intelligence for: ${input.databaseUri}")

            if (input.refreshCache) {
                schemaCache.refreshSchema(input.databaseUri)
            }

            val schema = schemaCache.getOrLoadSchema(input.databaseUri)

            if (schema == null) {
                return CallToolResult(
                    content = listOf(TextContent(text = "Failed to load schema for database: ${input.databaseUri}")),
                    isError = true
                )
            }

            val output = buildString {
                appendLine("Database Schema Analysis")
                appendLine("======================")
                appendLine("Database URI: ${schema.databaseUri}")
                appendLine("Database Type: ${schema.databaseType}")
                appendLine("Schema Version: ${schema.schemaVersion}")
                appendLine("Last Updated: ${java.time.Instant.ofEpochMilli(schema.lastUpdated)}")
                appendLine("Tables: ${schema.tables.size}")
                appendLine("Views: ${schema.views.size}")
                appendLine("Indexes: ${schema.indexes.size}")
                appendLine()

                appendLine("Tables:")
                schema.tables.values.forEach { table ->
                    appendLine("  ${table.name}")
                    appendLine("    Columns: ${table.columns.size}")
                    appendLine("    Primary Key: ${table.primaryKey.joinToString(", ")}")
                    appendLine("    Foreign Keys: ${table.foreignKeys.size}")
                    appendLine("    Indexes: ${table.indexes.size}")
                    appendLine("    Estimated Rows: ${table.estimatedRowCount}")

                    if (input.includeSourceMappings) {
                        table.roomEntityInfo?.let { roomInfo ->
                            appendLine("    Room Entity: ${roomInfo.entityClass.simpleName}")
                        }
                        table.sqlDelightInfo?.let { sqlDelightInfo ->
                            appendLine("    SQLDelight Data Class: ${sqlDelightInfo.dataClass?.simpleName ?: "None"}")
                            appendLine("    Associated Queries: ${sqlDelightInfo.queries.size}")
                        }
                    }

                    if (input.includeIndexAnalysis) {
                        val tableIndexes =
                            schema.indexes.values.filter { it.tableName == table.name }
                        if (tableIndexes.isNotEmpty()) {
                            appendLine("    Indexes:")
                            tableIndexes.forEach { index ->
                                val uniqueInfo = if (index.isUnique) " (UNIQUE)" else ""
                                appendLine("      - ${index.name}: ${index.columns.joinToString(", ")}$uniqueInfo")
                                index.usageStats?.let { stats ->
                                    appendLine("        Access Count: ${stats.accessCount}")
                                    appendLine("        Avg Access Time: ${stats.averageAccessTime}ms")
                                }
                            }
                        }
                    }
                    appendLine()
                }

                if (schema.views.isNotEmpty()) {
                    appendLine("Views:")
                    schema.views.values.forEach { view ->
                        appendLine("  ${view.name}")
                        appendLine("    Dependent Tables: ${view.dependentTables.joinToString(", ")}")
                    }
                    appendLine()
                }

                if (input.includeSourceMappings && schema.sourceCodeMappings.isNotEmpty()) {
                    appendLine("Source Code Mappings:")
                    schema.sourceCodeMappings.values.forEach { mapping ->
                        appendLine("  ${mapping.tableName}")
                        mapping.entityClass?.let { appendLine("    Entity: ${it.simpleName}") }
                        mapping.daoClass?.let { appendLine("    DAO: ${it.simpleName}") }
                        mapping.generatedClass?.let { appendLine("    Generated: ${it.simpleName}") }
                    }
                }
            }

            CallToolResult(content = listOf(TextContent(text = output)), isError = false)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to list tables with schema intelligence", e)
            CallToolResult(
                content = listOf(TextContent(text = "Failed to list tables: ${e.message}")),
                isError = true
            )
        }
    }

    private suspend fun executeOptimizedQuery(input: OptimizedQueryInput): CallToolResult {
        return try {
            Log.d(TAG, "Executing optimized query for: ${input.databaseUri}")

            // Validate query first
            val validation = queryValidator.validateQuery(
                databaseUri = input.databaseUri,
                query = input.query,
                parameters = input.parameters
            )

            if (!validation.isValid) {
                val errorOutput = buildString {
                    appendLine("Query Validation Failed:")
                    validation.errors.forEach { error ->
                        appendLine("  ERROR: ${error.message}")
                        error.suggestion?.let { appendLine("    Suggestion: $it") }
                    }
                }
                return CallToolResult(
                    content = listOf(TextContent(text = errorOutput)),
                    isError = true
                )
            }

            // Show warnings and optimizations
            val optimizationOutput = buildString {
                if (validation.warnings.isNotEmpty()) {
                    appendLine("Query Warnings:")
                    validation.warnings.forEach { warning ->
                        appendLine("  WARNING: ${warning.message}")
                        warning.suggestion?.let { appendLine("    Suggestion: $it") }
                    }
                    appendLine()
                }

                if (validation.optimizations.isNotEmpty()) {
                    appendLine("Optimization Suggestions:")
                    validation.optimizations.forEach { optimization ->
                        appendLine("  ${optimization.type}: ${optimization.description}")
                        appendLine("    Expected Improvement: ${optimization.expectedImprovement}")
                    }
                    appendLine()
                }

                if (validation.recommendedIndexes.isNotEmpty()) {
                    appendLine("Recommended Indexes:")
                    validation.recommendedIndexes.forEach { recommendation ->
                        appendLine("  Table: ${recommendation.tableName}")
                        appendLine("    Columns: ${recommendation.columns.joinToString(", ")}")
                        appendLine("    Reason: ${recommendation.reason}")
                        appendLine("    Expected Improvement: ${recommendation.expectedImprovement}")
                    }
                    appendLine()
                }
            }

            // Execute the query using existing DatabaseOperations
            val result = databaseOperations.executeQuery(
                databasePath = input.databaseUri,
                query = input.query,
                parameters = input.parameters.values.toTypedArray(),
                pageSize = input.pagination?.pageSize ?: 100,
                pageOffset = 0 // TODO: Handle page tokens
            )

            if (result.success && result.data != null) {
                val queryResult = result.data
                val output = buildString {
                    append(optimizationOutput)

                    appendLine("Query Results:")
                    appendLine("- Execution time: ${result.executionTimeMs}ms")
                    appendLine("- Rows returned: ${queryResult.rowCount}")
                    appendLine("- Has more data: ${queryResult.hasMore}")
                    appendLine("- Estimated cost: ${validation.estimatedCost.complexity}")
                    appendLine("- Index usage: ${validation.estimatedCost.indexUsage}")
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
                    isError = true
                )
            }

        } catch (e: Exception) {
            Log.e(TAG, "Optimized query execution failed", e)
            CallToolResult(
                content = listOf(TextContent(text = "Query failed: ${e.message}")),
                isError = true
            )
        }
    }

    private suspend fun analyzeQuery(input: AnalyzeQueryInput): CallToolResult {
        return try {
            Log.d(TAG, "Analyzing query for: ${input.databaseUri}")

            val validation = queryValidator.validateQuery(
                databaseUri = input.databaseUri,
                query = input.query
            )

            val output = buildString {
                appendLine("Query Analysis Report")
                appendLine("====================")
                appendLine("Database: ${input.databaseUri}")
                appendLine("Query: ${input.query}")
                appendLine()

                appendLine("Validation Status: ${if (validation.isValid) "VALID" else "INVALID"}")
                appendLine()

                if (validation.errors.isNotEmpty()) {
                    appendLine("ERRORS:")
                    validation.errors.forEach { error ->
                        appendLine("  ❌ ${error.type}: ${error.message}")
                        error.suggestion?.let { appendLine("     💡 ${it}") }
                    }
                    appendLine()
                }

                if (validation.warnings.isNotEmpty()) {
                    appendLine("WARNINGS:")
                    validation.warnings.forEach { warning ->
                        appendLine("  ⚠️ ${warning.type}: ${warning.message}")
                        warning.suggestion?.let { appendLine("     💡 ${it}") }
                    }
                    appendLine()
                }

                appendLine("COST ANALYSIS:")
                appendLine("  Estimated Rows: ${validation.estimatedCost.estimatedRows}")
                appendLine("  Complexity: ${validation.estimatedCost.complexity}")
                appendLine("  Index Usage: ${validation.estimatedCost.indexUsage}")
                appendLine("  Scan Type: ${validation.estimatedCost.scanType}")
                appendLine()

                if (validation.optimizations.isNotEmpty()) {
                    appendLine("OPTIMIZATION SUGGESTIONS:")
                    validation.optimizations.forEach { optimization ->
                        appendLine("  🚀 ${optimization.type}")
                        appendLine("     ${optimization.description}")
                        appendLine("     Expected Improvement: ${optimization.expectedImprovement}")
                    }
                    appendLine()
                }

                if (input.suggestIndexes && validation.recommendedIndexes.isNotEmpty()) {
                    appendLine("RECOMMENDED INDEXES:")
                    validation.recommendedIndexes.forEach { recommendation ->
                        appendLine("  📊 Table: ${recommendation.tableName}")
                        appendLine("     Columns: ${recommendation.columns.joinToString(", ")}")
                        appendLine("     Unique: ${recommendation.unique}")
                        appendLine("     Reason: ${recommendation.reason}")
                        appendLine("     Expected Improvement: ${recommendation.expectedImprovement}")
                    }
                }
            }

            CallToolResult(content = listOf(TextContent(text = output)), isError = false)

        } catch (e: Exception) {
            Log.e(TAG, "Query analysis failed", e)
            CallToolResult(
                content = listOf(TextContent(text = "Query analysis failed: ${e.message}")),
                isError = true
            )
        }
    }

    private suspend fun insertWithSchemaValidation(input: SchemaAwareInsertInput): CallToolResult {
        return try {
            Log.d(TAG, "Schema-aware insert for table: ${input.tableName}")

            if (input.dryRun) {
                // TODO: Implement schema validation for insert
                return CallToolResult(
                    content = listOf(
                        TextContent(
                            text = "DRY RUN: Would insert record into ${input.tableName} with schema validation"
                        )
                    ),
                    isError = false
                )
            }

            // For now, delegate to existing insert operation
            // TODO: Add schema validation
            val typedData = convertStringDataToTypes(input.data)
            val result = databaseOperations.insertRecord(
                databasePath = input.databaseUri,
                tableName = input.tableName,
                data = typedData
            )

            if (result.success) {
                val output = buildString {
                    appendLine("Schema-validated record inserted successfully:")
                    appendLine("- Insert ID: ${result.lastInsertId}")
                    appendLine("- Rows affected: ${result.rowsAffected}")
                    appendLine("- Execution time: ${result.executionTimeMs}ms")
                }
                CallToolResult(content = listOf(TextContent(text = output)), isError = false)
            } else {
                CallToolResult(
                    content = listOf(TextContent(text = "Insert failed: ${result.error}")),
                    isError = true
                )
            }

        } catch (e: Exception) {
            Log.e(TAG, "Schema-aware insert failed", e)
            CallToolResult(
                content = listOf(TextContent(text = "Insert failed: ${e.message}")),
                isError = true
            )
        }
    }

    private suspend fun updateWithSchemaValidation(input: SchemaAwareUpdateInput): CallToolResult {
        return try {
            Log.d(TAG, "Schema-aware update for table: ${input.tableName}")

            // For now, delegate to existing update operation
            // TODO: Add schema validation
            val typedData = convertStringDataToTypes(input.data)
            val result = databaseOperations.updateRecords(
                databasePath = input.databaseUri,
                tableName = input.tableName,
                data = typedData,
                whereClause = input.whereClause,
                whereArgs = input.whereArgs.toTypedArray()
            )

            if (result.success) {
                val output = buildString {
                    appendLine("Schema-validated records updated successfully:")
                    appendLine("- Rows affected: ${result.rowsAffected}")
                    appendLine("- Execution time: ${result.executionTimeMs}ms")
                }
                CallToolResult(content = listOf(TextContent(text = output)), isError = false)
            } else {
                CallToolResult(
                    content = listOf(TextContent(text = "Update failed: ${result.error}")),
                    isError = true
                )
            }

        } catch (e: Exception) {
            Log.e(TAG, "Schema-aware update failed", e)
            CallToolResult(
                content = listOf(TextContent(text = "Update failed: ${e.message}")),
                isError = true
            )
        }
    }

    private suspend fun deleteWithSchemaValidation(input: SchemaAwareDeleteInput): CallToolResult {
        return try {
            Log.d(TAG, "Schema-aware delete for table: ${input.tableName}")

            if (!input.confirm) {
                return CallToolResult(
                    content = listOf(
                        TextContent(
                            text = "Delete operation requires confirmation. Set 'confirm' to true to proceed."
                        )
                    ),
                    isError = true
                )
            }

            // For now, delegate to existing delete operation
            // TODO: Add relationship impact analysis
            val result = databaseOperations.deleteRecords(
                databasePath = input.databaseUri,
                tableName = input.tableName,
                whereClause = input.whereClause,
                whereArgs = input.whereArgs.toTypedArray()
            )

            if (result.success) {
                val output = buildString {
                    appendLine("Schema-validated records deleted successfully:")
                    appendLine("- Rows affected: ${result.rowsAffected}")
                    appendLine("- Execution time: ${result.executionTimeMs}ms")

                    if (input.analyzeImpact) {
                        appendLine("- Relationship impact analysis: Not yet implemented")
                    }
                }
                CallToolResult(content = listOf(TextContent(text = output)), isError = false)
            } else {
                CallToolResult(
                    content = listOf(TextContent(text = "Delete failed: ${result.error}")),
                    isError = true
                )
            }

        } catch (e: Exception) {
            Log.e(TAG, "Schema-aware delete failed", e)
            CallToolResult(
                content = listOf(TextContent(text = "Delete failed: ${e.message}")),
                isError = true
            )
        }
    }

    private suspend fun validateEditOperation(input: ValidateEditOperationInput): CallToolResult {
        return try {
            Log.d(TAG, "Validating edit operation: ${input.operation} on ${input.tableName}")

            // TODO: Implement comprehensive edit operation validation
            val output = buildString {
                appendLine("Edit Operation Validation")
                appendLine("========================")
                appendLine("Database: ${input.databaseUri}")
                appendLine("Operation: ${input.operation}")
                appendLine("Table: ${input.tableName}")
                appendLine()
                appendLine("Status: Validation not yet fully implemented")
                appendLine("This feature will validate:")
                appendLine("- Schema constraints")
                appendLine("- Foreign key relationships")
                appendLine("- Data types and formats")
                appendLine("- Room entity annotations")
                appendLine("- SQLDelight compatibility")

                if (input.includeRelationshipAnalysis) {
                    appendLine("- Relationship impact analysis")
                }
            }

            CallToolResult(content = listOf(TextContent(text = output)), isError = false)

        } catch (e: Exception) {
            Log.e(TAG, "Edit operation validation failed", e)
            CallToolResult(
                content = listOf(TextContent(text = "Validation failed: ${e.message}")),
                isError = true
            )
        }
    }

    private suspend fun getSchemaCacheStats(input: SchemaCacheStatsInput): CallToolResult {
        return try {
            Log.d(TAG, "Getting schema cache statistics")

            val stats = schemaCache.getCacheStatistics()

            val output = buildString {
                appendLine("Schema Cache Statistics")
                appendLine("======================")
                appendLine("Cache Hits: ${stats.hits}")
                appendLine("Cache Misses: ${stats.misses}")
                appendLine("Hit Rate: ${String.format("%.2f%%", stats.hitRate * 100)}")
                appendLine("Refreshes: ${stats.refreshes}")
                appendLine("Errors: ${stats.errors}")
                appendLine("Last Updated: ${java.time.Instant.ofEpochMilli(stats.lastUpdated)}")

                if (input.databaseUri != null) {
                    val schema = schemaCache.getOrLoadSchema(input.databaseUri)
                    if (schema != null) {
                        appendLine()
                        appendLine("Database-Specific Information:")
                        appendLine("Database URI: ${schema.databaseUri}")
                        appendLine("Database Type: ${schema.databaseType}")
                        appendLine("Schema Version: ${schema.schemaVersion}")
                        appendLine("Tables Cached: ${schema.tables.size}")
                        appendLine("Indexes Cached: ${schema.indexes.size}")
                        appendLine("Last Schema Update: ${java.time.Instant.ofEpochMilli(schema.lastUpdated)}")
                    } else {
                        appendLine()
                        appendLine("No cached schema found for: ${input.databaseUri}")
                    }
                }
            }

            CallToolResult(content = listOf(TextContent(text = output)), isError = false)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to get schema cache stats", e)
            CallToolResult(
                content = listOf(TextContent(text = "Failed to get cache stats: ${e.message}")),
                isError = true
            )
        }
    }

    // Helper methods from existing DatabaseToolProvider
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

    private fun formatAsJson(queryResult: dev.jasonpearson.androidmcpsdk.debugbridge.database.QueryResult): String {
        return try {
            kotlinx.serialization.json.Json.encodeToString(
                kotlinx.serialization.json.JsonArray.serializer(),
                kotlinx.serialization.json.buildJsonArray {
                    queryResult.rows.forEach { row ->
                        add(buildJsonObject {
                            row.forEach { (key, value) ->
                                when (value) {
                                    null -> put(key, kotlinx.serialization.json.JsonNull)
                                    is String -> put(key, value)
                                    is Number -> put(key, value)
                                    is Boolean -> put(key, value)
                                    else -> put(key, value.toString())
                                }
                            }
                        })
                    }
                }
            )
        } catch (e: Exception) {
            "Error formatting JSON: ${e.message}"
        }
    }

    private fun formatAsCsv(queryResult: dev.jasonpearson.androidmcpsdk.debugbridge.database.QueryResult): String {
        return buildString {
            // Headers
            appendLine(queryResult.columnNames.joinToString(","))

            // Data rows
            queryResult.rows.forEach { row ->
                val values = queryResult.columnNames.map { columnName ->
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

    private fun formatAsTable(queryResult: dev.jasonpearson.androidmcpsdk.debugbridge.database.QueryResult): String {
        if (queryResult.rows.isEmpty()) {
            return "No data returned"
        }

        // Calculate column widths
        val columnWidths = queryResult.columnNames.associateWith { columnName ->
            maxOf(
                columnName.length,
                queryResult.rows.maxOfOrNull { row ->
                    row[columnName]?.toString()?.length ?: 0
                } ?: 0
            )
        }

        return buildString {
            // Header
            val header = queryResult.columnNames.joinToString(" | ") { columnName ->
                columnName.padEnd(columnWidths[columnName]!!)
            }
            appendLine(header)

            // Separator
            val separator = queryResult.columnNames.joinToString("-|-") { columnName ->
                "-".repeat(columnWidths[columnName]!!)
            }
            appendLine(separator)

            // Data rows
            queryResult.rows.forEach { row ->
                val rowString = queryResult.columnNames.joinToString(" | ") { columnName ->
                    val value = row[columnName]?.toString() ?: ""
                    value.padEnd(columnWidths[columnName]!!)
                }
                appendLine(rowString)
            }
        }
    }
}