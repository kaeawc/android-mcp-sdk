# Task 17: Database Editing Implementation

## Status: `[P]` In Progress - Enhanced Schema Intelligence Integration Required

## Objective

Implement secure database modification capabilities for Android applications with intelligent schema
validation, Room/SQLDelight integration, and comprehensive safety mechanisms. Provide safe INSERT,
UPDATE, DELETE operations with schema-aware validation, transaction support, and audit logging for
Room databases, SQLite, and Content Providers.

## Enhanced Requirements

### Schema Intelligence Integration

- **Schema-Aware Validation**: Use cached table schemas from Task 16 to validate all edit operations
  before execution
- **Room Entity Integration**: Leverage Room entity information for type-safe edit operations and
  constraint validation
- **SQLDelight Integration**: Use SQLDelight schema information for compile-time validated edit
  operations
- **Automatic Schema Dependency**: Automatically refresh schema cache if edit operations fail due to
  schema mismatches
- **Source Code Correlation**: Use Room/SQLDelight source mappings to provide intelligent error
  messages and validation

### Enhanced Safety Requirements

- **Pre-execution Validation**: Validate all edit operations against cached schema before database
  interaction
- **Constraint Checking**: Comprehensive foreign key, check constraint, and unique constraint
  validation
- **Type Safety**: Leverage Room entity type information for safe data conversion and validation
- **Transaction Intelligence**: Smart transaction boundaries based on schema relationships and
  dependencies
- **Backup Integration**: Automatic backups with schema-aware restoration capabilities

### Technical Requirements

- **Safe Write Operations**: Schema-validated INSERT, UPDATE, DELETE with comprehensive constraint
  checking
- **Transaction Support**: ACID transactions with schema-aware rollback capabilities
- **Batch Operations**: Efficient bulk data modifications with constraint validation
- **Schema Validation**: Real-time constraint compliance using cached schema information
- **Backup and Recovery**: Automatic backups with schema-aware recovery mechanisms
- **Change Tracking**: Detailed audit logs with schema context and relationship awareness
- **Permission Control**: Granular write permissions per table/operation based on schema metadata

### Security Requirements

- **SQL Injection Prevention**: Enhanced parameterization with schema-aware type checking and
  validation
- **Data Validation**: Comprehensive type checking using Room/SQLDelight type information
- **Access Control**: Schema-based permissions for database modifications with entity-level controls
- **Audit Logging**: Complete trail of changes with schema context and relationship impact analysis
- **Backup Integration**: Schema-aware backup creation before potentially destructive operations
- **Rollback Capabilities**: Intelligent transaction rollback with constraint dependency awareness

## Implementation Steps

### Step 1: Enhanced Database Editor Framework with Schema Integration

Create `core/src/main/kotlin/dev/jasonpearson/mcpandroidsdk/database/SchemaAwareDatabaseEditor.kt`:

```kotlin
class SchemaAwareDatabaseEditor(
    private val context: Context,
    private val schemaCache: DatabaseSchemaCache
) {
    
    data class EditConfig(
        val enableTransactions: Boolean = true,
        val autoBackup: Boolean = true,
        val validateConstraints: Boolean = true,
        val auditChanges: Boolean = true,
        val maxBatchSize: Int = 1000,
        val dryRun: Boolean = false,
        val useSchemaValidation: Boolean = true,
        val refreshSchemaOnFailure: Boolean = true
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
        val schemaWarnings: List<String> = emptyList(),
        val constraintViolations: List<ConstraintViolation> = emptyList(),
        val auditId: String? = null
    )
    
    suspend fun executeSchemaAwareEdit(request: EditRequest): EditResult {
        // Step 1: Validate schema availability
        val schema = schemaCache.getOrLoadSchema(request.databaseUri)
            ?: return EditResult(
                success = false,
                rowsAffected = 0,
                executionTimeMs = 0,
                validationErrors = listOf("Schema unavailable for database: ${request.databaseUri}")
            )
        
        // Step 2: Validate table exists and get schema
        val tableSchema = schema.tables[request.tableName]
            ?: return EditResult(
                success = false,
                rowsAffected = 0,
                executionTimeMs = 0,
                validationErrors = listOf("Table '${request.tableName}' not found in schema")
            )
        
        // Step 3: Validate operation against schema
        val validation = validateOperationAgainstSchema(request, tableSchema, schema)
        if (!validation.isValid) {
            return EditResult(
                success = false,
                rowsAffected = 0,
                executionTimeMs = 0,
                validationErrors = validation.errors,
                constraintViolations = validation.constraintViolations
            )
        }
        
        // Step 4: Execute with schema-aware safety
        return executeWithSchemaValidation(request, tableSchema, schema)
    }
    
    suspend fun executeTransaction(requests: List<EditRequest>): TransactionResult
    suspend fun rollbackTransaction(transactionId: String): EditResult
    
    private suspend fun validateOperationAgainstSchema(
        request: EditRequest,
        tableSchema: TableSchema,
        databaseSchema: CachedDatabaseSchema
    ): SchemaValidationResult
    
    private suspend fun executeWithSchemaValidation(
        request: EditRequest,
        tableSchema: TableSchema,
        databaseSchema: CachedDatabaseSchema
    ): EditResult
}
```

