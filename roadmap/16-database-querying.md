# Task 16: Database Querying Implementation

## Status: `[P]` In Progress - Enhanced Schema Intelligence Required

## Objective

Implement comprehensive database querying capabilities with intelligent schema caching,
Room/SQLDelight integration, and query optimization for Android applications. Provide safe and
efficient SQL query execution with schema-aware validation, index optimization, and seamless
integration with modern Android database frameworks.

## Enhanced Requirements

### Schema Intelligence Requirements

- **Intelligent Schema Caching**: In-memory cache of complete table schemas including columns,
  types, constraints, indexes, and foreign key relationships
- **Room Integration**: Parse Room entity classes and DAO annotations to augment database schema
  with source code information
- **SQLDelight Integration**: Parse SQLDelight schema files (.sq) to enhance table metadata with
  compile-time query information
- **Source Code Augmentation**: Correlate database tables with Kotlin data classes, including
  property mappings and validation rules
- **Automatic Schema Refresh**: Detect schema changes and refresh cache automatically when queries
  fail due to schema mismatches

### Query Optimization Requirements

- **Index-Aware Query Planning**: Analyze query patterns and suggest optimal index usage
- **Pagination Optimization**: Smart LIMIT/OFFSET with cursor-based pagination for large datasets
- **Query Validation**: Pre-execution validation using cached schema to prevent runtime errors
- **Performance Hints**: Suggest query improvements based on schema analysis and execution patterns

### Security & Safety Requirements

- **Schema-Based Validation**: Validate all queries against cached schema before execution
- **SQL Injection Prevention**: Enhanced parameterization with schema-aware type checking
- **Permission-Based Access**: Table and column-level access control based on data sensitivity
- **Query Complexity Limits**: Prevent expensive queries that could impact app performance

## Implementation Steps

### Step 1: Enhanced Schema Cache Framework

Create `core/src/main/kotlin/dev/jasonpearson/mcpandroidsdk/database/schema/DatabaseSchemaCache.kt`:

```kotlin
class DatabaseSchemaCache(
    private val context: Context
) {
    
    data class CachedDatabaseSchema(
        val databaseUri: String,
        val databaseType: DatabaseType,
        val tables: Map<String, TableSchema>,
        val views: Map<String, ViewSchema>,
        val indexes: Map<String, IndexSchema>,
        val foreignKeys: List<ForeignKeyConstraint>,
        val triggers: Map<String, TriggerSchema>,
        val sourceCodeMappings: Map<String, SourceCodeMapping>,
        val lastUpdated: Long = System.currentTimeMillis(),
        val schemaVersion: Int
    )
    
    data class TableSchema(
        val name: String,
        val columns: List<ColumnSchema>,
        val primaryKey: List<String>,
        val indexes: List<String>,
        val foreignKeys: List<ForeignKeyConstraint>,
        val checkConstraints: List<String>,
        val roomEntityInfo: RoomEntityInfo? = null,
        val sqlDelightInfo: SqlDelightInfo? = null,
        val estimatedRowCount: Long = 0
    )
    
    data class ColumnSchema(
        val name: String,
        val type: String,
        val sqliteType: SqliteDataType,
        val isNullable: Boolean,
        val defaultValue: String? = null,
        val isAutoIncrement: Boolean = false,
        val collation: String? = null,
        val roomPropertyInfo: RoomPropertyInfo? = null,
        val sqlDelightPropertyInfo: SqlDelightPropertyInfo? = null
    )
    
    data class IndexSchema(
        val name: String,
        val tableName: String,
        val columns: List<String>,
        val isUnique: Boolean,
        val whereClause: String? = null,
        val cardinality: Long = 0,
        val usageStats: IndexUsageStats? = null
    )
    
    enum class DatabaseType {
        ROOM, SQLITE_DIRECT, SQLDELIGHT, CONTENT_PROVIDER, UNKNOWN
    }
    
    suspend fun getOrLoadSchema(databaseUri: String): CachedDatabaseSchema?
    suspend fun refreshSchema(databaseUri: String): CachedDatabaseSchema?
    suspend fun validateSchemaVersion(databaseUri: String): Boolean
    suspend fun preloadAllDatabaseSchemas()
    
    fun getTableSchema(databaseUri: String, tableName: String): TableSchema?
    fun getOptimalIndexes(databaseUri: String, tableName: String, whereColumns: List<String>): List<IndexSchema>
    fun suggestQueryOptimizations(query: String, schema: CachedDatabaseSchema): List<QueryOptimization>
}
```

