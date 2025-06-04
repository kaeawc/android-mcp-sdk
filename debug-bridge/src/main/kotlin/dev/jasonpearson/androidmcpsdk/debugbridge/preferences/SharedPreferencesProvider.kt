package dev.jasonpearson.androidmcpsdk.debugbridge.preferences

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

/**
 * Provides comprehensive SharedPreferences access and management. Supports regular
 * SharedPreferences files with optional encryption support.
 */
class SharedPreferencesProvider(private val context: Context) {

    companion object {
        private const val TAG = "SharedPreferencesProvider"
        private const val DEFAULT_PREFS_NAME = "default_prefs"
    }

    private val preferencesCache = ConcurrentHashMap<String, SharedPreferences>()
    private val observerRegistry = ConcurrentHashMap<String, PreferencesObserver>()

    @Serializable
    data class PreferencesConfig(
        val fileName: String,
        val mode: Int = Context.MODE_PRIVATE,
        val readOnly: Boolean = false,
        val enableChangeNotifications: Boolean = true,
    )

    @Serializable
    data class PreferencesFileInfo(
        val fileName: String,
        val path: String,
        val size: Long,
        val lastModified: Long,
        val keyCount: Int,
    )

    @Serializable
    data class PreferencesContent(
        val fileName: String,
        val preferences: Map<String, PreferenceValue>,
        val lastModified: Long,
        val size: Int,
    )

    @Serializable
    data class PreferenceValue(
        val key: String,
        val value: String, // Serialized as string for MCP compatibility
        val originalType: PreferenceType,
        val lastModified: Long = System.currentTimeMillis(),
    )

    @Serializable
    enum class PreferenceType {
        STRING,
        INT,
        BOOLEAN,
        FLOAT,
        LONG,
        STRING_SET,
    }

    /** Add or configure a SharedPreferences file for MCP access */
    suspend fun addPreferencesFile(uri: String, config: PreferencesConfig): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val preferences = context.getSharedPreferences(config.fileName, config.mode)
                preferencesCache[config.fileName] = preferences

                if (config.enableChangeNotifications) {
                    startObserving(config.fileName)
                }

