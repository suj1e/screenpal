package com.suj1e.screenpal.translate

import com.suj1e.screenpal.tts.TtsManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * EXPLAIN 分支矩阵（2026-08-29-broadcast-mode）：讲解成功 / 中文文本也讲解
 * （跳过中文启发式）/ 讲解失败与超时降级播原文 / 讲解空视为失败 / 空文本直返
 * Direct（零网络零播报）/ 外层取消传播；外加默认参数零回归钉（缺省 mode =
 * TRANSLATE，绝不调 explain）。
 */
class ChineseBroadcastPipelineExplainTest {

    private class FakeTranslateService(
        private val translateBehavior: suspend (String) -> String = { "你好世界" },
        private val explainBehavior: suspend (String) -> String = { "这是圈选内容的讲解" }
    ) : TranslateService {
        val translateRequested = mutableListOf<String>()
        val explainRequested = mutableListOf<String>()
        var translateCallCount = 0
            private set
        var explainCallCount = 0
            private set

        override suspend fun translate(text: String): String {
            translateCallCount++
            translateRequested.add(text)
            return translateBehavior(text)
        }

        override suspend fun explain(text: String): String {
            explainCallCount++
            explainRequested.add(text)
            return explainBehavior(text)
        }
    }

    private fun tts(): TtsManager = mockk(relaxed = true)

    @Test
    fun explainMode_success_speaksExplanation_returnsExplained() = runTest {
        val service = FakeTranslateService(explainBehavior = { "这是勿扰模式，打开后来电静音" })
        val pipeline = ChineseBroadcastPipeline(service)
        val tts = tts()

        val outcome = pipeline.broadcast("Screen Time", tts, mode = BroadcastMode.EXPLAIN)

        assertEquals(BroadcastOutcome.EXPLAINED, outcome)
        assertEquals(0, service.translateCallCount)
        assertEquals(listOf("Screen Time"), service.explainRequested)
        coVerify(exactly = 1) { tts.speak("这是勿扰模式，打开后来电静音") }
        coVerify(exactly = 0) { tts.speak("Screen Time") }
        assertEquals("这是勿扰模式，打开后来电静音", pipeline.lastSpokenText)
    }

    @Test
    fun explainMode_chineseText_stillExplains_noHeuristicGate() = runTest {
        val service = FakeTranslateService(explainBehavior = { "这是一个设置项" })
        val pipeline = ChineseBroadcastPipeline(service)
        val tts = tts()

        val outcome = pipeline.broadcast("勿扰模式", tts, mode = BroadcastMode.EXPLAIN)

        assertEquals(BroadcastOutcome.EXPLAINED, outcome)
        assertEquals(1, service.explainCallCount)
        coVerify(exactly = 1) { tts.speak("这是一个设置项") }
        coVerify(exactly = 0) { tts.speak("勿扰模式") }
    }

    @Test
    fun explainMode_ignoresTranslationEnabledSwitch() = runTest {
        // 「中文播报」开关仅作用于 TRANSLATE 分支；讲解模式是显式意图，不受其限制。
        val service = FakeTranslateService(explainBehavior = { "这是一个设置项" })
        val pipeline = ChineseBroadcastPipeline(service)
        val tts = tts()

        val outcome = pipeline.broadcast("勿扰模式", tts, translationEnabled = false, mode = BroadcastMode.EXPLAIN)

        assertEquals(BroadcastOutcome.EXPLAINED, outcome)
        assertEquals(1, service.explainCallCount)
        coVerify(exactly = 1) { tts.speak("这是一个设置项") }
    }

    @Test
    fun explainMode_failure_fallsBackToOriginal() = runTest {
        val service = FakeTranslateService { throw TranslationException("讲解缺少 StepFun API Key") }
        val pipeline = ChineseBroadcastPipeline(service)
        val tts = tts()

        val outcome = pipeline.broadcast("Screen Time", tts, mode = BroadcastMode.EXPLAIN)

        assertEquals(BroadcastOutcome.FallbackOriginal, outcome)
        coVerify(exactly = 1) { tts.speak("Screen Time") }
        assertEquals("Screen Time", pipeline.lastSpokenText)
    }

