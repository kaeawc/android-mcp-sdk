package dev.jasonpearson.androidmcpsdk.debugbridge.tools

import android.content.Context
import dev.jasonpearson.androidmcpsdk.debugbridge.preferences.SharedPreferencesProvider
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class SharedPreferencesToolProviderTest {

    private lateinit var context: Context
    private lateinit var toolProvider: SharedPreferencesToolProvider

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        toolProvider = SharedPreferencesToolProvider(context)
    }

    @Test
    fun `PreferencesConfig data class should work correctly`() {
        val config = SharedPreferencesProvider.PreferencesConfig(
            fileName = "test",
            mode = Context.MODE_PRIVATE,
            readOnly = false,
            enableChangeNotifications = true
        )

        assertEquals("test", config.fileName)
        assertEquals(Context.MODE_PRIVATE, config.mode)
        assertFalse(config.readOnly)
        assertTrue(config.enableChangeNotifications)
    }

    @Test
    fun `PreferencesQueryInput data class should work correctly`() {
        val input = SharedPreferencesToolProvider.PreferencesQueryInput(
            fileName = "test_file",
            key = "test_key",
            outputFormat = "json"
        )

        assertEquals("test_file", input.fileName)
        assertEquals("test_key", input.key)
        assertEquals("json", input.outputFormat)
    }

    @Test
    fun `PreferencesSetInput data class should work correctly`() {
        val input = SharedPreferencesToolProvider.PreferencesSetInput(
            fileName = "test_file",
            key = "test_key",
            value = "test_value",
            type = "STRING",
            dryRun = false
        )

        assertEquals("test_file", input.fileName)
        assertEquals("test_key", input.key)
        assertEquals("test_value", input.value)
        assertEquals("STRING", input.type)
        assertFalse(input.dryRun)
    }

    @Test
    fun `BatchOperation data class should work correctly`() {
        val operation = SharedPreferencesToolProvider.BatchOperation(
            action = "SET",
            key = "test_key",
            value = "test_value",
            type = "STRING"
        )

        assertEquals("SET", operation.action)
        assertEquals("test_key", operation.key)
        assertEquals("test_value", operation.value)
        assertEquals("STRING", operation.type)
    }

    @Test
    fun `PreferencesBatchEditInput data class should work correctly`() {
        val operations = listOf(
            SharedPreferencesToolProvider.BatchOperation("SET", "key1", "value1", "STRING"),
            SharedPreferencesToolProvider.BatchOperation("REMOVE", "key2")
        )

        val input = SharedPreferencesToolProvider.PreferencesBatchEditInput(
            fileName = "test_file",
            operations = operations,
            dryRun = true
        )

        assertEquals("test_file", input.fileName)
        assertEquals(2, input.operations.size)
        assertTrue(input.dryRun)
    }
}