### Step 2: Room-Aware Database Editor

Create `core/src/main/kotlin/dev/jasonpearson/mcpandroidsdk/database/RoomAwareDatabaseEditor.kt`:

```kotlin
class RoomAwareDatabaseEditor(
    private val context: Context,
    private val schemaCache: DatabaseSchemaCache
) {
    
    data class RoomEditConfig(
        val database: RoomDatabase,
        val validateEntities: Boolean = true,
        val useDAOs: Boolean = true,
        val enableForeignKeyConstraints: Boolean = true,
        val validateRelationships: Boolean = true
    )
    
    suspend fun insertEntityWithSchemaValidation(
        database: RoomDatabase,
        entity: Any,
        config: RoomEditConfig
    ): EditResult {
        val databaseUri = getDatabaseUri(database)
        val schema = schemaCache.getOrLoadSchema(databaseUri)
            ?: return EditResult.schemaUnavailable()
        
        return database.withTransaction {
            try {
                // Get Room entity information from schema
                val entityClass = entity::class
                val roomEntityInfo = findRoomEntityInfo(schema, entityClass)
                
                if (roomEntityInfo != null) {
                    // Use Room-aware validation
                    val validation = validateRoomEntity(entity, roomEntityInfo, schema)
                    if (!validation.isValid) {
                        return@withTransaction EditResult.validationFailed(validation.errors)
                    }
                    
                    // Execute using Room DAO if available
                    val result = if (config.useDAOs) {
                        insertViaRoomDao(database, entity, roomEntityInfo)
                    } else {
                        insertViaRawQueryWithSchemaValidation(database, entity, roomEntityInfo, schema)
                    }
                    
                    EditResult(
                        success = true,
                        rowsAffected = 1,
                        lastInsertId = result,
                        executionTimeMs = measureTimeMillis { /* operation */ }
                    )
                } else {
                    // Fallback to generic schema validation
                    executeGenericInsertWithSchemaValidation(database, entity, schema)
                }
            } catch (e: Exception) {
                EditResult(
                    success = false,
                    rowsAffected = 0,
                    executionTimeMs = 0,
                    validationErrors = listOf("Room entity insert failed: ${e.message}")
                )
            }
        }
    }
    
    suspend fun updateEntityWithSchemaValidation(
        database: RoomDatabase,
        entity: Any,
        whereClause: String = "",
        config: RoomEditConfig
    ): EditResult
    
    suspend fun deleteEntityWithSchemaValidation(
        database: RoomDatabase,
        entityClass: KClass<*>,
        whereClause: String,
        whereArgs: Array<String>,
        config: RoomEditConfig
    ): EditResult
    
    private fun validateRoomEntity(entity: Any, roomEntityInfo: RoomEntityInfo, schema: CachedDatabaseSchema): RoomValidationResult
    private fun findRoomEntityInfo(schema: CachedDatabaseSchema, entityClass: KClass<*>): RoomEntityInfo?
    private suspend fun insertViaRoomDao(database: RoomDatabase, entity: Any, roomEntityInfo: RoomEntityInfo): Long
}
```

### Step 3: Schema-Aware Data Validation Framework

