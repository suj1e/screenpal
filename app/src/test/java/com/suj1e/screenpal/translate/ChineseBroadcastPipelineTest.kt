package com.suj1e.screenpal.translate

import com.suj1e.screenpal.tts.TtsManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * 降级矩阵五用例（开关关 / 中文直读 / 翻译成功 / 无 Key 失败 / 超时降级）
 * + 边界（译文空视为失败；speak 自身异常不吞、向上传播）。
 */
class ChineseBroadcastPipelineTest {

    private class FakeTranslateService(
        private val behavior: suspend (String) -> String = { "你好世界" }
    ) : TranslateService {
        val requested = mutableListOf<String>()
        var callCount = 0
            private set

        override suspend fun translate(text: String): String {
            callCount++
            requested.add(text)
            return behavior(text)
        }
    }

    private fun tts(): TtsManager = mockk(relaxed = true)

    @Test
    fun translationDisabled_speaksOriginalDirectly_zeroNetwork() = runTest {
        val service = FakeTranslateService()
        val pipeline = ChineseBroadcastPipeline(service)
        val tts = tts()

        val outcome = pipeline.broadcast("Hello world", tts, translationEnabled = false)

        assertEquals(BroadcastOutcome.Direct, outcome)
        assertEquals(0, service.callCount)
        coVerify(exactly = 1) { tts.speak("Hello world") }
        assertEquals("Hello world", pipeline.lastSpokenText)
    }

    @Test
    fun chineseText_speaksOriginalDirectly_zeroNetwork() = runTest {
        val service = FakeTranslateService()
        val pipeline = ChineseBroadcastPipeline(service)
        val tts = tts()

        val outcome = pipeline.broadcast("今天天气真不错", tts, translationEnabled = true)

        assertEquals(BroadcastOutcome.Direct, outcome)
        assertEquals(0, service.callCount)
        coVerify(exactly = 1) { tts.speak("今天天气真不错") }
    }

    @Test
    fun translationSuccess_speaksTranslated() = runTest {
        val service = FakeTranslateService { "你好，世界" }
        val pipeline = ChineseBroadcastPipeline(service)
        val tts = tts()

        val outcome = pipeline.broadcast("Hello, world", tts, translationEnabled = true)

        assertEquals(BroadcastOutcome.Translated, outcome)
        assertEquals(listOf("Hello, world"), service.requested)
        coVerify(exactly = 1) { tts.speak("你好，世界") }
        coVerify(exactly = 0) { tts.speak("Hello, world") }
        assertEquals("你好，世界", pipeline.lastSpokenText)
    }

    @Test
    fun translationFails_missingKey_fallsBackToOriginal() = runTest {
        val service = FakeTranslateService { throw TranslationException("翻译缺少火山方舟 API Key") }
        val pipeline = ChineseBroadcastPipeline(service)
        val tts = tts()

        val outcome = pipeline.broadcast("Hello, world", tts, translationEnabled = true)

        assertEquals(BroadcastOutcome.FallbackOriginal, outcome)
        coVerify(exactly = 1) { tts.speak("Hello, world") }
        assertEquals("Hello, world", pipeline.lastSpokenText)
    }

    @Test
    fun translationTimeout_fallsBackToOriginal() = runTest {
        // runTest 虚拟时间：翻译挂起 10s > 默认 5s 超时，测试本身瞬时完成。
        val service = FakeTranslateService { delay(10_000); "迟到译文" }
        val pipeline = ChineseBroadcastPipeline(service, timeoutMs = 5_000)
        val tts = tts()

        val outcome = pipeline.broadcast("Hello, world", tts, translationEnabled = true)

        assertEquals(BroadcastOutcome.FallbackOriginal, outcome)
        coVerify(exactly = 1) { tts.speak("Hello, world") }
        coVerify(exactly = 0) { tts.speak("迟到译文") }
    }

    @Test
    fun blankTranslation_treatedAsFailure_fallsBackToOriginal() = runTest {
        val service = FakeTranslateService { "   " }
        val pipeline = ChineseBroadcastPipeline(service)
        val tts = tts()

        val outcome = pipeline.broadcast("Hello, world", tts, translationEnabled = true)

        assertEquals(BroadcastOutcome.FallbackOriginal, outcome)
        coVerify(exactly = 1) { tts.speak("Hello, world") }
    }

    @Test
    fun speakFailure_propagates_noSwallow_noSecondSpeak() = runTest {
        val service = FakeTranslateService { "你好，世界" }
        val pipeline = ChineseBroadcastPipeline(service)
        val tts = tts()
        coEvery { tts.speak(any()) } throws RuntimeException("tts boom")

        assertThrows(RuntimeException::class.java) {
            kotlinx.coroutines.runBlocking { pipeline.broadcast("Hello, world", tts) }
        }

        coVerify(exactly = 1) { tts.speak(any()) }
    }
}
