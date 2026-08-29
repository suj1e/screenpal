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
 * MainViewModel must expose and persist the selection-mode setting
 * (2026-08-29-selection-mode): the「框选方式」card flips selectionMode in
 * DataStore and MainUiState stays in sync.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MainViewModelSelectionModeTest {

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
    fun selectionModeSetter_persistsToDataStore_andSyncsUiState() {
        val repository = SettingsRepository(RuntimeEnvironment.getApplication())
        val viewModel = newViewModel(repository)

        viewModel.setSelectionMode("RECT")

        // setSelectionMode persists via a fire-and-forget coroutine (DataStore
        // edit suspends on IO), so poll instead of asserting synchronously.
        val deadline = System.currentTimeMillis() + 5_000
        var persisted = runBlocking { repository.userSettings.first() }
        while (persisted.selectionMode != "RECT" && System.currentTimeMillis() < deadline) {
            Thread.sleep(20)
            persisted = runBlocking { repository.userSettings.first() }
        }
        assertEquals("RECT", persisted.selectionMode)
        assertEquals("RECT", viewModel.uiState.value.selectionMode)

        // Self-clean: the DataStore file is shared across tests.
        runBlocking { repository.update { copy(selectionMode = "LASSO") } }
    }

    @Test
    fun uiState_syncsFromPersistedSelectionMode() = runBlocking<Unit> {
        val repository = SettingsRepository(RuntimeEnvironment.getApplication())
        repository.update { copy(selectionMode = "RECT") }

        val viewModel = newViewModel(repository)

        val deadline = System.currentTimeMillis() + 5_000
        while (viewModel.uiState.value.selectionMode != "RECT" &&
            System.currentTimeMillis() < deadline
        ) {
            Thread.sleep(20)
        }
        assertEquals("RECT", viewModel.uiState.value.selectionMode)

        // Self-clean: the DataStore file is shared across tests.
        repository.update { copy(selectionMode = "LASSO") }
    }

    @Test
    fun unrelatedUpdate_preservesSelectionMode() = runBlocking<Unit> {
        val repository = SettingsRepository(RuntimeEnvironment.getApplication())
        val viewModel = newViewModel(repository)

        viewModel.setSelectionMode("RECT")
        viewModel.setTtsRate(1.5f)

        // 轮询条件必须是本次两次写入的合取：DataStore 文件跨测试类共享，
        // ttsRate 可能被前一测试类遗留为 1.5f——只等 rate 会在 setter 落盘前
        // 提前放行，读到旧 selectionMode 而误报 flaky（2026-08-29-broadcast-mode 修复）。
        val deadline = System.currentTimeMillis() + 5_000
        var settings = runBlocking { repository.userSettings.first() }
        while ((settings.selectionMode != "RECT" || settings.ttsRate != 1.5f) &&
            System.currentTimeMillis() < deadline
        ) {
            Thread.sleep(20)
            settings = runBlocking { repository.userSettings.first() }
        }
        assertEquals("RECT", settings.selectionMode)
        assertEquals(1.5f, settings.ttsRate)

        // Self-clean: the DataStore file is shared across tests.
        runBlocking { repository.update { copy(selectionMode = "LASSO", ttsRate = 1.0f) } }
    }
}
