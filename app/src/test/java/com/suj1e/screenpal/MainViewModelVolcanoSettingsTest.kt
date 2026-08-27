package com.suj1e.screenpal

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.suj1e.screenpal.util.SettingsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * MainViewModel must expose and persist the three Volcano (豆包) TTS settings
 * (2026-08-27-tts-domestic-online): volcanoSpeechAppId / volcanoSpeechToken /
 * ttsVoice, keeping uiState in sync with DataStore.
 *
 * NOTE: Dispatchers.Main is injected (UnconfinedTestDispatcher here) because
 * viewModelScope's Main dispatching never lands under Robolectric's shadow
 * looper. vmScope is cancelled via ViewModelStore.clear() in @After so the
 * DataStore collect cannot outlive the test sandbox.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MainViewModelVolcanoSettingsTest {

    private val viewModelStore = ViewModelStore()

    @After
    fun tearDown() {
        // Cancel vmScope (ends the DataStore collect) before the sandbox dies.
        viewModelStore.clear()
    }

    private fun newViewModel(repository: SettingsRepository): MainViewModel =
        ViewModelProvider(
            viewModelStore,
            viewModelFactory {
                initializer {
                    MainViewModel(repository, mainDispatcher = UnconfinedTestDispatcher())
                }
            }
        )[MainViewModel::class.java]

    @Test
    fun volcanoSetters_persistToDataStore_andSyncUiState() {
        val repository = SettingsRepository(RuntimeEnvironment.getApplication())
        val viewModel = newViewModel(repository)

        viewModel.setVolcanoAppId("app-9")
        viewModel.setVolcanoToken("tok-9")
        viewModel.setTtsVoice("zh_female_test")

        // DataStore edits finish on real IO threads; poll (bounded) for the writes.
        val persisted = runBlocking { awaitPersisted(repository) }
        assertEquals("app-9", persisted.volcanoSpeechAppId)
        assertEquals("tok-9", persisted.volcanoSpeechToken)
        assertEquals("zh_female_test", persisted.ttsVoice)

        val state = viewModel.uiState.value
        assertEquals("app-9", state.volcanoSpeechAppId)
        assertEquals("tok-9", state.volcanoSpeechToken)
        assertEquals("zh_female_test", state.ttsVoice)
    }

    @Test
    fun uiState_syncsFromPersistedSettings() = runBlocking<Unit> {
        val repository = SettingsRepository(RuntimeEnvironment.getApplication())
        // Pre-seed persisted values, then verify the init collector surfaces them.
        repository.update {
            copy(volcanoSpeechAppId = "seed-id", volcanoSpeechToken = "seed-token")
        }

        val viewModel = newViewModel(repository)

        val deadline = System.currentTimeMillis() + 5_000
        while (viewModel.uiState.value.volcanoSpeechAppId != "seed-id" &&
            System.currentTimeMillis() < deadline
        ) {
            Thread.sleep(20)
        }
        val state = viewModel.uiState.value
        assertEquals("seed-id", state.volcanoSpeechAppId)
        assertEquals("seed-token", state.volcanoSpeechToken)
    }

    private suspend fun awaitPersisted(
        repository: SettingsRepository,
        timeoutMs: Long = 5_000
    ): com.suj1e.screenpal.util.UserSettings {
        val deadline = System.currentTimeMillis() + timeoutMs
        var settings = repository.userSettings.first()
        while ((settings.volcanoSpeechAppId != "app-9" ||
                settings.volcanoSpeechToken != "tok-9" ||
                settings.ttsVoice != "zh_female_test") &&
            System.currentTimeMillis() < deadline
        ) {
            Thread.sleep(20)
            settings = repository.userSettings.first()
        }
        return settings
    }
}
