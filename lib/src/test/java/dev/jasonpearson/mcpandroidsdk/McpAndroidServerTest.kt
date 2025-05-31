package dev.jasonpearson.mcpandroidsdk

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for McpAndroidServer
 */
class McpAndroidServerTest {

    @Test
    fun `getMcpSdkVersion should return correct version`() {
        assertEquals("0.5.0", McpAndroidServer.getMcpSdkVersion())
    }

    @Test
    fun `ServerInfo should have correct properties`() {
        val serverInfo = ServerInfo(
            name = "test-server",
            version = "1.0.0",
            sdkVersion = "0.5.0",
            isRunning = false
        )

        assertEquals("test-server", serverInfo.name)
        assertEquals("1.0.0", serverInfo.version)
        assertEquals("0.5.0", serverInfo.sdkVersion)
        assertFalse(serverInfo.isRunning)
    }

    @Test
    fun `ServerInfo should support running state`() {
        val runningServer = ServerInfo(
            name = "running-server",
            version = "2.0.0",
            sdkVersion = "0.5.0",
            isRunning = true
        )

        assertTrue("Server should be marked as running", runningServer.isRunning)
    }

    @Test
    fun `ServerInfo data class should implement equality correctly`() {
        val server1 = ServerInfo("test", "1.0", "0.5.0", false)
        val server2 = ServerInfo("test", "1.0", "0.5.0", false)
        val server3 = ServerInfo("test", "1.0", "0.5.0", true)

        assertEquals("Identical ServerInfo objects should be equal", server1, server2)
        assertNotEquals("Different ServerInfo objects should not be equal", server1, server3)
    }

    @Test
    fun `ToolExecutionResult should handle success and failure cases`() {
        val successResult = ToolExecutionResult(
            success = true,
            result = "Success!",
            error = null
        )
        
        assertTrue(successResult.success)
        assertEquals("Success!", successResult.result)
        assertNull(successResult.error)
        
        val failureResult = ToolExecutionResult(
            success = false,
            result = null,
            error = "Something went wrong"
        )
        
        assertFalse(failureResult.success)
        assertNull(failureResult.result)
        assertEquals("Something went wrong", failureResult.error)
    }
}
