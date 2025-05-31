package dev.jasonpearson.mcpandroidsdk.transport

import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TransportManagerTest {

    private lateinit var transportManager: TransportManager

    @Before
    fun setUp() {
        transportManager = TransportManager()
    }

    @After
    fun tearDown() = runTest {
        if (transportManager.isRunning) {
            transportManager.stopAll()
        }
    }

    @Test
    fun `setupDefaultTransports should configure WebSocket and HTTP SSE transports`() {
        // Setup default transports
        transportManager.setupDefaultTransports()

        // Verify both transports are added
        assertEquals(2, transportManager.getTransportNames().size)
        assertTrue(transportManager.getTransportNames().contains("websocket"))
        assertTrue(transportManager.getTransportNames().contains("http_sse"))

        // Verify transport types
        val websocketTransport = transportManager.getTransport("websocket")
        val httpTransport = transportManager.getTransport("http_sse")

        assertNotNull(websocketTransport)
        assertNotNull(httpTransport)
        assertTrue(websocketTransport is WebSocketTransport)
        assertTrue(httpTransport is HttpSseTransport)
    }

    @Test
    fun `addTransport should successfully add new transport`() {
        val mockTransport =
            object : McpTransport {
                override val isRunning: Boolean = false

                override suspend fun start(): Result<Unit> = Result.success(Unit)

                override suspend fun stop(): Result<Unit> = Result.success(Unit)

                override suspend fun sendMessage(message: String): Result<Unit> =
                    Result.success(Unit)

                override val incomingMessages = kotlinx.coroutines.flow.emptyFlow<String>()

                override fun getConnectionInfo() = mapOf("type" to "mock")
            }

        transportManager.addTransport("mock", mockTransport)

        assertEquals(1, transportManager.getTransportNames().size)
        assertTrue(transportManager.getTransportNames().contains("mock"))
        assertSame(mockTransport, transportManager.getTransport("mock"))
    }

    @Test
    fun `removeTransport should successfully remove transport`() {
        transportManager.setupDefaultTransports()

        assertTrue(transportManager.removeTransport("websocket"))
        assertEquals(1, transportManager.getTransportNames().size)
        assertFalse(transportManager.getTransportNames().contains("websocket"))

        assertFalse(transportManager.removeTransport("nonexistent"))
    }

    @Test
    fun `getConnectionInfo should return comprehensive information`() {
        transportManager.setupDefaultTransports()

        val connectionInfo = transportManager.getConnectionInfo()

        assertEquals(false, connectionInfo["isRunning"])
        assertEquals(2, connectionInfo["transportCount"])
        assertTrue(connectionInfo.containsKey("transports"))

        @Suppress("UNCHECKED_CAST")
        val transports = connectionInfo["transports"] as Map<String, Any>
        assertTrue(transports.containsKey("websocket"))
        assertTrue(transports.containsKey("http_sse"))
    }

    @Test
    fun `getTransportStatuses should return status for all transports`() {
        transportManager.setupDefaultTransports()

        val statuses = transportManager.getTransportStatuses()

        assertEquals(2, statuses.size)
        assertTrue(statuses.containsKey("websocket"))
        assertTrue(statuses.containsKey("http_sse"))

        val websocketStatus = statuses["websocket"]!!
        assertEquals("websocket", websocketStatus.type)
        assertEquals(false, websocketStatus.isRunning)
        assertEquals(8080, websocketStatus.port)

        val httpStatus = statuses["http_sse"]!!
        assertEquals("http_sse", httpStatus.type)
        assertEquals(false, httpStatus.isRunning)
        assertEquals(8081, httpStatus.port)
    }

    @Test
    fun `manager should prevent modifications while running`() = runTest {
        transportManager.setupDefaultTransports()
        transportManager.startAll()

        try {
            // These should throw exceptions while running
            assertThrows(IllegalStateException::class.java) {
                transportManager.setupDefaultTransports()
            }

            assertThrows(IllegalStateException::class.java) {
                transportManager.addTransport(
                    "test",
                    object : McpTransport {
                        override val isRunning: Boolean = false

                        override suspend fun start(): Result<Unit> = Result.success(Unit)

                        override suspend fun stop(): Result<Unit> = Result.success(Unit)

                        override suspend fun sendMessage(message: String): Result<Unit> =
                            Result.success(Unit)

                        override val incomingMessages = kotlinx.coroutines.flow.emptyFlow<String>()

                        override fun getConnectionInfo() = emptyMap<String, Any>()
                    },
                )
            }

            assertThrows(IllegalStateException::class.java) {
                transportManager.removeTransport("websocket")
            }
        } finally {
            transportManager.stopAll()
        }
    }

    @Test
    fun `broadcast should fail when not running`() = runTest {
        transportManager.setupDefaultTransports()

        val result = transportManager.broadcast("test message")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
    }
}
