package dev.jasonpearson.mcpandroidsdk

import android.content.Context
import androidx.startup.AppInitializer
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
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
        McpServerManager.getInstance().cleanup()
    }

    @After
    fun tearDown() {
        McpServerManager.getInstance().cleanup()
    }

    @Test
    fun `test manual initialization with McpStartup`() {
        // Initially should not be initialized
        assertFalse(McpStartup.isInitialized())

        // Manual initialization should succeed
        val manager = McpStartup.initializeManually(context)
        
        assertNotNull(manager)
        assertTrue(McpStartup.isInitialized())
        assertTrue(manager.isInitialized())
    }

    @Test
    fun `test custom configuration initialization`() {
        // Initially should not be initialized
        assertFalse(McpStartup.isInitialized())

        val customName = "Test MCP Server"
        val customVersion = "1.2.3"

        // Custom initialization should succeed
        val result = McpStartup.initializeWithCustomConfig(
            context = context,
            serverName = customName,
            serverVersion = customVersion
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
        // Initialize first
        McpStartup.initializeManually(context)
        
        // Now getManager should work
        val manager = McpStartup.getManager()
        assertNotNull(manager)
        assertTrue(manager.isInitialized())
    }

    @Test
    fun `test isInitialized returns false when not initialized`() {
        assertFalse(McpStartup.isInitialized())
    }

    @Test
    fun `test isInitialized returns true when initialized`() {
        McpStartup.initializeManually(context)
        assertTrue(McpStartup.isInitialized())
    }
}
