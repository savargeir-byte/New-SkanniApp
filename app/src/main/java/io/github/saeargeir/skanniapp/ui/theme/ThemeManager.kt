package io.github.saeargeir.skanniapp.ui.theme

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Theme preferences key
private val THEME_MODE_KEY = stringPreferencesKey("theme_mode")
private val DYNAMIC_COLORS_KEY = booleanPreferencesKey("dynamic_colors")

// Extension property for DataStore
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "theme_settings")

/**
 * Theme modes available in the app
 */
enum class ThemeMode {
    LIGHT,      // Always light theme
    DARK,       // Always dark theme
    AUTO        // Follow system theme
}

/**
 * Theme configuration data class
 */
data class ThemeConfig(
    val mode: ThemeMode = ThemeMode.AUTO,
    val useDynamicColors: Boolean = true
)

/**
 * Theme Manager for handling theme persistence and state
 */
class ThemeManager(private val context: Context) {
    
    /**
     * Get theme configuration flow
     */
    val themeConfig: Flow<ThemeConfig> = context.dataStore.data.map { preferences ->
        val themeModeString = preferences[THEME_MODE_KEY] ?: ThemeMode.AUTO.name
        val themeMode = try {
            ThemeMode.valueOf(themeModeString)
        } catch (e: IllegalArgumentException) {
            ThemeMode.AUTO
        }
        
        ThemeConfig(
            mode = themeMode,
            useDynamicColors = preferences[DYNAMIC_COLORS_KEY] ?: true
        )
    }
    
    /**
     * Update theme mode
     */
    suspend fun updateThemeMode(mode: ThemeMode) {
        context.dataStore.edit { preferences ->
            preferences[THEME_MODE_KEY] = mode.name
        }
    }
    
    /**
     * Toggle between light and dark modes
     * If currently in AUTO mode, switches to opposite of current system theme
     */
    suspend fun toggleTheme(isCurrentlyDark: Boolean) {
        val newMode = when {
            isCurrentlyDark -> ThemeMode.LIGHT
            else -> ThemeMode.DARK
        }
        updateThemeMode(newMode)
    }
    
    /**
     * Set to auto (follow system) mode
     */
    suspend fun setAutoTheme() {
        updateThemeMode(ThemeMode.AUTO)
    }
    
    /**
     * Update dynamic colors preference
     */
    suspend fun updateDynamicColors(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[DYNAMIC_COLORS_KEY] = enabled
        }
    }
    
    /**
     * Reset to default theme settings
     */
    suspend fun resetToDefaults() {
        context.dataStore.edit { preferences ->
            preferences.clear()
        }
    }
}

/**
 * Determine if dark theme should be used based on configuration
 */
@Composable
fun shouldUseDarkTheme(themeConfig: ThemeConfig): Boolean {
    return when (themeConfig.mode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.AUTO -> isSystemInDarkTheme()
    }
}

/**
 * Get user-friendly theme mode name in Icelandic
 */
fun ThemeMode.getDisplayName(): String {
    return when (this) {
        ThemeMode.LIGHT -> "Ljóst þema"
        ThemeMode.DARK -> "Dökkt þema"
        ThemeMode.AUTO -> "Sjálfvirkt (fylgir kerfinu)"
    }
}

/**
 * Get next theme mode for cycling through options
 */
fun ThemeMode.getNext(): ThemeMode {
    return when (this) {
        ThemeMode.LIGHT -> ThemeMode.DARK
        ThemeMode.DARK -> ThemeMode.AUTO
        ThemeMode.AUTO -> ThemeMode.LIGHT
    }
}