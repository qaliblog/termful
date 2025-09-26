package com.termux.app.agent

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*

/**
 * Manages agent interaction history with search, filtering, and persistence
 */
class AgentHistoryManager private constructor(private val context: Context) {
    
    companion object {
        @Volatile
        private var INSTANCE: AgentHistoryManager? = null
        
        private const val PREFS_FILE = "agent_history"
        private const val HISTORY_KEY = "history_entries"
        private const val FAVORITES_KEY = "favorite_entries"
        private const val MAX_HISTORY_ENTRIES = 1000
        
        fun getInstance(context: Context): AgentHistoryManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AgentHistoryManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
    private val gson = Gson()
    
    // Live data for history updates
    private val _historyEntries = MutableLiveData<List<AgentHistoryEntry>>()
    val historyEntries: LiveData<List<AgentHistoryEntry>> = _historyEntries
    
    private val _favoriteEntries = MutableLiveData<List<AgentHistoryEntry>>()
    val favoriteEntries: LiveData<List<AgentHistoryEntry>> = _favoriteEntries
    
    // In-memory cache
    private val historyCache = mutableListOf<AgentHistoryEntry>()
    private val favoritesCache = mutableSetOf<String>()
    
    init {
        loadHistoryFromStorage()
    }
    
    /**
     * Add a new history entry
     */
    suspend fun addHistoryEntry(entry: AgentHistoryEntry) {
        withContext(Dispatchers.IO) {
            // Add to cache
            historyCache.add(0, entry) // Add to beginning for most recent first
            
            // Limit cache size
            while (historyCache.size > MAX_HISTORY_ENTRIES) {
                historyCache.removeAt(historyCache.size - 1)
            }
            
            // Save to storage
            saveHistoryToStorage()
            
            // Update live data
            _historyEntries.postValue(historyCache.toList())
        }
    }
    
    /**
     * Get all history entries
     */
    suspend fun getAllHistory(): List<AgentHistoryEntry> {
        return withContext(Dispatchers.IO) {
            historyCache.toList()
        }
    }
    
    /**
     * Get history entries for a specific session
     */
    suspend fun getSessionHistory(sessionHandle: String): List<AgentHistoryEntry> {
        return withContext(Dispatchers.IO) {
            historyCache.filter { it.sessionHandle == sessionHandle }
        }
    }
    
    /**
     * Search history entries
     */
    suspend fun searchHistory(
        query: String,
        sessionHandle: String? = null,
        successOnly: Boolean = false,
        dateRange: Pair<Long, Long>? = null
    ): List<AgentHistoryEntry> {
        return withContext(Dispatchers.IO) {
            var results = historyCache.asSequence()
            
            // Filter by session if specified
            if (sessionHandle != null) {
                results = results.filter { it.sessionHandle == sessionHandle }
            }
            
            // Filter by success status if specified
            if (successOnly) {
                results = results.filter { it.success }
            }
            
            // Filter by date range if specified
            if (dateRange != null) {
                results = results.filter { it.timestamp in dateRange.first..dateRange.second }
            }
            
            // Filter by query if specified
            if (query.isNotBlank()) {
                val lowercaseQuery = query.lowercase()
                results = results.filter { entry ->
                    entry.query.lowercase().contains(lowercaseQuery) ||
                    entry.response.lowercase().contains(lowercaseQuery) ||
                    entry.workingDirectory.lowercase().contains(lowercaseQuery)
                }
            }
            
            results.toList()
        }
    }
    
    /**
     * Add entry to favorites
     */
    suspend fun addToFavorites(entryId: String) {
        withContext(Dispatchers.IO) {
            favoritesCache.add(entryId)
            saveFavoritesToStorage()
            updateFavoritesLiveData()
        }
    }
    
    /**
     * Remove entry from favorites
     */
    suspend fun removeFromFavorites(entryId: String) {
        withContext(Dispatchers.IO) {
            favoritesCache.remove(entryId)
            saveFavoritesToStorage()
            updateFavoritesLiveData()
        }
    }
    
    /**
     * Check if entry is favorited
     */
    fun isFavorite(entryId: String): Boolean {
        return favoritesCache.contains(entryId)
    }
    
    /**
     * Get all favorite entries
     */
    suspend fun getFavorites(): List<AgentHistoryEntry> {
        return withContext(Dispatchers.IO) {
            historyCache.filter { favoritesCache.contains(it.id) }
        }
    }
    
    /**
     * Delete a history entry
     */
    suspend fun deleteEntry(entryId: String) {
        withContext(Dispatchers.IO) {
            historyCache.removeAll { it.id == entryId }
            favoritesCache.remove(entryId)
            
            saveHistoryToStorage()
            saveFavoritesToStorage()
            
            _historyEntries.postValue(historyCache.toList())
            updateFavoritesLiveData()
        }
    }
    
    /**
     * Clear all history
     */
    suspend fun clearHistory() {
        withContext(Dispatchers.IO) {
            historyCache.clear()
            favoritesCache.clear()
            
            saveHistoryToStorage()
            saveFavoritesToStorage()
            
            _historyEntries.postValue(emptyList())
            _favoriteEntries.postValue(emptyList())
        }
    }
    
    /**
     * Export history as JSON
     */
    suspend fun exportHistory(): String {
        return withContext(Dispatchers.IO) {
            val exportData = AgentHistoryExport(
                entries = historyCache,
                favorites = favoritesCache.toList(),
                exportedAt = System.currentTimeMillis(),
                version = 1
            )
            
            gson.toJson(exportData)
        }
    }
    
    /**
     * Import history from JSON
     */
    suspend fun importHistory(jsonData: String, mergeWithExisting: Boolean = false) {
        withContext(Dispatchers.IO) {
            try {
                val exportData = gson.fromJson(jsonData, AgentHistoryExport::class.java)
                
                if (!mergeWithExisting) {
                    historyCache.clear()
                    favoritesCache.clear()
                }
                
                // Add imported entries
                exportData.entries.forEach { entry ->
                    // Avoid duplicates
                    if (!historyCache.any { it.id == entry.id }) {
                        historyCache.add(entry)
                    }
                }
                
                // Add imported favorites
                favoritesCache.addAll(exportData.favorites)
                
                // Sort by timestamp (most recent first)
                historyCache.sortByDescending { it.timestamp }
                
                // Limit size
                while (historyCache.size > MAX_HISTORY_ENTRIES) {
                    historyCache.removeAt(historyCache.size - 1)
                }
                
                saveHistoryToStorage()
                saveFavoritesToStorage()
                
                _historyEntries.postValue(historyCache.toList())
                updateFavoritesLiveData()
                
            } catch (e: Exception) {
                throw IllegalArgumentException("Invalid history export format: ${e.message}")
            }
        }
    }
    
    /**
     * Get history statistics
     */
    suspend fun getHistoryStats(): AgentHistoryStats {
        return withContext(Dispatchers.IO) {
            val totalEntries = historyCache.size
            val successfulEntries = historyCache.count { it.success }
            val failedEntries = totalEntries - successfulEntries
            val totalFavorites = favoritesCache.size
            
            val sessionsMap = historyCache.groupBy { it.sessionHandle }
            val uniqueSessions = sessionsMap.size
            
            val oldestEntry = historyCache.minByOrNull { it.timestamp }?.timestamp
            val newestEntry = historyCache.maxByOrNull { it.timestamp }?.timestamp
            
            // Calculate average response time (if we track it)
            val entriesWithResponseTime = historyCache.filter { it.responseTimeMs > 0 }
            val avgResponseTime = if (entriesWithResponseTime.isNotEmpty()) {
                entriesWithResponseTime.map { it.responseTimeMs }.average()
            } else 0.0
            
            AgentHistoryStats(
                totalEntries = totalEntries,
                successfulEntries = successfulEntries,
                failedEntries = failedEntries,
                totalFavorites = totalFavorites,
                uniqueSessions = uniqueSessions,
                oldestEntryTimestamp = oldestEntry,
                newestEntryTimestamp = newestEntry,
                averageResponseTimeMs = avgResponseTime
            )
        }
    }
    
    private fun loadHistoryFromStorage() {
        try {
            // Load history entries
            val historyJson = prefs.getString(HISTORY_KEY, null)
            if (historyJson != null) {
                val type = object : TypeToken<List<AgentHistoryEntry>>() {}.type
                val entries: List<AgentHistoryEntry> = gson.fromJson(historyJson, type)
                historyCache.clear()
                historyCache.addAll(entries.sortedByDescending { it.timestamp })
                _historyEntries.value = historyCache.toList()
            }
            
            // Load favorites
            val favoritesJson = prefs.getString(FAVORITES_KEY, null)
            if (favoritesJson != null) {
                val type = object : TypeToken<List<String>>() {}.type
                val favorites: List<String> = gson.fromJson(favoritesJson, type)
                favoritesCache.clear()
                favoritesCache.addAll(favorites)
                updateFavoritesLiveData()
            }
        } catch (e: Exception) {
            // Handle storage corruption
            historyCache.clear()
            favoritesCache.clear()
        }
    }
    
    private fun saveHistoryToStorage() {
        try {
            val historyJson = gson.toJson(historyCache)
            prefs.edit()
                .putString(HISTORY_KEY, historyJson)
                .apply()
        } catch (e: Exception) {
            // Handle storage error
        }
    }
    
    private fun saveFavoritesToStorage() {
        try {
            val favoritesJson = gson.toJson(favoritesCache.toList())
            prefs.edit()
                .putString(FAVORITES_KEY, favoritesJson)
                .apply()
        } catch (e: Exception) {
            // Handle storage error
        }
    }
    
    private fun updateFavoritesLiveData() {
        val favoriteEntries = historyCache.filter { favoritesCache.contains(it.id) }
        _favoriteEntries.value = favoriteEntries
    }
}

/**
 * Agent history entry data class
 */
data class AgentHistoryEntry(
    val id: String,
    val query: String,
    val response: String,
    val timestamp: Long,
    val success: Boolean,
    val sessionHandle: String,
    val workingDirectory: String,
    val provider: String? = null,
    val model: String? = null,
    val responseTimeMs: Long = 0,
    val metadata: Map<String, Any> = emptyMap()
)

/**
 * History export data class
 */
data class AgentHistoryExport(
    val entries: List<AgentHistoryEntry>,
    val favorites: List<String>,
    val exportedAt: Long,
    val version: Int
)

/**
 * History statistics data class
 */
data class AgentHistoryStats(
    val totalEntries: Int,
    val successfulEntries: Int,
    val failedEntries: Int,
    val totalFavorites: Int,
    val uniqueSessions: Int,
    val oldestEntryTimestamp: Long?,
    val newestEntryTimestamp: Long?,
    val averageResponseTimeMs: Double
)