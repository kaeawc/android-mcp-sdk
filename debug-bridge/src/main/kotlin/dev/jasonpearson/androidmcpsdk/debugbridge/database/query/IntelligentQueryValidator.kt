package dev.jasonpearson.androidmcpsdk.debugbridge.database.query

import android.util.Log
import dev.jasonpearson.androidmcpsdk.debugbridge.database.schema.DatabaseSchemaCache
import dev.jasonpearson.androidmcpsdk.debugbridge.database.schema.OptimizationType
import dev.jasonpearson.androidmcpsdk.debugbridge.database.schema.QueryOptimization
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Intelligent query validator that validates queries against cached schemas.
 */
class IntelligentQueryValidator(
    private val schemaCache: DatabaseSchemaCache
) {

    companion object {
        private const val TAG = "QueryValidator"
        private val DANGEROUS_KEYWORDS = setOf(
            "DROP", "DELETE", "UPDATE", "INSERT", "ALTER", "CREATE",
            "PRAGMA", "ATTACH", "DETACH", "VACUUM"
        )
    }

    data class QueryValidationResult(
        val isValid: Boolean,
        val errors: List<QueryError>,
        val warnings: List<QueryWarning>,
        val optimizations: List<QueryOptimization>,
        val estimatedCost: QueryCost,
        val recommendedIndexes: List<IndexRecommendation>
    )

    data class QueryError(
        val type: ErrorType,
        val message: String,
        val line: Int? = null,
        val column: Int? = null,
        val suggestion: String? = null
    )

    data class QueryWarning(
        val type: WarningType,
        val message: String,
        val suggestion: String? = null
    )

    data class QueryCost(
        val estimatedRows: Long,
        val complexity: ComplexityLevel,
        val indexUsage: IndexUsage,
        val scanType: ScanType
    )

    data class IndexRecommendation(
        val tableName: String,
        val columns: List<String>,
        val unique: Boolean = false,
        val reason: String,
        val expectedImprovement: String
    )

    data class PaginationValidationResult(
        val isOptimal: Boolean,
        val optimizedQuery: String,
        val recommendedCursorFields: List<String>,
        val indexRecommendations: List<IndexRecommendation>
    ) {
        companion object {
            val SCHEMA_UNAVAILABLE = PaginationValidationResult(
                isOptimal = false,
                optimizedQuery = "",
                recommendedCursorFields = emptyList(),
                indexRecommendations = emptyList()
            )
        }
    }

    data class ParsedQuery(
        val type: QueryType,
        val tables: List<String>,
        val columns: List<String>,
        val whereColumns: List<String>,
        val joinColumns: List<String>,
        val orderByColumns: List<String>,
        val groupByColumns: List<String>,
        val hasSubqueries: Boolean,
        val sql: String
    )

    enum class ErrorType {
        SCHEMA_UNAVAILABLE, TABLE_NOT_FOUND, COLUMN_NOT_FOUND,
        INVALID_SYNTAX, UNSAFE_OPERATION, TYPE_MISMATCH
    }

    enum class WarningType {
        MISSING_INDEX, INEFFICIENT_QUERY, LARGE_RESULT_SET,
        DEPRECATED_SYNTAX, POTENTIAL_PERFORMANCE_ISSUE
    }

    enum class ComplexityLevel {
        LOW, MEDIUM, HIGH, VERY_HIGH
    }

    enum class IndexUsage {
        OPTIMAL, PARTIAL, NONE, UNKNOWN
    }

    enum class ScanType {
        INDEX_SCAN, TABLE_SCAN, RANGE_SCAN, UNKNOWN
    }

    enum class QueryType {
        SELECT, INSERT, UPDATE, DELETE, CREATE, DROP, UNKNOWN
    }

    /**
     * Validate a query against the cached schema.
     */
    suspend fun validateQuery(
        databaseUri: String,
        query: String,
        parameters: Map<String, Any> = emptyMap()
    ): QueryValidationResult = withContext(Dispatchers.IO) {

        Log.d(TAG, "Validating query for database: $databaseUri")

        val schema = schemaCache.getOrLoadSchema(databaseUri)
            ?: return@withContext QueryValidationResult(
                isValid = false,
                errors = listOf(
                    QueryError(
                        ErrorType.SCHEMA_UNAVAILABLE,
                        "Schema not available for database: $databaseUri"
                    )
                ),
                warnings = emptyList(),
                optimizations = emptyList(),
                estimatedCost = QueryCost(
                    0,
                    ComplexityLevel.LOW,
                    IndexUsage.UNKNOWN,
                    ScanType.UNKNOWN
                ),
                recommendedIndexes = emptyList()
            )

        try {
            val parsedQuery = parseQuery(query)
            val errors = validateQueryAgainstSchema(parsedQuery, schema)
            val warnings = analyzeQueryWarnings(parsedQuery, schema)
            val optimizations = suggestOptimizations(parsedQuery, schema)
            val cost = estimateQueryCost(parsedQuery, schema)
            val indexes = recommendIndexes(parsedQuery, schema)

            QueryValidationResult(
                isValid = errors.isEmpty(),
                errors = errors,
                warnings = warnings,
                optimizations = optimizations,
                estimatedCost = cost,
                recommendedIndexes = indexes
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to validate query", e)
            QueryValidationResult(
                isValid = false,
                errors = listOf(
                    QueryError(
                        ErrorType.INVALID_SYNTAX,
                        "Failed to parse query: ${e.message}"
                    )
                ),
                warnings = emptyList(),
                optimizations = emptyList(),
                estimatedCost = QueryCost(
                    0,
                    ComplexityLevel.LOW,
                    IndexUsage.UNKNOWN,
                    ScanType.UNKNOWN
                ),
                recommendedIndexes = emptyList()
            )
        }
    }

    /**
     * Validate query for pagination optimization.
     */
    suspend fun validateQueryForPagination(
        databaseUri: String,
        query: String,
        pageSize: Int,
        sortColumns: List<String>
    ): PaginationValidationResult = withContext(Dispatchers.IO) {

        val schema = schemaCache.getOrLoadSchema(databaseUri)
            ?: return@withContext PaginationValidationResult.SCHEMA_UNAVAILABLE

        try {
            val parsedQuery = parseQuery(query)
            val hasAppropriateIndexes = validatePaginationIndexes(query, sortColumns, schema)
            val optimizedQuery = optimizeForPagination(query, sortColumns, schema)
            val cursorFields = suggestCursorFields(parsedQuery, schema)
            val indexRecommendations = suggestPaginationIndexes(parsedQuery, sortColumns, schema)

            PaginationValidationResult(
                isOptimal = hasAppropriateIndexes,
                optimizedQuery = optimizedQuery,
                recommendedCursorFields = cursorFields,
                indexRecommendations = indexRecommendations
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to validate pagination", e)
            PaginationValidationResult.SCHEMA_UNAVAILABLE
        }
    }

    private fun parseQuery(query: String): ParsedQuery {
        val trimmedQuery = query.trim().uppercase()

        // Simple SQL parsing - in production, we'd use a proper SQL parser
        val queryType = when {
            trimmedQuery.startsWith("SELECT") -> QueryType.SELECT
            trimmedQuery.startsWith("INSERT") -> QueryType.INSERT
            trimmedQuery.startsWith("UPDATE") -> QueryType.UPDATE
            trimmedQuery.startsWith("DELETE") -> QueryType.DELETE
            trimmedQuery.startsWith("CREATE") -> QueryType.CREATE
            trimmedQuery.startsWith("DROP") -> QueryType.DROP
            else -> QueryType.UNKNOWN
        }

        // TODO: Implement proper SQL parsing
        // For now, return a basic parsed query structure
        return ParsedQuery(
            type = queryType,
            tables = extractTables(query),
            columns = extractColumns(query),
            whereColumns = extractWhereColumns(query),
            joinColumns = extractJoinColumns(query),
            orderByColumns = extractOrderByColumns(query),
            groupByColumns = extractGroupByColumns(query),
            hasSubqueries = query.uppercase().contains("SELECT") && query.count {
                it.toString().uppercase() == "SELECT"
            } > 1,
            sql = query
        )
    }

    private fun validateQueryAgainstSchema(
        parsedQuery: ParsedQuery,
        schema: DatabaseSchemaCache.CachedDatabaseSchema
    ): List<QueryError> {
        val errors = mutableListOf<QueryError>()

        // Check for unsafe operations
        if (DANGEROUS_KEYWORDS.any { parsedQuery.sql.uppercase().contains(it) }) {
            errors.add(
                QueryError(
                    type = ErrorType.UNSAFE_OPERATION,
                    message = "Query contains potentially unsafe operations",
                    suggestion = "Use parameterized queries and avoid destructive operations"
                )
            )
        }

        // Validate table existence
        parsedQuery.tables.forEach { tableName ->
            if (!schema.tables.containsKey(tableName)) {
                errors.add(
                    QueryError(
                        type = ErrorType.TABLE_NOT_FOUND,
                        message = "Table '$tableName' not found in schema",
                        suggestion = "Check table name spelling or verify database schema"
                    )
                )
            }
        }

        // Validate column existence
        parsedQuery.columns.forEach { columnName ->
            val columnExists = parsedQuery.tables.any { tableName ->
                val tableSchema = schema.tables[tableName]
                tableSchema?.columns?.any { it.name == columnName } == true
            }

            if (!columnExists && columnName != "*") {
                errors.add(
                    QueryError(
                        type = ErrorType.COLUMN_NOT_FOUND,
                        message = "Column '$columnName' not found in any referenced table",
                        suggestion = "Check column name spelling or table aliases"
                    )
                )
            }
        }

        return errors
    }

    private fun analyzeQueryWarnings(
        parsedQuery: ParsedQuery,
        schema: DatabaseSchemaCache.CachedDatabaseSchema
    ): List<QueryWarning> {
        val warnings = mutableListOf<QueryWarning>()

        // Check for missing indexes on WHERE columns
        parsedQuery.whereColumns.forEach { columnName ->
            val needsIndex = parsedQuery.tables.any { tableName ->
                val tableSchema = schema.tables[tableName]
                val column = tableSchema?.columns?.find { it.name == columnName }
                val hasIndex = schema.indexes.values.any { index ->
                    index.tableName == tableName && index.columns.contains(columnName)
                }
                column != null && !hasIndex
            }

            if (needsIndex) {
                warnings.add(
                    QueryWarning(
                        type = WarningType.MISSING_INDEX,
                        message = "Column '$columnName' used in WHERE clause but has no index",
                        suggestion = "Consider adding an index on '$columnName' for better performance"
                    )
                )
            }
        }

        // Check for potential large result sets
        if (parsedQuery.type == QueryType.SELECT && parsedQuery.whereColumns.isEmpty()) {
            warnings.add(
                QueryWarning(
                    type = WarningType.LARGE_RESULT_SET,
                    message = "SELECT query without WHERE clause may return large result set",
                    suggestion = "Consider adding WHERE conditions or LIMIT clause"
                )
            )
        }

        return warnings
    }

    private fun suggestOptimizations(
        parsedQuery: ParsedQuery,
        schema: DatabaseSchemaCache.CachedDatabaseSchema
    ): List<QueryOptimization> {
        val optimizations = mutableListOf<QueryOptimization>()

        // Suggest column selection optimization
        if (parsedQuery.columns.contains("*")) {
            optimizations.add(
                QueryOptimization(
                    type = OptimizationType.COLUMN_SELECTION,
                    description = "Select only required columns instead of using SELECT *",
                    suggestedQuery = null,
                    expectedImprovement = "Reduced network traffic and memory usage"
                )
            )
        }

        // Suggest index usage optimization
        if (parsedQuery.whereColumns.isNotEmpty()) {
            optimizations.add(
                QueryOptimization(
                    type = OptimizationType.INDEX_USAGE,
                    description = "Consider adding composite index for WHERE clause columns",
                    suggestedQuery = null,
                    expectedImprovement = "Faster query execution"
                )
            )
        }

        return optimizations
    }

    private fun estimateQueryCost(
        parsedQuery: ParsedQuery,
        schema: DatabaseSchemaCache.CachedDatabaseSchema
    ): QueryCost {
        // Simple cost estimation based on query characteristics
        val estimatedRows = parsedQuery.tables.sumOf { tableName ->
            schema.tables[tableName]?.estimatedRowCount ?: 1000L
        }

        val complexity = when {
            parsedQuery.hasSubqueries || parsedQuery.joinColumns.isNotEmpty() -> ComplexityLevel.HIGH
            parsedQuery.whereColumns.size > 2 || parsedQuery.groupByColumns.isNotEmpty() -> ComplexityLevel.MEDIUM
            else -> ComplexityLevel.LOW
        }

        val indexUsage = if (parsedQuery.whereColumns.any { columnName ->
                schema.indexes.values.any { index -> index.columns.contains(columnName) }
            }) IndexUsage.PARTIAL else IndexUsage.NONE

        return QueryCost(
            estimatedRows = estimatedRows,
            complexity = complexity,
            indexUsage = indexUsage,
            scanType = if (indexUsage != IndexUsage.NONE) ScanType.INDEX_SCAN else ScanType.TABLE_SCAN
        )
    }

    private fun recommendIndexes(
        parsedQuery: ParsedQuery,
        schema: DatabaseSchemaCache.CachedDatabaseSchema
    ): List<IndexRecommendation> {
        val recommendations = mutableListOf<IndexRecommendation>()

        // Recommend indexes for WHERE columns
        parsedQuery.tables.forEach { tableName ->
            val whereColumnsForTable = parsedQuery.whereColumns.filter { columnName ->
                schema.tables[tableName]?.columns?.any { it.name == columnName } == true
            }

            if (whereColumnsForTable.isNotEmpty()) {
                recommendations.add(
                    IndexRecommendation(
                        tableName = tableName,
                        columns = whereColumnsForTable,
                        reason = "Improve WHERE clause performance",
                        expectedImprovement = "Faster query execution for filtered results"
                    )
                )
            }
        }

        return recommendations
    }

    // Simple extraction methods - in production, use a proper SQL parser
    private fun extractTables(query: String): List<String> = emptyList()
    private fun extractColumns(query: String): List<String> = emptyList()
    private fun extractWhereColumns(query: String): List<String> = emptyList()
    private fun extractJoinColumns(query: String): List<String> = emptyList()
    private fun extractOrderByColumns(query: String): List<String> = emptyList()
    private fun extractGroupByColumns(query: String): List<String> = emptyList()

    private fun validatePaginationIndexes(
        query: String,
        sortColumns: List<String>,
        schema: DatabaseSchemaCache.CachedDatabaseSchema
    ): Boolean = false

    private fun optimizeForPagination(
        query: String,
        sortColumns: List<String>,
        schema: DatabaseSchemaCache.CachedDatabaseSchema
    ): String = query

    private fun suggestCursorFields(
        parsedQuery: ParsedQuery,
        schema: DatabaseSchemaCache.CachedDatabaseSchema
    ): List<String> = emptyList()

    private fun suggestPaginationIndexes(
        parsedQuery: ParsedQuery,
        sortColumns: List<String>,
        schema: DatabaseSchemaCache.CachedDatabaseSchema
    ): List<IndexRecommendation> = emptyList()
}
