# Task 16: Database Querying Implementation

## Status: `[ ]` Not Started

## Objective

Implement comprehensive database querying capabilities for Android applications, providing safe and
efficient SQL query execution, result formatting, and query optimization for Room databases, SQLite,
and Content Providers.

## Requirements

### Technical Requirements

- **Safe Query Execution**: Parameterized queries with SQL injection prevention
- **Multiple Database Support**: Room, SQLite, and Content Provider queries
- **Result Formatting**: JSON, CSV, and structured data output formats
- **Query Optimization**: Query planning and performance analysis
- **Schema-Aware Queries**: Intelligent autocomplete and validation
- **Pagination Support**: Large result set handling with cursors
- **Query Caching**: Intelligent caching for repeated queries

### Security Requirements

- **SQL Injection Prevention**: Comprehensive input validation and sanitization
- **Access Control**: Query permissions based on table and column access
- **Query Auditing**: Log all database queries with user context
- **Result Filtering**: Automatic PII detection and masking in results

## Implementation Steps

### Step 1: Create Query Engine Framework

Create `core/src/main/kotlin/dev/jasonpearson/mcpandroidsdk/database/DatabaseQueryEngine.kt`:

```kotlin
class DatabaseQueryEngine(
    private val context: Context
) {
    
    data class QueryConfig(
        val maxResultRows: Int = 1000,
        val enablePagination: Boolean = true,
        val allowUnsafeQueries: Boolean = false,
        val cacheResults: Boolean = true,
        val maskSensitiveData: Boolean = true
    )
    
    data class QueryRequest(
        val databaseUri: String,
        val query: String,
        val parameters: Map<String, Any> = emptyMap(),
        val outputFormat: OutputFormat = OutputFormat.JSON,
        val pageSize: Int = 100,
        val pageOffset: Int = 0
    )
    
    enum class OutputFormat {
        JSON, CSV, XML, PARQUET
    }
    
    suspend fun executeQuery(request: QueryRequest): QueryResult
    suspend fun explainQuery(request: QueryRequest): QueryPlan
    suspend fun validateQuery(query: String, schemaUri: String): QueryValidation
    
    data class QueryResult(
        val success: Boolean,
        val data: String,
        val format: OutputFormat,
        val rowCount: Int,
        val executionTimeMs: Long,
        val hasMore: Boolean = false,
        val nextPageToken: String? = null,
        val columnMetadata: List<ColumnInfo>,
        val warnings: List<String> = emptyList()
    )
}
```

### Step 2: Room Database Query Support

Create `core/src/main/kotlin/dev/jasonpearson/mcpandroidsdk/database/RoomQueryExecutor.kt`:

```kotlin
class RoomQueryExecutor(
    private val context: Context
) {
    
    data class RoomQueryConfig(
        val database: RoomDatabase,
        val allowCustomQueries: Boolean = false,
        val enableLiveData: Boolean = true,
        val queryTimeout: Duration = Duration.ofSeconds(30)
    )
    
    suspend fun executeRoomQuery(
        database: RoomDatabase,
        query: String,
        parameters: Map<String, Any>
    ): QueryResult {
        return try {
            validateRoomQuery(query, database)
            val results = database.query(buildSqliteQuery(query, parameters))
            formatQueryResults(results, OutputFormat.JSON)
        } catch (e: Exception) {
            QueryResult(
                success = false,
                data = "Error: ${e.message}",
                format = OutputFormat.JSON,
                rowCount = 0,
                executionTimeMs = 0,
                columnMetadata = emptyList()
            )
        }
    }
    
    suspend fun executeEntityQuery(
        database: RoomDatabase,
        entityClass: KClass<*>,
        whereClause: String = "",
        orderBy: String = "",
        limit: Int = 100
    ): QueryResult
    
    suspend fun getRoomDatabaseMetadata(database: RoomDatabase): DatabaseMetadata
    
    private suspend fun validateRoomQuery(query: String, database: RoomDatabase): QueryValidation
    private fun buildSqliteQuery(query: String, parameters: Map<String, Any>): SupportSQLiteQuery
}
```

### Step 3: SQLite Direct Query Support

Create `core/src/main/kotlin/dev/jasonpearson/mcpandroidsdk/database/SqliteQueryExecutor.kt`:

