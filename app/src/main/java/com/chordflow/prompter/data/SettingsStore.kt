package com.chordflow.prompter.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "chordflow_settings")

/** Teleprompter and detector preferences that persist between services. */
class SettingsStore(private val context: Context) {

    data class Settings(
        val sensitivity: Float = 0.35f,
        val confidence: Float = 0.62f,
        val fontScale: Float = 1.0f,
        val autoScrollEnabled: Boolean = true,
        val debounceMs: Int = 450,
        val matchStrictness: String = "FAMILY",
        val lastSetlist: String = Song.DEFAULT_SETLIST,
        val customCatalogUrl: String = ""
    )

    private object Keys {
        val SENSITIVITY = floatPreferencesKey("sensitivity")
        val CONFIDENCE = floatPreferencesKey("confidence")
        val FONT_SCALE = floatPreferencesKey("font_scale")
        val AUTO_SCROLL = booleanPreferencesKey("auto_scroll")
        val DEBOUNCE = intPreferencesKey("debounce_ms")
        val STRICTNESS = stringPreferencesKey("strictness")
        val LAST_SETLIST = stringPreferencesKey("last_setlist")
        val CATALOG_URL = stringPreferencesKey("catalog_url")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { p -> p.toSettings() }

    private fun Preferences.toSettings() = Settings(
        sensitivity = this[Keys.SENSITIVITY] ?: 0.35f,
        confidence = this[Keys.CONFIDENCE] ?: 0.62f,
        fontScale = this[Keys.FONT_SCALE] ?: 1.0f,
        autoScrollEnabled = this[Keys.AUTO_SCROLL] ?: true,
        debounceMs = this[Keys.DEBOUNCE] ?: 450,
        matchStrictness = this[Keys.STRICTNESS] ?: "FAMILY",
        lastSetlist = this[Keys.LAST_SETLIST] ?: Song.DEFAULT_SETLIST,
        customCatalogUrl = this[Keys.CATALOG_URL] ?: ""
    )

    suspend fun setSensitivity(value: Float) = edit { it[Keys.SENSITIVITY] = value }
    suspend fun setConfidence(value: Float) = edit { it[Keys.CONFIDENCE] = value }
    suspend fun setFontScale(value: Float) = edit { it[Keys.FONT_SCALE] = value }
    suspend fun setAutoScroll(value: Boolean) = edit { it[Keys.AUTO_SCROLL] = value }
    suspend fun setDebounce(value: Int) = edit { it[Keys.DEBOUNCE] = value }
    suspend fun setStrictness(value: String) = edit { it[Keys.STRICTNESS] = value }
    suspend fun setLastSetlist(value: String) = edit { it[Keys.LAST_SETLIST] = value }
    suspend fun setCatalogUrl(value: String) = edit { it[Keys.CATALOG_URL] = value }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit { block(it) }
    }
}
