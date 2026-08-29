package com.suj1e.screenpal

import android.content.Context
import androidx.lifecycle.ViewModel
import com.suj1e.screenpal.service.FloatingWindowService
import com.suj1e.screenpal.util.AccessibilityHelper
import com.suj1e.screenpal.util.PermissionHelper
import com.suj1e.screenpal.util.SettingsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MainUiState(
    val floatingWindowEnabled: Boolean = false,
    val ttsEngine: String = "PIPER",
    val ttsRate: Float = 1.0f,
    val ttsPitch: Float = 1.0f,
    val ocrMode: String = "HYBRID",
    val translationEnabled: Boolean = true,
    val stepfunApiKey: String = "",
    val stepfunVoice: String = "tianmeinvsheng",
    // 框选方式（2026-08-29-selection-mode）：LASSO 随手画 / RECT 长方形，默认随手画。
    val selectionMode: String = "LASSO",
    val overlayPermissionGranted: Boolean = false,
    val notificationPermissionGranted: Boolean = false,
    // 无障碍截屏主路径开关（2026-08-29-permission-tri-card）：实时读系统回显，
    // 不持久化；true 时点悬浮球零弹窗识读。
    val accessibilityEnabled: Boolean = false
) {
    fun missingRequiredPermissions(): List<String> {
        val missing = mutableListOf<String>()
        if (!overlayPermissionGranted) missing.add("悬浮窗权限")
        if (!notificationPermissionGranted) missing.add("通知权限")
        return missing
    }
}

class MainViewModel(
    private val settingsRepository: SettingsRepository,
    // Injectable so Robolectric tests can swap Dispatchers.Main (whose
    // viewModelScope dispatching never lands under the shadow looper).
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val vmScope = CoroutineScope(SupervisorJob() + mainDispatcher)

    init {
        vmScope.launch {
            settingsRepository.userSettings.collect { settings ->
                _uiState.value = _uiState.value.copy(
                    floatingWindowEnabled = settings.floatingWindowEnabled,
                    ttsEngine = settings.ttsEngine,
                    ttsRate = settings.ttsRate,
                    ttsPitch = settings.ttsPitch,
                    ocrMode = settings.ocrMode,
                    translationEnabled = settings.translationEnabled,
                    stepfunApiKey = settings.stepfunApiKey,
                    stepfunVoice = settings.stepfunVoice,
                    selectionMode = settings.selectionMode
                )
                maybeAutoStartFloatingService()
            }
        }
    }

    /** Called from Activity.onResume so permission badges stay current after returning from Settings. */
    fun refreshPermissions(context: Context) {
        val status = PermissionHelper.getAllPermissionStatus(context)
        _uiState.value = _uiState.value.copy(
            overlayPermissionGranted = status["overlay"] ?: false,
            notificationPermissionGranted = status["notification"] ?: false,
            // 第三行「无障碍权限」：以 AccessibilityManager 实时回显为准，服务被杀后自然回到未开启态。
            accessibilityEnabled = AccessibilityHelper.isEnabled(context)
        )
        maybeAutoStartFloatingService()
    }

    /**
     * Self-heal: the persisted toggle says ON but the service died with a
     * previous process (reinstall, force-stop, crash). Restart it. Called from
     * both the settings collector and onResume because either side may be
     * ready first.
     */
    private fun maybeAutoStartFloatingService() {
        val state = _uiState.value
        if (state.floatingWindowEnabled &&
            state.overlayPermissionGranted &&
            !FloatingWindowService.serviceRunning
        ) {
            FloatingWindowService.start(settingsRepository.appContext)
        }
    }

    private fun update(transform: (MainUiState) -> MainUiState) {
        vmScope.launch {
            // Merge onto the persisted current values: consecutive rapid updates
            // (e.g. typing AppID then Token) each see fresh data, so no field is
            // lost to a stale in-memory uiState snapshot.
            settingsRepository.update {
                toUiState().let(transform).toUserSettings()
            }
        }
    }

    private fun com.suj1e.screenpal.util.UserSettings.toUiState(): MainUiState {
        val state = _uiState.value
        return MainUiState(
            floatingWindowEnabled = floatingWindowEnabled,
            ttsEngine = ttsEngine,
            ttsRate = ttsRate,
            ttsPitch = ttsPitch,
            ocrMode = ocrMode,
            translationEnabled = translationEnabled,
            stepfunApiKey = stepfunApiKey,
            stepfunVoice = stepfunVoice,
            selectionMode = selectionMode,
            // Runtime-only permission badges are not persisted; keep current view.
            overlayPermissionGranted = state.overlayPermissionGranted,
            notificationPermissionGranted = state.notificationPermissionGranted,
            accessibilityEnabled = state.accessibilityEnabled
        )
    }

    private fun MainUiState.toUserSettings(): com.suj1e.screenpal.util.UserSettings =
        com.suj1e.screenpal.util.UserSettings(
            floatingWindowEnabled = floatingWindowEnabled,
            ttsEngine = ttsEngine,
            ttsRate = ttsRate,
            ttsPitch = ttsPitch,
            ocrMode = ocrMode,
            translationEnabled = translationEnabled,
            stepfunApiKey = stepfunApiKey,
            stepfunVoice = stepfunVoice,
            selectionMode = selectionMode
        )

    fun setTtsEngine(engine: String) = update { it.copy(ttsEngine = engine) }

    fun setTtsRate(rate: Float) = update { it.copy(ttsRate = rate) }

    fun setTtsPitch(pitch: Float) = update { it.copy(ttsPitch = pitch) }

    fun setOcrMode(mode: String) = update { it.copy(ocrMode = mode) }

    fun setTranslationEnabled(enabled: Boolean) = update { it.copy(translationEnabled = enabled) }

    fun setStepfunApiKey(key: String) = update { it.copy(stepfunApiKey = key) }

    fun setStepfunVoice(voice: String) = update { it.copy(stepfunVoice = voice) }

    fun setSelectionMode(mode: String) = update { it.copy(selectionMode = mode) }

    /**
     * Start the floating window only when required permissions are granted.
     * Returns false (and does not start) when permissions are missing.
     */
    fun startFloatingWindow(context: Context): Boolean {
        if (_uiState.value.missingRequiredPermissions().isNotEmpty()) return false

        FloatingWindowService.start(context)
        setFloatingWindowEnabled(true)
        return true
    }

    fun stopFloatingWindow(context: Context) {
        FloatingWindowService.stop(context)
        setFloatingWindowEnabled(false)
    }

    private fun setFloatingWindowEnabled(enabled: Boolean) =
        update { it.copy(floatingWindowEnabled = enabled) }

    override fun onCleared() {
        vmScope.cancel()
        super.onCleared()
    }
}
