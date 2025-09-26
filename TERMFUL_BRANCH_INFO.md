# Termful Branch Information

This branch contains the transformed Termful app with Alpine Linux support.

## Latest Update
- **Date:** 2024-12-26 (Auto-generated)
- **Source Commit:** `91b3fe6f`
- **Source Branch:** `cursor/rebrand-app-and-set-up-alpine-rootfs-8092`
- **Trigger:** Manual creation

## Transformation Status
- [x] Package name changed to com.termful
- [x] App branding updated to Termful
- [x] Alpine Linux rootfs integration
- [x] APK package manager support
- [x] Architecture support maintained
- [x] Native code updated
- [x] Build system configured
- [x] GitHub workflows added

## Build Instructions
```bash
# Setup environment
./setup_build_env.sh

# Build the app
./gradlew clean
./gradlew assembleDebug
```

## Alpine Linux Integration
- **Version:** v3.19 (latest stable)
- **Source:** Official Alpine Linux CDN
- **Architectures:** aarch64, armv7, x86, x86_64
- **Package Manager:** Alpine APK

## GitHub Workflows
- **build-termful.yml:** Main build and test workflow
- **create-release-branch.yml:** Manual release creation
- **auto-push-termful.yml:** Auto-updates this branch

## Key Changes from Termux
1. **Package Name:** com.termux → com.termful
2. **App Name:** Termux → Termful
3. **Bootstrap:** APT packages → Alpine Linux rootfs
4. **Package Manager:** apt → apk
5. **Directory Structure:** Reorganized to com.termful namespace
6. **Native Libraries:** Updated for new package structure
7. **Build System:** Alpine Linux integration
8. **Workflows:** Complete CI/CD setup

---
*This branch is automatically maintained by GitHub Actions*