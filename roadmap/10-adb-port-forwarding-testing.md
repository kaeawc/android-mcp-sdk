# 10 - ADB Port Forwarding Testing

## Status: `[ ]` Not Started

## Objective

Validate and test the adb port forwarding workflow for connecting external MCP clients to the
Android MCP server running on device/emulator. This includes testing connection establishment,
message routing, error handling, and performance over adb forwarded connections.

## Requirements

### Technical Requirements

- **ADB Tool**: Android Debug Bridge for port forwarding
- **Multiple Transport Protocols**: WebSocket and HTTP/SSE over forwarded ports
- **Connection Testing**: Automated scripts for connection validation
- **Performance Metrics**: Latency and throughput testing over adb
- **Error Recovery**: Testing connection drops and recovery scenarios

### Testing Scope

- **Port Forwarding Setup**: Automated port forwarding configuration
- **Transport Layer**: WebSocket and HTTP/SSE communication over forwarded ports
- **MCP Protocol**: Full MCP communication over forwarded connections
- **Multi-Client**: Multiple concurrent clients over forwarded ports
- **Network Conditions**: Testing various network scenarios and failures
- **Performance**: Latency and bandwidth testing

### Test Categories

1. **Connection Tests**: Basic port forwarding and connectivity
2. **Protocol Tests**: MCP communication over forwarded connections
3. **Performance Tests**: Latency and throughput measurements
4. **Reliability Tests**: Connection stability and recovery
5. **Multi-Client Tests**: Concurrent client connections

## Dependencies

**Must Complete First:**

- [04-websocket-transport-implementation.md](04-websocket-transport-implementation.md) - WebSocket
  transport needed
- [05-http-sse-transport-implementation.md](05-http-sse-transport-implementation.md) - HTTP/SSE
  transport needed
- [09-integration-testing-suite.md](09-integration-testing-suite.md) - Basic testing infrastructure

**Should Complete First:**

- [06-transport-integration-testing.md](06-transport-integration-testing.md) - Transport layer
  testing
- [11-mcp-client-communication-testing.md](11-mcp-client-communication-testing.md) - Client
  communication testing

## Implementation Steps

### Phase 1: Test Infrastructure Setup

#### Step 1.1: Create ADB Testing Scripts

Create `scripts/adb_testing/`:

**setup_port_forwarding.sh:**

