package dev.jasonpearson.androidmcpsdk.debugbridge.tools

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.jasonpearson.androidmcpsdk.core.features.tools.ToolProvider
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ViewHierarchyToolProviderTest {

    private lateinit var context: Context
    private lateinit var toolProvider: ToolProvider
    private lateinit var viewHierarchyProvider: ViewHierarchyToolProvider

    @Before
    fun setup() {
        context = mockk()

        // Mock context.applicationContext to return null (no Application lifecycle callbacks)
        every { context.applicationContext } returns null

        // Create real ToolProvider for testing actual registration
        toolProvider = ToolProvider(context)
        viewHierarchyProvider = ViewHierarchyToolProvider(context)
    }

    @Test
    fun `registerTools should add all view hierarchy tools to registry`() {
        // Get initial tool count
        val initialToolCount = toolProvider.getAllTools().size

        // When
        viewHierarchyProvider.registerTools(toolProvider)

        // Then - verify that exactly 6 tools are registered
        val finalToolCount = toolProvider.getAllTools().size
        assertEquals("Should add exactly 6 tools", 6, finalToolCount - initialToolCount)
    }

    @Test
    fun `should register view_hierarchy_capture tool`() {
        // When
        viewHierarchyProvider.registerTools(toolProvider)

        // Then
        val tools = toolProvider.getAllTools()
        assertTrue(
            "Should register view_hierarchy_capture tool",
            tools.any { it.name == "view_hierarchy_capture" },
        )
    }

    @Test
    fun `should register view_find_by_text tool`() {
        // When
        viewHierarchyProvider.registerTools(toolProvider)

        // Then
        val tools = toolProvider.getAllTools()
        assertTrue(
            "Should register view_find_by_text tool",
            tools.any { it.name == "view_find_by_text" },
        )
    }

    @Test
    fun `should register view_find_by_id tool`() {
        // When
        viewHierarchyProvider.registerTools(toolProvider)

        // Then
        val tools = toolProvider.getAllTools()
        assertTrue(
            "Should register view_find_by_id tool",
            tools.any { it.name == "view_find_by_id" },
        )
    }

    @Test
    fun `should register view_find_by_class tool`() {
        // When
        viewHierarchyProvider.registerTools(toolProvider)

        // Then
        val tools = toolProvider.getAllTools()
        assertTrue(
            "Should register view_find_by_class tool",
            tools.any { it.name == "view_find_by_class" },
        )
    }

    @Test
    fun `should register view_hierarchy_configure_streaming tool`() {
        // When
        viewHierarchyProvider.registerTools(toolProvider)

        // Then
        val tools = toolProvider.getAllTools()
        assertTrue(
            "Should register view_hierarchy_configure_streaming tool",
            tools.any { it.name == "view_hierarchy_configure_streaming" },
        )
    }

    @Test
    fun `should register view_hierarchy_get_recomposition_stats tool`() {
        // When
        viewHierarchyProvider.registerTools(toolProvider)

        // Then
        val tools = toolProvider.getAllTools()
        assertTrue(
            "Should register view_hierarchy_get_recomposition_stats tool",
            tools.any { it.name == "view_hierarchy_get_recomposition_stats" },
        )
    }
}
