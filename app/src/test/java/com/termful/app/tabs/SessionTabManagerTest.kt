package com.termful.app.tabs

import android.content.Context
import com.termful.shared.termux.shell.command.runner.terminal.TermuxSession
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.mock

/**
 * Unit tests for SessionTabManager focusing on PTY separation and tab management
 */
class SessionTabManagerTest {

    @Mock
    private lateinit var mockContext: Context

    @Mock
    private lateinit var mockTermuxSession1: TermuxSession

    @Mock
    private lateinit var mockTermuxSession2: TermuxSession

    private lateinit var sessionTabManager: SessionTabManager

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        // Reset singleton for testing
        resetSessionTabManager()
        sessionTabManager = SessionTabManager.getInstance()
    }

    @Test
    fun testSessionInitialization() {
        val sessionHandle = "test-session-1"

        // Initialize session
        sessionTabManager.initializeSession(sessionHandle, mockTermuxSession1, mockContext)

        // Verify session was added
        assertTrue("Session should exist", sessionTabManager.getAllSessions().contains(sessionHandle))

        // Verify session data
        val sessionData = sessionTabManager.getSessionTabData(sessionHandle)
        assertNotNull("Session data should not be null", sessionData)
        assertEquals("Session handle should match", sessionHandle, sessionData?.sessionHandle)
        assertEquals("TermuxSession should match", mockTermuxSession1, sessionData?.termuxSession)
    }

    @Test
    fun testMultipleSessionManagement() {
        val sessionHandle1 = "session-1"
        val sessionHandle2 = "session-2"

        // Initialize multiple sessions
        sessionTabManager.initializeSession(sessionHandle1, mockTermuxSession1, mockContext)
        sessionTabManager.initializeSession(sessionHandle2, mockTermuxSession2, mockContext)

        // Verify both sessions exist
        val allSessions = sessionTabManager.getAllSessions()
        assertEquals("Should have 2 sessions", 2, allSessions.size)
        assertTrue("Should contain session 1", allSessions.contains(sessionHandle1))
        assertTrue("Should contain session 2", allSessions.contains(sessionHandle2))

        // Verify session data independence
        val sessionData1 = sessionTabManager.getSessionTabData(sessionHandle1)
        val sessionData2 = sessionTabManager.getSessionTabData(sessionHandle2)

        assertNotNull("Session 1 data should not be null", sessionData1)
        assertNotNull("Session 2 data should not be null", sessionData2)
        assertNotEquals("Session data should be different objects", sessionData1, sessionData2)
    }

    @Test
    fun testCurrentSessionManagement() {
        val sessionHandle1 = "session-1"
        val sessionHandle2 = "session-2"

        // Initialize sessions
        sessionTabManager.initializeSession(sessionHandle1, mockTermuxSession1, mockContext)
        sessionTabManager.initializeSession(sessionHandle2, mockTermuxSession2, mockContext)

        // First session should be current
        assertEquals("First session should be current", sessionHandle1, getCurrentSession())

        // Switch to second session
        sessionTabManager.setCurrentSession(sessionHandle2)
        assertEquals("Second session should be current", sessionHandle2, getCurrentSession())

        // Switch back to first session
        sessionTabManager.setCurrentSession(sessionHandle1)
        assertEquals("First session should be current again", sessionHandle1, getCurrentSession())
    }

    @Test
    fun testSessionRemoval() {
        val sessionHandle = "test-session"

        // Initialize session
        sessionTabManager.initializeSession(sessionHandle, mockTermuxSession1, mockContext)
        assertTrue("Session should exist", sessionTabManager.getAllSessions().contains(sessionHandle))

        // Remove session
        sessionTabManager.removeSession(sessionHandle)
        assertFalse("Session should not exist after removal", sessionTabManager.getAllSessions().contains(sessionHandle))

        // Verify session data is null
        val sessionData = sessionTabManager.getSessionTabData(sessionHandle)
        assertNull("Session data should be null after removal", sessionData)
    }

    @Test
    fun testCurrentSessionUpdateOnRemoval() {
        val sessionHandle1 = "session-1"
        val sessionHandle2 = "session-2"

        // Initialize sessions
        sessionTabManager.initializeSession(sessionHandle1, mockTermuxSession1, mockContext)
        sessionTabManager.initializeSession(sessionHandle2, mockTermuxSession2, mockContext)

        // Set first session as current
        sessionTabManager.setCurrentSession(sessionHandle1)
        assertEquals("First session should be current", sessionHandle1, getCurrentSession())

        // Remove current session
        sessionTabManager.removeSession(sessionHandle1)

        // Current session should update to remaining session
        assertEquals("Second session should become current", sessionHandle2, getCurrentSession())

        // Remove last session
        sessionTabManager.removeSession(sessionHandle2)

        // Current session should be null
        assertNull("Current session should be null when no sessions remain", getCurrentSession())
    }

    @Test
    fun testTabDataInitialization() {
        val sessionHandle = "test-session"

        // Initialize session
        sessionTabManager.initializeSession(sessionHandle, mockTermuxSession1, mockContext)
        val sessionData = sessionTabManager.getSessionTabData(sessionHandle)!!

        // Test initial tab state
        assertEquals("Initial tab should be TERMINAL", TabType.TERMINAL, sessionData.getCurrentTabType())

        // Test tab data initialization
        assertNull("Terminal tab data should be null initially", sessionData.terminalTabData)
        assertNull("File manager tab data should be null initially", sessionData.fileManagerTabData)
        assertNull("Editor tab data should be null initially", sessionData.editorTabData)
        assertNull("Agent tab data should be null initially", sessionData.agentTabData)
    }

    @Test
    fun testTabSwitching() {
        val sessionHandle = "test-session"

        // Initialize session
        sessionTabManager.initializeSession(sessionHandle, mockTermuxSession1, mockContext)
        val sessionData = sessionTabManager.getSessionTabData(sessionHandle)!!

        // Test tab switching
        sessionData.setCurrentTab(TabType.FILE_MANAGER)
        assertEquals("Current tab should be FILE_MANAGER", TabType.FILE_MANAGER, sessionData.getCurrentTabType())

        sessionData.setCurrentTab(TabType.EDITOR)
        assertEquals("Current tab should be EDITOR", TabType.EDITOR, sessionData.getCurrentTabType())

        sessionData.setCurrentTab(TabType.AGENT)
        assertEquals("Current tab should be AGENT", TabType.AGENT, sessionData.getCurrentTabType())

        sessionData.setCurrentTab(TabType.TERMINAL)
        assertEquals("Current tab should be TERMINAL", TabType.TERMINAL, sessionData.getCurrentTabType())
    }

    @Test
    fun testTabDataIndependence() {
        val sessionHandle1 = "session-1"
        val sessionHandle2 = "session-2"

        // Initialize sessions
        sessionTabManager.initializeSession(sessionHandle1, mockTermuxSession1, mockContext)
        sessionTabManager.initializeSession(sessionHandle2, mockTermuxSession2, mockContext)

        val sessionData1 = sessionTabManager.getSessionTabData(sessionHandle1)!!
        val sessionData2 = sessionTabManager.getSessionTabData(sessionHandle2)!!

        // Initialize tab data for session 1
        sessionData1.terminalTabData = TerminalTabData(sessionHandle1, showSessionInfo = true)
        sessionData1.fileManagerTabData = FileManagerTabData(sessionHandle1, currentDirectory = "/test1")

        // Initialize different tab data for session 2
        sessionData2.terminalTabData = TerminalTabData(sessionHandle2, showSessionInfo = false)
        sessionData2.fileManagerTabData = FileManagerTabData(sessionHandle2, currentDirectory = "/test2")

        // Verify independence
        assertTrue("Session 1 should show session info", sessionData1.terminalTabData?.showSessionInfo == true)
        assertFalse("Session 2 should not show session info", sessionData2.terminalTabData?.showSessionInfo == true)

        assertEquals("Session 1 directory should be /test1", "/test1", sessionData1.fileManagerTabData?.currentDirectory)
        assertEquals("Session 2 directory should be /test2", "/test2", sessionData2.fileManagerTabData?.currentDirectory)
    }

    @Test
    fun testSessionCleanup() {
        val sessionHandle = "test-session"

        // Initialize session with tab data
        sessionTabManager.initializeSession(sessionHandle, mockTermuxSession1, mockContext)
        val sessionData = sessionTabManager.getSessionTabData(sessionHandle)!!

        // Add tab data
        sessionData.terminalTabData = TerminalTabData(sessionHandle)
        sessionData.fileManagerTabData = FileManagerTabData(sessionHandle)
        sessionData.editorTabData = EditorTabData(sessionHandle)
        sessionData.agentTabData = AgentTabData(sessionHandle)

        // Verify tab data exists
        assertNotNull("Terminal tab data should exist", sessionData.terminalTabData)
        assertNotNull("File manager tab data should exist", sessionData.fileManagerTabData)
        assertNotNull("Editor tab data should exist", sessionData.editorTabData)
        assertNotNull("Agent tab data should exist", sessionData.agentTabData)

        // Remove session (should trigger cleanup)
        sessionTabManager.removeSession(sessionHandle)

        // Verify session is removed
        assertNull("Session should be removed", sessionTabManager.getSessionTabData(sessionHandle))
    }

    @Test
    fun testManagerCleanup() {
        val sessionHandle1 = "session-1"
        val sessionHandle2 = "session-2"

        // Initialize sessions
        sessionTabManager.initializeSession(sessionHandle1, mockTermuxSession1, mockContext)
        sessionTabManager.initializeSession(sessionHandle2, mockTermuxSession2, mockContext)

        // Verify sessions exist
        assertEquals("Should have 2 sessions", 2, sessionTabManager.getAllSessions().size)

        // Cleanup all sessions
        sessionTabManager.cleanup()

        // Verify all sessions are removed
        assertEquals("Should have 0 sessions after cleanup", 0, sessionTabManager.getAllSessions().size)
        assertNull("Current session should be null", getCurrentSession())
    }

    // Helper methods
    private fun getCurrentSession(): String? {
        // Access the current session value directly
        return sessionTabManager.currentSession.value
    }

    private fun resetSessionTabManager() {
        // Reset singleton for testing - in real implementation this would need reflection
        // or a test-specific factory method
    }
}