```bash
#!/bin/bash

# Android MCP SDK - ADB Port Forwarding Setup Script
set -e

WEBSOCKET_PORT=${WEBSOCKET_PORT:-8080}
HTTP_SSE_PORT=${HTTP_SSE_PORT:-8081}
DEVICE_SERIAL=${DEVICE_SERIAL:-}

echo "Setting up ADB port forwarding for Android MCP SDK..."

# Function to check if adb is available
check_adb() {
    if ! command -v adb &> /dev/null; then
        echo "Error: adb command not found. Please install Android SDK platform-tools."
        exit 1
    fi
}

# Function to check device connection
check_device() {
    local devices=$(adb devices | grep -v "List of devices" | grep "device$" | wc -l)
    if [ "$devices" -eq 0 ]; then
        echo "Error: No Android devices/emulators connected."
        echo "Please connect a device or start an emulator."
        exit 1
    elif [ "$devices" -gt 1 ] && [ -z "$DEVICE_SERIAL" ]; then
        echo "Multiple devices detected. Please specify DEVICE_SERIAL:"
        adb devices
        exit 1
    fi
}

# Function to setup port forwarding
setup_forwarding() {
    local adb_cmd="adb"
    if [ -n "$DEVICE_SERIAL" ]; then
        adb_cmd="adb -s $DEVICE_SERIAL"
    fi
    
    echo "Setting up port forwarding..."
    
    # Forward WebSocket port
    echo "Forwarding WebSocket port: $WEBSOCKET_PORT"
    $adb_cmd forward tcp:$WEBSOCKET_PORT tcp:$WEBSOCKET_PORT
    
    # Forward HTTP/SSE port
    echo "Forwarding HTTP/SSE port: $HTTP_SSE_PORT"
    $adb_cmd forward tcp:$HTTP_SSE_PORT tcp:$HTTP_SSE_PORT
    
    echo "Port forwarding setup complete!"
}

# Function to verify forwarding
verify_forwarding() {
    local adb_cmd="adb"
    if [ -n "$DEVICE_SERIAL" ]; then
        adb_cmd="adb -s $DEVICE_SERIAL"
    fi
    
    echo "Verifying port forwarding..."
    $adb_cmd forward --list | grep -E "(tcp:$WEBSOCKET_PORT|tcp:$HTTP_SSE_PORT)"
    
    if [ $? -eq 0 ]; then
        echo "✓ Port forwarding verified"
    else
        echo "✗ Port forwarding verification failed"
        exit 1
    fi
}

# Function to test connectivity
test_connectivity() {
    echo "Testing connectivity..."
    
    # Test HTTP endpoint
    if curl -s --max-time 5 "http://localhost:$HTTP_SSE_PORT/mcp/status" > /dev/null; then
        echo "✓ HTTP/SSE endpoint accessible"
    else
        echo "✗ HTTP/SSE endpoint not accessible"
    fi
    
    # Test WebSocket endpoint (requires wscat)
    if command -v wscat &> /dev/null; then
        if timeout 5 wscat -c "ws://localhost:$WEBSOCKET_PORT/mcp" --execute 'process.exit(0)' 2>/dev/null; then
            echo "✓ WebSocket endpoint accessible"
        else
            echo "✗ WebSocket endpoint not accessible"
        fi
    else
        echo "! wscat not available for WebSocket testing (npm install -g wscat)"
    fi
}

# Main execution
main() {
    check_adb
    check_device
    setup_forwarding
    verify_forwarding
    test_connectivity
    
    echo ""
    echo "ADB port forwarding setup complete!"
    echo "WebSocket endpoint: ws://localhost:$WEBSOCKET_PORT/mcp"
    echo "HTTP/SSE endpoints:"
    echo "  - POST: http://localhost:$HTTP_SSE_PORT/mcp/message"
    echo "  - GET:  http://localhost:$HTTP_SSE_PORT/mcp/events"
    echo "  - GET:  http://localhost:$HTTP_SSE_PORT/mcp/status"
}

main "$@"
```

**cleanup_port_forwarding.sh:**

```bash
#!/bin/bash

# Android MCP SDK - ADB Port Forwarding Cleanup Script
set -e

DEVICE_SERIAL=${DEVICE_SERIAL:-}

echo "Cleaning up ADB port forwarding..."

# Function to remove port forwarding
cleanup_forwarding() {
    local adb_cmd="adb"
    if [ -n "$DEVICE_SERIAL" ]; then
        adb_cmd="adb -s $DEVICE_SERIAL"
    fi
    
    echo "Removing all port forwarding rules..."
    $adb_cmd forward --remove-all
    
    echo "✓ Port forwarding cleanup complete"
}

# Function to verify cleanup
verify_cleanup() {
    local adb_cmd="adb"
    if [ -n "$DEVICE_SERIAL" ]; then
        adb_cmd="adb -s $DEVICE_SERIAL"
    fi
    
    local forwards=$($adb_cmd forward --list | wc -l)
    if [ "$forwards" -eq 0 ]; then
        echo "✓ All port forwarding rules removed"
    else
        echo "! Some port forwarding rules still exist:"
        $adb_cmd forward --list
    fi
}

# Main execution
main() {
    cleanup_forwarding
    verify_cleanup
}

main "$@"
```

#### Step 1.2: Create Connection Test Utilities

Create `lib/src/androidTest/kotlin/dev/jasonpearson/mcpandroidsdk/adb/`:

**AdbTestUtils.kt:**

