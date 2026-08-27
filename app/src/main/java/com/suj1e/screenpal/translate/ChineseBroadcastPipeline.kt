package com.suj1e.screenpal.translate

import android.util.Log
import com.suj1e.screenpal.tts.TtsManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

/** 播报结果：翻译后播报 / 中文直读 / 降级播报原文。 */
enum class BroadcastOutcome { Translated, Direct, FallbackOriginal }

/**
 * OCR 文本 → 中文播报管道：
 * 开关关或启发式判定中文 → 直读原文（零网络）；
 * 否则 5s 超时内调 [TranslateService] 转译，成功播译文，
 * 任何失败（无 Key / 网络 / 超时 / 空译文）降级播原文。
 * TTS 自身异常不吞、向上传播，保持既有播报失败语义。
 */
class ChineseBroadcastPipeline(
    private val translateService: TranslateService,
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS
) {

    /** 最近一次 broadcast 实际播报的文本（降级时为原文），供结果卡主显。 */
    var lastSpokenText: String? = null
        private set

    suspend fun broadcast(
        text: String,
        tts: TtsManager,
        translationEnabled: Boolean = true
    ): BroadcastOutcome {
        if (!translationEnabled || ChineseHeuristic.isMostlyChinese(text)) {
            lastSpokenText = text
            tts.speak(text)
            return BroadcastOutcome.Direct
        }

        val translated = try {
            withTimeout(timeoutMs) { translateService.translate(text) }
        } catch (e: TimeoutCancellationException) {
            Log.w(TAG, "翻译超时（${timeoutMs}ms），降级播报原文", e)
            null
        } catch (e: CancellationException) {
            // 外层协程取消必须继续传播；仅超时降级。
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "翻译失败，降级播报原文", e)
            null
        }

        if (translated.isNullOrBlank()) {
            lastSpokenText = text
            tts.speak(text)
            return BroadcastOutcome.FallbackOriginal
        }

        lastSpokenText = translated
        tts.speak(translated)
        return BroadcastOutcome.Translated
    }

    companion object {
        const val TAG = "ChineseBroadcast"

        /** 翻译超时上限：大模型短文本 RTT 约 1–2s，5s 兜底。 */
        const val DEFAULT_TIMEOUT_MS = 5000L
    }
}
