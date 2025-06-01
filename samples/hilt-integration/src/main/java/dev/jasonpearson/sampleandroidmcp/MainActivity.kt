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
import dagger.hilt.android.AndroidEntryPoint
import dev.jasonpearson.androidmcpsdk.core.McpServerManager
import dev.jasonpearson.sampleandroidmcp.ui.theme.SampleAndroidMCPTheme
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "HiltMcpActivity"
    }

    // Inject MCP dependencies
    @Inject lateinit var mcpServerManager: McpServerManager

    @Inject lateinit var mcpConfiguration: McpConfiguration

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.i(TAG, "🚀 Starting Hilt MCP Integration Sample Activity")

        enableEdgeToEdge()
        setContent {
            SampleAndroidMCPTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    HiltMcpDemo(modifier = Modifier.padding(innerPadding))
                }
            }
        }

        // Configure MCP behavior using injected dependencies
        configureHiltMcp()
    }

    private fun configureHiltMcp() {
        lifecycleScope.launch {
            try {
                Log.i(TAG, "ℹ️  MCP Server Manager injected via Hilt")
                Log.i(TAG, "ℹ️  Server info: ${mcpConfiguration.getServerInfo()}")
                Log.i(TAG, "✅ MCP Server running: ${mcpConfiguration.isServerReady()}")

                // Configure custom tools using the injected configuration
                mcpConfiguration.configureCustomTools()

                // Get transport info
                val transportInfo = mcpServerManager.getTransportInfo()
                Log.i(TAG, "ℹ️  Transport info: $transportInfo")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error configuring Hilt MCP", e)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HiltMcpDemo(modifier: Modifier = Modifier) {
    var demoText by remember { mutableStateOf("Initializing Hilt MCP Demo...") }

    LaunchedEffect(Unit) {
        demoText =
            """
            🎯 Hilt MCP Integration Demo
            
            This sample demonstrates:
            • Hilt dependency injection with MCP
            • Manual initialization via DI
            • Configuration management
            • Production-ready patterns
            
            ✅ MCP Server initialized via Hilt DI
            ✅ Custom tools configured
            ✅ Transport layer active
            
            Check the logs for detailed information!
        """
                .trimIndent()
    }

    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Card {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = "Hilt MCP Integration", style = MaterialTheme.typography.headlineMedium)

                Spacer(modifier = Modifier.height(16.dp))

                Text(text = demoText, style = MaterialTheme.typography.bodyMedium)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Card(
            colors =
                CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Key Features",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )

                Spacer(modifier = Modifier.height(8.dp))

                val features =
                    listOf(
                        "🔧 Hilt dependency injection",
                        "🚀 Manual MCP initialization",
                        "⚙️ Configuration via DI",
                        "🏗️ Production-ready patterns",
                        "🧪 Easy testing and mocking",
                        "📦 Clean separation of concerns",
                    )

                LazyColumn(modifier = Modifier.height(150.dp)) {
                    items(features) { feature ->
                        Text(
                            text = feature,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(vertical = 2.dp),
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Card(
            colors =
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Source Code",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text =
                        """
                        📁 samples/hilt-integration/
                        ├── HiltMcpApplication.kt
                        ├── di/McpModule.kt
                        ├── McpConfiguration.kt
                        └── MainActivity.kt
                        
                        See these files for complete implementation details.
                    """
                            .trimIndent(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun HiltMcpDemoPreview() {
    SampleAndroidMCPTheme { HiltMcpDemo() }
}
