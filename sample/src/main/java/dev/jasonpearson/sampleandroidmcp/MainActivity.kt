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

                    // Start the server
                    manager.startServerAsync()
                    Log.i(TAG, "MCP Server started")

                    // Test STDIO transport (this will show the limitation)
                    val serverInfo = manager.getServerInfo()
                    Log.i(TAG, "Server info: $serverInfo")

                    // Try to get transport info
                    if (serverInfo is dev.jasonpearson.mcpandroidsdk.models.ComprehensiveServerInfo) {
                        Log.i(TAG, "Transport info: ${serverInfo.transportInfo}")
                    }
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
    var isServerRunning by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try {
            if (McpStartup.isInitialized()) {
                val manager = McpStartup.getManager()
                serverStatus = "Initialized"
                isServerRunning = manager.isServerRunning()

                val info = manager.getServerInfo()
                info?.let { mcpServerInfo ->
                    val infoMap = mapOf(
                        "Name" to mcpServerInfo.name,
                        "Version" to mcpServerInfo.version,
                        "SDK Version" to mcpServerInfo.sdkVersion,
                        "Running" to mcpServerInfo.isRunning.toString(),
                        "Initialized" to (mcpServerInfo as? dev.jasonpearson.mcpandroidsdk.models.ComprehensiveServerInfo)?.isInitialized?.toString(),
                        "Tools" to mcpServerInfo.toolCount.toString(),
                        "Transport Info" to (mcpServerInfo as? dev.jasonpearson.mcpandroidsdk.models.ComprehensiveServerInfo)?.transportInfo?.toString()
                    ).filterValues { it != null } as Map<String, Any>

                    serverInfoMap = infoMap
                }
            } else {
                serverStatus = "Not Initialized"
            }
        } catch (e: Exception) {
            serverStatus = "Error: ${e.message}"
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Android MCP Server Demo",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(16.dp))

        Card {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Server Status: $serverStatus",
                    style = MaterialTheme.typography.bodyLarge
                )

                Text(
                    text = "Running: ${if (isServerRunning) "Yes" else "No"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isServerRunning)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.error
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (serverInfoMap.isNotEmpty()) {
            Card {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Server Information",
                        style = MaterialTheme.typography.titleMedium
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    LazyColumn {
                        items(serverInfoMap.toList()) { (key, value) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "$key:",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = value.toString(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "STDIO Transport Limitation",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Android apps cannot access System.in/System.out, so STDIO transport is not feasible. " +
                            "Use WebSocket or HTTP transport instead.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun McpServerDemoPreview() {
    SampleAndroidMCPTheme {
        McpServerDemo()
    }
}
