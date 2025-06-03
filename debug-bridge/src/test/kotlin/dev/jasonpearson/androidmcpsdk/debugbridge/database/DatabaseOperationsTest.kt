package dev.jasonpearson.androidmcpsdk.debugbridge.database

import android.content.Context
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class DatabaseOperationsTest {

    private lateinit var mockContext: Context
    private lateinit var mockDatabaseFactory: SqliteDatabaseFactory
    private lateinit var databaseOperations: DatabaseOperations

    @Before
    fun setUp() {
        mockContext = mockk()
        mockDatabaseFactory = mockk()
        databaseOperations = DatabaseOperations(mockContext, mockDatabaseFactory)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `validateQuerySafety should allow safe SELECT queries`() {
        // Use reflection to access private method
        val method = DatabaseOperations::class.java.getDeclaredMethod(
            "validateQuerySafety",
            String::class.java
        )
        method.isAccessible = true

        // Test safe queries
        val safeQueries = listOf(
            "SELECT * FROM users",
            "select id, name from products",
            "SELECT COUNT(*) FROM orders WHERE status = 'active'",
            "select distinct category from items"
        )

        safeQueries.forEach { query ->
            val result = method.invoke(databaseOperations, query) as Boolean
            assertTrue("Query '$query' should be considered safe", result)
        }
    }

    @Test
    fun `validateQuerySafety should reject unsafe queries`() {
        // Use reflection to access private method
        val method = DatabaseOperations::class.java.getDeclaredMethod(
            "validateQuerySafety",
            String::class.java
        )
        method.isAccessible = true

        // Test unsafe queries
        val unsafeQueries = listOf(
            "DROP TABLE users",
            "DELETE FROM products",
            "UPDATE users SET password = 'hacked'",
            "INSERT INTO admin VALUES ('hacker')",
            "ALTER TABLE users ADD COLUMN hacked TEXT",
            "CREATE TABLE malicious (id INT)",
            "PRAGMA user_version = 999"
        )

        unsafeQueries.forEach { query ->
            val result = method.invoke(databaseOperations, query) as Boolean
            assertFalse("Query '$query' should be considered unsafe", result)
        }
    }

    @Test
    fun `addLimitToQuery should add LIMIT and OFFSET correctly`() {
        // Use reflection to access private method
        val method = DatabaseOperations::class.java.getDeclaredMethod(
            "addLimitToQuery",
            String::class.java,
            Int::class.java,
            Int::class.java
        )
        method.isAccessible = true

        // Test adding limit to query without existing limit
        val query = "SELECT * FROM users"
        val result = method.invoke(databaseOperations, query, 100, 50) as String
        assertEquals("SELECT * FROM users LIMIT 100 OFFSET 50", result)
    }

    @Test
    fun `addLimitToQuery should preserve existing LIMIT clause`() {
        // Use reflection to access private method
        val method = DatabaseOperations::class.java.getDeclaredMethod(
            "addLimitToQuery",
            String::class.java,
            Int::class.java,
            Int::class.java
        )
        method.isAccessible = true

        // Test preserving existing limit
        val queryWithLimit = "SELECT * FROM users LIMIT 50"
        val result = method.invoke(databaseOperations, queryWithLimit, 100, 0) as String
        assertEquals("SELECT * FROM users LIMIT 50", result)
    }

    @Test
    fun `listDatabaseFiles should handle non-existent directory`() {
        // Mock context to return a non-existent database path
        val mockDummyDbPath = mockk<java.io.File>()
        every { mockContext.getDatabasePath("dummy") } returns mockDummyDbPath
        every { mockDummyDbPath.parent } returns "/nonexistent/path"

        // Act
        val result = databaseOperations.listDatabaseFiles()

        // Assert
        assertTrue("Should return empty list for non-existent directory", result.isEmpty())
    }

    @Test
    fun `DatabaseConfig should have correct default values`() {
        // Test default configuration
        val config = DatabaseConfig(path = "/test/path")

        assertEquals("/test/path", config.path)
        assertNull(config.password)
        assertFalse(config.readOnly)
        assertTrue(config.enableWAL)
        assertTrue(config.enableForeignKeys)
        assertEquals(30_000L, config.queryTimeout)
        assertEquals(10_000L, config.busyTimeout)
    }

    @Test
    fun `DatabaseResult should have correct properties`() {
        // Test successful result
        val successResult = DatabaseResult(
            success = true,
            data = "test data",
            rowsAffected = 5,
            lastInsertId = 123L,
            executionTimeMs = 250L
        )

        assertTrue(successResult.success)
        assertEquals("test data", successResult.data)
        assertEquals(5, successResult.rowsAffected)
        assertEquals(123L, successResult.lastInsertId)
        assertEquals(250L, successResult.executionTimeMs)
        assertNull(successResult.error)

        // Test error result
        val errorResult = DatabaseResult<String>(
            success = false,
            error = "Database error occurred"
        )

        assertFalse(errorResult.success)
        assertNull(errorResult.data)
        assertEquals(0, errorResult.rowsAffected)
        assertNull(errorResult.lastInsertId)
        assertEquals(0L, errorResult.executionTimeMs)
        assertEquals("Database error occurred", errorResult.error)
    }

    @Test
    fun `QueryResult should have correct properties`() {
        val rows = listOf(
            mapOf("id" to 1, "name" to "John"),
            mapOf("id" to 2, "name" to "Jane")
        )

        val queryResult = QueryResult(
            rows = rows,
            columnNames = listOf("id", "name"),
            rowCount = 2,
            executionTimeMs = 150L,
            hasMore = true,
            nextPageToken = "token123"
        )

        assertEquals(rows, queryResult.rows)
        assertEquals(listOf("id", "name"), queryResult.columnNames)
        assertEquals(2, queryResult.rowCount)
        assertEquals(150L, queryResult.executionTimeMs)
        assertTrue(queryResult.hasMore)
        assertEquals("token123", queryResult.nextPageToken)
    }

    @Test
    fun `TableSchema should have correct structure`() {
        val columns = listOf(
            ColumnInfo(name = "id", type = "INTEGER", nullable = false, primaryKey = true),
            ColumnInfo(
                name = "name",
                type = "TEXT",
                nullable = false,
                primaryKey = false,
                defaultValue = "''"
            )
        )

        val indexes = listOf(
            IndexInfo(name = "idx_name", unique = true, columns = listOf("name"))
        )

        val foreignKeys = listOf(
            ForeignKeyInfo(column = "user_id", referencedTable = "users", referencedColumn = "id")
        )

        val tableSchema = TableSchema(
            name = "test_table",
            columns = columns,
            indexes = indexes,
            foreignKeys = foreignKeys
        )

        assertEquals("test_table", tableSchema.name)
        assertEquals(columns, tableSchema.columns)
        assertEquals(indexes, tableSchema.indexes)
        assertEquals(foreignKeys, tableSchema.foreignKeys)
    }

    @Test
    fun `ColumnInfo should have correct properties`() {
        // Test primary key column
        val primaryColumn = ColumnInfo(
            name = "id",
            type = "INTEGER",
            nullable = false,
            primaryKey = true
        )

        assertEquals("id", primaryColumn.name)
        assertEquals("INTEGER", primaryColumn.type)
        assertFalse(primaryColumn.nullable)
        assertTrue(primaryColumn.primaryKey)
        assertNull(primaryColumn.defaultValue)

        // Test column with default value
        val defaultColumn = ColumnInfo(
            name = "status",
            type = "TEXT",
            nullable = true,
            primaryKey = false,
            defaultValue = "'active'"
        )

        assertEquals("status", defaultColumn.name)
        assertEquals("TEXT", defaultColumn.type)
        assertTrue(defaultColumn.nullable)
        assertFalse(defaultColumn.primaryKey)
        assertEquals("'active'", defaultColumn.defaultValue)
    }

    @Test
    fun `IndexInfo should have correct properties`() {
        val uniqueIndex = IndexInfo(
            name = "unique_email_idx",
            unique = true,
            columns = listOf("email")
        )

        assertEquals("unique_email_idx", uniqueIndex.name)
        assertTrue(uniqueIndex.unique)
        assertEquals(listOf("email"), uniqueIndex.columns)

        val compositeIndex = IndexInfo(
            name = "composite_idx",
            unique = false,
            columns = listOf("last_name", "first_name")
        )

        assertEquals("composite_idx", compositeIndex.name)
        assertFalse(compositeIndex.unique)
        assertEquals(listOf("last_name", "first_name"), compositeIndex.columns)
    }

    @Test
    fun `ForeignKeyInfo should have correct properties`() {
        val foreignKey = ForeignKeyInfo(
            column = "user_id",
            referencedTable = "users",
            referencedColumn = "id"
        )

        assertEquals("user_id", foreignKey.column)
        assertEquals("users", foreignKey.referencedTable)
        assertEquals("id", foreignKey.referencedColumn)
    }

    @Test
    fun `DatabaseMetadata should have correct structure`() {
        val tables = listOf(
            TableSchema(name = "users", columns = emptyList()),
            TableSchema(name = "products", columns = emptyList())
        )

        val metadata = DatabaseMetadata(
            path = "/test/database.db",
            version = 1,
            tables = tables,
            views = listOf("user_view"),
            triggers = listOf("update_trigger")
        )

        assertEquals("/test/database.db", metadata.path)
        assertEquals(1, metadata.version)
        assertEquals(tables, metadata.tables)
        assertEquals(listOf("user_view"), metadata.views)
        assertEquals(listOf("update_trigger"), metadata.triggers)
    }
}
