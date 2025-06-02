package dev.jasonpearson.androidmcpsdk.debugbridge.tools

import android.app.Activity
import android.app.Application
import android.content.Context
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.core.view.isVisible
import dev.jasonpearson.androidmcpsdk.core.features.tools.McpToolProvider
import dev.jasonpearson.androidmcpsdk.core.features.tools.addTool
import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.TextContent
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.WeakHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

/** Provides UI view hierarchy inspection tools for the debug bridge. */
class ViewHierarchyToolProvider(private val context: Context) {

    companion object {
        private const val TAG = "ViewHierarchyProvider"
        private const val DEFAULT_POLLING_INTERVAL_MS = 1000L
        private const val ANDROIDX_PACKAGE_PREFIX = "androidx."
        private const val ANDROID_PACKAGE_PREFIX = "android."
        private const val COMPOSE_VIEW_CLASS = "androidx.compose.ui.platform.ComposeView"
    }

    @Serializable
    data class ViewHierarchyCaptureInput(
        val includeInvisible: Boolean = false,
        val maxDepth: Int? = null,
        val trackRecompositions: Boolean = false,
        val includePackageInfo: Boolean = true,
    )

    @Serializable
    data class ViewSearchInput(
        val text: String,
        val exactMatch: Boolean = false,
        val includePackageInfo: Boolean = true,
    )

    @Serializable
    data class ViewIdSearchInput(val id: String, val includePackageInfo: Boolean = true)

    @Serializable
    data class ViewClassSearchInput(val className: String, val includePackageInfo: Boolean = true)

    @Serializable
    data class StreamingConfigInput(
        val enabled: Boolean,
        val intervalMs: Long = DEFAULT_POLLING_INTERVAL_MS,
        val includeInvisible: Boolean = false,
        val maxDepth: Int? = null,
        val trackRecompositions: Boolean = true,
    )

    @Serializable
    data class ViewNode(
        val id: String?,
        val className: String,
        val packageName: String?,
        val fullClassName: String,
        val bounds: String,
        val text: String?,
        val contentDescription: String?,
        val isVisible: Boolean,
        val isClickable: Boolean,
        val isFocusable: Boolean,
        val isEnabled: Boolean,
        val depth: Int,
        val viewType: ViewType,
        val frameworkInfo: FrameworkInfo? = null,
        val recompositionCount: Long? = null,
        val children: List<ViewNode> = emptyList(),
    )

    @Serializable
    enum class ViewType {
        ANDROID_WIDGET,
        ANDROIDX_COMPONENT,
        COMPOSE_NODE,
        CIRCUIT_SCREEN,
        WORKFLOW_RENDERING,
        CUSTOM_VIEW,
        UNKNOWN,
    }

    @Serializable
    data class FrameworkInfo(
        val framework: String,
        val componentType: String? = null,
        val screenName: String? = null,
        val stateInfo: Map<String, String> = emptyMap(),
    )

    private var currentActivity: Activity? = null
    private val hierarchyStream = MutableSharedFlow<ViewNode>(replay = 1)
    private var streamingJob: Job? = null
    private var isStreamingEnabled = false
    private var streamingConfig = StreamingConfigInput(enabled = false)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val recompositionTracker = RecompositionTracker()
    private val viewTreeObservers = WeakHashMap<Activity, ViewTreeObserver.OnGlobalLayoutListener>()

    init {
        // Register activity lifecycle callbacks to track current activity
        (context.applicationContext as? Application)?.registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}

                override fun onActivityStarted(activity: Activity) {}

                override fun onActivityResumed(activity: Activity) {
                    currentActivity = activity
                    setupViewTreeObserver(activity)
                    if (isStreamingEnabled) {
                        startStreaming()
                    }
                }

                override fun onActivityPaused(activity: Activity) {
                    if (currentActivity == activity) {
                        cleanupViewTreeObserver(activity)
                    }
                }

                override fun onActivityStopped(activity: Activity) {}

                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

