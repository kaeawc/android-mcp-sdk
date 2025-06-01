# Task 07: File System Resources with Proper Android Permissions

## Objective

Implement comprehensive file system resource access with proper Android permissions, scoped storage
compliance, and security boundaries for the MCP SDK.

## Requirements

### Technical Requirements

- **Android API 29+**: Full scoped storage compliance
- **Permission Management**: Runtime permission handling for external storage
- **Security Boundaries**: Restrict access to app-specific directories by default
- **MediaStore Integration**: Access media files through MediaStore API
- **SAF Integration**: Support Storage Access Framework for user-selected directories
- **MIME Type Detection**: Automatic MIME type detection for all file types

### Security Requirements

- **Principle of Least Privilege**: Only request necessary permissions
- **Scoped Storage Compliance**: Follow Android 10+ storage restrictions
- **Path Traversal Protection**: Prevent directory traversal attacks
- **Permission Validation**: Runtime permission checks before file operations

## Implementation Steps

### Step 1: Create File Permission Manager

Create `lib/src/main/kotlin/dev/jasonpearson/mcpandroidsdk/resources/FilePermissionManager.kt`:

```kotlin
class FilePermissionManager(private val context: Context) {
    
    enum class StorageScope {
        APP_INTERNAL,           // Always accessible
        APP_EXTERNAL,           // App-specific external storage
        MEDIA_IMAGES,           // Requires READ_MEDIA_IMAGES (API 33+)
        MEDIA_VIDEO,            // Requires READ_MEDIA_VIDEO (API 33+)  
        MEDIA_AUDIO,            // Requires READ_MEDIA_AUDIO (API 33+)
        EXTERNAL_STORAGE,       // Requires MANAGE_EXTERNAL_STORAGE (special)
        USER_SELECTED           // Via Storage Access Framework
    }
    
    suspend fun checkFileAccess(uri: String): FileAccessResult
    suspend fun requestFilePermissions(scope: StorageScope): PermissionResult
    fun getScopedDirectories(): List<ScopedDirectory>
}
```

### Step 2: Enhanced Resource Provider

Update `ResourceProvider.kt` to include file system integration:

```kotlin
class FileSystemResourceProvider(
    private val context: Context,
    private val permissionManager: FilePermissionManager
) {
    
    suspend fun addFileResource(
        uri: String,
        filePath: String,
        scope: StorageScope = StorageScope.APP_INTERNAL,
        requirePermission: Boolean = true
    ): Result<Unit>
    
    suspend fun addDirectoryResource(
        uri: String,
        directoryPath: String,
        recursive: Boolean = false,
        scope: StorageScope = StorageScope.APP_INTERNAL
    ): Result<Unit>
    
    suspend fun addMediaStoreResource(
        uri: String,
        mediaType: MediaType,
        selection: String? = null
    ): Result<Unit>
}
```

### Step 3: Implement Storage Access Framework Support

Create `lib/src/main/kotlin/dev/jasonpearson/mcpandroidsdk/resources/SafResourceProvider.kt`:

```kotlin
class SafResourceProvider(private val context: Context) {
    
    fun createDocumentPickerIntent(mimeTypes: Array<String>): Intent
    
    suspend fun handleDocumentResult(
        resultCode: Int, 
        data: Intent?
    ): SafAccessResult
    
    suspend fun addSafResource(
        uri: String,
        documentUri: Uri,
        persistent: Boolean = true
    ): Result<Unit>
}
```

### Step 4: Create Permission Request Activity

Create `lib/src/main/kotlin/dev/jasonpearson/mcpandroidsdk/ui/PermissionRequestActivity.kt`:

```kotlin
class PermissionRequestActivity : ComponentActivity() {
    
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        handlePermissionResult(permissions)
    }
    
    private val documentPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        handleDocumentPickerResult(result)
    }
    
    companion object {
        fun createIntent(
            context: Context, 
            requestType: PermissionRequestType
        ): Intent
    }
}
```

### Step 5: Update Manifest with Permissions

Update `lib/src/main/AndroidManifest.xml`:

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    
    <!-- Storage permissions -->
    <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE"
                     android:maxSdkVersion="32" />
    <uses-permission android:name="android.permission.READ_MEDIA_IMAGES" />
    <uses-permission android:name="android.permission.READ_MEDIA_VIDEO" />
    <uses-permission android:name="android.permission.READ_MEDIA_AUDIO" />
    
    <!-- Special permissions (user must enable in settings) -->
    <uses-permission android:name="android.permission.MANAGE_EXTERNAL_STORAGE"
                     tools:ignore="ScopedStorage" />
    
    <!-- Permission request activity -->
    <activity
        android:name=".ui.PermissionRequestActivity"
        android:theme="@style/Theme.Transparent"
        android:exported="false" />
        
    <!-- File provider for sharing app files -->
    <provider
        android:name="androidx.core.content.FileProvider"
        android:authorities="${applicationId}.fileprovider"
        android:exported="false"
        android:grantUriPermissions="true">
        <meta-data
            android:name="android.support.FILE_PROVIDER_PATHS"
            android:resource="@xml/file_provider_paths" />
    </provider>
    
