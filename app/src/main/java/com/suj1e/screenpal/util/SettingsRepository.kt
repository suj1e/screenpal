package com.suj1e.screenpal.util

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: androidx.datastore.core.DataStore<Preferences> by preferencesDataStore(name = "settings")

data class UserSettings(
    val floatingWindowEnabled: Boolean = false,
    val ttsEngine: String = "PIPER",
    val ttsRate: Float = 1.0f,
    val ttsPitch: Float = 1.0f,
    val ocrMode: String = "HYBRID",
    val cloudApiKey: String = "",
    val translationEnabled: Boolean = true
)

class SettingsRepository(private val context: Context) {

    val appContext: Context = context.applicationContext

    val userSettings: Flow<UserSettings> = context.dataStore.data.map { prefs ->
        UserSettings(
            floatingWindowEnabled = prefs[KEY_FLOATING_WINDOW] ?: false,
            ttsEngine = prefs[KEY_TTS_ENGINE] ?: "PIPER",
            ttsRate = prefs[KEY_TTS_RATE] ?: 1.0f,
            ttsPitch = prefs[KEY_TTS_PITCH] ?: 1.0f,
            ocrMode = prefs[KEY_OCR_MODE] ?: "HYBRID",
            cloudApiKey = prefs[KEY_CLOUD_API_KEY] ?: "",
            translationEnabled = prefs[KEY_TRANSLATION_ENABLED] ?: true
        )
    }

    suspend fun update(transform: UserSettings.() -> UserSettings) {
        context.dataStore.edit { prefs ->
            val current = UserSettings(
                floatingWindowEnabled = prefs[KEY_FLOATING_WINDOW] ?: false,
                ttsEngine = prefs[KEY_TTS_ENGINE] ?: "PIPER",
                ttsRate = prefs[KEY_TTS_RATE] ?: 1.0f,
                ttsPitch = prefs[KEY_TTS_PITCH] ?: 1.0f,
                ocrMode = prefs[KEY_OCR_MODE] ?: "HYBRID",
                cloudApiKey = prefs[KEY_CLOUD_API_KEY] ?: "",
                translationEnabled = prefs[KEY_TRANSLATION_ENABLED] ?: true
            )
            val updated = current.transform()
            prefs[KEY_FLOATING_WINDOW] = updated.floatingWindowEnabled
            prefs[KEY_TTS_ENGINE] = updated.ttsEngine
            prefs[KEY_TTS_RATE] = updated.ttsRate
            prefs[KEY_TTS_PITCH] = updated.ttsPitch
            prefs[KEY_OCR_MODE] = updated.ocrMode
            prefs[KEY_CLOUD_API_KEY] = updated.cloudApiKey
            prefs[KEY_TRANSLATION_ENABLED] = updated.translationEnabled
        }
    }

    companion object {
        private val KEY_FLOATING_WINDOW = booleanPreferencesKey("floating_window_enabled")
        private val KEY_TTS_ENGINE = stringPreferencesKey("tts_engine")
        private val KEY_TTS_RATE = floatPreferencesKey("tts_rate")
        private val KEY_TTS_PITCH = floatPreferencesKey("tts_pitch")
        private val KEY_OCR_MODE = stringPreferencesKey("ocr_mode")
        private val KEY_CLOUD_API_KEY = stringPreferencesKey("cloud_api_key")
        private val KEY_TRANSLATION_ENABLED = booleanPreferencesKey("translationEnabled")
    }
}
