# 🔧 Android SDK Conflict Resolution

## 🚨 Issue Identified
**Problem**: Multiple Android SDK paths causing Gradle build failures
```
ANDROID_HOME: /home/runner/android-sdk          # Our manual installation
ANDROID_SDK_ROOT: /usr/local/lib/android/sdk    # Pre-installed by GitHub runner
```

**Error**: 
> Several environment variables and/or system properties contain different paths to the SDK.
> Please correct and use only one way to inject the SDK location.

## ✅ Solution Implemented

### **1. Smart SDK Detection**
The workflows now:
1. **Check for pre-installed SDK** at `/usr/local/lib/android/sdk`
2. **Use existing SDK** if available (faster, more reliable)
3. **Fall back to manual installation** only if needed
4. **Clear conflicting environment variables**

### **2. Environment Variable Management**
```bash
# Clear the conflicting variable
unset ANDROID_SDK_ROOT
echo "ANDROID_SDK_ROOT=" >> $GITHUB_ENV

# Set only ANDROID_HOME
export ANDROID_HOME=/usr/local/lib/android/sdk
echo "ANDROID_HOME=/usr/local/lib/android/sdk" >> $GITHUB_ENV
```

### **3. Updated All Workflows**
- ✅ **`simple-build.yml`** - Prioritizes existing SDK
- ✅ **`build-termful.yml`** - Both main and test jobs fixed
- ✅ **`create-release-branch.yml`** - Consistent SDK setup
- 🆕 **`debug-environment.yml`** - Environment troubleshooting

## 🏗️ New Workflow Logic

### **Before (Problematic)**
```bash
# Always install manually → conflict with pre-installed SDK
wget android-sdk.zip
unzip and setup → ANDROID_HOME=/home/runner/android-sdk
# But ANDROID_SDK_ROOT already set to /usr/local/lib/android/sdk
```

### **After (Fixed)**
```bash
# Smart detection
if [ -d "/usr/local/lib/android/sdk" ]; then
  # Use existing → no conflicts
  export ANDROID_HOME=/usr/local/lib/android/sdk
else
  # Install manually only if needed
  wget and setup → ANDROID_HOME=/home/runner/android-sdk
fi
```

## 🎯 Expected Results

### **Workflow Execution**
1. ✅ Detects pre-installed Android SDK
2. ✅ Clears conflicting environment variables
3. ✅ Sets single ANDROID_HOME path
4. ✅ Installs missing components (API 34, Build Tools, NDK)
5. ✅ Gradle builds without SDK conflicts

### **Build Process**
1. ✅ Downloads Alpine Linux rootfs for all architectures
2. ✅ Builds Termful APKs (debug and release)
3. ✅ Generates artifacts for download
4. ✅ Updates termful branch automatically

## 🔍 Troubleshooting Tools

### **Debug Workflow**
Run `debug-environment.yml` to check:
- Environment variables
- SDK installation paths
- Available tools
- Java setup
- Disk space

### **Manual Verification**
```bash
# Check environment
echo $ANDROID_HOME
echo $ANDROID_SDK_ROOT

# Verify SDK components
$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager --list
```

## 📋 Verification Steps

### **1. Test Simple Build**
- Go to Actions → "Simple Termful Build"
- Run workflow manually
- Check for successful completion

### **2. Monitor SDK Setup**
Look for these log messages:
- ✅ "Using pre-installed Android SDK"
- ✅ "ANDROID_HOME: /usr/local/lib/android/sdk"
- ✅ "ANDROID_SDK_ROOT: " (empty)

### **3. Validate Build Artifacts**
- APK files generated successfully
- All architectures included (aarch64, armv7, x86, x86_64)
- Alpine Linux rootfs downloaded

## 🚀 Ready for Testing

The workflows are now updated and pushed to the `termful` branch. You can:

1. **Run "Simple Termful Build"** - Most reliable option
2. **Run "Debug Environment"** - If issues persist
3. **Monitor Actions tab** - For real-time build progress

The Android SDK conflict should now be resolved! 🎉

## 📞 If Issues Persist

If you still encounter problems:
1. Run the `debug-environment.yml` workflow
2. Check the workflow logs for specific error messages
3. Verify the environment variables are set correctly
4. Consider using only the `simple-build.yml` workflow initially