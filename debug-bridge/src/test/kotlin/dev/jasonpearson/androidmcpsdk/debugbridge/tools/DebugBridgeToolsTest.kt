package dev.jasonpearson.androidmcpsdk.debugbridge.tools

import android.content.Context
import dev.jasonpearson.androidmcpsdk.debugbridge.DebugBridgeToolContributor
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

class DebugBridgeToolsTest {

    @Test
    fun `should have correct provider name`() {
        val mockContext = mockk<Context>(relaxed = true)
        val debugBridgeContributor = DebugBridgeToolContributor(mockContext)
        val providerName = debugBridgeContributor.getProviderName()
        assertEquals("DebugBridge", providerName)
    }
}
