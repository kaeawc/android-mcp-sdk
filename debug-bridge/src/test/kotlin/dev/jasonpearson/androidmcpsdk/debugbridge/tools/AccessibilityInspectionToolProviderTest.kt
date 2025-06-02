package dev.jasonpearson.androidmcpsdk.debugbridge.tools

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.res.Resources
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.util.DisplayMetrics
import android.view.View
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.jasonpearson.androidmcpsdk.core.features.tools.ToolRegistry
import io.mockk.*
import io.modelcontextprotocol.kotlin.sdk.TextContent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [29])
class AccessibilityInspectionToolProviderTest {

    private lateinit var context: Context
    private lateinit var toolRegistry: ToolRegistry
    private lateinit var accessibilityManager: AccessibilityManager
    private lateinit var resources: Resources
    private lateinit var displayMetrics: DisplayMetrics
    private lateinit var provider: AccessibilityInspectionToolProvider

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        toolRegistry = mockk(relaxed = true)
        accessibilityManager = mockk(relaxed = true)
        resources = mockk(relaxed = true)
        displayMetrics = mockk(relaxed = true)

        // Return null for applicationContext to skip the Activity lifecycle callbacks
        every { context.applicationContext } returns null

        // Setup basic context services
        every { context.getSystemService(Context.ACCESSIBILITY_SERVICE) } returns
            accessibilityManager
        every { context.resources } returns resources
        every { resources.displayMetrics } returns displayMetrics
        //        every { displayMetrics.density } returns 2.0f

