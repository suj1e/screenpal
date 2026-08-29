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

/**
 * StepFun-only 键面（2026-08-29-stepfun-only）：在线能力唯一凭据 = stepfunApiKey /
 * stepfunVoice（TTS / 云 OCR / 转译共用一把 Key）。旧的双服务商键
 * （服务商选择键、方舟键、火山语音三键）已删除；DataStore 里遗留的旧键条目
 * 不迁移，读侧不再映射即视为删除（DataStore 容忍未知键）。
 */
data class UserSettings(
    val floatingWindowEnabled: Boolean = false,
    val ttsEngine: String = "PIPER",
    val ttsRate: Float = 1.0f,
    val ttsPitch: Float = 1.0f,
    val ocrMode: String = "HYBRID",
    val translationEnabled: Boolean = true,
    val stepfunApiKey: String = "",
    val stepfunVoice: String = "tianmeinvsheng"
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
            translationEnabled = prefs[KEY_TRANSLATION_ENABLED] ?: true,
            stepfunApiKey = prefs[KEY_STEPFUN_API_KEY] ?: "",
            stepfunVoice = prefs[KEY_STEPFUN_VOICE] ?: "tianmeinvsheng"
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
                translationEnabled = prefs[KEY_TRANSLATION_ENABLED] ?: true,
                stepfunApiKey = prefs[KEY_STEPFUN_API_KEY] ?: "",
                stepfunVoice = prefs[KEY_STEPFUN_VOICE] ?: "tianmeinvsheng"
            )
            val updated = current.transform()
            prefs[KEY_FLOATING_WINDOW] = updated.floatingWindowEnabled
            prefs[KEY_TTS_ENGINE] = updated.ttsEngine
            prefs[KEY_TTS_RATE] = updated.ttsRate
            prefs[KEY_TTS_PITCH] = updated.ttsPitch
            prefs[KEY_OCR_MODE] = updated.ocrMode
            prefs[KEY_TRANSLATION_ENABLED] = updated.translationEnabled
            prefs[KEY_STEPFUN_API_KEY] = updated.stepfunApiKey
            prefs[KEY_STEPFUN_VOICE] = updated.stepfunVoice
        }
    }

    companion object {
        private val KEY_FLOATING_WINDOW = booleanPreferencesKey("floating_window_enabled")
        private val KEY_TTS_ENGINE = stringPreferencesKey("tts_engine")
        private val KEY_TTS_RATE = floatPreferencesKey("tts_rate")
        private val KEY_TTS_PITCH = floatPreferencesKey("tts_pitch")
        private val KEY_OCR_MODE = stringPreferencesKey("ocr_mode")
        private val KEY_TRANSLATION_ENABLED = booleanPreferencesKey("translationEnabled")
        private val KEY_STEPFUN_API_KEY = stringPreferencesKey("stepfun_api_key")
        private val KEY_STEPFUN_VOICE = stringPreferencesKey("stepfun_voice")
    }
}
