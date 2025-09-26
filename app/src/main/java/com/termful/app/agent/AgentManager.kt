package com.termful.app.agent

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages AI agent processes and communication
 */
class AgentManager private constructor(private val context: Context) {
    
    companion object {
        @Volatile
        private var INSTANCE: AgentManager? = null
        
        fun getInstance(context: Context): AgentManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AgentManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    // Map of session handle to agent process
    private val agentProcesses = ConcurrentHashMap<String, AgentProcess>()
    
    // Configuration and provider management
    private val configManager = AgentConfigManager.getInstance(context)
    private val providerManager = AgentProviderManager.getInstance(context)
    
    /**
     * Check if agent is configured with at least one provider and API key
     */
    suspend fun isConfigured(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val config = configManager.getAgentConfig()
                config.providers.isNotEmpty() && 
                config.providers.any { it.apiKeys.isNotEmpty() }
            } catch (e: Exception) {
                false
            }
        }
    }
    
    /**
     * Start agent process for a session
     */
    suspend fun startAgentProcess(
        sessionHandle: String,
        workingDirectory: String,
        onOutput: (String) -> Unit,
        onError: (String) -> Unit,
        onProcessExit: (Int) -> Unit
    ): Int {
        return withContext(Dispatchers.IO) {
            try {
                // Create agent process
                val agentProcess = AgentProcess(
                    sessionHandle = sessionHandle,
                    workingDirectory = workingDirectory,
                    configManager = configManager,
                    providerManager = providerManager,
                    onOutput = onOutput,
                    onError = onError,
                    onProcessExit = { exitCode ->
                        agentProcesses.remove(sessionHandle)
                        onProcessExit(exitCode)
                    }
                )
                
                val processId = agentProcess.start()
                agentProcesses[sessionHandle] = agentProcess
                
                processId
            } catch (e: Exception) {
                onError("Failed to start agent process: ${e.message}")
                throw e
            }
        }
    }
    
    /**
     * Send message to agent
     */
    suspend fun sendMessage(
        sessionHandle: String,
        message: String,
        workingDirectory: String
    ): String {
        return withContext(Dispatchers.IO) {
            val agentProcess = agentProcesses[sessionHandle]
                ?: throw IllegalStateException("Agent process not found for session: $sessionHandle")
            
            agentProcess.sendMessage(message, workingDirectory)
        }
    }
    
    /**
     * Terminate agent process for a session
     */
    suspend fun terminateAgentProcess(sessionHandle: String) {
        withContext(Dispatchers.IO) {
            val agentProcess = agentProcesses[sessionHandle]
            if (agentProcess != null) {
                agentProcess.terminate()
                agentProcesses.remove(sessionHandle)
            }
        }
    }
    
    /**
     * Get agent process status
     */
    fun getAgentProcessStatus(sessionHandle: String): AgentProcessStatus {
        val agentProcess = agentProcesses[sessionHandle]
        return agentProcess?.getStatus() ?: AgentProcessStatus.NOT_RUNNING
    }
    
    /**
     * Cleanup all agent processes
     */
    fun cleanup() {
        agentProcesses.values.forEach { process ->
            try {
                process.terminate()
            } catch (e: Exception) {
                // Ignore cleanup errors
            }
        }
        agentProcesses.clear()
    }
}

/**
 * Represents an individual agent process for a session
 */
