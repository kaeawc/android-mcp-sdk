package dev.jasonpearson.androidmcpsdk.debugbridge.database

import android.content.ContentValues
import android.database.Cursor

/**
 * Interface for SQLite database operations. This abstraction allows swapping implementations (e.g.,
 * SQLCipher, custom SQLite variants).
 */
interface SqliteDatabase : java.io.Closeable {

    /**
     * Execute a single SQL statement that is NOT a SELECT or any other SQL statement that returns
     * data.
     */
    fun execSQL(sql: String)

    /** Execute a raw SQL query with optional parameters. */
    fun rawQuery(sql: String, selectionArgs: Array<String>? = null): Cursor

    /** Insert a new row into the specified table. */
    fun insert(table: String, nullColumnHack: String?, values: ContentValues): Long

    /** Update existing rows in the specified table. */
    fun update(
        table: String,
        values: ContentValues,
        whereClause: String?,
        whereArgs: Array<String>?,
    ): Int

    /** Delete rows from the specified table. */
    fun delete(table: String, whereClause: String?, whereArgs: Array<String>?): Int

    /** Begin a database transaction. */
    fun beginTransaction()

    /** Mark the current transaction as successful. */
    fun setTransactionSuccessful()

    /** End the current transaction. */
    fun endTransaction()

    /** Check if the database is currently in a transaction. */
    fun inTransaction(): Boolean

    /** Close the database connection. */
    override fun close()

    /** Check if the database is open. */
    fun isOpen(): Boolean

    /** Get the database version. */
    fun getVersion(): Int

    /** Get the database path. */
    fun getPath(): String?
}

/** Factory interface for creating SqliteDatabase instances. */
interface SqliteDatabaseFactory {

    /** Open or create a database. */
    fun openOrCreateDatabase(path: String, password: String? = null): SqliteDatabase

    /** Open a database in read-only mode. */
    fun openDatabase(path: String, flags: Int, password: String? = null): SqliteDatabase

    /** Check if a database file exists. */
    fun databaseExists(path: String): Boolean

    /** Get the name of this factory implementation. */
    fun getFactoryName(): String
}

/** Configuration for database operations. */
data class DatabaseConfig(
    val path: String,
    val password: String? = null,
    val readOnly: Boolean = false,
    val enableWAL: Boolean = true,
    val enableForeignKeys: Boolean = true,
    val queryTimeout: Long = 30_000L, // 30 seconds
    val busyTimeout: Long = 10_000L, // 10 seconds
)

/** Result of a database operation. */
data class DatabaseResult<T>(
    val success: Boolean,
    val data: T? = null,
    val rowsAffected: Int = 0,
    val lastInsertId: Long? = null,
    val executionTimeMs: Long = 0,
    val error: String? = null,
)

/** Query result with metadata. */
data class QueryResult(
    val rows: List<Map<String, Any?>>,
    val columnNames: List<String>,
    val rowCount: Int,
    val executionTimeMs: Long,
    val hasMore: Boolean = false,
    val nextPageToken: String? = null,
)

/** Database schema information. */
data class TableSchema(
    val name: String,
    val columns: List<ColumnInfo>,
    val indexes: List<IndexInfo> = emptyList(),
    val foreignKeys: List<ForeignKeyInfo> = emptyList(),
)

data class ColumnInfo(
    val name: String,
    val type: String,
    val nullable: Boolean,
    val primaryKey: Boolean,
    val defaultValue: String? = null,
)

data class IndexInfo(val name: String, val unique: Boolean, val columns: List<String>)

data class ForeignKeyInfo(
    val column: String,
    val referencedTable: String,
    val referencedColumn: String,
)

/** Database metadata. */
data class DatabaseMetadata(
    val path: String,
    val version: Int,
    val tables: List<TableSchema>,
    val views: List<String> = emptyList(),
    val triggers: List<String> = emptyList(),
)
