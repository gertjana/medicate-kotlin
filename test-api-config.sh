#!/bin/bash
# Test script to verify API configuration in built frontend

echo "=== Testing Frontend API Configuration ==="

if [ ! -d "frontend/build" ]; then
    echo "❌ Frontend not built. Run: cd frontend && npm run build"
    exit 1
fi

echo ""
echo "Checking for 'browser' import in built files..."
if grep -r "browser" frontend/build/server/chunks/*.js 2>/dev/null | head -3; then
    echo "✓ Found browser-aware code"
else
    echo "⚠ No browser checks found in build"
fi

echo ""
echo "Checking for API_BASE configuration..."
if grep -r "127.0.0.1:8080" frontend/build/server/chunks/*.js 2>/dev/null | head -3; then
    echo "✓ Found internal API URL (127.0.0.1:8080) for SSR"
else
    echo "⚠ Internal API URL not found in build"
fi

echo ""
echo "Checking source file..."
grep -A 3 "const API_BASE" frontend/src/lib/api.ts

echo ""
echo "=== Test Complete ==="
