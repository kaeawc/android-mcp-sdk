# Task 08: Database Resources Implementation

## Objective

Implement comprehensive database resource access for Android applications, including Room database
integration, SQLite support, and secure data exposure through the MCP protocol.

## Requirements

### Technical Requirements

- **Room Database Integration**: First-class support for AndroidX Room
- **SQLite Support**: Direct SQLite database access capabilities
- **Content Provider Integration**: Access shared data through Content Providers
- **Data Security**: Secure access controls and query validation
- **Query Builder**: Safe SQL query construction and execution
- **Schema Introspection**: Automatic database schema discovery
- **Real-time Updates**: Live data synchronization for database changes

### Security Requirements

- **SQL Injection Prevention**: Parameterized queries and input validation
- **Access Control**: Table and column-level permissions
- **Data Sanitization**: Automatic PII detection and masking
- **Audit Logging**: Track database access and modifications

## Implementation Steps

### Step 1: Create Database Resource Framework

Create `core/src/main/kotlin/dev/jasonpearson/mcpandroidsdk/resources/DatabaseResourceProvider.kt`:

```kotlin
class DatabaseResourceProvider(
    private val context: Context
) {
    
    enum class DatabaseType {
        ROOM,
        SQLITE, 
        CONTENT_PROVIDER
    }
    
    data class DatabaseConfig(
        val name: String,
        val type: DatabaseType,
        val allowedTables: Set<String> = emptySet(),
        val allowedOperations: Set<DatabaseOperation> = setOf(DatabaseOperation.READ),
        val maxQueryResults: Int = 1000,
        val enableRealTimeUpdates: Boolean = false
    )
    
    enum class DatabaseOperation {
        READ, WRITE, DELETE, SCHEMA
    }
    
    suspend fun addDatabase(
        uri: String,
        database: Any, // Room database, SQLiteDatabase, or ContentResolver
        config: DatabaseConfig
    ): Result<Unit>
    
    suspend fun addTable(
        databaseUri: String,
        tableName: String,
        config: TableConfig
    ): Result<Unit>
}
```

### Step 2: Room Database Integration

Create `core/src/main/kotlin/dev/jasonpearson/mcpandroidsdk/resources/RoomDatabaseProvider.kt`:

```kotlin
class RoomDatabaseProvider(
    private val context: Context
) {
    
    data class RoomConfig(
        val database: RoomDatabase,
        val exposedEntities: Set<KClass<*>>,
        val customQueries: Map<String, String> = emptyMap(),
        val enableMigrationInfo: Boolean = false
    )
    
    suspend fun addRoomDatabase(
        uri: String,
        config: RoomConfig
    ): Result<Unit>
    
    suspend fun generateEntitySchemas(database: RoomDatabase): List<EntitySchema>
    
    suspend fun executeRoomQuery(
        database: RoomDatabase,
        query: String,
        parameters: Map<String, Any>
    ): QueryResult
    
    private fun createEntityResource(
        databaseUri: String,
        entity: KClass<*>
    ): Resource
}
```

### Step 3: SQLite Direct Access

Create `core/src/main/kotlin/dev/jasonpearson/mcpandroidsdk/resources/SqliteResourceProvider.kt`:

```kotlin
class SqliteResourceProvider(
    private val context: Context
) {
    
    data class SqliteConfig(
        val databasePath: String,
        val readOnly: Boolean = true,
        val allowCustomQueries: Boolean = false,
        val trustedQueries: Set<String> = emptySet()
    )
    
    suspend fun addSqliteDatabase(
        uri: String,
        config: SqliteConfig
    ): Result<Unit>
    
    suspend fun introspectSchema(databasePath: String): DatabaseSchema
    
    suspend fun executeSafeQuery(
        databasePath: String,
        query: String,
        parameters: Array<String>
    ): QueryResult
    
    private fun validateQuery(query: String): QueryValidationResult
}
```

### Step 4: Content Provider Integration

Create
`core/src/main/kotlin/dev/jasonpearson/mcpandroidsdk/resources/ContentProviderResourceProvider.kt`:

