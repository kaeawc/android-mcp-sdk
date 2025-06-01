# Task 17: Database Editing Implementation

## Status: `[ ]` Not Started

## Objective

Implement secure database modification capabilities for Android applications, providing safe INSERT,
UPDATE, DELETE operations with comprehensive validation, transaction support, and audit logging for
Room databases, SQLite, and Content Providers.

## Requirements

### Technical Requirements

- **Safe Write Operations**: Parameterized INSERT, UPDATE, DELETE with validation
- **Transaction Support**: ACID transactions with rollback capabilities
- **Batch Operations**: Efficient bulk data modifications
- **Schema Validation**: Ensure data integrity and constraint compliance
- **Backup and Recovery**: Automatic backups before destructive operations
- **Change Tracking**: Detailed audit logs of all database modifications
- **Permission Control**: Granular write permissions per table/operation

### Security Requirements

- **SQL Injection Prevention**: Comprehensive input validation for all write operations
- **Data Validation**: Type checking and constraint enforcement
- **Access Control**: Role-based permissions for database modifications
- **Audit Logging**: Complete trail of all database changes with user context
- **Backup Integration**: Automatic backup before potentially destructive operations
- **Rollback Capabilities**: Transaction rollback on validation failures

## Implementation Steps

### Step 1: Create Database Editor Framework

Create `lib/src/main/kotlin/dev/jasonpearson/mcpandroidsdk/database/DatabaseEditor.kt`:

```kotlin
class DatabaseEditor(
    private val context: Context
) {
    
    data class EditConfig(
        val enableTransactions: Boolean = true,
        val autoBackup: Boolean = true,
        val validateConstraints: Boolean = true,
        val auditChanges: Boolean = true,
        val maxBatchSize: Int = 1000,
        val dryRun: Boolean = false
    )
    
    data class EditRequest(
        val databaseUri: String,
        val operation: DatabaseOperation,
        val tableName: String,
        val data: Map<String, Any>,
        val whereClause: String? = null,
        val whereArgs: Array<String> = emptyArray(),
        val config: EditConfig = EditConfig()
    )
    
    sealed class DatabaseOperation {
        object Insert : DatabaseOperation()
        object Update : DatabaseOperation()
        object Delete : DatabaseOperation()
        data class BatchInsert(val records: List<Map<String, Any>>) : DatabaseOperation()
        data class BatchUpdate(val updates: List<UpdateRecord>) : DatabaseOperation()
        data class BatchDelete(val conditions: List<DeleteCondition>) : DatabaseOperation()
    }
    
    data class EditResult(
        val success: Boolean,
        val rowsAffected: Int,
        val lastInsertId: Long? = null,
        val executionTimeMs: Long,
        val backupPath: String? = null,
        val validationErrors: List<String> = emptyList(),
        val auditId: String? = null
    )
    
    suspend fun executeEdit(request: EditRequest): EditResult
    suspend fun executeTransaction(requests: List<EditRequest>): TransactionResult
    suspend fun rollbackTransaction(transactionId: String): EditResult
}
```

### Step 2: Room Database Editing Support

Create `lib/src/main/kotlin/dev/jasonpearson/mcpandroidsdk/database/RoomDatabaseEditor.kt`:

```kotlin
class RoomDatabaseEditor(
    private val context: Context
) {
    
    data class RoomEditConfig(
        val database: RoomDatabase,
        val validateEntities: Boolean = true,
        val useDAOs: Boolean = true,
        val enableForeignKeyConstraints: Boolean = true
    )
    
    suspend fun insertEntity(
        database: RoomDatabase,
        entity: Any,
        config: RoomEditConfig
    ): EditResult {
        return database.withTransaction {
            try {
                val dao = findDaoForEntity(database, entity::class)
                val result = if (config.useDAOs && dao != null) {
                    insertViaDao(dao, entity)
                } else {
                    insertViaRawQuery(database, entity)
                }
                
                EditResult(
                    success = true,
                    rowsAffected = 1,
                    lastInsertId = result,
                    executionTimeMs = measureTimeMillis { /* operation */ }
                )
            } catch (e: Exception) {
                EditResult(
                    success = false,
                    rowsAffected = 0,
                    executionTimeMs = 0,
                    validationErrors = listOf(e.message ?: "Unknown error")
                )
            }
        }
    }
    
    suspend fun updateEntity(
        database: RoomDatabase,
        entity: Any,
        whereClause: String = "",
        config: RoomEditConfig
    ): EditResult
    
    suspend fun deleteEntity(
        database: RoomDatabase,
        entityClass: KClass<*>,
        whereClause: String,
        whereArgs: Array<String>,
        config: RoomEditConfig
    ): EditResult
    
    suspend fun batchInsertEntities(
        database: RoomDatabase,
        entities: List<Any>,
        config: RoomEditConfig
    ): EditResult
    
    private fun validateEntityConstraints(entity: Any, database: RoomDatabase): List<String>
    private fun findDaoForEntity(database: RoomDatabase, entityClass: KClass<*>): Any?
}
```