### Step 2: Room Framework Integration

Create `core/src/main/kotlin/dev/jasonpearson/mcpandroidsdk/database/schema/RoomSchemaAnalyzer.kt`:

```kotlin
class RoomSchemaAnalyzer(
    private val context: Context
) {
    
    data class RoomEntityInfo(
        val entityClass: KClass<*>,
        val tableName: String,
        val primaryKeys: List<String>,
        val foreignKeys: List<RoomForeignKey>,
        val indices: List<RoomIndex>,
        val embeddedProperties: List<RoomEmbedded>,
        val typeConverters: List<RoomTypeConverter>
    )
    
    data class RoomPropertyInfo(
        val propertyName: String,
        val columnName: String,
        val propertyType: KType,
        val isNullable: Boolean,
        val columnInfo: RoomColumnInfo? = null,
        val relationInfo: RoomRelationInfo? = null
    )
    
    data class RoomDaoInfo(
        val daoClass: KClass<*>,
        val queries: List<RoomQueryMethod>,
        val insertMethods: List<RoomInsertMethod>,
        val updateMethods: List<RoomUpdateMethod>,
        val deleteMethods: List<RoomDeleteMethod>
    )
    
    suspend fun analyzeRoomDatabase(database: RoomDatabase): RoomDatabaseInfo {
        return try {
            val entities = extractRoomEntities(database)
            val daos = extractRoomDaos(database)
            val migrations = extractMigrationInfo(database)
            
            RoomDatabaseInfo(
                databaseClass = database::class,
                entities = entities,
                daos = daos,
                version = extractDatabaseVersion(database),
                migrations = migrations,
                typeConverters = extractGlobalTypeConverters(database)
            )
        } catch (e: Exception) {
            throw RoomAnalysisException("Failed to analyze Room database: ${e.message}", e)
        }
    }
    
    suspend fun enhanceTableSchemaWithRoom(
        tableSchema: TableSchema,
        roomInfo: RoomDatabaseInfo
    ): TableSchema {
        val entityInfo = roomInfo.entities.find { it.tableName == tableSchema.name }
            ?: return tableSchema
            
        val enhancedColumns = tableSchema.columns.map { column ->
            val roomProperty = entityInfo.properties.find { it.columnName == column.name }
            column.copy(roomPropertyInfo = roomProperty)
        }
        
        return tableSchema.copy(
            columns = enhancedColumns,
            roomEntityInfo = entityInfo
        )
    }
    
    private fun extractRoomEntities(database: RoomDatabase): List<RoomEntityInfo>
    private fun extractRoomDaos(database: RoomDatabase): List<RoomDaoInfo>
    private fun extractMigrationInfo(database: RoomDatabase): List<RoomMigrationInfo>
}
```

### Step 3: SQLDelight Integration

Create
`core/src/main/kotlin/dev/jasonpearson/mcpandroidsdk/database/schema/SqlDelightSchemaAnalyzer.kt`:

```kotlin
class SqlDelightSchemaAnalyzer(
    private val context: Context
) {
    
    data class SqlDelightInfo(
        val schemaFile: String,
        val compiledQueries: List<SqlDelightQuery>,
        val migrations: List<SqlDelightMigration>,
        val tables: List<SqlDelightTable>
    )
    
    data class SqlDelightQuery(
        val name: String,
        val sql: String,
        val parameters: List<SqlDelightParameter>,
        val returnType: KType,
        val affectedTables: List<String>
    )
    
    data class SqlDelightTable(
        val name: String,
        val createStatement: String,
        val dataClass: KClass<*>?,
        val properties: List<SqlDelightProperty>
    )
    
    suspend fun analyzeSqlDelightDatabase(databasePath: String): SqlDelightDatabaseInfo? {
        return try {
            val schemaFiles = findSqlDelightSchemaFiles()
            val compiledQueries = extractCompiledQueries()
            val migrations = extractMigrations()
            
            SqlDelightDatabaseInfo(
                databasePath = databasePath,
                schemaFiles = schemaFiles,
                queries = compiledQueries,
                migrations = migrations,
                generatedClasses = extractGeneratedDataClasses()
            )
        } catch (e: Exception) {
            null // SQLDelight not used in this project
        }
    }
    
    suspend fun enhanceTableSchemaWithSqlDelight(
        tableSchema: TableSchema,
        sqlDelightInfo: SqlDelightDatabaseInfo
    ): TableSchema {
        val sqlDelightTable = sqlDelightInfo.tables.find { it.name == tableSchema.name }
            ?: return tableSchema
            
        val enhancedColumns = tableSchema.columns.map { column ->
            val sqlDelightProperty = sqlDelightTable.properties.find { it.columnName == column.name }
            column.copy(sqlDelightPropertyInfo = sqlDelightProperty)
        }
        
        return tableSchema.copy(
            columns = enhancedColumns,
            sqlDelightInfo = SqlDelightTableInfo(
                dataClass = sqlDelightTable.dataClass,
                queries = sqlDelightInfo.queries.filter { it.affectedTables.contains(tableSchema.name) }
            )
        )
    }
    
    private fun findSqlDelightSchemaFiles(): List<String>
    private fun extractCompiledQueries(): List<SqlDelightQuery>
    private fun extractGeneratedDataClasses(): List<KClass<*>>
}
```

### Step 4: Intelligent Query Validator

Create
`core/src/main/kotlin/dev/jasonpearson/mcpandroidsdk/database/query/IntelligentQueryValidator.kt`:

```kotlin
class IntelligentQueryValidator(
    private val schemaCache: DatabaseSchemaCache
) {
    
    data class QueryValidationResult(
        val isValid: Boolean,
        val errors: List<QueryError>,
        val warnings: List<QueryWarning>,
        val optimizations: List<QueryOptimization>,
        val estimatedCost: QueryCost,
        val recommendedIndexes: List<IndexRecommendation>
    )
    
    data class QueryOptimization(
        val type: OptimizationType,
        val description: String,
        val suggestedQuery: String?,
        val expectedImprovement: String
    )
    
    enum class OptimizationType {
        INDEX_USAGE, PAGINATION, JOIN_ORDER, SUBQUERY_ELIMINATION, 
        QUERY_REWRITE, COLUMN_SELECTION, WHERE_OPTIMIZATION
    }
    
    suspend fun validateQuery(
        databaseUri: String,
        query: String,
        parameters: Map<String, Any> = emptyMap()
    ): QueryValidationResult {
        val schema = schemaCache.getOrLoadSchema(databaseUri)
            ?: return QueryValidationResult(
                isValid = false,
                errors = listOf(QueryError.SCHEMA_UNAVAILABLE),
                warnings = emptyList(),
                optimizations = emptyList(),
                estimatedCost = QueryCost.UNKNOWN,
                recommendedIndexes = emptyList()
            )
        
        val parsedQuery = parseQuery(query)
        val errors = validateQueryAgainstSchema(parsedQuery, schema)
        val warnings = analyzeQueryWarnings(parsedQuery, schema)
        val optimizations = suggestOptimizations(parsedQuery, schema)
        val cost = estimateQueryCost(parsedQuery, schema)
        val indexes = recommendIndexes(parsedQuery, schema)
        
        return QueryValidationResult(
            isValid = errors.isEmpty(),
            errors = errors,
            warnings = warnings,
            optimizations = optimizations,
            estimatedCost = cost,
            recommendedIndexes = indexes
        )
    }
    
    suspend fun validateQueryForPagination(
        databaseUri: String,
        query: String,
        pageSize: Int,
        sortColumns: List<String>
    ): PaginationValidationResult {
        val schema = schemaCache.getOrLoadSchema(databaseUri) ?: return PaginationValidationResult.SCHEMA_UNAVAILABLE
        
        val hasAppropriateIndexes = validatePaginationIndexes(query, sortColumns, schema)
        val optimizedQuery = optimizeForPagination(query, sortColumns, schema)
        val cursorFields = suggestCursorFields(query, schema)
        
        return PaginationValidationResult(
            isOptimal = hasAppropriateIndexes,
            optimizedQuery = optimizedQuery,
            recommendedCursorFields = cursorFields,
            indexRecommendations = suggestPaginationIndexes(query, sortColumns, schema)
        )
    }
    
    private fun parseQuery(query: String): ParsedQuery
    private fun validateQueryAgainstSchema(parsedQuery: ParsedQuery, schema: CachedDatabaseSchema): List<QueryError>
    private fun suggestOptimizations(parsedQuery: ParsedQuery, schema: CachedDatabaseSchema): List<QueryOptimization>
}
```

