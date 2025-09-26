package com.termux.app.agent

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import com.google.gson.Gson
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.math.pow
import kotlin.random.Random

/**
 * Manages AI provider clients and handles API key rotation
 */
class AgentProviderManager private constructor(private val context: Context) {
    
    companion object {
        @Volatile
        private var INSTANCE: AgentProviderManager? = null
        
        fun getInstance(context: Context): AgentProviderManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AgentProviderManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private val gson = Gson()
    private val providerClients = ConcurrentHashMap<String, AgentProviderClient>()
    private val keyRotationState = ConcurrentHashMap<String, KeyRotationState>()
    
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    
    init {
        initializeProviders()
    }
    
    private fun initializeProviders() {
        // Initialize built-in providers
        providerClients["gemini"] = GeminiProviderClient()
        providerClients["openai"] = OpenAIProviderClient()
        providerClients["anthropic"] = AnthropicProviderClient()
    }
    
    /**
     * Get provider client by ID
     */
    fun getProviderClient(providerId: String): AgentProviderClient? {
        return providerClients[providerId]
    }
    
    /**
     * Process message using configured providers with rotation
     */
    suspend fun processMessage(
        message: String,
        workingDirectory: String,
        configManager: AgentConfigManager
    ): AgentProviderResponse {
        return withContext(Dispatchers.IO) {
            val config = configManager.getAgentConfig()
            val enabledProviders = config.providers.filter { it.enabled && it.apiKeys.isNotEmpty() }
            
            if (enabledProviders.isEmpty()) {
                throw IllegalStateException("No enabled providers with API keys configured")
            }
            
            // Try providers in order until one succeeds
            var lastException: Exception? = null
            
            for (provider in enabledProviders) {
                try {
                    val response = processWithProvider(provider, message, workingDirectory)
                    
                    // Log successful request
                    logProviderUsage(provider.providerId, true, null)
                    
                    return@withContext response
                } catch (e: Exception) {
                    lastException = e
                    
                    // Log failed request
                    logProviderUsage(provider.providerId, false, e.message)
                    
                    // Check if this is a rate limit or outage error
                    if (isRateLimitError(e) || isOutageError(e)) {
                        handleProviderError(provider, e)
                    }
                    
                    // Continue to next provider
                    continue
                }
            }
            
            // All providers failed
            throw lastException ?: Exception("All providers failed")
        }
    }
    
    private suspend fun processWithProvider(
        provider: AgentProvider,
        message: String,
        workingDirectory: String
    ): AgentProviderResponse {
        val providerClient = providerClients[provider.providerId]
            ?: throw IllegalArgumentException("Provider client not found: ${provider.providerId}")
        
        if (provider.useRotation && provider.apiKeys.size > 1) {
            return processWithRotation(provider, providerClient, message, workingDirectory)
        } else {
            // Use first available key
            val apiKey = provider.apiKeys.firstOrNull { it.enabled && !isKeySuspended(it) }
                ?: throw IllegalStateException("No available API keys for provider: ${provider.providerId}")
            
            return providerClient.processMessage(apiKey.key, message, workingDirectory, provider)
        }
    }
    
    private suspend fun processWithRotation(
        provider: AgentProvider,
        providerClient: AgentProviderClient,
        message: String,
        workingDirectory: String
    ): AgentProviderResponse {
        val rotationState = getOrCreateRotationState(provider.providerId)
        val availableKeys = provider.apiKeys.filter { it.enabled && !isKeySuspended(it) }
        
        if (availableKeys.isEmpty()) {
            throw IllegalStateException("No available API keys for provider: ${provider.providerId}")
        }
        
        // Select key based on rotation mode
        val selectedKey = when (provider.rotationMode) {
            RotationMode.ROUND_ROBIN -> selectRoundRobinKey(availableKeys, rotationState)
            RotationMode.FAILOVER_PRIORITY -> selectFailoverKey(availableKeys)
        }
        
        try {
            val response = providerClient.processMessage(selectedKey.key, message, workingDirectory, provider)
            
            // Update rotation state on success
            rotationState.lastSuccessfulKeyId = selectedKey.id
            rotationState.consecutiveFailures = 0
            
            return response
        } catch (e: Exception) {
            // Handle key failure
            handleKeyFailure(provider, selectedKey, e)
            
            // Try next key if available
            val remainingKeys = availableKeys.filter { it.id != selectedKey.id }
            if (remainingKeys.isNotEmpty()) {
                return processWithRotation(
                    provider.copy(apiKeys = remainingKeys.toMutableList()),
                    providerClient,
                    message,
                    workingDirectory
                )
            } else {
                throw e
            }
        }
    }
    
    private fun selectRoundRobinKey(keys: List<AgentApiKey>, rotationState: KeyRotationState): AgentApiKey {
        val currentIndex = rotationState.currentKeyIndex
        val selectedKey = keys[currentIndex % keys.size]
        
        // Update rotation state
        rotationState.currentKeyIndex = (currentIndex + 1) % keys.size
        
        return selectedKey
    }
    
    private fun selectFailoverKey(keys: List<AgentApiKey>): AgentApiKey {
        // Return first available key (priority order)
        return keys.first()
    }
    
    private fun handleKeyFailure(provider: AgentProvider, apiKey: AgentApiKey, exception: Exception) {
        if (isRateLimitError(exception) || isOutageError(exception)) {
            // Suspend the key temporarily
            suspendKey(provider, apiKey, exception)
        }
        
        // Increment failure count
        val rotationState = getOrCreateRotationState(provider.providerId)
        rotationState.consecutiveFailures++
        
        // Log the failure
        logKeyFailure(provider.providerId, apiKey.id, exception.message ?: "Unknown error")
    }
    
    private fun suspendKey(provider: AgentProvider, apiKey: AgentApiKey, exception: Exception) {
        val suspensionTime = calculateBackoffTime(provider, apiKey.failureCount)
        val suspendedUntil = System.currentTimeMillis() + suspensionTime
        
        // Update key suspension (this would need to be persisted in the config)
        // For now, we'll use in-memory tracking
        val rotationState = getOrCreateRotationState(provider.providerId)
        rotationState.suspendedKeys[apiKey.id] = suspendedUntil
        
        logKeyFailure(
            provider.providerId,
            apiKey.id,
            "Key suspended until ${java.util.Date(suspendedUntil)} due to: ${exception.message}"
        )
    }
    
    private fun calculateBackoffTime(provider: AgentProvider, failureCount: Int): Long {
        // Exponential backoff with jitter
        val baseBackoff = provider.backoffMs
        val exponentialBackoff = baseBackoff * (2.0.pow(failureCount.coerceAtMost(10))).toLong()
        val jitter = Random.nextLong(0, exponentialBackoff / 4)
        
        return (exponentialBackoff + jitter).coerceAtMost(TimeUnit.HOURS.toMillis(1)) // Max 1 hour
    }
    
    private fun isKeySuspended(apiKey: AgentApiKey): Boolean {
        // Check if key is currently suspended
        return apiKey.suspendedUntil > System.currentTimeMillis()
    }
    
    private fun isRateLimitError(exception: Exception): Boolean {
        val message = exception.message?.lowercase() ?: ""
        return message.contains("rate limit") ||
                message.contains("429") ||
                message.contains("quota exceeded") ||
                message.contains("too many requests")
    }
    
    private fun isOutageError(exception: Exception): Boolean {
        val message = exception.message?.lowercase() ?: ""
        return message.contains("503") ||
                message.contains("service unavailable") ||
                message.contains("outage") ||
                message.contains("maintenance")
    }
    
    private fun handleProviderError(provider: AgentProvider, exception: Exception) {
        // Handle provider-level errors (like outages)
        logProviderUsage(provider.providerId, false, "Provider error: ${exception.message}")
    }
    
    private fun getOrCreateRotationState(providerId: String): KeyRotationState {
        return keyRotationState.getOrPut(providerId) { KeyRotationState() }
    }
    
    private fun logProviderUsage(providerId: String, success: Boolean, error: String?) {
        // TODO: Implement logging to persistent storage
        val timestamp = System.currentTimeMillis()
        val status = if (success) "SUCCESS" else "FAILURE"
        val errorMsg = error?.let { " - $it" } ?: ""
        
        println("[$timestamp] Provider $providerId: $status$errorMsg")
    }
    
    private fun logKeyFailure(providerId: String, keyId: String, error: String) {
        // TODO: Implement key failure logging
        val timestamp = System.currentTimeMillis()
        println("[$timestamp] Key failure - Provider: $providerId, Key: $keyId, Error: $error")
    }
}

/**
 * Tracks rotation state for a provider
 */
data class KeyRotationState(
    var currentKeyIndex: Int = 0,
    var lastSuccessfulKeyId: String? = null,
    var consecutiveFailures: Int = 0,
    val suspendedKeys: MutableMap<String, Long> = mutableMapOf() // keyId -> suspendedUntil timestamp
)

/**
 * Abstract base class for provider clients
 */
abstract class AgentProviderClient {
    abstract suspend fun processMessage(
        apiKey: String,
        message: String,
        workingDirectory: String,
        provider: AgentProvider
    ): AgentProviderResponse
    