Create
`core/src/main/kotlin/dev/jasonpearson/mcpandroidsdk/database/validation/SchemaAwareDataValidator.kt`:

```kotlin
class SchemaAwareDataValidator(
    private val schemaCache: DatabaseSchemaCache
) {
    
    data class SchemaValidationResult(
        val isValid: Boolean,
        val errors: List<String> = emptyList(),
        val warnings: List<String> = emptyList(),
        val constraintViolations: List<ConstraintViolation> = emptyList(),
        val typeConversions: List<TypeConversion> = emptyList(),
        val relationshipImpacts: List<RelationshipImpact> = emptyList()
    )
    
    data class ConstraintViolation(
        val constraintType: ConstraintType,
        val columnName: String,
        val violationDescription: String,
        val suggestedFix: String? = null
    )
    
    enum class ConstraintType {
        PRIMARY_KEY, FOREIGN_KEY, UNIQUE, CHECK, NOT_NULL, DATA_TYPE, ROOM_RELATION
    }
    
    suspend fun validateInsert(
        databaseUri: String,
        tableName: String,
        data: Map<String, Any>
    ): SchemaValidationResult {
        val schema = schemaCache.getOrLoadSchema(databaseUri)
            ?: return SchemaValidationResult(
                isValid = false,
                errors = listOf("Schema not available for validation")
            )
        
        val tableSchema = schema.tables[tableName]
            ?: return SchemaValidationResult(
                isValid = false,
                errors = listOf("Table '$tableName' not found in schema")
            )
        
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        val violations = mutableListOf<ConstraintViolation>()
        val conversions = mutableListOf<TypeConversion>()
        val impacts = mutableListOf<RelationshipImpact>()
        
        // Validate column existence and types
        for ((columnName, value) in data) {
            val columnSchema = tableSchema.columns.find { it.name == columnName }
            if (columnSchema == null) {
                warnings.add("Column '$columnName' not found in schema - may be ignored")
                continue
            }
            
            // Type validation with Room integration
            val typeValidation = validateColumnType(value, columnSchema)
            if (!typeValidation.isValid) {
                violations.add(ConstraintViolation(
                    constraintType = ConstraintType.DATA_TYPE,
                    columnName = columnName,
                    violationDescription = typeValidation.error,
                    suggestedFix = typeValidation.suggestedFix
                ))
            }
            
            if (typeValidation.requiresConversion) {
                conversions.add(typeValidation.conversion)
            }
            
            // Null constraint validation
            if (!columnSchema.isNullable && (value == null || value.toString().isBlank())) {
                violations.add(ConstraintViolation(
                    constraintType = ConstraintType.NOT_NULL,
                    columnName = columnName,
                    violationDescription = "Column '$columnName' cannot be null"
                ))
            }
        }
        
        // Required column validation
        for (column in tableSchema.columns.filter { !it.isNullable && it.defaultValue == null && !it.isAutoIncrement }) {
            if (!data.containsKey(column.name)) {
                violations.add(ConstraintViolation(
                    constraintType = ConstraintType.NOT_NULL,
                    columnName = column.name,
                    violationDescription = "Required column '${column.name}' is missing"
                ))
            }
        }
        
        // Foreign key validation
        for (foreignKey in tableSchema.foreignKeys) {
            val fkValidation = validateForeignKeyConstraint(foreignKey, data, schema)
            if (!fkValidation.isValid) {
                violations.addAll(fkValidation.violations)
            }
        }
        
        // Room relationship validation
        if (tableSchema.roomEntityInfo != null) {
            val roomValidation = validateRoomRelationships(tableSchema.roomEntityInfo, data, schema)
            violations.addAll(roomValidation.violations)
            impacts.addAll(roomValidation.relationshipImpacts)
        }
        
        return SchemaValidationResult(
            isValid = violations.isEmpty(),
            errors = errors,
            warnings = warnings,
            constraintViolations = violations,
            typeConversions = conversions,
            relationshipImpacts = impacts
        )
    }
    
    suspend fun validateUpdate(
        databaseUri: String,
        tableName: String,
        data: Map<String, Any>,
        whereClause: String,
        whereArgs: Array<String>
    ): SchemaValidationResult
    
    suspend fun validateDelete(
        databaseUri: String,
        tableName: String,
        whereClause: String,
        whereArgs: Array<String>
    ): SchemaValidationResult
    
    private fun validateColumnType(value: Any?, columnSchema: ColumnSchema): TypeValidationResult
    private fun validateForeignKeyConstraint(foreignKey: ForeignKeyConstraint, data: Map<String, Any>, schema: CachedDatabaseSchema): ForeignKeyValidationResult
    private fun validateRoomRelationships(roomEntityInfo: RoomEntityInfo, data: Map<String, Any>, schema: CachedDatabaseSchema): RoomRelationshipValidationResult
}
```