```kotlin
class SqliteQueryExecutor(
    private val context: Context
) {
    
    data class SqliteQueryConfig(
        val databasePath: String,
        val readOnly: Boolean = true,
        val enableWalMode: Boolean = false,
        val queryTimeout: Duration = Duration.ofSeconds(30)
    )
    
    suspend fun executeSqliteQuery(
        databasePath: String,
        query: String,
        parameters: Array<String> = emptyArray(),
        config: SqliteQueryConfig
    ): QueryResult {
        return withContext(Dispatchers.IO) {
            val database = SQLiteDatabase.openDatabase(
                databasePath,
                null,
                if (config.readOnly) SQLiteDatabase.OPEN_READONLY else SQLiteDatabase.OPEN_READWRITE
            )
            
            try {
                val cursor = database.rawQuery(query, parameters)
                val result = formatCursorResults(cursor)
                cursor.close()
                result
            } finally {
                database.close()
            }
        }
    }
    
    suspend fun getTableSchema(databasePath: String, tableName: String): TableSchema
    suspend fun getTableData(
        databasePath: String,
        tableName: String,
        limit: Int = 100,
        offset: Int = 0,
        whereClause: String = ""
    ): QueryResult
    
    suspend fun executeExplainQuery(databasePath: String, query: String): QueryPlan
    
    private fun formatCursorResults(cursor: Cursor): QueryResult
    private fun validateSqliteQuery(query: String): QueryValidation
}
```

### Step 4: Content Provider Query Support

Create
`core/src/main/kotlin/dev/jasonpearson/mcpandroidsdk/database/ContentProviderQueryExecutor.kt`:

```kotlin
class ContentProviderQueryExecutor(
    private val context: Context
) {
    
    data class ContentProviderQuery(
        val authority: String,
        val path: String,
        val projection: Array<String>? = null,
        val selection: String? = null,
        val selectionArgs: Array<String>? = null,
        val sortOrder: String? = null
    )
    
    suspend fun queryContentProvider(
        query: ContentProviderQuery,
        outputFormat: OutputFormat = OutputFormat.JSON
    ): QueryResult {
        return withContext(Dispatchers.IO) {
            try {
                val uri = Uri.parse("content://${query.authority}/${query.path}")
                val cursor = context.contentResolver.query(
                    uri,
                    query.projection,
                    query.selection,
                    query.selectionArgs,
                    query.sortOrder
                )
                
                cursor?.use { c ->
                    formatCursorResults(c, outputFormat)
                } ?: QueryResult(
                    success = false,
                    data = "No data found",
                    format = outputFormat,
                    rowCount = 0,
                    executionTimeMs = 0,
                    columnMetadata = emptyList()
                )
            } catch (e: SecurityException) {
                QueryResult(
                    success = false,
                    data = "Permission denied: ${e.message}",
                    format = outputFormat,
                    rowCount = 0,
                    executionTimeMs = 0,
                    columnMetadata = emptyList()
                )
            }
        }
    }
    
    suspend fun getContentProviderSchema(authority: String): ProviderSchema
    suspend fun queryBuiltInProviders(): List<BuiltInProvider>
    
    // Built-in provider helpers
    suspend fun queryContacts(
        projection: Array<String>? = null,
        selection: String? = null
    ): QueryResult
    
    suspend fun queryMediaStore(
        mediaType: MediaType,
        projection: Array<String>? = null
    ): QueryResult
    
    enum class MediaType { IMAGES, VIDEOS, AUDIO, FILES }
}
```

### Step 5: Query Builder and Safety Framework

Create `core/src/main/kotlin/dev/jasonpearson/mcpandroidsdk/database/SafeQueryBuilder.kt`:

