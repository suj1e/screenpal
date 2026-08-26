package com.suj1e.screenpal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.suj1e.screenpal.util.SettingsRepository
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
    val cloudApiKey: String = ""
)

class MainViewModel(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.userSettings.collect { settings ->
                _uiState.value = MainUiState(
                    floatingWindowEnabled = settings.floatingWindowEnabled,
                    ttsEngine = settings.ttsEngine,
                    ttsRate = settings.ttsRate,
                    ttsPitch = settings.ttsPitch,
                    ocrMode = settings.ocrMode,
                    cloudApiKey = settings.cloudApiKey
                )
            }
        }
    }
}
