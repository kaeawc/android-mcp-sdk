# Usage Guide

This guide covers common usage patterns and provides comprehensive examples for working with the
Android MCP SDK.

## Basic Server Operations

### Starting and Stopping the Server

```kotlin
val manager = McpStartup.getManager()

// Start server asynchronously (recommended)
manager.startServerAsync()

// Start server with coroutines for error handling
lifecycleScope.launch {
    try {
        manager.startServer().getOrThrow()
        Log.d("MCP", "Server started successfully")
    } catch (e: Exception) {
        Log.e("MCP", "Failed to start server", e)
    }
}

// Stop server
lifecycleScope.launch {
    manager.stopServer().getOrThrow()
    Log.d("MCP", "Server stopped")
}

// Check server status
val isRunning = manager.isServerRunning()
Log.d("MCP", "Server running: $isRunning")
```

### Server Information

```kotlin
val manager = McpStartup.getManager()

// Get SDK version
val sdkVersion = manager.getMcpSdkVersion()
Log.d("MCP", "SDK Version: $sdkVersion")

// Get transport information
val transportInfo = manager.getTransportInfo()
Log.d("MCP", "Transport info: $transportInfo")

// Check if MCP SDK integration is available
val hasIntegration = manager.hasSDKIntegration()
Log.d("MCP", "SDK Integration available: $hasIntegration")
```

## Adding Custom Tools

Tools are functions that AI models can call to perform actions or retrieve information.

### Simple Tools

For basic tools with minimal configuration:

```kotlin
val manager = McpStartup.getManager()

// Add a simple calculation tool
manager.addSimpleTool(
    name = "calculate_sum",
    description = "Calculate the sum of two numbers",
    parameters = mapOf(
        "a" to "number",
        "b" to "number"
    )
) { arguments ->
    val a = arguments["a"] as? Number ?: 0
    val b = arguments["b"] as? Number ?: 0
    "Sum: ${a.toDouble() + b.toDouble()}"
}

// Add a text processing tool
manager.addSimpleTool(
    name = "reverse_text",
    description = "Reverse the input text",
    parameters = mapOf("text" to "string")
) { arguments ->
    val text = arguments["text"] as? String ?: ""
    text.reversed()
}
```

### Advanced MCP Tools

For tools that need full MCP protocol support:

```kotlin
// Create a complex tool with proper JSON schema
val complexTool = Tool(
    name = "process_data",
    description = "Process data with various operations",
    inputSchema = Tool.Input(
        properties = buildJsonObject {
            put("operation", buildJsonObject {
                put("type", JsonPrimitive("string"))
                put("enum", buildJsonArray {
                    add(JsonPrimitive("encode"))
                    add(JsonPrimitive("decode"))
                    add(JsonPrimitive("hash"))
                    add(JsonPrimitive("format"))
                })
                put("description", JsonPrimitive("The operation to perform"))
            })
            put("data", buildJsonObject {
                put("type", JsonPrimitive("string"))
                put("description", JsonPrimitive("The data to process"))
            })
            put("options", buildJsonObject {
                put("type", JsonPrimitive("object"))
                put("description", JsonPrimitive("Additional options"))
                put("properties", buildJsonObject {
                    put("encoding", buildJsonObject {
                        put("type", JsonPrimitive("string"))
                        put("default", JsonPrimitive("UTF-8"))
                    })
                })
            })
        },
        required = listOf("operation", "data")
    )
)

manager.addMcpTool(complexTool) { arguments ->
    val operation = arguments["operation"] as? String ?: "encode"
    val data = arguments["data"] as? String ?: ""
    val options = arguments["options"] as? Map<*, *> ?: emptyMap<String, Any>()
    
    val result = when (operation) {
        "encode" -> {
            val encoding = options["encoding"] as? String ?: "UTF-8"
            java.util.Base64.getEncoder().encodeToString(data.toByteArray(charset(encoding)))
        }
        "decode" -> {
            try {
                String(java.util.Base64.getDecoder().decode(data))
            } catch (e: Exception) {
                "Error: Invalid base64 data"
            }
        }
        "hash" -> {
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            digest.digest(data.toByteArray()).joinToString("") { "%02x".format(it) }
        }
        "format" -> {
            // Simple JSON formatting
            try {
                val json = kotlinx.serialization.json.Json.parseToJsonElement(data)
                kotlinx.serialization.json.Json { prettyPrint = true }.encodeToString(json)
            } catch (e: Exception) {
                "Error: Invalid JSON data"
            }
        }
        else -> "Error: Unknown operation '$operation'"
    }
    
    CallToolResult(
        content = listOf(TextContent(text = result)),
        isError = result.startsWith("Error:")
    )
}
```

