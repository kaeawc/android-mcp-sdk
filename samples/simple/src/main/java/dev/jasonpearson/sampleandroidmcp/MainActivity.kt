package dev.jasonpearson.sampleandroidmcp

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import dev.jasonpearson.mcpandroidsdk.McpStartup
import dev.jasonpearson.sampleandroidmcp.ui.theme.SampleAndroidMCPTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    companion object {
        private const val TAG = "SampleMcpActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.i(TAG, "Starting MCP Android Sample App")

        enableEdgeToEdge()
        setContent {
            SampleAndroidMCPTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    McpServerDemo(modifier = Modifier.padding(innerPadding))
                }
            }
        }

        // Demonstrate MCP server functionality
        demonstrateMcpServer()
    }

    private fun demonstrateMcpServer() {
        lifecycleScope.launch {
            try {
                // Check if MCP server is initialized (should be via AndroidX Startup)
                if (McpStartup.isInitialized()) {
                    val manager = McpStartup.getManager()
                    Log.i(TAG, "MCP Server Manager available: ${manager.getMcpSdkVersion()}")
                    Log.i(TAG, "MCP Server running: ${manager.isServerRunning()}")

                    // Check if sample app configuration was applied
                    val sampleApp = application as SampleMcpApplication
                    Log.i(TAG, "Sample app MCP ready: ${sampleApp.isMcpServerReady()}")

                    // Get server info
                    val serverInfo = manager.getServerInfo()
                    Log.i(TAG, "Server info: $serverInfo")
                } else {
                    Log.w(TAG, "MCP Server not initialized")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error demonstrating MCP server", e)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun McpServerDemo(modifier: Modifier = Modifier) {
    var serverStatus by remember { mutableStateOf("Checking...") }
    var serverInfoMap by remember { mutableStateOf<Map<String, Any>>(emptyMap()) }
    var transportInfoMap by remember { mutableStateOf<Map<String, Any>>(emptyMap()) }
    var isServerRunning by remember { mutableStateOf(false) }
    var messageStatus by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        try {
            if (McpStartup.isInitialized()) {
                val manager = McpStartup.getManager()
                serverStatus = "Initialized"
                isServerRunning = manager.isServerRunning()

                val info = manager.getServerInfo()
                info?.let { mcpServerInfo ->
                    val infoMap =
                        mapOf(
                                "Name" to mcpServerInfo.name,
                                "Version" to mcpServerInfo.version,
                                "SDK Version" to mcpServerInfo.sdkVersion,
                                "Running" to mcpServerInfo.isRunning.toString(),
                                "Tools" to mcpServerInfo.toolCount.toString(),
                            )
                            .filterValues { it != null } as Map<String, Any>

                    serverInfoMap = infoMap
                }

                // Get transport information
                try {
                    val transportInfo = manager.getTransportInfo()
                    transportInfoMap = transportInfo
                } catch (e: Exception) {
                    transportInfoMap = mapOf("Error" to e.message.orEmpty())
                }
            } else {
                serverStatus = "Not Initialized"
            }
        } catch (e: Exception) {
            serverStatus = "Error: ${e.message}"
        }
    }

    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = "Android MCP Server Demo", style = MaterialTheme.typography.headlineMedium)

        Spacer(modifier = Modifier.height(16.dp))

        Card {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Server Status: $serverStatus",
                    style = MaterialTheme.typography.bodyLarge,
                )

                Text(
                    text = "Running: ${if (isServerRunning) "Yes" else "No"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color =
                        if (isServerRunning) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error,
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (serverInfoMap.isNotEmpty()) {
            Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = "Server Information", style = MaterialTheme.typography.titleMedium)

                    Spacer(modifier = Modifier.height(8.dp))

                    LazyColumn(modifier = Modifier.height(120.dp)) {
                        items(serverInfoMap.toList()) { (key, value) ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = "$key:",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    text = value.toString(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Transport Information Card
        if (transportInfoMap.isNotEmpty()) {
            Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Transport Information",
                        style = MaterialTheme.typography.titleMedium,
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    LazyColumn(modifier = Modifier.height(150.dp)) {
                        items(transportInfoMap.toList()) { (key, value) ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = "$key:",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    text = value.toString(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Transport Test Controls
        Card {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = "Transport Test", style = MaterialTheme.typography.titleMedium)

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        if (McpStartup.isInitialized()) {
                            val manager = McpStartup.getManager()
                            kotlinx.coroutines
                                .CoroutineScope(kotlinx.coroutines.Dispatchers.Main)
                                .launch {
                                    try {
                                        val testMessage =
                                            """{"jsonrpc": "2.0", "method": "ping", "id": 1, "timestamp": ${System.currentTimeMillis()}}"""
                                        val result = manager.broadcastMessage(testMessage)
                                        messageStatus =
                                            if (result.isSuccess) {
                                                "Message sent successfully"
                                            } else {
                                                "Failed: ${result.exceptionOrNull()?.message}"
                                            }
                                    } catch (e: Exception) {
                                        messageStatus = "Error: ${e.message}"
                                    }
                                }
                        } else {
                            messageStatus = "Server not initialized"
                        }
                    },
                    enabled = isServerRunning,
                ) {
                    Text("Send Test Message")
                }

                if (messageStatus.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Status: $messageStatus",
                        style = MaterialTheme.typography.bodySmall,
                        color =
                            if (messageStatus.contains("successfully"))
                                MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            colors =
                CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Transport Usage",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text =
                        "WebSocket: ws://localhost:8080/mcp\nHTTP/SSE: http://localhost:8081/mcp/\n\nUse adb port forwarding to access from workstation:\nadb forward tcp:8080 tcp:8080\nadb forward tcp:8081 tcp:8081",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun McpServerDemoPreview() {
    SampleAndroidMCPTheme { McpServerDemo() }
}