### Step 5: Query Execution Engine with Optimization

Create
`core/src/main/kotlin/dev/jasonpearson/mcpandroidsdk/database/query/OptimizedQueryExecutor.kt`:

```kotlin
class OptimizedQueryExecutor(
    private val schemaCache: DatabaseSchemaCache,
    private val validator: IntelligentQueryValidator
) {
    
    data class QueryExecutionRequest(
        val databaseUri: String,
        val query: String,
        val parameters: Map<String, Any> = emptyMap(),
        val outputFormat: OutputFormat = OutputFormat.JSON,
        val pagination: PaginationConfig? = null,
        val optimizationLevel: OptimizationLevel = OptimizationLevel.BALANCED
    )
    
    data class PaginationConfig(
        val pageSize: Int = 100,
        val pageToken: String? = null,
        val sortColumns: List<String> = emptyList(),
        val useCursorPagination: Boolean = true
    )
    
    enum class OptimizationLevel {
        NONE, BASIC, BALANCED, AGGRESSIVE
    }
    
    suspend fun executeOptimizedQuery(request: QueryExecutionRequest): QueryExecutionResult {
        // Step 1: Validate schema availability
        val schema = schemaCache.getOrLoadSchema(request.databaseUri)
            ?: return QueryExecutionResult.schemaUnavailable()
        
        // Step 2: Validate query
        val validation = validator.validateQuery(request.databaseUri, request.query, request.parameters)
        if (!validation.isValid) {
            return QueryExecutionResult.validationFailed(validation.errors)
        }
        
        // Step 3: Apply optimizations
        val optimizedQuery = applyOptimizations(request.query, validation.optimizations, request.optimizationLevel)
        
        // Step 4: Setup pagination if requested
        val paginatedQuery = if (request.pagination != null) {
            applyPagination(optimizedQuery, request.pagination, schema)
        } else {
            optimizedQuery
        }
        
        // Step 5: Execute query
        val startTime = System.currentTimeMillis()
        val result = executeQuery(request.databaseUri, paginatedQuery, request.parameters)
        val executionTime = System.currentTimeMillis() - startTime
        
        // Step 6: Format results
        return formatExecutionResult(result, request.outputFormat, executionTime, validation)
    }
    
    suspend fun suggestQueryImprovements(
        databaseUri: String,
        query: String
    ): QueryImprovementSuggestions {
        val schema = schemaCache.getOrLoadSchema(databaseUri) ?: return QueryImprovementSuggestions.empty()
        val validation = validator.validateQuery(databaseUri, query)
        
        return QueryImprovementSuggestions(
            indexSuggestions = validation.recommendedIndexes,
            queryOptimizations = validation.optimizations,
            performanceHints = generatePerformanceHints(query, schema),
            alternativeQueries = suggestAlternativeQueries(query, schema)
        )
    }
    
    private fun applyOptimizations(query: String, optimizations: List<QueryOptimization>, level: OptimizationLevel): String
    private fun applyPagination(query: String, pagination: PaginationConfig, schema: CachedDatabaseSchema): String
    private fun generatePerformanceHints(query: String, schema: CachedDatabaseSchema): List<PerformanceHint>
}
```

