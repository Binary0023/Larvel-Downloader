package com.example.larveldownloader.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsDataStore(private val context: Context) {
    
    companion object {
        val BUFFER_SIZE = intPreferencesKey("buffer_size")
        val MAX_PARALLEL_DOWNLOADS = intPreferencesKey("max_parallel_downloads")
        val EMBED_THUMBNAIL = booleanPreferencesKey("embed_thumbnail")
        val DARK_MODE = stringPreferencesKey("dark_mode") // "system", "light", "dark"
        
        const val DEFAULT_BUFFER_SIZE = 32768
        const val DEFAULT_MAX_PARALLEL = 3
        const val DEFAULT_EMBED_THUMBNAIL = true
        const val DEFAULT_DARK_MODE = "system"
    }
    
    val bufferSize: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[BUFFER_SIZE] ?: DEFAULT_BUFFER_SIZE
    }
    
    val maxParallelDownloads: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[MAX_PARALLEL_DOWNLOADS] ?: DEFAULT_MAX_PARALLEL
    }
    
    val embedThumbnail: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[EMBED_THUMBNAIL] ?: DEFAULT_EMBED_THUMBNAIL
    }
    
    val darkMode: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[DARK_MODE] ?: DEFAULT_DARK_MODE
    }
    
    suspend fun setBufferSize(size: Int) {
        context.dataStore.edit { prefs ->
            prefs[BUFFER_SIZE] = size
        }
    }
    
    suspend fun setMaxParallelDownloads(count: Int) {
        context.dataStore.edit { prefs ->
            prefs[MAX_PARALLEL_DOWNLOADS] = count
        }
    }
    
    suspend fun setEmbedThumbnail(embed: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[EMBED_THUMBNAIL] = embed
        }
    }
    
    suspend fun setDarkMode(mode: String) {
        context.dataStore.edit { prefs ->
            prefs[DARK_MODE] = mode
        }
    }
}
