package com.suj1e.screenpal.ocr

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions

/**
 * On-device OCR using the bundled ML Kit Chinese model, so recognition works
 * fully offline and on images without Google Play services.
 */
class MlKitOcrProvider : OcrEngine {

    private val recognizer by lazy {
        TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    }

    override suspend fun recognize(bitmap: Bitmap): OcrResult {
        // Tasks.await blocks; callers already run us off the main thread.
        val visionText = Tasks.await(recognizer.process(InputImage.fromBitmap(bitmap, 0)))

        val blocks = visionText.textBlocks.map { block ->
            val lineConfidences = block.lines.mapNotNull { it.confidence }
            TextBlock(
                text = block.text,
                confidence = if (lineConfidences.isNotEmpty()) {
                    lineConfidences.average().toFloat()
                } else DEFAULT_CONFIDENCE,
                boundingBox = block.boundingBox ?: Rect()
            )
        }
        val avgConfidence = if (blocks.isNotEmpty()) {
            blocks.map { it.confidence }.average().toFloat()
        } else 0f

        return OcrResult(
            text = visionText.text,
            confidence = avgConfidence,
            blocks = blocks
        )
    }

    companion object {
        const val DEFAULT_CONFIDENCE = 0.9f
    }
}
