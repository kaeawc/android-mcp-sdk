package dev.jasonpearson.mcpandroidsdk.transport

import android.util.Log
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.serialization.json.Json

/**
 * HTTP with Server-Sent Events (SSE) transport implementation for MCP communication.
 *
 * This transport provides:
 * - HTTP POST endpoint for client-to-server messages
 * - Server-Sent Events (SSE) endpoint for server-to-client messages
 * - RESTful API for MCP protocol communication
 */
class HttpSseTransport(private val config: TransportConfig = TransportConfig()) : McpTransport {

    companion object {
        private const val TAG = "HttpSseTransport"
        private const val MESSAGE_ENDPOINT = "/mcp/message"
        private const val SSE_ENDPOINT = "/mcp/events"
        private const val STATUS_ENDPOINT = "/mcp/status"
    }

    private val _isRunning = AtomicBoolean(false)
    override val isRunning: Boolean
        get() = _isRunning.get()

    private var server: EmbeddedServer<*, *>? = null
    private val connectionCount = AtomicInteger(0)
    private val sseConnections = ConcurrentHashMap<String, Channel<String>>()

    // Channel for incoming messages from clients
    private val messageChannel = Channel<String>(Channel.UNLIMITED)
    override val incomingMessages: Flow<String> = messageChannel.receiveAsFlow()

    override suspend fun start(): Result<Unit> = runCatching {
        if (_isRunning.compareAndSet(false, true)) {
            Log.i(TAG, "Starting HTTP/SSE transport on ${config.host}:${config.port}")

            server =
                embeddedServer(Netty, port = config.port, host = config.host) {
                    install(ContentNegotiation) {
                        json(
                            Json {
                                prettyPrint = true
                                isLenient = true
                                ignoreUnknownKeys = true
                            }
                        )
                    }

                    routing {
                        // HTTP POST endpoint for client messages
                        post(MESSAGE_ENDPOINT) { handleIncomingMessage(call) }

                        // Server-Sent Events endpoint for server messages
                        get(SSE_ENDPOINT) { handleSseConnection(call) }

                        // Status endpoint
                        get(STATUS_ENDPOINT) { handleStatusRequest(call) }

                        // Health check endpoint
                        get("/health") {
                            call.respond(HttpStatusCode.OK, mapOf("status" to "healthy"))
                        }
                    }
                }

            server?.start(wait = false)
            Log.i(TAG, "HTTP/SSE transport started successfully")
        } else {
            Log.w(TAG, "HTTP/SSE transport is already running")
        }
    }

    override suspend fun stop(): Result<Unit> = runCatching {
        if (_isRunning.compareAndSet(true, false)) {
            Log.i(TAG, "Stopping HTTP/SSE transport...")

            // Close all SSE connections
            sseConnections.values.forEach { channel ->
                try {
                    channel.close()
                } catch (e: Exception) {
                    Log.w(TAG, "Error closing SSE channel", e)
                }
            }
            sseConnections.clear()

            // Stop the server
            server?.stop(1000, 5000)
            server = null

            // Close the message channel
            messageChannel.close()

            connectionCount.set(0)
            Log.i(TAG, "HTTP/SSE transport stopped successfully")
        } else {
            Log.w(TAG, "HTTP/SSE transport is not running")
        }
    }

