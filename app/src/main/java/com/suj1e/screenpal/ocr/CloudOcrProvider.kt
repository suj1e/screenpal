package com.suj1e.screenpal.ocr

class CloudOcrProvider : OcrEngine {
    override suspend fun recognize(text: String): String = text
}