### Android-Specific Tools

Create tools that leverage Android capabilities:

```kotlin
// File operations tool
manager.addMcpTool(
    Tool(
        name = "file_operations",
        description = "Perform file operations within app directory",
        inputSchema = Tool.Input(
            properties = buildJsonObject {
                put("action", buildJsonObject {
                    put("type", JsonPrimitive("string"))
                    put("enum", buildJsonArray {
                        add(JsonPrimitive("list"))
                        add(JsonPrimitive("read"))
                        add(JsonPrimitive("write"))
                        add(JsonPrimitive("delete"))
                    })
                })
                put("path", buildJsonObject {
                    put("type", JsonPrimitive("string"))
                    put("description", JsonPrimitive("Relative path within app directory"))
                })
                put("content", buildJsonObject {
                    put("type", JsonPrimitive("string"))
                    put("description", JsonPrimitive("Content for write operations"))
                })
            },
            required = listOf("action", "path")
        )
    )
) { arguments ->
    val action = arguments["action"] as? String ?: "list"
    val path = arguments["path"] as? String ?: ""
    val content = arguments["content"] as? String ?: ""
    
    try {
        val appDir = File(context.filesDir, path)
        
        val result = when (action) {
            "list" -> {
                if (appDir.isDirectory) {
                    appDir.listFiles()?.joinToString("\n") { it.name } ?: "Empty directory"
                } else {
                    "Not a directory"
                }
            }
            "read" -> {
                if (appDir.exists() && appDir.isFile) {
                    appDir.readText()
                } else {
                    "File not found"
                }
            }
            "write" -> {
                appDir.parentFile?.mkdirs()
                appDir.writeText(content)
                "File written successfully"
            }
            "delete" -> {
                if (appDir.exists()) {
                    if (appDir.delete()) "File deleted" else "Failed to delete"
                } else {
                    "File not found"
                }
            }
            else -> "Unknown action: $action"
        }
        
        CallToolResult(
            content = listOf(TextContent(text = result)),
            isError = false
        )
    } catch (e: Exception) {
        CallToolResult(
            content = listOf(TextContent(text = "Error: ${e.message}")),
            isError = true
        )
    }
}
```

## Adding Custom Resources

Resources provide file-like data that clients can read and potentially subscribe to for updates.

### Simple File Resources

```kotlin
val manager = McpStartup.getManager()

// Add a static file resource
manager.addFileResource(
    uri = "app://config/settings.json",
    name = "App Settings",
    description = "Application configuration settings",
    filePath = File(context.filesDir, "settings.json").absolutePath,
    mimeType = "application/json"
)

// Add app info as a resource
manager.addFileResource(
    uri = "app://info/manifest.xml",
    name = "App Manifest",
    description = "Application manifest information",
    filePath = context.packageManager.getApplicationInfo(context.packageName, 0).sourceDir,
    mimeType = "application/xml"
)
```

### Dynamic Resources

```kotlin
// Create a dynamic resource that provides real-time data
val statusResource = Resource(
    uri = "app://status/realtime",
    name = "Real-time Status",
    description = "Current application status and metrics",
    mimeType = "application/json"
)

manager.addMcpResource(statusResource) {
    val status = buildJsonObject {
        put("timestamp", JsonPrimitive(System.currentTimeMillis()))
        put("uptime", JsonPrimitive(SystemClock.elapsedRealtime()))
        put("memory", buildJsonObject {
            val runtime = Runtime.getRuntime()
            put("total", JsonPrimitive(runtime.totalMemory()))
            put("free", JsonPrimitive(runtime.freeMemory()))
            put("max", JsonPrimitive(runtime.maxMemory()))
            put("used", JsonPrimitive(runtime.totalMemory() - runtime.freeMemory()))
        })
        put("threads", JsonPrimitive(Thread.activeCount()))
        put("battery", buildJsonObject {
            val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val level = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            put("level", JsonPrimitive(level))
            put("charging", JsonPrimitive(batteryManager.isCharging))
        })
    }
    
    AndroidResourceContent(
        uri = "app://status/realtime",
        text = status.toString(),
        mimeType = "application/json"
    )
}

// Subscribe to resource updates
manager.subscribeMcpResource("app://status/realtime")
```