```kotlin
class ContentProviderResourceProvider(
    private val context: Context
) {
    
    data class ContentProviderConfig(
        val authority: String,
        val allowedUris: Set<String>,
        val requirePermissions: Set<String> = emptySet(),
        val enableCursor: Boolean = true
    )
    
    suspend fun addContentProvider(
        uri: String,
        config: ContentProviderConfig
    ): Result<Unit>
    
    suspend fun queryContentProvider(
        authority: String,
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?
    ): ContentProviderResult
    
    // Built-in Android content providers
    suspend fun addContactsProvider(): Result<Unit>
    suspend fun addMediaStoreProvider(): Result<Unit>
    suspend fun addCalendarProvider(): Result<Unit>
}
```

### Step 5: Query Builder and Safety

Create `core/src/main/kotlin/dev/jasonpearson/mcpandroidsdk/resources/SafeQueryBuilder.kt`:

```kotlin
class SafeQueryBuilder {
    
    data class QueryContext(
        val tableName: String,
        val allowedColumns: Set<String>,
        val maxResults: Int = 1000,
        val allowJoins: Boolean = false,
        val allowSubqueries: Boolean = false
    )
    
    class SelectBuilder(private val context: QueryContext) {
        fun select(vararg columns: String): SelectBuilder
        fun where(condition: String, vararg args: Any): SelectBuilder
        fun orderBy(column: String, direction: SortDirection = SortDirection.ASC): SelectBuilder
        fun limit(count: Int): SelectBuilder
        fun build(): SafeQuery
    }
    
    data class SafeQuery(
        val sql: String,
        val parameters: Array<String>,
        val estimatedRows: Int
    )
    
    fun from(table: String): SelectBuilder
    fun validateQuery(query: String): List<SecurityIssue>
}
```

### Step 6: Database Schema Discovery

Create `core/src/main/kotlin/dev/jasonpearson/mcpandroidsdk/resources/DatabaseSchemaProvider.kt`:

```kotlin
class DatabaseSchemaProvider {
    
    data class DatabaseSchema(
        val name: String,
        val version: Int,
        val tables: List<TableSchema>,
        val views: List<ViewSchema>,
        val indexes: List<IndexSchema>
    )
    
    data class TableSchema(
        val name: String,
        val columns: List<ColumnSchema>,
        val primaryKey: List<String>,
        val foreignKeys: List<ForeignKeySchema>,
        val constraints: List<ConstraintSchema>
    )
    
    data class ColumnSchema(
        val name: String,
        val type: String,
        val nullable: Boolean,
        val defaultValue: String?,
        val isPrimaryKey: Boolean,
        val isAutoIncrement: Boolean
    )
    
    suspend fun discoverRoomSchema(database: RoomDatabase): DatabaseSchema
    suspend fun discoverSqliteSchema(databasePath: String): DatabaseSchema
    suspend fun generateSchemaResource(schema: DatabaseSchema): Resource
}
```

### Step 7: Built-in Database Resources

Add to `ResourceProvider.kt`:

```kotlin
private suspend fun registerBuiltInDatabaseResources() {
    // App's SQLite databases
    context.databaseList().forEach { dbName ->
        addResource(Resource(
            uri = "android://database/sqlite/$dbName",
            name = "SQLite Database: $dbName",
            description = "Application SQLite database"
        )) {
            generateDatabaseResource(dbName)
        }
    }
    
    // Shared preferences as database
    addResource(Resource(
        uri = "android://database/preferences",
        name = "Shared Preferences",
        description = "Application preferences as key-value store"
    )) {
        generatePreferencesResource()
    }
    
    // Built-in content providers
    addResource(Resource(
        uri = "android://database/contacts",
        name = "Contacts Database",
        description = "Device contacts (requires permission)"
    )) {
        generateContactsResource()
    }
    
    addResource(Resource(
        uri = "android://database/mediastore",
        name = "MediaStore Database", 
        description = "Device media files metadata"
    )) {
        generateMediaStoreResource()
    }
}
```

### Step 8: Real-time Database Updates

Create `core/src/main/kotlin/dev/jasonpearson/mcpandroidsdk/resources/DatabaseObserver.kt`:

```kotlin
class DatabaseObserver(
    private val context: Context
) {
    
    suspend fun observeRoomDatabase(
        database: RoomDatabase,
        entityClass: KClass<*>
    ): Flow<DatabaseChange>
    
    suspend fun observeContentProvider(
        uri: Uri
    ): Flow<ContentChange>
    
    suspend fun observeSqliteDatabase(
        databasePath: String,
        tableName: String
    ): Flow<DatabaseChange>
    
    data class DatabaseChange(
        val type: ChangeType,
        val tableName: String,
        val affectedRows: List<String>,
        val timestamp: Long
    )
    
    enum class ChangeType {
        INSERT, UPDATE, DELETE, SCHEMA_CHANGE
    }
}
```