```kotlin
package dev.jasonpearson.mcpandroidsdk.adb

import kotlinx.coroutines.delay
import okhttp3.*
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.system.measureTimeMillis

object AdbTestUtils {
    
    data class ConnectionTestResult(
        val success: Boolean,
        val latencyMs: Long,
        val error: String? = null
    )
    
    data class PortForwardingStatus(
        val webSocketPort: Int,
        val httpSsePort: Int,
        val webSocketReachable: Boolean,
        val httpSseReachable: Boolean
    )
    
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()
    
    /**
     * Test HTTP/SSE endpoint connectivity and measure latency
     */
    suspend fun testHttpSseConnection(
        port: Int = 8081,
        timeoutMs: Long = 5000
    ): ConnectionTestResult {
        return try {
            val latency = measureTimeMillis {
                val request = Request.Builder()
                    .url("http://localhost:$port/mcp/status")
                    .get()
                    .build()
                
                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("HTTP request failed: ${response.code}")
                    }
                }
            }
            
            ConnectionTestResult(success = true, latencyMs = latency)
        } catch (e: Exception) {
            ConnectionTestResult(success = false, latencyMs = -1, error = e.message)
        }
    }
    
    /**
     * Test WebSocket endpoint connectivity and measure connection time
     */
    suspend fun testWebSocketConnection(
        port: Int = 8080,
        timeoutMs: Long = 5000
    ): ConnectionTestResult {
        return try {
            var connected = false
            var error: String? = null
            
            val latency = measureTimeMillis {
                val listener = object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        connected = true
                        webSocket.close(1000, "Test complete")
                    }
                    
                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        error = t.message
                    }
                }
                
                val request = Request.Builder()
                    .url("ws://localhost:$port/mcp")
                    .build()
                
                val webSocket = okHttpClient.newWebSocket(request, listener)
                
                // Wait for connection or timeout
                val startTime = System.currentTimeMillis()
                while (!connected && error == null && 
                       (System.currentTimeMillis() - startTime) < timeoutMs) {
                    delay(50)
                }
                
                webSocket.close(1000, "Test complete")
            }
            
            if (connected) {
                ConnectionTestResult(success = true, latencyMs = latency)
            } else {
                ConnectionTestResult(success = false, latencyMs = -1, error = error ?: "Connection timeout")
            }
        } catch (e: Exception) {
            ConnectionTestResult(success = false, latencyMs = -1, error = e.message)
        }
    }
    
    /**
     * Send an MCP message via HTTP and measure round-trip time
     */
    suspend fun sendMcpHttpMessage(
        message: String,
        port: Int = 8081
    ): ConnectionTestResult {
        return try {
            val latency = measureTimeMillis {
                val request = Request.Builder()
                    .url("http://localhost:$port/mcp/message")
                    .post(RequestBody.create(
                        MediaType.get("application/json"),
                        message
                    ))
                    .build()
                
                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("MCP HTTP request failed: ${response.code}")
                    }
                    
                    // Validate response format
                    val responseBody = response.body?.string()
                    if (responseBody != null) {
                        val json = JSONObject(responseBody)
                        if (!json.has("jsonrpc")) {
                            throw IOException("Invalid MCP response format")
                        }
                    }
                }
            }
            
            ConnectionTestResult(success = true, latencyMs = latency)
        } catch (e: Exception) {
            ConnectionTestResult(success = false, latencyMs = -1, error = e.message)
        }
    }
    
    /**
     * Send an MCP message via WebSocket and measure round-trip time
     */
    suspend fun sendMcpWebSocketMessage(
        message: String,
        port: Int = 8080,
        timeoutMs: Long = 5000
    ): ConnectionTestResult {
        return try {
            var responseReceived = false
            var error: String? = null
            
            val latency = measureTimeMillis {
                val listener = object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        webSocket.send(message)
                    }
                    
                    override fun onMessage(webSocket: WebSocket, text: String) {
                        try {
                            val json = JSONObject(text)
                            if (json.has("jsonrpc")) {
                                responseReceived = true
                            }
                        } catch (e: Exception) {
                            error = "Invalid JSON response: ${e.message}"
                        }
                        webSocket.close(1000, "Test complete")
                    }
                    
                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        error = t.message
                    }
                }
                
                val request = Request.Builder()
                    .url("ws://localhost:$port/mcp")
                    .build()
                
                val webSocket = okHttpClient.newWebSocket(request, listener)
                
                // Wait for response or timeout
                val startTime = System.currentTimeMillis()
                while (!responseReceived && error == null && 
                       (System.currentTimeMillis() - startTime) < timeoutMs) {
                    delay(50)
                }
                
                webSocket.close(1000, "Test complete")
            }
            
            if (responseReceived) {
                ConnectionTestResult(success = true, latencyMs = latency)
            } else {
                ConnectionTestResult(success = false, latencyMs = -1, error = error ?: "Response timeout")
            }
        } catch (e: Exception) {
            ConnectionTestResult(success = false, latencyMs = -1, error = e.message)
        }
    }
    
    /**
     * Test port forwarding status for both WebSocket and HTTP/SSE
     */
    suspend fun checkPortForwardingStatus(
        webSocketPort: Int = 8080,
        httpSsePort: Int = 8081
    ): PortForwardingStatus {
        val wsResult = testWebSocketConnection(webSocketPort)
        val httpResult = testHttpSseConnection(httpSsePort)
        
        return PortForwardingStatus(
            webSocketPort = webSocketPort,
            httpSsePort = httpSsePort,
            webSocketReachable = wsResult.success,
            httpSseReachable = httpResult.success
        )
    }
    
    /**
     * Perform comprehensive latency testing
     */
    suspend fun performLatencyTest(
        iterations: Int = 10,
        webSocketPort: Int = 8080,
        httpSsePort: Int = 8081
    ): LatencyTestResults {
        val wsLatencies = mutableListOf<Long>()
        val httpLatencies = mutableListOf<Long>()
        
        val testMessage = """
            {
                "jsonrpc": "2.0",
                "id": 1,
                "method": "tools/list",
                "params": {}
            }
        """.trimIndent()
        
        // Test WebSocket latency
        repeat(iterations) {
            val result = sendMcpWebSocketMessage(testMessage, webSocketPort)
            if (result.success) {
                wsLatencies.add(result.latencyMs)
            }
            delay(100) // Small delay between tests
        }
        
        // Test HTTP latency
        repeat(iterations) {
            val result = sendMcpHttpMessage(testMessage, httpSsePort)
            if (result.success) {
                httpLatencies.add(result.latencyMs)
            }
            delay(100) // Small delay between tests
        }
        
        return LatencyTestResults(
            webSocketLatencies = wsLatencies,
            httpSseLatencies = httpLatencies,
            iterations = iterations
        )
    }
    
    data class LatencyTestResults(
        val webSocketLatencies: List<Long>,
        val httpSseLatencies: List<Long>,
        val iterations: Int
    ) {
        val webSocketAverage: Double get() = webSocketLatencies.average()
        val webSocketMin: Long get() = webSocketLatencies.minOrNull() ?: -1
        val webSocketMax: Long get() = webSocketLatencies.maxOrNull() ?: -1
        val webSocketSuccessRate: Double get() = webSocketLatencies.size.toDouble() / iterations
        
        val httpSseAverage: Double get() = httpSseLatencies.average()
        val httpSseMin: Long get() = httpSseLatencies.minOrNull() ?: -1
        val httpSseMax: Long get() = httpSseLatencies.maxOrNull() ?: -1
        val httpSseSuccessRate: Double get() = httpSseLatencies.size.toDouble() / iterations
    }
}
```