class AgentProcess(
    private val sessionHandle: String,
    private val workingDirectory: String,
    private val configManager: AgentConfigManager,
    private val providerManager: AgentProviderManager,
    private val onOutput: (String) -> Unit,
    private val onError: (String) -> Unit,
    private val onProcessExit: (Int) -> Unit
) {
    
    private var process: Process? = null
    private var processId: Int = -1
    private var outputStream: OutputStreamWriter? = null
    private var inputReader: BufferedReader? = null
    private var errorReader: BufferedReader? = null
    
    @Volatile
    private var status = AgentProcessStatus.NOT_RUNNING
    
    /**
     * Start the agent process
     */
    fun start(): Int {
        try {
            status = AgentProcessStatus.STARTING
            
            // Create agent script/command
            val agentScript = createAgentScript()
            
            // Start process in working directory
            val processBuilder = ProcessBuilder()
                .command("sh", "-c", agentScript)
                .directory(File(workingDirectory))
                .redirectErrorStream(false)
            
            // Set environment variables
            val env = processBuilder.environment()
            env["AGENT_SESSION"] = sessionHandle
            env["AGENT_WORKING_DIR"] = workingDirectory
            
            process = processBuilder.start()
            processId = getProcessId(process!!)
            
            // Setup I/O streams
            outputStream = OutputStreamWriter(process!!.outputStream)
            inputReader = BufferedReader(InputStreamReader(process!!.inputStream))
            errorReader = BufferedReader(InputStreamReader(process!!.errorStream))
            
            // Start monitoring threads
            startOutputMonitoring()
            startErrorMonitoring()
            startProcessMonitoring()
            
            status = AgentProcessStatus.RUNNING
            
            return processId
        } catch (e: Exception) {
            status = AgentProcessStatus.ERROR
            throw e
        }
    }
    
    /**
     * Send message to agent process
     */
    suspend fun sendMessage(message: String, workingDir: String): String {
        return withContext(Dispatchers.IO) {
            if (status != AgentProcessStatus.RUNNING) {
                throw IllegalStateException("Agent process is not running")
            }
            
            try {
                status = AgentProcessStatus.PROCESSING
                
                // Create request for agent
                val request = AgentRequest(
                    message = message,
                    workingDirectory = workingDir,
                    sessionHandle = sessionHandle,
                    timestamp = System.currentTimeMillis()
                )
                
                // Send request to process
                val requestJson = gson.toJson(request)
                outputStream?.write("$requestJson\n")
                outputStream?.flush()
                
                // Wait for response (with timeout)
                val response = waitForResponse()
                
                status = AgentProcessStatus.RUNNING
                
                response
            } catch (e: Exception) {
                status = AgentProcessStatus.ERROR
                throw e
            }
        }
    }
    
    /**
     * Terminate the agent process
     */
    fun terminate() {
        try {
            status = AgentProcessStatus.TERMINATING
            
            outputStream?.close()
            inputReader?.close()
            errorReader?.close()
            
            process?.destroyForcibly()
            process?.waitFor()
            
            status = AgentProcessStatus.NOT_RUNNING
        } catch (e: Exception) {
            status = AgentProcessStatus.ERROR
        }
    }
    
    /**
     * Get current process status
     */
    fun getStatus(): AgentProcessStatus = status
    
    private fun createAgentScript(): String {
        // Create a shell script that will handle agent communication
        // This script will use the configured AI providers to process requests
        return """
            #!/bin/bash
            
            # Agent communication script
            AGENT_SESSION="$sessionHandle"
            AGENT_WORKING_DIR="$workingDirectory"
            
            # Function to call AI API
            call_ai_api() {
                local message=${'$'}1
                local working_dir=${'$'}2
                
                # Read configuration and make API call
                # This will be implemented to use the configured providers with rotation
                python3 -c "
import json
import sys
import os
import subprocess
from pathlib import Path

# Add agent helper scripts to path
sys.path.insert(0, os.path.join(os.path.dirname(__file__), 'agent_helpers'))

# Import agent modules (to be created)
from agent_api_client import AgentAPIClient
from agent_config import load_agent_config

def main():
    # Read input
    line = sys.stdin.readline().strip()
    if not line:
        return
    
    try:
        request = json.loads(line)
        message = request.get('message', '')
        working_dir = request.get('workingDirectory', os.getcwd())
        
        # Load configuration
        config = load_agent_config()
        
        # Create API client with rotation
        client = AgentAPIClient(config)
        
        # Process message
        response = client.process_message(message, working_dir)
        
        # Return response
        result = {
            'response': response,
            'success': True,
            'timestamp': request.get('timestamp', 0)
        }
        
        print(json.dumps(result))
        sys.stdout.flush()
        
    except Exception as e:
        error_result = {
            'response': f'Error: {str(e)}',
            'success': False,
            'error': str(e),
            'timestamp': request.get('timestamp', 0) if 'request' in locals() else 0
        }
        print(json.dumps(error_result))
        sys.stdout.flush()

if __name__ == '__main__':
    main()
"
            }
            
            # Main communication loop
            while IFS= read -r line; do
                if [ -n "${'$'}line" ]; then
                    echo "${'$'}line" | call_ai_api
                fi
            done
        """.trimIndent()
    }
    
    private fun getProcessId(process: Process): Int {
        return try {
            val field = process.javaClass.getDeclaredField("pid")
            field.isAccessible = true
            field.getInt(process)
        } catch (e: Exception) {
            -1
        }
    }
    
    private fun startOutputMonitoring() {
        Thread {
            try {
                inputReader?.use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        line?.let { onOutput(it) }
                    }
                }
            } catch (e: Exception) {
                if (status == AgentProcessStatus.RUNNING) {
                    onError("Output monitoring error: ${e.message}")
                }
            }
        }.start()
    }
    
    private fun startErrorMonitoring() {
        Thread {
            try {
                errorReader?.use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        line?.let { onError(it) }
                    }
                }
            } catch (e: Exception) {
                if (status == AgentProcessStatus.RUNNING) {
                    onError("Error monitoring error: ${e.message}")
                }
            }
        }.start()
    }
    
    private fun startProcessMonitoring() {
        Thread {
            try {
                val exitCode = process?.waitFor() ?: -1
                onProcessExit(exitCode)
            } catch (e: Exception) {
                onProcessExit(-1)
            }
        }.start()
    }
    
    private suspend fun waitForResponse(): String {
        return withContext(Dispatchers.IO) {
            // Wait for response from agent process
            // This is a simplified implementation
            try {
                val response = inputReader?.readLine()
                if (response != null) {
                    val responseData = gson.fromJson(response, AgentResponse::class.java)
                    responseData.response
                } else {
                    throw IOException("No response from agent process")
                }
            } catch (e: Exception) {
                throw e
            }
        }
    }
    
    companion object {
        private val gson = com.google.gson.Gson()
    }
}

/**
 * Agent process status
 */
enum class AgentProcessStatus {
    NOT_RUNNING,
    STARTING,
    RUNNING,
    PROCESSING,
    TERMINATING,
    ERROR
}

/**
 * Request data class for agent communication
 */
data class AgentRequest(
    val message: String,
    val workingDirectory: String,
    val sessionHandle: String,
    val timestamp: Long
)

/**
 * Response data class for agent communication
 */
data class AgentResponse(
    val response: String,
    val success: Boolean,
    val error: String? = null,
    val timestamp: Long
)