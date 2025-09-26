package com.termux.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.ActivityTestRule
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.*
import androidx.test.espresso.matcher.ViewMatchers.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*
import android.content.Context
import com.termux.R
import com.termux.app.tabs.SessionTabManager
import com.termux.app.tabs.TabType

/**
 * Integration test for the 4-tab system
 */
@RunWith(AndroidJUnit4::class)
class TabIntegrationTest {

    @get:Rule
    val activityRule = ActivityTestRule(TermuxTabbedActivity::class.java)

    @Test
    fun useAppContext() {
        // Context of the app under test.
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.termux", appContext.packageName)
    }

    @Test
    fun testTabSystemInitialization() {
        // Wait for activity to load
        Thread.sleep(2000)

        // Check that all tabs are present
        onView(withText(R.string.tab_terminal)).check(matches(isDisplayed()))
        onView(withText(R.string.tab_files)).check(matches(isDisplayed()))
        onView(withText(R.string.tab_editor)).check(matches(isDisplayed()))
        onView(withText(R.string.tab_agent)).check(matches(isDisplayed()))
    }

    @Test
    fun testTabSwitching() {
        // Wait for activity to load
        Thread.sleep(2000)

        // Test switching to File Manager tab
        onView(withText(R.string.tab_files)).perform(click())
        Thread.sleep(1000)

        // Verify file manager UI is shown
        onView(withId(R.id.home_button)).check(matches(isDisplayed()))
        onView(withId(R.id.files_recycler_view)).check(matches(isDisplayed()))

        // Test switching to Editor tab
        onView(withText(R.string.tab_editor)).perform(click())
        Thread.sleep(1000)

        // Verify editor UI is shown
        onView(withId(R.id.code_editor)).check(matches(isDisplayed()))
        onView(withText(R.string.no_file_open)).check(matches(isDisplayed()))

        // Test switching to Agent tab
        onView(withText(R.string.tab_agent)).perform(click())
        Thread.sleep(1000)

        // Verify agent UI is shown
        onView(withId(R.id.agent_status_text)).check(matches(isDisplayed()))

        // Switch back to Terminal tab
        onView(withText(R.string.tab_terminal)).perform(click())
        Thread.sleep(1000)

        // Verify terminal is shown
        onView(withId(R.id.terminal_view)).check(matches(isDisplayed()))
    }

    @Test
    fun testFileManagerEditorIntegration() {
        // Wait for activity to load
        Thread.sleep(2000)

        // Switch to File Manager
        onView(withText(R.string.tab_files)).perform(click())
        Thread.sleep(1000)

        // Create a new file
        onView(withId(R.id.fab_new)).perform(click())
        Thread.sleep(500)

        // This would open a dialog - in a real test we'd interact with it
        // For now, we'll simulate the file creation flow
        
        // Verify we can navigate to editor tab
        onView(withText(R.string.tab_editor)).perform(click())
        Thread.sleep(1000)

        // Verify editor shows no file open state initially
        onView(withText(R.string.no_file_open)).check(matches(isDisplayed()))
    }

    @Test
    fun testSessionManagement() {
        val activity = activityRule.activity
        val sessionTabManager = activity.getSessionTabManager()

        // Wait for initial session creation
        Thread.sleep(2000)

        // Verify initial session exists
        val currentSession = activity.getCurrentSessionHandle()
        assertNotNull("Should have a current session", currentSession)

        // Verify session has tab data
        val sessionData = sessionTabManager.getSessionTabData(currentSession!!)
        assertNotNull("Session data should exist", sessionData)
        assertEquals("Initial tab should be terminal", TabType.TERMINAL, sessionData?.getCurrentTabType())

        // Test tab switching affects session data
        onView(withText(R.string.tab_files)).perform(click())
        Thread.sleep(1000)

        assertEquals("Current tab should be file manager", TabType.FILE_MANAGER, sessionData?.getCurrentTabType())
    }

    @Test
    fun testNewSessionCreation() {
        // Wait for activity to load
        Thread.sleep(2000)

        // Open drawer
        onView(withId(R.id.drawer_layout)).perform(swipeRight())
        Thread.sleep(500)

        // Click new session button
        onView(withId(R.id.new_session_button)).perform(click())
        Thread.sleep(2000)

        val activity = activityRule.activity
        val sessionTabManager = activity.getSessionTabManager()

        // Verify we have multiple sessions
        assertTrue("Should have multiple sessions", sessionTabManager.getAllSessions().size > 1)

        // Verify current session is set
        val currentSession = activity.getCurrentSessionHandle()
        assertNotNull("Should have current session", currentSession)
    }

