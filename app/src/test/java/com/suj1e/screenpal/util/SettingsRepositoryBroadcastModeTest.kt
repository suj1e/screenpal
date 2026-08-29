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
 * Defaults contract for the broadcast-mode key (2026-08-29-broadcast-mode):
 * existing installs have no `broadcastMode` entry and must land on TRANSLATE
 * so the translate-read-aloud behavior stays unchanged after upgrade.
 */
class BroadcastModeDefaultsTest {

    @Test
    fun broadcastMode_defaultIsTranslate() {
        assertEquals("TRANSLATE", UserSettings().broadcastMode)
    }
}

/**
 * Persistence round-trip for the `broadcastMode` DataStore key. update()
 * rewrites every key, so a missing write there would silently reset the
 * chosen mode on unrelated toggles.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsRepositoryBroadcastModeTest {

    @Test
    fun broadcastMode_roundTrip() = runBlocking<Unit> {
        val repository = SettingsRepository(RuntimeEnvironment.getApplication())

        repository.update { copy(broadcastMode = "EXPLAIN") }

        val after = repository.userSettings.first()
        assertEquals("EXPLAIN", after.broadcastMode)

        // Self-clean: the DataStore file is shared across tests.
        repository.update { copy(broadcastMode = "TRANSLATE") }
    }

    @Test
    fun update_unrelatedKey_preservesBroadcastMode() = runBlocking<Unit> {
        val repository = SettingsRepository(RuntimeEnvironment.getApplication())

        repository.update { copy(broadcastMode = "EXPLAIN") }
        repository.update { copy(ttsRate = 1.5f) }

        val after = repository.userSettings.first()
        assertEquals("EXPLAIN", after.broadcastMode)
        assertEquals(1.5f, after.ttsRate)

        // Self-clean: the DataStore file is shared across tests.
        repository.update { copy(broadcastMode = "TRANSLATE") }
    }
}
