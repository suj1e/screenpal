package com.suj1e.screenpal.ocr.paddle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest

/**
 * Locks the bundled PP-OCR ONNX assets: presence, sane sizes, dictionary line
 * count, and upstream sha256 (guards against model/dictionary version drift,
 * which would silently produce garbled CTC output).
 */
class OcrModelAssetsTest {

    private val assetsDir = File("src/main/assets/ocr")

    private fun asset(name: String): File = File(assetsDir, name)

    private fun sha256(file: File): String =
        MessageDigest.getInstance("SHA-256").digest(file.readBytes())
            .joinToString("") { "%02x".format(it) }

    @Test
    fun all_four_asset_files_exist() {
        listOf("det.onnx", "cls.onnx", "rec.onnx", "ppocr_keys_v1.txt").forEach {
            assertTrue("missing asset: $it", asset(it).isFile)
        }
    }

    @Test
    fun model_sizes_within_packaging_budget() {
        val detBytes = asset("det.onnx").length()
        val clsBytes = asset("cls.onnx").length()
        val recBytes = asset("rec.onnx").length()

        assertTrue("det.onnx expected < 8MB but was $detBytes", detBytes in 1 until 8L * 1024 * 1024)
        assertTrue("cls.onnx expected < 2MB but was $clsBytes", clsBytes in 1 until 2L * 1024 * 1024)
        assertTrue("rec.onnx expected < 15MB but was $recBytes", recBytes in 1 until 15L * 1024 * 1024)
    }

    @Test
    fun dictionary_has_6623_entries() {
        val lines = asset("ppocr_keys_v1.txt").readText().splitToSequence('\n')
            .filter { it.isNotEmpty() }
            .toList()
        assertEquals(6623, lines.size)
        assertEquals(6623, lines.toSet().size)
    }

    @Test
    fun assets_match_locked_upstream_sha256() {
        // Sources: hf-mirror.com/SWHL/RapidOCR (PP-OCRv4 det/rec, PP-OCRv1 cls)
        // and RapidAI/RapidOCR v2.0.0 ppocr_keys_v1.txt. Values are the upstream
        // LFS/CDN digests verified at integration time.
        assertEquals(
            "d2a7720d45a54257208b1e13e36a8479894cb74155a5efe29462512d42f49da9",
            sha256(asset("det.onnx"))
        )
        assertEquals(
            "e47acedf663230f8863ff1ab0e64dd2d82b838fceb5957146dab185a89d6215c",
            sha256(asset("cls.onnx"))
        )
        assertEquals(
            "48fc40f24f6d2a207a2b1091d3437eb3cc3eb6b676dc3ef9c37384005483683b",
            sha256(asset("rec.onnx"))
        )
        assertEquals(
            "28b2362ad4ab2dc38769aa72feb535e3a9ddb3fd2a7585a05920e6393b1dc7f7",
            sha256(asset("ppocr_keys_v1.txt"))
        )
    }
}