    abstract suspend fun testApiKey(apiKey: String): AgentKeyTestResult
    
    protected fun createHttpRequest(
        url: String,
        method: String = "POST",
        headers: Map<String, String> = emptyMap(),
        body: String? = null
    ): Request {
        val requestBuilder = Request.Builder().url(url)
        
        // Add headers
        headers.forEach { (key, value) ->
            requestBuilder.addHeader(key, value)
        }
        
        // Add body for POST requests
        if (method == "POST" && body != null) {
            val mediaType = "application/json; charset=utf-8".toMediaType()
            requestBuilder.post(body.toRequestBody(mediaType))
        }
        
        return requestBuilder.build()
    }
    
    protected suspend fun executeHttpRequest(request: Request): String {
        return withContext(Dispatchers.IO) {
            val client = OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build()
            
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("HTTP ${response.code}: ${response.message}")
                }
                
                response.body?.string() ?: throw IOException("Empty response body")
            }
        }
    }
}

/**
 * Google Gemini provider client
 */
class GeminiProviderClient : AgentProviderClient() {
    
    override suspend fun processMessage(
        apiKey: String,
        message: String,
        workingDirectory: String,
        provider: AgentProvider
    ): AgentProviderResponse {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-pro:generateContent"
        
        val requestBody = mapOf(
            "contents" to listOf(
                mapOf(
                    "parts" to listOf(
                        mapOf("text" to message)
                    )
                )
            )
        )
        
        val headers = mapOf(
            "Authorization" to "Bearer $apiKey",
            "Content-Type" to "application/json"
        )
        
        val request = createHttpRequest(
            url = "$url?key=$apiKey",
            headers = headers,
            body = Gson().toJson(requestBody)
        )
        
        val responseJson = executeHttpRequest(request)
        val response = Gson().fromJson(responseJson, Map::class.java)
        
        // Parse Gemini response
        val candidates = response["candidates"] as? List<*>
        val firstCandidate = candidates?.firstOrNull() as? Map<*, *>
        val content = firstCandidate?.get("content") as? Map<*, *>
        val parts = content?.get("parts") as? List<*>
        val firstPart = parts?.firstOrNull() as? Map<*, *>
        val text = firstPart?.get("text") as? String
        
        return AgentProviderResponse(
            content = text ?: "No response content",
            provider = "gemini",
            model = "gemini-pro",
            timestamp = System.currentTimeMillis()
        )
    }
    