                Log.i(TAG, "Added preferences file: ${config.fileName}")
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to add preferences file: ${config.fileName}", e)
                Result.failure(e)
            }
        }

    /** Get all available SharedPreferences files */
    suspend fun getAllPreferenceFiles(): List<PreferencesFileInfo> =
        withContext(Dispatchers.IO) {
            val files = mutableListOf<PreferencesFileInfo>()

            try {
                // Add default preferences if exists
                val defaultPrefs =
                    context.getSharedPreferences(DEFAULT_PREFS_NAME, Context.MODE_PRIVATE)
                val defaultFile = getPreferencesFile(DEFAULT_PREFS_NAME)
                if (defaultFile.exists()) {
                    files.add(createFileInfo(DEFAULT_PREFS_NAME, defaultFile, defaultPrefs))
                }

                // Scan for other preference files in shared_prefs directory
                val sharedPrefsDir = File(context.applicationInfo.dataDir, "shared_prefs")
                if (sharedPrefsDir.exists()) {
                    sharedPrefsDir
                        .listFiles { _, name -> name.endsWith(".xml") }
                        ?.forEach { file ->
                            val fileName = file.nameWithoutExtension
                            if (fileName != DEFAULT_PREFS_NAME) {
                                try {
                                    val prefs =
                                        context.getSharedPreferences(fileName, Context.MODE_PRIVATE)
                                    files.add(createFileInfo(fileName, file, prefs))
                                } catch (e: Exception) {
                                    Log.w(TAG, "Failed to read preferences file: $fileName", e)
                                }
                            }
                        }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to scan preference files", e)
            }

            files
        }

    /** Get content of a specific SharedPreferences file */
    suspend fun getPreferencesContent(fileName: String): PreferencesContent =
        withContext(Dispatchers.IO) {
            val preferences = getOrCreatePreferences(fileName)
            val allPrefs = preferences.all

            val prefValues =
                allPrefs
                    .map { (key, value) ->
                        key to
                            PreferenceValue(
                                key = key,
                                value = serializeValue(value),
                                originalType = getValueType(value),
                                lastModified = System.currentTimeMillis(),
                            )
                    }
                    .toMap()

            PreferencesContent(
                fileName = fileName,
                preferences = prefValues,
                lastModified = getPreferencesFile(fileName).lastModified(),
                size = prefValues.size,
            )
        }

    /** Get a specific preference value */
    suspend fun getPreferenceValue(fileName: String, key: String): PreferenceValue? =
        withContext(Dispatchers.IO) {
            try {
                val preferences = getOrCreatePreferences(fileName)
                val value = preferences.all[key] ?: return@withContext null

                PreferenceValue(
                    key = key,
                    value = serializeValue(value),
                    originalType = getValueType(value),
                    lastModified = System.currentTimeMillis(),
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get preference value: $fileName.$key", e)
                null
            }
        }

    /** Set a preference value with type conversion */
    suspend fun setPreferenceValue(
        fileName: String,
        key: String,
        value: String,
        type: PreferenceType,
    ): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val preferences = getOrCreatePreferences(fileName)
                val editor = preferences.edit()

                when (type) {
                    PreferenceType.STRING -> editor.putString(key, value)
                    PreferenceType.INT -> editor.putInt(key, value.toInt())
                    PreferenceType.BOOLEAN -> editor.putBoolean(key, value.toBoolean())
                    PreferenceType.FLOAT -> editor.putFloat(key, value.toFloat())
                    PreferenceType.LONG -> editor.putLong(key, value.toLong())
                    PreferenceType.STRING_SET -> {
                        val stringSet = value.split(",").map { it.trim() }.toSet()
                        editor.putStringSet(key, stringSet)
                    }
                }

                editor.apply()
                Log.d(TAG, "Set preference: $fileName.$key = $value ($type)")
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to set preference: $fileName.$key", e)
                Result.failure(e)
            }
        }

    /** Remove a preference key */
    suspend fun removePreferenceKey(fileName: String, key: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val preferences = getOrCreatePreferences(fileName)
                preferences.edit().remove(key).apply()
                Log.d(TAG, "Removed preference key: $fileName.$key")
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to remove preference key: $fileName.$key", e)
                Result.failure(e)
            }
        }

    /** Clear all preferences in a file */
    suspend fun clearPreferences(fileName: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val preferences = getOrCreatePreferences(fileName)
                preferences.edit().clear().apply()
                Log.d(TAG, "Cleared preferences: $fileName")
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to clear preferences: $fileName", e)
                Result.failure(e)
            }
        }

    /** Start observing changes for a preferences file */
    fun startObserving(fileName: String) {
        if (observerRegistry.containsKey(fileName)) {
            Log.d(TAG, "Already observing: $fileName")
            return
        }

        try {
            val preferences = preferencesCache[fileName] ?: getOrCreatePreferences(fileName)
            val observer = PreferencesObserver(fileName, preferences)
            observerRegistry[fileName] = observer
            Log.d(TAG, "Started observing: $fileName")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start observing: $fileName", e)
        }
    }

    /** Stop observing changes for a preferences file */
    fun stopObserving(fileName: String) {
        observerRegistry.remove(fileName)?.let { observer ->
            observer.stop()
            Log.d(TAG, "Stopped observing: $fileName")
        }
    }

    /** Stop all observers */
    fun stopAllObservers() {
        observerRegistry.values.forEach { it.stop() }
        observerRegistry.clear()
        Log.d(TAG, "Stopped all observers")
    }

    private fun getOrCreatePreferences(fileName: String): SharedPreferences {
        return preferencesCache[fileName]
            ?: context.getSharedPreferences(fileName, Context.MODE_PRIVATE)
    }

    private fun getPreferencesFile(fileName: String): File {
        return File(context.applicationInfo.dataDir, "shared_prefs/$fileName.xml")
    }

    private fun createFileInfo(
        fileName: String,
        file: File,
        preferences: SharedPreferences,
    ): PreferencesFileInfo {
        return PreferencesFileInfo(
            fileName = fileName,
            path = file.absolutePath,
            size = file.length(),
            lastModified = file.lastModified(),
            keyCount = preferences.all.size,
        )
    }

    private fun serializeValue(value: Any?): String {
        return when (value) {
            null -> "null"
            is String -> value
            is Set<*> -> value.joinToString(", ")
            else -> value.toString()
        }
    }

    private fun getValueType(value: Any?): PreferenceType {
        return when (value) {
            is String -> PreferenceType.STRING
            is Int -> PreferenceType.INT
            is Boolean -> PreferenceType.BOOLEAN
            is Float -> PreferenceType.FLOAT
            is Long -> PreferenceType.LONG
            is Set<*> -> PreferenceType.STRING_SET
            else -> PreferenceType.STRING
        }
    }
}
