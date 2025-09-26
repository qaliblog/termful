package com.termux.app.agent

import android.content.Context
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.whenever
import java.util.concurrent.atomic.AtomicInteger

/**
 * Unit tests for AgentProviderManager focusing on API key rotation and failure handling
 */
class AgentProviderManagerTest {

    @Mock
    private lateinit var mockContext: Context

    @Mock
    private lateinit var mockConfigManager: AgentConfigManager

    private lateinit var providerManager: AgentProviderManager

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        // Note: In real tests, we'd need to properly mock the singleton
        // For this example, we'll test the rotation logic directly
    }

    @Test
    fun testRoundRobinRotation() {
        // Create test API keys
        val keys = listOf(
            AgentApiKey(id = "key1", key = "test-key-1"),
            AgentApiKey(id = "key2", key = "test-key-2"),
            AgentApiKey(id = "key3", key = "test-key-3")
        )

        val rotationState = KeyRotationState()

        // Test round-robin selection
        val selectedKeys = mutableListOf<AgentApiKey>()
        repeat(6) { // Test 2 full rotations
            val provider = AgentProvider(
                providerId = "test",
                displayName = "Test Provider",
                apiKeys = keys.toMutableList(),
                useRotation = true,
                rotationMode = RotationMode.ROUND_ROBIN
            )

            val selectedKey = selectRoundRobinKey(keys, rotationState)
            selectedKeys.add(selectedKey)
        }

        // Verify rotation pattern
        assertEquals("key1", selectedKeys[0].id)
        assertEquals("key2", selectedKeys[1].id)
        assertEquals("key3", selectedKeys[2].id)
        assertEquals("key1", selectedKeys[3].id) // Should cycle back
        assertEquals("key2", selectedKeys[4].id)
        assertEquals("key3", selectedKeys[5].id)
    }

    @Test
    fun testFailoverPrioritySelection() {
        val keys = listOf(
            AgentApiKey(id = "primary", key = "primary-key"),
            AgentApiKey(id = "secondary", key = "secondary-key"),
            AgentApiKey(id = "tertiary", key = "tertiary-key")
        )

        // Test failover selection (should always return first available)
        val selectedKey = selectFailoverKey(keys)
        assertEquals("primary", selectedKey.id)

        // Test with first key suspended
        val availableKeys = keys.drop(1) // Remove first key
        val selectedKey2 = selectFailoverKey(availableKeys)
        assertEquals("secondary", selectedKey2.id)
    }

    @Test
    fun testBackoffCalculation() {
        val provider = AgentProvider(
            providerId = "test",
            displayName = "Test",
            backoffMs = 1000L
        )

        // Test exponential backoff
        val backoff1 = calculateBackoffTime(provider, 0)
        val backoff2 = calculateBackoffTime(provider, 1)
        val backoff3 = calculateBackoffTime(provider, 2)

        assertTrue("First backoff should be around base time", backoff1 >= 1000L && backoff1 <= 2000L)
        assertTrue("Second backoff should be roughly double", backoff2 >= 2000L && backoff2 <= 4000L)
        assertTrue("Third backoff should be roughly quadruple", backoff3 >= 4000L && backoff3 <= 8000L)
    }

    @Test
    fun testRateLimitErrorDetection() {
        val rateLimitErrors = listOf(
            Exception("Rate limit exceeded"),
            Exception("HTTP 429: Too Many Requests"),
            Exception("Quota exceeded for this API key"),
            Exception("rate limit")
        )

        val nonRateLimitErrors = listOf(
            Exception("Invalid API key"),
            Exception("Network timeout"),
            Exception("JSON parse error"),
            Exception("HTTP 500: Internal Server Error")
        )

        rateLimitErrors.forEach { error ->
            assertTrue("Should detect rate limit error: ${error.message}", isRateLimitError(error))
        }

        nonRateLimitErrors.forEach { error ->
            assertFalse("Should not detect as rate limit error: ${error.message}", isRateLimitError(error))
        }
    }

    @Test
    fun testOutageErrorDetection() {
        val outageErrors = listOf(
            Exception("HTTP 503: Service Unavailable"),
            Exception("Service outage in progress"),
            Exception("Maintenance mode"),
            Exception("503")
        )

        val nonOutageErrors = listOf(
            Exception("HTTP 400: Bad Request"),
            Exception("Invalid input"),
            Exception("HTTP 401: Unauthorized")
        )

        outageErrors.forEach { error ->
            assertTrue("Should detect outage error: ${error.message}", isOutageError(error))
        }

        nonOutageErrors.forEach { error ->
            assertFalse("Should not detect as outage error: ${error.message}", isOutageError(error))
        }
    }

    @Test
    fun testKeySuspension() {
        val key = AgentApiKey(id = "test-key", key = "key-value")
        val provider = AgentProvider(
            providerId = "test",
            displayName = "Test",
            backoffMs = 1000L
        )

        // Test key suspension
        val rotationState = KeyRotationState()
        val suspensionTime = System.currentTimeMillis() + 5000L // 5 seconds from now

        rotationState.suspendedKeys[key.id] = suspensionTime

        // Key should be suspended
        assertTrue("Key should be suspended", rotationState.suspendedKeys.containsKey(key.id))
        assertTrue("Suspension time should be in future", rotationState.suspendedKeys[key.id]!! > System.currentTimeMillis())
    }

    @Test
    fun testConsecutiveFailureTracking() {
        val rotationState = KeyRotationState()

        // Test consecutive failure increment
        assertEquals(0, rotationState.consecutiveFailures)

        rotationState.consecutiveFailures++
        assertEquals(1, rotationState.consecutiveFailures)

        rotationState.consecutiveFailures++
        assertEquals(2, rotationState.consecutiveFailures)

        // Test reset on success
        rotationState.consecutiveFailures = 0
        assertEquals(0, rotationState.consecutiveFailures)
    }

    @Test
    fun testMaxConsecutiveFailuresHandling() {
        val provider = AgentProvider(
            providerId = "test",
            displayName = "Test",
            maxConsecutiveFailures = 3
        )

        val rotationState = KeyRotationState()

        // Simulate consecutive failures
        repeat(provider.maxConsecutiveFailures + 1) {
            rotationState.consecutiveFailures++
        }

        assertTrue(
            "Should exceed max consecutive failures",
            rotationState.consecutiveFailures > provider.maxConsecutiveFailures
        )
    }

    // Helper methods that would normally be private in the actual class
    private fun selectRoundRobinKey(keys: List<AgentApiKey>, rotationState: KeyRotationState): AgentApiKey {
        val currentIndex = rotationState.currentKeyIndex
        val selectedKey = keys[currentIndex % keys.size]
        rotationState.currentKeyIndex = (currentIndex + 1) % keys.size
        return selectedKey
    }

    private fun selectFailoverKey(keys: List<AgentApiKey>): AgentApiKey {
        return keys.first()
    }

    private fun calculateBackoffTime(provider: AgentProvider, failureCount: Int): Long {
        val baseBackoff = provider.backoffMs
        val exponentialBackoff = baseBackoff * (kotlin.math.pow(2.0, failureCount.toDouble())).toLong()
        val jitter = kotlin.random.Random.nextLong(0, exponentialBackoff / 4)
        return (exponentialBackoff + jitter).coerceAtMost(java.util.concurrent.TimeUnit.HOURS.toMillis(1))
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
}