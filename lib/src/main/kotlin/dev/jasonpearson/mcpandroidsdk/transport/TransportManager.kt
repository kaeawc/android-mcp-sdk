package dev.jasonpearson.mcpandroidsdk.transport

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.merge
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Transport manager that coordinates multiple transport implementations.
 * 
 * This manager can run multiple transports simultaneously (e.g., both WebSocket and HTTP/SSE)
 * and provides a unified interface for MCP communication.
 */
class TransportManager {

    companion object {
        private const val TAG = "TransportManager"
    }

    private val _isRunning = AtomicBoolean(false)
    val isRunning: Boolean get() = _isRunning.get()

    private val transports = mutableMapOf<String, McpTransport>()
    private var messageFlow: Flow<String>? = null

    /**
     * Add a transport with a given name
     */
    fun addTransport(name: String, transport: McpTransport) {
        if (isRunning) {
            throw IllegalStateException("Cannot add transports while manager is running")
        }
        transports[name] = transport
        Log.d(TAG, "Added transport: $name")
    }

    /**
     * Remove a transport by name
     */
    fun removeTransport(name: String): Boolean {
        if (isRunning) {
            throw IllegalStateException("Cannot remove transports while manager is running")
        }
        val removed = transports.remove(name) != null
        if (removed) {
            Log.d(TAG, "Removed transport: $name")
        }
        return removed
    }

    /**
     * Get a transport by name
     */
    fun getTransport(name: String): McpTransport? = transports[name]

    /**
     * Get all transport names
     */
    fun getTransportNames(): Set<String> = transports.keys.toSet()

    /**
     * Start all transports
     */
    suspend fun startAll(): Result<Unit> = runCatching {
        if (_isRunning.compareAndSet(false, true)) {
            Log.i(TAG, "Starting ${transports.size} transports...")
            
            val startResults = mutableListOf<Result<Unit>>()
            
            transports.forEach { (name, transport) ->
                try {
                    val result = transport.start()
                    startResults.add(result)
                    
                    if (result.isSuccess) {
                        Log.i(TAG, "Transport '$name' started successfully")
                    } else {
                        Log.e(TAG, "Transport '$name' failed to start: ${result.exceptionOrNull()?.message}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Exception starting transport '$name'", e)
                    startResults.add(Result.failure(e))
                }
            }
            
            // Merge all incoming message flows
            val messageFlows = transports.values.map { it.incomingMessages }
            messageFlow = if (messageFlows.isNotEmpty()) {
                merge(*messageFlows.toTypedArray())
            } else {
                null
            }
            
            // Check if any transports failed to start
            val failures = startResults.filter { it.isFailure }
            if (failures.isNotEmpty()) {
                Log.w(TAG, "${failures.size} transports failed to start")
                // Continue running with successful transports
            }
            
            Log.i(TAG, "Transport manager started with ${transports.size - failures.size}/${transports.size} transports")
        } else {
            Log.w(TAG, "Transport manager is already running")
        }
    }

    /**
     * Stop all transports
     */
    suspend fun stopAll(): Result<Unit> = runCatching {
        if (_isRunning.compareAndSet(true, false)) {
            Log.i(TAG, "Stopping ${transports.size} transports...")
            
            val stopResults = mutableListOf<Result<Unit>>()
            
            transports.forEach { (name, transport) ->
                try {
                    val result = transport.stop()
                    stopResults.add(result)
                    
                    if (result.isSuccess) {
                        Log.i(TAG, "Transport '$name' stopped successfully")
                    } else {
                        Log.e(TAG, "Transport '$name' failed to stop: ${result.exceptionOrNull()?.message}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Exception stopping transport '$name'", e)
                    stopResults.add(Result.failure(e))
                }
            }
            
            messageFlow = null
            
            val failures = stopResults.filter { it.isFailure }
            if (failures.isNotEmpty()) {
                Log.w(TAG, "${failures.size} transports failed to stop cleanly")
            }
            
            Log.i(TAG, "Transport manager stopped")
        } else {
            Log.w(TAG, "Transport manager is not running")
        }
    }

    /**
     * Send a message to all active transports
     */
    suspend fun broadcast(message: String): Result<Unit> = runCatching {
        if (!isRunning) {
            throw IllegalStateException("Transport manager is not running")
        }
        
        val sendResults = mutableListOf<Result<Unit>>()
        
        transports.forEach { (name, transport) ->
            if (transport.isRunning) {
                try {
                    val result = transport.sendMessage(message)
                    sendResults.add(result)
                    
                    if (result.isSuccess) {
                        Log.d(TAG, "Message sent via transport '$name'")
                    } else {
                        Log.w(TAG, "Failed to send message via transport '$name': ${result.exceptionOrNull()?.message}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Exception sending message via transport '$name'", e)
                    sendResults.add(Result.failure(e))
                }
            } else {
                Log.w(TAG, "Transport '$name' is not running, skipping message")
            }
        }
        
        val failures = sendResults.filter { it.isFailure }
        if (failures.isNotEmpty() && failures.size == sendResults.size) {
            throw Exception("All transports failed to send message")
        }
        
        Log.d(TAG, "Message broadcast to ${sendResults.size - failures.size}/${sendResults.size} transports")
    }

    /**
     * Get incoming messages from all transports
     */
    fun getIncomingMessages(): Flow<String>? = messageFlow

    /**
     * Get status information for all transports
     */
    fun getTransportStatuses(): Map<String, TransportStatus> {
        return transports.mapValues { (name, transport) ->
            when (transport) {
                is WebSocketTransport -> transport.getStatus()
                is HttpSseTransport -> transport.getStatus()
                else -> TransportStatus(
                    type = "unknown",
                    isRunning = transport.isRunning,
                    connectionCount = 0
                )
            }
        }
    }

    /**
     * Get comprehensive connection information
     */
    fun getConnectionInfo(): Map<String, Any> {
        return mapOf(
            "isRunning" to isRunning,
            "transportCount" to transports.size,
            "transports" to transports.mapValues { (_, transport) ->
                transport.getConnectionInfo()
            }
        )
    }

    /**
     * Create a default transport configuration with both WebSocket and HTTP/SSE
     */
    fun setupDefaultTransports(
        webSocketPort: Int = 8080,
        httpPort: Int = 8081,
        host: String = "0.0.0.0"
    ) {
        if (isRunning) {
            throw IllegalStateException("Cannot setup transports while manager is running")
        }
        
        // Add WebSocket transport
        val webSocketConfig = TransportConfig(port = webSocketPort, host = host)
        addTransport("websocket", WebSocketTransport(webSocketConfig))
        
        // Add HTTP/SSE transport
        val httpConfig = TransportConfig(port = httpPort, host = host)
        addTransport("http_sse", HttpSseTransport(httpConfig))
        
        Log.i(TAG, "Default transports configured: WebSocket on port $webSocketPort, HTTP/SSE on port $httpPort")
    }
}