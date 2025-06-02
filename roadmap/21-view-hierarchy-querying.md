# Task 21: View Hierarchy Querying

## Status: `[C]` Complete

## Objective

Implement UI view hierarchy inspection and querying capabilities, enabling MCP clients to analyze,
search, and interact with Android application UI components for testing and debugging purposes.

## Implementation Summary

**COMPLETED** - View hierarchy querying has been fully implemented with the following features:

✅ **ViewHierarchyToolProvider**: Complete implementation with 6 MCP tools
✅ **Modern Framework Support**: Detection and inspection of Slack's Circuit, Square's Workflow, and
Jetpack Compose
✅ **Activity Lifecycle Tracking**: Automatic tracking of current activity using
Application.ActivityLifecycleCallbacks
✅ **Hierarchy Capture**: Full view tree traversal with configurable depth and visibility filtering
✅ **Search Capabilities**: Find views by text content, resource ID, and class name with framework
detection
✅ **Package Information**: Detailed package name extraction for custom views (non-AndroidX/Android)
✅ **SSE Streaming**: Real-time streaming of view hierarchy changes over Server-Sent Events
✅ **Recomposition Tracking**: Compose recomposition count tracking and performance metrics
✅ **Security Features**: Password field masking and debug-only operation
✅ **Integration**: Registered with DebugBridgeToolContributor

### Implemented Tools

1. **`view_hierarchy_capture`** - Captures complete view hierarchy of active activity with modern
   framework support
2. **`view_find_by_text`** - Finds views containing specific text (exact or partial match) with
   framework detection
3. **`view_find_by_id`** - Finds views by resource ID (supports partial matching) with framework
   detection
4. **`view_find_by_class`** - Finds views by class name (simple or fully qualified) with framework
   detection
5. **`view_hierarchy_configure_streaming`** - Configure real-time streaming of view hierarchy
   changes over SSE
6. **`view_hierarchy_get_recomposition_stats`** - Get Compose recomposition statistics and
   performance metrics

### Key Features

- **Real-time Activity Tracking**: Uses ActivityLifecycleCallbacks to track current activity
- **Modern Framework Detection**: Identifies Slack's Circuit, Square's Workflow, and Compose
  components
- **Comprehensive View Information**: Captures ID, class, package, text, bounds, visibility,
  interaction state
- **Framework-Specific Metadata**: Extracts framework-specific information using reflection
- **Package Classification**: Distinguishes between Android, AndroidX, and custom view packages
- **Hierarchical Display**: Nested view structure with proper indentation and framework annotations
- **Security Conscious**: Masks password fields to prevent sensitive data exposure
- **Flexible Search**: Multiple search strategies with configurable exact/partial matching
- **SSE Streaming**: Configurable real-time streaming with custom polling intervals
- **Recomposition Metrics**: Tracks and reports Compose recomposition performance
- **Error Handling**: Graceful handling of missing activities and edge cases

## Requirements

### Technical Requirements

- **Hierarchy Traversal**: ✅ Complete view tree inspection
- **Property Extraction**: ✅ View attributes, bounds, visibility, text content
- **Search and Filtering**: ✅ Find views by ID, class, text, properties
- **Real-time Updates**: ✅ Live view hierarchy changes via activity tracking
- **Screenshot Integration**: ⚠️ Not implemented (bounds provided as text coordinates)
- **Accessibility Integration**: ⚠️ Not implemented (uses direct view access)

### Security Requirements

- **Sensitive Data Masking**: ✅ Hide passwords, secure text fields
- **Permission Validation**: ✅ Ensure proper UI access permissions
- **Debug-only Operations**: ✅ Restrict to debug builds only

## Implementation Steps

### Step 1: View Hierarchy Provider ✅

Created
`debug-bridge/src/main/kotlin/dev/jasonpearson/androidmcpsdk/debugbridge/tools/ViewHierarchyToolProvider.kt`:

- ✅ ViewNode data class with comprehensive view information
- ✅ Activity lifecycle tracking for current activity detection
- ✅ Recursive view traversal with depth and visibility controls
- ✅ Text extraction with password field protection
- ✅ Resource ID name resolution with fallback handling
- ✅ Framework detection and metadata extraction
- ✅ Real-time streaming configuration
- ✅ Compose recomposition tracking and metrics

### Step 2: MCP Tool Integration ✅

Added view hierarchy tools to `ToolProvider.kt`:

- ✅ `view_hierarchy_capture` - Complete hierarchy capture
- ✅ `view_find_by_text` - Text-based view search
- ✅ `view_find_by_id` - ID-based view search
- ✅ `view_find_by_class` - Class-based view search
- ✅ `view_hierarchy_configure_streaming` - Configure real-time streaming
- ✅ `view_hierarchy_get_recomposition_stats` - Get Compose recomposition statistics

### Step 3: Built-in Resources ⚠️

Built-in resources not implemented as originally planned:

- ❌ `android://ui/hierarchy` resource not created
- ❌ `android://ui/screenshot` resource not created

*Note: Tools provide equivalent functionality through direct MCP tool calls*

## Success Criteria

- ✅ Complete view hierarchy capture working
- ✅ View search and filtering functional
- ✅ Real-time hierarchy updates available (through activity tracking)
- ❌ Screenshot integration with view bounds (not implemented)
- ✅ Sensitive data properly masked
- ✅ Debug-only access enforced
- ✅ MCP tool integration complete
- ✅ Modern framework detection working
- ✅ Real-time streaming configured
- ✅ Compose recomposition metrics collected

## Dependencies

- ✅ **Core ResourceProvider**: Basic resource framework
- ✅ **Core ToolProvider**: Tool execution framework
- ✅ **Task 01**: Resource subscription logic (for real-time updates)

## Build Verification

- ✅ Code compiles successfully
- ✅ Project builds without errors
- ✅ Lint checks pass
- ✅ ktfmt formatting applied
- ✅ Integration with DebugBridgeToolContributor

## Resources

- [View System Overview](https://developer.android.com/guide/topics/ui/how-android-draws)
- [UI Testing](https://developer.android.com/training/testing/ui-testing)
- [Accessibility Services](https://developer.android.com/guide/topics/ui/accessibility/service)