```kotlin
class SafeQueryBuilder(
    private val databaseMetadata: DatabaseMetadata
) {
    
    class SelectBuilder {
        fun select(vararg columns: String): SelectBuilder
        fun from(table: String): SelectBuilder
        fun where(condition: WhereCondition): SelectBuilder
        fun join(table: String, on: String): SelectBuilder
        fun orderBy(column: String, direction: SortDirection = SortDirection.ASC): SelectBuilder
        fun groupBy(vararg columns: String): SelectBuilder
        fun having(condition: WhereCondition): SelectBuilder
        fun limit(count: Int): SelectBuilder
        fun offset(count: Int): SelectBuilder
        
        fun build(): SafeQuery
    }
    
    class WhereCondition {
        companion object {
            fun eq(column: String, value: Any): WhereCondition
            fun like(column: String, pattern: String): WhereCondition
            fun `in`(column: String, values: List<Any>): WhereCondition
            fun between(column: String, start: Any, end: Any): WhereCondition
            fun isNull(column: String): WhereCondition
            fun and(vararg conditions: WhereCondition): WhereCondition
            fun or(vararg conditions: WhereCondition): WhereCondition
        }
    }
    
    data class SafeQuery(
        val sql: String,
        val parameters: Array<String>,
        val affectedTables: Set<String>,
        val estimatedRowCount: Int,
        val isReadOnly: Boolean = true
    )
    
    fun select(): SelectBuilder
    fun validateQuery(query: String): QueryValidation
    fun sanitizeUserInput(input: String): String
}
```

### Step 6: Query Result Formatting

Create `core/src/main/kotlin/dev/jasonpearson/mcpandroidsdk/database/QueryResultFormatter.kt`:

```kotlin
class QueryResultFormatter {
    
    suspend fun formatAsJson(
        cursor: Cursor,
        columnMetadata: List<ColumnInfo>
    ): String {
        val result = JSONArray()
        
        while (cursor.moveToNext()) {
            val row = JSONObject()
            for (i in 0 until cursor.columnCount) {
                val columnName = cursor.getColumnName(i)
                val value = when (cursor.getType(i)) {
                    Cursor.FIELD_TYPE_NULL -> null
                    Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(i)
                    Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(i)
                    Cursor.FIELD_TYPE_STRING -> cursor.getString(i)
                    Cursor.FIELD_TYPE_BLOB -> cursor.getBlob(i).toString()
                    else -> cursor.getString(i)
                }
                row.put(columnName, value)
            }
            result.put(row)
        }
        
        return result.toString(2)
    }
    
    suspend fun formatAsCsv(cursor: Cursor): String {
        val builder = StringBuilder()
        
        // Headers
        val headers = (0 until cursor.columnCount).map { cursor.getColumnName(it) }
        builder.appendLine(headers.joinToString(","))
        
        // Data rows
        while (cursor.moveToNext()) {
            val row = (0 until cursor.columnCount).map { i ->
                val value = cursor.getString(i) ?: ""
                if (value.contains(",") || value.contains("\"")) {
                    "\"${value.replace("\"", "\"\"\")}\""
                } else {
                    value
                }
            }
            builder.appendLine(row.joinToString(","))
        }
        
        return builder.toString()
    }
    
    suspend fun formatAsXml(cursor: Cursor): String
    suspend fun formatAsParquet(cursor: Cursor): ByteArray
    
    private fun maskSensitiveData(value: String, columnName: String): String {
        return when {
            columnName.contains("password", ignoreCase = true) -> "****"
            columnName.contains("email", ignoreCase = true) -> maskEmail(value)
            columnName.contains("phone", ignoreCase = true) -> maskPhone(value)
            columnName.contains("ssn", ignoreCase = true) -> "***-**-****"
            else -> value
        }
    }
}
```

### Step 7: MCP Tool Integration

Add to existing `ToolProvider.kt`:

```kotlin
private fun createDatabaseQueryTools(): List<Tool> {
    return listOf(
        Tool(
            name = "database_query",
            description = "Execute SQL queries on application databases",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    put("database_uri", buildJsonObject {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("Database URI to query"))
                    })
                    put("query", buildJsonObject {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("SQL query to execute"))
                    })
                    put("parameters", buildJsonObject {
                        put("type", JsonPrimitive("object"))
                        put("description", JsonPrimitive("Query parameters"))
                    })
                    put("output_format", buildJsonObject {
                        put("type", JsonPrimitive("string"))
                        put("enum", buildJsonArray {
                            add("json")
                            add("csv")
                            add("xml")
                        })
                        put("default", JsonPrimitive("json"))
                    })
                    put("page_size", buildJsonObject {
                        put("type", JsonPrimitive("integer"))
                        put("default", JsonPrimitive(100))
                        put("maximum", JsonPrimitive(1000))
                    })
                },
                required = listOf("database_uri", "query")
            )
        ),
        
        Tool(
            name = "database_schema",
            description = "Get database schema information",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    put("database_uri", buildJsonObject {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("Database URI to inspect"))
                    })
                    put("table_name", buildJsonObject {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("Specific table to describe (optional)"))
                    })
                },
                required = listOf("database_uri")
            )
        ),
        
        Tool(
            name = "database_explain_query",
            description = "Explain query execution plan",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    put("database_uri", buildJsonObject {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("Database URI"))
                    })
                    put("query", buildJsonObject {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("SQL query to explain"))
                    })
                },
                required = listOf("database_uri", "query")
            )
        )
    )
}
```

