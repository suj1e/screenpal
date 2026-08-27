package com.suj1e.screenpal.ocr

import android.content.Context
import android.graphics.Bitmap
import com.suj1e.screenpal.ocr.paddle.PaddleOcrProvider

enum class OcrMode {
    LOCAL,
    CLOUD,
    HYBRID
}

class OcrEngineFactory(
    private val context: Context
) {
    fun create(
        mode: OcrMode,
        cloudConfig: CloudOcrConfig?
    ): OcrEngine {
        return when (mode) {
            OcrMode.LOCAL -> PaddleOcrProvider.getInstance(context)
            OcrMode.CLOUD -> {
                val config = cloudConfig ?: throw IllegalArgumentException("Cloud OCR requires config")
                CloudOcrProvider(config)
            }
            OcrMode.HYBRID -> {
                val localProvider = PaddleOcrProvider.getInstance(context)
                val cloudProvider = cloudConfig?.let { CloudOcrProvider(it) }
                HybridOcrEngine(localProvider, cloudProvider)
            }
        }
    }
}