    @Test
    fun testPtyIndependence() {
        val activity = activityRule.activity
        val sessionTabManager = activity.getSessionTabManager()

        // Wait for activity to load
        Thread.sleep(2000)

        val initialSession = activity.getCurrentSessionHandle()
        assertNotNull("Should have initial session", initialSession)

        // Create a new session
        onView(withId(R.id.drawer_layout)).perform(swipeRight())
        Thread.sleep(500)
        onView(withId(R.id.new_session_button)).perform(click())
        Thread.sleep(2000)

        val newSession = activity.getCurrentSessionHandle()
        assertNotNull("Should have new session", newSession)
        assertNotEquals("New session should be different", initialSession, newSession)

        // Verify sessions are independent
        val initialSessionData = sessionTabManager.getSessionTabData(initialSession!!)
        val newSessionData = sessionTabManager.getSessionTabData(newSession!!)

        assertNotNull("Initial session data should exist", initialSessionData)
        assertNotNull("New session data should exist", newSessionData)
        assertNotEquals("Session data should be different objects", initialSessionData, newSessionData)

        // Verify independent terminal sessions
        assertNotEquals(
            "TermuxSessions should be different",
            initialSessionData?.termuxSession,
            newSessionData?.termuxSession
        )
    }

    @Test
    fun testDrawerFunctionality() {
        // Wait for activity to load
        Thread.sleep(2000)

        // Test opening drawer
        onView(withId(R.id.drawer_layout)).perform(swipeRight())
        Thread.sleep(500)

        // Verify drawer content is visible
        onView(withId(R.id.settings_button)).check(matches(isDisplayed()))
        onView(withId(R.id.terminal_sessions_list)).check(matches(isDisplayed()))
        onView(withId(R.id.toggle_keyboard_button)).check(matches(isDisplayed()))
        onView(withId(R.id.new_session_button)).check(matches(isDisplayed()))

        // Test closing drawer
        onView(withId(R.id.drawer_layout)).perform(swipeLeft())
        Thread.sleep(500)
    }

    @Test
    fun testAgentTabInactiveState() {
        // Wait for activity to load
        Thread.sleep(2000)

        // Switch to Agent tab
        onView(withText(R.string.tab_agent)).perform(click())
        Thread.sleep(1000)

        // Since agent is not configured, should show inactive state
        onView(withText(R.string.agent_not_configured)).check(matches(isDisplayed()))
        onView(withId(R.id.configure_agent_button)).check(matches(isDisplayed()))

        // Test configure button
        onView(withId(R.id.configure_agent_button)).perform(click())
        Thread.sleep(500)

        // This should open settings - in a real test we'd verify the settings activity
    }

    @Test
    fun testEditorFileIntegration() {
        // Wait for activity to load
        Thread.sleep(2000)

        // Switch to Editor tab
        onView(withText(R.string.tab_editor)).perform(click())
        Thread.sleep(1000)

        // Verify no file open state
        onView(withText(R.string.no_file_open)).check(matches(isDisplayed()))
        onView(withText(R.string.open_file_from_manager)).check(matches(isDisplayed()))

        // Verify editor buttons are disabled when no file is open
        onView(withId(R.id.save_button)).check(matches(not(isEnabled())))
        onView(withId(R.id.undo_button)).check(matches(not(isEnabled())))
        onView(withId(R.id.redo_button)).check(matches(not(isEnabled())))
    }

    @Test
    fun testTerminalTabSessionInfo() {
        // Wait for activity to load
        Thread.sleep(2000)

        // Ensure we're on terminal tab
        onView(withText(R.string.tab_terminal)).perform(click())
        Thread.sleep(1000)

        // Initially session info should be hidden
        onView(withId(R.id.session_info_panel)).check(matches(not(isDisplayed())))

        // Click toggle info button
        onView(withId(R.id.toggle_info_button)).perform(click())
        Thread.sleep(500)

        // Session info should now be visible
        onView(withId(R.id.session_info_panel)).check(matches(isDisplayed()))
        onView(withId(R.id.session_pid_text)).check(matches(isDisplayed()))
        onView(withId(R.id.session_working_dir_text)).check(matches(isDisplayed()))

        // Toggle again to hide
        onView(withId(R.id.toggle_info_button)).perform(click())
        Thread.sleep(500)

        // Should be hidden again
        onView(withId(R.id.session_info_panel)).check(matches(not(isDisplayed())))
    }
}