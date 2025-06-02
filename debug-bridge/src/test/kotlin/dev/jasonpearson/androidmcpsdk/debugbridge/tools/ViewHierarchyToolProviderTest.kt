package dev.jasonpearson.androidmcpsdk.debugbridge.tools

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.jasonpearson.androidmcpsdk.core.features.tools.ToolRegistry
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ViewHierarchyToolProviderTest {

    private lateinit var context: Context
    private lateinit var toolRegistry: ToolRegistry
    private lateinit var viewHierarchyProvider: ViewHierarchyToolProvider

    @Before
    fun setup() {
        context = mockk()
        toolRegistry = mockk(relaxed = true)

        // Mock context.applicationContext to return null (no Application lifecycle callbacks)
        every { context.applicationContext } returns null

        viewHierarchyProvider = ViewHierarchyToolProvider(context)
    }

    @Test
    fun `registerTools should add all view hierarchy tools to registry`() {
        // When
        viewHierarchyProvider.registerTools(toolRegistry)

        // Then - verify that exactly 6 tools are registered
        verify(exactly = 6) { toolRegistry.addTool(any(), any()) }
    }

    @Test
    fun `should register view_hierarchy_capture tool`() {
        // When
        viewHierarchyProvider.registerTools(toolRegistry)

        // Then
        verify { toolRegistry.addTool(match { it.name == "view_hierarchy_capture" }, any()) }
    }

    @Test
    fun `should register view_find_by_text tool`() {
        // When
        viewHierarchyProvider.registerTools(toolRegistry)

        // Then
        verify { toolRegistry.addTool(match { it.name == "view_find_by_text" }, any()) }
    }

    @Test
    fun `should register view_find_by_id tool`() {
        // When
        viewHierarchyProvider.registerTools(toolRegistry)

        // Then
        verify { toolRegistry.addTool(match { it.name == "view_find_by_id" }, any()) }
    }

    @Test
    fun `should register view_find_by_class tool`() {
        // When
        viewHierarchyProvider.registerTools(toolRegistry)

        // Then
        verify { toolRegistry.addTool(match { it.name == "view_find_by_class" }, any()) }
    }

    @Test
    fun `should register view_hierarchy_configure_streaming tool`() {
        // When
        viewHierarchyProvider.registerTools(toolRegistry)

        // Then
        verify {
            toolRegistry.addTool(match { it.name == "view_hierarchy_configure_streaming" }, any())
        }
    }

    @Test
    fun `should register view_hierarchy_get_recomposition_stats tool`() {
        // When
        viewHierarchyProvider.registerTools(toolRegistry)

        // Then
        verify {
            toolRegistry.addTool(
                match { it.name == "view_hierarchy_get_recomposition_stats" },
                any(),
            )
        }
    }
}
