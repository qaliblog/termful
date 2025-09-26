package com.termux.app

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.view.View
import android.widget.ImageButton
import android.widget.ListView
import androidx.appcompat.app.AppCompatActivity
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.viewpager.widget.ViewPager
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.button.MaterialButton
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.termux.R
import com.termux.app.activities.SettingsActivity
import com.termux.app.tabs.*
import com.termux.app.terminal.TermuxSessionsListViewController
import com.termux.shared.termux.shell.command.runner.terminal.TermuxSession
import kotlinx.coroutines.launch

/**
 * Enhanced TermuxActivity with 4-tab system
 */
class TermuxTabbedActivity : AppCompatActivity(), ServiceConnection {
    
    // Service connection
    private var termuxService: TermuxService? = null
    
    // UI Components
    private var drawerLayout: DrawerLayout? = null
    private var sessionTabs: TabLayout? = null
    private var sessionViewPager: ViewPager2? = null
    private var terminalSessionsList: ListView? = null
    private var settingsButton: ImageButton? = null
    private var toggleKeyboardButton: MaterialButton? = null
    private var newSessionButton: MaterialButton? = null
    private var terminalToolbarViewPager: ViewPager? = null
    
    // Tab management
    private var currentSessionHandle: String? = null
    private var sessionTabsAdapter: SessionTabsAdapter? = null
    private var sessionsListController: TermuxSessionsListViewController? = null
    