    override suspend fun testApiKey(apiKey: String): AgentKeyTestResult {
        return try {
            processMessage(apiKey, "Hello", "/", AgentProvider("gemini", "Gemini"))
            AgentKeyTestResult(true, "Key is valid")
        } catch (e: Exception) {
            AgentKeyTestResult(false, "Key test failed: ${e.message}")
        }
    }
}

/**
 * OpenAI provider client
 */
class OpenAIProviderClient : AgentProviderClient() {
    
    override suspend fun processMessage(
        apiKey: String,
        message: String,
        workingDirectory: String,
        provider: AgentProvider
    ): AgentProviderResponse {
        val url = "https://api.openai.com/v1/chat/completions"
        
        val requestBody = mapOf(
            "model" to "gpt-3.5-turbo",
            "messages" to listOf(
                mapOf(
                    "role" to "user",
                    "content" to message
                )
            ),
            "max_tokens" to 1000
        )
        
        val headers = mapOf(
            "Authorization" to "Bearer $apiKey",
            "Content-Type" to "application/json"
        )
        
        val request = createHttpRequest(
            url = url,
            headers = headers,
            body = Gson().toJson(requestBody)
        )
        
        val responseJson = executeHttpRequest(request)
        val response = Gson().fromJson(responseJson, Map::class.java)
        
        // Parse OpenAI response
        val choices = response["choices"] as? List<*>
        val firstChoice = choices?.firstOrNull() as? Map<*, *>
        val message_response = firstChoice?.get("message") as? Map<*, *>
        val content = message_response?.get("content") as? String
        
        return AgentProviderResponse(
            content = content ?: "No response content",
            provider = "openai",
            model = "gpt-3.5-turbo",
            timestamp = System.currentTimeMillis()
        )
    }
    
