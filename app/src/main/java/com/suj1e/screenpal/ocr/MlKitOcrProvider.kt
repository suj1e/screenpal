package com.suj1e.screenpal.ocr

import android.graphics.Bitmap

class MlKitOcrProvider : OcrEngine {

    override suspend fun recognize(bitmap: Bitmap): OcrResult {
        // ML Kit integration placeholder
        // Full implementation requires proper InputImage creation
        // and TextRecognizer configuration
        return OcrResult(
            text = "",
            confidence = 0f,
            blocks = emptyList()
        )
    }
}
