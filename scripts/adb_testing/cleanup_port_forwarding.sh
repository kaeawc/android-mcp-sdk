#!/bin/bash

# Android MCP SDK - ADB Port Forwarding Cleanup Script
set -e

DEVICE_SERIAL=${DEVICE_SERIAL:-}

echo "Cleaning up ADB port forwarding..."

# Function to remove port forwarding
cleanup_forwarding() {
    local adb_cmd="adb"
    if [ -n "$DEVICE_SERIAL" ]; then
        adb_cmd="adb -s $DEVICE_SERIAL"
    fi
    
    echo "Removing all port forwarding rules..."
    $adb_cmd forward --remove-all
    
    echo "✓ Port forwarding cleanup complete"
}

# Function to verify cleanup
verify_cleanup() {
    local adb_cmd="adb"
    if [ -n "$DEVICE_SERIAL" ]; then
        adb_cmd="adb -s $DEVICE_SERIAL"
    fi
    
    local forwards
    forwards=$($adb_cmd forward --list | wc -l)
    if [ "$forwards" -eq 0 ]; then
        echo "✓ All port forwarding rules removed"
    else
        echo "! Some port forwarding rules still exist:"
        $adb_cmd forward --list
    fi
}

# Main execution
main() {
    cleanup_forwarding
    verify_cleanup
}

main "$@"
