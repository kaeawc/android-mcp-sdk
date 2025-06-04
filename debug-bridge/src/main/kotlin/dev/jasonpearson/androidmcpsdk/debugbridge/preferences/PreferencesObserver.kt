package dev.jasonpearson.androidmcpsdk.debugbridge.preferences

import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/** Observes changes to SharedPreferences and emits notifications */
class PreferencesObserver(
    private val fileName: String,
    private val preferences: SharedPreferences,
) {
    companion object {
        private const val TAG = "PreferencesObserver"
    }

    private val _changes = Channel<PreferenceChange>(Channel.BUFFERED)
    val changes: Flow<PreferenceChange> = _changes.receiveAsFlow()

    private val listener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key != null) {
                val change =
                    PreferenceChange(
                        fileName = fileName,
                        key = key,
                        newValue = preferences.all[key],
                        timestamp = System.currentTimeMillis(),
                    )

                val sent = _changes.trySend(change).isSuccess
                if (!sent) {
                    Log.w(TAG, "Failed to send preference change notification for $fileName.$key")
                } else {
                    Log.d(TAG, "Preference changed: $fileName.$key")
                }
            }
        }

    init {
        preferences.registerOnSharedPreferenceChangeListener(listener)
        Log.d(TAG, "Started observing preferences: $fileName")
    }

    fun stop() {
        try {
            preferences.unregisterOnSharedPreferenceChangeListener(listener)
            _changes.close()
            Log.d(TAG, "Stopped observing preferences: $fileName")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping preferences observer: $fileName", e)
        }
    }

    data class PreferenceChange(
        val fileName: String,
        val key: String,
        val newValue: Any?, // This will be serialized as string when needed
        val timestamp: Long,
    )
}
