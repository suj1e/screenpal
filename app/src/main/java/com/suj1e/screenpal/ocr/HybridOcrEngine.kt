package com.suj1e.screenpal.ocr

import android.graphics.Bitmap
import android.util.Log
import kotlin.coroutines.cancellation.CancellationException

class HybridOcrEngine(
    private val mlKitProvider: OcrEngine,
    private val cloudProvider: OcrEngine?,
    private val confidenceThreshold: Float = 0.75f
) : OcrEngine {

    override suspend fun recognize(bitmap: Bitmap): OcrResult {
        val mlKitResult = mlKitProvider.recognize(bitmap)

        if (mlKitResult.confidence < confidenceThreshold && cloudProvider != null) {
            return try {
                cloudProvider.recognize(bitmap)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Design contract: cloud errors (401/429/network) degrade to the
                // local result instead of failing the whole recognition.
                Log.w("HybridOcrEngine", "cloud OCR failed; falling back to local result", e)
                mlKitResult
            }
        }

        return mlKitResult
    }
}