### Database Resources

```kotlin
// Expose database tables as resources
val dbResource = Resource(
    uri = "app://database/users",
    name = "User Database",
    description = "User data from local database",
    mimeType = "application/json"
)

manager.addMcpResource(dbResource) {
    // Query your Room database or SQLite
    val users = userDao.getAllUsers() // Assuming you have a DAO
    
    val jsonUsers = buildJsonArray {
        users.forEach { user ->
            add(buildJsonObject {
                put("id", JsonPrimitive(user.id))
                put("name", JsonPrimitive(user.name))
                put("email", JsonPrimitive(user.email))
                put("created", JsonPrimitive(user.createdAt.toString()))
            })
        }
    }
    
    AndroidResourceContent(
        uri = "app://database/users",
        text = jsonUsers.toString(),
        mimeType = "application/json"
    )
}
```

## Adding Custom Prompts

Prompts provide template-based text generation with dynamic arguments.

### Simple Prompts

```kotlin
val manager = McpStartup.getManager()

// Add a code review prompt
manager.addSimplePrompt(
    name = "review_code",
    description = "Generate a code review for the provided code",
    arguments = listOf(
        PromptArgument(
            name = "code",
            description = "The code to review",
            required = true
        ),
        PromptArgument(
            name = "language",
            description = "Programming language (default: kotlin)",
            required = false
        ),
        PromptArgument(
            name = "focus",
            description = "Areas to focus on (performance, security, style)",
            required = false
        )
    )
) { arguments ->
    val code = arguments["code"] as? String ?: ""
    val language = arguments["language"] as? String ?: "kotlin"
    val focus = arguments["focus"] as? String ?: "general best practices"
    
    """
    Please review the following $language code with focus on $focus:
    
    ```$language
    $code
    ```
    
    Provide feedback on:
    1. Code quality and readability
    2. Potential bugs or issues
    3. Performance considerations
    4. Security implications
    5. Adherence to best practices
    6. Suggestions for improvement
    
    Format your response with specific line references where applicable.
    """.trimIndent()
}
```

### Advanced MCP Prompts

```kotlin
// Create a complex prompt with multiple messages
val logAnalysisPrompt = Prompt(
    name = "analyze_android_logs",
    description = "Analyze Android application logs for issues and patterns",
    arguments = listOf(
        PromptArgument(
            name = "logs",
            description = "The log content to analyze",
            required = true
        ),
        PromptArgument(
            name = "time_range",
            description = "Time range for analysis (e.g., 'last 24 hours')",
            required = false
        ),
        PromptArgument(
            name = "severity",
            description = "Minimum log severity to consider (DEBUG, INFO, WARN, ERROR)",
            required = false
        )
    )
)

manager.addMcpPrompt(logAnalysisPrompt) { arguments ->
    val logs = arguments["logs"] as? String ?: ""
    val timeRange = arguments["time_range"] as? String ?: "recent"
    val severity = arguments["severity"] as? String ?: "WARN"
    
    GetPromptResult(
        description = "Analyze Android logs for patterns and issues",
        messages = listOf(
            PromptMessage(
                role = MessageRole.USER,
                content = TextContent(
                    text = """
                    You are an expert Android developer and log analyst. Please analyze the following Android application logs for the $timeRange time period, focusing on $severity level and above.
                    
                    Look for:
                    1. Critical errors and exceptions
                    2. Performance bottlenecks
                    3. Memory leaks or OutOfMemoryErrors
                    4. ANR (Application Not Responding) events
                    5. Security-related warnings
                    6. Unusual patterns or repeated issues
                    7. Potential root causes
                    
                    Logs to analyze:
                    ```
                    $logs
                    ```
                    
                    Provide a structured analysis with:
                    - Summary of findings
                    - Critical issues requiring immediate attention
                    - Performance recommendations
                    - Security considerations
                    - Suggested fixes or investigations
                    """.trimIndent()
                )
            )
        )
    )
}
```

## Lifecycle Management

### Configuring Lifecycle Behavior

```kotlin
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        val manager = McpStartup.initializeManually(this)
        
        // Configure comprehensive lifecycle management
        manager.initializeLifecycleManagement(
            application = this,
            config = McpLifecycleManager.LifecycleConfig(
                autoStartOnAppStart = true,           // Start server when app starts
                autoStopOnAppStop = false,            // Keep running in background
                restartOnAppReturn = true,            // Restart when returning from background
                pauseOnBackground = false,            // Don't pause when app goes to background
                stopOnLastActivityDestroyed = false   // Don't stop when all activities are destroyed
            )
        )
    }
}
```

