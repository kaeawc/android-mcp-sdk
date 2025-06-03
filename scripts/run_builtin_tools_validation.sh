#!/bin/bash

# Built-in Tools Validation Script
# This script runs comprehensive validation tests for all built-in Android tools

set -euo pipefail

# Source common utilities
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

echo "🔧 Built-in Tools Validation"
echo "============================="

# Check if device is connected
if ! adb devices | grep -q "device$"; then
    echo "❌ No Android device found. Please connect a device or start an emulator."
    exit 1
fi

echo "📱 Device connected:"
adb devices

cd "$ROOT_DIR"

echo ""
echo "🏗️  Building debug-bridge module..."
./gradlew :debug-bridge:assembleDebug

echo ""
echo "🧪 Running individual tool validation tests..."

echo ""
echo "📋 Device Info Tool Test:"
./gradlew :debug-bridge:connectedDebugAndroidTest --tests "*DeviceInfoToolTest" || echo "❌ Device Info Test failed"

echo ""
echo "⏰ System Time Tool Test:"
./gradlew :debug-bridge:connectedDebugAndroidTest --tests "*SystemTimeToolTest" || echo "❌ System Time Test failed"

echo ""
echo "🎯 All Tools Integration Test:"
./gradlew :debug-bridge:connectedDebugAndroidTest --tests "*AllToolsValidationTest" || echo "❌ All Tools Test failed"

echo ""
echo "📊 Full Tool Validation Suite:"
./gradlew :debug-bridge:connectedDebugAndroidTest --tests "*.tools.*" || echo "❌ Some tests failed"

echo ""
echo "✅ Built-in tools validation completed!"
echo ""
echo "📖 To run individual tests manually:"
echo "   ./gradlew :debug-bridge:connectedDebugAndroidTest --tests '*DeviceInfoToolTest'"
echo "   ./gradlew :debug-bridge:connectedDebugAndroidTest --tests '*SystemTimeToolTest'"
echo "   ./gradlew :debug-bridge:connectedDebugAndroidTest --tests '*AllToolsValidationTest'"