    override suspend fun sendMessage(message: String): Result<Unit> = runCatching {
        if (!isRunning) {
            throw IllegalStateException("Transport is not running")
        }

        val connectionsToRemove = mutableListOf<String>()

        sseConnections.forEach { (connectionId, channel) ->
            try {
                if (!channel.isClosedForSend) {
                    channel.trySend("data: $message\n\n")
                    Log.d(TAG, "Sent SSE message to connection $connectionId")
                } else {
                    connectionsToRemove.add(connectionId)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to send SSE message to connection $connectionId", e)
                connectionsToRemove.add(connectionId)
            }
        }

        // Remove failed connections
        connectionsToRemove.forEach { connectionId ->
            sseConnections.remove(connectionId)?.close()
            connectionCount.decrementAndGet()
            Log.d(TAG, "Removed failed SSE connection: $connectionId")
        }

        Log.d(TAG, "Broadcast SSE message to ${sseConnections.size} active connections")
    }

    override fun getConnectionInfo(): Map<String, Any> {
        return mapOf(
            "type" to "http_sse",
            "host" to config.host,
            "port" to config.port,
            "endpoints" to
                mapOf(
                    "message" to "http://${config.host}:${config.port}$MESSAGE_ENDPOINT",
                    "events" to "http://${config.host}:${config.port}$SSE_ENDPOINT",
                    "status" to "http://${config.host}:${config.port}$STATUS_ENDPOINT",
                ),
            "isRunning" to isRunning,
            "connectionCount" to connectionCount.get(),
            "activeConnections" to sseConnections.keys.toList(),
        )
    }

    /** Get current transport status */
    fun getStatus(): TransportStatus {
        return TransportStatus(
            type = "http_sse",
            isRunning = isRunning,
            connectionCount = connectionCount.get(),
            port = config.port,
            host = config.host,
            metadata =
                mapOf(
                    "endpoints" to
                        mapOf(
                            "message" to "http://${config.host}:${config.port}$MESSAGE_ENDPOINT",
                            "events" to "http://${config.host}:${config.port}$SSE_ENDPOINT",
                            "status" to "http://${config.host}:${config.port}$STATUS_ENDPOINT",
                        ),
                    "activeConnections" to sseConnections.keys.toList(),
                ),
        )
    }

    private suspend fun handleIncomingMessage(call: ApplicationCall) {
        try {
            val message = call.receiveText()
            Log.d(TAG, "Received HTTP message: $message")

            // Send message to the incoming message channel
            messageChannel.trySend(message)

            call.respond(HttpStatusCode.OK, mapOf("status" to "received"))
        } catch (e: Exception) {
            Log.e(TAG, "Error handling incoming HTTP message", e)
            call.respond(
                HttpStatusCode.BadRequest,
                mapOf("error" to "Failed to process message: ${e.message}"),
            )
        }
    }

    private suspend fun handleSseConnection(call: ApplicationCall) {
        val connectionId = generateConnectionId()
        connectionCount.incrementAndGet()

        Log.i(TAG, "New SSE connection: $connectionId (total: ${connectionCount.get()})")

        call.response.headers.append(HttpHeaders.ContentType, "text/event-stream")
        call.response.headers.append(HttpHeaders.CacheControl, "no-cache")
        call.response.headers.append(HttpHeaders.Connection, "keep-alive")
        call.response.headers.append("Access-Control-Allow-Origin", "*")

        try {
            // Create a channel for this connection
            val connectionChannel = Channel<String>(Channel.UNLIMITED)
            sseConnections[connectionId] = connectionChannel

            // Send initial connection message
            call.respondTextWriter {
                write("data: {\"type\": \"connection\", \"id\": \"$connectionId\"}\n\n")
                flush()

                // Keep the connection alive and send messages
                var lastPing = System.currentTimeMillis()

                while (
                    !connectionChannel.isClosedForReceive &&
                        sseConnections.containsKey(connectionId)
                ) {
                    try {
                        // Check for messages with timeout
                        val result = withTimeoutOrNull(1000) { connectionChannel.receive() }

                        if (result != null) {
                            write(result)
                            flush()
                        } else {
                            // Send keep-alive ping every 5 seconds
                            val now = System.currentTimeMillis()
                            if (now - lastPing > 5000) {
                                write(": ping\n\n")
                                flush()
                                lastPing = now
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "SSE connection $connectionId lost", e)
                        break
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error in SSE connection $connectionId", e)
        } finally {
            sseConnections.remove(connectionId)?.close()
            connectionCount.decrementAndGet()
            Log.i(
                TAG,
                "SSE connection $connectionId disconnected (remaining: ${connectionCount.get()})",
            )
        }
    }

    private suspend fun handleStatusRequest(call: ApplicationCall) {
        val status = getStatus()
        call.respond(HttpStatusCode.OK, status)
    }

    private fun generateConnectionId(): String {
        return "sse_${System.currentTimeMillis()}_${(1000..9999).random()}"
    }
}
