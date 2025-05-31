package dev.jasonpearson.mcpandroidsdk.features.prompts

import android.content.Context
import android.util.Log
import dev.jasonpearson.mcpandroidsdk.*
import dev.jasonpearson.mcpandroidsdk.models.*
import io.modelcontextprotocol.kotlin.sdk.Prompt
import java.util.concurrent.ConcurrentHashMap

/**
 * Provider for MCP prompts that enables servers to expose reusable prompt templates.
 *
 * Prompts provide standardized ways to interact with LLMs and can include dynamic
 * arguments and resource context.
 */
class PromptProvider(private val context: Context) {

    companion object {
        private const val TAG = "PromptProvider"
    }

    // Storage for custom prompts
    private val customPrompts =
        ConcurrentHashMap<String, Pair<Prompt, suspend (Map<String, Any?>) -> GetPromptResult>>()

    /**
     * Get all available prompts including built-in and custom prompts
     */
    fun getAllPrompts(): List<Prompt> {
        val builtInPrompts = createBuiltInPrompts()
        val customPromptList = customPrompts.values.map { it.first }
        return builtInPrompts + customPromptList
    }

    /**
     * Get a specific prompt by name with the provided arguments
     */
    suspend fun getPrompt(
        name: String,
        arguments: Map<String, Any?> = emptyMap()
    ): GetPromptResult {
        Log.d(TAG, "Getting prompt: $name with arguments: $arguments")

        return when {
            customPrompts.containsKey(name) -> {
                val handler = customPrompts[name]?.second
                handler?.invoke(arguments) ?: GetPromptResult(
                    description = "Custom prompt handler not found for $name",
                    messages = listOf(
                        PromptMessage(
                            role = MessageRole.USER,
                            content = TextContent(text = "Error: Custom prompt handler not found for $name")
                        )
                    )
                )
            }

            name in getBuiltInPromptNames() -> getBuiltInPrompt(name, arguments)
            else -> GetPromptResult(
                description = "Prompt not found: $name",
                messages = listOf(
                    PromptMessage(
                        role = MessageRole.USER,
                        content = TextContent(text = "Error: Prompt not found: $name")
                    )
                )
            )
        }
    }

    /**
     * Add a custom prompt with its handler
     */
    fun addPrompt(prompt: Prompt, handler: suspend (Map<String, Any?>) -> GetPromptResult) {
        customPrompts[prompt.name] = Pair(prompt, handler)
        Log.i(TAG, "Added custom prompt: ${prompt.name}")
    }

    /**
     * Remove a custom prompt
     */
    fun removePrompt(name: String): Boolean {
        val removed = customPrompts.remove(name) != null
        if (removed) {
            Log.i(TAG, "Removed custom prompt: $name")
        }
        return removed
    }

    /**
     * Create built-in Android-specific prompts
     */
    private fun createBuiltInPrompts(): List<Prompt> {
        return listOf(
            createAnalyzeLogPrompt(),
            createGenerateCodePrompt(),
            createExplainErrorPrompt(),
            createCreateTestPrompt(),
            createReviewCodePrompt()
        )
    }

    private fun getBuiltInPromptNames(): Set<String> {
        return setOf(
            "analyze_android_log",
            "generate_android_code",
            "explain_android_error",
            "create_android_test",
            "review_android_code"
        )
    }

