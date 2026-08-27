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
 * Pure-JVM defaults contract: the user-visible defaults for the Volcano (豆包)
 * TTS keys. (DataStore-dependent tests are order-fragile because the
 * preferencesDataStore singleton is shared across Robolectric tests in one JVM.)
 */
class SettingsDefaultsTest {

    @Test
    fun volcanoDefaults_emptyCredentials_andBv001Voice() {
        val defaults = UserSettings()
        assertEquals("", defaults.volcanoSpeechAppId)
        assertEquals("", defaults.volcanoSpeechToken)
        assertEquals("BV001_streaming", defaults.ttsVoice)
    }
}

/**
 * Persistence round-trip for the Volcano (豆包) TTS settings keys
 * (2026-08-27-tts-domestic-online): volcanoSpeechAppId / volcanoSpeechToken /
 * ttsVoice must survive an update() cycle (update rewrites every key, so a
 * missing key there would silently wipe credentials on unrelated toggles).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsRepositoryTest {

    @Test
    fun volcanoKeys_roundTrip() = runBlocking<Unit> {
        val repository = SettingsRepository(RuntimeEnvironment.getApplication())

        repository.update {
            copy(
                volcanoSpeechAppId = "app-123",
                volcanoSpeechToken = "tok-456",
                ttsVoice = "zh_female_copper"
            )
        }

        val after = repository.userSettings.first()
        assertEquals("app-123", after.volcanoSpeechAppId)
        assertEquals("tok-456", after.volcanoSpeechToken)
        assertEquals("zh_female_copper", after.ttsVoice)
    }

    @Test
    fun update_unrelatedKey_preservesVolcanoCredentials() = runBlocking<Unit> {
        val repository = SettingsRepository(RuntimeEnvironment.getApplication())

        repository.update {
            copy(volcanoSpeechAppId = "keep-id", volcanoSpeechToken = "keep-token")
        }
        repository.update { copy(ttsRate = 1.5f) }

        val after = repository.userSettings.first()
        assertEquals("keep-id", after.volcanoSpeechAppId)
        assertEquals("keep-token", after.volcanoSpeechToken)
        assertEquals(1.5f, after.ttsRate)
    }
}
