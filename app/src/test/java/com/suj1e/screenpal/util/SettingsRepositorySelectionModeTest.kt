package com.suj1e.screenpal.util

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Defaults contract for the selection-mode key (2026-08-29-selection-mode):
 * existing installs have no `selectionMode` entry and must land on LASSO so
 * the lasso behavior stays unchanged after upgrade. The "missing key → LASSO"
 * fallback is asserted pure-JVM (UserSettings default + fromStorageValue(null)),
 * because preferencesDataStore is a process-wide singleton whose state leaks
 * across tests (same convention as SettingsDefaultsTest).
 */
class SelectionModeDefaultsTest {

    @Test
    fun selectionMode_defaultIsLasso() {
        assertEquals("LASSO", UserSettings().selectionMode)
    }
}

/**
 * Persistence round-trip for the `selectionMode` DataStore key. update()
 * rewrites every key, so a missing write there would silently reset the
 * chosen mode on unrelated toggles.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsRepositorySelectionModeTest {

    @Test
    fun selectionMode_roundTrip() = runBlocking<Unit> {
        val repository = SettingsRepository(RuntimeEnvironment.getApplication())

        repository.update { copy(selectionMode = "RECT") }

        val after = repository.userSettings.first()
        assertEquals("RECT", after.selectionMode)

        // Self-clean: the DataStore file is shared across tests.
        repository.update { copy(selectionMode = "LASSO") }
    }

    @Test
    fun update_unrelatedKey_preservesSelectionMode() = runBlocking<Unit> {
        val repository = SettingsRepository(RuntimeEnvironment.getApplication())

        repository.update { copy(selectionMode = "RECT") }
        repository.update { copy(ttsRate = 1.5f) }

        val after = repository.userSettings.first()
        assertEquals("RECT", after.selectionMode)
        assertEquals(1.5f, after.ttsRate)

        // Self-clean: the DataStore file is shared across tests.
        repository.update { copy(selectionMode = "LASSO") }
    }
}
