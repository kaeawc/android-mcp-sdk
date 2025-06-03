package dev.jasonpearson.androidmcpsdk.debugbridge.database

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.util.Log
import java.io.File
import kotlin.system.measureTimeMillis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.put

/** Main database operations engine for querying and editing databases. */
class DatabaseOperations(
    private val context: Context,
    private val databaseFactory: SqliteDatabaseFactory = StandardSqliteDatabaseFactory(),
) {

    companion object {
        private const val TAG = "DatabaseOperations"
        private const val MAX_QUERY_ROWS = 1000
        private const val DEFAULT_PAGE_SIZE = 100
    }

    /** Execute a SQL query and return results in JSON format. */
    suspend fun executeQuery(
        databasePath: String,
        query: String,
        parameters: Array<String> = emptyArray(),
        pageSize: Int = DEFAULT_PAGE_SIZE,
        pageOffset: Int = 0,
    ): DatabaseResult<QueryResult> =
        withContext(Dispatchers.IO) {
            try {
                if (!validateQuerySafety(query)) {
                    return@withContext DatabaseResult(
                        success = false,
                        error = "Query contains potentially unsafe operations",
                    )
                }

                val config = DatabaseConfig(path = databasePath, readOnly = true)

                val helper = DatabaseHelper(config, databaseFactory)
                val database = helper.openDatabase()

                var queryResult: QueryResult? = null
                val executionTime = measureTimeMillis {
                    database.use { db ->
                        val limitedQuery = addLimitToQuery(query, pageSize, pageOffset)
                        val cursor = db.rawQuery(limitedQuery, parameters)
                        cursor.use { c -> queryResult = processCursorToQueryResult(c, 0L) }
                    }
                }

                DatabaseResult(
                    success = true,
                    data = queryResult!!.copy(executionTimeMs = executionTime),
                    executionTimeMs = executionTime,
                )
            } catch (e: Exception) {
                Log.e(TAG, "Query execution failed", e)
                DatabaseResult(
                    success = false,
                    error = "Query failed: ${e.message}",
                    executionTimeMs = 0,
                )
            }
        }

    /** Insert a new record into a database table. */
    suspend fun insertRecord(
        databasePath: String,
        tableName: String,
        data: Map<String, Any?>,
    ): DatabaseResult<Long> =
        withContext(Dispatchers.IO) {
            try {
                val config = DatabaseConfig(path = databasePath, readOnly = false)

                val helper = DatabaseHelper(config, databaseFactory)
                val database = helper.openDatabase()

                var insertId = -1L
                val executionTime = measureTimeMillis {
                    database.use { db ->
                        db.beginTransaction()

                        try {
                            val values =
                                ContentValues().apply {
                                    data.forEach { (key, value) ->
                                        when (value) {
                                            is String -> put(key, value)
                                            is Int -> put(key, value)
                                            is Long -> put(key, value)
                                            is Double -> put(key, value)
                                            is Float -> put(key, value)
                                            is Boolean -> put(key, if (value) 1 else 0)
                                            is ByteArray -> put(key, value)
                                            null -> putNull(key)
                                            else -> put(key, value.toString())
                                        }
                                    }
                                }

                            insertId = db.insert(tableName, null, values)

                            if (insertId != -1L) {
                                db.setTransactionSuccessful()
                            }
                        } finally {
                            db.endTransaction()
                        }
                    }
                }

                if (insertId != -1L) {
                    DatabaseResult(
                        success = true,
                        data = insertId,
                        rowsAffected = 1,
                        lastInsertId = insertId,
                        executionTimeMs = executionTime,
                    )
                } else {
                    DatabaseResult(
                        success = false,
                        error = "Insert failed - no rows affected",
                        executionTimeMs = executionTime,
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Insert operation failed", e)
                DatabaseResult(success = false, error = "Insert failed: ${e.message}")
            }
        }

    /** Update existing records in a database table. */
    suspend fun updateRecords(
        databasePath: String,
        tableName: String,
        data: Map<String, Any?>,
        whereClause: String,
        whereArgs: Array<String>,
    ): DatabaseResult<Int> =
        withContext(Dispatchers.IO) {
            try {
                val config = DatabaseConfig(path = databasePath, readOnly = false)

                val helper = DatabaseHelper(config, databaseFactory)
                val database = helper.openDatabase()

                var rowsUpdated = 0
                val executionTime = measureTimeMillis {
                    database.use { db ->
                        db.beginTransaction()

                        try {
                            val values =
                                ContentValues().apply {
                                    data.forEach { (key, value) ->
                                        when (value) {
                                            is String -> put(key, value)
                                            is Int -> put(key, value)
                                            is Long -> put(key, value)
                                            is Double -> put(key, value)
                                            is Float -> put(key, value)
                                            is Boolean -> put(key, if (value) 1 else 0)
                                            is ByteArray -> put(key, value)
                                            null -> putNull(key)
                                            else -> put(key, value.toString())
                                        }
                                    }
                                }

                            rowsUpdated = db.update(tableName, values, whereClause, whereArgs)
                            db.setTransactionSuccessful()
                        } finally {
                            db.endTransaction()
                        }
                    }
                }

                if (rowsUpdated > 0) {
                    DatabaseResult(
                        success = true,
                        data = rowsUpdated,
                        rowsAffected = rowsUpdated,
                        executionTimeMs = executionTime,
                    )
                } else {
                    DatabaseResult(
                        success = false,
                        error = "Update failed - no rows affected",
                        executionTimeMs = executionTime,
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Update operation failed", e)
                DatabaseResult(success = false, error = "Update failed: ${e.message}")
            }
        }

    /** Delete records from a database table. */
    suspend fun deleteRecords(
        databasePath: String,
        tableName: String,
        whereClause: String,
        whereArgs: Array<String>,
    ): DatabaseResult<Int> =
        withContext(Dispatchers.IO) {
            try {
                val config = DatabaseConfig(path = databasePath, readOnly = false)

                val helper = DatabaseHelper(config, databaseFactory)
                val database = helper.openDatabase()

                var rowsDeleted = 0
                val executionTime = measureTimeMillis {
                    database.use { db ->
                        db.beginTransaction()

                        try {
                            rowsDeleted = db.delete(tableName, whereClause, whereArgs)
                            db.setTransactionSuccessful()
                        } finally {
                            db.endTransaction()
                        }
                    }
                }

                if (rowsDeleted > 0) {
                    DatabaseResult(
                        success = true,
                        data = rowsDeleted,
                        rowsAffected = rowsDeleted,
                        executionTimeMs = executionTime,
                    )
                } else {
                    DatabaseResult(
                        success = false,
                        error = "Delete failed - no rows affected",
                        executionTimeMs = executionTime,
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Delete operation failed", e)
                DatabaseResult(success = false, error = "Delete failed: ${e.message}")
            }
        }

    /** Get database schema information. */
    suspend fun getDatabaseSchema(databasePath: String): DatabaseResult<DatabaseMetadata> =
        withContext(Dispatchers.IO) {
            try {
                val config = DatabaseConfig(path = databasePath, readOnly = true)

                val helper = DatabaseHelper(config, databaseFactory)
                val database = helper.openDatabase()

                database.use { db ->
                    val version = db.getVersion()
                    val tables = getTableSchemas(db)
                    val views = getViews(db)
                    val triggers = getTriggers(db)

                    val metadata =
                        DatabaseMetadata(
                            path = databasePath,
                            version = version,
                            tables = tables,
                            views = views,
                            triggers = triggers,
                        )

                    DatabaseResult(success = true, data = metadata)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Schema retrieval failed", e)
                DatabaseResult(success = false, error = "Schema retrieval failed: ${e.message}")
            }
        }

    /** List available database files in the app's database directory. */
    fun listDatabaseFiles(): List<String> {
        val dbDir = File(context.getDatabasePath("dummy").parent!!)
        return if (dbDir.exists() && dbDir.isDirectory) {
            dbDir
                .listFiles { file ->
                    file.isFile &&
                        (file.extension == "db" ||
                            file.extension == "sqlite" ||
                            file.extension == "sqlite3")
                }
                ?.map { it.absolutePath } ?: emptyList()
        } else {
            emptyList()
        }
    }

    private fun validateQuerySafety(query: String): Boolean {
        val trimmedQuery = query.trim().uppercase()

        // Allow only SELECT statements for queries
        if (!trimmedQuery.startsWith("SELECT")) {
            return false
        }

        // Prevent potentially dangerous keywords
        val dangerousKeywords =
            listOf("DROP", "DELETE", "UPDATE", "INSERT", "ALTER", "CREATE", "PRAGMA")
        return dangerousKeywords.none { keyword -> trimmedQuery.contains(keyword) }
    }

    private fun addLimitToQuery(query: String, pageSize: Int, pageOffset: Int): String {
        val trimmedQuery = query.trim()
        val limitClause = "LIMIT $pageSize OFFSET $pageOffset"

        return if (trimmedQuery.uppercase().contains("LIMIT")) {
            // Query already has LIMIT, don't modify
            trimmedQuery
        } else {
            "$trimmedQuery $limitClause"
        }
    }

    private fun processCursorToQueryResult(cursor: Cursor, executionTime: Long): QueryResult {
        val rows = mutableListOf<Map<String, Any?>>()
        val columnNames = cursor.columnNames.toList()

        while (cursor.moveToNext() && rows.size < MAX_QUERY_ROWS) {
            val row = mutableMapOf<String, Any?>()

            for (i in 0 until cursor.columnCount) {
                val columnName = cursor.getColumnName(i)
                val value =
                    when (cursor.getType(i)) {
                        Cursor.FIELD_TYPE_NULL -> null
                        Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(i)
                        Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(i)
                        Cursor.FIELD_TYPE_STRING -> cursor.getString(i)
                        Cursor.FIELD_TYPE_BLOB -> cursor.getBlob(i)
                        else -> cursor.getString(i)
                    }
                row[columnName] = value
            }

            rows.add(row)
        }

        return QueryResult(
            rows = rows,
            columnNames = columnNames,
            rowCount = rows.size,
            executionTimeMs = executionTime,
            hasMore = cursor.moveToNext(), // Check if there are more rows
        )
    }

    private fun getTableSchemas(database: SqliteDatabase): List<TableSchema> {
        val tables = mutableListOf<TableSchema>()

        val cursor =
            database.rawQuery(
                "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'",
                null,
            )

        cursor.use { c ->
            while (c.moveToNext()) {
                val tableName = c.getString(0)
                val columns = getTableColumns(database, tableName)
                val indexes = getTableIndexes(database, tableName)
                val foreignKeys = getTableForeignKeys(database, tableName)

                tables.add(
                    TableSchema(
                        name = tableName,
                        columns = columns,
                        indexes = indexes,
                        foreignKeys = foreignKeys,
                    )
                )
            }
        }

        return tables
    }

    private fun getTableColumns(database: SqliteDatabase, tableName: String): List<ColumnInfo> {
        val columns = mutableListOf<ColumnInfo>()

        val cursor = database.rawQuery("PRAGMA table_info($tableName)", null)
        cursor.use { c ->
            while (c.moveToNext()) {
                val name = c.getString(1) // column name
                val type = c.getString(2) // data type
                val notNull = c.getInt(3) == 1 // not null constraint
                val defaultValue = c.getString(4) // default value
                val primaryKey = c.getInt(5) == 1 // primary key

                columns.add(
                    ColumnInfo(
                        name = name,
                        type = type,
                        nullable = !notNull,
                        primaryKey = primaryKey,
                        defaultValue = defaultValue,
                    )
                )
            }
        }

        return columns
    }

    private fun getTableIndexes(database: SqliteDatabase, tableName: String): List<IndexInfo> {
        val indexes = mutableListOf<IndexInfo>()

        val cursor = database.rawQuery("PRAGMA index_list($tableName)", null)
        cursor.use { c ->
            while (c.moveToNext()) {
                val indexName = c.getString(1)
                val unique = c.getInt(2) == 1

                val indexColumns = getIndexColumns(database, indexName)

                indexes.add(IndexInfo(name = indexName, unique = unique, columns = indexColumns))
            }
        }

        return indexes
    }

    private fun getIndexColumns(database: SqliteDatabase, indexName: String): List<String> {
        val columns = mutableListOf<String>()

        val cursor = database.rawQuery("PRAGMA index_info($indexName)", null)
        cursor.use { c ->
            while (c.moveToNext()) {
                val columnName = c.getString(2)
                columns.add(columnName)
            }
        }

        return columns
    }

    private fun getTableForeignKeys(
        database: SqliteDatabase,
        tableName: String,
    ): List<ForeignKeyInfo> {
        val foreignKeys = mutableListOf<ForeignKeyInfo>()

        val cursor = database.rawQuery("PRAGMA foreign_key_list($tableName)", null)
        cursor.use { c ->
            while (c.moveToNext()) {
                val table = c.getString(2) // referenced table
                val from = c.getString(3) // column name
                val to = c.getString(4) // referenced column

                foreignKeys.add(
                    ForeignKeyInfo(column = from, referencedTable = table, referencedColumn = to)
                )
            }
        }

        return foreignKeys
    }

    private fun getViews(database: SqliteDatabase): List<String> {
        val views = mutableListOf<String>()

        val cursor = database.rawQuery("SELECT name FROM sqlite_master WHERE type='view'", null)

        cursor.use { c ->
            while (c.moveToNext()) {
                views.add(c.getString(0))
            }
        }

        return views
    }

    private fun getTriggers(database: SqliteDatabase): List<String> {
        val triggers = mutableListOf<String>()

        val cursor = database.rawQuery("SELECT name FROM sqlite_master WHERE type='trigger'", null)

        cursor.use { c ->
            while (c.moveToNext()) {
                triggers.add(c.getString(0))
            }
        }

        return triggers
    }
}