### Step 6: Enhanced MCP Tool Integration

Update existing database tools in `DatabaseToolProvider.kt`:

```kotlin
private fun createEnhancedDatabaseTools(): List<Tool> {
    return listOf(
        Tool(
            name = "database_list_tables",
            description = "List database tables with comprehensive schema information including Room/SQLDelight mappings",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    put("database_uri", buildJsonObject {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("Database URI to inspect"))
                    })
                    put("include_source_mappings", buildJsonObject {
                        put("type", JsonPrimitive("boolean"))
                        put("default", JsonPrimitive(true))
                        put("description", JsonPrimitive("Include Room/SQLDelight source code mappings"))
                    })
                    put("include_index_analysis", buildJsonObject {
                        put("type", JsonPrimitive("boolean"))
                        put("default", JsonPrimitive(true))
                        put("description", JsonPrimitive("Include index usage analysis"))
                    })
                    put("refresh_cache", buildJsonObject {
                        put("type", JsonPrimitive("boolean"))
                        put("default", JsonPrimitive(false))
                        put("description", JsonPrimitive("Force refresh of schema cache"))
                    })
                },
                required = listOf("database_uri")
            )
        ),
        
        Tool(
            name = "database_optimized_query",
            description = "Execute SQL queries with intelligent optimization and validation",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    put("database_uri", buildJsonObject {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("Database URI to query"))
                    })
                    put("query", buildJsonObject {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("SQL query to execute with optimization"))
                    })
                    put("parameters", buildJsonObject {
                        put("type", JsonPrimitive("object"))
                        put("description", JsonPrimitive("Query parameters for safe parameterization"))
                    })
                    put("optimization_level", buildJsonObject {
                        put("type", JsonPrimitive("string"))
                        put("enum", buildJsonArray {
                            add("none")
                            add("basic") 
                            add("balanced")
                            add("aggressive")
                        })
                        put("default", JsonPrimitive("balanced"))
                    })
                    put("pagination", buildJsonObject {
                        put("type", JsonPrimitive("object"))
                        put("properties", buildJsonObject {
                            put("page_size", buildJsonObject { 
                                put("type", JsonPrimitive("integer"))
                                put("maximum", JsonPrimitive(1000))
                            })
                            put("page_token", buildJsonObject { put("type", JsonPrimitive("string")) })
                            put("sort_columns", buildJsonObject { 
                                put("type", JsonPrimitive("array"))
                                put("items", buildJsonObject { put("type", JsonPrimitive("string")) })
                            })
                        })
                    })
                },
                required = listOf("database_uri", "query")
            )
        ),
        
        Tool(
            name = "database_analyze_query",
            description = "Analyze query performance and suggest optimizations",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    put("database_uri", buildJsonObject {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("Database URI"))
                    })
                    put("query", buildJsonObject {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("SQL query to analyze"))
                    })
                    put("include_execution_plan", buildJsonObject {
                        put("type", JsonPrimitive("boolean"))
                        put("default", JsonPrimitive(true))
                        put("description", JsonPrimitive("Include detailed execution plan"))
                    })
                    put("suggest_indexes", buildJsonObject {
                        put("type", JsonPrimitive("boolean"))
                        put("default", JsonPrimitive(true))
                        put("description", JsonPrimitive("Suggest optimal indexes"))
                    })
                },
                required = listOf("database_uri", "query")
            )
        ),
        
        Tool(
            name = "database_schema_cache_stats",
            description = "Get statistics about cached database schemas",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    put("database_uri", buildJsonObject {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("Specific database URI (optional)"))
                    })
                },
                required = emptyList()
            )
        )
    )
}
```

// ... existing code ...