### Step 4: Enhanced MCP Tool Integration

Update existing database tools in `DatabaseToolProvider.kt`:

```kotlin
private fun createEnhancedDatabaseEditTools(): List<Tool> {
    return listOf(
        Tool(
            name = "database_schema_aware_insert",
            description = "Insert new record with comprehensive schema validation and Room/SQLDelight integration",
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
                        put("description", JsonPrimitive("Record data with schema-aware validation"))
                    })
                    put("validate_relationships", buildJsonObject {
                        put("type", JsonPrimitive("boolean"))
                        put("default", JsonPrimitive(true))
                        put("description", JsonPrimitive("Validate Room relationships and foreign keys"))
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
            name = "database_schema_aware_update",
            description = "Update existing records with comprehensive schema validation",
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
                        put("description", JsonPrimitive("Updated data with schema validation"))
                    })
                    put("where_clause", buildJsonObject {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("WHERE clause to identify records"))
                    })
                    put("where_args", buildJsonObject {
                        put("type", JsonPrimitive("array"))
                        put("description", JsonPrimitive("WHERE clause parameters"))
                    })
                    put("validate_constraints", buildJsonObject {
                        put("type", JsonPrimitive("boolean"))
                        put("default", JsonPrimitive(true))
                        put("description", JsonPrimitive("Validate all constraints before update"))
                    })
                },
                required = listOf("database_uri", "table_name", "data", "where_clause")
            )
        ),
        
        Tool(
            name = "database_schema_aware_delete",
            description = "Delete records with relationship impact analysis",
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
                    put("analyze_impact", buildJsonObject {
                        put("type", JsonPrimitive("boolean"))
                        put("default", JsonPrimitive(true))
                        put("description", JsonPrimitive("Analyze foreign key relationship impact"))
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
            name = "database_validate_edit_operation",
            description = "Validate edit operation against schema without execution",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    put("database_uri", buildJsonObject {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("Database URI"))
                    })
                    put("operation", buildJsonObject {
                        put("type", JsonPrimitive("string"))
                        put("enum", buildJsonArray {
                            add("insert")
                            add("update")
                            add("delete")
                        })
                        put("description", JsonPrimitive("Operation type to validate"))
                    })
                    put("table_name", buildJsonObject {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("Table name"))
                    })
                    put("data", buildJsonObject {
                        put("type", JsonPrimitive("object"))
                        put("description", JsonPrimitive("Operation data"))
                    })
                    put("include_relationship_analysis", buildJsonObject {
                        put("type", JsonPrimitive("boolean"))
                        put("default", JsonPrimitive(true))
                        put("description", JsonPrimitive("Include relationship impact analysis"))
                    })
                },
                required = listOf("database_uri", "operation", "table_name")
            )
        )
    )
}
```

## Dependencies

- **Task 16**: Database Querying Implementation (schema cache dependency)
- **DatabaseSchemaCache**: Required for schema-aware validation
- **Room**: `androidx.room:room-runtime` for Room integration
- **SQLDelight**: Optional integration for SQLDelight support

## Success Criteria

- [ ] Schema-aware validation for all edit operations
- [ ] Room entity integration with type-safe operations
- [ ] SQLDelight integration for compile-time validated operations
- [ ] Comprehensive constraint validation using cached schema
- [ ] Foreign key relationship impact analysis
- [ ] Automatic schema refresh on validation failures
- [ ] Enhanced MCP tools with schema intelligence
- [ ] Transaction support with schema-aware rollback
- [ ] Audit logging with schema context
- [ ] Test coverage >95% for schema-aware edit operations