### Step 8: Built-in Query Resources

Add to `ResourceProvider.kt`:

```kotlin
private suspend fun createDatabaseQueryResources(): List<Resource> {
    return listOf(
        Resource(
            uri = "android://database/query/recent",
            name = "Recent Database Queries",
            description = "History of recently executed database queries",
            mimeType = "application/json"
        ),
        
        Resource(
            uri = "android://database/schema/all",
            name = "All Database Schemas",
            description = "Complete schema information for all accessible databases",
            mimeType = "application/json"
        ),
        
        Resource(
            uri = "android://database/performance/stats",
            name = "Database Performance Statistics",
            description = "Query execution statistics and performance metrics",
            mimeType = "application/json"
        )
    )
}
```

## Verification Steps

### 1. Unit Tests

Create `lib/src/test/kotlin/dev/jasonpearson/mcpandroidsdk/database/DatabaseQueryTest.kt`:

```kotlin
@Test
fun `SafeQueryBuilder should prevent SQL injection in WHERE clauses`()

@Test 
fun `query parameter sanitization should work correctly`()

@Test
fun `query validation should detect unsafe patterns`()

@Test
fun `result formatting should handle all data types correctly`()

@Test
fun `pagination should work for large result sets`()

@Test
fun `sensitive data masking should protect PII`()
```

### 2. Integration Tests

Create `lib/src/androidTest/kotlin/dev/jasonpearson/mcpandroidsdk/DatabaseQueryIntegrationTest.kt`:

```kotlin
@Test
fun `Room database queries should execute correctly`()

@Test
fun `SQLite direct queries should work with file databases`()

@Test
fun `Content Provider queries should respect permissions`()

@Test
fun `Query results should format correctly in all output formats`()

@Test
fun `Database schema introspection should work`()

@Test
fun `Query performance monitoring should track execution times`()
```

### 3. Manual Testing

1. **Execute simple SELECT queries** on test databases
2. **Test complex queries** with JOINs and subqueries
3. **Verify SQL injection protection** with malicious inputs
4. **Test pagination** with large result sets
5. **Validate output formats** (JSON, CSV, XML)
6. **Test schema introspection** on Room and SQLite databases

## Dependencies

- **Task 08**: Database Resources Implementation (must be completed first)
- **Core ResourceProvider**: Basic resource framework
- **Core ToolProvider**: Tool execution framework

## Success Criteria

- [ ] Safe SQL query execution for all database types
- [ ] Multiple output format support (JSON, CSV, XML)
- [ ] SQL injection prevention mechanisms active
- [ ] Query validation and sanitization working
- [ ] Database schema introspection complete
- [ ] Pagination support for large result sets
- [ ] Query performance monitoring enabled
- [ ] Sensitive data masking functional
- [ ] MCP tool integration complete
- [ ] Test coverage >90% for query operations
- [ ] Sample app demonstrates all query patterns

## Resources

### Android Documentation

- [Room Database Queries](https://developer.android.com/training/data-storage/room/accessing-data)
- [SQLite Query Language](https://www.sqlite.org/lang.html)
- [Content Provider Query](https://developer.android.com/guide/topics/providers/content-provider-basics#SimpleQuery)

### Security Resources

- [SQL Injection Prevention](https://owasp.org/www-community/attacks/SQL_Injection)
- [Parameterized Queries](https://cheatsheetseries.owasp.org/cheatsheets/Query_Parameterization_Cheat_Sheet.html)
- [Android Security Guidelines](https://developer.android.com/training/articles/security-tips)