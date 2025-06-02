package dev.jasonpearson.androidmcpsdk.debugbridge.tools

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.app.Application
import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.text.Layout
import android.text.TextUtils
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.core.view.children
import androidx.core.view.isEmpty
import androidx.core.view.isVisible
import dev.jasonpearson.androidmcpsdk.core.features.tools.ToolRegistry
import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.TextContent
import io.modelcontextprotocol.kotlin.sdk.Tool
import java.util.Locale
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/** Provides accessibility inspection tools for Android apps. */
class AccessibilityInspectionToolProvider(internal val context: Context) {

    companion object {
        internal const val TAG = "AccessibilityInspection"
        internal const val MIN_TOUCH_TARGET_SIZE_DP = 48
        internal const val MIN_TEXT_SIZE_SP = 12
        internal const val MIN_CONTRAST_RATIO_NORMAL_TEXT = 4.5
        internal const val MIN_CONTRAST_RATIO_LARGE_TEXT = 3.0
        internal const val LARGE_TEXT_SIZE_SP = 18
        internal const val HEADING_MIN_TEXT_SIZE_SP = 14 // Example, slightly larger
        internal const val FOCUS_ORDER_MAX_DISTANCE_FACTOR = 2.5 // Heuristic for focus order jumps

        internal val GENERIC_INTERACTIVE_TEXT =
            setOf(
                "click here",
                "learn more",
                "read more",
                "more info",
                "details",
                "submit",
                "go",
                "next",
                "continue",
            )
    }

    internal var currentActivity: Activity? = null

    init {
        // Track current activity for accessibility inspection
        (context.applicationContext as? Application)?.registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}

                override fun onActivityStarted(activity: Activity) {}

                override fun onActivityResumed(activity: Activity) {
                    currentActivity = activity
                }

                override fun onActivityPaused(activity: Activity) {}

                override fun onActivityStopped(activity: Activity) {}

                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

