# 13 - Sample App Enhancement

## Status: `[ ]` Not Started

## Objective

Enhance the sample Android application to provide comprehensive examples and demonstrations of the
Android MCP SDK capabilities. The sample app should serve as both a working reference implementation
and a testing tool for developers integrating the MCP SDK.

## Requirements

### Technical Requirements

- **Modern Android UI**: Material Design 3 with Jetpack Compose
- **Comprehensive Examples**: Demonstrating all MCP SDK features
- **Interactive Testing**: Real-time tool execution and result display
- **Developer-Friendly**: Clear code examples and documentation
- **Production-Ready**: Following Android best practices and architecture patterns

### Feature Requirements

- **Server Management**: Start/stop MCP server with visual feedback
- **Built-in Tools**: Interactive execution of all built-in Android tools
- **Custom Tools**: Examples of adding and using custom tools
- **Resources Management**: Demonstration of file and dynamic resources
- **Prompts System**: Examples of creating and using prompts
- **Transport Testing**: WebSocket and HTTP/SSE connectivity testing
- **Performance Monitoring**: Real-time server performance metrics
- **Logging System**: Comprehensive logging with filtering options

### Architecture Requirements

- **MVVM Pattern**: Using ViewModel and LiveData/StateFlow
- **Dependency Injection**: Using Hilt for dependency management
- **Navigation**: Jetpack Navigation with proper deep linking
- **State Management**: Proper handling of configuration changes
- **Error Handling**: Graceful error handling with user feedback

## Dependencies

**Must Complete First:**

- [09-integration-testing-suite.md](09-integration-testing-suite.md) - Testing infrastructure
- [12-builtin-tools-validation.md](12-builtin-tools-validation.md) - Tools validation

**Should Complete First:**

- [10-adb-port-forwarding-testing.md](10-adb-port-forwarding-testing.md) - ADB testing
- [11-mcp-client-communication-testing.md](11-mcp-client-communication-testing.md) - Client
  communication

## Implementation Steps

### Phase 1: Modern UI Foundation

#### Step 1.1: Update Sample App Dependencies

Update `samples/simple/build.gradle.kts`:

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("kotlin-kapt")
    id("dagger.hilt.android.plugin")
    id("kotlin-parcelize")
}

