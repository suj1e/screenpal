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
 * MainViewModel must expose and persist the broadcast-mode setting
 * (2026-08-29-broadcast-mode): the「播报模式」card flips broadcastMode in
 * DataStore and MainUiState stays in sync.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MainViewModelBroadcastModeTest {

    private val viewModelStore = ViewModelStore()

    @After
    fun tearDown() {
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
    fun broadcastModeSetter_persistsToDataStore_andSyncsUiState() {
        val repository = SettingsRepository(RuntimeEnvironment.getApplication())
        val viewModel = newViewModel(repository)

        viewModel.setBroadcastMode("EXPLAIN")

        // setBroadcastMode persists via a fire-and-forget coroutine (DataStore
        // edit suspends on IO), so poll instead of asserting synchronously.
        val deadline = System.currentTimeMillis() + 5_000
        var persisted = runBlocking { repository.userSettings.first() }
        while (persisted.broadcastMode != "EXPLAIN" && System.currentTimeMillis() < deadline) {
            Thread.sleep(20)
            persisted = runBlocking { repository.userSettings.first() }
        }
        assertEquals("EXPLAIN", persisted.broadcastMode)
        assertEquals("EXPLAIN", viewModel.uiState.value.broadcastMode)

        // Self-clean: the DataStore file is shared across tests.
        runBlocking { repository.update { copy(broadcastMode = "TRANSLATE") } }
    }

    @Test
    fun uiState_syncsFromPersistedBroadcastMode() = runBlocking<Unit> {
        val repository = SettingsRepository(RuntimeEnvironment.getApplication())
        repository.update { copy(broadcastMode = "EXPLAIN") }

        val viewModel = newViewModel(repository)

        val deadline = System.currentTimeMillis() + 5_000
        while (viewModel.uiState.value.broadcastMode != "EXPLAIN" &&
            System.currentTimeMillis() < deadline
        ) {
            Thread.sleep(20)
        }
        assertEquals("EXPLAIN", viewModel.uiState.value.broadcastMode)

        // Self-clean: the DataStore file is shared across tests.
        repository.update { copy(broadcastMode = "TRANSLATE") }
    }

    @Test
    fun unrelatedUpdate_preservesBroadcastMode() = runBlocking<Unit> {
        val repository = SettingsRepository(RuntimeEnvironment.getApplication())
        val viewModel = newViewModel(repository)

        viewModel.setBroadcastMode("EXPLAIN")
        viewModel.setTtsRate(1.5f)

        val deadline = System.currentTimeMillis() + 5_000
        var settings = runBlocking { repository.userSettings.first() }
        while (settings.ttsRate != 1.5f && System.currentTimeMillis() < deadline) {
            Thread.sleep(20)
            settings = runBlocking { repository.userSettings.first() }
        }
        assertEquals("EXPLAIN", settings.broadcastMode)
        assertEquals(1.5f, settings.ttsRate)

        // Self-clean: the DataStore file is shared across tests.
        runBlocking { repository.update { copy(broadcastMode = "TRANSLATE") } }
    }
}