                override fun onActivityDestroyed(activity: Activity) {
                    if (currentActivity == activity) {
                        currentActivity = null
                        cleanupViewTreeObserver(activity)
                        stopStreaming()
                    }
                }
            }
        )
    }

    fun registerTools(toolProvider: McpToolProvider) {
        Log.d(TAG, "Registering view hierarchy tools")

        // View hierarchy capture tool
        toolProvider.addTool<ViewHierarchyCaptureInput>(
            name = "view_hierarchy_capture",
            description =
                "Capture current view hierarchy of the active activity with modern framework support",
        ) { input ->
            captureViewHierarchy(input)
        }

        // Find views by text tool
        toolProvider.addTool<ViewSearchInput>(
            name = "view_find_by_text",
            description = "Find views containing specific text with framework detection",
            required = listOf("text"),
        ) { input ->
            findViewsByText(input)
        }

        // Find views by ID tool
        toolProvider.addTool<ViewIdSearchInput>(
            name = "view_find_by_id",
            description = "Find views by resource ID with framework detection",
            required = listOf("id"),
        ) { input ->
            findViewsById(input)
        }

        // Find views by class tool
        toolProvider.addTool<ViewClassSearchInput>(
            name = "view_find_by_class",
            description = "Find views by class name with framework detection",
            required = listOf("className"),
        ) { input ->
            findViewsByClass(input)
        }

        // Configure streaming tool
        toolProvider.addTool<StreamingConfigInput>(
            name = "view_hierarchy_configure_streaming",
            description = "Configure real-time streaming of view hierarchy changes over SSE",
            required = listOf("enabled"),
        ) { input ->
            configureStreaming(input)
        }

        // Get recomposition stats tool
        toolProvider.addTool<EmptyInput>(
            name = "view_hierarchy_get_recomposition_stats",
            description = "Get Compose recomposition statistics and performance metrics",
        ) { _ ->
            getRecompositionStats()
        }

        Log.d(TAG, "View hierarchy tools registered")
    }

    @Serializable data class EmptyInput(val placeholder: String? = null)

    private suspend fun captureViewHierarchy(input: ViewHierarchyCaptureInput): CallToolResult {
        val includeInvisible = input.includeInvisible
        val maxDepth = input.maxDepth
        val trackRecompositions = input.trackRecompositions
        val includePackageInfo = input.includePackageInfo

        val activity = currentActivity
        if (activity == null) {
            return CallToolResult(
                content =
                    listOf(
                        TextContent(
                            text =
                                "No active activity found. Make sure the app is in the foreground."
                        )
                    ),
                isError = true,
            )
        }

        try {
            val rootView = activity.findViewById<View>(android.R.id.content)
            val hierarchy =
                captureViewNode(
                    rootView,
                    includeInvisible,
                    maxDepth,
                    0,
                    trackRecompositions,
                    includePackageInfo,
                )

            val result = buildString {
                appendLine("View Hierarchy:")
                appendLine("Activity: ${activity.javaClass.simpleName}")
                appendLine("Package: ${activity.javaClass.`package`?.name ?: "unknown"}")
                appendLine()
                appendViewNode(hierarchy, 0)
            }

            return CallToolResult(content = listOf(TextContent(text = result)), isError = false)
        } catch (e: Exception) {
            Log.e(TAG, "Error capturing view hierarchy", e)
            return CallToolResult(
                content =
                    listOf(TextContent(text = "Error capturing view hierarchy: ${e.message}")),
                isError = true,
            )
        }
    }

    private suspend fun configureStreaming(input: StreamingConfigInput): CallToolResult {
        val enabled = input.enabled
        val intervalMs = input.intervalMs
        val includeInvisible = input.includeInvisible
        val maxDepth = input.maxDepth
        val trackRecompositions = input.trackRecompositions

        streamingConfig = input

        isStreamingEnabled = enabled

        if (enabled) {
            startStreaming()
        } else {
            stopStreaming()
        }

        val result = buildString {
            appendLine("Streaming configuration updated:")
            appendLine("- Enabled: $enabled")
            if (enabled) {
                appendLine("- Interval: ${intervalMs}ms")
                appendLine("- Include invisible: $includeInvisible")
                appendLine("- Max depth: ${maxDepth ?: "unlimited"}")
                appendLine("- Track recompositions: $trackRecompositions")
            }
        }

        return CallToolResult(content = listOf(TextContent(text = result)), isError = false)
    }

    private suspend fun getRecompositionStats(input: EmptyInput? = null): CallToolResult {
        val stats = recompositionTracker.getStats()

        val result = buildString {
            appendLine("Compose Recomposition Statistics:")
            appendLine("- Total recompositions: ${stats.totalRecompositions}")
            appendLine("- Active composables: ${stats.activeComposables}")
            appendLine("- Average recompositions per composable: ${stats.averageRecompositions}")
            appendLine()

            if (stats.topRecomposingViews.isNotEmpty()) {
                appendLine("Top recomposing views:")
                stats.topRecomposingViews.take(10).forEach { (viewId, count) ->
                    appendLine("- $viewId: $count recompositions")
                }
            }
        }

        return CallToolResult(content = listOf(TextContent(text = result)), isError = false)
    }

    private fun startStreaming() {
        stopStreaming() // Stop any existing streaming

        streamingJob =
            CoroutineScope(Dispatchers.Main).launch {
                while (isActive && isStreamingEnabled) {
                    currentActivity?.let { activity ->
                        try {
                            val rootView = activity.findViewById<View>(android.R.id.content)
                            val hierarchy =
                                captureViewNode(
                                    rootView,
                                    streamingConfig.includeInvisible,
                                    streamingConfig.maxDepth,
                                    0,
                                    streamingConfig.trackRecompositions,
                                    true,
                                )
                            hierarchyStream.emit(hierarchy)
                        } catch (e: Exception) {
                            Log.w(TAG, "Error during streaming capture", e)
                        }
                    }
                    delay(streamingConfig.intervalMs)
                }
            }
    }

    private fun stopStreaming() {
        streamingJob?.cancel()
        streamingJob = null
    }

    private fun setupViewTreeObserver(activity: Activity) {
        if (!streamingConfig.trackRecompositions) return

        val rootView = activity.findViewById<View>(android.R.id.content) ?: return
        val listener =
            ViewTreeObserver.OnGlobalLayoutListener { recompositionTracker.onLayoutChange() }

        rootView.viewTreeObserver.addOnGlobalLayoutListener(listener)
        viewTreeObservers[activity] = listener
    }

    private fun cleanupViewTreeObserver(activity: Activity) {
        val rootView = activity.findViewById<View>(android.R.id.content) ?: return
        viewTreeObservers.remove(activity)?.let { listener ->
            rootView.viewTreeObserver.removeOnGlobalLayoutListener(listener)
        }
    }

    private fun captureViewNode(
        view: View,
        includeInvisible: Boolean,
        maxDepth: Int?,
        currentDepth: Int,
        trackRecompositions: Boolean,
        includePackageInfo: Boolean,
    ): ViewNode {
        val bounds = Rect()
        view.getGlobalVisibleRect(bounds)

        val children = mutableListOf<ViewNode>()

        // Only traverse children if we haven't hit max depth
        if (maxDepth == null || currentDepth < maxDepth) {
            if (view is ViewGroup) {
                for (i in 0 until view.childCount) {
                    val child = view.getChildAt(i)
                    if (includeInvisible || child.isVisible) {
                        children.add(
                            captureViewNode(
                                child,
                                includeInvisible,
                                maxDepth,
                                currentDepth + 1,
                                trackRecompositions,
                                includePackageInfo,
                            )
                        )
                    }
                }
            }
        }

        val fullClassName = view.javaClass.name
        val viewType = determineViewType(view, fullClassName)
        val frameworkInfo = extractFrameworkInfo(view, viewType)
        val recompositionCount =
            if (trackRecompositions) {
                recompositionTracker.getRecompositionCount(view)
            } else null

        return ViewNode(
            id = getViewIdName(view),
            className = view.javaClass.simpleName,
            packageName = if (includePackageInfo) extractPackageName(fullClassName) else null,
            fullClassName = fullClassName,
            bounds = "${bounds.left},${bounds.top},${bounds.right},${bounds.bottom}",
            text = getViewText(view),
            contentDescription = view.contentDescription?.toString(),
            isVisible = view.isVisible,
            isClickable = view.isClickable,
            isFocusable = view.isFocusable,
            isEnabled = view.isEnabled,
            depth = currentDepth,
            viewType = viewType,
            frameworkInfo = frameworkInfo,
            recompositionCount = recompositionCount,
            children = children,
        )
    }

    private fun determineViewType(view: View, fullClassName: String): ViewType {
        return when {
            fullClassName.startsWith(ANDROID_PACKAGE_PREFIX) -> ViewType.ANDROID_WIDGET
            fullClassName.startsWith(ANDROIDX_PACKAGE_PREFIX) -> ViewType.ANDROIDX_COMPONENT
            isComposeView(view, fullClassName) -> ViewType.COMPOSE_NODE
            isCircuitScreen(view, fullClassName) -> ViewType.CIRCUIT_SCREEN
            isWorkflowRendering(view, fullClassName) -> ViewType.WORKFLOW_RENDERING
            isCustomView(fullClassName) -> ViewType.CUSTOM_VIEW
            else -> ViewType.UNKNOWN
        }
    }

    private fun isComposeView(view: View, fullClassName: String): Boolean {
        return fullClassName == COMPOSE_VIEW_CLASS ||
            fullClassName.contains("compose", ignoreCase = true)
    }

    private fun isCircuitScreen(view: View, fullClassName: String): Boolean {
        return fullClassName.contains("circuit", ignoreCase = true) ||
            fullClassName.contains("slack", ignoreCase = true) ||
            hasCircuitAnnotations(view.javaClass)
    }

    private fun isWorkflowRendering(view: View, fullClassName: String): Boolean {
        return fullClassName.contains("workflow", ignoreCase = true) ||
            fullClassName.contains("square", ignoreCase = true) ||
            hasWorkflowInterfaces(view.javaClass)
    }

    private fun isCustomView(fullClassName: String): Boolean {
        return !fullClassName.startsWith(ANDROID_PACKAGE_PREFIX) &&
            !fullClassName.startsWith(ANDROIDX_PACKAGE_PREFIX) &&
            !fullClassName.startsWith("java.") &&
            !fullClassName.startsWith("kotlin.")
    }

    private fun hasCircuitAnnotations(clazz: Class<*>): Boolean {
        return try {
            clazz.annotations.any {
                it.annotationClass.qualifiedName?.contains("circuit", true) == true
            } || clazz.interfaces.any { it.name.contains("circuit", true) }
        } catch (e: Exception) {
            false
        }
    }

    private fun hasWorkflowInterfaces(clazz: Class<*>): Boolean {
        return try {
            clazz.interfaces.any {
                it.name.contains("workflow", true) || it.name.contains("rendering", true)
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun extractPackageName(fullClassName: String): String? {
        val lastDotIndex = fullClassName.lastIndexOf('.')
        return if (lastDotIndex > 0) {
            fullClassName.substring(0, lastDotIndex)
        } else null
    }

    private fun extractFrameworkInfo(view: View, viewType: ViewType): FrameworkInfo? {
        return when (viewType) {
            ViewType.CIRCUIT_SCREEN -> extractCircuitInfo(view)
            ViewType.WORKFLOW_RENDERING -> extractWorkflowInfo(view)
            ViewType.COMPOSE_NODE -> extractComposeInfo(view)
            else -> null
        }
    }

    private fun extractCircuitInfo(view: View): FrameworkInfo? {
        return try {
            val stateInfo = mutableMapOf<String, String>()

            // Try to extract Circuit-specific information using reflection
            val screenField = findFieldByType(view.javaClass, "Screen")
            screenField?.let { field ->
                field.isAccessible = true
                val screen = field.get(view)
                stateInfo["screen"] = screen?.javaClass?.simpleName ?: "Unknown"
            }

            FrameworkInfo(
                framework = "Circuit",
                componentType = "Screen",
                screenName = view.javaClass.simpleName,
                stateInfo = stateInfo,
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to extract Circuit info", e)
            FrameworkInfo(framework = "Circuit", componentType = "Screen")
        }
    }

    private fun extractWorkflowInfo(view: View): FrameworkInfo? {
        return try {
            val stateInfo = mutableMapOf<String, String>()

            // Try to extract Workflow-specific information
            val renderingMethod = findMethodByName(view.javaClass, "render")
            renderingMethod?.let { stateInfo["hasRenderMethod"] = "true" }

            FrameworkInfo(
                framework = "Workflow",
                componentType = "Rendering",
                stateInfo = stateInfo,
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to extract Workflow info", e)
            FrameworkInfo(framework = "Workflow", componentType = "Rendering")
        }
    }

    private fun extractComposeInfo(view: View): FrameworkInfo? {
        return try {
            val stateInfo = mutableMapOf<String, String>()

            // Try to check if it's a ComposeView using reflection
            val hasCompositionMethod = findMethodByName(view.javaClass, "hasComposition")
            hasCompositionMethod?.let { method ->
                method.isAccessible = true
                val hasComposition = method.invoke(view) as? Boolean
                stateInfo["hasComposition"] = hasComposition.toString()
            }

            FrameworkInfo(
                framework = "Compose",
                componentType = "ComposeView",
                stateInfo = stateInfo,
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to extract Compose info", e)
            FrameworkInfo(framework = "Compose", componentType = "ComposeView")
        }
    }

    private fun findFieldByType(clazz: Class<*>, typeName: String): Field? {
        return try {
            clazz.declaredFields.find { field ->
                field.type.simpleName.contains(typeName, ignoreCase = true)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun findMethodByName(clazz: Class<*>, methodName: String): Method? {
        return try {
            clazz.methods.find { method -> method.name.equals(methodName, ignoreCase = true) }
        } catch (e: Exception) {
            null
        }
    }

    // Existing methods remain the same but now call the enhanced captureViewNode
    private suspend fun findViewsByText(input: ViewSearchInput): CallToolResult {
        val searchText = input.text
        val exactMatch = input.exactMatch
        val includePackageInfo = input.includePackageInfo

        if (searchText.isNullOrBlank()) {
            return CallToolResult(
                content = listOf(TextContent(text = "Search text cannot be empty")),
                isError = true,
            )
        }

        val activity = currentActivity
        if (activity == null) {
            return CallToolResult(
                content = listOf(TextContent(text = "No active activity found")),
                isError = true,
            )
        }

        try {
            val rootView = activity.findViewById<View>(android.R.id.content)
            val matchingViews = mutableListOf<ViewNode>()
            findViewsByTextRecursive(
                rootView,
                searchText,
                exactMatch,
                matchingViews,
                0,
                includePackageInfo,
            )

            val result = buildString {
                appendLine("Views containing text '$searchText' (exact match: $exactMatch):")
                appendLine("Found ${matchingViews.size} matching views")
                appendLine()
                matchingViews.forEachIndexed { index, view ->
                    appendLine("${index + 1}. ${view.className} (${view.viewType})")
                    if (includePackageInfo && view.packageName != null) {
                        appendLine("   Package: ${view.packageName}")
                    }
                    appendLine("   ID: ${view.id ?: "None"}")
                    appendLine("   Text: ${view.text}")
                    appendLine("   Bounds: ${view.bounds}")
                    appendLine("   Visible: ${view.isVisible}")
                    view.frameworkInfo?.let { info ->
                        appendLine("   Framework: ${info.framework} (${info.componentType})")
                    }
                    appendLine()
                }
            }

            return CallToolResult(content = listOf(TextContent(text = result)), isError = false)
        } catch (e: Exception) {
            Log.e(TAG, "Error finding views by text", e)
            return CallToolResult(
                content = listOf(TextContent(text = "Error finding views: ${e.message}")),
                isError = true,
            )
        }
    }

    private suspend fun findViewsById(input: ViewIdSearchInput): CallToolResult {
        val searchId = input.id
        val includePackageInfo = input.includePackageInfo

        if (searchId.isNullOrBlank()) {
            return CallToolResult(
                content = listOf(TextContent(text = "Search ID cannot be empty")),
                isError = true,
            )
        }

        val activity = currentActivity
        if (activity == null) {
            return CallToolResult(
                content = listOf(TextContent(text = "No active activity found")),
                isError = true,
            )
        }

        try {
            val rootView = activity.findViewById<View>(android.R.id.content)
            val matchingViews = mutableListOf<ViewNode>()
            findViewsByIdRecursive(rootView, searchId, matchingViews, 0, includePackageInfo)

            val result = buildString {
                appendLine("Views with ID '$searchId':")
                appendLine("Found ${matchingViews.size} matching views")
                appendLine()
                matchingViews.forEachIndexed { index, view ->
                    appendLine("${index + 1}. ${view.className} (${view.viewType})")
                    if (includePackageInfo && view.packageName != null) {
                        appendLine("   Package: ${view.packageName}")
                    }
                    appendLine("   ID: ${view.id}")
                    appendLine("   Text: ${view.text ?: "None"}")
                    appendLine("   Bounds: ${view.bounds}")
                    appendLine("   Visible: ${view.isVisible}")
                    view.frameworkInfo?.let { info ->
                        appendLine("   Framework: ${info.framework} (${info.componentType})")
                    }
                    appendLine()
                }
            }

            return CallToolResult(content = listOf(TextContent(text = result)), isError = false)
        } catch (e: Exception) {
            Log.e(TAG, "Error finding views by ID", e)
            return CallToolResult(
                content = listOf(TextContent(text = "Error finding views: ${e.message}")),
                isError = true,
            )
        }
    }

    private suspend fun findViewsByClass(input: ViewClassSearchInput): CallToolResult {
        val searchClass = input.className
        val includePackageInfo = input.includePackageInfo

        if (searchClass.isNullOrBlank()) {
            return CallToolResult(
                content = listOf(TextContent(text = "Search class name cannot be empty")),
                isError = true,
            )
        }

        val activity = currentActivity
        if (activity == null) {
            return CallToolResult(
                content = listOf(TextContent(text = "No active activity found")),
                isError = true,
            )
        }

        try {
            val rootView = activity.findViewById<View>(android.R.id.content)
            val matchingViews = mutableListOf<ViewNode>()
            findViewsByClassRecursive(rootView, searchClass, matchingViews, 0, includePackageInfo)

            val result = buildString {
                appendLine("Views with class '$searchClass':")
                appendLine("Found ${matchingViews.size} matching views")
                appendLine()
                matchingViews.forEachIndexed { index, view ->
                    appendLine("${index + 1}. ${view.className} (${view.viewType})")
                    if (includePackageInfo && view.packageName != null) {
                        appendLine("   Package: ${view.packageName}")
                    }
                    appendLine("   Full Class: ${view.fullClassName}")
                    appendLine("   ID: ${view.id ?: "None"}")
                    appendLine("   Text: ${view.text ?: "None"}")
                    appendLine("   Bounds: ${view.bounds}")
                    appendLine("   Visible: ${view.isVisible}")
                    view.frameworkInfo?.let { info ->
                        appendLine("   Framework: ${info.framework} (${info.componentType})")
                    }
                    appendLine()
                }
            }

            return CallToolResult(content = listOf(TextContent(text = result)), isError = false)
        } catch (e: Exception) {
            Log.e(TAG, "Error finding views by class", e)
            return CallToolResult(
                content = listOf(TextContent(text = "Error finding views: ${e.message}")),
                isError = true,
            )
        }
    }

    private fun findViewsByTextRecursive(
        view: View,
        searchText: String,
        exactMatch: Boolean,
        matches: MutableList<ViewNode>,
        depth: Int,
        includePackageInfo: Boolean,
    ) {
        val viewText = getViewText(view)
        val contentDesc = view.contentDescription?.toString()

        val textMatches =
            if (exactMatch) {
                viewText == searchText || contentDesc == searchText
            } else {
                viewText?.contains(searchText, ignoreCase = true) == true ||
                    contentDesc?.contains(searchText, ignoreCase = true) == true
            }

        if (textMatches) {
            matches.add(captureViewNode(view, false, 0, depth, false, includePackageInfo))
        }

        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                findViewsByTextRecursive(
                    view.getChildAt(i),
                    searchText,
                    exactMatch,
                    matches,
                    depth + 1,
                    includePackageInfo,
                )
            }
        }
    }

    private fun findViewsByIdRecursive(
        view: View,
        searchId: String,
        matches: MutableList<ViewNode>,
        depth: Int,
        includePackageInfo: Boolean,
    ) {
        val viewId = getViewIdName(view)
        if (
            viewId != null &&
                (viewId == searchId ||
                    viewId.endsWith(":id/$searchId") ||
                    viewId.endsWith("/$searchId"))
        ) {
            matches.add(captureViewNode(view, false, 0, depth, false, includePackageInfo))
        }

        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                findViewsByIdRecursive(
                    view.getChildAt(i),
                    searchId,
                    matches,
                    depth + 1,
                    includePackageInfo,
                )
            }
        }
    }

    private fun findViewsByClassRecursive(
        view: View,
        searchClass: String,
        matches: MutableList<ViewNode>,
        depth: Int,
        includePackageInfo: Boolean,
    ) {
        val className = view.javaClass.simpleName
        val fullClassName = view.javaClass.name

        if (
            className == searchClass ||
                fullClassName == searchClass ||
                fullClassName.endsWith(".$searchClass")
        ) {
            matches.add(captureViewNode(view, false, 0, depth, false, includePackageInfo))
        }

        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                findViewsByClassRecursive(
                    view.getChildAt(i),
                    searchClass,
                    matches,
                    depth + 1,
                    includePackageInfo,
                )
            }
        }
    }

    private fun getViewIdName(view: View): String? {
        return try {
            if (view.id != View.NO_ID) {
                view.context.resources.getResourceEntryName(view.id)?.let { entryName ->
                    view.context.resources.getResourceName(view.id)
                } ?: view.id.toString()
            } else null
        } catch (e: Exception) {
            view.id.takeIf { it != View.NO_ID }?.toString()
        }
    }

    private fun getViewText(view: View): String? {
        return when (view) {
            is android.widget.TextView -> view.text?.toString()
            is android.widget.Button -> view.text?.toString()
            is android.widget.EditText ->
                if (view.inputType and android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD != 0) {
                    "[PASSWORD FIELD]"
                } else {
                    view.text?.toString()
                }
            else -> null
        }
    }

    private fun StringBuilder.appendViewNode(node: ViewNode, indentLevel: Int) {
        val indent = "  ".repeat(indentLevel)
        appendLine("$indent${node.className} (${node.viewType})")
        if (node.packageName != null) appendLine("$indent  Package: ${node.packageName}")
        if (node.id != null) appendLine("$indent  ID: ${node.id}")
        if (node.text != null) appendLine("$indent  Text: ${node.text}")
        if (node.contentDescription != null)
            appendLine("$indent  Description: ${node.contentDescription}")
        appendLine("$indent  Bounds: ${node.bounds}")
        appendLine(
            "$indent  Visible: ${node.isVisible}, Clickable: ${node.isClickable}, Focusable: ${node.isFocusable}, Enabled: ${node.isEnabled}"
        )

        node.frameworkInfo?.let { info ->
            appendLine("$indent  Framework: ${info.framework} (${info.componentType})")
            if (info.stateInfo.isNotEmpty()) {
                appendLine("$indent  State: ${info.stateInfo}")
            }
        }

        node.recompositionCount?.let { count -> appendLine("$indent  Recompositions: $count") }

        if (node.children.isNotEmpty()) {
            appendLine("$indent  Children:")
            node.children.forEach { child -> appendViewNode(child, indentLevel + 2) }
        }
    }

    // Expose the hierarchy stream for SSE integration
    fun getHierarchyStream(): SharedFlow<ViewNode> = hierarchyStream
}

/** Tracks Compose recomposition counts and performance metrics */
private class RecompositionTracker {
    private val recompositionCounts = WeakHashMap<View, AtomicLong>()
    private val totalRecompositions = AtomicLong(0)
    private var lastLayoutTime = System.currentTimeMillis()

    fun onLayoutChange() {
        totalRecompositions.incrementAndGet()
        lastLayoutTime = System.currentTimeMillis()
    }

    fun getRecompositionCount(view: View): Long {
        return recompositionCounts.getOrPut(view) { AtomicLong(0) }.get()
    }

    fun incrementRecomposition(view: View) {
        recompositionCounts.getOrPut(view) { AtomicLong(0) }.incrementAndGet()
        totalRecompositions.incrementAndGet()
    }

    data class RecompositionStats(
        val totalRecompositions: Long,
        val activeComposables: Int,
        val averageRecompositions: Double,
        val topRecomposingViews: List<Pair<String, Long>>,
    )

    fun getStats(): RecompositionStats {
        val total = totalRecompositions.get()
        val active = recompositionCounts.size
        val average = if (active > 0) total.toDouble() / active else 0.0

        val topViews =
            recompositionCounts.entries
                .map { (view, count) ->
                    val viewId = view.javaClass.simpleName to count.get()
                    viewId
                }
                .sortedByDescending { it.second }
                .take(10)

        return RecompositionStats(
            totalRecompositions = total,
            activeComposables = active,
            averageRecompositions = average,
            topRecomposingViews = topViews,
        )
    }
}