    override suspend fun testApiKey(apiKey: String): AgentKeyTestResult {
        return try {
            processMessage(apiKey, "Hello", "/", AgentProvider("openai", "OpenAI"))
            AgentKeyTestResult(true, "Key is valid")
        } catch (e: Exception) {
            AgentKeyTestResult(false, "Key test failed: ${e.message}")
        }
    }
}

/**
 * Anthropic Claude provider client
 */
class AnthropicProviderClient : AgentProviderClient() {
    
    override suspend fun processMessage(
        apiKey: String,
        message: String,
        workingDirectory: String,
        provider: AgentProvider
    ): AgentProviderResponse {
        val url = "https://api.anthropic.com/v1/messages"
        
        val requestBody = mapOf(
            "model" to "claude-3-sonnet-20240229",
            "max_tokens" to 1000,
            "messages" to listOf(
                mapOf(
                    "role" to "user",
                    "content" to message
                )
            )
        )
        
        val headers = mapOf(
            "x-api-key" to apiKey,
            "Content-Type" to "application/json",
            "anthropic-version" to "2023-06-01"
        )
        
        val request = createHttpRequest(
            url = url,
            headers = headers,
            body = Gson().toJson(requestBody)
        )
        
        val responseJson = executeHttpRequest(request)
        val response = Gson().fromJson(responseJson, Map::class.java)
        
        // Parse Anthropic response
        val content = response["content"] as? List<*>
        val firstContent = content?.firstOrNull() as? Map<*, *>
        val text = firstContent?.get("text") as? String
        
        return AgentProviderResponse(
            content = text ?: "No response content",
            provider = "anthropic",
            model = "claude-3-sonnet",
            timestamp = System.currentTimeMillis()
        )
    }
    
    override suspend fun testApiKey(apiKey: String): AgentKeyTestResult {
        return try {
            processMessage(apiKey, "Hello", "/", AgentProvider("anthropic", "Anthropic"))
            AgentKeyTestResult(true, "Key is valid")
        } catch (e: Exception) {
            AgentKeyTestResult(false, "Key test failed: ${e.message}")
        }
    }
}

/**
 * Response from AI provider
 */
data class AgentProviderResponse(
    val content: String,
    val provider: String,
    val model: String,
    val timestamp: Long,
    val metadata: Map<String, Any> = emptyMap()
)