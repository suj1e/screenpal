package com.suj1e.screenpal.util

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * 纯 JVM 默认值契约：StepFun-only 键面（2026-08-29-stepfun-only）——
 * 豆包/火山/方舟 5 键已从 [UserSettings] 删除，仅保留 StepFun 凭据。
 * （DataStore 依赖测试因 preferencesDataStore 单例跨测试共享而顺序脆弱，
 * 故默认值与键面用纯 JVM 断言。）
 *
 * token 以字符串拼接构造，避免残留扫描类测试对本文件自匹配。
 */
class SettingsDefaultsTest {

    /** 已删除的 5 个旧键（服务商选择键、方舟键、火山三键）。 */
    private val removedKeys = listOf(
        "cloud" + "Vendor",
        "cloud" + "ApiKey",
        "volc" + "anoSpeechAppId",
        "volc" + "anoSpeechToken",
        "tts" + "Voice"
    )

    @Test
    fun stepfunDefaults_emptyKey_andDefaultVoice() {
        val defaults = UserSettings()
        assertEquals("", defaults.stepfunApiKey)
        assertEquals("tianmeinvsheng", defaults.stepfunVoice)
    }

    @Test
    fun legacyVendorKeys_areRemovedFromUserSettings() {
        val names = UserSettings::class.java.declaredFields.map { it.name }.toSet()
        val residue = removedKeys.filter { it in names }
        assertTrue("已删键仍残留在 UserSettings：$residue", residue.isEmpty())
        assertTrue("StepFun 键必须在键面上", listOf("stepfunApiKey", "stepfunVoice").all { it in names })
    }
}

/**
 * Persistence round-trip for the StepFun settings keys: stepfunApiKey /
 * stepfunVoice must survive an update() cycle (update rewrites every key, so a
 * missing key there would silently wipe credentials on unrelated toggles).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsRepositoryTest {

    @Test
    fun stepfunKeys_roundTrip() = runBlocking<Unit> {
        val repository = SettingsRepository(RuntimeEnvironment.getApplication())

        repository.update { copy(stepfunApiKey = "sk-123", stepfunVoice = "xiaochen") }

        val after = repository.userSettings.first()
        assertEquals("sk-123", after.stepfunApiKey)
        assertEquals("xiaochen", after.stepfunVoice)
    }

    @Test
    fun update_unrelatedKey_preservesStepfunCredentials() = runBlocking<Unit> {
        val repository = SettingsRepository(RuntimeEnvironment.getApplication())

        repository.update { copy(stepfunApiKey = "sk-keep", stepfunVoice = "wenying") }
        repository.update { copy(ttsRate = 1.5f) }

        val after = repository.userSettings.first()
        assertEquals("sk-keep", after.stepfunApiKey)
        assertEquals("wenying", after.stepfunVoice)
        assertEquals(1.5f, after.ttsRate)
    }

    @Test
    fun update_unrelatedKey_preservesTranslationEnabled() = runBlocking<Unit> {
        // Merge-regression guard: SettingsRepository.update overwrites every
        // key it knows, so an unrelated setter must not reset translationEnabled.
        val repository = SettingsRepository(RuntimeEnvironment.getApplication())

        repository.update { copy(translationEnabled = false) }
        repository.update { copy(ttsRate = 1.5f) }

        assertFalse(
            "translationEnabled must survive unrelated updates",
            repository.userSettings.first().translationEnabled
        )

        // Self-clean: later test classes share this DataStore file and assert
        // the default translationEnabled=true.
        repository.update { copy(translationEnabled = true) }
    }

    @Test
    fun legacyRemovedKeys_inDataStore_areIgnoredOnRead() = runBlocking<Unit> {
        // 升级前 DataStore 里可能残留旧键；读侧不再映射即视为删除（不迁移、不报错），
        // update() 也只写已知键，不会复活旧键。StepFun 键在写入旧键前后保持不变。
        val repository = SettingsRepository(RuntimeEnvironment.getApplication())
        repository.update { copy(stepfunApiKey = "sk-legacy-check") }

        val legacyVolcanoAppId = stringPreferencesKey("volc" + "ano_speech_app_id")
        val legacyCloudApiKey = stringPreferencesKey("cloud" + "_api_key")
        repository.appContext.dataStore.edit { prefs ->
            prefs[legacyVolcanoAppId] = "legacy-app"
            prefs[legacyCloudApiKey] = "legacy-ark"
        }

        assertEquals("sk-legacy-check", repository.userSettings.first().stepfunApiKey)

        repository.update { copy(ttsRate = 1.25f) }
        val after = repository.userSettings.first()
        assertEquals(1.25f, after.ttsRate)
        assertEquals("sk-legacy-check", after.stepfunApiKey)

        // Self-clean the legacy entries for later test classes.
        repository.appContext.dataStore.edit { prefs ->
            prefs.remove(legacyVolcanoAppId)
            prefs.remove(legacyCloudApiKey)
        }
    }
}