    @Test
    fun explainMode_blankExplanation_treatedAsFailure_fallsBackToOriginal() = runTest {
        val service = FakeTranslateService(explainBehavior = { "   " })
        val pipeline = ChineseBroadcastPipeline(service)
        val tts = tts()

        val outcome = pipeline.broadcast("Screen Time", tts, mode = BroadcastMode.EXPLAIN)

        assertEquals(BroadcastOutcome.FallbackOriginal, outcome)
        coVerify(exactly = 1) { tts.speak("Screen Time") }
        assertEquals("Screen Time", pipeline.lastSpokenText)
    }

    @Test
    fun explainMode_timeout_fallsBackToOriginal() = runTest {
        // runTest 虚拟时间：讲解挂起 10s > 5s 超时，测试本身瞬时完成。
        val service = FakeTranslateService(explainBehavior = { delay(10_000); "迟到讲解" })
        val pipeline = ChineseBroadcastPipeline(service, timeoutMs = 5_000)
        val tts = tts()

        val outcome = pipeline.broadcast("Screen Time", tts, mode = BroadcastMode.EXPLAIN)

        assertEquals(BroadcastOutcome.FallbackOriginal, outcome)
        coVerify(exactly = 1) { tts.speak("Screen Time") }
        coVerify(exactly = 0) { tts.speak("迟到讲解") }
    }

    @Test
    fun explainMode_emptyText_returnsDirect_zeroNetwork_zeroSpeak() = runTest {
        val service = FakeTranslateService()
        val pipeline = ChineseBroadcastPipeline(service)
        val tts = tts()

        val outcome = pipeline.broadcast("", tts, mode = BroadcastMode.EXPLAIN)

        assertEquals(BroadcastOutcome.Direct, outcome)
        assertEquals(0, service.explainCallCount)
        assertEquals(0, service.translateCallCount)
        coVerify(exactly = 0) { tts.speak(any()) }
        assertEquals("", pipeline.lastSpokenText)
    }

    @Test
    fun `explain mode outer cancellation propagates and never falls back`() = runTest {
        val tts = tts()
        val service = FakeTranslateService(explainBehavior = { awaitCancellation() })
        val pipeline = ChineseBroadcastPipeline(service)

        val job = launch {
            pipeline.broadcast("hello world", tts, mode = BroadcastMode.EXPLAIN)
        }
        advanceTimeBy(1000)
        job.cancelAndJoin()

        coVerify(exactly = 0) { tts.speak(any()) }
    }

    @Test
    fun defaultMode_isTranslate_neverCallsExplain_zeroRegression() = runTest {
        // 缺省 mode 参数 = TRANSLATE：走既有翻译分支，explain 永不被调用。
        val service = FakeTranslateService()
        val pipeline = ChineseBroadcastPipeline(service)
        val tts = tts()

        val outcome = pipeline.broadcast("Hello, world", tts)

        assertEquals(BroadcastOutcome.Translated, outcome)
        assertEquals(0, service.explainCallCount)
        assertEquals(listOf("Hello, world"), service.translateRequested)
        coVerify(exactly = 1) { tts.speak("你好世界") }
    }

    @Test
    fun explainMode_speakFailure_propagates_noSwallow() = runTest {
        val service = FakeTranslateService(explainBehavior = { "这是讲解" })
        val pipeline = ChineseBroadcastPipeline(service)
        val tts = tts()
        coEvery { tts.speak(any()) } throws RuntimeException("tts boom")

        assertThrows(RuntimeException::class.java) {
            kotlinx.coroutines.runBlocking { pipeline.broadcast("Hello", tts, mode = BroadcastMode.EXPLAIN) }
        }

        coVerify(exactly = 1) { tts.speak(any()) }
    }
}