### Step 9: Add Dependencies

Update `core/build.gradle.kts`:

```kotlin
dependencies {
    // Room database
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")
    
    // SQLite
    implementation("androidx.sqlite:sqlite-ktx:2.4.0")
    
    // Content providers
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.7.0")
    
    // Reactive streams
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    
    // Reflection for Room introspection
    implementation("org.jetbrains.kotlin:kotlin-reflect:1.9.21")
}
```

## Verification Steps

### 1. Unit Tests

Create `lib/src/test/kotlin/dev/jasonpearson/mcpandroidsdk/resources/DatabaseResourceTest.kt`:

```kotlin
@Test
fun `SafeQueryBuilder should prevent SQL injection`()

@Test
fun `validateQuery should detect malicious SQL patterns`()

@Test
fun `database schema discovery should work for Room databases`()

@Test
fun `table access controls should be enforced`()

@Test
fun `query result limits should be respected`()
```

### 2. Integration Tests with Room

Create `lib/src/androidTest/kotlin/dev/jasonpearson/mcpandroidsdk/RoomDatabaseIntegrationTest.kt`:

```kotlin
@Database(
    entities = [TestUser::class, TestPost::class],
    version = 1
)
abstract class TestDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun postDao(): PostDao
}

@Test
fun `Room database should be accessible via MCP`()

@Test
fun `Room entities should appear as resources`()

@Test
fun `Room queries should execute safely`()

@Test
fun `Room database changes should trigger notifications`()
```

### 3. SQLite Integration Tests

Create `lib/src/androidTest/kotlin/dev/jasonpearson/mcpandroidsdk/SqliteIntegrationTest.kt`:

```kotlin
@Test
fun `SQLite database should be discoverable`()

@Test  
fun `SQLite schema introspection should work`()

@Test
fun `SQLite queries should be validated for safety`()

@Test
fun `SQLite read-only access should be enforced`()
```

### 4. Content Provider Tests

```kotlin
@Test
fun `content providers should be accessible with permissions`()

@Test
fun `contacts provider should work when permission granted`()

@Test
fun `media store provider should expose metadata`()

@Test
fun `content provider queries should respect security boundaries`()
```

### 5. Manual Testing

1. **Create test Room database** in sample app
2. **Verify database appears in MCP resources**
3. **Test schema introspection** returns correct structure
4. **Execute safe queries** and verify results
5. **Test malicious queries** are blocked
6. **Verify real-time updates** work for database changes

## Dependencies

- **Task 07**: File system permissions (for SQLite file access)
- **Core ResourceProvider**: Basic resource framework must exist
- **Task 01**: Resource subscription logic (for database change notifications)

## Resources

### Android Documentation

- [Room Persistence Library](https://developer.android.com/training/data-storage/room)
- [SQLite Database](https://developer.android.com/training/data-storage/sqlite)
- [Content Providers](https://developer.android.com/guide/topics/providers/content-providers)
- [Storage Best Practices](https://developer.android.com/training/data-storage)

### AndroidX Libraries

- [Room Documentation](https://developer.android.com/jetpack/androidx/releases/room)
- [SQLite KTX](https://developer.android.com/kotlin/ktx#sqlite)
- [Lifecycle Components](https://developer.android.com/jetpack/androidx/releases/lifecycle)

### Security Resources

- [SQL Injection Prevention](https://owasp.org/www-community/attacks/SQL_Injection)
- [Android Security Guidelines](https://developer.android.com/training/articles/security-tips)
- [Database Security Best Practices](https://developer.android.com/training/articles/security-tips#StoringData)

## Success Criteria

- [ ] Room databases accessible via MCP resources
- [ ] SQLite direct access with security controls
- [ ] Content Provider integration working
- [ ] SQL injection prevention mechanisms active
- [ ] Database schema introspection complete
- [ ] Real-time database change notifications
- [ ] Query result limits enforced
- [ ] Comprehensive security validation
- [ ] Test coverage >95% for database operations
- [ ] Sample app demonstrates all database access patterns