### Monitoring Lifecycle State

```kotlin
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val manager = McpStartup.getManager()
        val lifecycleState = manager.getLifecycleState()
        
        Log.d("MCP", "App in background: ${lifecycleState.isAppInBackground}")
        Log.d("MCP", "Active activities: ${lifecycleState.activeActivities}")
        Log.d("MCP", "Server running: ${lifecycleState.isServerRunning}")
        Log.d("MCP", "Auto-start enabled: ${lifecycleState.config.autoStartOnAppStart}")
        
        // Update configuration dynamically
        if (shouldKeepServerRunning()) {
            manager.updateLifecycleConfig(
                lifecycleState.config.copy(
                    autoStopOnAppStop = false,
                    stopOnLastActivityDestroyed = false
                )
            )
        }
    }
    
    private fun shouldKeepServerRunning(): Boolean {
        // Your logic to determine if server should keep running
        return true
    }
}
```

## Transport Layer Usage

### WebSocket Transport

```kotlin
val manager = McpStartup.getManager()

// Get WebSocket transport information
val transportInfo = manager.getTransportInfo()
Log.d("MCP", "WebSocket endpoint: ws://localhost:8080/mcp")

// Send custom messages to connected clients
lifecycleScope.launch {
    val message = buildJsonObject {
        put("jsonrpc", JsonPrimitive("2.0"))
        put("method", JsonPrimitive("notification/custom"))
        put("params", buildJsonObject {
            put("message", JsonPrimitive("Hello from Android!"))
            put("timestamp", JsonPrimitive(System.currentTimeMillis()))
        })
    }
    
    manager.broadcastMessage(message.toString())
}
```

### HTTP/SSE Transport

```kotlin
// HTTP endpoints are automatically available:
// POST http://localhost:8081/mcp/message - for client-to-server messages
// GET  http://localhost:8081/mcp/events  - for server-to-client events (SSE)
// GET  http://localhost:8081/mcp/status  - for transport status

// Test endpoints from adb
// adb forward tcp:8081 tcp:8081
// curl -X POST http://localhost:8081/mcp/message -H "Content-Type: application/json" -d '{"jsonrpc":"2.0","method":"tools/list","id":1}'
```

## Error Handling

### Robust Error Handling Patterns

```kotlin
val manager = McpStartup.getManager()

// Safe server operations with proper error handling
lifecycleScope.launch {
    manager.startServer().fold(
        onSuccess = {
            Log.d("MCP", "Server started successfully")
            
            // Add tools with error handling
            try {
                manager.addSimpleTool(
                    name = "safe_operation",
                    description = "A safe operation with error handling",
                    parameters = mapOf("input" to "string")
                ) { arguments ->
                    try {
                        val input = arguments["input"] as? String ?: ""
                        // Your operation here
                        "Processed: $input"
                    } catch (e: Exception) {
                        Log.e("MCP", "Tool execution failed", e)
                        "Error: ${e.message}"
                    }
                }
            } catch (e: Exception) {
                Log.e("MCP", "Failed to add tool", e)
            }
        },
        onFailure = { exception ->
            Log.e("MCP", "Failed to start server", exception)
            // Handle startup failure
        }
    )
}
```

## Best Practices

### Tool Design

1. **Clear naming**: Use descriptive, action-oriented names
2. **Comprehensive descriptions**: Help AI understand when to use the tool
3. **Proper schemas**: Define clear parameter types and requirements
4. **Error handling**: Always handle and report errors gracefully
5. **Validation**: Validate input parameters before processing

### Resource Management

1. **Efficient data**: Only expose necessary data, avoid large responses
2. **Security**: Never expose sensitive information
3. **Updates**: Use subscriptions for dynamic resources
4. **Caching**: Cache expensive operations when appropriate

### Performance

1. **Async operations**: Use coroutines for I/O operations
2. **Background threads**: Don't block the main thread
3. **Memory management**: Be mindful of memory usage in tools and resources
4. **Connection limits**: Monitor transport connections and resource usage

### Security

1. **Input validation**: Always validate tool inputs
2. **File access**: Restrict file operations to safe directories
3. **Permissions**: Request only necessary Android permissions
4. **Data exposure**: Be careful about what data you expose via resources