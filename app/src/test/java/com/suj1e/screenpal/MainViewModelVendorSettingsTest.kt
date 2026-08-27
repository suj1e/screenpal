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
 * MainViewModel must expose and persist the vendor-selector settings
 * (2026-08-27-stepfun-vendor): cloudVendor / stepfunApiKey / stepfunVoice,
 * keeping uiState in sync with DataStore.
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
    fun vendorSetters_persistToDataStore_andSyncUiState() {
        val repository = SettingsRepository(RuntimeEnvironment.getApplication())
        val viewModel = newViewModel(repository)

        viewModel.setCloudVendor("STEPFUN")
        viewModel.setStepfunApiKey("sk-9")
        viewModel.setStepfunVoice("xiaochen")

        val persisted = runBlocking { awaitPersisted(repository) }
        assertEquals("STEPFUN", persisted.cloudVendor)
        assertEquals("sk-9", persisted.stepfunApiKey)
        assertEquals("xiaochen", persisted.stepfunVoice)

        val state = viewModel.uiState.value
        assertEquals("STEPFUN", state.cloudVendor)
        assertEquals("sk-9", state.stepfunApiKey)
        assertEquals("xiaochen", state.stepfunVoice)
    }

    @Test
    fun uiState_syncsFromPersistedVendorSettings() = runBlocking<Unit> {
        val repository = SettingsRepository(RuntimeEnvironment.getApplication())
        repository.update {
            copy(cloudVendor = "STEPFUN", stepfunApiKey = "sk-seed", stepfunVoice = "xiaochen")
        }

        val viewModel = newViewModel(repository)

        val deadline = System.currentTimeMillis() + 5_000
        while (viewModel.uiState.value.cloudVendor != "STEPFUN" &&
            System.currentTimeMillis() < deadline
        ) {
            Thread.sleep(20)
        }
        val state = viewModel.uiState.value
        assertEquals("STEPFUN", state.cloudVendor)
        assertEquals("sk-seed", state.stepfunApiKey)
        assertEquals("xiaochen", state.stepfunVoice)
    }

    @Test
    fun unrelatedUpdate_preservesVendorSettings() = runBlocking<Unit> {
        val repository = SettingsRepository(RuntimeEnvironment.getApplication())
        val viewModel = newViewModel(repository)

        viewModel.setCloudVendor("STEPFUN")
        viewModel.setStepfunApiKey("sk-keep")
        viewModel.setStepfunVoice("wenying")
        viewModel.setTtsRate(1.5f)

        val deadline = System.currentTimeMillis() + 5_000
        var settings = runBlocking { repository.userSettings.first() }
        while (settings.ttsRate != 1.5f && System.currentTimeMillis() < deadline) {
            Thread.sleep(20)
            settings = runBlocking { repository.userSettings.first() }
        }
        assertEquals("STEPFUN", settings.cloudVendor)
        assertEquals("sk-keep", settings.stepfunApiKey)
        assertEquals("wenying", settings.stepfunVoice)
        assertEquals(1.5f, settings.ttsRate)
    }

    private suspend fun awaitPersisted(
        repository: SettingsRepository,
        timeoutMs: Long = 5_000
    ): com.suj1e.screenpal.util.UserSettings {
        val deadline = System.currentTimeMillis() + timeoutMs
        var settings = repository.userSettings.first()
        while ((settings.cloudVendor != "STEPFUN" ||
                settings.stepfunApiKey != "sk-9" ||
                settings.stepfunVoice != "xiaochen") &&
            System.currentTimeMillis() < deadline
        ) {
            Thread.sleep(20)
            settings = repository.userSettings.first()
        }
        return settings
    }
}