### Phase 2: Connection Testing

#### Step 2.1: Basic Connection Tests

Create `lib/src/androidTest/kotlin/dev/jasonpearson/mcpandroidsdk/adb/AdbConnectionTest.kt`:

```kotlin
package dev.jasonpearson.mcpandroidsdk.adb

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.jasonpearson.mcpandroidsdk.McpServerManager
import dev.jasonpearson.mcpandroidsdk.testing.McpTestUtils
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*

@RunWith(AndroidJUnit4::class)
class AdbConnectionTest {
    
    private lateinit var serverManager: McpServerManager
    private lateinit var context: android.content.Context
    
    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        serverManager = McpServerManager.getInstance()
        
        runBlocking {
            serverManager.initialize(context, "ADB Test Server", "1.0.0")
            serverManager.startServer()
            McpTestUtils.waitForCondition { serverManager.isServerRunning() }
        }
    }
    
    @After
    fun teardown() {
        runBlocking {
            serverManager.stopServer()
            serverManager.shutdown()
        }
    }
    
    @Test
    fun testBasicHttpSseConnection() = runBlocking {
        val result = AdbTestUtils.testHttpSseConnection()
        
        assertTrue("HTTP/SSE connection should succeed", result.success)
        assertTrue("Latency should be reasonable", result.latencyMs < 1000)
        assertNull("No connection errors", result.error)
        
        println("HTTP/SSE connection test:")
        println("- Success: ${result.success}")
        println("- Latency: ${result.latencyMs}ms")
    }
    
    @Test
    fun testBasicWebSocketConnection() = runBlocking {
        val result = AdbTestUtils.testWebSocketConnection()
        
        assertTrue("WebSocket connection should succeed", result.success)
        assertTrue("Connection time should be reasonable", result.latencyMs < 2000)
        assertNull("No connection errors", result.error)
        
        println("WebSocket connection test:")
        println("- Success: ${result.success}")
        println("- Connection time: ${result.latencyMs}ms")
    }
    
    @Test
    fun testPortForwardingStatus() = runBlocking {
        val status = AdbTestUtils.checkPortForwardingStatus()
        
        assertTrue("WebSocket should be reachable", status.webSocketReachable)
        assertTrue("HTTP/SSE should be reachable", status.httpSseReachable)
        
        println("Port forwarding status:")
        println("- WebSocket (${status.webSocketPort}): ${status.webSocketReachable}")
        println("- HTTP/SSE (${status.httpSsePort}): ${status.httpSseReachable}")
    }
    
    @Test
    fun testMcpHttpMessage() = runBlocking {
        val testMessage = """
            {
                "jsonrpc": "2.0",
                "id": 1,
                "method": "tools/list",
                "params": {}
            }
        """.trimIndent()
        
        val result = AdbTestUtils.sendMcpHttpMessage(testMessage)
        
        assertTrue("MCP HTTP message should succeed", result.success)
        assertTrue("Round-trip time should be reasonable", result.latencyMs < 1000)
        assertNull("No message errors", result.error)
        
        println("MCP HTTP message test:")
        println("- Success: ${result.success}")
        println("- Round-trip time: ${result.latencyMs}ms")
    }
    
    @Test
    fun testMcpWebSocketMessage() = runBlocking {
        val testMessage = """
            {
                "jsonrpc": "2.0",
                "id": 1,
                "method": "tools/list",
                "params": {}
            }
        """.trimIndent()
        
        val result = AdbTestUtils.sendMcpWebSocketMessage(testMessage)
        
        assertTrue("MCP WebSocket message should succeed", result.success)
        assertTrue("Round-trip time should be reasonable", result.latencyMs < 1000)
        assertNull("No message errors", result.error)
        
        println("MCP WebSocket message test:")
        println("- Success: ${result.success}")
        println("- Round-trip time: ${result.latencyMs}ms")
    }
}
```

