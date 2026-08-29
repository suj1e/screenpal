package com.suj1e.screenpal.tts

import android.content.Context
import android.util.Log
import com.suj1e.screenpal.util.SettingsRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.first
import java.util.concurrent.atomic.AtomicBoolean

class TtsException(message: String, cause: Throwable? = null) : Exception(message, cause)

data class TtsConfig(
    val engineType: TtsEngineType,
    val rate: Float,
    val pitch: Float
)

/**
 * Unified TTS entry point. Picks the engine configured in settings and degrades
 * automatically: CLOUD(StepFun) -> PIPER -> SYSTEM whichever slot fails.
 */
class TtsManager(
    private val context: Context,
    private val piperEngine: TtsEngine,
    private val cloudProviderFactory: suspend () -> TtsEngine? = { null },
    private val systemEngineProvider: () -> TtsEngine = { SystemTtsEngine(context) },
    private val settingsProvider: suspend () -> TtsConfig
) {
    private val speakingFlow = MutableSharedFlow<Boolean>(replay = 1)
    val isSpeaking: SharedFlow<Boolean> = speakingFlow

    private var activeEngine: TtsEngine? = null
    private var activeCloudEngine: TtsEngine? = null
    private val systemEngine: TtsEngine by lazy { systemEngineProvider() }
    private var systemEngineInitialized = AtomicBoolean(false)

    suspend fun initialize() {
        runCatching { piperEngine.initialize() }
            .onFailure { Log.w(TAG, "Piper init failed; will degrade at speak time", it) }
    }

    suspend fun speak(text: String) {
        if (text.isBlank()) return

        val config = settingsProvider()
        val type = config.engineType

        speakingFlow.emit(true)
        try {
            when (type) {
                TtsEngineType.PIPER -> speakWithFallback(text, config.rate, config.pitch)
                TtsEngineType.CLOUD -> {
                    if (!speakWithCloud(text, config.rate, config.pitch)) {
                        speakWithFallback(text, config.rate, config.pitch)
                    }
                }
                TtsEngineType.SYSTEM -> speakWithSystem(text, config.rate, config.pitch)
            }
        } finally {
            speakingFlow.emit(false)
        }
    }

    private suspend fun speakWithFallback(text: String, rate: Float, pitch: Float) {
        try {
            piperEngine.speak(text, rate, pitch)
            activeEngine = piperEngine
        } catch (e: Exception) {
            Log.w(TAG, "Piper speak failed; degrading", e)
            if (!speakWithCloud(text, rate, pitch)) {
                speakWithSystem(text, rate, pitch)
            }
        }
    }

    private suspend fun speakWithCloud(text: String, rate: Float, pitch: Float): Boolean {
        val engine = cloudProviderFactory() ?: return false
        return try {
            // Replace-in-progress semantics: stop the previous engine before
            // starting a new one, else overlapping plays cannot be stopped.
            activeCloudEngine?.stop()
            activeEngine = engine
            activeCloudEngine = engine
            engine.speak(text, rate, pitch)
            true
        } catch (e: Exception) {
            Log.w(TAG, "Cloud TTS failed; degrading", e)
            false
        }
    }

    private suspend fun speakWithSystem(text: String, rate: Float, pitch: Float) {
        try {
            if (!systemEngine.isInitialized && !systemEngineInitialized.get()) {
                systemEngine.initialize()
                systemEngineInitialized.set(true)
            }
            systemEngine.speak(text, rate, pitch)
            activeEngine = systemEngine
        } catch (e: Exception) {
            Log.e(TAG, "All TTS engines failed", e)
            throw TtsException("All TTS engines failed", e)
        }
    }

    fun stop() {
        piperEngine.stop()
        activeCloudEngine?.stop()
        systemEngine.stop()
    }

    fun shutdown() {
        stop()
        piperEngine.shutdown()
        systemEngine.shutdown()
        activeEngine = null
    }

    companion object {
        const val TAG = "TtsManager"

    }
}
