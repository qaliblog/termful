# GitHub Workflows for Termful

This document describes the GitHub Actions workflows set up for the Termful project.

## Available Workflows

### 1. Build Termful App (`build-termful.yml`)

**Trigger**: Push to main/master/develop, Pull Requests, Manual dispatch

**Purpose**: Builds the Termful app and runs tests

**Features**:
- Downloads Alpine Linux rootfs for all architectures
- Builds both debug and release APKs
- Runs unit tests and lint checks
- Uploads build artifacts
- Caches Gradle dependencies and Alpine downloads
- Creates release branch with build artifacts (on main/master)

**Artifacts**:
- `termful-debug-apk`: Debug APK and build info
- `termful-release-apk`: Release APK and build info  
- `test-results`: Test reports and lint results

### 2. Create Release Branch (`create-release-branch.yml`)

**Trigger**: Manual dispatch only

**Purpose**: Creates a dedicated release branch with optional APK inclusion

**Options**:
- `branch_name`: Custom branch name (auto-generated if empty)
- `include_apks`: Whether to include APK files in the branch

**Features**:
- Builds fresh APKs for the release
- Creates comprehensive build metadata
- Generates checksums for APK files
- Creates timestamped branch names
- Optionally creates GitHub releases
- Provides detailed build information

**Output**: Creates branch like `releases/termful-20241226-123456-a1b2c3d`

### 3. Auto Push to Termful Branch (`auto-push-termful.yml`)

**Trigger**: Push to main/master affecting key files

**Purpose**: Automatically maintains a `termful` branch with latest changes

**Features**:
- Monitors changes to app, shared libraries, and workflows
- Creates or updates the `termful` branch
- Merges changes from main/master automatically
- Updates branch information file
- Provides comparison links and status

**Monitored Paths**:
- `app/**`
- `termful-shared/**`  
- `terminal-emulator/**`
- `terminal-view/**`
- `*.gradle`
- `gradle.properties`
- `.github/workflows/**`

## Workflow Usage

### Building Manually

To trigger a manual build:

1. Go to Actions tab in GitHub
2. Select "Build Termful App"
3. Click "Run workflow"
4. Choose the branch to build

### Creating a Release

To create a release branch:

1. Go to Actions tab in GitHub  
2. Select "Create Termful Release Branch"
3. Click "Run workflow"
4. Optionally specify:
   - Custom branch name
   - Whether to include APK files

### Automatic Updates

The `termful` branch is automatically updated when you:
- Push changes to main/master
- Modify app source code
- Update build configurations
- Change workflows

## Branch Strategy

### Main Development
- `main` or `master`: Primary development branch
- Features developed in feature branches
- Pull requests merged to main

### Automatic Branches  
- `termful`: Auto-updated with latest changes
- `releases/termful-YYYYMMDD-HHMMSS-HASH`: Timestamped releases

### Manual Releases
- Create release branches when ready to distribute
- Include APKs for easy distribution
- Comprehensive metadata and build info

## Build Artifacts

### APK Files
- Debug: `termful-app_apk-android-7-debug_universal.apk`
- Release: `termful-app_apk-android-7-release_universal.apk`
- Architecture-specific APKs (if split builds enabled)

### Metadata
- `build-info.txt`: Basic build information
- `BUILD_INFO.md`: Detailed markdown build report
- `build.json`: JSON metadata for automation
- `checksums.txt`: SHA256 checksums for APKs

### Test Results
- Unit test reports
- Lint check results  
- Coverage reports (if configured)

## Caching Strategy

### Gradle Cache
- Dependencies cached across builds
- Wrapper files cached
- Speeds up subsequent builds

### Alpine Rootfs Cache
- Downloaded Alpine minirootfs files cached
- Reduces download time for repeated builds
- Cache key includes Alpine version and build config

## Security Considerations

### Token Usage
- Uses `GITHUB_TOKEN` for repository operations
- Automatically provided by GitHub Actions
- Limited to repository scope

### Secrets
- No custom secrets required for basic workflows
- Add `ANDROID_KEYSTORE_*` secrets for signed releases
- API keys for external services (if needed)

## Customization

### Adding Architectures
Update `downloadBootstraps` task in `app/build.gradle`:
```gradle
downloadAlpineRootfs("new-arch", "alpine-arch", alpineVersion)
```

### Changing Alpine Version
Update `alpineVersion` in `app/build.gradle`:
```gradle
def alpineVersion = "v3.20"  // New version
```

### Custom Build Steps
Add steps to workflow files:
- Pre-build setup
- Post-build processing
- Custom testing
- Deployment steps

## Troubleshooting

### Common Issues

**Build Failures**:
- Check Android SDK version compatibility
- Verify NDK version matches configuration
- Ensure internet access for Alpine downloads

**Cache Issues**:
- Clear caches manually if needed
- Update cache keys when dependencies change

**Permission Errors**:
- Verify `GITHUB_TOKEN` permissions
- Check repository settings for Actions

### Debugging

**Enable Debug Logging**:
Add to workflow steps:
```yaml
- name: Debug Step
  run: ./gradlew assembleDebug --info --stacktrace
```

**View Detailed Logs**:
- Click on workflow runs in Actions tab
- Expand steps to see detailed output
- Download logs for offline analysis

## Monitoring

### Build Status
- Workflow badges in README
- Status checks on pull requests
- Email notifications (configurable)

### Artifact Management  
- Automatic cleanup after retention period
- Size limits for artifacts
- Download statistics tracking