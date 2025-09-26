package com.termful.app.tabs.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.termful.R
import com.termful.app.tabs.SessionTabData
import com.termful.app.tabs.SessionTabManager
import com.termful.app.tabs.TerminalTabData
import com.termful.view.TerminalView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Fragment for the Terminal tab - displays the interactive terminal
 */
class TerminalTabFragment : Fragment() {
    
    private var sessionHandle: String? = null
    private var terminalView: TerminalView? = null
    private var sessionInfoPanel: LinearLayout? = null
    private var sessionPidText: TextView? = null
    private var sessionWorkingDirText: TextView? = null
    private var toggleInfoButton: ImageButton? = null
    
    companion object {
        private const val ARG_SESSION_HANDLE = "session_handle"
        
        fun newInstance(sessionHandle: String): TerminalTabFragment {
            return TerminalTabFragment().apply {
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
        return inflater.inflate(R.layout.fragment_terminal, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        sessionInfoPanel = view.findViewById(R.id.session_info_panel)
        sessionPidText = view.findViewById(R.id.session_pid_text)
        sessionWorkingDirText = view.findViewById(R.id.session_working_dir_text)
        toggleInfoButton = view.findViewById(R.id.toggle_info_button)
        terminalView = view.findViewById(R.id.terminal_view)
        
        setupUI()
        setupTerminal()
    }
    
    private fun setupUI() {
        toggleInfoButton?.setOnClickListener {
            toggleSessionInfo()
        }
        
        // Initialize terminal tab data if not exists
        sessionHandle?.let { handle ->
            val tabData = SessionTabManager.getInstance().getSessionTabData(handle)
            if (tabData?.terminalTabData == null) {
                tabData?.terminalTabData = TerminalTabData(handle)
            }
        }
        
        updateSessionInfo()
    }
    
    private fun setupTerminal() {
        sessionHandle?.let { handle ->
            val tabData = SessionTabManager.getInstance().getSessionTabData(handle)
            val termuxSession = tabData?.termuxSession
            
            termuxSession?.let { session ->
                terminalView?.attachSession(session.terminalSession)
                // Terminal view setup would be handled by existing Termux code
            }
        }
    }
    
    private fun toggleSessionInfo() {
        sessionHandle?.let { handle ->
            val tabData = SessionTabManager.getInstance().getSessionTabData(handle)
            val terminalTabData = tabData?.terminalTabData
            
            if (terminalTabData != null) {
                terminalTabData.showSessionInfo = !terminalTabData.showSessionInfo
                sessionInfoPanel?.visibility = if (terminalTabData.showSessionInfo) {
                    View.VISIBLE
                } else {
                    View.GONE
                }
                
                if (terminalTabData.showSessionInfo) {
                    updateSessionInfo()
                }
            }
        }
    }
    
    private fun updateSessionInfo() {
        sessionHandle?.let { handle ->
            val tabData = SessionTabManager.getInstance().getSessionTabData(handle)
            val termuxSession = tabData?.termuxSession
            
            if (termuxSession != null) {
                lifecycleScope.launch {
                    val processInfo = getProcessInfo(termuxSession)
                    withContext(Dispatchers.Main) {
                        sessionPidText?.text = "PID: ${processInfo.first}"
                        sessionWorkingDirText?.text = "Dir: ${processInfo.second}"
                    }
                }
            }
        }
    }
    
    private suspend fun getProcessInfo(termuxSession: com.termux.shared.termux.shell.command.runner.terminal.TermuxSession): Pair<String, String> {
        return withContext(Dispatchers.IO) {
            try {
                // Get PID from the terminal session
                val pid = termuxSession.terminalSession.pid.toString()
                
                // Get working directory by reading from proc filesystem
                val workingDir = try {
                    val process = Runtime.getRuntime().exec("readlink /proc/$pid/cwd")
                    val reader = BufferedReader(InputStreamReader(process.inputStream))
                    reader.readLine() ?: "unknown"
                } catch (e: Exception) {
                    termuxSession.terminalSession.cwd ?: "unknown"
                }
                
                Pair(pid, workingDir)
            } catch (e: Exception) {
                Pair("unknown", "unknown")
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        // Update session info when fragment becomes visible
        sessionHandle?.let { handle ->
            val tabData = SessionTabManager.getInstance().getSessionTabData(handle)
            val terminalTabData = tabData?.terminalTabData
            
            if (terminalTabData?.showSessionInfo == true) {
                updateSessionInfo()
            }
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        terminalView = null
        sessionInfoPanel = null
        sessionPidText = null
        sessionWorkingDirText = null
        toggleInfoButton = null
    }
}