### Step 3: SQLite Direct Editing Support

Create `lib/src/main/kotlin/dev/jasonpearson/mcpandroidsdk/database/SqliteDatabaseEditor.kt`:

```kotlin
class SqliteDatabaseEditor(
    private val context: Context
) {
    
    data class SqliteEditConfig(
        val databasePath: String,
        val enableWAL: Boolean = true,
        val validateSchema: Boolean = true,
        val foreignKeysEnabled: Boolean = true
    )
    
    suspend fun insertRecord(
        config: SqliteEditConfig,
        tableName: String,
        values: ContentValues
    ): EditResult {
        return withContext(Dispatchers.IO) {
            val database = openDatabase(config)
            
            try {
                database.beginTransaction()
                
                val validation = validateInsert(database, tableName, values)
                if (validation.isNotEmpty()) {
                    return@withContext EditResult(
                        success = false,
                        rowsAffected = 0,
                        executionTimeMs = 0,
                        validationErrors = validation
                    )
                }
                
                val startTime = System.currentTimeMillis()
                val insertId = database.insert(tableName, null, values)
                val executionTime = System.currentTimeMillis() - startTime
                
                database.setTransactionSuccessful()
                
                EditResult(
                    success = insertId != -1L,
                    rowsAffected = if (insertId != -1L) 1 else 0,
                    lastInsertId = insertId,
                    executionTimeMs = executionTime
                )
            } catch (e: Exception) {
                EditResult(
                    success = false,
                    rowsAffected = 0,
                    executionTimeMs = 0,
                    validationErrors = listOf(e.message ?: "Database error")
                )
            } finally {
                database.endTransaction()
                database.close()
            }
        }
    }
    
    suspend fun updateRecord(
        config: SqliteEditConfig,
        tableName: String,
        values: ContentValues,
        whereClause: String,
        whereArgs: Array<String>
    ): EditResult
    
    suspend fun deleteRecords(
        config: SqliteEditConfig,
        tableName: String,
        whereClause: String,
        whereArgs: Array<String>
    ): EditResult
    
    suspend fun executeBulkInsert(
        config: SqliteEditConfig,
        tableName: String,
        records: List<ContentValues>
    ): EditResult
    
    private fun validateInsert(database: SQLiteDatabase, tableName: String, values: ContentValues): List<String>
    private fun validateUpdate(database: SQLiteDatabase, tableName: String, values: ContentValues): List<String>
    private fun createBackup(databasePath: String): String
}
```

### Step 4: Content Provider Editing Support

Create `lib/src/main/kotlin/dev/jasonpearson/mcpandroidsdk/database/ContentProviderEditor.kt`:

```kotlin
class ContentProviderEditor(
    private val context: Context
) {
    
    data class ContentProviderEditConfig(
        val authority: String,
        val requirePermissions: Set<String> = emptySet(),
        val validateWriteAccess: Boolean = true
    )
    
    suspend fun insertContent(
        config: ContentProviderEditConfig,
        path: String,
        values: ContentValues
    ): EditResult {
        return withContext(Dispatchers.IO) {
            try {
                checkPermissions(config.requirePermissions)
                
                val uri = Uri.parse("content://${config.authority}/$path")
                val resultUri = context.contentResolver.insert(uri, values)
                
                EditResult(
                    success = resultUri != null,
                    rowsAffected = if (resultUri != null) 1 else 0,
                    lastInsertId = resultUri?.lastPathSegment?.toLongOrNull(),
                    executionTimeMs = 0 // Content providers don't provide timing
                )
            } catch (e: SecurityException) {
                EditResult(
                    success = false,
                    rowsAffected = 0,
                    executionTimeMs = 0,
                    validationErrors = listOf("Permission denied: ${e.message}")
                )
            } catch (e: Exception) {
                EditResult(
                    success = false,
                    rowsAffected = 0,
                    executionTimeMs = 0,
                    validationErrors = listOf(e.message ?: "Content provider error")
                )
            }
        }
    }
    
    suspend fun updateContent(
        config: ContentProviderEditConfig,
        path: String,
        values: ContentValues,
        selection: String?,
        selectionArgs: Array<String>?
    ): EditResult
    
    suspend fun deleteContent(
        config: ContentProviderEditConfig,
        path: String,
        selection: String?,
        selectionArgs: Array<String>?
    ): EditResult
    
    suspend fun bulkInsertContent(
        config: ContentProviderEditConfig,
        path: String,
        valuesArray: Array<ContentValues>
    ): EditResult
    
    // Built-in provider editing helpers
    suspend fun insertContact(contactData: ContactData): EditResult
    suspend fun updateContact(contactId: Long, contactData: ContactData): EditResult
    suspend fun deleteContact(contactId: Long): EditResult
    
    private fun checkPermissions(permissions: Set<String>)
}
```

