package dev.jasonpearson.mcpandroidsdk

import dev.jasonpearson.mcpandroidsdk.models.*
import org.junit.Assert.*
import org.junit.Test

/** Unit tests for ComprehensiveMcpServer */
class ComprehensiveMcpServerTest {

    @Test
    fun `getMcpSdkVersion should return correct version`() {
        assertEquals("0.5.0", ComprehensiveMcpServer.getMcpSdkVersion())
    }

    @Test
    fun `ComprehensiveServerInfo should create correctly`() {
        val info =
            ComprehensiveServerInfo(
                name = "Test Server",
                version = "1.0.0",
                sdkVersion = "0.5.0",
                isRunning = false,
                isInitialized = true,
                capabilities = ServerCapabilities(),
                toolCount = 5,
                resourceCount = 3,
                promptCount = 4,
                rootCount = 2,
            )

        assertEquals("Test Server", info.name)
        assertEquals("1.0.0", info.version)
        assertEquals("0.5.0", info.sdkVersion)
        assertFalse(info.isRunning)
        assertTrue(info.isInitialized)
        assertEquals(5, info.toolCount)
        assertEquals(3, info.resourceCount)
        assertEquals(4, info.promptCount)
        assertEquals(2, info.rootCount)
    }

    @Test
    fun `ServerCapabilities should create with all features`() {
        val capabilities =
            ServerCapabilities(
                tools = ToolsCapability(listChanged = true),
                resources = ResourcesCapability(subscribe = true, listChanged = true),
                prompts = PromptsCapability(listChanged = true),
            )

        assertNotNull(capabilities.tools)
        assertNotNull(capabilities.resources)
        assertNotNull(capabilities.prompts)
        assertTrue(capabilities.tools?.listChanged ?: false)
        assertTrue(capabilities.resources?.subscribe ?: false)
        assertTrue(capabilities.resources?.listChanged ?: false)
        assertTrue(capabilities.prompts?.listChanged ?: false)
    }
}
