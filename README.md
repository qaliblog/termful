# Termful

**A lightweight terminal emulator for Android with Alpine Linux environment**

Transformed from Termux to provide a minimal, security-focused terminal experience powered by Alpine Linux.

## Key Features

- Alpine Linux: Minimal, security-focused userland
- APK Package Manager: Native Alpine package management  
- Multi-Architecture: ARM64, ARM32, x86_64, x86 support
- Lightweight: Optimized for performance and battery life
- Full Terminal: Complete terminal emulation with file manager
- AI Agent: Integrated AI assistance for development

## Quick Start

### Installation
1. Download APK from Releases
2. Install on Android 7.0+
3. Open Termful and wait for setup

### First Commands
```bash
apk update              # Update packages
apk add bash git nano   # Install tools
cat /etc/alpine-release # Check version
```

## Building

```bash
./setup_build_env.sh    # Setup environment
./gradlew clean         # Clean builds
./gradlew assembleDebug # Build APK
```

## GitHub Workflows

- Auto Push: Updates termful branch on main changes
- Build & Test: Builds APKs and runs tests  
- Release: Creates timestamped release branches

## Documentation

- [Transformation Summary](TERMFUL_TRANSFORMATION_SUMMARY.md)
- [Setup Guide](setup_build_env.sh)

---

Built with Alpine Linux