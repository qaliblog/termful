# Termux Agent Tabs Feature

## Overview

This document describes the implementation of the 4-tab system in Termux with integrated AI Agent functionality. This feature adds Terminal, File Manager, Text Editor, and AI Agent tabs to each Termux session, with each tab running in its own separate PTY/process.

## Feature Highlights

- **4-Tab System**: Each terminal session now has Terminal, File Manager, Editor, and AI Agent tabs
- **Independent PTY Sessions**: Each tab runs in its own process/PTY for complete isolation
- **AI Agent with Rotation**: Multi-provider AI support with automatic API key rotation and failure handling
- **Integrated File Management**: Full-featured file manager with multi-select, clipboard operations
- **Code Editor**: Text editor with syntax highlighting and file watching
- **Secure Storage**: API keys stored using Android Keystore encryption
- **History Management**: Comprehensive agent interaction history with search and favorites

## Architecture

### Tab System Architecture

```
TermuxTabbedActivity
├── SessionTabManager (Singleton)
│   ├── SessionTabData (per session)
│   │   ├── TerminalTabData
│   │   ├── FileManagerTabData 
│   │   ├── EditorTabData
│   │   └── AgentTabData
│   └── Current Session Tracking
├── ViewPager2 + TabLayout
│   ├── TerminalTabFragment
│   ├── FileManagerTabFragment
│   ├── EditorTabFragment
│   └── AgentTabFragment
└── Individual PTY/Process per tab
```

### Agent System Architecture

```
AgentManager
├── AgentConfigManager (Secure Storage)
├── AgentProviderManager (API Rotation)
│   ├── GeminiProviderClient
│   ├── OpenAIProviderClient
│   ├── AnthropicProviderClient
│   └── Custom Provider Support
├── AgentHistoryManager (Persistent History)
└── AgentProcess (Per Session)
    ├── Independent PTY
    ├── Command Execution
    └── IPC Communication
```

## Key Components

### 1. Session Tab Management

**SessionTabManager**: Singleton that manages all session tabs
- Tracks active sessions and their tab states
- Ensures proper cleanup when sessions are destroyed
- Maintains current session and tab selection

**SessionTabData**: Per-session data container
- Holds tab-specific data for each session
- Manages current active tab
- Handles lifecycle and cleanup

### 2. File Manager

**Features**:
- Hierarchical file browsing with breadcrumb navigation
- Multi-select with long-press
- Copy/Cut/Paste operations with session clipboard
- File search and sorting (name, size, date, type)
- Create new files and folders
- Integration with Text Editor

**Key Classes**:
- `FileManagerTabFragment`: Main UI controller
- `FileManagerTabData`: State management
- `FilesAdapter`: RecyclerView adapter for file list
- `BreadcrumbAdapter`: Navigation breadcrumb

### 3. Text Editor

**Features**:
- Syntax highlighting using Sora Editor
- Undo/Redo functionality
- Find and Replace
- File watching for external changes
- Auto-save on pause
- Multiple encoding support

**Key Classes**:
- `EditorTabFragment`: Main editor interface
- `EditorTabData`: Editor state and file tracking
- Integration with `CodeEditor` from Sora library

### 4. AI Agent System

**Features**:
- Multi-provider support (OpenAI, Google Gemini, Anthropic Claude)
- API key rotation with failure detection
- Exponential backoff for rate limiting
- Secure key storage using Android Keystore
- Interaction history with search and favorites
- Working directory selection
- Independent PTY for command execution

**Key Classes**:
- `AgentManager`: Central coordinator
- `AgentConfigManager`: Secure configuration storage
- `AgentProviderManager`: Provider abstraction with rotation
- `AgentHistoryManager`: Persistent history management
- `AgentTabFragment`: UI interface

## API Key Rotation System

The agent system implements sophisticated API key rotation with the following features:

### Rotation Modes
1. **Round Robin**: Cycles through all available keys
2. **Failover Priority**: Uses keys in order, falling back on failure

### Failure Detection
- HTTP 429 (Rate Limited)
- HTTP 503 (Service Unavailable) 
- Provider-specific error messages
- Quota exceeded errors

### Backoff Strategy
- Exponential backoff with jitter
- Per-key suspension tracking
- Configurable maximum failures
- Automatic recovery after suspension period

### Configuration Example
```json
{
  "provider_id": "openai",
  "display_name": "OpenAI",
  "api_keys": [
    {"id": "key1", "key": "sk-...", "meta": {"label": "Primary"}},
    {"id": "key2", "key": "sk-...", "meta": {"label": "Backup"}}
  ],
  "use_rotation": true,
  "rotation_mode": "round_robin",
  "max_consecutive_failures": 3,
  "backoff_ms": 1000
}
```

## Security Implementation

### API Key Storage
- **Encrypted Storage**: Using `EncryptedSharedPreferences` with AES256-GCM
- **Key Masking**: Keys are masked in UI and logs
- **Memory Cleanup**: Sensitive data cleared from memory
- **No Plain Text**: Keys never stored in plain text files

### Security Features
- API key validation and format checking
- Error message sanitization to prevent key leakage
- Secure export/import with encryption
- Rotation state tracking without exposing keys

## PTY Separation

Each tab operates with its own Process/PTY:

### Terminal Tab
- Uses existing TermuxSession PTY
- Maintains backward compatibility
- Shows session info (PID, working directory)

### File Manager Tab
- File operations run in separate process context
- No interference with terminal session

### Editor Tab  
- File I/O operations isolated
- External change detection
- Independent of terminal state

### Agent Tab
- Dedicated PTY for AI agent process
- Command execution in agent's working directory
- IPC communication with main UI thread
- Process lifecycle management

## Installation and Setup

### Prerequisites
- Android API 21+ (Android 5.0+)
- Termux app with this feature branch
- Internet connection for AI providers

### Building from Source

1. **Clone the repository**:
```bash
git clone https://github.com/termux/termux-app.git
cd termux-app
git checkout feature/agent-tabs
```

2. **Build the APK**:
```bash
./gradlew assembleDebug
```

3. **Run tests**:
```bash
./gradlew test
./gradlew connectedAndroidTest  # if emulator available
```

### Configuration

#### Setting up AI Providers

1. Open Termux and navigate to **Settings → AI Agent → Providers**
2. Click **Add Provider** and select your preferred provider:
   - **Google Gemini**: Requires Gemini API key
   - **OpenAI**: Requires OpenAI API key (sk-...)
   - **Anthropic**: Requires Claude API key (sk-ant-...)

3. Configure rotation settings:
   - Enable **API Key Rotation** for multiple keys
   - Set **Rotation Mode** (Round Robin or Failover Priority)
   - Configure **Max Consecutive Failures** (default: 3)
   - Set **Backoff Delay** (default: 1000ms)

4. Test your configuration using the **Test Key** button

#### Provider-Specific Setup

