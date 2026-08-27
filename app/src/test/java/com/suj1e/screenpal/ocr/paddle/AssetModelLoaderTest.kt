package com.suj1e.screenpal.ocr.paddle

import android.app.Application
import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AssetModelLoaderTest {

    private lateinit var context: Context
    private lateinit var loader: AssetModelLoader

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication() as Application
        loader = AssetModelLoader(context)
    }

    @Test
    fun ensureOcrModel_copiesAssetIntoFilesDirCache() {
        val file = loader.ensureOcrModel("ppocr_keys_v1.txt")

        val expected = File(context.filesDir, "ocr/ppocr_keys_v1.txt")
        assertEquals(expected.absolutePath, file.absolutePath)
        assertTrue(file.isFile)

        val expectedBytes = context.assets.open("ocr/ppocr_keys_v1.txt").use { it.readBytes() }
        assertEquals(expectedBytes.size.toLong(), file.length())
        assertTrue(file.readBytes().contentEquals(expectedBytes))
    }

    @Test
    fun ensureOcrModel_reusesCacheWithoutRecopying() {
        val file = loader.ensureOcrModel("ppocr_keys_v1.txt")
        val firstCopy = file.readBytes()

        // Simulate an old cache entry so a re-copy would refresh the timestamp.
        file.setLastModified(1_000L)

        loader.ensureOcrModel("ppocr_keys_v1.txt")

        assertEquals(1_000L, file.lastModified())
        assertTrue(file.readBytes().contentEquals(firstCopy))
    }

    @Test
    fun ensureOcrModel_rewritesCacheWhenLengthDiffers() {
        val file = loader.ensureOcrModel("ppocr_keys_v1.txt")
        file.writeBytes(ByteArray(file.length().toInt() - 1))

        loader.ensureOcrModel("ppocr_keys_v1.txt")

        val expectedBytes = context.assets.open("ocr/ppocr_keys_v1.txt").use { it.readBytes() }
        assertTrue(file.readBytes().contentEquals(expectedBytes))
    }

    @Test
    fun ensureOcrModel_rejectsPathTraversal() {
        assertThrows(IllegalArgumentException::class.java) {
            loader.ensureOcrModel("../evil.onnx")
        }
        assertThrows(IllegalArgumentException::class.java) {
            loader.ensureOcrModel("sub/dir/model.onnx")
        }
    }
}
