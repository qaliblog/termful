package com.termux.app.tabs.fragments

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.button.MaterialButton
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.termux.R
import com.termux.app.activities.SettingsActivity
import com.termux.app.agent.*
import com.termux.app.tabs.*
import kotlinx.coroutines.launch

/**
 * Fragment for the AI Agent tab
 */
class AgentTabFragment : Fragment() {
    
    private var sessionHandle: String? = null
    
    // UI Components
    private var agentStatusIndicator: View? = null
    private var agentStatusText: TextView? = null
    private var workingDirButton: ImageButton? = null
    private var historyButton: ImageButton? = null
    private var agentSettingsButton: ImageButton? = null
    private var agentProcessInfo: LinearLayout? = null
    private var agentProcessPidText: TextView? = null
    private var agentWorkingDirText: TextView? = null
    private var terminateAgentButton: ImageButton? = null
    private var agentConsoleRecycler: RecyclerView? = null
    private var quickCommandsRecycler: RecyclerView? = null
    private var agentInputText: EditText? = null
    private var sendButton: ImageButton? = null
    private var sidePanel: LinearLayout? = null
    private var sidePanelTabs: TabLayout? = null
    private var sidePanelViewPager: ViewPager2? = null
    private var agentInactiveState: LinearLayout? = null
    private var configureAgentButton: MaterialButton? = null
    
    // Adapters and managers
    private var consoleAdapter: AgentConsoleAdapter? = null
    private var agentManager: AgentManager? = null
    
