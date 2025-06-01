package dev.jasonpearson.mcpandroidsdk

import org.junit.Assert.*
import org.junit.Assert.fail
import org.junit.Test

/** Unit tests for McpServerManager */
class McpServerManagerTest {

    @Test
    fun `getInstance should return singleton instance`() {
        val instance1 = McpServerManager.getInstance()
        val instance2 = McpServerManager.getInstance()

        assertNotNull("Instance should not be null", instance1)
        assertSame("Should return same singleton instance", instance1, instance2)
    }

    @Test
    fun `getMcpSdkVersion should return correct version`() {
        val manager = McpServerManager.getInstance()
        assertEquals("0.5.0", manager.getMcpSdkVersion())
    }

    @Test
    fun `isInitialized should return false initially`() {
        val manager = McpServerManager.getInstance()
        assertFalse("Should return false initially", manager.isInitialized())
    }

    @Test
    fun `isServerRunning should handle uninitialized state gracefully`() {
        val manager = McpServerManager.getInstance()
        val isRunning = manager.isServerRunning()
        assertFalse("Should return false when not initialized", isRunning)
    }

    @Test
    fun `getServerInfo should handle uninitialized state gracefully`() {
        val manager = McpServerManager.getInstance()
        val serverInfo = manager.getServerInfo()
        assertNull("Should return null when not initialized", serverInfo)
    }

    @Test
    fun `singleton should maintain state across calls`() {
        val manager1 = McpServerManager.getInstance()
        val manager2 = McpServerManager.getInstance()

        assertTrue("References should be equal", manager1 === manager2)
        assertEquals("Hash codes should be equal", manager1.hashCode(), manager2.hashCode())
    }

    @Test
    fun `getMcpSdkVersion should be consistent`() {
        val manager = McpServerManager.getInstance()
        val version1 = manager.getMcpSdkVersion()
        val version2 = manager.getMcpSdkVersion()

        assertEquals("Version should be consistent", version1, version2)
        assertEquals(
            "Should match static method",
            ComprehensiveMcpServer.getMcpSdkVersion(),
            version1,
        )
    }

    @Test
    fun `operations should throw exception when not initialized`() {
        val manager = McpServerManager.getInstance()

        try {
            manager.getMcpServer()
            fail("Should throw IllegalStateException when not initialized")
        } catch (e: IllegalStateException) {
            assertTrue(
                "Should contain initialization message",
                e.message?.contains("not initialized") == true,
            )
        }

        try {
            manager.getAndroidTools()
            fail("Should throw IllegalStateException when not initialized")
        } catch (e: IllegalStateException) {
            assertTrue(
                "Should contain initialization message",
                e.message?.contains("not initialized") == true,
            )
        }

        try {
            manager.getMcpResources()
            fail("Should throw IllegalStateException when not initialized")
        } catch (e: IllegalStateException) {
            assertTrue(
                "Should contain initialization message",
                e.message?.contains("not initialized") == true,
            )
        }

        try {
            manager.getMcpPrompts()
            fail("Should throw IllegalStateException when not initialized")
        } catch (e: IllegalStateException) {
            assertTrue(
                "Should contain initialization message",
                e.message?.contains("not initialized") == true,
            )
        }
    }
}
