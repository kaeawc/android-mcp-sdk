package dev.jasonpearson.mcpandroidsdk.transport

import android.util.Log
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.seconds
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * WebSocket transport implementation for MCP communication.
 * 
 * This transport creates a WebSocket server that MCP clients can connect to.
 * It supports multiple concurrent client connections and bidirectional communication.
 */
class WebSocketTransport(
    private val config: TransportConfig = TransportConfig()
) : McpTransport {

    companion object {
        private const val TAG = "WebSocketTransport"
        private const val WEBSOCKET_PATH = "/mcp"
    }

    private val _isRunning = AtomicBoolean(false)
    override val isRunning: Boolean get() = _isRunning.get()

    private var server: EmbeddedServer<*, *>? = null
    private val connectionCount = AtomicInteger(0)
    private val activeConnections = ConcurrentHashMap<String, WebSocketSession>()
    
    // Channel for incoming messages from clients
    private val messageChannel = Channel<String>(Channel.UNLIMITED)
    override val incomingMessages: Flow<String> = messageChannel.receiveAsFlow()

    override suspend fun start(): Result<Unit> = runCatching {
        if (_isRunning.compareAndSet(false, true)) {
            Log.i(TAG, "Starting WebSocket transport on ${config.host}:${config.port}")
            
            server = embeddedServer(Netty, port = config.port, host = config.host) {
                install(WebSockets) {
                    pingPeriod = 15.seconds
                    timeout = 15.seconds
                    maxFrameSize = Long.MAX_VALUE
                    masking = false
                }
                
                if (config.enableLogging) {
                    install(ContentNegotiation) {
                        json(Json {
                            prettyPrint = true
                            isLenient = true
                            ignoreUnknownKeys = true
                        })
                    }
                }
                
                routing {
                    webSocket(WEBSOCKET_PATH) {
                        handleWebSocketConnection(this)
                    }
                }
            }
            
            server?.start(wait = false)
            Log.i(TAG, "WebSocket transport started successfully")
        } else {
            Log.w(TAG, "WebSocket transport is already running")
        }
    }

    override suspend fun stop(): Result<Unit> = runCatching {
        if (_isRunning.compareAndSet(true, false)) {
            Log.i(TAG, "Stopping WebSocket transport...")
            
            // Close all active connections
            activeConnections.values.forEach { session ->
                try {
                    session.close(CloseReason(CloseReason.Codes.GOING_AWAY, "Server shutting down"))
                } catch (e: Exception) {
                    Log.w(TAG, "Error closing WebSocket session", e)
                }
            }
            activeConnections.clear()
            
            // Stop the server
            server?.stop(1000, 5000)
            server = null
            
            // Close the message channel
            messageChannel.close()
            
            connectionCount.set(0)
            Log.i(TAG, "WebSocket transport stopped successfully")
        } else {
            Log.w(TAG, "WebSocket transport is not running")
        }
    }

    override suspend fun sendMessage(message: String): Result<Unit> = runCatching {
        if (!isRunning) {
            throw IllegalStateException("Transport is not running")
        }
        
        val connectionsToRemove = mutableListOf<String>()
        
        activeConnections.forEach { (connectionId, session) ->
            try {
                session.send(Frame.Text(message))
                Log.d(TAG, "Sent message to connection $connectionId")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to send message to connection $connectionId", e)
                connectionsToRemove.add(connectionId)
            }
        }
        
        // Remove failed connections
        connectionsToRemove.forEach { connectionId ->
            activeConnections.remove(connectionId)
            connectionCount.decrementAndGet()
            Log.d(TAG, "Removed failed connection: $connectionId")
        }
        
        Log.d(TAG, "Broadcast message to ${activeConnections.size} active connections")
    }

    override fun getConnectionInfo(): Map<String, Any> {
        return mapOf(
            "type" to "websocket",
            "host" to config.host,
            "port" to config.port,
            "path" to WEBSOCKET_PATH,
            "url" to "ws://${config.host}:${config.port}$WEBSOCKET_PATH",
            "isRunning" to isRunning,
            "connectionCount" to connectionCount.get(),
            "activeConnections" to activeConnections.keys.toList()
        )
    }

    /**
     * Get current transport status
     */
    fun getStatus(): TransportStatus {
        return TransportStatus(
            type = "websocket",
            isRunning = isRunning,
            connectionCount = connectionCount.get(),
            port = config.port,
            host = config.host,
            metadata = mapOf(
                "path" to WEBSOCKET_PATH,
                "url" to "ws://${config.host}:${config.port}$WEBSOCKET_PATH",
                "activeConnections" to activeConnections.keys.toList()
            )
        )
    }

    private suspend fun handleWebSocketConnection(session: WebSocketSession) {
        val connectionId = generateConnectionId()
        activeConnections[connectionId] = session
        connectionCount.incrementAndGet()
        
        Log.i(TAG, "New WebSocket connection: $connectionId (total: ${connectionCount.get()})")
        
        try {
            for (frame in session.incoming) {
                when (frame) {
                    is Frame.Text -> {
                        val message = frame.readText()
                        Log.d(TAG, "Received message from $connectionId: $message")
                        
                        // Send message to the incoming message channel
                        messageChannel.trySend(message)
                    }
                    is Frame.Close -> {
                        Log.i(TAG, "WebSocket connection $connectionId closed: ${frame.readReason()}")
                        break
                    }
                    else -> {
                        Log.d(TAG, "Received non-text frame from $connectionId: ${frame.frameType}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error in WebSocket connection $connectionId", e)
        } finally {
            activeConnections.remove(connectionId)
            connectionCount.decrementAndGet()
            Log.i(TAG, "WebSocket connection $connectionId disconnected (remaining: ${connectionCount.get()})")
        }
    }

    private fun generateConnectionId(): String {
        return "ws_${System.currentTimeMillis()}_${(1000..9999).random()}"
    }
}