### Phase 3: Performance Testing

#### Step 3.1: Latency and Throughput Tests

Create `lib/src/androidTest/kotlin/dev/jasonpearson/mcpandroidsdk/adb/AdbPerformanceTest.kt`:

```kotlin
package dev.jasonpearson.mcpandroidsdk.adb

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.jasonpearson.mcpandroidsdk.McpServerManager
import dev.jasonpearson.mcpandroidsdk.testing.McpTestUtils
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*

@RunWith(AndroidJUnit4::class)
class AdbPerformanceTest {
    
    private lateinit var serverManager: McpServerManager
    private lateinit var context: android.content.Context
    
    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        serverManager = McpServerManager.getInstance()
        
        runBlocking {
            serverManager.initialize(context, "ADB Performance Test Server", "1.0.0")
            serverManager.startServer()
            McpTestUtils.waitForCondition { serverManager.isServerRunning() }
        }
    }
    
    @After
    fun teardown() {
        runBlocking {
            serverManager.stopServer()
            serverManager.shutdown()
        }
    }
    
    @Test
    fun testLatencyPerformance() = runBlocking {
        val results = AdbTestUtils.performLatencyTest(iterations = 20)
        
        // WebSocket performance assertions
        assertTrue("WebSocket success rate should be high", 
            results.webSocketSuccessRate > 0.9)
        assertTrue("WebSocket average latency should be acceptable", 
            results.webSocketAverage < 500)
        
        // HTTP/SSE performance assertions
        assertTrue("HTTP/SSE success rate should be high", 
            results.httpSseSuccessRate > 0.9)
        assertTrue("HTTP/SSE average latency should be acceptable", 
            results.httpSseAverage < 500)
        
        println("Latency Performance Results:")
        println("WebSocket:")
        println("  - Success Rate: ${(results.webSocketSuccessRate * 100).toInt()}%")
        println("  - Average: ${results.webSocketAverage.toInt()}ms")
        println("  - Min: ${results.webSocketMin}ms")
        println("  - Max: ${results.webSocketMax}ms")
        println("HTTP/SSE:")
        println("  - Success Rate: ${(results.httpSseSuccessRate * 100).toInt()}%")
        println("  - Average: ${results.httpSseAverage.toInt()}ms")
        println("  - Min: ${results.httpSseMin}ms")
        println("  - Max: ${results.httpSseMax}ms")
    }
    
    @Test
    fun testConcurrentConnections() = runBlocking {
        val concurrentClients = 10
        val results = mutableListOf<AdbTestUtils.ConnectionTestResult>()
        
        val jobs = (1..concurrentClients).map { clientId ->
            async {
                val testMessage = """
                    {
                        "jsonrpc": "2.0",
                        "id": $clientId,
                        "method": "tools/list",
                        "params": {}
                    }
                """.trimIndent()
                
                AdbTestUtils.sendMcpWebSocketMessage(testMessage)
            }
        }
        
        val connectionResults = jobs.awaitAll()
        results.addAll(connectionResults)
        
        val successCount = results.count { it.success }
        val successRate = successCount.toDouble() / concurrentClients
        val averageLatency = results.filter { it.success }.map { it.latencyMs }.average()
        
        assertTrue("Most concurrent connections should succeed", successRate > 0.8)
        assertTrue("Average latency should remain reasonable", averageLatency < 1000)
        
        println("Concurrent connections test:")
        println("- Clients: $concurrentClients")
        println("- Success rate: ${(successRate * 100).toInt()}%")
        println("- Average latency: ${averageLatency.toInt()}ms")
        println("- Successful connections: $successCount")
    }
    
    @Test
    fun testSustainedLoad() = runBlocking {
        val testDurationMs = 30000L // 30 seconds
        val requestIntervalMs = 500L // Request every 500ms
        val results = mutableListOf<AdbTestUtils.ConnectionTestResult>()
        
        val startTime = System.currentTimeMillis()
        var requestCount = 0
        
        while (System.currentTimeMillis() - startTime < testDurationMs) {
            requestCount++
            val testMessage = """
                {
                    "jsonrpc": "2.0",
                    "id": $requestCount,
                    "method": "device_info",
                    "params": {}
                }
            """.trimIndent()
            
            val result = AdbTestUtils.sendMcpHttpMessage(testMessage)
            results.add(result)
            
            delay(requestIntervalMs)
        }
        
        val successCount = results.count { it.success }
        val successRate = successCount.toDouble() / results.size
        val averageLatency = results.filter { it.success }.map { it.latencyMs }.average()
        
        assertTrue("Sustained load success rate should be high", successRate > 0.95)
        assertTrue("Average latency should remain stable", averageLatency < 1000)
        
        println("Sustained load test:")
        println("- Duration: ${testDurationMs / 1000}s")
        println("- Total requests: ${results.size}")
        println("- Success rate: ${(successRate * 100).toInt()}%")
        println("- Average latency: ${averageLatency.toInt()}ms")
    }
}
```