                override fun onActivityDestroyed(activity: Activity) {
                    if (currentActivity == activity) {
                        currentActivity = null
                    }
                }
            }
        )
    }

    fun registerTools(registry: ToolRegistry) {
        Log.d(TAG, "Registering accessibility inspection tools")

        registry.addTool(createAccessibilityCaptureTool()) { arguments ->
            captureAccessibilityTree(arguments)
        }

        registry.addTool(createAccessibilityValidateTool()) { arguments ->
            validateAccessibility(arguments)
        }

        registry.addTool(createAccessibilityServiceStatusTool()) { arguments ->
            getAccessibilityServiceStatus(arguments)
        }

        Log.d(TAG, "Accessibility inspection tools registered")
    }

    internal fun createAccessibilityCaptureTool(): Tool {
        return Tool(
            name = "accessibility_capture",
            description =
                "Capture accessibility tree of the current activity, including detailed properties.",
            inputSchema =
                Tool.Input(
                    properties = buildJsonObject { put("type", JsonPrimitive("object")) },
                    required = emptyList(),
                ),
        )
    }

    internal fun createAccessibilityValidateTool(): Tool {
        return Tool(
            name = "accessibility_validate",
            description =
                "Validate accessibility compliance, including contrast, text size, and element usage.",
            inputSchema =
                Tool.Input(
                    properties = buildJsonObject { put("type", JsonPrimitive("object")) },
                    required = emptyList(),
                ),
        )
    }

    internal fun createAccessibilityServiceStatusTool(): Tool {
        return Tool(
            name = "accessibility_service_status",
            description = "Get accessibility service status and running services.",
            inputSchema =
                Tool.Input(
                    properties = buildJsonObject { put("type", JsonPrimitive("object")) },
                    required = emptyList(),
                ),
        )
    }

    @Suppress("UNUSED_PARAMETER")
    internal suspend fun captureAccessibilityTree(arguments: Map<String, Any>): CallToolResult {
        val activity = currentActivity
        if (activity == null) {
            return CallToolResult(
                content = listOf(TextContent(text = "No active activity found")),
                isError = true,
            )
        }

        return withContext(Dispatchers.Main) {
            try {
                val rootView = activity.findViewById<View>(android.R.id.content)
                val result = buildString {
                    appendLine("Accessibility Tree:")
                    appendLine("Activity: ${activity.javaClass.simpleName}")
                    appendLine()
                    captureViewNode(rootView, 0)
                }

                CallToolResult(content = listOf(TextContent(text = result)), isError = false)
            } catch (e: Exception) {
                Log.e(TAG, "Error capturing accessibility tree", e)
                CallToolResult(
                    content = listOf(TextContent(text = "Error: ${e.message}")),
                    isError = true,
                )
            }
        }
    }

    @Suppress("UNUSED_PARAMETER")
    internal suspend fun validateAccessibility(arguments: Map<String, Any>): CallToolResult {
        val activity = currentActivity
        if (activity == null) {
            return CallToolResult(
                content = listOf(TextContent(text = "No active activity found")),
                isError = true,
            )
        }

        return withContext(Dispatchers.Main) {
            try {
                val rootView = activity.findViewById<View>(android.R.id.content)
                val issues = mutableListOf<String>()
                val focusableElements = mutableListOf<View>()
                collectFocusableElements(rootView, focusableElements)
                validateViewRecursive(rootView, issues, focusableElements)

                val result = buildString {
                    appendLine("Accessibility Validation Results:")
                    appendLine("Found ${issues.size} issues")
                    appendLine()
                    if (issues.isEmpty()) {
                        appendLine("No accessibility issues found.")
                    } else {
                        issues.forEach { issue -> appendLine("• $issue") }
                    }
                }

                CallToolResult(content = listOf(TextContent(text = result)), isError = false)
            } catch (e: Exception) {
                Log.e(TAG, "Error validating accessibility", e)
                CallToolResult(
                    content = listOf(TextContent(text = "Error: ${e.message}")),
                    isError = true,
                )
            }
        }
    }

    @Suppress("UNUSED_PARAMETER")
    internal suspend fun getAccessibilityServiceStatus(
        arguments: Map<String, Any>
    ): CallToolResult {
        return withContext(Dispatchers.IO) {
            try {
                val accessibilityManager =
                    context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager

                val enabledServices =
                    accessibilityManager.getEnabledAccessibilityServiceList(
                        AccessibilityServiceInfo.FEEDBACK_ALL_MASK
                    )

                val result = buildString {
                    appendLine("Accessibility Service Status:")
                    appendLine("Enabled: ${accessibilityManager.isEnabled}")
                    appendLine(
                        "Touch Exploration: ${accessibilityManager.isTouchExplorationEnabled}"
                    )
                    appendLine("Running Services: ${enabledServices.size}")
                    if (enabledServices.isEmpty()) {
                        appendLine("  (None)")
                    } else {
                        enabledServices.forEach { service ->
                            appendLine("  • ${service.resolveInfo.serviceInfo.name}")
                        }
                    }
                }

                CallToolResult(content = listOf(TextContent(text = result)), isError = false)
            } catch (e: Exception) {
                Log.e(TAG, "Error getting accessibility service status", e)
                CallToolResult(
                    content = listOf(TextContent(text = "Error: ${e.message}")),
                    isError = true,
                )
            }
        }
    }

    internal fun StringBuilder.captureViewNode(view: View, depth: Int) {
        val indent = "  ".repeat(depth)
        val bounds = Rect()
        view.getGlobalVisibleRect(bounds)

        val nodeInfo = AccessibilityNodeInfoCompat.wrap(view.createAccessibilityNodeInfo())

        appendLine("${indent}${view.javaClass.simpleName} - ${getViewIdName(view) ?: "No ID"}")
        appendLine("$indent  Text: ${getViewText(view) ?: "None"}")
        appendLine("$indent  Content Desc: ${nodeInfo.contentDescription ?: "None"}")
        appendLine("$indent  Bounds: ${bounds.left},${bounds.top},${bounds.right},${bounds.bottom}")
        appendLine("$indent  Visible: ${view.isVisible} (ToUser: ${nodeInfo.isVisibleToUser})")
        appendLine("$indent  Enabled: ${view.isEnabled}")
        appendLine("$indent  Clickable: ${view.isClickable}")
        appendLine("$indent  Long Clickable: ${view.isLongClickable}")
        appendLine("$indent  Focusable: ${view.isFocusable}")
        appendLine("$indent  Is Heading: ${nodeInfo.isHeading}")
        appendLine("$indent  Role: ${getRoleDescription(nodeInfo)}")
        appendLine("$indent  Accessibility Actions: ${getAccessibilityActionNames(nodeInfo)}")
        appendLine("$indent  Labeled By: ${nodeInfo.labeledBy?.viewIdResourceName ?: "None"}")
        nodeInfo.labelFor?.let {
            appendLine(
                "${indent}  Label For: ${
                    it.viewIdResourceName
                }"
            )
        }
        if (view is EditText) {
            appendLine("$indent  Hint: ${view.hint ?: "None"}")
        }

        if (view is TextView) {
            appendLine(
                "$indent  Text Size: ${
                    TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_PX,
                        view.textSize,
                        context.resources.displayMetrics,
                    ) / context.resources.displayMetrics.density
                }sp"
            )
            appendLine("$indent  Text Color: #${Integer.toHexString(view.currentTextColor)}")
            getBackgroundColor(view)?.let {
                appendLine("$indent  Background Color: #${Integer.toHexString(it)}")
            }
            appendLine("$indent  Line Spacing Multiplier: ${view.lineSpacingMultiplier}")
            appendLine(
                "$indent  Line Spacing Extra: ${
                    TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_PX,
                        view.lineSpacingExtra,
                        context.resources.displayMetrics,
                    ) / context.resources.displayMetrics.density
                }sp"
            )
            appendLine("$indent  Text Alignment: ${view.textAlignment}")
        }

        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                captureViewNode(view.getChildAt(i), depth + 1)
            }
        }
    }

    internal fun validateViewRecursive(
        view: View,
        issues: MutableList<String>,
        focusableElements: List<View>,
    ) {
        val nodeInfo = AccessibilityNodeInfoCompat.wrap(view.createAccessibilityNodeInfo())
        val viewId = getViewIdName(view) ?: view.javaClass.simpleName

        // Check touch target size
        if (view.isClickable) {
            val density = view.context.resources.displayMetrics.density
            val minSizePx = (MIN_TOUCH_TARGET_SIZE_DP * density).toInt()
            if (view.width < minSizePx || view.height < minSizePx) {
                issues.add(
                    "[$viewId] Touch target too small: ${view.width}x${view.height}px (min ${minSizePx}px)"
                )
            }
        }

        // Check content descriptions and generic text for interactive elements
        if (isAccessibilityFocusable(view)) {
            val text = getViewText(view)?.toString()?.lowercase()
            val contentDesc = nodeInfo.contentDescription?.toString()?.lowercase()

            if (TextUtils.isEmpty(text) && TextUtils.isEmpty(contentDesc)) {
                issues.add("[$viewId] Interactive element lacks text or content description.")
            } else if (view.isClickable || isButtonRole(nodeInfo)) {
                val effectiveText = contentDesc ?: text
                if (effectiveText != null && GENERIC_INTERACTIVE_TEXT.contains(effectiveText)) {
                    issues.add(
                        "[$viewId] Uses generic text ('$effectiveText') for interactive element. Provide more context."
                    )
                }
            }
        }

        // Unlabeled Form Inputs
        if (view is EditText) {
            val hasLabel =
                nodeInfo.labeledBy != null ||
                    !TextUtils.isEmpty(view.hint) ||
                    !TextUtils.isEmpty(nodeInfo.contentDescription)
            if (!hasLabel) {
                issues.add("[$viewId] EditText is missing a label, hint, or contentDescription.")
            }
        }

        // Redundant Content Description for TextViews
        if (view is TextView && !view.isClickable && !isButtonRole(nodeInfo)) {
            val viewText = view.text?.toString()?.trim()
            val contentDescText = nodeInfo.contentDescription?.toString()?.trim()
            if (
                !TextUtils.isEmpty(viewText) &&
                    !TextUtils.isEmpty(contentDescText) &&
                    viewText == contentDescText
            ) {
                issues.add(
                    "[$viewId] TextView has redundant contentDescription (same as visible text)."
                )
            }
        }

        // Check for announcements (live regions) and headers on navigation
        if (nodeInfo.paneTitle != null) {
            issues.add("[$viewId] Uses paneTitle (screen title), ensure it's announced correctly.")
        }
        if (nodeInfo.isHeading) {
            if (getViewText(view).isNullOrBlank() && nodeInfo.contentDescription.isNullOrBlank()) {
                issues.add("[$viewId] Heading lacks text or content description.")
            }
            if (view !is TextView) {
                issues.add(
                    "[$viewId] Non-TextView marked as heading. Consider using a TextView for headings."
                )
            }
            if (
                view is TextView &&
                    (TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_PX,
                        view.textSize,
                        context.resources.displayMetrics,
                    ) / context.resources.displayMetrics.density) < HEADING_MIN_TEXT_SIZE_SP
            ) {
                issues.add(
                    "[$viewId] Heading text size is too small (${
                        TypedValue.applyDimension(
                            TypedValue.COMPLEX_UNIT_PX,
                            view.textSize,
                            context.resources.displayMetrics,
                        ) / context.resources.displayMetrics.density
                    }sp). Min recommended: ${HEADING_MIN_TEXT_SIZE_SP}sp"
                )
            }
        }
        if (view.accessibilityLiveRegion != View.ACCESSIBILITY_LIVE_REGION_NONE) {
            issues.add("[$viewId] Is a live region. Ensure updates are announced properly.")
        }

        // Accessibility Visibility Mismatch
        if (nodeInfo.isVisibleToUser && view.visibility != View.VISIBLE) {
            issues.add(
                "[$viewId] View is visible to accessibility services but not visually (visibility: ${view.visibility}). Potential ghost element."
            )
        }
        if (!nodeInfo.isVisibleToUser && isAccessibilityFocusable(view) && view.isVisible) {
            issues.add(
                "[$viewId] View is visually visible and accessibility focusable, but reported as not visible to user by NodeInfo. Potential ghost element or misconfiguration."
            )
        }

        if (view is TextView) {
            // Check contrast
            val textColor = view.currentTextColor
            val bgColor = getBackgroundColor(view)
            if (bgColor != null) {
                val contrastRatio =
                    androidx.core.graphics.ColorUtils.calculateContrast(textColor, bgColor)
                val textSizeSp =
                    TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_PX,
                        view.textSize,
                        context.resources.displayMetrics,
                    ) / context.resources.displayMetrics.density
                val minContrast =
                    if (textSizeSp >= LARGE_TEXT_SIZE_SP || isBold(view))
                        MIN_CONTRAST_RATIO_LARGE_TEXT
                    else MIN_CONTRAST_RATIO_NORMAL_TEXT
                if (contrastRatio < minContrast) {
                    issues.add(
                        "[$viewId] Low text contrast: ${
                            String.format(
                                Locale.US,
                                "%.2f",
                                contrastRatio,
                            )
                        }:1 (min ${minContrast}:1 for text size ${textSizeSp}sp)"
                    )
                }
            }

            // Check minimum text size
            val textSizeSp =
                TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_PX,
                    view.textSize,
                    context.resources.displayMetrics,
                ) / context.resources.displayMetrics.density
            if (
                textSizeSp < MIN_TEXT_SIZE_SP && !nodeInfo.isHeading
            ) { // Don't double-penalize headings for small text if already caught
                issues.add(
                    "[$viewId] Text size too small: ${textSizeSp}sp (min ${MIN_TEXT_SIZE_SP}sp)"
                )
            }

            // TextView Line Spacing
            if (view.lineSpacingMultiplier < 1.0f) {
                issues.add(
                    "[$viewId] Line spacing multiplier is less than 1.0, which may affect readability. Recommended: 1.0 or higher."
                )
            }

            // TextView Justification Mode
            // Justification mode was added in API 26 (Build.VERSION_CODES.O).
            // Justified text can create uneven spacing and reduce readability ("rivers of white").
            if (view.justificationMode == Layout.JUSTIFICATION_MODE_INTER_WORD) {
                issues.add(
                    "[$viewId] TextView uses inter-word justification (API 26+), which can create uneven spacing and reduce readability."
                )
            }
            // JUSTIFICATION_MODE_INTER_CHARACTER was added in API 34
            // (Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
            if (
                Build.VERSION.SDK_INT >= 34
            ) { // Use 34 directly if UPSIDE_DOWN_CAKE constant is not available, though
                // Layout.JUSTIFICATION_MODE_INTER_CHARACTER implies API 34+
                if (view.justificationMode == Layout.JUSTIFICATION_MODE_INTER_CHARACTER) {
                    issues.add(
                        "[$viewId] TextView uses inter-character justification (API 34+), which can also impact readability."
                    )
                }
            }
            // Note: textAlignment (e.g., View.TEXT_ALIGNMENT_CENTER,
            // View.TEXT_ALIGNMENT_VIEW_START)
            // is different from justificationMode.
            // The previous check for `view.textAlignment == 6` (View.TEXT_ALIGNMENT_VIEW_END) was
            // incorrect for detecting justification.
        }

        // EditText with Error
        if (view is EditText) {
            if (view.error != null && nodeInfo.contentDescription != null) {
                issues.add(
                    "[$viewId] EditText has an error message but should ensure the error message is also announced to accessibility services."
                )
            }
        }

        // Clickable ImageView without ContentDescription
        if (
            view is ImageView && view.isClickable && TextUtils.isEmpty(nodeInfo.contentDescription)
        ) {
            issues.add(
                "[$viewId] Clickable ImageView lacks content description, making it inaccessible."
            )
        }

        // Check for custom onTouch listeners missing proper accessibility support
        if (
            view.isClickable &&
                nodeInfo.actionList.none { it.id == AccessibilityNodeInfoCompat.ACTION_CLICK }
        ) {
            issues.add(
                "[$viewId] Has custom click listener (or onTouch) but may not support click action for accessibility services."
            )
        }

        // Check for misuse of ImageView/TextView for buttons
        if (
            (view is ImageView || view is TextView) &&
                view.isClickable &&
                nodeInfo.roleDescription == null &&
                !isButtonRole(nodeInfo)
        ) {
            if (!isClearlySemantic(nodeInfo)) {
                issues.add(
                    "[$viewId] Clickable ${view.javaClass.simpleName} used. Consider using a Button or explicitly setting accessibility role for better semantics."
                )
            }
        }

        // Check for custom controls lacking roles (e.g. a clickable ViewGroup without a role)
        if (
            view is ViewGroup &&
                view.isClickable &&
                nodeInfo.roleDescription == null &&
                getPlatformSpecificRole(nodeInfo) == null
        ) {
            if (
                view.isEmpty() ||
                    view.children.all {
                        !it.isImportantForAccessibility &&
                            TextUtils.isEmpty(getViewText(it)) &&
                            TextUtils.isEmpty(
                                AccessibilityNodeInfoCompat.wrap(it.createAccessibilityNodeInfo())
                                    .contentDescription
                            )
                    }
            ) {
                issues.add(
                    "[$viewId] Clickable ViewGroup used as a custom control but lacks an accessibility role or descriptive content for its children. Consider setting a roleDescription or ensuring children are accessible."
                )
            }
        }

        // Check if information is conveyed by color alone (basic check for TextViews)
        if (view is TextView) {
            val textColor = view.currentTextColor
            val bgColor = getBackgroundColor(view)
            // This is a simplistic check. Real color-alone issues are more complex.
            // Example: if text changes color to indicate state, without other visual cues.
            // For now, we're flagging if the only distinguishing factor of similar elements might
            // be color.
            // A more advanced check would involve comparing to sibling/similar elements.
            if (
                bgColor != null &&
                    textColor != android.graphics.Color.TRANSPARENT &&
                    bgColor != android.graphics.Color.TRANSPARENT
            ) {
                // Heuristic: If text has no strong visual cues (bold, underline, significantly
                // larger size) AND its contrast is acceptable but not very high,
                // it *might* be relying on color if other similar elements exist.
                // A better check would require context of other elements.
                val textSizeSp =
                    TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_PX,
                        view.textSize,
                        context.resources.displayMetrics,
                    ) / context.resources.displayMetrics.density
                if (
                    !isBold(view) && textSizeSp < LARGE_TEXT_SIZE_SP * 1.2
                ) { // Not significantly larger or bold
                    val contrastRatio =
                        androidx.core.graphics.ColorUtils.calculateContrast(textColor, bgColor)
                    if (
                        contrastRatio > MIN_CONTRAST_RATIO_NORMAL_TEXT &&
                            contrastRatio < MIN_CONTRAST_RATIO_NORMAL_TEXT + 2.0
                    ) { // Acceptable, but not very high contrast
                        // issues.add("[$viewId] Text color might be used to convey information.
                        // Ensure there are non-color cues if so. (Heuristic)")
                        // This heuristic is too prone to false positives, commenting out for now.
                        // A better check would require context of other elements.
                        // TODO: Flag these elements so we can do another pass and properly check
                        // with context
                    }
                }
            }
        }

        // Check for logical focus order (basic proximity check)
        val currentIndex = focusableElements.indexOf(view)
        if (currentIndex != -1 && currentIndex < focusableElements.size - 1) {
            val nextFocusableView = focusableElements[currentIndex + 1]
            checkFocusOrderProximity(view, nextFocusableView, issues)
        }

        // Check for Keyboard Trap: If a ViewGroup is focusable, it should usually not trap focus
        // from its children
        if (
            view is ViewGroup &&
                view.isFocusable &&
                view.descendantFocusability != ViewGroup.FOCUS_BLOCK_DESCENDANTS
        ) {
            var hasFocusableChild = false
            for (i in 0 until view.childCount) {
                if (isAccessibilityFocusable(view.getChildAt(i))) {
                    hasFocusableChild = true
                    break
                }
            }
            if (hasFocusableChild) {
                issues.add(
                    "[$viewId] Focusable ViewGroup might trap focus. Consider `descendantFocusability = FOCUS_BLOCK_DESCENDANTS` or making the ViewGroup not focusable if it's just a container."
                )
            }
        }

        // Recursively check children
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                validateViewRecursive(view.getChildAt(i), issues, focusableElements)
            }
        }
    }

    // Utility methods
    internal fun isAccessibilityFocusable(view: View): Boolean {
        return ViewCompat.isAccessibilityHeading(view) ||
            (view.isFocusable && view.isVisible && view.isEnabled) || // Standard focusable
            (view.isClickable &&
                view.isVisible &&
                view.isEnabled) || // Clickable implies focusable for a11y
            (!TextUtils.isEmpty(view.contentDescription) &&
                view.isVisible &&
                view.isEnabled) || // Has content desc and is interactable
            (view is TextView &&
                !TextUtils.isEmpty(getViewText(view)) &&
                view.isVisible &&
                view.isEnabled) // TextView with text
    }

    internal fun getViewIdName(view: View): String? {
        return try {
            if (view.id != View.NO_ID) {
                view.context.resources.getResourceEntryName(view.id)?.let { entryName ->
                    view.context.resources.getResourcePackageName(view.id) +
                        ":" +
                        view.context.resources.getResourceTypeName(view.id) +
                        "/" +
                        entryName
                } ?: view.id.toString()
            } else null
        } catch (_: Exception) {
            view.id.takeIf { it != View.NO_ID }?.toString()
        }
    }

    internal fun getViewText(view: View): CharSequence? {
        return when (view) {
            is TextView ->
                view.text // Includes Button, EditText. Return CharSequence for richer info.
            else -> null
        }
    }

    internal fun getBackgroundColor(view: View): Int? {
        var currentView: View? = view
        while (
            currentView != null && currentView.background == null && currentView.parent is View
        ) {
            currentView = currentView.parent as View
        }
        return (currentView?.background as? android.graphics.drawable.ColorDrawable)?.color
    }

    internal fun isBold(textView: TextView): Boolean {
        val typeface = textView.typeface
        return typeface != null && typeface.isBold
    }

    internal fun getAccessibilityActionNames(nodeInfo: AccessibilityNodeInfoCompat): String {
        return nodeInfo.actionList
            .joinToString { action ->
                when (action.id) {
                    AccessibilityNodeInfoCompat.ACTION_FOCUS -> "FOCUS"
                    AccessibilityNodeInfoCompat.ACTION_CLEAR_FOCUS -> "CLEAR_FOCUS"
                    AccessibilityNodeInfoCompat.ACTION_SELECT -> "SELECT"
                    AccessibilityNodeInfoCompat.ACTION_CLEAR_SELECTION -> "CLEAR_SELECTION"
                    AccessibilityNodeInfoCompat.ACTION_CLICK -> "CLICK"
                    AccessibilityNodeInfoCompat.ACTION_LONG_CLICK -> "LONG_CLICK"
                    AccessibilityNodeInfoCompat.ACTION_ACCESSIBILITY_FOCUS -> "ACCESSIBILITY_FOCUS"
                    AccessibilityNodeInfoCompat.ACTION_CLEAR_ACCESSIBILITY_FOCUS ->
                        "CLEAR_ACCESSIBILITY_FOCUS"
                    AccessibilityNodeInfoCompat.ACTION_NEXT_AT_MOVEMENT_GRANULARITY ->
                        "NEXT_AT_MOVEMENT_GRANULARITY"
                    AccessibilityNodeInfoCompat.ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY ->
                        "PREVIOUS_AT_MOVEMENT_GRANULARITY"
                    AccessibilityNodeInfoCompat.ACTION_NEXT_HTML_ELEMENT -> "NEXT_HTML_ELEMENT"
                    AccessibilityNodeInfoCompat.ACTION_PREVIOUS_HTML_ELEMENT ->
                        "PREVIOUS_HTML_ELEMENT"
                    AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD -> "SCROLL_FORWARD"
                    AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD -> "SCROLL_BACKWARD"
                    AccessibilityNodeInfoCompat.ACTION_COPY -> "COPY"
                    AccessibilityNodeInfoCompat.ACTION_PASTE -> "PASTE"
                    AccessibilityNodeInfoCompat.ACTION_CUT -> "CUT"
                    AccessibilityNodeInfoCompat.ACTION_SET_SELECTION -> "SET_SELECTION"
                    AccessibilityNodeInfoCompat.ACTION_EXPAND -> "EXPAND"
                    AccessibilityNodeInfoCompat.ACTION_COLLAPSE -> "COLLAPSE"
                    AccessibilityNodeInfoCompat.ACTION_DISMISS -> "DISMISS"
                    AccessibilityNodeInfoCompat.ACTION_SET_TEXT -> "SET_TEXT"
                    // Add more cases as needed from AccessibilityNodeInfoCompat
                    else -> action.label?.toString() ?: "CUSTOM_ACTION (${action.id})"
                }
            }
            .ifEmpty { "None" }
    }

    internal fun getRoleDescription(nodeInfo: AccessibilityNodeInfoCompat): String {
        return nodeInfo.roleDescription?.toString() ?: getPlatformSpecificRoleDescription(nodeInfo)
    }

    internal fun isButtonRole(nodeInfo: AccessibilityNodeInfoCompat): Boolean {
        return nodeInfo.className?.toString() == Button::class.java.name ||
            nodeInfo.className?.toString()?.contains("Button") == true
    }

    internal fun getPlatformSpecificRole(nodeInfo: AccessibilityNodeInfoCompat): String? {
        // Use AccessibilityNodeInfoCompat properties instead of reflection
        if (nodeInfo.collectionInfo != null) return "ROLE_LIST"
        if (nodeInfo.collectionItemInfo != null) return "ROLE_LIST_ITEM"

        // Check for standard roles by class name
        return when (nodeInfo.className?.toString()) {
            Button::class.java.name -> "ROLE_BUTTON"
            EditText::class.java.name -> "ROLE_EDIT_TEXT"
            ImageView::class.java.name -> "ROLE_IMAGE"
            "android.widget.CheckBox" -> "ROLE_CHECK_BOX"
            "android.widget.RadioButton" -> "ROLE_RADIO_BUTTON"
            "android.widget.Switch" -> "ROLE_SWITCH"
            "android.widget.SeekBar" -> "ROLE_SEEK_CONTROL"
            "android.widget.ProgressBar" -> "ROLE_PROGRESS_BAR"
            else -> null
        }
    }

    internal fun getPlatformSpecificRoleDescription(nodeInfo: AccessibilityNodeInfoCompat): String {
        val role = getPlatformSpecificRole(nodeInfo)
        // Prefer roleDescription if explicitly set by the developer
        if (!TextUtils.isEmpty(nodeInfo.roleDescription)) {
            return nodeInfo.roleDescription.toString()
        }
        return when (role) {
            "ROLE_BUTTON" -> "Button"
            "ROLE_CHECK_BOX" -> "CheckBox"
            "ROLE_EDIT_TEXT" -> "EditText"
            "ROLE_GRID" -> "Grid"
            "ROLE_IMAGE" -> "Image"
            "ROLE_LIST" -> "List"
            "ROLE_LIST_ITEM" -> "ListItem"
            "ROLE_NONE" -> "None"
            "ROLE_PAGER" -> "Pager"
            "ROLE_PROGRESS_BAR" -> "ProgressBar"
            "ROLE_RADIO_BUTTON" -> "RadioButton"
            "ROLE_SEEK_CONTROL" -> "SeekControl"
            "ROLE_SWITCH" -> "Switch"
            "ROLE_TAB_BAR" -> "TabBar"
            "ROLE_TEXT_ENTRY_KEY" -> "TextEntryKey"
            "ROLE_VIEW_GROUP" -> "ViewGroup"
            else -> nodeInfo.className?.toString() ?: "Unknown"
        }
    }

    internal fun isClearlySemantic(nodeInfo: AccessibilityNodeInfoCompat): Boolean {
        // If it has a specific accessibility role set, assume it's intentional
        if (!TextUtils.isEmpty(nodeInfo.roleDescription)) {
            return true
        }
        val platformRole = getPlatformSpecificRole(nodeInfo)
        if (platformRole != null && platformRole != "ROLE_NONE") {
            // Check if the role is one that typically implies interactivity or specific semantics
            when (platformRole) {
                "ROLE_BUTTON",
                "ROLE_CHECK_BOX",
                "ROLE_SWITCH",
                "ROLE_RADIO_BUTTON",
                "ROLE_SEEK_CONTROL",
                "ROLE_IMAGE" // Image can be semantic if it has alt text
                -> return true
            }
        }
        // If it's a standard Android widget known to be interactive
        if (
            nodeInfo.className != null &&
                (nodeInfo.className.contains(
                    "Button",
                    ignoreCase = true,
                ) || // Catches ImageButton too
                    nodeInfo.className.contains("CheckBox", ignoreCase = true) ||
                    nodeInfo.className.contains("RadioButton", ignoreCase = true) ||
                    nodeInfo.className.contains("Switch", ignoreCase = true) ||
                    nodeInfo.className.contains("SeekBar", ignoreCase = true))
        ) {
            return true
        }
        return false
    }

    internal fun collectFocusableElements(view: View, focusableElements: MutableList<View>) {
        if (isAccessibilityFocusable(view)) {
            focusableElements.add(view)
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                collectFocusableElements(view.getChildAt(i), focusableElements)
            }
        }
    }

    internal fun checkFocusOrderProximity(
        currentView: View,
        nextView: View,
        issues: MutableList<String>,
    ) {
        val currentRect = Rect()
        currentView.getGlobalVisibleRect(currentRect)
        val nextRect = Rect()
        nextView.getGlobalVisibleRect(nextRect)

        val currentCenterX = currentRect.centerX()
        val currentCenterY = currentRect.centerY()
        val nextCenterX = nextRect.centerX()
        val nextCenterY = nextRect.centerY()

        val distance =
            sqrt(
                    (currentCenterX - nextCenterX).toDouble().pow(2.0) +
                        (currentCenterY - nextCenterY).toDouble().pow(2.0)
                )
                .toFloat()

        // Heuristic: if the distance is too large compared to the screen diagonal, it might be a
        // jump
        val avgElementSize =
            (currentRect.width() + currentRect.height() + nextRect.width() + nextRect.height()) /
                4.0f

        // If next element is significantly far (e.g. > 2.5 times its own average size) from current
        // element in a different logical direction
        // This is a very basic heuristic and can be improved.
        val verticalJump =
            abs(currentCenterY - nextCenterY) > avgElementSize * FOCUS_ORDER_MAX_DISTANCE_FACTOR &&
                abs(currentCenterX - nextCenterX) < avgElementSize
        val horizontalJump =
            abs(currentCenterX - nextCenterX) > avgElementSize * FOCUS_ORDER_MAX_DISTANCE_FACTOR &&
                abs(currentCenterY - nextCenterY) < avgElementSize
        val diagonalJumpFar =
            distance >
                avgElementSize * (FOCUS_ORDER_MAX_DISTANCE_FACTOR + 1) // More lenient for diagonals

        if (verticalJump || horizontalJump || diagonalJumpFar) {
            val currentViewId = getViewIdName(currentView) ?: currentView.javaClass.simpleName
            val nextViewId = getViewIdName(nextView) ?: nextView.javaClass.simpleName
            issues.add(
                "[$currentViewId -> $nextViewId] Potential illogical focus jump. Distance: ${
                    String.format(
                        Locale.US,
                        "%.0f",
                        distance,
                    )
                }px. Check reading order."
            )
        }
    }
}
