package com.suj1e.screenpal.util

import android.content.Context
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * 「中文播报」开关持久化往返：默认 true；写 false/true 均能读回。
 * 单方法串行执行，避免同文件 DataStore 多实例冲突。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsRepositoryTranslationEnabledTest {

    @Test
    fun translationEnabled_defaultTrue_roundtripPersists() = runBlocking {
        val context = RuntimeEnvironment.getApplication() as Context
        val repo = SettingsRepository(context)

        // 默认值 true（开关默认开）
        assertEquals(true, repo.userSettings.first().translationEnabled)

        // 写 false → 读回 false
        repo.update { copy(translationEnabled = false) }
        assertEquals(false, repo.userSettings.first().translationEnabled)

        // 写回 true → 读回 true，且不影响其他键
        repo.update { copy(translationEnabled = true) }
        val settings = repo.userSettings.first()
        assertEquals(true, settings.translationEnabled)
        assertEquals("HYBRID", settings.ocrMode)
    }
}
