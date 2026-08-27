package com.suj1e.screenpal.ocr

import android.graphics.Bitmap
import io.mockk.mockk
import org.junit.Assert.assertEquals
import kotlinx.coroutines.test.runTest
import org.junit.Test

class HybridOcrEngineTest {

    @Test
    fun hybrid_confidenceAboveThreshold_returnsMlKitResult() = kotlinx.coroutines.runBlocking {
        val mlKitProvider = FakeLocalProvider(OcrResult("text1", 0.9f, emptyList()))
        val hybrid = HybridOcrEngine(mlKitProvider, null, confidenceThreshold = 0.75f)

        val result = hybrid.recognize(mockk<Bitmap>(relaxed = true))
        assertEquals("text1", result.text)
    }

    @Test
    fun hybrid_confidenceBelowThreshold_callsCloud() = kotlinx.coroutines.runBlocking {
        val mlKitProvider = FakeLocalProvider(OcrResult("text1", 0.5f, emptyList()))
        val cloudProvider = FakeCloudProvider(OcrResult("text2", 0.95f, emptyList()))
        val hybrid = HybridOcrEngine(mlKitProvider, cloudProvider, confidenceThreshold = 0.75f)

        val result = hybrid.recognize(mockk<Bitmap>(relaxed = true))
        assertEquals("text2", result.text)
    }

    @Test
    fun hybrid_cloudNull_returnsMlKitResult() = kotlinx.coroutines.runBlocking {
        val mlKitProvider = FakeLocalProvider(OcrResult("text1", 0.5f, emptyList()))
        val hybrid = HybridOcrEngine(mlKitProvider, null, confidenceThreshold = 0.75f)

        val result = hybrid.recognize(mockk<Bitmap>(relaxed = true))
        assertEquals("text1", result.text)
    }


    @Test
    fun `cloud failure falls back to local result`() = runTest {
        val local = FakeLocalProvider(OcrResult("本地结果", 0.5f, emptyList()))
        val cloud = object : OcrEngine {
            override suspend fun recognize(bitmap: Bitmap): OcrResult =
                throw IllegalStateException("429 quota")
        }
        val engine = HybridOcrEngine(local, cloud, confidenceThreshold = 0.75f)

        val result = engine.recognize(mockk<Bitmap>(relaxed = true))

        assertEquals("本地结果", result.text)
    }
}

class FakeLocalProvider(private val result: OcrResult) : OcrEngine {
    override suspend fun recognize(bitmap: Bitmap): OcrResult = result
}

class FakeCloudProvider(private val result: OcrResult) : OcrEngine {
    override suspend fun recognize(bitmap: Bitmap): OcrResult = result

}
