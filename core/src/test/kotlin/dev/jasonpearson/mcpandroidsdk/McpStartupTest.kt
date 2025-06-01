package dev.jasonpearson.mcpandroidsdk

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class McpStartupTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // Clean up any previous state
        McpServerManager.getInstance().resetForTesting()
    }

    @After
    fun tearDown() {
        McpServerManager.getInstance().resetForTesting()
    }

    /**
     * Create a minimal initialized manager for testing without expensive operations. This bypasses
     * transport setup, SDK reflection, and feature provider initialization.
     */
    private fun createTestInitializedManager(): McpServerManager {
        val manager = McpServerManager.getInstance()

        // Use proper initialization instead of reflection to avoid type issues
        val result = manager.initialize(context, "Test Server", "1.0.0")
        if (result.isFailure) {
            throw RuntimeException(
                "Failed to initialize manager for testing",
                result.exceptionOrNull(),
            )
        }

        return manager
    }

    /** Minimal mock server for testing - no longer needed since we use proper initialization */
    // private class MockMcpServer { ... }

    @Test
    fun `test manual initialization with McpStartup`() {
        // Initially should not be initialized
        Log.d("TEST", "Initial state - isInitialized: ${McpStartup.isInitialized()}")
        assertFalse(McpStartup.isInitialized())

        try {
            // Use direct initialization instead of AndroidX Startup for testing
            val manager = McpServerManager.getInstance()
            val initResult = manager.initialize(context, "Test Server", "1.0.0")
            assertTrue("Initialization should succeed", initResult.isSuccess)

            Log.d("TEST", "manager.isInitialized(): ${manager.isInitialized()}")
            Log.d("TEST", "McpStartup.isInitialized(): ${McpStartup.isInitialized()}")

            assertNotNull(manager)
            assertTrue("Manager should be initialized", manager.isInitialized())
            assertTrue("McpStartup should report initialized", McpStartup.isInitialized())
        } catch (e: Exception) {
            Log.e("TEST", "Exception during initialization", e)
            throw e
        }
    }

    @Test
    fun `test custom configuration initialization`() {
        // Initially should not be initialized
        assertFalse(McpStartup.isInitialized())

        val customName = "Test MCP Server"
        val customVersion = "1.2.3"

        // Custom initialization should succeed
        val result =
            McpStartup.initializeWithCustomConfig(
                context = context,
                serverName = customName,
                serverVersion = customVersion,
            )

        assertTrue(result.isSuccess)
        val manager = result.getOrThrow()

        assertNotNull(manager)
        assertTrue(McpStartup.isInitialized())
        assertTrue(manager.isInitialized())
    }

    @Test
    fun `test getManager throws when not initialized`() {
        // Initially should not be initialized
        assertFalse(McpStartup.isInitialized())

        try {
            McpStartup.getManager()
            assert(false) { "Expected IllegalStateException" }
        } catch (e: IllegalStateException) {
            assertTrue(e.message?.contains("not initialized") == true)
        }
    }

    @Test
    fun `test getManager returns manager when initialized`() {
        // Use fast test initialization
        createTestInitializedManager()

        // Now getManager should work
        val retrievedManager = McpStartup.getManager()
        assertNotNull(retrievedManager)
        assertTrue(retrievedManager.isInitialized())
    }

    @Test
    fun `test isInitialized returns false when not initialized`() {
        assertFalse(McpStartup.isInitialized())
    }

    @Test
    fun `test isInitialized returns true when initialized`() {
        Log.d("TEST", "Before manual init - isInitialized: ${McpStartup.isInitialized()}")

        // Use direct initialization instead of AndroidX Startup for testing
        val manager = McpServerManager.getInstance()
        val initResult = manager.initialize(context, "Test Server", "1.0.0")
        assertTrue("Initialization should succeed", initResult.isSuccess)

        Log.d("TEST", "After manual init - isInitialized: ${McpStartup.isInitialized()}")
        assertTrue(
            "McpStartup should report initialized after manual init",
            McpStartup.isInitialized(),
        )
    }

    @Test
    fun `test initialization step by step`() {
        Log.d("TEST", "=== Starting step by step test ===")

        // Step 1: Verify initial state
        Log.d("TEST", "Step 1: Check initial state")
        val initialState = McpStartup.isInitialized()
        Log.d("TEST", "Initial isInitialized: $initialState")
        assertFalse("Should start uninitialized", initialState)

        // Step 2: Get manager instance (should not be initialized yet)
        Log.d("TEST", "Step 2: Get manager instance")
        val manager = McpServerManager.getInstance()
        Log.d("TEST", "Got manager: $manager")
        val managerInitState = manager.isInitialized()
        Log.d("TEST", "Manager isInitialized: $managerInitState")
        assertFalse("Manager should not be initialized yet", managerInitState)

        // Step 3: Try manual initialization
        Log.d("TEST", "Step 3: Initialize manually")
        try {
            val initResult = manager.initialize(context, "Test Server", "1.0.0")
            Log.d("TEST", "Initialize result: $initResult")
            if (initResult.isFailure) {
                Log.e("TEST", "Initialization failed", initResult.exceptionOrNull())
            }
            assertTrue("Initialization should succeed", initResult.isSuccess)

            // Step 4: Check state after initialization
            Log.d("TEST", "Step 4: Check state after manual initialization")
            val afterInitState = manager.isInitialized()
            Log.d("TEST", "Manager isInitialized after init: $afterInitState")
            assertTrue("Manager should be initialized after init", afterInitState)

            val startupState = McpStartup.isInitialized()
            Log.d("TEST", "McpStartup isInitialized after init: $startupState")
            assertTrue("McpStartup should report initialized", startupState)
        } catch (e: Exception) {
            Log.e("TEST", "Exception during manual init", e)
            throw e
        }

        Log.d("TEST", "=== Step by step test completed ===")
    }
}