### Phase 4: Reliability Testing

#### Step 4.1: Connection Recovery Tests

Create `lib/src/androidTest/kotlin/dev/jasonpearson/mcpandroidsdk/adb/AdbReliabilityTest.kt`:

```kotlin
package dev.jasonpearson.mcpandroidsdk.adb

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.jasonpearson.mcpandroidsdk.McpServerManager
import dev.jasonpearson.mcpandroidsdk.testing.McpTestUtils
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*

@RunWith(AndroidJUnit4::class)
class AdbReliabilityTest {
    
    private lateinit var serverManager: McpServerManager
    private lateinit var context: android.content.Context
    
    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        serverManager = McpServerManager.getInstance()
        
        runBlocking {
            serverManager.initialize(context, "ADB Reliability Test Server", "1.0.0")
            serverManager.startServer()
            McpTestUtils.waitForCondition { serverManager.isServerRunning() }
        }
    }
    
    @After
    fun teardown() {
        runBlocking {
            serverManager.stopServer()
            serverManager.shutdown()
        }
    }
    
    @Test
    fun testServerRestartRecovery() = runBlocking {
        // Verify initial connection
        val initialResult = AdbTestUtils.testWebSocketConnection()
        assertTrue("Initial connection should work", initialResult.success)
        
        // Stop and restart server
        serverManager.stopServer()
        delay(1000)
        
        // Verify connection fails while stopped
        val stoppedResult = AdbTestUtils.testWebSocketConnection(timeoutMs = 2000)
        assertFalse("Connection should fail when server is stopped", stoppedResult.success)
        
        // Restart server
        serverManager.startServer()
        McpTestUtils.waitForCondition { serverManager.isServerRunning() }
        delay(2000) // Additional time for transport to initialize
        
        // Verify connection recovery
        val recoveredResult = AdbTestUtils.testWebSocketConnection()
        assertTrue("Connection should recover after restart", recoveredResult.success)
        
        println("Server restart recovery test:")
        println("- Initial connection: ${initialResult.success}")
        println("- During stop: ${stoppedResult.success}")
        println("- After restart: ${recoveredResult.success}")
    }
    
    @Test
    fun testMultipleClientReconnection() = runBlocking {
        val clientCount = 5
        val testMessage = """
            {
                "jsonrpc": "2.0",
                "id": 1,
                "method": "tools/list",
                "params": {}
            }
        """.trimIndent()
        
        // Test initial connections
        val initialResults = (1..clientCount).map {
            async { AdbTestUtils.sendMcpWebSocketMessage(testMessage) }
        }.awaitAll()
        
        val initialSuccessCount = initialResults.count { it.success }
        assertTrue("Most initial connections should succeed", 
            initialSuccessCount >= clientCount * 0.8)
        
        // Simulate connection disruption by restarting server
        serverManager.stopServer()
        delay(2000)
        serverManager.startServer()
        McpTestUtils.waitForCondition { serverManager.isServerRunning() }
        delay(3000) // Additional stabilization time
        
        // Test reconnections
        val reconnectResults = (1..clientCount).map {
            async { AdbTestUtils.sendMcpWebSocketMessage(testMessage) }
        }.awaitAll()
        
        val reconnectSuccessCount = reconnectResults.count { it.success }
        assertTrue("Most reconnections should succeed", 
            reconnectSuccessCount >= clientCount * 0.8)
        
        println("Multiple client reconnection test:")
        println("- Client count: $clientCount")
        println("- Initial success: $initialSuccessCount")
        println("- Reconnect success: $reconnectSuccessCount")
    }
    
    @Test
    fun testLongRunningConnection() = runBlocking {
        val testDurationMs = 60000L // 1 minute
        val pingIntervalMs = 5000L // Ping every 5 seconds
        var consecutiveFailures = 0
        var totalPings = 0
        var successfulPings = 0
        
        val startTime = System.currentTimeMillis()
        
        while (System.currentTimeMillis() - startTime < testDurationMs) {
            totalPings++
            
            val pingMessage = """
                {
                    "jsonrpc": "2.0",
                    "id": $totalPings,
                    "method": "tools/list",
                    "params": {}
                }
            """.trimIndent()
            
            val result = AdbTestUtils.sendMcpWebSocketMessage(pingMessage, timeoutMs = 3000)
            
            if (result.success) {
                successfulPings++
                consecutiveFailures = 0
            } else {
                consecutiveFailures++
                
                // Fail test if too many consecutive failures
                if (consecutiveFailures > 3) {
                    fail("Too many consecutive connection failures: $consecutiveFailures")
                }
            }
            
            delay(pingIntervalMs)
        }
        
        val successRate = successfulPings.toDouble() / totalPings
        assertTrue("Long-running connection success rate should be high", successRate > 0.9)
        
        println("Long-running connection test:")
        println("- Duration: ${testDurationMs / 1000}s")
        println("- Total pings: $totalPings")
        println("- Successful pings: $successfulPings")
        println("- Success rate: ${(successRate * 100).toInt()}%")
        println("- Max consecutive failures: $consecutiveFailures")
    }
}
```

