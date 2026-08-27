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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

val Application.dataStore by preferencesDataStore(name = "settings")

// open only so Robolectric tests can subclass and record warmUpTts() wiring.
open class ScreenPalApplication : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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
        warmUpTts()
    }

    /**
     * Async warm-up of the default Piper TTS engine: triggers model download and
     * ONNX session creation off the main thread. initialize() is runCatching-safe
     * (network failure only logs), so failures never crash the app; next launch retries.
     * open + internal so tests can record that onCreate wires it up.
     */
    internal open fun warmUpTts() {
        appScope.launch { ttsManager.initialize() }
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
