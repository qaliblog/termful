package com.termful.app.agent

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.SecureRandom
import java.util.*

/**
 * Manages secure storage and retrieval of agent configuration including API keys
 */
class AgentConfigManager private constructor(private val context: Context) {
    
    companion object {
        @Volatile
        private var INSTANCE: AgentConfigManager? = null
        
        private const val ENCRYPTED_PREFS_FILE = "agent_config_secure"
        private const val REGULAR_PREFS_FILE = "agent_config"
        private const val CONFIG_KEY = "agent_config_data"
        
        fun getInstance(context: Context): AgentConfigManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AgentConfigManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private val gson = Gson()
    private val regularPrefs: SharedPreferences
    private val encryptedPrefs: SharedPreferences
    
    init {
        // Initialize regular SharedPreferences for non-sensitive data
        regularPrefs = context.getSharedPreferences(REGULAR_PREFS_FILE, Context.MODE_PRIVATE)
        
        // Initialize encrypted SharedPreferences for API keys
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        
        encryptedPrefs = EncryptedSharedPreferences.create(
            context,
            ENCRYPTED_PREFS_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
    
    /**
     * Get the complete agent configuration
     */
    suspend fun getAgentConfig(): AgentConfig {
        return withContext(Dispatchers.IO) {
            try {
                val configJson = regularPrefs.getString(CONFIG_KEY, null)
                if (configJson != null) {
                    val config = gson.fromJson(configJson, AgentConfig::class.java)
                    
                    // Load encrypted API keys
                    config.providers.forEach { provider ->
                        provider.apiKeys.forEach { apiKey ->
                            val encryptedKey = encryptedPrefs.getString("api_key_${apiKey.id}", null)
                            if (encryptedKey != null) {
                                apiKey.key = encryptedKey
                            }
                        }
                    }
                    
                    config
                } else {
                    createDefaultConfig()
                }
            } catch (e: Exception) {
                createDefaultConfig()
            }
        }
    }
    
    /**
     * Save the agent configuration
     */
    suspend fun saveAgentConfig(config: AgentConfig) {
        withContext(Dispatchers.IO) {
            try {
                // Save API keys to encrypted storage
                config.providers.forEach { provider ->
                    provider.apiKeys.forEach { apiKey ->
                        if (apiKey.key.isNotEmpty()) {
                            encryptedPrefs.edit()
                                .putString("api_key_${apiKey.id}", apiKey.key)
                                .apply()
                        }
                    }
                }
                
                // Create a copy without actual keys for regular storage
                val configForStorage = config.copy()
                configForStorage.providers.forEach { provider ->
                    provider.apiKeys.forEach { apiKey ->
                        apiKey.key = "" // Don't store in regular prefs
                    }
                }
                
                // Save configuration structure to regular prefs
                val configJson = gson.toJson(configForStorage)
                regularPrefs.edit()
                    .putString(CONFIG_KEY, configJson)
                    .apply()
                    
            } catch (e: Exception) {
                throw e
            }
        }
    }
    
    /**
     * Add a new provider
     */
    suspend fun addProvider(provider: AgentProvider) {
        val config = getAgentConfig()
        config.providers.add(provider)
        saveAgentConfig(config)
    }
    
    /**
     * Remove a provider
     */
    suspend fun removeProvider(providerId: String) {
        val config = getAgentConfig()
        config.providers.removeAll { it.providerId == providerId }
        
        // Remove associated API keys from encrypted storage
        config.providers.find { it.providerId == providerId }?.apiKeys?.forEach { apiKey ->
            encryptedPrefs.edit()
                .remove("api_key_${apiKey.id}")
                .apply()
        }
        
        saveAgentConfig(config)
    }
    
    /**
     * Add API key to a provider
     */
    suspend fun addApiKey(providerId: String, apiKey: AgentApiKey) {
        val config = getAgentConfig()
        val provider = config.providers.find { it.providerId == providerId }
            ?: throw IllegalArgumentException("Provider not found: $providerId")
        
        provider.apiKeys.add(apiKey)
        saveAgentConfig(config)
    }
    
    /**
     * Remove API key from a provider
     */
    suspend fun removeApiKey(providerId: String, keyId: String) {
        val config = getAgentConfig()
        val provider = config.providers.find { it.providerId == providerId }
            ?: throw IllegalArgumentException("Provider not found: $providerId")
        
        provider.apiKeys.removeAll { it.id == keyId }
        
        // Remove from encrypted storage
        encryptedPrefs.edit()
            .remove("api_key_$keyId")
            .apply()
        
        saveAgentConfig(config)
    }
    
    /**
     * Test an API key
     */
    suspend fun testApiKey(providerId: String, keyId: String): AgentKeyTestResult {
        return withContext(Dispatchers.IO) {
            try {
                val config = getAgentConfig()
                val provider = config.providers.find { it.providerId == providerId }
                    ?: return@withContext AgentKeyTestResult(false, "Provider not found")
                
                val apiKey = provider.apiKeys.find { it.id == keyId }
                    ?: return@withContext AgentKeyTestResult(false, "API key not found")
                
                // Test the key with the provider
                val providerClient = AgentProviderManager.getInstance(context)
                    .getProviderClient(providerId)
                    ?: return@withContext AgentKeyTestResult(false, "Provider client not available")
                
                val testResult = providerClient.testApiKey(apiKey.key)
                AgentKeyTestResult(testResult.isValid, testResult.message)
                
            } catch (e: Exception) {
                AgentKeyTestResult(false, "Test failed: ${e.message}")
            }
        }
    }
    
    /**
     * Export configuration (encrypted)
     */
    suspend fun exportConfig(): String {
        return withContext(Dispatchers.IO) {
            val config = getAgentConfig()
            gson.toJson(config)
        }
    }
    
    /**
     * Import configuration
     */
    suspend fun importConfig(configJson: String) {
        withContext(Dispatchers.IO) {
            try {
                val config = gson.fromJson(configJson, AgentConfig::class.java)
                saveAgentConfig(config)
            } catch (e: Exception) {
                throw IllegalArgumentException("Invalid configuration format: ${e.message}")
            }
        }
    }
    
    /**
     * Clear all configuration
     */
    suspend fun clearConfig() {
        withContext(Dispatchers.IO) {
            // Clear encrypted keys
            encryptedPrefs.edit().clear().apply()
            
            // Clear regular config
            regularPrefs.edit().clear().apply()
        }
    }
    
    private fun createDefaultConfig(): AgentConfig {
        return AgentConfig(
            providers = mutableListOf(),
            globalSettings = AgentGlobalSettings()
        )
    }
}

/**
 * Main agent configuration data class
 */
data class AgentConfig(
    val providers: MutableList<AgentProvider> = mutableListOf(),
    val globalSettings: AgentGlobalSettings = AgentGlobalSettings()
)

/**
 * Global agent settings
 */
data class AgentGlobalSettings(
    val defaultProvider: String? = null,
    val enableLogging: Boolean = true,
    val maxHistoryEntries: Int = 1000,
    val autoSaveHistory: Boolean = true,
    val requestTimeout: Long = 30000, // 30 seconds
    val retryAttempts: Int = 3
)

/**
 * Agent provider configuration
 */
data class AgentProvider(
    val providerId: String,
    val displayName: String,
    val apiKeys: MutableList<AgentApiKey> = mutableListOf(),
    val useRotation: Boolean = false,
    val rotationMode: RotationMode = RotationMode.ROUND_ROBIN,
    val maxConsecutiveFailures: Int = 3,
    val backoffMs: Long = 1000,
    val rpmLimit: Int? = null,
    val rpdLimit: Int? = null,
    val baseUrl: String? = null, // For custom providers
    val headers: Map<String, String> = emptyMap(),
    val enabled: Boolean = true
)

/**
 * API key configuration
 */
data class AgentApiKey(
    val id: String = UUID.randomUUID().toString(),
    var key: String,
    val meta: Map<String, String> = emptyMap(),
    val label: String? = null,
    val enabled: Boolean = true,
    val lastUsed: Long = 0,
    val failureCount: Int = 0,
    val suspendedUntil: Long = 0,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Rotation mode for API keys
 */
enum class RotationMode {
    ROUND_ROBIN,
    FAILOVER_PRIORITY
}

/**
 * API key test result
 */
data class AgentKeyTestResult(
    val isValid: Boolean,
    val message: String,
    val responseTime: Long = 0,
    val metadata: Map<String, Any> = emptyMap()
)