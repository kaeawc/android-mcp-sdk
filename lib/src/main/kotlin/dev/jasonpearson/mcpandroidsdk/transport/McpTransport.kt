package dev.jasonpearson.mcpandroidsdk.transport

import kotlinx.coroutines.flow.Flow

/**
 * Base interface for MCP transport implementations.
 *
 * Defines the core contract for communication between MCP clients and servers over different
 * transport protocols (WebSocket, HTTP/SSE, etc.).
 */
interface McpTransport {

    /** Check if the transport is currently running */
    val isRunning: Boolean

    /** Start the transport and begin listening for connections */
    suspend fun start(): Result<Unit>

    /** Stop the transport and close all connections */
    suspend fun stop(): Result<Unit>

    /** Send a message to connected clients */
    suspend fun sendMessage(message: String): Result<Unit>

    /** Flow of incoming messages from clients */
    val incomingMessages: Flow<String>

    /** Get transport-specific connection information */
    fun getConnectionInfo(): Map<String, Any>
}

/** Transport configuration options */
data class TransportConfig(
    val port: Int = 8080,
    val host: String = "0.0.0.0",
    val enableLogging: Boolean = true,
    val additionalOptions: Map<String, Any> = emptyMap(),
)

/** Transport status information */
data class TransportStatus(
    val type: String,
    val isRunning: Boolean,
    val connectionCount: Int,
    val port: Int? = null,
    val host: String? = null,
    val metadata: Map<String, Any> = emptyMap(),
)