</manifest>
```

### Step 6: Add File Provider Paths

Create `lib/src/main/res/xml/file_provider_paths.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<paths xmlns:android="http://schemas.android.com/apk/res/android">
    <files-path name="internal_files" path="." />
    <cache-path name="cache_files" path="." />
    <external-files-path name="external_files" path="." />
    <external-cache-path name="external_cache" path="." />
</paths>
```

### Step 7: Implement Built-in File Resources

Add to `ResourceProvider.kt`:

```kotlin
private fun registerBuiltInFileResources() {
    // App internal files
    addResource(Resource(
        uri = "android://files/internal",
        name = "Internal Files",
        description = "Application internal file directory"
    )) {
        listDirectoryContents(context.filesDir)
    }
    
    // App cache files  
    addResource(Resource(
        uri = "android://files/cache",
        name = "Cache Files", 
        description = "Application cache directory"
    )) {
        listDirectoryContents(context.cacheDir)
    }
    
    // Shared preferences as files
    addResource(Resource(
        uri = "android://files/preferences",
        name = "Shared Preferences",
        description = "Application shared preferences"
    )) {
        getSharedPreferencesAsResource()
    }
}
```

### Step 8: Add Dependencies

Update `lib/build.gradle.kts`:

```kotlin
dependencies {
    // Storage Access Framework
    implementation("androidx.activity:activity-ktx:1.8.2")
    implementation("androidx.documentfile:documentfile:1.0.1")
    
    // File operations
    implementation("androidx.core:core-ktx:1.12.0")
    
    // Permission handling
    implementation("androidx.fragment:fragment-ktx:1.6.2")
}
```

## Verification Steps

### 1. Unit Tests

Create `lib/src/test/kotlin/dev/jasonpearson/mcpandroidsdk/resources/FilePermissionManagerTest.kt`:

```kotlin
@Test
fun `checkFileAccess should return success for app internal files`()

@Test  
fun `checkFileAccess should require permission for external storage`()

@Test
fun `getScopedDirectories should return valid accessible directories`()

@Test
fun `requestFilePermissions should handle permission denial gracefully`()
```

### 2. Integration Tests

Create `lib/src/androidTest/kotlin/dev/jasonpearson/mcpandroidsdk/FileSystemIntegrationTest.kt`:

```kotlin
@Test
fun `can access internal app files without permission`()

@Test
fun `can request and access media files with permission`()

@Test
fun `storage access framework integration works`()

@Test
fun `file resources appear in MCP resource list`()
```

### 3. Manual Testing

1. **Install sample app** and verify no crashes on startup
2. **Test internal file access** - should work without permissions
3. **Test media file access** - should request appropriate permissions
4. **Test SAF integration** - document picker should open
5. **Test permission denial** - app should handle gracefully
6. **Test scoped storage** - cannot access arbitrary external files

### 4. Security Testing

1. **Path traversal test** - attempt `../../etc/passwd` paths
2. **Permission bypass test** - access external files without permission
3. **Scope validation** - ensure only declared scopes are accessible

## Dependencies

- **Task 01**: Resource subscription logic (for file watching)
- **Core ResourceProvider**: Basic resource framework must exist

## Resources

### Android Documentation

- [Scoped Storage](https://developer.android.com/training/data-storage/shared/scoped-directory-access)
- [Storage Access Framework](https://developer.android.com/guide/topics/providers/document-provider)
- [App-specific Storage](https://developer.android.com/training/data-storage/app-specific)
- [Request Runtime Permissions](https://developer.android.com/training/permissions/requesting)

### AndroidX Libraries

- [Activity Result APIs](https://developer.android.com/training/basics/intents/result)
- [DocumentFile](https://developer.android.com/reference/androidx/documentfile/provider/DocumentFile)
- [Core KTX](https://developer.android.com/kotlin/ktx#core)

### Best Practices

- [Android Storage Best Practices](https://developer.android.com/training/data-storage/use-cases)
- [Permission Best Practices](https://developer.android.com/training/permissions/usage-notes)
- [Scoped Storage Migration](https://developer.android.com/training/data-storage/shared/scoped-directory-access)

## Success Criteria

- [ ] All file operations respect Android scoped storage
- [ ] Runtime permissions properly requested and handled
- [ ] Storage Access Framework integration working
- [ ] Built-in file resources accessible via MCP
- [ ] Security boundaries enforced
- [ ] No crashes on permission denial
- [ ] Comprehensive test coverage (>90%)
- [ ] Sample app demonstrates all file access patterns