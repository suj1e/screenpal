package com.suj1e.screenpal.ocr

class MlKitOcrProvider : OcrEngine {
    override suspend fun recognize(text: String): String = text
}
