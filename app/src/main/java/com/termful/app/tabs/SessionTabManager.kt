package com.termful.app.tabs

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.termful.shared.termux.shell.command.runner.terminal.TermuxSession
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages the 4-tab system for each terminal session.
 * Each session can have: Terminal, File Manager, Editor, and AI Agent tabs.
 */
class SessionTabManager private constructor() {
    companion object {
        @Volatile
        private var INSTANCE: SessionTabManager? = null
        
        fun getInstance(): SessionTabManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SessionTabManager().also { INSTANCE = it }
            }
        }
    }
    
    // Map of session handle to tab data
    private val sessionTabs = ConcurrentHashMap<String, SessionTabData>()
    
    // Current active session
    private val _currentSession = MutableLiveData<String?>()
    val currentSession: LiveData<String?> = _currentSession
    
    /**
     * Initialize tabs for a new session
     */
    fun initializeSession(sessionHandle: String, termuxSession: TermuxSession, context: Context) {
        val tabData = SessionTabData(
            sessionHandle = sessionHandle,
            termuxSession = termuxSession,
            context = context
        )
        sessionTabs[sessionHandle] = tabData
        
        // Set as current if it's the first session
        if (_currentSession.value == null) {
            _currentSession.value = sessionHandle
        }
    }
    
    /**
     * Get tab data for a session
     */
    fun getSessionTabData(sessionHandle: String): SessionTabData? {
        return sessionTabs[sessionHandle]
    }
    
    /**
     * Remove session and cleanup resources
     */
    fun removeSession(sessionHandle: String) {
        sessionTabs[sessionHandle]?.cleanup()
        sessionTabs.remove(sessionHandle)
        
        // Update current session if we removed the active one
        if (_currentSession.value == sessionHandle) {
            _currentSession.value = sessionTabs.keys.firstOrNull()
        }
    }
    
    /**
     * Set the current active session
     */
    fun setCurrentSession(sessionHandle: String) {
        if (sessionTabs.containsKey(sessionHandle)) {
            _currentSession.value = sessionHandle
        }
    }
    
    /**
     * Get all session handles
     */
    fun getAllSessions(): Set<String> {
        return sessionTabs.keys.toSet()
    }
    
    /**
     * Cleanup all sessions
     */
    fun cleanup() {
        sessionTabs.values.forEach { it.cleanup() }
        sessionTabs.clear()
        _currentSession.value = null
    }
}

/**
 * Tab types available in each session
 */
enum class TabType(val displayName: String) {
    TERMINAL("Terminal"),
    FILE_MANAGER("Files"),
    EDITOR("Editor"),
    AGENT("Agent")
}

/**
 * Holds all data for a session's tabs
 */
data class SessionTabData(
    val sessionHandle: String,
    val termuxSession: TermuxSession,
    val context: Context
) {
    // Current active tab
    private val _currentTab = MutableLiveData(TabType.TERMINAL)
    val currentTab: LiveData<TabType> = _currentTab
    
    // Tab-specific data
    var terminalTabData: TerminalTabData? = null
    var fileManagerTabData: FileManagerTabData? = null
    var editorTabData: EditorTabData? = null
    var agentTabData: AgentTabData? = null
    
    fun setCurrentTab(tabType: TabType) {
        _currentTab.value = tabType
    }
    
    fun getCurrentTabType(): TabType {
        return _currentTab.value ?: TabType.TERMINAL
    }
    
    fun cleanup() {
        terminalTabData?.cleanup()
        fileManagerTabData?.cleanup()
        editorTabData?.cleanup()
        agentTabData?.cleanup()
    }
}

/**
 * Data specific to Terminal tab
 */
data class TerminalTabData(
    val sessionHandle: String,
    var showSessionInfo: Boolean = false,
    var workingDirectory: String = "~"
) {
    fun cleanup() {
        // Terminal cleanup handled by TermuxSession
    }
}

/**
 * Data specific to File Manager tab
 */
data class FileManagerTabData(
    val sessionHandle: String,
    var currentDirectory: String = System.getProperty("user.home") ?: "/",
    var selectedFiles: MutableSet<String> = mutableSetOf(),
    var clipboardFiles: MutableList<String> = mutableListOf(),
    var clipboardOperation: ClipboardOperation? = null,
    var sortMode: FileSortMode = FileSortMode.NAME_ASC,
    var searchQuery: String = ""
) {
    fun cleanup() {
        selectedFiles.clear()
        clipboardFiles.clear()
    }
}

enum class ClipboardOperation {
    COPY, CUT
}

enum class FileSortMode(val displayName: String) {
    NAME_ASC("Name A-Z"),
    NAME_DESC("Name Z-A"),
    SIZE_ASC("Size smallest first"),
    SIZE_DESC("Size largest first"),
    DATE_ASC("Date oldest first"),
    DATE_DESC("Date newest first"),
    TYPE_ASC("Type A-Z")
}

/**
 * Data specific to Editor tab
 */
data class EditorTabData(
    val sessionHandle: String,
    var currentFile: String? = null,
    var isModified: Boolean = false,
    var lastSavedTime: Long = 0,
    var encoding: String = "UTF-8",
    var language: String = "Plain Text",
    var cursorLine: Int = 1,
    var cursorColumn: Int = 1,
    var findQuery: String = "",
    var replaceQuery: String = "",
    var showFindReplace: Boolean = false
) {
    fun cleanup() {
        // Editor cleanup - save unsaved changes if needed
    }
}

/**
 * Data specific to AI Agent tab
 */
data class AgentTabData(
    val sessionHandle: String,
    var agentProcessId: Int? = null,
    var workingDirectory: String = System.getProperty("user.home") ?: "/",
    var isAgentActive: Boolean = false,
    var agentStatus: AgentStatus = AgentStatus.INACTIVE,
    var currentProvider: String? = null,
    var showHistory: Boolean = false,
    var showSidePanel: Boolean = false
) {
    fun cleanup() {
        // Cleanup agent process if running
        agentProcessId?.let {
            try {
                Runtime.getRuntime().exec("kill -9 $it")
            } catch (e: Exception) {
                // Ignore cleanup errors
            }
        }
    }
}

enum class AgentStatus(val displayName: String) {
    INACTIVE("Inactive"),
    ACTIVE("Active"),
    PROCESSING("Processing"),
    ERROR("Error")
}