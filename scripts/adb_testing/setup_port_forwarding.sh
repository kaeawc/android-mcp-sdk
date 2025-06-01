#!/bin/bash

# Android MCP SDK - ADB Port Forwarding Setup Script
set -e

# Default ports - adjusting to match actual server configuration  
SSE_PORT=${SSE_PORT:-8080}  # Server uses 8080 for SSE
DEVICE_SERIAL=${DEVICE_SERIAL:-}

echo "Setting up ADB port forwarding for Android MCP SDK..."

# Function to check if adb is available
check_adb() {
    if ! command -v adb &> /dev/null; then
        echo "Error: adb command not found. Please install Android SDK platform-tools."
        exit 1
    fi
}

# Function to check device connection
check_device() {
    local devices
    devices=$(adb devices | grep -v "List of devices" | grep -c "device$")
    if [ "$devices" -eq 0 ]; then
        echo "Error: No Android devices/emulators connected."
        echo "Please connect a device or start an emulator."
        exit 1
    elif [ "$devices" -gt 1 ] && [ -z "$DEVICE_SERIAL" ]; then
        echo "Multiple devices detected. Please specify DEVICE_SERIAL:"
        adb devices
        exit 1
    fi
}

# Function to setup port forwarding
setup_forwarding() {
    local adb_cmd="adb"
    if [ -n "$DEVICE_SERIAL" ]; then
        adb_cmd="adb -s $DEVICE_SERIAL"
    fi
    
    echo "Setting up port forwarding..."
    
    # Forward SSE port (server currently uses 8080 for HTTP/SSE)
    echo "Forwarding SSE port: $SSE_PORT"
    $adb_cmd forward tcp:"$SSE_PORT" tcp:"$SSE_PORT"
    
    echo "Port forwarding setup complete!"
}

# Function to verify forwarding
verify_forwarding() {
    local adb_cmd="adb"
    if [ -n "$DEVICE_SERIAL" ]; then
        adb_cmd="adb -s $DEVICE_SERIAL"
    fi
    
    echo "Verifying port forwarding..."
    if $adb_cmd forward --list | grep "tcp:$SSE_PORT"; then
        echo "✓ Port forwarding verified"
    else
        echo "✗ Port forwarding verification failed"
        exit 1
    fi
}

# Function to test connectivity
test_connectivity() {
    echo "Testing connectivity..."
    
    # Test HTTP/SSE endpoint
    if curl -s --max-time 5 "http://localhost:$SSE_PORT/mcp" > /dev/null; then
        echo "✓ HTTP/SSE endpoint accessible"
    else
        echo "✗ HTTP/SSE endpoint not accessible (this is normal if MCP server isn't running)"
    fi
}

# Main execution
main() {
    check_adb
    check_device
    setup_forwarding
    verify_forwarding
    test_connectivity
    
    echo ""
    echo "ADB port forwarding setup complete!"
    echo "SSE endpoint: http://localhost:$SSE_PORT/mcp"
    echo ""
    echo "Next steps:"
    echo "1. Launch your Android app with MCP server"
    echo "2. Connect MCP clients to: http://localhost:$SSE_PORT/mcp"
}

main "$@"