## Verification Steps

### Automated Verification

#### Step V1: Setup Port Forwarding

```bash
# Make scripts executable
chmod +x scripts/adb_testing/*.sh

# Setup port forwarding
./scripts/adb_testing/setup_port_forwarding.sh
```

#### Step V2: Run Connection Tests

```bash
./gradlew :lib:connectedAndroidTest --tests "*AdbConnectionTest"
```

#### Step V3: Run Performance Tests

```bash
./gradlew :lib:connectedAndroidTest --tests "*AdbPerformanceTest"
```

#### Step V4: Run Reliability Tests

```bash
./gradlew :lib:connectedAndroidTest --tests "*AdbReliabilityTest"
```

#### Step V5: Cleanup Port Forwarding

```bash
./scripts/adb_testing/cleanup_port_forwarding.sh
```

### Manual Verification

#### Step M1: Manual Connection Testing

```bash
# Test WebSocket connection with wscat
npx wscat -c ws://localhost:8080/mcp

# Send test message
{"jsonrpc":"2.0","id":1,"method":"tools/list","params":{}}

# Test HTTP endpoint with curl
curl -X POST http://localhost:8081/mcp/message \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/list","params":{}}'

# Test SSE endpoint
curl -N -H "Accept: text/event-stream" http://localhost:8081/mcp/events
```