### Step 5: Transaction Management

Create `lib/src/main/kotlin/dev/jasonpearson/mcpandroidsdk/database/TransactionManager.kt`:

```kotlin
class TransactionManager(
    private val context: Context
) {
    
    data class Transaction(
        val id: String = UUID.randomUUID().toString(),
        val operations: List<EditRequest>,
        val startTime: Long = System.currentTimeMillis(),
        val status: TransactionStatus = TransactionStatus.PENDING
    )
    
    enum class TransactionStatus {
        PENDING, EXECUTING, COMMITTED, ROLLED_BACK, FAILED
    }
    
    data class TransactionResult(
        val transactionId: String,
        val success: Boolean,
        val results: List<EditResult>,
        val totalRowsAffected: Int,
        val executionTimeMs: Long,
        val rollbackPoint: String? = null
    )
    
    suspend fun executeTransaction(operations: List<EditRequest>): TransactionResult {
        val transaction = Transaction(operations = operations)
        val startTime = System.currentTimeMillis()
        
        return try {
            val rollbackPoint = createRollbackPoint(operations)
            val results = mutableListOf<EditResult>()
            var totalRowsAffected = 0
            
            for (operation in operations) {
                val result = executeEditOperation(operation)
                results.add(result)
                
                if (!result.success) {
                    rollbackToPoint(rollbackPoint)
                    return TransactionResult(
                        transactionId = transaction.id,
                        success = false,
                        results = results,
                        totalRowsAffected = 0,
                        executionTimeMs = System.currentTimeMillis() - startTime,
                        rollbackPoint = rollbackPoint
                    )
                }
                
                totalRowsAffected += result.rowsAffected
            }
            
            TransactionResult(
                transactionId = transaction.id,
                success = true,
                results = results,
                totalRowsAffected = totalRowsAffected,
                executionTimeMs = System.currentTimeMillis() - startTime
            )
        } catch (e: Exception) {
            TransactionResult(
                transactionId = transaction.id,
                success = false,
                results = emptyList(),
                totalRowsAffected = 0,
                executionTimeMs = System.currentTimeMillis() - startTime
            )
        }
    }
    
    private suspend fun createRollbackPoint(operations: List<EditRequest>): String
    private suspend fun rollbackToPoint(rollbackPoint: String)
    private suspend fun executeEditOperation(operation: EditRequest): EditResult
}
```

### Step 6: Data Validation Framework

Create `lib/src/main/kotlin/dev/jasonpearson/mcpandroidsdk/database/DataValidator.kt`:

```kotlin
class DataValidator {
    
    data class ValidationRule(
        val columnName: String,
        val type: DataType,
        val required: Boolean = false,
        val maxLength: Int? = null,
        val pattern: Regex? = null,
        val customValidator: ((Any?) -> String?)? = null
    )
    
    enum class DataType {
        INTEGER, REAL, TEXT, BLOB, BOOLEAN, DATE, TIMESTAMP
    }
    
    data class ValidationResult(
        val isValid: Boolean,
        val errors: List<String> = emptyList(),
        val warnings: List<String> = emptyList()
    )
    
    fun validateRecord(
        data: Map<String, Any?>,
        rules: List<ValidationRule>
    ): ValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        
        for (rule in rules) {
            val value = data[rule.columnName]
            
            // Check required fields
            if (rule.required && (value == null || value.toString().isBlank())) {
                errors.add("Column '${rule.columnName}' is required")
                continue
            }
            
            if (value != null) {
                // Type validation
                val typeError = validateType(value, rule.type)
                if (typeError != null) {
                    errors.add("Column '${rule.columnName}': $typeError")
                }
                
                // Length validation
                if (rule.maxLength != null && value.toString().length > rule.maxLength) {
                    errors.add("Column '${rule.columnName}' exceeds maximum length of ${rule.maxLength}")
                }
                
                // Pattern validation
                if (rule.pattern != null && !rule.pattern.matches(value.toString())) {
                    errors.add("Column '${rule.columnName}' does not match required pattern")
                }
                
                // Custom validation
                rule.customValidator?.invoke(value)?.let { error ->
                    errors.add("Column '${rule.columnName}': $error")
                }
            }
        }
        
        return ValidationResult(
            isValid = errors.isEmpty(),
            errors = errors,
            warnings = warnings
        )
    }
    
    private fun validateType(value: Any, expectedType: DataType): String? {
        return when (expectedType) {
            DataType.INTEGER -> if (value !is Number) "Expected integer" else null
            DataType.REAL -> if (value !is Number) "Expected real number" else null
            DataType.TEXT -> if (value !is String) "Expected text" else null
            DataType.BOOLEAN -> if (value !is Boolean) "Expected boolean" else null
            DataType.DATE -> validateDate(value.toString())
            DataType.TIMESTAMP -> validateTimestamp(value.toString())
            DataType.BLOB -> null // Accept any type for blob
        }
    }
    
    private fun validateDate(value: String): String?
    private fun validateTimestamp(value: String): String?
}
```

### Step 7: MCP Tool Integration

Add to existing `ToolProvider.kt`:

```kotlin
private fun createDatabaseEditingTools(): List<Tool> {
    return listOf(
        Tool(
            name = "database_insert",
            description = "Insert new record into database table",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    put("database_uri", buildJsonObject {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("Database URI"))
                    })
                    put("table_name", buildJsonObject {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("Table name"))
                    })
                    put("data", buildJsonObject {
                        put("type", JsonPrimitive("object"))
                        put("description", JsonPrimitive("Record data as key-value pairs"))
                    })
                    put("dry_run", buildJsonObject {
                        put("type", JsonPrimitive("boolean"))
                        put("default", JsonPrimitive(false))
                        put("description", JsonPrimitive("Validate without executing"))
                    })
                },
                required = listOf("database_uri", "table_name", "data")
            )
        ),
        
        Tool(
            name = "database_update",
            description = "Update existing database records",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    put("database_uri", buildJsonObject {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("Database URI"))
                    })
                    put("table_name", buildJsonObject {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("Table name"))
                    })
                    put("data", buildJsonObject {
                        put("type", JsonPrimitive("object"))
                        put("description", JsonPrimitive("Updated data as key-value pairs"))
                    })
                    put("where_clause", buildJsonObject {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("WHERE clause to identify records"))
                    })
                    put("where_args", buildJsonObject {
                        put("type", JsonPrimitive("array"))
                        put("description", JsonPrimitive("WHERE clause parameters"))
                    })
                },
                required = listOf("database_uri", "table_name", "data", "where_clause")
            )
        ),
        
        Tool(
            name = "database_delete",
            description = "Delete records from database table",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    put("database_uri", buildJsonObject {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("Database URI"))
                    })
                    put("table_name", buildJsonObject {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("Table name"))
                    })
                    put("where_clause", buildJsonObject {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("WHERE clause to identify records"))
                    })
                    put("where_args", buildJsonObject {
                        put("type", JsonPrimitive("array"))
                        put("description", JsonPrimitive("WHERE clause parameters"))
                    })
                    put("confirm", buildJsonObject {
                        put("type", JsonPrimitive("boolean"))
                        put("default", JsonPrimitive(false))
                        put("description", JsonPrimitive("Confirm deletion"))
                    })
                },
                required = listOf("database_uri", "table_name", "where_clause", "confirm")
            )
        ),
        
        Tool(
            name = "database_transaction",
            description = "Execute multiple database operations in a transaction",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    put("operations", buildJsonObject {
                        put("type", JsonPrimitive("array"))
                        put("description", JsonPrimitive("List of database operations"))
                    })
                    put("rollback_on_error", buildJsonObject {
                        put("type", JsonPrimitive("boolean"))
                        put("default", JsonPrimitive(true))
                        put("description", JsonPrimitive("Rollback entire transaction on any error"))
                    })
                },
                required = listOf("operations")
            )
        )
    )
}
```