    /**
     * Handle built-in prompt requests
     */
    private suspend fun getBuiltInPrompt(
        name: String,
        arguments: Map<String, Any?>
    ): GetPromptResult {
        Log.d(TAG, "Getting built-in prompt: $name")
        return try {
            when (name) {
                "analyze_android_log" -> analyzeAndroidLog(arguments)
                "generate_android_code" -> generateAndroidCode(arguments)
                "explain_android_error" -> explainAndroidError(arguments)
                "create_android_test" -> createAndroidTest(arguments)
                "review_android_code" -> reviewAndroidCode(arguments)
                else -> GetPromptResult(
                    description = "Unknown built-in prompt: $name",
                    messages = listOf(
                        PromptMessage(
                            role = MessageRole.USER,
                            content = TextContent(text = "Error: Unknown built-in prompt: $name")
                        )
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting built-in prompt $name", e)
            GetPromptResult(
                description = "Error executing prompt $name: ${e.message}",
                messages = listOf(
                    PromptMessage(
                        role = MessageRole.USER,
                        content = TextContent(text = "Error executing prompt $name: ${e.message}")
                    )
                )
            )
        }
    }

    // Built-in prompt definitions

    private fun createAnalyzeLogPrompt(): Prompt {
        return Prompt(
            name = "analyze_android_log",
            description = "Analyze Android log output for errors, warnings, and issues",
            arguments = listOf(
                io.modelcontextprotocol.kotlin.sdk.PromptArgument(
                    name = "logData",
                    description = "Android log output to analyze",
                    required = true
                ),
                io.modelcontextprotocol.kotlin.sdk.PromptArgument(
                    name = "focusArea",
                    description = "Specific area to focus on (crashes, performance, security, etc.)",
                    required = false
                )
            )
        )
    }

    private fun createGenerateCodePrompt(): Prompt {
        return Prompt(
            name = "generate_android_code",
            description = "Generate Android code for specific functionality",
            arguments = listOf(
                io.modelcontextprotocol.kotlin.sdk.PromptArgument(
                    name = "functionality",
                    description = "Description of the functionality to implement",
                    required = true
                ),
                io.modelcontextprotocol.kotlin.sdk.PromptArgument(
                    name = "language",
                    description = "Programming language (Kotlin, Java)",
                    required = false
                ),
                io.modelcontextprotocol.kotlin.sdk.PromptArgument(
                    name = "architecture",
                    description = "Architecture pattern (MVVM, MVP, MVI, etc.)",
                    required = false
                )
            )
        )
    }

    private fun createExplainErrorPrompt(): Prompt {
        return Prompt(
            name = "explain_android_error",
            description = "Explain Android error messages and provide solutions",
            arguments = listOf(
                io.modelcontextprotocol.kotlin.sdk.PromptArgument(
                    name = "errorMessage",
                    description = "The error message or stack trace",
                    required = true
                ),
                io.modelcontextprotocol.kotlin.sdk.PromptArgument(
                    name = "context",
                    description = "Additional context about when the error occurred",
                    required = false
                )
            )
        )
    }

    private fun createCreateTestPrompt(): Prompt {
        return Prompt(
            name = "create_android_test",
            description = "Create unit or instrumentation tests for Android code",
            arguments = listOf(
                io.modelcontextprotocol.kotlin.sdk.PromptArgument(
                    name = "codeToTest",
                    description = "The code that needs to be tested",
                    required = true
                ),
                io.modelcontextprotocol.kotlin.sdk.PromptArgument(
                    name = "testType",
                    description = "Type of test (unit, integration, ui)",
                    required = false
                )
            )
        )
    }

    private fun createReviewCodePrompt(): Prompt {
        return Prompt(
            name = "review_android_code",
            description = "Review Android code for best practices, performance, and potential issues",
            arguments = listOf(
                io.modelcontextprotocol.kotlin.sdk.PromptArgument(
                    name = "code",
                    description = "The code to review",
                    required = true
                ),
                io.modelcontextprotocol.kotlin.sdk.PromptArgument(
                    name = "focusAreas",
                    description = "Specific areas to focus on (performance, security, maintainability, etc.)",
                    required = false
                )
            )
        )
    }

    // Built-in prompt implementations

    private fun analyzeAndroidLog(arguments: Map<String, Any?>): GetPromptResult {
        val logData = arguments["logData"] as? String ?: ""
        val focusArea = arguments["focusArea"] as? String ?: "general analysis"

        val promptText = buildString {
            appendLine("Please analyze the following Android log output:")
            appendLine()
            appendLine("Focus area: $focusArea")
            appendLine()
            appendLine("Log data:")
            appendLine("```")
            appendLine(logData)
            appendLine("```")
            appendLine()
            appendLine("Please provide:")
            appendLine("1. Summary of key issues found")
            appendLine("2. Error and warning analysis")
            appendLine("3. Potential root causes")
            appendLine("4. Recommended solutions")
            appendLine("5. Prevention strategies")
        }

        return GetPromptResult(
            description = "Analyze Android log output focusing on $focusArea",
            messages = listOf(
                PromptMessage(
                    role = MessageRole.USER,
                    content = TextContent(text = promptText)
                )
            )
        )
    }

    private fun generateAndroidCode(arguments: Map<String, Any?>): GetPromptResult {
        val functionality = arguments["functionality"] as? String ?: ""
        val language = arguments["language"] as? String ?: "Kotlin"
        val architecture = arguments["architecture"] as? String ?: "MVVM"

        val promptText = buildString {
            appendLine("Please generate Android code for the following functionality:")
            appendLine()
            appendLine("Functionality: $functionality")
            appendLine("Language: $language")
            appendLine("Architecture: $architecture")
            appendLine()
            appendLine("Please provide:")
            appendLine("1. Complete, working code implementation")
            appendLine("2. Follow Android best practices and $architecture architecture")
            appendLine("3. Include proper error handling")
            appendLine("4. Add appropriate comments")
            appendLine("5. Consider performance and memory efficiency")
            appendLine("6. Include any necessary dependencies or permissions")
        }

        return GetPromptResult(
            description = "Generate $language Android code for $functionality using $architecture architecture",
            messages = listOf(
                PromptMessage(
                    role = MessageRole.USER,
                    content = TextContent(text = promptText)
                )
            )
        )
    }

    private fun explainAndroidError(arguments: Map<String, Any?>): GetPromptResult {
        val errorMessage = arguments["errorMessage"] as? String ?: ""
        val context = arguments["context"] as? String ?: "No additional context provided"

        val promptText = buildString {
            appendLine("Please explain the following Android error and provide solutions:")
            appendLine()
            appendLine("Error message:")
            appendLine("```")
            appendLine(errorMessage)
            appendLine("```")
            appendLine()
            appendLine("Context: $context")
            appendLine()
            appendLine("Please provide:")
            appendLine("1. Explanation of what this error means")
            appendLine("2. Common causes of this error")
            appendLine("3. Step-by-step solutions")
            appendLine("4. Code examples if applicable")
            appendLine("5. Prevention strategies")
            appendLine("6. Related documentation or resources")
        }

        return GetPromptResult(
            description = "Explain Android error and provide solutions",
            messages = listOf(
                PromptMessage(
                    role = MessageRole.USER,
                    content = TextContent(text = promptText)
                )
            )
        )
    }

    private fun createAndroidTest(arguments: Map<String, Any?>): GetPromptResult {
        val codeToTest = arguments["codeToTest"] as? String ?: ""
        val testType = arguments["testType"] as? String ?: "unit"

        val promptText = buildString {
            appendLine("Please create comprehensive $testType tests for the following Android code:")
            appendLine()
            appendLine("Code to test:")
            appendLine("```kotlin")
            appendLine(codeToTest)
            appendLine("```")
            appendLine()
            appendLine("Please provide:")
            appendLine("1. Complete test implementation")
            appendLine("2. Test all public methods and edge cases")
            appendLine("3. Use appropriate testing frameworks (JUnit, Mockito, Espresso, etc.)")
            appendLine("4. Include setup and teardown if needed")
            appendLine("5. Mock dependencies appropriately")
            appendLine("6. Test both success and failure scenarios")
            appendLine("7. Add descriptive test names and comments")
        }

        return GetPromptResult(
            description = "Create $testType tests for Android code",
            messages = listOf(
                PromptMessage(
                    role = MessageRole.USER,
                    content = TextContent(text = promptText)
                )
            )
        )
    }

    private fun reviewAndroidCode(arguments: Map<String, Any?>): GetPromptResult {
        val code = arguments["code"] as? String ?: ""
        val focusAreas = arguments["focusAreas"] as? String ?: "general code quality"

        val promptText = buildString {
            appendLine("Please review the following Android code:")
            appendLine()
            appendLine("Focus areas: $focusAreas")
            appendLine()
            appendLine("Code to review:")
            appendLine("```kotlin")
            appendLine(code)
            appendLine("```")
            appendLine()
            appendLine("Please provide:")
            appendLine("1. Overall code quality assessment")
            appendLine("2. Android best practices compliance")
            appendLine("3. Performance considerations")
            appendLine("4. Security issues (if any)")
            appendLine("5. Maintainability and readability")
            appendLine("6. Specific suggestions for improvement")
            appendLine("7. Alternative approaches or patterns")
            appendLine("8. Potential bugs or edge cases")
        }

        return GetPromptResult(
            description = "Review Android code focusing on $focusAreas",
            messages = listOf(
                PromptMessage(
                    role = MessageRole.USER,
                    content = TextContent(text = promptText)
                )
            )
        )
    }
}
