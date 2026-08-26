package com.suj1e.screenpal.ocr

import android.graphics.Bitmap

class HybridOcrEngine(
    private val mlKitProvider: OcrEngine,
    private val cloudProvider: OcrEngine?,
    private val confidenceThreshold: Float = 0.75f
) : OcrEngine {

    override suspend fun recognize(bitmap: Bitmap): OcrResult {
        val mlKitResult = mlKitProvider.recognize(bitmap)

        if (mlKitResult.confidence < confidenceThreshold && cloudProvider != null) {
            return cloudProvider.recognize(bitmap)
        }

        return mlKitResult
    }
}