        provider = AccessibilityInspectionToolProvider(context)
    }

    @After
    fun tearDown() {
        clearAllMocks()
    }

    @Test
    fun `registerTools should add exactly 3 accessibility tools to registry`() {
        // When
        provider.registerTools(toolRegistry)

        // Then
        verify(exactly = 3) { toolRegistry.addTool(any(), any()) }
    }

    @Test
    fun `should register accessibility_capture tool`() {
        // When
        provider.registerTools(toolRegistry)

        // Then
        verify { toolRegistry.addTool(match { it.name == "accessibility_capture" }, any()) }
    }

    @Test
    fun `should register accessibility_validate tool`() {
        // When
        provider.registerTools(toolRegistry)

        // Then
        verify { toolRegistry.addTool(match { it.name == "accessibility_validate" }, any()) }
    }

    @Test
    fun `should register accessibility_service_status tool`() {
        // When
        provider.registerTools(toolRegistry)

        // Then
        verify { toolRegistry.addTool(match { it.name == "accessibility_service_status" }, any()) }
    }

    @Test
    fun `captureAccessibilityTree should return error when no active activity`() = runTest {
        // Given
        provider.currentActivity = null

        // When
        val result = provider.captureAccessibilityTree(emptyMap())

        // Then
        assertTrue(result.isError ?: false)
        assertEquals("No active activity found", (result.content.first() as TextContent).text!!)
    }

    @Test
    fun `validateAccessibility should return error when no active activity`() = runTest {
        // Given
        provider.currentActivity = null

        // When
        val result = provider.validateAccessibility(emptyMap())

        // Then
        assertTrue(result.isError ?: false)
        assertEquals("No active activity found", (result.content.first() as TextContent).text!!)
    }

    @Test
    fun `getAccessibilityServiceStatus should return service information`() = runTest {
        // Given
        val enabledServices = listOf<AccessibilityServiceInfo>()
        every { accessibilityManager.isEnabled } returns true
        every { accessibilityManager.isTouchExplorationEnabled } returns false
        every { accessibilityManager.getEnabledAccessibilityServiceList(any()) } returns
            enabledServices

        // When
        val result = provider.getAccessibilityServiceStatus(emptyMap())

        // Then
        assertFalse(result.isError ?: false)
        val content = (result.content.first() as TextContent).text!!
        assertTrue(content.contains("Accessibility Service Status:"))
        assertTrue(content.contains("Enabled: true"))
        assertTrue(content.contains("Touch Exploration: false"))
        assertTrue(content.contains("Running Services: 0"))
    }

    @Test
    fun `isAccessibilityFocusable should return true for clickable enabled visible view`() {
        // Given
        val view = mockk<Button>(relaxed = true)
        every { view.isClickable } returns true
        every { view.visibility } returns View.VISIBLE // Fix: mock visibility as Int
        every { view.isEnabled } returns true
        every { view.isFocusable } returns false
        every { view.contentDescription } returns null

        // When
        val result = provider.isAccessibilityFocusable(view)

        // Then
        assertTrue(result)
    }

    @Test
    fun `isAccessibilityFocusable should return false for disabled view`() {
        // Given
        val view = mockk<Button>(relaxed = true)
        every { view.isClickable } returns true
        every { view.visibility } returns View.VISIBLE // Fix: mock visibility as Int
        every { view.isEnabled } returns false

        // When
        val result = provider.isAccessibilityFocusable(view)

        // Then
        assertFalse(result)
    }

    @Test
    fun `getViewIdName should return proper resource name`() {
        // Given
        val view = mockk<View>(relaxed = true)
        every { view.id } returns 12345
        every { view.context } returns context
        every { resources.getResourceEntryName(12345) } returns "button_submit"
        every { resources.getResourcePackageName(12345) } returns "com.example"
        every { resources.getResourceTypeName(12345) } returns "id"

        // When
        val result = provider.getViewIdName(view)

        // Then
        assertEquals("com.example:id/button_submit", result)
    }

    @Test
    fun `getViewIdName should return null for NO_ID`() {
        // Given
        val view = mockk<View>(relaxed = true)
        every { view.id } returns View.NO_ID

        // When
        val result = provider.getViewIdName(view)

        // Then
        assertNull(result)
    }

    @Test
    fun `getViewText should return text for TextView`() {
        // Given
        val textView = mockk<TextView>(relaxed = true)
        every { textView.text } returns "Hello World"

        // When
        val result = provider.getViewText(textView)

        // Then
        assertEquals("Hello World", result.toString())
    }

    @Test
    fun `getViewText should return null for non-TextView`() {
        // Given
        val imageView = mockk<ImageView>(relaxed = true)

        // When
        val result = provider.getViewText(imageView)

        // Then
        assertNull(result)
    }

    @Test
    fun `getBackgroundColor should return color from ColorDrawable`() {
        // Given
        val view = mockk<View>(relaxed = true)
        val colorDrawable = mockk<ColorDrawable>(relaxed = true)
        every { view.background } returns colorDrawable
        every { colorDrawable.color } returns 0xFF0000FF.toInt() // Blue

        // When
        val result = provider.getBackgroundColor(view)

        // Then
        assertEquals(0xFF0000FF.toInt(), result)
    }

    @Test
    fun `isBold should return true for bold typeface`() {
        // Given
        val textView = mockk<TextView>(relaxed = true)
        val typeface = mockk<Typeface>(relaxed = true)
        every { textView.typeface } returns typeface
        every { typeface.isBold } returns true

        // When
        val result = provider.isBold(textView)

        // Then
        assertTrue(result)
    }

    @Test
    fun `isBold should return false for regular typeface`() {
        // Given
        val textView = mockk<TextView>(relaxed = true)
        val typeface = mockk<Typeface>(relaxed = true)
        every { textView.typeface } returns typeface
        every { typeface.isBold } returns false

        // When
        val result = provider.isBold(textView)

        // Then
        assertFalse(result)
    }

    @Test
    fun `isButtonRole should return true for Button class`() {
        // Given
        val nodeInfo = mockk<AccessibilityNodeInfoCompat>(relaxed = true)
        every { nodeInfo.className } returns Button::class.java.name

        // When
        val result = provider.isButtonRole(nodeInfo)

        // Then
        assertTrue(result)
    }

    @Test
    fun `collectFocusableElements should collect focusable views`() {
        // Given
        val rootView = mockk<LinearLayout>(relaxed = true)
        val button = mockk<Button>(relaxed = true)
        val focusableElements = mutableListOf<View>()

        // Setup root view with one child
        every { rootView.childCount } returns 1
        every { rootView.getChildAt(0) } returns button

        // Mock button as focusable - fix visibility mocking
        every { button.isClickable } returns true
        every { button.visibility } returns View.VISIBLE // Mock visibility as Int, not Boolean
        every { button.isEnabled } returns true
        every { button.isFocusable } returns false
        every { button.contentDescription } returns null

        // Root view not focusable - fix visibility mocking
        every { rootView.isClickable } returns false
        every { rootView.visibility } returns View.VISIBLE // Mock visibility as Int, not Boolean
        every { rootView.isEnabled } returns true
        every { rootView.isFocusable } returns false
        every { rootView.contentDescription } returns null

        // When
        provider.collectFocusableElements(rootView, focusableElements)

        // Then
        assertEquals(1, focusableElements.size)
        assertEquals(button, focusableElements[0])
    }

    // Helper method for creating mock accessibility node info
    private fun mockAccessibilityNodeInfo(): AccessibilityNodeInfoCompat {
        val nodeInfo = mockk<AccessibilityNodeInfoCompat>(relaxed = true)
        val unwrapped = mockk<android.view.accessibility.AccessibilityNodeInfo>(relaxed = true)

        every { nodeInfo.unwrap() } returns unwrapped
        every { nodeInfo.contentDescription } returns null
        every { nodeInfo.isVisibleToUser } returns true
        every { nodeInfo.isHeading } returns false
        every { nodeInfo.roleDescription } returns null
        every { nodeInfo.labeledBy } returns null
        every { nodeInfo.labelFor } returns null
        every { nodeInfo.className } returns null
        every { nodeInfo.paneTitle } returns null
        every { nodeInfo.collectionInfo } returns null
        every { nodeInfo.collectionItemInfo } returns null
        every { nodeInfo.actionList } returns emptyList()

        return nodeInfo
    }
}