**Google Gemini**:
1. Visit [Google AI Studio](https://makersuite.google.com/app/apikey)
2. Create a new API key
3. Add to Termux: Settings → AI Agent → Providers → Add Provider → Gemini

**OpenAI**:
1. Visit [OpenAI API Keys](https://platform.openai.com/api-keys)
2. Create a new secret key (starts with `sk-`)
3. Add to Termux: Settings → AI Agent → Providers → Add Provider → OpenAI

**Anthropic Claude**:
1. Visit [Anthropic Console](https://console.anthropic.com/)
2. Generate an API key (starts with `sk-ant-`)
3. Add to Termux: Settings → AI Agent → Providers → Add Provider → Anthropic

## Usage Guide

### Basic Navigation

1. **Open Termux** - The app will show 4 tabs at the top:
   - **Terminal**: Interactive shell (default)
   - **Files**: File manager
   - **Editor**: Text editor  
   - **Agent**: AI assistant

2. **Switch between tabs** by tapping the tab headers

3. **Each tab maintains its own state** per session

### File Manager Usage

1. **Navigation**:
   - Tap folders to enter them
   - Use breadcrumb to navigate back
   - Tap 🏠 to go to home directory

2. **File Operations**:
   - **Long press** files to select (multi-select supported)
   - Use action buttons: Copy, Cut, Delete
   - **Tap** files to open in Editor

3. **Creating Files**:
   - Tap the **+** FAB button
   - Choose "New File" or "New Folder"
   - Enter name and confirm

### Text Editor Usage

1. **Opening Files**:
   - Open files from File Manager (automatic)
   - Files will load with syntax highlighting

2. **Editing Features**:
   - **Undo/Redo**: Use toolbar buttons
   - **Find/Replace**: Menu → Find & Replace
   - **Save**: Ctrl+S or toolbar button
   - **Auto-save**: Enabled on app pause

3. **File Watching**:
   - Editor detects external file changes
   - Prompts to reload when changes detected

### AI Agent Usage

1. **Initial Setup**:
   - If not configured, tap "Configure Agent"
   - Add at least one AI provider and API key

2. **Interaction**:
   - Type questions in the input field
   - Tap send button or press Enter
   - View responses in the console area

3. **Working Directory**:
   - Tap folder icon to select working directory
   - Commands execute in selected directory
   - Directory shown in status bar

4. **History Management**:
   - Tap history icon to view past interactions
   - Star ⭐ interactions to favorite them
   - Search through history
   - Re-run previous commands

### Session Management

1. **Creating Sessions**:
   - Swipe right to open session drawer
   - Tap "New Session" to create additional sessions

2. **Session Independence**:
   - Each session has its own 4 tabs
   - Tab states are maintained per session
   - PTY processes are isolated

3. **Session Info** (Terminal tab):
   - Tap info icon to show PID and working directory
   - Useful for monitoring session state

## Developer Documentation

### Adding New Providers

To add a new AI provider:

1. **Create Provider Client**:
```kotlin
class CustomProviderClient : AgentProviderClient() {
    override suspend fun processMessage(
        apiKey: String,
        message: String,
        workingDirectory: String,
        provider: AgentProvider
    ): AgentProviderResponse {
        // Implement API communication
    }
    
    override suspend fun testApiKey(apiKey: String): AgentKeyTestResult {
        // Implement key validation
    }
}
```

2. **Register Provider**:
```kotlin
// In AgentProviderManager.initializeProviders()
providerClients["custom"] = CustomProviderClient()
```

3. **Add UI Support**:
- Add provider option in settings
- Add validation rules
- Add provider-specific configuration

### Extending Tab System

To add a new tab type:

1. **Add TabType enum**:
```kotlin
enum class TabType {
    TERMINAL, FILE_MANAGER, EDITOR, AGENT, NEW_TAB
}
```

2. **Create Fragment**:
```kotlin
class NewTabFragment : Fragment() {
    companion object {
        fun newInstance(sessionHandle: String): NewTabFragment
    }
}
```

3. **Add to Adapter**:
```kotlin
// Update SessionTabsAdapter
override fun createFragment(position: Int): Fragment {
    return when (position) {
        // ... existing tabs
        TAB_NEW -> NewTabFragment.newInstance(sessionHandle)
    }
}
```

4. **Add Tab Data**:
```kotlin
data class NewTabData(
    val sessionHandle: String
    // Add tab-specific fields
)
```

### Rotation Configuration

Rotation parameters can be modified in `AgentProviderManager`:

```kotlin
private fun calculateBackoffTime(provider: AgentProvider, failureCount: Int): Long {
    val baseBackoff = provider.backoffMs
    val exponentialBackoff = baseBackoff * (2.0.pow(failureCount)).toLong()
    val jitter = Random.nextLong(0, exponentialBackoff / 4)
    return (exponentialBackoff + jitter).coerceAtMost(TimeUnit.HOURS.toMillis(1))
}
```

### Testing

Run the test suite:

```bash
# Unit tests
./gradlew test

# Specific test categories
./gradlew test --tests "*AgentProviderManagerTest*"
./gradlew test --tests "*FileManagerTest*"
./gradlew test --tests "*SessionTabManagerTest*"
./gradlew test --tests "*SecurityTest*"

# Integration tests (requires emulator)
./gradlew connectedAndroidTest
```

### Debugging

Enable debug logging:

```kotlin
// In AgentProviderManager
private fun logProviderUsage(providerId: String, success: Boolean, error: String?) {
    Log.d("AgentProvider", "[$providerId] ${if (success) "SUCCESS" else "FAILURE"} $error")
}
```

View agent process logs:
```bash
# In terminal tab
ps aux | grep agent  # Find agent process
tail -f /proc/[PID]/fd/1  # Follow agent output
```

## Troubleshooting

### Common Issues

**Agent not responding**:
1. Check API key configuration
2. Verify internet connection
3. Check provider status (rate limits)
4. Review agent process logs

**File manager not loading**:
1. Check storage permissions
2. Verify directory access rights
3. Check if directory exists

**Editor not saving**:
1. Check file permissions
2. Verify disk space
3. Check file path validity

**Tabs not switching**:
1. Check session state
2. Verify fragment lifecycle
3. Review memory constraints

### Performance Optimization

**Memory Management**:
- Each tab loads on demand
- Fragments are reused when possible
- Large files are streamed rather than loaded entirely

**Process Management**:
- PTY processes are cleaned up on session close
- Agent processes have timeout handling
- File operations use background threads

## Testing Coverage

The implementation includes comprehensive tests:

### Unit Tests
- **AgentProviderManagerTest**: API rotation logic
- **SessionTabManagerTest**: PTY separation and tab management
- **FileManagerTest**: File operations and state management
- **SecurityTest**: API key security and encryption

### Integration Tests
- **TabIntegrationTest**: Full tab system workflow
- Session creation and management
- File manager ↔ editor integration
- Agent configuration and usage

### Security Tests
- API key storage encryption
- Key masking in logs and UI
- Error message sanitization
- Memory cleanup verification

## Future Enhancements

### Planned Features
1. **Custom Agent Providers**: Support for local AI models
2. **File Manager Enhancements**: Cloud storage integration
3. **Editor Improvements**: LSP support, themes, plugins
4. **Agent Capabilities**: Tool use, file operations, code execution
5. **Session Persistence**: Save and restore session states

### Performance Improvements
1. **Lazy Loading**: Load tabs only when accessed
2. **Resource Pooling**: Share resources between tabs
3. **Background Processing**: Optimize file operations
4. **Memory Optimization**: Better lifecycle management

## Contributing

### Code Style
- Follow existing Kotlin conventions
- Use meaningful variable names
- Add comprehensive documentation
- Write tests for new features

### Pull Request Process
1. Create feature branch from `main`
2. Implement changes with tests
3. Update documentation
4. Submit PR with detailed description
5. Address review feedback

### Issue Reporting
- Use GitHub issue templates
- Provide device and Android version
- Include logs and reproduction steps
- Tag with appropriate labels

## License

This feature is part of the Termux project and follows the same licensing terms. See the main repository LICENSE file for details.

## Acknowledgments

- **Termux Team**: For the foundational terminal application
- **Sora Editor**: For the advanced code editor component
- **Android Security Team**: For the encrypted storage APIs
- **AI Provider Teams**: For their excellent APIs and documentation

---

*This documentation covers the comprehensive 4-tab system implementation. For additional support, please refer to the main Termux documentation or submit an issue on GitHub.*