android {
    namespace = "dev.jasonpearson.mcpandroidsdk.sample"
    compileSdk = 34

    defaultConfig {
        applicationId = "dev.jasonpearson.mcpandroidsdk.sample"
        minSdk = 21
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    
    kotlinOptions {
        jvmTarget = "1.8"
    }
    
    buildFeatures {
        compose = true
    }
    
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }
    
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(project(":core"))
    
    // Core AndroidX
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    
    // Compose BOM
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    
    // ViewModel and LiveData
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
    
    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.6")
    
    // Hilt Dependency Injection
    implementation("com.google.dagger:hilt-android:2.48")
    implementation("androidx.hilt:hilt-navigation-compose:1.1.0")
    kapt("com.google.dagger:hilt-compiler:2.48")
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    
    // JSON handling
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")
    
    // Networking for testing
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    
    // Timber for logging
    implementation("com.jakewharton.timber:timber:5.0.1")
    
    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.02.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
```

#### Step 1.2: Create App Architecture Foundation

Create `samples/simple/src/main/kotlin/dev/jasonpearson/mcpandroidsdk/sample/`:

**SampleApplication.kt:**

```kotlin
package dev.jasonpearson.mcpandroidsdk.sample

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

@HiltAndroidApp
class SampleApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        
        // Initialize Timber for logging
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        
        Timber.i("Android MCP SDK Sample App initialized")
    }
}
```

**MainActivity.kt:**

```kotlin
package dev.jasonpearson.mcpandroidsdk.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import dagger.hilt.android.AndroidEntryPoint
import dev.jasonpearson.mcpandroidsdk.sample.ui.theme.McpSampleTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        setContent {
            McpSampleTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    McpSampleApp()
                }
            }
        }
    }
}
```

#### Step 1.3: Create Navigation and Theme System

Create `samples/simple/src/main/kotlin/dev/jasonpearson/mcpandroidsdk/sample/ui/`:

**McpSampleApp.kt:**

```kotlin
package dev.jasonpearson.mcpandroidsdk.sample.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.jasonpearson.mcpandroidsdk.sample.R
import dev.jasonpearson.mcpandroidsdk.sample.ui.screens.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun McpSampleApp() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    
    val bottomNavItems = listOf(
        BottomNavItem(
            route = "server",
            icon = Icons.Default.Settings,
            labelRes = R.string.nav_server
        ),
        BottomNavItem(
            route = "tools",
            icon = Icons.Default.Build,
            labelRes = R.string.nav_tools
        ),
        BottomNavItem(
            route = "resources",
            icon = Icons.Default.Folder,
            labelRes = R.string.nav_resources
        ),
        BottomNavItem(
            route = "testing",
            icon = Icons.Default.NetworkCheck,
            labelRes = R.string.nav_testing
        ),
        BottomNavItem(
            route = "logs",
            icon = Icons.Default.Article,
            labelRes = R.string.nav_logs
        )
    )
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("MCP SDK Sample") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },
        bottomBar = {
            NavigationBar {
                bottomNavItems.forEach { item ->
                    NavigationBarItem(
                        icon = { Icon(item.icon, contentDescription = null) },
                        label = { Text(stringResource(item.labelRes)) },
                        selected = currentDestination?.hierarchy?.any { it.route == item.route } == true,
                        onClick = {
                            navController.navigate(item.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "server",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("server") {
                ServerManagementScreen(viewModel = hiltViewModel())
            }
            composable("tools") {
                ToolsScreen(viewModel = hiltViewModel())
            }
            composable("resources") {
                ResourcesScreen(viewModel = hiltViewModel())
            }
            composable("testing") {
                TransportTestingScreen(viewModel = hiltViewModel())
            }
            composable("logs") {
                LogsScreen(viewModel = hiltViewModel())
            }
        }
    }
}

data class BottomNavItem(
    val route: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val labelRes: Int
)
```

### Phase 2: Server Management Screen

#### Step 2.1: Server Management ViewModel

Create
`samples/simple/src/main/kotlin/dev/jasonpearson/mcpandroidsdk/sample/viewmodel/ServerViewModel.kt`:

```kotlin
package dev.jasonpearson.mcpandroidsdk.sample.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.jasonpearson.mcpandroidsdk.McpServerManager
import dev.jasonpearson.mcpandroidsdk.McpStartup
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class ServerViewModel @Inject constructor(
    application: Application
) : AndroidViewModel(application) {
    
    private val serverManager = McpServerManager.getInstance()
    
    private val _serverState = MutableStateFlow(ServerState())
    val serverState = _serverState.asStateFlow()
    
    private val _serverInfo = MutableStateFlow<ServerInfo?>(null)
    val serverInfo = _serverInfo.asStateFlow()
    
    private val _performanceMetrics = MutableStateFlow(PerformanceMetrics())
    val performanceMetrics = _performanceMetrics.asStateFlow()
    
    init {
        updateServerState()
        startPerformanceMonitoring()
    }
    
    fun initializeServer(serverName: String, serverVersion: String) {
        viewModelScope.launch {
            _serverState.value = _serverState.value.copy(isLoading = true, error = null)
            
            try {
                val result = serverManager.initialize(
                    context = getApplication(),
                    serverName = serverName,
                    serverVersion = serverVersion
                )
                
                if (result.isSuccess) {
                    updateServerState()
                    updateServerInfo()
                    Timber.i("Server initialized successfully")
                } else {
                    _serverState.value = _serverState.value.copy(
                        isLoading = false,
                        error = "Failed to initialize server"
                    )
                }
            } catch (e: Exception) {
                _serverState.value = _serverState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Unknown error"
                )
                Timber.e(e, "Failed to initialize server")
            }
        }
    }
    
    fun startServer() {
        viewModelScope.launch {
            _serverState.value = _serverState.value.copy(isLoading = true, error = null)
            
            try {
                val result = serverManager.startServer()
                
                if (result.isSuccess) {
                    updateServerState()
                    Timber.i("Server started successfully")
                } else {
                    _serverState.value = _serverState.value.copy(
                        isLoading = false,
                        error = "Failed to start server"
                    )
                }
            } catch (e: Exception) {
                _serverState.value = _serverState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Unknown error"
                )
                Timber.e(e, "Failed to start server")
            }
        }
    }
    
    fun stopServer() {
        viewModelScope.launch {
            _serverState.value = _serverState.value.copy(isLoading = true, error = null)
            
            try {
                val result = serverManager.stopServer()
                
                if (result.isSuccess) {
                    updateServerState()
                    Timber.i("Server stopped successfully")
                } else {
                    _serverState.value = _serverState.value.copy(
                        isLoading = false,
                        error = "Failed to stop server"
                    )
                }
            } catch (e: Exception) {
                _serverState.value = _serverState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Unknown error"
                )
                Timber.e(e, "Failed to stop server")
            }
        }
    }
    
    fun clearError() {
        _serverState.value = _serverState.value.copy(error = null)
    }
    
    private fun updateServerState() {
        _serverState.value = _serverState.value.copy(
            isInitialized = serverManager.isInitialized(),
            isRunning = serverManager.isServerRunning(),
            isLoading = false
        )
    }
    
    private fun updateServerInfo() {
        try {
            val info = serverManager.getServerInfo()
            _serverInfo.value = ServerInfo(
                name = info.name,
                version = info.version,
                transportInfo = serverManager.getTransportInfo(),
                availableTools = serverManager.getAvailableTools(),
                sdkVersion = serverManager.getMcpSdkVersion()
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to update server info")
        }
    }
    
    private fun startPerformanceMonitoring() {
        viewModelScope.launch {
            while (true) {
                if (serverManager.isServerRunning()) {
                    updatePerformanceMetrics()
                }
                kotlinx.coroutines.delay(2000) // Update every 2 seconds
            }
        }
    }
    
    private fun updatePerformanceMetrics() {
        try {
            val runtime = Runtime.getRuntime()
            val totalMemory = runtime.totalMemory()
            val freeMemory = runtime.freeMemory()
            val usedMemory = totalMemory - freeMemory
            val maxMemory = runtime.maxMemory()
            
            _performanceMetrics.value = PerformanceMetrics(
                usedMemoryMB = usedMemory / (1024 * 1024),
                totalMemoryMB = totalMemory / (1024 * 1024),
                maxMemoryMB = maxMemory / (1024 * 1024),
                memoryUsagePercent = (usedMemory.toDouble() / maxMemory * 100).toFloat(),
                uptime = if (serverManager.isServerRunning()) 
                    System.currentTimeMillis() - startTime else 0
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to update performance metrics")
        }
    }
    
    companion object {
        private var startTime = System.currentTimeMillis()
    }
}

data class ServerState(
    val isInitialized: Boolean = false,
    val isRunning: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null
)

data class ServerInfo(
    val name: String,
    val version: String,
    val transportInfo: String,
    val availableTools: List<String>,
    val sdkVersion: String
)

data class PerformanceMetrics(
    val usedMemoryMB: Long = 0,
    val totalMemoryMB: Long = 0,
    val maxMemoryMB: Long = 0,
    val memoryUsagePercent: Float = 0f,
    val uptime: Long = 0
)
```

#### Step 2.2: Server Management Screen

Create
`samples/simple/src/main/kotlin/dev/jasonpearson/mcpandroidsdk/sample/ui/screens/ServerManagementScreen.kt`:

```kotlin
package dev.jasonpearson.mcpandroidsdk.sample.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.jasonpearson.mcpandroidsdk.sample.viewmodel.ServerViewModel
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerManagementScreen(
    viewModel: ServerViewModel,
    modifier: Modifier = Modifier
) {
    val serverState by viewModel.serverState.collectAsStateWithLifecycle()
    val serverInfo by viewModel.serverInfo.collectAsStateWithLifecycle()
    val performanceMetrics by viewModel.performanceMetrics.collectAsStateWithLifecycle()
    
    var serverName by remember { mutableStateOf("Android MCP Sample Server") }
    var serverVersion by remember { mutableStateOf("1.0.0") }
    var showInitDialog by remember { mutableStateOf(false) }
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Server Status Card
        ServerStatusCard(
            serverState = serverState,
            onStartClick = { viewModel.startServer() },
            onStopClick = { viewModel.stopServer() },
            onInitializeClick = { showInitDialog = true }
        )
        
        // Server Information Card
        if (serverInfo != null) {
            ServerInfoCard(serverInfo = serverInfo!!)
        }
        
        // Performance Metrics Card
        if (serverState.isRunning) {
            PerformanceMetricsCard(performanceMetrics = performanceMetrics)
        }
        
        // Error Display
        if (serverState.error != null) {
            ErrorCard(
                error = serverState.error!!,
                onDismiss = { viewModel.clearError() }
            )
        }
    }
    
    // Initialize Server Dialog
    if (showInitDialog) {
        InitializeServerDialog(
            serverName = serverName,
            serverVersion = serverVersion,
            onServerNameChange = { serverName = it },
            onServerVersionChange = { serverVersion = it },
            onConfirm = {
                viewModel.initializeServer(serverName, serverVersion)
                showInitDialog = false
            },
            onDismiss = { showInitDialog = false }
        )
    }
}

@Composable
private fun ServerStatusCard(
    serverState: ServerState,
    onStartClick: () -> Unit,
    onStopClick: () -> Unit,
    onInitializeClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when {
                serverState.isRunning -> MaterialTheme.colorScheme.primaryContainer
                serverState.isInitialized -> MaterialTheme.colorScheme.secondaryContainer
                else -> MaterialTheme.colorScheme.errorContainer
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = when {
                        serverState.isRunning -> Icons.Default.CheckCircle
                        serverState.isInitialized -> Icons.Default.Warning
                        else -> Icons.Default.Error
                    },
                    contentDescription = null
                )
                Text(
                    text = "Server Status",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Text(
                text = when {
                    serverState.isRunning -> "Server is running and ready for connections"
                    serverState.isInitialized -> "Server is initialized but not running"
                    else -> "Server is not initialized"
                }
            )
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!serverState.isInitialized) {
                    Button(
                        onClick = onInitializeClick,
                        enabled = !serverState.isLoading
                    ) {
                        if (serverState.isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp))
                        } else {
                            Text("Initialize")
                        }
                    }
                } else {
                    if (!serverState.isRunning) {
                        Button(
                            onClick = onStartClick,
                            enabled = !serverState.isLoading
                        ) {
                            if (serverState.isLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp))
                            } else {
                                Text("Start Server")
                            }
                        }
                    } else {
                        Button(
                            onClick = onStopClick,
                            enabled = !serverState.isLoading,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            if (serverState.isLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp))
                            } else {
                                Text("Stop Server")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ServerInfoCard(serverInfo: ServerInfo) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Server Information",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            InfoRow("Name", serverInfo.name)
            InfoRow("Version", serverInfo.version)
            InfoRow("SDK Version", serverInfo.sdkVersion)
            InfoRow("Available Tools", "${serverInfo.availableTools.size} tools")
            
            Text(
                text = "Transport Info:",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = serverInfo.transportInfo,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun PerformanceMetricsCard(performanceMetrics: PerformanceMetrics) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Performance Metrics",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            InfoRow("Memory Usage", "${performanceMetrics.usedMemoryMB}MB / ${performanceMetrics.maxMemoryMB}MB")
            InfoRow("Memory %", "${performanceMetrics.memoryUsagePercent.roundToInt()}%")
            InfoRow("Uptime", formatUptime(performanceMetrics.uptime))
            
            LinearProgressIndicator(
                progress = performanceMetrics.memoryUsagePercent / 100f,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun ErrorCard(
    error: String,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Error,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = error,
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Dismiss"
                )
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun InitializeServerDialog(
    serverName: String,
    serverVersion: String,
    onServerNameChange: (String) -> Unit,
    onServerVersionChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Initialize MCP Server") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = serverName,
                    onValueChange = onServerNameChange,
                    label = { Text("Server Name") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = serverVersion,
                    onValueChange = onServerVersionChange,
                    label = { Text("Server Version") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Initialize")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private fun formatUptime(uptimeMs: Long): String {
    val seconds = uptimeMs / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    
    return when {
        hours > 0 -> "${hours}h ${minutes % 60}m"
        minutes > 0 -> "${minutes}m ${seconds % 60}s"
        else -> "${seconds}s"
    }
}
```

### Phase 3: Tools Testing Screen

#### Step 3.1: Tools ViewModel

Create
`samples/simple/src/main/kotlin/dev/jasonpearson/mcpandroidsdk/sample/viewmodel/ToolsViewModel.kt`:

```kotlin
package dev.jasonpearson.mcpandroidsdk.sample.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.jasonpearson.mcpandroidsdk.McpServerManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import kotlin.system.measureTimeMillis

@HiltViewModel
class ToolsViewModel @Inject constructor() : ViewModel() {
    
    private val serverManager = McpServerManager.getInstance()
    
    private val _toolsState = MutableStateFlow(ToolsState())
    val toolsState = _toolsState.asStateFlow()
    
    private val _executionResults = MutableStateFlow<Map<String, ToolExecutionResult>>(emptyMap())
    val executionResults = _executionResults.asStateFlow()
    
    init {
        loadAvailableTools()
    }
    
    fun loadAvailableTools() {
        viewModelScope.launch {
            try {
                val tools = serverManager.getAvailableTools()
                _toolsState.value = _toolsState.value.copy(
                    availableTools = tools,
                    isLoading = false,
                    error = null
                )
            } catch (e: Exception) {
                _toolsState.value = _toolsState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to load tools"
                )
                Timber.e(e, "Failed to load available tools")
            }
        }
    }
    
    fun executeTool(toolName: String, arguments: Map<String, Any> = emptyMap()) {
        viewModelScope.launch {
            val currentResults = _executionResults.value.toMutableMap()
            
            // Mark tool as executing
            currentResults[toolName] = ToolExecutionResult(
                toolName = toolName,
                isExecuting = true,
                arguments = arguments
            )
            _executionResults.value = currentResults
            
            try {
                val executionTime = measureTimeMillis {
                    val result = serverManager.executeAndroidTool(toolName, arguments)
                    
                    currentResults[toolName] = ToolExecutionResult(
                        toolName = toolName,
                        isExecuting = false,
                        success = result.success,
                        result = result.result,
                        error = result.error,
                        arguments = arguments,
                        executionTimeMs = 0 // Will be set after measureTimeMillis
                    )
                }
                
                // Update with execution time
                currentResults[toolName] = currentResults[toolName]!!.copy(
                    executionTimeMs = executionTime
                )
                
                _executionResults.value = currentResults
                
                Timber.i("Tool $toolName executed in ${executionTime}ms")
                
            } catch (e: Exception) {
                currentResults[toolName] = ToolExecutionResult(
                    toolName = toolName,
                    isExecuting = false,
                    success = false,
                    error = e.message ?: "Execution failed",
                    arguments = arguments
                )
                _executionResults.value = currentResults
                
                Timber.e(e, "Failed to execute tool $toolName")
            }
        }
    }
    
    fun clearResults() {
        _executionResults.value = emptyMap()
    }
    
    fun clearError() {
        _toolsState.value = _toolsState.value.copy(error = null)
    }
}

data class ToolsState(
    val availableTools: List<String> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

data class ToolExecutionResult(
    val toolName: String,
    val isExecuting: Boolean = false,
    val success: Boolean = false,
    val result: String = "",
    val error: String? = null,
    val arguments: Map<String, Any> = emptyMap(),
    val executionTimeMs: Long = 0,
    val timestamp: Long = System.currentTimeMillis()
)
```

## Verification Steps

### Automated Verification

#### Step V1: Build Sample App

```bash
./gradlew :samples:simple:assembleDebug
```

#### Step V2: Run Sample App Tests

```bash
./gradlew :samples:simple:testDebugUnitTest
./gradlew :samples:simple:connectedAndroidTest
```

#### Step V3: Install and Test

```bash
./gradlew :samples:simple:installDebug
```

### Manual Verification

#### Step M1: UI/UX Testing

- [ ] App launches successfully
- [ ] Navigation between screens works
- [ ] Server management controls function
- [ ] Tools execute and display results
- [ ] Error handling shows appropriate messages
- [ ] Performance metrics update in real-time

#### Step M2: Feature Testing

- [ ] Initialize server with custom parameters
- [ ] Start/stop server functionality
- [ ] Execute all built-in tools
- [ ] View formatted tool results
- [ ] Test error scenarios
- [ ] Monitor performance metrics

#### Step M3: Integration Testing

- [ ] Connect external MCP client to sample app server
- [ ] Verify adb port forwarding works
- [ ] Test WebSocket and HTTP/SSE connectivity
- [ ] Validate MCP protocol compliance

## Success Criteria

### Functional Criteria

- [ ] Complete sample app with modern Android UI
- [ ] All MCP SDK features demonstrated
- [ ] Interactive testing of tools and resources
- [ ] Real-time server monitoring
- [ ] Comprehensive error handling

### UI/UX Criteria

- [ ] Material Design 3 implementation
- [ ] Responsive design for different screen sizes
- [ ] Intuitive navigation and user flow
- [ ] Clear feedback for user actions
- [ ] Accessibility support

### Documentation Criteria

- [ ] In-app help and examples
- [ ] Code comments and documentation
- [ ] README with setup instructions
- [ ] Developer guide for customization

## Resources

### Android Development

- [Jetpack Compose Documentation](https://developer.android.com/jetpack/compose)
- [Material Design 3](https://m3.material.io/)
- [Android Architecture Components](https://developer.android.com/topic/libraries/architecture)
- [Hilt Dependency Injection](https://developer.android.com/training/dependency-injection/hilt-android)

### UI/UX Design

- [Material Design Guidelines](https://material.io/design)
- [Android Design Patterns](https://developer.android.com/design)
- [Accessibility Guidelines](https://developer.android.com/guide/topics/ui/accessibility)
