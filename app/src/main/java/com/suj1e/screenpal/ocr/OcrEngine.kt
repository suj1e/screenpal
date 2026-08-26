package com.suj1e.screenpal.ocr

interface OcrEngine {
    suspend fun recognize(text: String): String
}
