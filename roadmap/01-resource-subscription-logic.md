# Task 01: Resource Subscription Logic Implementation

## Status: `[ ]` Not Started

## Objective

Implement file observers for resource subscriptions in `ResourceProvider.kt` to enable real-time
updates when resources change. This will complete the MCP resource subscription mechanism by
automatically notifying clients when subscribed resources are modified.

## Requirements

### Technical Requirements

- Use Android's `FileObserver` API for monitoring file system changes
- Implement `androidx.lifecycle.Observer` pattern for resource state management
- Support both file-based and dynamic resource subscriptions
- Ensure thread-safe operations with proper synchronization
- Maintain subscription state across server restarts
- Handle edge cases like file deletion, permission changes, and device storage issues

### Performance Requirements

- Minimize battery drain from file system monitoring
- Use efficient polling strategies for dynamic resources
- Implement debouncing to avoid excessive notifications
- Support batch notifications for multiple resource changes

### Security Requirements

- Respect Android file permissions and scoped storage
- Prevent subscription to unauthorized file paths
- Validate resource URIs before creating observers

## Implementation Steps

### Step 1: Define Subscription Data Structures
```kotlin
data class ResourceSubscription(
    val uri: String,
    val observer: FileObserver?,
    val lastModified: Long,
    val isActive: Boolean = true
)

class ResourceSubscriptionManager {
    private val subscriptions = ConcurrentHashMap<String, ResourceSubscription>()
    private val notificationCallbacks = mutableListOf<(String) -> Unit>()
}
```

### Step 2: Implement File Observer Integration
```kotlin
private fun createFileObserver(filePath: String, uri: String): FileObserver {
    return object : FileObserver(filePath, MODIFY or DELETE or MOVED_FROM or MOVED_TO) {
        override fun onEvent(event: Int, path: String?) {
            when (event) {
                MODIFY -> notifyResourceChanged(uri)
                DELETE -> notifyResourceDeleted(uri)
                MOVED_FROM, MOVED_TO -> notifyResourceMoved(uri)
            }
        }
    }
}
```

### Step 3: Implement Dynamic Resource Polling
```kotlin
private fun startDynamicResourcePolling(uri: String, pollInterval: Long = 5000L) {
    val handler = Handler(Looper.getMainLooper())
    val runnable = object : Runnable {
        override fun run() {
            checkDynamicResourceChanges(uri)
            handler.postDelayed(this, pollInterval)
        }
    }
    handler.post(runnable)
}
```

### Step 4: Add Subscription Management Methods
```kotlin
fun subscribeToResource(uri: String): Result<Unit>
fun unsubscribeFromResource(uri: String): Result<Unit>
fun isSubscribed(uri: String): Boolean
fun getActiveSubscriptions(): List<String>
```

### Step 5: Implement Notification System
```kotlin
private fun notifyResourceChanged(uri: String) {
    notificationCallbacks.forEach { callback ->
        try {
            callback(uri)
        } catch (e: Exception) {
            Log.e(TAG, "Error notifying resource change for $uri", e)
        }
    }
}
```

### Step 6: Integrate with MCP Server
```kotlin
// In ComprehensiveMcpServer.kt
private val subscriptionManager = ResourceSubscriptionManager()

override suspend fun subscribeToResource(uri: String): Result<Unit> {
    return subscriptionManager.subscribeToResource(uri)
}
```

### Step 7: Handle Lifecycle Events
```kotlin
// Stop all observers when server stops
override suspend fun stop(): Result<Unit> {
    subscriptionManager.stopAllObservers()
    return super.stop()
}

// Restart observers when server starts
override suspend fun start(): Result<Unit> {
    val result = super.start()
    if (result.isSuccess) {
        subscriptionManager.restartActiveObservers()
    }
    return result
}
```

## Verification Steps

### Unit Tests

1. **Subscription Management Tests**
   ```kotlin
   @Test
   fun `subscribe to file resource creates observer`() {
       val uri = "file:///path/to/test.txt"
       val result = subscriptionManager.subscribeToResource(uri)
       assertTrue(result.isSuccess)
       assertTrue(subscriptionManager.isSubscribed(uri))
   }
   ```

2. **File Observer Tests**
   ```kotlin
   @Test
   fun `file modification triggers notification`() {
       // Create temporary file
       // Subscribe to resource
       // Modify file
       // Verify notification received
   }
   ```

3. **Dynamic Resource Tests**
   ```kotlin
   @Test
   fun `dynamic resource polling detects changes`() {
       // Mock dynamic resource
       // Subscribe with short poll interval
       // Change resource content
       // Verify notification within expected time
   }
   ```

### Integration Tests

1. **End-to-End Subscription Flow**
   ```kotlin
   @Test
   fun `complete subscription flow works`() {
       // Initialize server
       // Add resource
       // Subscribe to resource
       // Modify resource
       // Verify MCP client receives notification
   }
   ```

2. **Performance Tests**
   ```kotlin
   @Test
   fun `multiple subscriptions dont degrade performance`() {
       // Subscribe to 100+ resources
       // Measure resource usage
       // Verify acceptable performance
   }
   ```

### Manual Testing

1. Create test app with file resources
2. Subscribe to resources via MCP client
3. Modify files using Android file manager
4. Verify notifications are received
5. Test edge cases (file deletion, permission changes)

### Verification Commands

```bash
# Run unit tests
./gradlew :lib:testDebugUnitTest --tests "*ResourceSubscription*"

# Run integration tests
./gradlew :lib:testDebugUnitTest --tests "*ResourceProvider*"

# Check code coverage
./gradlew :lib:jacocoTestReport
```

## Dependencies

- **None** - This is a core functionality that other tasks depend on
- Requires existing `ResourceProvider.kt` implementation
- Should be completed before transport layer implementations

## Resources

### Android Documentation

- [FileObserver API](https://developer.android.com/reference/android/os/FileObserver)
- [Scoped Storage](https://developer.android.com/training/data-storage/shared/scoped-directory-access)
- [Background Processing](https://developer.android.com/guide/background)

### AndroidX Libraries

- `androidx.lifecycle:lifecycle-common:2.7.0` - For Observer pattern
- `androidx.work:work-runtime-ktx:2.8.1` - For background tasks if needed

### MCP Specification

- [Resource Subscriptions](https://modelcontextprotocol.io/docs/specification/resources#subscriptions)
- [Notification Protocol](https://modelcontextprotocol.io/docs/specification/notifications)

### Implementation Examples

```kotlin
// Example file observer implementation
class ResourceFileObserver(
    private val path: String,
    private val onChanged: (String) -> Unit
) : FileObserver(path, MODIFY or DELETE) {
    
    override fun onEvent(event: Int, path: String?) {
        when (event) {
            MODIFY -> onChanged("Resource modified: $path")
            DELETE -> onChanged("Resource deleted: $path")
        }
    }
}
```

## Notes

- Consider using `androidx.work.WorkManager` for background polling of dynamic resources
- Implement exponential backoff for polling to optimize battery usage
- Use `kotlinx.coroutines.channels` for efficient notification delivery
- Consider implementing a resource cache to avoid unnecessary file reads
- Ensure proper cleanup of observers to prevent memory leaks