#### Step M2: Verify Port Forwarding Status

```bash
# Check active port forwards
adb forward --list

# Should show:
# [device serial] tcp:8080 tcp:8080
# [device serial] tcp:8081 tcp:8081
```

#### Step M3: Test Multiple Devices

```bash
# Test with specific device
DEVICE_SERIAL=emulator-5554 ./scripts/adb_testing/setup_port_forwarding.sh

# Run tests on specific device
./gradlew :lib:connectedAndroidTest -PtestDeviceSerial=emulator-5554
```

#### Step M4: Performance Benchmarking

```bash
# Test latency with multiple iterations
for i in {1..10}; do
  time curl -s -X POST http://localhost:8081/mcp/message \
    -H "Content-Type: application/json" \
    -d '{"jsonrpc":"2.0","id":'$i',"method":"tools/list","params":{}}'
done
```

## Success Criteria

### Functional Criteria

- [ ] Port forwarding setup scripts work reliably
- [ ] WebSocket connections establish successfully over adb
- [ ] HTTP/SSE connections work properly over forwarded ports
- [ ] MCP protocol communication functions correctly
- [ ] Multiple concurrent clients supported
- [ ] Connection recovery after server restart

### Performance Criteria

- [ ] WebSocket connection time <2 seconds
- [ ] HTTP/SSE request latency <500ms average
- [ ] Support for >10 concurrent clients
- [ ] > 95% success rate under normal conditions
- [ ] Stable performance over extended periods

### Reliability Criteria

- [ ] Automatic recovery from connection drops
- [ ] Graceful handling of server restarts
- [ ] Error messages provide clear diagnostics
- [ ] Port forwarding cleanup works properly
- [ ] Multi-device support functions correctly

## Resources

### ADB Documentation

- [Android Debug Bridge](https://developer.android.com/studio/command-line/adb)
- [ADB Port Forwarding](https://developer.android.com/studio/command-line/adb#forwardports)

### Testing Tools

- [wscat WebSocket Client](https://github.com/websockets/wscat)
- [curl HTTP Client](https://curl.se/)
- [OkHttp Testing](https://square.github.io/okhttp/4.x/okhttp/okhttp3/-ok-http-client/)

### Network Testing

- [Android Network Testing](https://developer.android.com/training/testing/ui-testing/espresso-testing)
- [WebSocket Testing Patterns](https://developer.android.com/guide/topics/connectivity/cronet/testing)