    companion object {
        private const val ARG_SESSION_HANDLE = "session_handle"
        
        fun newInstance(sessionHandle: String): AgentTabFragment {
            return AgentTabFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_SESSION_HANDLE, sessionHandle)
                }
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sessionHandle = arguments?.getString(ARG_SESSION_HANDLE)
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_agent, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        initViews(view)
        setupAgentData()
        setupUI()
        checkAgentConfiguration()
    }
    
    private fun initViews(view: View) {
        agentStatusIndicator = view.findViewById(R.id.agent_status_indicator)
        agentStatusText = view.findViewById(R.id.agent_status_text)
        workingDirButton = view.findViewById(R.id.working_dir_button)
        historyButton = view.findViewById(R.id.history_button)
        agentSettingsButton = view.findViewById(R.id.agent_settings_button)
        agentProcessInfo = view.findViewById(R.id.agent_process_info)
        agentProcessPidText = view.findViewById(R.id.agent_process_pid_text)
        agentWorkingDirText = view.findViewById(R.id.agent_working_dir_text)
        terminateAgentButton = view.findViewById(R.id.terminate_agent_button)
        agentConsoleRecycler = view.findViewById(R.id.agent_console_recycler)
        quickCommandsRecycler = view.findViewById(R.id.quick_commands_recycler)
        agentInputText = view.findViewById(R.id.agent_input_text)
        sendButton = view.findViewById(R.id.send_button)
        sidePanel = view.findViewById(R.id.side_panel)
        sidePanelTabs = view.findViewById(R.id.side_panel_tabs)
        sidePanelViewPager = view.findViewById(R.id.side_panel_view_pager)
        agentInactiveState = view.findViewById(R.id.agent_inactive_state)
        configureAgentButton = view.findViewById(R.id.configure_agent_button)
    }
    
    private fun setupAgentData() {
        sessionHandle?.let { handle ->
            val tabData = SessionTabManager.getInstance().getSessionTabData(handle)
            if (tabData?.agentTabData == null) {
                tabData?.agentTabData = AgentTabData(handle)
            }
            
            // Initialize agent manager
            agentManager = AgentManager.getInstance(requireContext())
        }
    }
    
    private fun setupUI() {
        // Setup console
        consoleAdapter = AgentConsoleAdapter()
        agentConsoleRecycler?.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = consoleAdapter
        }
        
        // Setup input
        agentInputText?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                sendButton?.isEnabled = !s.isNullOrBlank()
            }
        })
        
        // Setup button listeners
        workingDirButton?.setOnClickListener { selectWorkingDirectory() }
        historyButton?.setOnClickListener { toggleHistory() }
        agentSettingsButton?.setOnClickListener { openAgentSettings() }
        terminateAgentButton?.setOnClickListener { terminateAgent() }
        sendButton?.setOnClickListener { sendMessage() }
        configureAgentButton?.setOnClickListener { openAgentSettings() }
        
        // Setup side panel
        setupSidePanel()
        
        // Initialize status
        updateAgentStatus(AgentStatus.INACTIVE)
    }
    
    private fun setupSidePanel() {
        // TODO: Setup side panel with ViewPager2 for history and commands
        sidePanelViewPager?.adapter = AgentSidePanelAdapter(this)
        
        sidePanelTabs?.let { tabLayout ->
            sidePanelViewPager?.let { viewPager ->
                TabLayoutMediator(tabLayout, viewPager) { tab, position ->
                    tab.text = when (position) {
                        0 -> getString(R.string.tab_history)
                        1 -> getString(R.string.tab_commands)
                        else -> ""
                    }
                }.attach()
            }
        }
    }
    
    private fun checkAgentConfiguration() {
        agentManager?.let { manager ->
            lifecycleScope.launch {
                val isConfigured = manager.isConfigured()
                if (isConfigured) {
                    showAgentInterface()
                    initializeAgent()
                } else {
                    showInactiveState()
                }
            }
        }
    }
    
    private fun showAgentInterface() {
        agentInactiveState?.visibility = View.GONE
        agentConsoleRecycler?.visibility = View.VISIBLE
        // Show other UI components
    }
    
    private fun showInactiveState() {
        agentInactiveState?.visibility = View.VISIBLE
        agentConsoleRecycler?.visibility = View.GONE
        // Hide other UI components
    }
    
    private fun initializeAgent() {
        sessionHandle?.let { handle ->
            val tabData = SessionTabManager.getInstance().getSessionTabData(handle)
            val agentData = tabData?.agentTabData ?: return
            
            lifecycleScope.launch {
                try {
                    updateAgentStatus(AgentStatus.ACTIVE)
                    
                    agentManager?.let { manager ->
                        // Start agent process for this session
                        val processId = manager.startAgentProcess(
                            sessionHandle = handle,
                            workingDirectory = agentData.workingDirectory,
                            onOutput = { output -> addConsoleOutput(output) },
                            onError = { error -> addConsoleError(error) },
                            onProcessExit = { exitCode -> onAgentProcessExit(exitCode) }
                        )
                        
                        agentData.agentProcessId = processId
                        agentData.isAgentActive = true
                        
                        updateProcessInfo(processId, agentData.workingDirectory)
                        addConsoleMessage("Agent initialized successfully", AgentMessageType.SYSTEM)
                    }
                } catch (e: Exception) {
                    updateAgentStatus(AgentStatus.ERROR)
                    addConsoleError("Failed to initialize agent: ${e.message}")
                }
            }
        }
    }
    
    private fun updateAgentStatus(status: AgentStatus) {
        sessionHandle?.let { handle ->
            val tabData = SessionTabManager.getInstance().getSessionTabData(handle)
            val agentData = tabData?.agentTabData ?: return
            
            agentData.agentStatus = status
            agentStatusText?.text = status.displayName
            
            val colorRes = when (status) {
                AgentStatus.INACTIVE -> R.color.status_inactive
                AgentStatus.ACTIVE -> R.color.status_active
                AgentStatus.PROCESSING -> R.color.status_processing
                AgentStatus.ERROR -> R.color.status_error
            }
            
            agentStatusIndicator?.setBackgroundResource(colorRes)
            
            // Show/hide process info based on status
            agentProcessInfo?.visibility = if (status == AgentStatus.ACTIVE || status == AgentStatus.PROCESSING) {
                View.VISIBLE
            } else {
                View.GONE
            }
        }
    }
    
    private fun updateProcessInfo(processId: Int, workingDirectory: String) {
        agentProcessPidText?.text = "Agent PID: $processId"
        agentWorkingDirText?.text = "Working Dir: $workingDirectory"
    }
    
    private fun sendMessage() {
        val message = agentInputText?.text?.toString()?.trim()
        if (message.isNullOrEmpty()) return
        
        sessionHandle?.let { handle ->
            val tabData = SessionTabManager.getInstance().getSessionTabData(handle)
            val agentData = tabData?.agentTabData ?: return
            
            // Clear input
            agentInputText?.text?.clear()
            
            // Add user message to console
            addConsoleMessage(message, AgentMessageType.USER)
            
            // Update status to processing
            updateAgentStatus(AgentStatus.PROCESSING)
            
            lifecycleScope.launch {
                try {
                    agentManager?.let { manager ->
                        // Send message to agent
                        val response = manager.sendMessage(
                            sessionHandle = handle,
                            message = message,
                            workingDirectory = agentData.workingDirectory
                        )
                        
                        // Add agent response to console
                        addConsoleMessage(response, AgentMessageType.AGENT)
                        
                        // Save to history
                        AgentHistoryManager.getInstance(requireContext())
                            .addHistoryEntry(
                                AgentHistoryEntry(
                                    id = java.util.UUID.randomUUID().toString(),
                                    query = message,
                                    response = response,
                                    timestamp = System.currentTimeMillis(),
                                    success = true,
                                    sessionHandle = handle,
                                    workingDirectory = agentData.workingDirectory
                                )
                            )
                        
                        updateAgentStatus(AgentStatus.ACTIVE)
                    }
                } catch (e: Exception) {
                    addConsoleError("Error: ${e.message}")
                    updateAgentStatus(AgentStatus.ERROR)
                    
                    // Save error to history
                    AgentHistoryManager.getInstance(requireContext())
                        .addHistoryEntry(
                            AgentHistoryEntry(
                                id = java.util.UUID.randomUUID().toString(),
                                query = message,
                                response = "Error: ${e.message}",
                                timestamp = System.currentTimeMillis(),
                                success = false,
                                sessionHandle = handle,
                                workingDirectory = agentData.workingDirectory
                            )
                        )
                }
            }
        }
    }
    
    private fun addConsoleMessage(message: String, type: AgentMessageType) {
        val consoleMessage = AgentConsoleMessage(
            id = java.util.UUID.randomUUID().toString(),
            content = message,
            type = type,
            timestamp = System.currentTimeMillis()
        )
        
        consoleAdapter?.addMessage(consoleMessage)
        agentConsoleRecycler?.scrollToPosition(consoleAdapter?.itemCount?.minus(1) ?: 0)
    }
    
    private fun addConsoleOutput(output: String) {
        addConsoleMessage(output, AgentMessageType.OUTPUT)
    }
    
    private fun addConsoleError(error: String) {
        addConsoleMessage(error, AgentMessageType.ERROR)
    }
    
    private fun onAgentProcessExit(exitCode: Int) {
        sessionHandle?.let { handle ->
            val tabData = SessionTabManager.getInstance().getSessionTabData(handle)
            val agentData = tabData?.agentTabData ?: return
            
            agentData.agentProcessId = null
            agentData.isAgentActive = false
            
            updateAgentStatus(AgentStatus.INACTIVE)
            addConsoleMessage("Agent process exited with code: $exitCode", AgentMessageType.SYSTEM)
        }
    }
    
    private fun selectWorkingDirectory() {
        // TODO: Implement directory selector using FileManagerTabFragment components
        // For now, show a simple dialog
        
        val editText = EditText(requireContext())
        editText.hint = "Working directory path"
        
        sessionHandle?.let { handle ->
            val tabData = SessionTabManager.getInstance().getSessionTabData(handle)
            val agentData = tabData?.agentTabData
            editText.setText(agentData?.workingDirectory ?: "/")
        }
        
        AlertDialog.Builder(requireContext())
            .setTitle("Select Working Directory")
            .setView(editText)
            .setPositiveButton("Select") { _, _ ->
                val newDir = editText.text.toString().trim()
                if (newDir.isNotEmpty()) {
                    updateWorkingDirectory(newDir)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun updateWorkingDirectory(directory: String) {
        sessionHandle?.let { handle ->
            val tabData = SessionTabManager.getInstance().getSessionTabData(handle)
            val agentData = tabData?.agentTabData ?: return
            
            agentData.workingDirectory = directory
            agentWorkingDirText?.text = "Working Dir: $directory"
            
            addConsoleMessage("Working directory changed to: $directory", AgentMessageType.SYSTEM)
        }
    }
    
    private fun toggleHistory() {
        sessionHandle?.let { handle ->
            val tabData = SessionTabManager.getInstance().getSessionTabData(handle)
            val agentData = tabData?.agentTabData ?: return
            
            agentData.showSidePanel = !agentData.showSidePanel
            sidePanel?.visibility = if (agentData.showSidePanel) View.VISIBLE else View.GONE
        }
    }
    
    private fun openAgentSettings() {
        val intent = Intent(requireContext(), SettingsActivity::class.java)
        intent.putExtra("fragment", "agent_settings")
        startActivity(intent)
    }
    
    private fun terminateAgent() {
        AlertDialog.Builder(requireContext())
            .setTitle("Terminate Agent")
            .setMessage("Are you sure you want to terminate the agent process?")
            .setPositiveButton("Terminate") { _, _ ->
                performTerminateAgent()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun performTerminateAgent() {
        sessionHandle?.let { handle ->
            val tabData = SessionTabManager.getInstance().getSessionTabData(handle)
            val agentData = tabData?.agentTabData ?: return
            
            lifecycleScope.launch {
                try {
                    agentManager?.terminateAgentProcess(handle)
                    
                    agentData.agentProcessId = null
                    agentData.isAgentActive = false
                    
                    updateAgentStatus(AgentStatus.INACTIVE)
                    addConsoleMessage("Agent process terminated", AgentMessageType.SYSTEM)
                } catch (e: Exception) {
                    addConsoleError("Failed to terminate agent: ${e.message}")
                }
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        // Refresh agent status
        checkAgentConfiguration()
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        
        // Cleanup agent process if needed
        sessionHandle?.let { handle ->
            val tabData = SessionTabManager.getInstance().getSessionTabData(handle)
            val agentData = tabData?.agentTabData
            
            if (agentData?.isAgentActive == true) {
                lifecycleScope.launch {
                    try {
                        agentManager?.terminateAgentProcess(handle)
                    } catch (e: Exception) {
                        // Ignore cleanup errors
                    }
                }
            }
        }
    }
}

// Console message types
enum class AgentMessageType {
    USER, AGENT, SYSTEM, OUTPUT, ERROR
}

// Console message data class
data class AgentConsoleMessage(
    val id: String,
    val content: String,
    val type: AgentMessageType,
    val timestamp: Long
)

// Console adapter class
class AgentConsoleAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private val messages = mutableListOf<AgentConsoleMessage>()
    
    fun addMessage(message: AgentConsoleMessage) {
        messages.add(message)
        notifyItemInserted(messages.size - 1)
    }
    
    override fun getItemCount() = messages.size
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        // TODO: Implement ViewHolder for different message types
        return object : RecyclerView.ViewHolder(View(parent.context)) {}
    }
    
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        // TODO: Implement bind logic
    }
}

// Side panel adapter for history and commands
class AgentSidePanelAdapter(fragment: Fragment) : androidx.viewpager2.adapter.FragmentStateAdapter(fragment) {
    override fun getItemCount() = 2
    
    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> AgentHistoryFragment()
            1 -> AgentCommandsFragment()
            else -> Fragment()
        }
    }
}

// Placeholder fragments for side panel
class AgentHistoryFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        // TODO: Implement history view
        return TextView(requireContext()).apply {
            text = "Agent History"
            gravity = android.view.Gravity.CENTER
        }
    }
}

class AgentCommandsFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        // TODO: Implement commands view
        return TextView(requireContext()).apply {
            text = "Quick Commands"
            gravity = android.view.Gravity.CENTER
        }
    }
}