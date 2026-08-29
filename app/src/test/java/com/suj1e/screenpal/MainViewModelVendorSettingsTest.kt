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
 * MainViewModel must expose and persist the StepFun credentials
 * (2026-08-29-stepfun-only): stepfunApiKey / stepfunVoice, keeping uiState in
 * sync with DataStore. The vendor-selector (cloudVendor) is gone — StepFun is
 * the only online provider.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MainViewModelVendorSettingsTest {

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
    fun stepfunSetters_persistToDataStore_andSyncUiState() {
        val repository = SettingsRepository(RuntimeEnvironment.getApplication())
        val viewModel = newViewModel(repository)

        // Sequential writes with polling in between: consecutive update() calls
        // merge onto the persisted snapshot, so overlapping writes can lose a
        // field (last writer wins with its own snapshot).
        viewModel.setStepfunApiKey("sk-9")
        runBlocking { awaitPersisted(repository, expectKey = "sk-9") }
        viewModel.setStepfunVoice("xiaochen")

        val persisted = runBlocking { awaitPersisted(repository, expectKey = "sk-9", expectVoice = "xiaochen") }
        assertEquals("sk-9", persisted.stepfunApiKey)
        assertEquals("xiaochen", persisted.stepfunVoice)

        val state = viewModel.uiState.value
        assertEquals("sk-9", state.stepfunApiKey)
        assertEquals("xiaochen", state.stepfunVoice)
    }

    @Test
    fun uiState_syncsFromPersistedStepfunSettings() = runBlocking<Unit> {
        val repository = SettingsRepository(RuntimeEnvironment.getApplication())
        repository.update {
            copy(stepfunApiKey = "sk-seed", stepfunVoice = "xiaochen")
        }

        val viewModel = newViewModel(repository)

        val deadline = System.currentTimeMillis() + 5_000
        while (viewModel.uiState.value.stepfunApiKey != "sk-seed" &&
            System.currentTimeMillis() < deadline
        ) {
            Thread.sleep(20)
        }
        val state = viewModel.uiState.value
        assertEquals("sk-seed", state.stepfunApiKey)
        assertEquals("xiaochen", state.stepfunVoice)
    }

    @Test
    fun unrelatedUpdate_preservesStepfunSettings() = runBlocking<Unit> {
        val repository = SettingsRepository(RuntimeEnvironment.getApplication())
        val viewModel = newViewModel(repository)

        // Sequential writes with polling in between (see stepfunSetters test).
        viewModel.setStepfunApiKey("sk-keep")
        awaitPersisted(repository, expectKey = "sk-keep")
        viewModel.setStepfunVoice("wenying")
        awaitPersisted(repository, expectKey = "sk-keep", expectVoice = "wenying")
        viewModel.setTtsRate(1.5f)

        val deadline = System.currentTimeMillis() + 5_000
        var settings = repository.userSettings.first()
        while (settings.ttsRate != 1.5f && System.currentTimeMillis() < deadline) {
            Thread.sleep(20)
            settings = repository.userSettings.first()
        }
        assertEquals("sk-keep", settings.stepfunApiKey)
        assertEquals("wenying", settings.stepfunVoice)
        assertEquals(1.5f, settings.ttsRate)
    }

    private suspend fun awaitPersisted(
        repository: SettingsRepository,
        expectKey: String,
        expectVoice: String? = null,
        timeoutMs: Long = 5_000
    ): com.suj1e.screenpal.util.UserSettings {
        val deadline = System.currentTimeMillis() + timeoutMs
        var settings = repository.userSettings.first()
        while ((settings.stepfunApiKey != expectKey ||
                (expectVoice != null && settings.stepfunVoice != expectVoice)) &&
            System.currentTimeMillis() < deadline
        ) {
            Thread.sleep(20)
            settings = repository.userSettings.first()
        }
        return settings
    }
}
