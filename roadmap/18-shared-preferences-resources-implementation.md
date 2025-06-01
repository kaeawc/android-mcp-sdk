# Task 18: SharedPreferences Resources Implementation

## Status: `[ ]` Not Started

## Objective

Implement comprehensive SharedPreferences access as MCP resources, enabling secure reading and
writing of application preferences with proper validation, encryption support, and real-time change
notifications.

## Requirements

### Technical Requirements

- **Multi-Preference File Support**: Access to all app SharedPreferences files
- **Type-Safe Operations**: Handle all preference data types (String, Int, Boolean, Float, Long,
  StringSet)
- **Real-time Updates**: Live synchronization for preference changes
- **Encryption Support**: Encrypted SharedPreferences integration
- **Backup and Restore**: Preference export/import functionality
- **Schema Validation**: Preference key/value validation and constraints
- **Access Control**: Granular permissions per preference file/key

### Security Requirements

- **Sensitive Data Protection**: Automatic encryption for sensitive preferences
- **Access Validation**: Permission checks for preference file access
- **Audit Logging**: Track all preference access and modifications
- **Data Sanitization**: PII detection and masking in preference values

## Implementation Steps

### Step 1: SharedPreferences Resource Provider

Create
`lib/src/main/kotlin/dev/jasonpearson/mcpandroidsdk/preferences/SharedPreferencesResourceProvider.kt`:

```kotlin
class SharedPreferencesResourceProvider(
    private val context: Context
) {
    
    data class PreferencesConfig(
        val fileName: String,
        val mode: Int = Context.MODE_PRIVATE,
        val encrypted: Boolean = false,
        val allowedKeys: Set<String> = emptySet(),
        val readOnly: Boolean = false,
        val enableChangeNotifications: Boolean = true
    )
    
    suspend fun addPreferencesFile(
        uri: String,
        config: PreferencesConfig
    ): Result<Unit>
    
    suspend fun getAllPreferenceFiles(): List<PreferencesFileInfo>
    suspend fun getPreferencesContent(fileName: String): PreferencesContent
    suspend fun getPreferenceValue(fileName: String, key: String): PreferenceValue?
    
    data class PreferencesContent(
        val fileName: String,
        val preferences: Map<String, PreferenceValue>,
        val lastModified: Long,
        val encrypted: Boolean,
        val size: Int
    )
    
    data class PreferenceValue(
        val key: String,
        val value: Any,
        val type: PreferenceType,
        val encrypted: Boolean = false,
        val lastModified: Long = System.currentTimeMillis()
    )
    
    enum class PreferenceType {
        STRING, INT, BOOLEAN, FLOAT, LONG, STRING_SET
    }
}
```

### Step 2: Encrypted SharedPreferences Support

Create
`lib/src/main/kotlin/dev/jasonpearson/mcpandroidsdk/preferences/EncryptedPreferencesProvider.kt`:

```kotlin
class EncryptedPreferencesProvider(
    private val context: Context
) {
    
    suspend fun createEncryptedPreferences(
        fileName: String,
        keyAlias: String = "mcp_preferences_key"
    ): SharedPreferences {
        val keyGenParameterSpec = MasterKey.Builder(context, keyAlias)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        
        return EncryptedSharedPreferences.create(
            context,
            fileName,
            keyGenParameterSpec,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
    
    suspend fun migrateToEncrypted(
        originalFileName: String,
        encryptedFileName: String
    ): Result<Unit>
    
    suspend fun decryptPreferences(
        encryptedPrefs: SharedPreferences
    ): Map<String, Any>
}
```

### Step 3: Built-in Preferences Resources

Add to `ResourceProvider.kt`:

```kotlin
private suspend fun createSharedPreferencesResources(): List<Resource> {
    return listOf(
        Resource(
            uri = "android://preferences/default",
            name = "Default SharedPreferences",
            description = "Application's default SharedPreferences file",
            mimeType = "application/json"
        ),
        
        Resource(
            uri = "android://preferences/all",
            name = "All SharedPreferences Files",
            description = "List of all SharedPreferences files in the application",
            mimeType = "application/json"
        ),
        
        Resource(
            uri = "android://preferences/encrypted",
            name = "Encrypted SharedPreferences",
            description = "Encrypted SharedPreferences files (if any)",
            mimeType = "application/json"
        )
    )
}
```

### Step 4: Real-time Change Notifications

Create `lib/src/main/kotlin/dev/jasonpearson/mcpandroidsdk/preferences/PreferencesObserver.kt`:

```kotlin
class PreferencesObserver(
    private val context: Context
) {
    
    suspend fun observePreferences(
        fileName: String
    ): Flow<PreferenceChange> {
        return callbackFlow {
            val preferences = context.getSharedPreferences(fileName, Context.MODE_PRIVATE)
            
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                val change = PreferenceChange(
                    fileName = fileName,
                    key = key,
                    newValue = preferences.all[key],
                    timestamp = System.currentTimeMillis()
                )
                trySend(change)
            }
            
            preferences.registerOnSharedPreferenceChangeListener(listener)
            
            awaitClose {
                preferences.unregisterOnSharedPreferenceChangeListener(listener)
            }
        }
    }
    
    data class PreferenceChange(
        val fileName: String,
        val key: String,
        val newValue: Any?,
        val timestamp: Long
    )
}
```

## Verification Steps

### 1. Unit Tests

```kotlin
@Test
fun `preferences resource should return all preference files`()

@Test
fun `encrypted preferences should be handled securely`()

@Test
fun `preference change notifications should be emitted`()

@Test
fun `sensitive preference values should be masked`()
```

### 2. Integration Tests

```kotlin
@Test
fun `preferences should be accessible via MCP resources`()

@Test
fun `encrypted preferences should decrypt correctly`()

@Test
fun `preference modifications should trigger notifications`()
```

## Dependencies

- **Core ResourceProvider**: Basic resource framework
- **Task 01**: Resource subscription logic (for change notifications)

## Success Criteria

- [ ] All SharedPreferences files accessible as MCP resources
- [ ] Encrypted SharedPreferences support working
- [ ] Real-time change notifications functional
- [ ] Type-safe preference value handling
- [ ] Sensitive data protection mechanisms active
- [ ] MCP resource integration complete
- [ ] Test coverage >90% for preferences operations

## Resources

### Android Documentation

- [SharedPreferences Guide](https://developer.android.com/training/data-storage/shared-preferences)
- [EncryptedSharedPreferences](https://developer.android.com/reference/androidx/security/crypto/EncryptedSharedPreferences)
- [Security Best Practices](https://developer.android.com/training/articles/security-tips)