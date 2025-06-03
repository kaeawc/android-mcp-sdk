package dev.jasonpearson.androidmcpsdk.debugbridge.database

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.io.File

/** Standard Android SQLite database implementation. */
class StandardSqliteDatabase(private val database: SQLiteDatabase) : SqliteDatabase {

    override fun rawQuery(sql: String, selectionArgs: Array<String>?): Cursor {
        return database.rawQuery(sql, selectionArgs)
    }

    override fun insert(table: String, nullColumnHack: String?, values: ContentValues): Long {
        return database.insert(table, nullColumnHack, values)
    }

    override fun update(
        table: String,
        values: ContentValues,
        whereClause: String?,
        whereArgs: Array<String>?,
    ): Int {
        return database.update(table, values, whereClause, whereArgs)
    }

    override fun delete(table: String, whereClause: String?, whereArgs: Array<String>?): Int {
        return database.delete(table, whereClause, whereArgs)
    }

    override fun beginTransaction() {
        database.beginTransaction()
    }

    override fun setTransactionSuccessful() {
        database.setTransactionSuccessful()
    }

    override fun endTransaction() {
        database.endTransaction()
    }

    override fun inTransaction(): Boolean {
        return database.inTransaction()
    }

    override fun close() {
        database.close()
    }

    override fun isOpen(): Boolean {
        return database.isOpen
    }

    override fun getVersion(): Int {
        return database.version
    }

    override fun getPath(): String? {
        return database.path
    }

    override fun execSQL(sql: String) {
        database.execSQL(sql)
    }
}

/** Factory for creating standard SQLite database instances. */
class StandardSqliteDatabaseFactory : SqliteDatabaseFactory {

    override fun openOrCreateDatabase(path: String, password: String?): SqliteDatabase {
        if (password != null) {
            throw UnsupportedOperationException(
                "Standard SQLite does not support password encryption. Use SQLCipher factory instead."
            )
        }

        val database = SQLiteDatabase.openOrCreateDatabase(path, null)
        return StandardSqliteDatabase(database)
    }

    override fun openDatabase(path: String, flags: Int, password: String?): SqliteDatabase {
        if (password != null) {
            throw UnsupportedOperationException(
                "Standard SQLite does not support password encryption. Use SQLCipher factory instead."
            )
        }

        val database = SQLiteDatabase.openDatabase(path, null, flags)
        return StandardSqliteDatabase(database)
    }

    override fun databaseExists(path: String): Boolean {
        return File(path).exists()
    }

    override fun getFactoryName(): String = "StandardSQLite"
}

/** Helper class for creating database instances with proper configuration. */
class DatabaseHelper(
    private val config: DatabaseConfig,
    private val factory: SqliteDatabaseFactory = StandardSqliteDatabaseFactory(),
) : SQLiteOpenHelper(null, config.path, null, 1) {

    override fun onCreate(db: SQLiteDatabase?) {
        // Override in subclasses if needed
    }

    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        // Override in subclasses if needed
    }

    fun openDatabase(): SqliteDatabase {
        val flags =
            if (config.readOnly) {
                SQLiteDatabase.OPEN_READONLY
            } else {
                SQLiteDatabase.OPEN_READWRITE
            }

        val database = factory.openDatabase(config.path, flags, config.password)

        // Configure database settings
        if (config.enableForeignKeys) {
            database.execSQL("PRAGMA foreign_keys = ON")
        }

        if (config.enableWAL && !config.readOnly) {
            database.execSQL("PRAGMA journal_mode = WAL")
        }

        database.execSQL("PRAGMA busy_timeout = ${config.busyTimeout}")

        return database
    }

    fun createDatabase(): SqliteDatabase {
        val database = factory.openOrCreateDatabase(config.path, config.password)

        // Configure database settings
        if (config.enableForeignKeys) {
            database.execSQL("PRAGMA foreign_keys = ON")
        }

        if (config.enableWAL) {
            database.execSQL("PRAGMA journal_mode = WAL")
        }

        database.execSQL("PRAGMA busy_timeout = ${config.busyTimeout}")

        return database
    }
}
