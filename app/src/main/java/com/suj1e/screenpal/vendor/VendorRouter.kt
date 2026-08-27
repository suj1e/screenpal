package com.suj1e.screenpal.vendor

import android.content.Context
import com.suj1e.screenpal.ocr.CloudOcrConfig
import com.suj1e.screenpal.ocr.CloudOcrProvider
import com.suj1e.screenpal.ocr.OcrEngine
import com.suj1e.screenpal.ocr.StepfunOcrProvider
import com.suj1e.screenpal.translate.DoubaoTranslateClient
import com.suj1e.screenpal.translate.StepfunTranslateClient
import com.suj1e.screenpal.translate.TranslateService
import com.suj1e.screenpal.tts.DoubaoTtsEngine
import com.suj1e.screenpal.tts.StepfunTtsEngine
import com.suj1e.screenpal.tts.TtsEngine
import com.suj1e.screenpal.util.UserSettings

/**
 * 在线服务商路由（抽象工厂）：按 [UserSettings.cloudVendor]（DOUBAO / STEPFUN，
 * 默认 DOUBAO）返回对应 vendor 的 TTS / 云 OCR / AI 转译实现；凭据缺失返回 null，
 * 由上层落兜底（TTS → Piper → 系统；OCR → 端侧；转译 → 播原文）。
 * 新增第三家只需在此各加一个分支，上层零改动。
 */
object VendorRouter {

    const val VENDOR_DOUBAO = "DOUBAO"
    const val VENDOR_STEPFUN = "STEPFUN"
    const val DEFAULT_VENDOR = VENDOR_DOUBAO

    /** 归一化 vendor 值：未知 / 空 / null 一律回落默认豆包。 */
    fun normalizeVendor(raw: String?): String = when (raw?.trim()?.uppercase()) {
        VENDOR_STEPFUN -> VENDOR_STEPFUN
        else -> DEFAULT_VENDOR
    }

    /** 在线 TTS 引擎工厂（TtsManager CLOUD 槽位）；凭据缺失返回 null。 */
    fun createTtsEngine(settings: UserSettings, context: Context): TtsEngine? =
        when (normalizeVendor(settings.cloudVendor)) {
            VENDOR_STEPFUN -> settings.stepfunApiKey.takeIf { it.isNotBlank() }?.let { key ->
                StepfunTtsEngine(
                    context = context,
                    apiKey = key,
                    voice = settings.stepfunVoice.ifBlank { StepfunTtsEngine.DEFAULT_VOICE }
                )
            }
            else -> if (settings.volcanoSpeechAppId.isNotBlank() && settings.volcanoSpeechToken.isNotBlank()) {
                DoubaoTtsEngine(
                    context = context,
                    appId = settings.volcanoSpeechAppId,
                    token = settings.volcanoSpeechToken,
                    voiceType = settings.ttsVoice.ifBlank { DoubaoTtsEngine.DEFAULT_VOICE_TYPE }
                )
            } else {
                null
            }
        }

    /** 云 OCR 引擎工厂（CLOUD 模式 / HYBRID 云侧）；凭据缺失返回 null。 */
    fun createOcrEngine(settings: UserSettings): OcrEngine? =
        when (normalizeVendor(settings.cloudVendor)) {
            VENDOR_STEPFUN -> settings.stepfunApiKey.takeIf { it.isNotBlank() }
                ?.let { StepfunOcrProvider(apiKey = it) }
            else -> settings.cloudApiKey.takeIf { it.isNotBlank() }
                ?.let { CloudOcrProvider(CloudOcrConfig(arkApiKey = it)) }
        }

    /** AI 转译客户端工厂（中文播报管道）；凭据缺失返回 null。 */
    fun createTranslateClient(settings: UserSettings): TranslateService? =
        when (normalizeVendor(settings.cloudVendor)) {
            VENDOR_STEPFUN -> settings.stepfunApiKey.takeIf { it.isNotBlank() }
                ?.let { StepfunTranslateClient(apiKey = it) }
            else -> settings.cloudApiKey.takeIf { it.isNotBlank() }
                ?.let { DoubaoTranslateClient(apiKey = it) }
        }
}
