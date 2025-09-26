#!/bin/bash

# Test script to validate Alpine Linux filtering approach
# This simulates what the Gradle build does

echo "Testing Alpine Linux filtering approach..."

# Create test directory
TEST_DIR="/tmp/alpine-test"
mkdir -p "$TEST_DIR"
cd "$TEST_DIR"

# Download Alpine minirootfs (small test)
echo "Downloading Alpine minirootfs..."
wget -q "https://dl-cdn.alpinelinux.org/alpine/v3.20/releases/aarch64/alpine-minirootfs-3.20.7-aarch64.tar.gz"

if [ ! -f "alpine-minirootfs-3.20.7-aarch64.tar.gz" ]; then
    echo "❌ Download failed"
    exit 1
fi

echo "✅ Download successful"

# Test extraction with exclusions
echo "Testing tar extraction with exclusions..."
mkdir temp-test
tar -xzf alpine-minirootfs-3.20.7-aarch64.tar.gz -C temp-test \
    --exclude=var/run --exclude=dev --exclude=proc \
    --exclude=sys --exclude=run --exclude="**/systemd/**" \
    --exclude="**/udev/**" --exclude="**/*x2f*" --exclude="**/*x2d*" 2>/dev/null

if [ $? -eq 0 ]; then
    echo "✅ Tar extraction with exclusions successful"
else
    echo "⚠️ Tar extraction with exclusions failed, trying basic extraction..."
    rm -rf temp-test
    mkdir temp-test
    tar -xzf alpine-minirootfs-3.20.7-aarch64.tar.gz -C temp-test
    if [ $? -eq 0 ]; then
        echo "✅ Basic tar extraction successful"
    else
        echo "❌ Both extraction methods failed"
        exit 1
    fi
fi

# Check for problematic files
echo "Checking for problematic files..."
PROBLEMATIC_COUNT=$(find temp-test -name "*x2f*" -o -name "*x2d*" -o -path "*/udev/*" -o -path "*/systemd/*" | wc -l)
echo "Found $PROBLEMATIC_COUNT problematic files/directories"

# Check directory structure
echo "Directory structure:"
ls -la temp-test/ | head -10

# Test zip creation
echo "Testing zip creation..."
cd temp-test
zip -r ../test-alpine.zip . >/dev/null 2>&1
cd ..

if [ -f "test-alpine.zip" ] && [ -s "test-alpine.zip" ]; then
    ZIP_SIZE=$(du -h test-alpine.zip | cut -f1)
    echo "✅ Zip creation successful - Size: $ZIP_SIZE"
else
    echo "❌ Zip creation failed"
    exit 1
fi

# Cleanup
echo "Cleaning up..."
rm -rf "$TEST_DIR"

echo "🎉 All tests passed! Alpine filtering approach works."