package com.suj1e.screenpal

import android.app.Application
import android.media.projection.MediaProjection
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.suj1e.screenpal.tts.GoogleCloudTtsProvider
import com.suj1e.screenpal.tts.PiperTtsEngine
import com.suj1e.screenpal.tts.SystemTtsEngine
import com.suj1e.screenpal.tts.TtsManager
import com.suj1e.screenpal.util.SettingsRepository
import kotlinx.coroutines.flow.first

val Application.dataStore by preferencesDataStore(name = "settings")

class ScreenPalApplication : Application() {
    lateinit var settingsRepository: SettingsRepository
        private set

    lateinit var ttsManager: TtsManager
        private set

    var mediaProjection: MediaProjection? = null
        private set

    override fun onCreate() {
        super.onCreate()
        settingsRepository = SettingsRepository(this)
        ttsManager = TtsManager(
            context = this,
            piperEngine = PiperTtsEngine(this),
            cloudProviderFactory = {
                val s = settingsRepository.userSettings.first()
                s.cloudApiKey.takeIf { it.isNotBlank() }?.let { GoogleCloudTtsProvider(it) }
            },
            systemEngineProvider = { SystemTtsEngine(this) },
            settingsProvider = {
                val s = settingsRepository.userSettings.first()
                com.suj1e.screenpal.tts.TtsConfig(
                    engineType = com.suj1e.screenpal.tts.TtsEngineType.from(s.ttsEngine),
                    rate = s.ttsRate,
                    pitch = s.ttsPitch
                )
            }
        )
    }

    fun setMediaProjection(projection: MediaProjection) {
        releaseMediaProjection()
        mediaProjection = projection
        // API 34+ requires a registered callback before createVirtualDisplay().
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            projection.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    // The async stop may belong to a replaced projection; only clear if current.
                    if (mediaProjection === projection) {
                        mediaProjection = null
                    }
                }
            }, null)
        }
    }

    fun hasValidMediaProjection(): Boolean {
        return mediaProjection != null
    }

    fun releaseMediaProjection() {
        try {
            mediaProjection?.stop()
        } catch (e: Exception) {
            // ignore
        } finally {
            mediaProjection = null
        }
    }
}