### Step 8: Audit Logging

Create `lib/src/main/kotlin/dev/jasonpearson/mcpandroidsdk/database/DatabaseAuditLogger.kt`:

```kotlin
class DatabaseAuditLogger(
    private val context: Context
) {
    
    data class AuditRecord(
        val id: String = UUID.randomUUID().toString(),
        val timestamp: Long = System.currentTimeMillis(),
        val operation: String,
        val tableName: String,
        val databaseUri: String,
        val rowsAffected: Int,
        val executionTimeMs: Long,
        val success: Boolean,
        val errorMessage: String? = null,
        val userContext: String? = null,
        val beforeData: String? = null,
        val afterData: String? = null
    )
    
    suspend fun logOperation(
        operation: String,
        tableName: String,
        databaseUri: String,
        result: EditResult,
        beforeData: String? = null,
        afterData: String? = null
    ): String {
        val record = AuditRecord(
            operation = operation,
            tableName = tableName,
            databaseUri = databaseUri,
            rowsAffected = result.rowsAffected,
            executionTimeMs = result.executionTimeMs,
            success = result.success,
            errorMessage = result.validationErrors.joinToString("; ").takeIf { it.isNotEmpty() },
            beforeData = beforeData,
            afterData = afterData
        )
        
        storeAuditRecord(record)
        return record.id
    }
    
    suspend fun getAuditHistory(
        databaseUri: String? = null,
        tableName: String? = null,
        operation: String? = null,
        limit: Int = 100
    ): List<AuditRecord>
    
    suspend fun exportAuditLog(format: String = "json"): String
    
    private suspend fun storeAuditRecord(record: AuditRecord)
}
```

## Verification Steps

### 1. Unit Tests

Create `lib/src/test/kotlin/dev/jasonpearson/mcpandroidsdk/database/DatabaseEditorTest.kt`:

```kotlin
@Test
fun `data validation should prevent invalid inserts`()

@Test
fun `transaction rollback should work on validation failure`()

@Test
fun `SQL injection prevention should work in edit operations`()

@Test
fun `batch operations should handle large datasets efficiently`()

@Test
fun `audit logging should capture all database changes`()

@Test
fun `backup creation should work before destructive operations`()
```

### 2. Integration Tests

Create
`lib/src/androidTest/kotlin/dev/jasonpearson/mcpandroidsdk/DatabaseEditingIntegrationTest.kt`:

```kotlin
@Test
fun `Room entity insertion should work with validation`()

@Test
fun `SQLite direct editing should respect constraints`()

@Test
fun `Content Provider editing should handle permissions`()

@Test
fun `Transaction management should ensure ACID properties`()

@Test
fun `Audit trail should be complete and accurate`()
```

### 3. Manual Testing

1. **Test data insertion** with valid and invalid data
2. **Verify transaction rollback** on constraint violations
3. **Test batch operations** with large datasets
4. **Validate audit logging** captures all changes
5. **Test backup and recovery** functionality
6. **Verify permission handling** for restricted operations

## Dependencies

- **Task 08**: Database Resources Implementation (must be completed first)
- **Task 16**: Database Querying (for validation queries)
- **Core ResourceProvider**: Basic resource framework
- **Core ToolProvider**: Tool execution framework

## Success Criteria

- [ ] Safe database modification operations for all database types
- [ ] Transaction support with ACID properties
- [ ] Comprehensive data validation and constraint checking
- [ ] SQL injection prevention for all write operations
- [ ] Complete audit logging of database changes
- [ ] Backup and recovery mechanisms functional
- [ ] Batch operation support for efficiency
- [ ] Permission-based access control working
- [ ] MCP tool integration complete
- [ ] Test coverage >90% for edit operations
- [ ] Sample app demonstrates all editing patterns

## Resources

### Android Documentation

- [Room Database Modifications](https://developer.android.com/training/data-storage/room/accessing-data#update)
- [SQLite Transactions](https://www.sqlite.org/lang_transaction.html)
- [Content Provider Modifications](https://developer.android.com/guide/topics/providers/content-provider-basics#Inserting)

### Security Resources

- [Database Security Best Practices](https://owasp.org/www-community/attacks/SQL_Injection)
- [Android Data Storage Security](https://developer.android.com/training/articles/security-tips#StoringData)
- [Transaction Management](https://developer.android.com/training/data-storage/room/accessing-data#database-transactions)