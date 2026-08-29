package com.suj1e.screenpal.ocr

/**
 * OCR 引擎模式：LOCAL 仅端侧 Paddle；CLOUD 仅云端 StepFun；HYBRID 端侧优先、
 * 低置信度回落云端。（原与 OcrEngineFactory 同文件，工厂删除后独立成档。）
 */
enum class OcrMode {
    LOCAL,
    CLOUD,
    HYBRID
}