    // Managers
    private lateinit var sessionTabManager: SessionTabManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_termux_tabbed)
        
        initializeManagers()
        initViews()
        setupUI()
        bindToTermuxService()
    }
    
    private fun initializeManagers() {
        sessionTabManager = SessionTabManager.getInstance()
    }
    
    private fun initViews() {
        drawerLayout = findViewById(R.id.drawer_layout)
        sessionTabs = findViewById(R.id.session_tabs)
        sessionViewPager = findViewById(R.id.session_view_pager)
        terminalSessionsList = findViewById(R.id.terminal_sessions_list)
        settingsButton = findViewById(R.id.settings_button)
        toggleKeyboardButton = findViewById(R.id.toggle_keyboard_button)
        newSessionButton = findViewById(R.id.new_session_button)
        terminalToolbarViewPager = findViewById(R.id.terminal_toolbar_view_pager)
    }
    
    private fun setupUI() {
        // Setup button listeners
        settingsButton?.setOnClickListener { openSettings() }
        toggleKeyboardButton?.setOnClickListener { toggleKeyboard() }
        newSessionButton?.setOnClickListener { createNewSession() }
        
        // Observe current session changes
        sessionTabManager.currentSession.observe(this) { sessionHandle ->
            if (sessionHandle != null && sessionHandle != currentSessionHandle) {
                switchToSession(sessionHandle)
            }
        }
    }
    
    private fun bindToTermuxService() {
        val serviceIntent = Intent(this, TermuxService::class.java)
        bindService(serviceIntent, this, BIND_AUTO_CREATE)
    }
    
    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
        val binder = service as? TermuxService.LocalBinder
        termuxService = binder?.service
        
        termuxService?.let { service ->
            setupSessionsList(service)
            
            // Create initial session if none exist
            if (service.termuxShellManager.mTermuxSessions.isEmpty()) {
                createNewSession()
            } else {
                // Initialize existing sessions
                service.termuxShellManager.mTermuxSessions.forEach { termuxSession ->
                    initializeSessionTabs(termuxSession)
                }
                
                // Switch to first session
                service.termuxShellManager.mTermuxSessions.firstOrNull()?.let { firstSession ->
                    switchToSession(firstSession.mHandle)
                }
            }
        }
    }
    
    override fun onServiceDisconnected(name: ComponentName?) {
        termuxService = null
    }
    
    private fun setupSessionsList(service: TermuxService) {
        sessionsListController = TermuxSessionsListViewController(
            this,
            terminalSessionsList!!
        )
        
        sessionsListController?.let { controller ->
            // Setup session selection listener
            controller.setOnSessionSelectedListener { termuxSession ->
                switchToSession(termuxSession.mHandle)
                drawerLayout?.closeDrawers()
            }
            
            // Setup session context menu
            controller.setOnSessionContextMenuListener { termuxSession, menuInfo ->
                // Handle context menu for session management
                showSessionContextMenu(termuxSession)
            }
            
            // Initialize with current sessions
            controller.notifyDataSetChanged()
        }
    }
    
    private fun createNewSession() {
        termuxService?.let { service ->
            lifecycleScope.launch {
                try {
                    // Create new terminal session through service
                    val termuxSession = service.createNewSession(null, null, null, false)
                    
                    if (termuxSession != null) {
                        initializeSessionTabs(termuxSession)
                        switchToSession(termuxSession.mHandle)
                        
                        // Update sessions list
                        sessionsListController?.notifyDataSetChanged()
                    }
                } catch (e: Exception) {
                    // Handle session creation error
                    showError("Failed to create new session: ${e.message}")
                }
            }
        }
    }
    
    private fun initializeSessionTabs(termuxSession: TermuxSession) {
        sessionTabManager.initializeSession(
            sessionHandle = termuxSession.mHandle,
            termuxSession = termuxSession,
            context = this
        )
    }
    
    private fun switchToSession(sessionHandle: String) {
        currentSessionHandle = sessionHandle
        sessionTabManager.setCurrentSession(sessionHandle)
        
        // Setup tabs for this session
        setupSessionTabs(sessionHandle)
        
        // Update sessions list selection
        sessionsListController?.setSelectedSession(sessionHandle)
    }
    
    private fun setupSessionTabs(sessionHandle: String) {
        // Create adapter for this session
        sessionTabsAdapter = SessionTabsAdapter(
            fragment = supportFragmentManager.findFragmentById(R.id.session_container) 
                ?: createContainerFragment(),
            sessionHandle = sessionHandle
        )
        
        // Setup ViewPager2
        sessionViewPager?.adapter = sessionTabsAdapter
        
        // Setup TabLayout with ViewPager2
        sessionTabs?.let { tabLayout ->
            sessionViewPager?.let { viewPager ->
                TabLayoutMediator(tabLayout, viewPager) { tab, position ->
                    when (position) {
                        SessionTabsAdapter.TAB_TERMINAL -> {
                            tab.text = getString(R.string.tab_terminal)
                            tab.setIcon(R.drawable.ic_terminal)
                        }
                        SessionTabsAdapter.TAB_FILE_MANAGER -> {
                            tab.text = getString(R.string.tab_files)
                            tab.setIcon(R.drawable.ic_folder)
                        }
                        SessionTabsAdapter.TAB_EDITOR -> {
                            tab.text = getString(R.string.tab_editor)
                            tab.setIcon(R.drawable.ic_edit)
                        }
                        SessionTabsAdapter.TAB_AGENT -> {
                            tab.text = getString(R.string.tab_agent)
                            tab.setIcon(R.drawable.ic_agent)
                        }
                    }
                }.attach()
            }
        }
        
        // Set initial tab based on session data
        val sessionData = sessionTabManager.getSessionTabData(sessionHandle)
        val currentTabType = sessionData?.getCurrentTabType() ?: TabType.TERMINAL
        val tabPosition = sessionTabsAdapter?.getPositionForTabType(currentTabType) ?: 0
        sessionViewPager?.setCurrentItem(tabPosition, false)
        
        // Listen for tab changes
        sessionViewPager?.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                
                sessionTabsAdapter?.let { adapter ->
                    val tabType = adapter.getTabType(position)
                    sessionData?.setCurrentTab(tabType)
                }
            }
        })
    }
    
    private fun createContainerFragment(): Fragment {
        // Create a container fragment if needed
        return Fragment()
    }
    
    private fun showSessionContextMenu(termuxSession: TermuxSession) {
        // TODO: Implement session context menu
        // - Rename session
        // - Kill session
        // - Duplicate session
    }
    
    private fun openSettings() {
        val intent = Intent(this, SettingsActivity::class.java)
        startActivity(intent)
    }
    
    private fun toggleKeyboard() {
        // TODO: Implement keyboard toggle
        // This should work with the active terminal tab
    }
    
    private fun showError(message: String) {
        // TODO: Implement error display
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_LONG).show()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        
        // Cleanup
        sessionTabManager.cleanup()
        
        // Unbind service
        try {
            unbindService(this)
        } catch (e: Exception) {
            // Service already unbound
        }
    }
    
    override fun onBackPressed() {
        // Close drawer if open
        if (drawerLayout?.isDrawerOpen(androidx.core.view.GravityCompat.START) == true) {
            drawerLayout?.closeDrawer(androidx.core.view.GravityCompat.START)
            return
        }
        
        // Handle back press in tabs
        currentSessionHandle?.let { sessionHandle ->
            val sessionData = sessionTabManager.getSessionTabData(sessionHandle)
            val currentTab = sessionData?.getCurrentTabType()
            
            when (currentTab) {
                TabType.FILE_MANAGER -> {
                    // Handle file manager back navigation
                    // TODO: Implement file manager back navigation
                }
                TabType.EDITOR -> {
                    // Check for unsaved changes
                    // TODO: Implement editor unsaved changes check
                }
                TabType.AGENT -> {
                    // Close agent side panel if open
                    // TODO: Implement agent side panel close
                }
                else -> {
                    // Default behavior for terminal
                    super.onBackPressed()
                }
            }
        } ?: run {
            super.onBackPressed()
        }
    }
    
    // Provide access to current session for fragments
    fun getCurrentSessionHandle(): String? = currentSessionHandle
    
    fun getTermuxService(): TermuxService? = termuxService
    
    fun getSessionTabManager(): SessionTabManager = sessionTabManager
}