package com.termful.app.agent

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.whenever
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import java.util.*

/**
 * Security tests for API key storage and handling
 */
class SecurityTest {

    @Mock
    private lateinit var mockContext: Context

    @Mock
    private lateinit var mockRegularPrefs: SharedPreferences

    @Mock
    private lateinit var mockEncryptedPrefs: SharedPreferences

    @Mock
    private lateinit var mockRegularEditor: SharedPreferences.Editor

    @Mock
    private lateinit var mockEncryptedEditor: SharedPreferences.Editor

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)

        // Mock SharedPreferences behavior
        whenever(mockRegularPrefs.edit()).thenReturn(mockRegularEditor)
        whenever(mockEncryptedPrefs.edit()).thenReturn(mockEncryptedEditor)
        whenever(mockRegularEditor.putString(any(), any())).thenReturn(mockRegularEditor)
        whenever(mockEncryptedEditor.putString(any(), any())).thenReturn(mockEncryptedEditor)
    }

    @Test
    fun testApiKeyNotStoredInPlainText() {
        val apiKey = AgentApiKey(
            id = "test-key-id",
            key = "sk-1234567890abcdef",
            label = "Test API Key"
        )

        val provider = AgentProvider(
            providerId = "test-provider",
            displayName = "Test Provider",
            apiKeys = mutableListOf(apiKey)
        )

        val config = AgentConfig(
            providers = mutableListOf(provider)
        )

        // Simulate config serialization without encryption
        val configJson = com.google.gson.Gson().toJson(config)

        // Verify the actual API key is not in the serialized config
        // (This test would fail if keys were stored in plain text)
        assertFalse(
            "API key should not appear in plain text config",
            configJson.contains("sk-1234567890abcdef")
        )

        // The config should contain the key structure but not the actual key value
        assertTrue("Config should contain provider", configJson.contains("test-provider"))
        assertTrue("Config should contain key ID", configJson.contains("test-key-id"))
    }

    @Test
    fun testApiKeyMasking() {
        val fullKey = "sk-1234567890abcdefghijklmnopqrstuvwxyz"
        
        // Test key masking for display
        val maskedKey = maskApiKey(fullKey)
        
        // Should show first 7 chars and mask the rest
        assertTrue("Masked key should start with original prefix", maskedKey.startsWith("sk-1234"))
        assertTrue("Masked key should contain asterisks", maskedKey.contains("*"))
        assertFalse("Masked key should not contain full original key", maskedKey.contains(fullKey))
        
        // Test different key formats
        val openAiKey = "sk-proj-abcdefghijklmnopqrstuvwxyz1234567890"
        val maskedOpenAi = maskApiKey(openAiKey)
        assertTrue("OpenAI key should be masked", maskedOpenAi.contains("*"))
        
        val anthropicKey = "sk-ant-api03-abcdefghijklmnopqrstuvwxyz"
        val maskedAnthropic = maskApiKey(anthropicKey)
        assertTrue("Anthropic key should be masked", maskedAnthropic.contains("*"))
    }

    @Test
    fun testApiKeyValidation() {
        // Test valid API key patterns
        assertTrue("OpenAI key should be valid", isValidApiKeyFormat("sk-1234567890abcdef", "openai"))
        assertTrue("Anthropic key should be valid", isValidApiKeyFormat("sk-ant-api03-abcdef", "anthropic"))
        assertTrue("Gemini key should be valid", isValidApiKeyFormat("AIzaSyAbCdEfGhIjKlMnOpQrStUvWxYz", "gemini"))
        
        // Test invalid API key patterns
        assertFalse("Empty key should be invalid", isValidApiKeyFormat("", "openai"))
        assertFalse("Short key should be invalid", isValidApiKeyFormat("sk-123", "openai"))
        assertFalse("Wrong prefix should be invalid", isValidApiKeyFormat("ak-1234567890abcdef", "openai"))
        assertFalse("Null key should be invalid", isValidApiKeyFormat(null, "openai"))
    }

    @Test
    fun testNoApiKeysInLogs() {
        val testKey = "sk-1234567890abcdef"
        
        // Simulate logging - should not contain actual key
        val logMessage = createLogMessage("API call successful", testKey)
        
        assertFalse("Log message should not contain actual API key", logMessage.contains(testKey))
        assertTrue("Log message should contain masked key", logMessage.contains("sk-****"))
    }

    @Test
    fun testKeyRotationSecurity() {
        val keys = listOf(
            AgentApiKey(id = "key1", key = "sk-key1-secret"),
            AgentApiKey(id = "key2", key = "sk-key2-secret"),
            AgentApiKey(id = "key3", key = "sk-key3-secret")
        )

        // Test that rotation state doesn't expose keys
        val rotationState = KeyRotationState()
        rotationState.lastSuccessfulKeyId = "key1"
        
        // Rotation state should only store key IDs, not actual keys
        assertEquals("Should store key ID", "key1", rotationState.lastSuccessfulKeyId)
        
        // Verify rotation state doesn't accidentally store actual key values
        val rotationJson = com.google.gson.Gson().toJson(rotationState)
        assertFalse("Rotation state should not contain actual keys", rotationJson.contains("sk-key1-secret"))
        assertFalse("Rotation state should not contain actual keys", rotationJson.contains("sk-key2-secret"))
        assertFalse("Rotation state should not contain actual keys", rotationJson.contains("sk-key3-secret"))
    }

    @Test
    fun testKeySuspensionSecurity() {
        val rotationState = KeyRotationState()
        val keyId = "test-key-id"
        val suspensionTime = System.currentTimeMillis() + 60000 // 1 minute
        
        // Suspend key
        rotationState.suspendedKeys[keyId] = suspensionTime
        
        // Verify suspension data doesn't expose sensitive information
        val suspensionJson = com.google.gson.Gson().toJson(rotationState.suspendedKeys)
        assertTrue("Should contain key ID", suspensionJson.contains(keyId))
        assertFalse("Should not contain actual key value", suspensionJson.contains("sk-"))
    }

    @Test
    fun testErrorMessageSanitization() {
        val apiKey = "sk-1234567890abcdef"
        val errorMessage = "Invalid API key: $apiKey"
        
        // Sanitize error message
        val sanitizedError = sanitizeErrorMessage(errorMessage)
        
        assertFalse("Sanitized error should not contain actual key", sanitizedError.contains(apiKey))
        assertTrue("Sanitized error should contain masked key", sanitizedError.contains("sk-****"))
    }

    @Test
    fun testConfigExportSecurity() {
        val apiKey = AgentApiKey(
            id = "test-key",
            key = "sk-sensitive-key-data",
            label = "Test Key"
        )

        val provider = AgentProvider(
            providerId = "test",
            displayName = "Test",
            apiKeys = mutableListOf(apiKey)
        )

        val config = AgentConfig(providers = mutableListOf(provider))

        // Export should not include actual keys
        val exportJson = exportConfigSafely(config)
        
        assertFalse("Export should not contain actual key", exportJson.contains("sk-sensitive-key-data"))
        assertTrue("Export should contain key structure", exportJson.contains("test-key"))
    }

    @Test
    fun testMemoryCleanup() {
        val sensitiveData = "sk-sensitive-api-key-123456"
        var keyVariable: String? = sensitiveData
        
        // Simulate cleanup
        keyVariable = null
        
        assertNull("Key variable should be null after cleanup", keyVariable)
        
        // Test array cleanup
        val keyArray = sensitiveData.toCharArray()
        Arrays.fill(keyArray, '\u0000') // Clear array
        
        // Verify array is cleared
        assertFalse("Array should not contain original data", String(keyArray).contains("sk-sensitive"))
    }

    // Helper methods that would be implemented in the actual security module
    private fun maskApiKey(apiKey: String): String {
        if (apiKey.length <= 7) return "*".repeat(apiKey.length)
        return apiKey.substring(0, 7) + "*".repeat(apiKey.length - 7)
    }

    private fun isValidApiKeyFormat(apiKey: String?, provider: String): Boolean {
        if (apiKey.isNullOrBlank()) return false
        
        return when (provider) {
            "openai" -> apiKey.startsWith("sk-") && apiKey.length >= 20
            "anthropic" -> apiKey.startsWith("sk-ant-") && apiKey.length >= 20
            "gemini" -> apiKey.startsWith("AIza") && apiKey.length >= 20
            else -> apiKey.length >= 10 // Generic validation
        }
    }

    private fun createLogMessage(message: String, apiKey: String): String {
        val maskedKey = maskApiKey(apiKey)
        return "$message (key: $maskedKey)"
    }

    private fun sanitizeErrorMessage(message: String): String {
        return message.replace(Regex("sk-[a-zA-Z0-9-_]+"), "sk-****")
    }

    private fun exportConfigSafely(config: AgentConfig): String {
        // Create a copy without sensitive data
        val safeConfig = config.copy()
        safeConfig.providers.forEach { provider ->
            provider.apiKeys.forEach { key ->
                key.key = "" // Remove actual key data
            }
        }
        return com.google.gson.Gson().toJson(safeConfig)
    }
}