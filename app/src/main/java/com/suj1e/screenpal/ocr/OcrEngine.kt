package com.suj1e.screenpal.ocr

import android.graphics.Bitmap
import android.graphics.Rect

data class OcrResult(
    val text: String,
    val confidence: Float,
    val blocks: List<TextBlock>
)

data class TextBlock(
    val text: String,
    val confidence: Float,
    val boundingBox: Rect
)

interface OcrEngine {
    suspend fun recognize(bitmap: Bitmap): OcrResult
}
