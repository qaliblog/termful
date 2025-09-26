#!/bin/bash

# Termful Build Environment Setup Script

echo "Setting up Termful build environment..."

# Check if Android SDK is installed
if [ -z "$ANDROID_HOME" ] && [ ! -f "local.properties" ]; then
    echo "Android SDK not found. Please install Android SDK and set ANDROID_HOME environment variable"
    echo "or create a local.properties file with sdk.dir pointing to your Android SDK installation."
    echo ""
    echo "Example local.properties content:"
    echo "sdk.dir=/path/to/your/android/sdk"
    echo ""
    echo "You can download Android SDK from: https://developer.android.com/studio"
    exit 1
fi

# Check if we have internet connectivity for downloading Alpine rootfs
echo "Checking internet connectivity..."
if ! ping -c 1 dl-cdn.alpinelinux.org > /dev/null 2>&1; then
    echo "Warning: Cannot reach Alpine Linux CDN. Internet connection required for downloading Alpine rootfs."
fi

# Make gradlew executable
chmod +x gradlew

echo "Build environment ready!"
echo ""
echo "To build Termful:"
echo "1. Run: ./gradlew clean"
echo "2. Run: ./gradlew assembleDebug"
echo ""
echo "The Alpine Linux rootfs will be automatically downloaded during the build process."
echo "Built APK will be available in app/build/outputs/apk/"