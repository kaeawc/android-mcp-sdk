# Task 22: Accessibility Inspection

## Status: `[C]` Complete

## Objective

Implement accessibility service integration for comprehensive UI accessibility analysis, enabling
MCP clients to inspect accessibility properties and validate accessibility compliance.

## Requirements

- **AccessibilityNodeInfo Analysis**: ✅ Extract accessibility properties from UI elements
- **Accessibility Service Integration**: ✅ Basic accessibility service status checking
- **Compliance Validation**: ✅ Check accessibility best practices (touch targets, content
  descriptions)
- **Navigation Testing**: ✅ Test accessibility navigation flows and focus order
- **Screen Reader Simulation**: ✅ Basic accessibility action support

## Implementation Steps

### Step 1: Accessibility Inspector ✅

```kotlin
class AccessibilityInspectionToolProvider(private val context: Context) {
    fun captureAccessibilityTree(arguments: Map<String, Any>): CallToolResult
    fun validateAccessibility(arguments: Map<String, Any>): CallToolResult  
    fun getAccessibilityServiceStatus(arguments: Map<String, Any>): CallToolResult
}
```

### Step 2: MCP Tool Integration ✅

Added tools:

- `accessibility_capture` - Capture accessibility tree of current activity
- `accessibility_validate` - Validate accessibility compliance
- `accessibility_service_status` - Get accessibility service status

## Implementation Details

The AccessibilityInspectionToolProvider has been implemented with the following features:

### Core Accessibility Features

- **Accessibility Tree Capture**: Recursively traverses view hierarchy extracting accessibility
  information
- **Touch Target Validation**: Checks minimum 48dp touch target size requirements
- **Content Description Validation**: Ensures interactive elements have proper text or descriptions
- **Password Field Detection**: Safely handles password fields with placeholder text
- **View ID Resolution**: Extracts resource names for better debugging

### MCP Tools Provided

1. **accessibility_capture**: Captures complete accessibility tree showing:
    - View class names and IDs
    - Text content and content descriptions
    - Bounds and layout information
    - Focusable and clickable states
    - Accessibility focusable status

2. **accessibility_validate**: Validates accessibility compliance checking for:
    - Touch targets smaller than 48dp minimum
    - Interactive elements lacking text or content descriptions
    - Recursive validation through view hierarchy

3. **accessibility_service_status**: Reports on accessibility services:
    - Whether accessibility services are enabled
    - Touch exploration status
    - List of running accessibility services

### Activity Lifecycle Integration

- Automatically tracks current activity through Application.ActivityLifecycleCallbacks
- Handles activity transitions and cleanup appropriately
- Works with any Android activity without additional setup

## Dependencies

- **Core ResourceProvider**: ✅ Basic resource framework
- **Task 21**: ✅ View Hierarchy Querying (for UI context)

## Success Criteria

- [x] Accessibility tree capture working
- [x] Accessibility compliance validation functional
- [x] Touch target size validation
- [x] Content description validation
- [x] Accessibility service status reporting
- [x] MCP tool integration complete
- [x] Activity lifecycle management
- [x] Integration with DebugBridgeToolContributor

## Usage Example

With the Android MCP SDK running, accessibility auditing can be performed via MCP clients:

```bash
# Capture current accessibility tree
mcp-tool accessibility_capture

# Validate accessibility compliance
mcp-tool accessibility_validate

# Check accessibility service status
mcp-tool accessibility_service_status
```

This enables comprehensive accessibility auditing and compliance validation for any Android
application integrated with the MCP SDK.
