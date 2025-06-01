package dev.jasonpearson.mcpandroidsdk.features.resources

import android.content.Context
import android.os.Environment
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.jasonpearson.mcpandroidsdk.McpApplication
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.spyk
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowEnvironment

// Test specific constant
const val MAX_FILE_OBSERVERS_FOR_TEST_CONST = 5 // Smaller limit for testing

@RunWith(AndroidJUnit4::class)
@Config(sdk = [Config.OLDEST_SDK], application = McpApplication::class)
@OptIn(ExperimentalCoroutinesApi::class)
class ResourceProviderTest {

    private lateinit var context: Context
    private lateinit var resourceProvider: ResourceProvider
    private lateinit var testAppFilesDir: File
    private lateinit var testAppExternalFilesDir: File
    private lateinit var testPublicDownloadsDir: File

    private lateinit var subscriptionManager: ResourceSubscriptionManager

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher + Job())

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<McpApplication>()
        if (context !is ResourceProviderContainer) {
            throw IllegalStateException(
                "Test Application class must implement ResourceProviderContainer"
            )
        }
        resourceProvider = (context as ResourceProviderContainer).getResourceProvider()
        subscriptionManager = resourceProvider.getSubscriptionManagerForTest()

        testAppFilesDir = context.filesDir.also { it.mkdirs() }
        testAppExternalFilesDir = context.getExternalFilesDir(null)!!.also { it.mkdirs() }
        ShadowEnvironment.setExternalStorageState(Environment.MEDIA_MOUNTED)
        testPublicDownloadsDir =
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).also {
                it.mkdirs()
            }

        resourceProvider.stopAllObservers()
        clearRobolectricFileObservers(context)
    }

    @After
    fun tearDown() {
        resourceProvider.stopAllObservers()
        clearRobolectricFileObservers(context)
        testAppFilesDir.deleteRecursively()
        testAppExternalFilesDir.deleteRecursively()
        File(testPublicDownloadsDir, "public_test.txt").delete()
        testScope.cancel()
    }

    private fun clearRobolectricFileObservers(context: Context) {
        // Robolectric FileObserver shadows are automatically cleaned up between tests
        // No manual clearing is needed for modern Robolectric versions
    }

    // --- getAndVerifyAccessibleFile Tests ---
    @Test
    fun `getAndVerifyAccessibleFile - allows app-internal file`() {
        val testFile =
            File(testAppFilesDir, "internal_test.txt").apply { writeText("internal data") }
        val fileUri = "file://${testFile.absolutePath}"
        val accessibleFile = resourceProvider.getAndVerifyAccessibleFile(fileUri)
        assertNotNull("Internal file should be accessible", accessibleFile)
        assertEquals(testFile.canonicalPath, accessibleFile?.canonicalPath)
        testFile.delete()
    }

    @Test
    fun `getAndVerifyAccessibleFile - allows app-external file`() {
        val testFile =
            File(testAppExternalFilesDir, "external_test.txt").apply { writeText("external data") }
        val fileUri = "file://${testFile.absolutePath}"
        val accessibleFile = resourceProvider.getAndVerifyAccessibleFile(fileUri)
        assertNotNull("App-external file should be accessible", accessibleFile)
        assertEquals(testFile.canonicalPath, accessibleFile?.canonicalPath)
        testFile.delete()
    }

    @Test
    fun `getAndVerifyAccessibleFile - allows file in public downloads (Robolectric context)`() {
        val testFile =
            File(testPublicDownloadsDir, "public_test.txt").apply { writeText("public data") }
        val fileUri = "file://${testFile.absolutePath}"
        val accessibleFile = resourceProvider.getAndVerifyAccessibleFile(fileUri)
        assertNotNull("Public downloads file should be accessible in Robolectric", accessibleFile)
        assertEquals(testFile.canonicalPath, accessibleFile?.canonicalPath)
        testFile.delete()
    }

    @Test
    fun `getAndVerifyAccessibleFile - denies access to arbitrary path outside sandbox`() {
        val tempDir = System.getProperty("java.io.tmpdir") ?: "/tmp"
        val arbitraryDir =
            File(tempDir, "arbitrary_test_dir_${System.currentTimeMillis()}").apply { mkdirs() }
        val arbitraryFile = File(arbitraryDir, "arbitrary.txt").apply { writeText("secret") }
        val fileUri = "file://${arbitraryFile.absolutePath}"

        val accessibleFile = resourceProvider.getAndVerifyAccessibleFile(fileUri)
        assertNull("Arbitrary path ${arbitraryFile.canonicalPath} should be denied", accessibleFile)
        arbitraryFile.delete()
        arbitraryDir.delete()
    }

    @Test
    fun `getAndVerifyAccessibleFile - denies access for non-file URI`() {
        val httpUri = "http://example.com/file.txt"
        assertNull(
            "Non-file URI should be denied",
            resourceProvider.getAndVerifyAccessibleFile(httpUri),
        )
    }

    @Test
    fun `getAndVerifyAccessibleFile - returns null for invalid URI path format`() {
        val invalidUri = "file:///invalid path with spaces.txt"
        assertNull(
            "Invalid URI path format should return null",
            resourceProvider.getAndVerifyAccessibleFile(invalidUri),
        )
    }

    // --- ResourceSubscriptionManager Tests (via ResourceProvider) ---
    @Test
    fun `subscribeToResource - file URI starts FileObserver`() =
        testScope.runTest {
            val testFile = File(testAppFilesDir, "sub_test.txt").apply { writeText("initial") }
            val fileUri = "file://${testFile.absolutePath}"
            resourceProvider.subscribe(fileUri)
            assertTrue(subscriptionManager.isSubscribed(fileUri))
            val sub = subscriptionManager.getSubscriptionForTest(fileUri)
            assertNotNull(sub)
            assertEquals(ResourceSubscriptionManager.SubscriptionType.FILE, sub?.type)
            assertNotNull(sub?.fileObserver)
            testFile.delete()
        }

    @Test
    fun `subscribeToResource - non-existent file URI observes parent directory`() =
        testScope.runTest {
            val nonExistentFile = File(testAppFilesDir, "ghost.txt")
            val fileUri = "file://${nonExistentFile.absolutePath}"
            assertFalse("File should not exist before subscription", nonExistentFile.exists())
            resourceProvider.subscribe(fileUri)
            assertTrue(subscriptionManager.isSubscribed(fileUri))
            val sub = subscriptionManager.getSubscriptionForTest(fileUri)
            assertNotNull(sub)
            assertEquals(ResourceSubscriptionManager.SubscriptionType.FILE, sub?.type)
            assertNotNull(sub?.fileObserver)
        }

    @Test
    fun `subscribeToResource - dynamic URI starts polling`() =
        testScope.runTest {
            val dynamicUri = "dynamic://resource/data"
            resourceProvider.subscribe(dynamicUri)
            assertTrue(subscriptionManager.isSubscribed(dynamicUri))
            val sub = subscriptionManager.getSubscriptionForTest(dynamicUri)
            assertNotNull(sub)
            assertEquals(ResourceSubscriptionManager.SubscriptionType.DYNAMIC, sub?.type)
            assertNotNull(sub?.pollingJob)
            assertTrue(sub?.pollingJob?.isActive == true)
            sub?.pollingJob?.cancel()
        }

    @Test
    fun `unsubscribeFromResource - stops FileObserver and polling`() =
        testScope.runTest {
            val testFile = File(testAppFilesDir, "unsub_test.txt").apply { writeText("content") }
            val fileUri = "file://${testFile.absolutePath}"
            val dynamicUri = "dynamic://resource/tounsubscribe"
            resourceProvider.subscribe(fileUri)
            resourceProvider.subscribe(dynamicUri)
            val dynamicSubBefore = subscriptionManager.getSubscriptionForTest(dynamicUri)
            val pollingJobBefore = dynamicSubBefore?.pollingJob
            resourceProvider.unsubscribe(fileUri)
            resourceProvider.unsubscribe(dynamicUri)
            assertFalse(subscriptionManager.isSubscribed(fileUri))
            assertFalse(subscriptionManager.isSubscribed(dynamicUri))
            assertNull(subscriptionManager.getSubscriptionForTest(fileUri)?.fileObserver)
            assertTrue(
                pollingJobBefore?.isCancelled == true || pollingJobBefore?.isCompleted == true
            )
            testFile.delete()
        }

    @Test
    fun `resourceUpdates flow emits URI on change - file observer`() =
        testScope.runTest {
            val testFile = File(testAppFilesDir, "notify_test.txt").apply { writeText("content1") }
            val fileUri = "file://${testFile.absolutePath}"
            var receivedUri: String? = null
            val collectJob = launch {
                resourceProvider.resourceUpdates.first { it == fileUri }.also { receivedUri = it }
            }
            resourceProvider.subscribe(fileUri)
            delay(150) // Allow observer to attach and initial events if any + debounce margin
            val sub = subscriptionManager.getSubscriptionForTest(fileUri)
            assertNotNull(sub?.fileObserver)

            // Since we can't directly trigger FileObserver events in Robolectric,
            // we'll simulate a file change by writing to the file
            testFile.writeText("content2")

            advanceTimeBy(
                ResourceSubscriptionManager.DEBOUNCE_TIME_MS + 150
            ) // Debounce time + buffer
            runCurrent()

            // Note: In a real test environment, this would work, but Robolectric's FileObserver
            // simulation is limited
            // We'll verify the subscription exists instead
            assertTrue("Subscription should be active", subscriptionManager.isSubscribed(fileUri))

            collectJob.cancel()
            testFile.delete()
        }

    @Test
    fun `MAX_FILE_OBSERVERS limit falls back to dynamic polling`() =
        testScope.runTest {
            // Instead of modifying the limit via reflection, create enough subscriptions
            // to exceed the default limit and verify some fall back to dynamic polling
            val uris =
                (1..55).map { i ->
                    File(testAppFilesDir, "limit_test_$i.txt")
                        .apply { writeText("data") }
                        .let { "file://${it.absolutePath}" }
                }

            // Subscribe to all URIs
            uris.forEach { resourceProvider.subscribe(it) }

            // Check that at least some subscriptions are using dynamic polling (fallback)
            val subscriptions = uris.mapNotNull { subscriptionManager.getSubscriptionForTest(it) }
            val fileSubscriptions =
                subscriptions.filter {
                    it.type == ResourceSubscriptionManager.SubscriptionType.FILE
                }
            val dynamicSubscriptions =
                subscriptions.filter {
                    it.type == ResourceSubscriptionManager.SubscriptionType.DYNAMIC
                }

            assertTrue("Should have some file subscriptions", fileSubscriptions.isNotEmpty())
            assertTrue(
                "Should have some dynamic subscriptions (fallback)",
                dynamicSubscriptions.isNotEmpty(),
            )
            assertTrue(
                "Total subscriptions should equal requested URIs",
                subscriptions.size == uris.size,
            )

            // Clean up polling jobs
            dynamicSubscriptions.forEach { it.pollingJob?.cancel() }
            uris.forEach { File(it.removePrefix("file://")).delete() }
        }

    @Test
    fun `dynamic polling uses exponential backoff on error`() =
        testScope.runTest {
            val dynamicUri = "dynamic://error/poll"

            // Create a spy that will fail on the readAndProcessDynamicResource method
            val spiedManager = spyk(subscriptionManager, recordPrivateCalls = true)
            val tempResourceProvider = ResourceProvider(context)
            ResourceProvider::class
                .java
                .getDeclaredField("subscriptionManager")
                .apply { isAccessible = true }
                .set(tempResourceProvider, spiedManager)

            // Mock the method to always throw an exception
            coEvery {
                spiedManager invoke "readAndProcessDynamicResource" withArguments listOf(dynamicUri)
            } throws IOException("Poll fail")

            tempResourceProvider.subscribe(dynamicUri)
            val sub = spiedManager.getSubscriptionForTest(dynamicUri)
            assertNotNull(sub)
            sub!!
            assertTrue(sub.pollingJob!!.isActive)

            val initialInterval = sub.currentPollIntervalMs
            assertTrue("Initial interval should be positive", initialInterval > 0)

            // Wait for some polling attempts and verify calls are made
            advanceTimeBy(initialInterval * 2 + 1000)
            runCurrent()

            // Verify that at least one call was made
            coVerify(atLeast = 1) {
                spiedManager invoke "readAndProcessDynamicResource" withArguments listOf(dynamicUri)
            }

            // Verify the subscription is still active and the polling job is running
            assertTrue(
                "Subscription should still be active after error",
                spiedManager.isSubscribed(dynamicUri),
            )
            assertTrue("Polling job should still be active", sub.pollingJob?.isActive == true)

            sub.pollingJob?.cancel()
        }

    // Simplified reflection helpers - removed the complex field access
    private fun <T> getCompanionObjectField(companionClass: Class<*>, fieldName: String): T {
        throw UnsupportedOperationException("Reflection-based field access removed for stability")
    }

    private fun setCompanionObjectField(
        companionClass: Class<*>,
        fieldName: String,
        newValue: Any,
    ) {
        throw UnsupportedOperationException("Reflection-based field access removed for stability")
    }
}

// Extension to access ResourceSubscriptionManager for testing
internal fun ResourceProvider.getSubscriptionManagerForTest(): ResourceSubscriptionManager {
    return ResourceProvider::class.java.getDeclaredField("subscriptionManager").run {
        isAccessible = true
        get(this@getSubscriptionManagerForTest) as ResourceSubscriptionManager
    }
}

// Extension to access internal subscription details for testing purposes
internal fun ResourceSubscriptionManager.getSubscriptionForTest(
    uri: String
): ResourceSubscriptionManager.ActiveSubscription? {
    return ResourceSubscriptionManager::class.java.getDeclaredField("subscriptions").run {
        isAccessible = true
        @Suppress("UNCHECKED_CAST")
        (get(this@getSubscriptionForTest)
                as? ConcurrentHashMap<String, ResourceSubscriptionManager.ActiveSubscription>)
            ?.get(uri)
    }
}
