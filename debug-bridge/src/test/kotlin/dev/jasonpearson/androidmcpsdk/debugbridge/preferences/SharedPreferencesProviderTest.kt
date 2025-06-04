package dev.jasonpearson.androidmcpsdk.debugbridge.preferences

import android.content.Context
import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

@RunWith(RobolectricTestRunner::class)
class SharedPreferencesProviderTest {

    private lateinit var context: Context
    private lateinit var provider: SharedPreferencesProvider
    private lateinit var mockPrefs: SharedPreferences
    private lateinit var mockEditor: SharedPreferences.Editor

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        provider = SharedPreferencesProvider(context)
        
        mockPrefs = mockk(relaxed = true)
        mockEditor = mockk(relaxed = true)
        
        every { mockPrefs.edit() } returns mockEditor
        every { mockEditor.apply() } returns Unit
    }

    @Test
    fun `addPreferencesFile should add preferences successfully`() = runTest {
        val config = SharedPreferencesProvider.PreferencesConfig(
            fileName = "test_prefs",
            enableChangeNotifications = false
        )

        val result = provider.addPreferencesFile("android://preferences/test_prefs", config)

        assertTrue(result.isSuccess)
    }

    @Test
    fun `getPreferencesContent should return all preferences`() = runTest {
        // Create actual preferences for testing
        val prefs = context.getSharedPreferences("test_content", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("test_string", "hello")
            .putInt("test_int", 42)
            .putBoolean("test_bool", true)
            .apply()

        val content = provider.getPreferencesContent("test_content")

        assertEquals("test_content", content.fileName)
        assertEquals(3, content.size)
        assertTrue(content.preferences.containsKey("test_string"))
        assertTrue(content.preferences.containsKey("test_int"))
        assertTrue(content.preferences.containsKey("test_bool"))
        
        assertEquals("hello", content.preferences["test_string"]?.value)
        assertEquals("42", content.preferences["test_int"]?.value)
        assertEquals("true", content.preferences["test_bool"]?.value)
    }

    @Test
    fun `getPreferenceValue should return specific preference`() = runTest {
        val prefs = context.getSharedPreferences("test_single", Context.MODE_PRIVATE)
        prefs.edit().putString("test_key", "test_value").apply()

        val value = provider.getPreferenceValue("test_single", "test_key")

        assertNotNull(value)
        assertEquals("test_key", value!!.key)
        assertEquals("test_value", value.value)
        assertEquals(SharedPreferencesProvider.PreferenceType.STRING, value.originalType)
    }

    @Test
    fun `getPreferenceValue should return null for non-existent key`() = runTest {
        val value = provider.getPreferenceValue("non_existent", "missing_key")
        assertNull(value)
    }

    @Test
    fun `setPreferenceValue should set string value`() = runTest {
        val result = provider.setPreferenceValue(
            "test_set",
            "string_key",
            "string_value",
            SharedPreferencesProvider.PreferenceType.STRING
        )

        assertTrue(result.isSuccess)
        
        // Verify the value was set
        val prefs = context.getSharedPreferences("test_set", Context.MODE_PRIVATE)
        assertEquals("string_value", prefs.getString("string_key", null))
    }

    @Test
    fun `setPreferenceValue should set int value`() = runTest {
        val result = provider.setPreferenceValue(
            "test_set",
            "int_key",
            "123",
            SharedPreferencesProvider.PreferenceType.INT
        )

        assertTrue(result.isSuccess)
        
        val prefs = context.getSharedPreferences("test_set", Context.MODE_PRIVATE)
        assertEquals(123, prefs.getInt("int_key", 0))
    }

    @Test
    fun `setPreferenceValue should set boolean value`() = runTest {
        val result = provider.setPreferenceValue(
            "test_set",
            "bool_key",
            "true",
            SharedPreferencesProvider.PreferenceType.BOOLEAN
        )

        assertTrue(result.isSuccess)
        
        val prefs = context.getSharedPreferences("test_set", Context.MODE_PRIVATE)
        assertTrue(prefs.getBoolean("bool_key", false))
    }

    @Test
    fun `setPreferenceValue should set float value`() = runTest {
        val result = provider.setPreferenceValue(
            "test_set",
            "float_key",
            "3.14",
            SharedPreferencesProvider.PreferenceType.FLOAT
        )

        assertTrue(result.isSuccess)
        
        val prefs = context.getSharedPreferences("test_set", Context.MODE_PRIVATE)
        assertEquals(3.14f, prefs.getFloat("float_key", 0f))
    }

    @Test
    fun `setPreferenceValue should set long value`() = runTest {
        val result = provider.setPreferenceValue(
            "test_set",
            "long_key",
            "9876543210",
            SharedPreferencesProvider.PreferenceType.LONG
        )

        assertTrue(result.isSuccess)
        
        val prefs = context.getSharedPreferences("test_set", Context.MODE_PRIVATE)
        assertEquals(9876543210L, prefs.getLong("long_key", 0L))
    }

    @Test
    fun `setPreferenceValue should set string set value`() = runTest {
        val result = provider.setPreferenceValue(
            "test_set",
            "stringset_key",
            "value1, value2, value3",
            SharedPreferencesProvider.PreferenceType.STRING_SET
        )

        assertTrue(result.isSuccess)
        
        val prefs = context.getSharedPreferences("test_set", Context.MODE_PRIVATE)
        val stringSet = prefs.getStringSet("stringset_key", emptySet())
        assertNotNull(stringSet)
        assertEquals(3, stringSet!!.size)
        assertTrue(stringSet.contains("value1"))
        assertTrue(stringSet.contains("value2"))
        assertTrue(stringSet.contains("value3"))
    }

    @Test
    fun `removePreferenceKey should remove existing key`() = runTest {
        val prefs = context.getSharedPreferences("test_remove", Context.MODE_PRIVATE)
        prefs.edit().putString("key_to_remove", "value").apply()

        val result = provider.removePreferenceKey("test_remove", "key_to_remove")

        assertTrue(result.isSuccess)
        assertNull(prefs.getString("key_to_remove", null))
    }

    @Test
    fun `clearPreferences should clear all preferences`() = runTest {
        val prefs = context.getSharedPreferences("test_clear", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("key1", "value1")
            .putString("key2", "value2")
            .apply()

        val result = provider.clearPreferences("test_clear")

        assertTrue(result.isSuccess)
        assertTrue(prefs.all.isEmpty())
    }

    @Test
    fun `getAllPreferenceFiles should return existing files`() = runTest {
        // Create some preferences to ensure files exist
        context.getSharedPreferences("file1", Context.MODE_PRIVATE)
            .edit().putString("key", "value").apply()
        context.getSharedPreferences("file2", Context.MODE_PRIVATE)
            .edit().putInt("count", 42).apply()

        val files = provider.getAllPreferenceFiles()

        assertTrue(files.isNotEmpty())
        val fileNames = files.map { it.fileName }
        assertTrue(fileNames.any { it.contains("file1") || it.contains("file2") })
    }

    @Test
    fun `serializeValue should handle different types correctly`() = runTest {
        val prefs = context.getSharedPreferences("test_serialize", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("string", "hello")
            .putInt("int", 42)
            .putBoolean("boolean", true)
            .putFloat("float", 3.14f)
            .putLong("long", 123456789L)
            .putStringSet("stringset", setOf("a", "b", "c"))
            .apply()

        val content = provider.getPreferencesContent("test_serialize")

        assertEquals("hello", content.preferences["string"]?.value)
        assertEquals("42", content.preferences["int"]?.value)
        assertEquals("true", content.preferences["boolean"]?.value)
        assertEquals("3.14", content.preferences["float"]?.value)
        assertEquals("123456789", content.preferences["long"]?.value)
        
        val stringSetValue = content.preferences["stringset"]?.value
        assertNotNull(stringSetValue)
        assertTrue(stringSetValue!!.contains("a"))
        assertTrue(stringSetValue.contains("b"))
        assertTrue(stringSetValue.contains("c"))
    }

    @Test
    fun `getValueType should detect types correctly`() = runTest {
        val prefs = context.getSharedPreferences("test_types", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("string", "hello")
            .putInt("int", 42)
            .putBoolean("boolean", true)
            .putFloat("float", 3.14f)
            .putLong("long", 123456789L)
            .putStringSet("stringset", setOf("a", "b"))
            .apply()

        val content = provider.getPreferencesContent("test_types")

        assertEquals(SharedPreferencesProvider.PreferenceType.STRING, 
                    content.preferences["string"]?.originalType)
        assertEquals(SharedPreferencesProvider.PreferenceType.INT, 
                    content.preferences["int"]?.originalType)
        assertEquals(SharedPreferencesProvider.PreferenceType.BOOLEAN, 
                    content.preferences["boolean"]?.originalType)
        assertEquals(SharedPreferencesProvider.PreferenceType.FLOAT, 
                    content.preferences["float"]?.originalType)
        assertEquals(SharedPreferencesProvider.PreferenceType.LONG, 
                    content.preferences["long"]?.originalType)
        assertEquals(SharedPreferencesProvider.PreferenceType.STRING_SET, 
                    content.preferences["stringset"]?.originalType)
    }

    @Test
    fun `invalid type conversion should fail gracefully`() = runTest {
        val result = provider.setPreferenceValue(
            "test_invalid",
            "invalid_int",
            "not_a_number",
            SharedPreferencesProvider.PreferenceType.INT
        )

        assertTrue(result.isFailure)
    }
}
