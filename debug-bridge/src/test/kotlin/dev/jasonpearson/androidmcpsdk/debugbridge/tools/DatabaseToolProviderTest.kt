package dev.jasonpearson.androidmcpsdk.debugbridge.tools

import android.content.Context
import dev.jasonpearson.androidmcpsdk.core.features.tools.McpToolProvider
import dev.jasonpearson.androidmcpsdk.debugbridge.database.*
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class DatabaseToolProviderTest {

    private lateinit var mockContext: Context
    private lateinit var databaseToolProvider: DatabaseToolProvider

    @Before
    fun setUp() {
        mockContext = mockk()
        databaseToolProvider = DatabaseToolProvider(mockContext)
    }

    @After
    fun tearDown() {
        // No need for unmockkAll since we're not using mocks for most tests
    }

    @Test
    fun `DatabaseQueryInput should have correct default values`() {
        // Act
        val input = DatabaseToolProvider.DatabaseQueryInput(
            databasePath = "/test/path",
            query = "SELECT * FROM test"
        )

        // Assert
        assertEquals("/test/path", input.databasePath)
        assertEquals("SELECT * FROM test", input.query)
        assertEquals(emptyList<String>(), input.parameters)
        assertEquals(100, input.pageSize)
        assertEquals(0, input.pageOffset)
        assertEquals("json", input.outputFormat)
    }

    @Test
    fun `DatabaseInsertInput should have correct default values`() {
        // Act
        val input = DatabaseToolProvider.DatabaseInsertInput(
            databasePath = "/test/path",
            tableName = "test_table",
            data = mapOf("name" to "test")
        )

        // Assert
        assertEquals("/test/path", input.databasePath)
        assertEquals("test_table", input.tableName)
        assertEquals(mapOf("name" to "test"), input.data)
        assertFalse(input.dryRun)
    }

    @Test
    fun `DatabaseUpdateInput should have correct default values`() {
        // Act
        val input = DatabaseToolProvider.DatabaseUpdateInput(
            databasePath = "/test/path",
            tableName = "test_table",
            data = mapOf("name" to "updated"),
            whereClause = "id = ?"
        )

        // Assert
        assertEquals("/test/path", input.databasePath)
        assertEquals("test_table", input.tableName)
        assertEquals(mapOf("name" to "updated"), input.data)
        assertEquals("id = ?", input.whereClause)
        assertEquals(emptyList<String>(), input.whereArgs)
        assertFalse(input.dryRun)
    }

    @Test
    fun `DatabaseDeleteInput should have correct default values`() {
        // Act
        val input = DatabaseToolProvider.DatabaseDeleteInput(
            databasePath = "/test/path",
            tableName = "test_table",
            whereClause = "id = ?"
        )

        // Assert
        assertEquals("/test/path", input.databasePath)
        assertEquals("test_table", input.tableName)
        assertEquals("id = ?", input.whereClause)
        assertEquals(emptyList<String>(), input.whereArgs)
        assertFalse(input.confirm)
    }

    @Test
    fun `DatabaseSchemaInput should have correct default values`() {
        // Act
        val input = DatabaseToolProvider.DatabaseSchemaInput(
            databasePath = "/test/path"
        )

        // Assert
        assertEquals("/test/path", input.databasePath)
        assertNull(input.tableName)
    }

    @Test
    fun `convertStringDataToTypes should convert null string to null`() {
        // Arrange
        val data = mapOf("nullable_field" to "null")

        // Act
        val result = databaseToolProvider.convertStringDataToTypes(data)

        // Assert
        assertNull(result["nullable_field"])
    }

    @Test
    fun `convertStringDataToTypes should convert boolean strings`() {
        // Arrange
        val data = mapOf(
            "true_field" to "true",
            "false_field" to "false",
            "True_field" to "True",
            "FALSE_field" to "FALSE"
        )

        // Act
        val result = databaseToolProvider.convertStringDataToTypes(data)

        // Assert
        assertEquals(true, result["true_field"])
        assertEquals(false, result["false_field"])
        assertEquals(true, result["True_field"])
        assertEquals(false, result["FALSE_field"])
    }

    @Test
    fun `convertStringDataToTypes should convert numeric strings`() {
        // Arrange
        val data = mapOf(
            "integer_field" to "42",
            "negative_int" to "-123",
            "double_field" to "3.14159",
            "negative_double" to "-2.71"
        )

        // Act
        val result = databaseToolProvider.convertStringDataToTypes(data)

        // Assert
        assertEquals(42L, result["integer_field"])
        assertEquals(-123L, result["negative_int"])
        assertEquals(3.14159, result["double_field"])
        assertEquals(-2.71, result["negative_double"])
    }

    @Test
    fun `convertStringDataToTypes should preserve string values when not convertible`() {
        // Arrange
        val data = mapOf(
            "string_field" to "hello world",
            "mixed_field" to "123abc",
            "empty_field" to "",
            "space_field" to "   "
        )

        // Act
        val result = databaseToolProvider.convertStringDataToTypes(data)

        // Assert
        assertEquals("hello world", result["string_field"])
        assertEquals("123abc", result["mixed_field"])
        assertEquals("", result["empty_field"])
        assertEquals("   ", result["space_field"])
    }

    @Test
    fun `formatAsJson should handle empty result set`() {
        // Arrange
        val queryResult = QueryResult(
            rows = emptyList(),
            columnNames = listOf("id", "name"),
            rowCount = 0,
            executionTimeMs = 5L
        )

        // Act
        val result = databaseToolProvider.formatAsJson(queryResult)

        // Assert
        assertEquals("[]", result)
    }

    @Test
    fun `formatAsCsv should include headers and data`() {
        // Arrange
        val queryResult = QueryResult(
            rows = listOf(
                mapOf("id" to 1, "name" to "John", "email" to "john@example.com"),
                mapOf("id" to 2, "name" to "Jane", "email" to "jane@example.com")
            ),
            columnNames = listOf("id", "name", "email"),
            rowCount = 2,
            executionTimeMs = 10L
        )

        // Act
        val result = databaseToolProvider.formatAsCsv(queryResult)

        // Assert
        val lines = result.split("\n").filter { it.isNotEmpty() }
        assertEquals(3, lines.size) // header + 2 data rows
        assertEquals("id,name,email", lines[0])
        assertTrue(lines[1].contains("John"))
        assertTrue(lines[2].contains("Jane"))
    }

    @Test
    fun `formatAsCsv should handle commas and quotes in data`() {
        // Arrange
        val queryResult = QueryResult(
            rows = listOf(
                mapOf("id" to 1, "name" to "John, Jr.", "notes" to "He said \"Hello\"")
            ),
            columnNames = listOf("id", "name", "notes"),
            rowCount = 1,
            executionTimeMs = 5L
        )

        // Act
        val result = databaseToolProvider.formatAsCsv(queryResult)

        // Assert
        val lines = result.split("\n").filter { it.isNotEmpty() }
        assertEquals(2, lines.size)
        assertTrue(lines[1].contains("\"John, Jr.\""))
        assertTrue(lines[1].contains("\"He said \"\"Hello\"\"\""))
    }

    @Test
    fun `formatAsTable should create proper table layout`() {
        // Arrange
        val queryResult = QueryResult(
            rows = listOf(
                mapOf("id" to 1, "name" to "John"),
                mapOf("id" to 2, "name" to "Jane")
            ),
            columnNames = listOf("id", "name"),
            rowCount = 2,
            executionTimeMs = 8L
        )

        // Act
        val result = databaseToolProvider.formatAsTable(queryResult)

        // Assert
        val lines = result.split("\n").filter { it.isNotEmpty() }
        assertEquals(4, lines.size) // header + separator + 2 data rows
        assertTrue(lines[0].contains("id"))
        assertTrue(lines[0].contains("name"))
        assertTrue(lines[1].contains("-"))
        assertTrue(lines[2].contains("John"))
        assertTrue(lines[3].contains("Jane"))
    }

    @Test
    fun `formatAsTable should handle empty result`() {
        // Arrange
        val queryResult = QueryResult(
            rows = emptyList(),
            columnNames = listOf("id", "name"),
            rowCount = 0,
            executionTimeMs = 1L
        )

        // Act
        val result = databaseToolProvider.formatAsTable(queryResult)

        // Assert
        assertEquals("No data returned", result)
    }

    @Test
    fun `EmptyInput should be serializable`() {
        // Act
        val input = DatabaseToolProvider.EmptyInput()

        // Assert
        assertNull(input.placeholder)
    }

    @Test
    fun `EmptyInput should accept placeholder`() {
        // Act
        val input = DatabaseToolProvider.EmptyInput(placeholder = "test")

        // Assert
        assertEquals("test", input.placeholder)
    }

    // Helper method to access private method for testing
    private fun DatabaseToolProvider.convertStringDataToTypes(data: Map<String, String>): Map<String, Any?> {
        // Use reflection to access private method
        val method = this::class.java.getDeclaredMethod("convertStringDataToTypes", Map::class.java)
        method.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return method.invoke(this, data) as Map<String, Any?>
    }

    private fun DatabaseToolProvider.formatAsJson(queryResult: QueryResult): String {
        val method = this::class.java.getDeclaredMethod("formatAsJson", QueryResult::class.java)
        method.isAccessible = true
        return method.invoke(this, queryResult) as String
    }

    private fun DatabaseToolProvider.formatAsCsv(queryResult: QueryResult): String {
        val method = this::class.java.getDeclaredMethod("formatAsCsv", QueryResult::class.java)
        method.isAccessible = true
        return method.invoke(this, queryResult) as String
    }

    private fun DatabaseToolProvider.formatAsTable(queryResult: QueryResult): String {
        val method = this::class.java.getDeclaredMethod("formatAsTable", QueryResult::class.java)
        method.isAccessible = true
        return method.invoke(this, queryResult) as String
    }
}
