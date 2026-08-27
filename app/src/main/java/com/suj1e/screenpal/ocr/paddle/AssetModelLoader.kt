package com.suj1e.screenpal.ocr.paddle

import android.content.Context
import java.io.File

/**
 * Copies OCR model files bundled under `assets/ocr/` into a filesDir cache so
 * that ONNX Runtime (which needs real file paths) can load them. Copies are
 * skipped when the cached file already has the expected length.
 */
class AssetModelLoader(private val context: Context) {

    fun ensureOcrModel(fileName: String): File {
        require(fileName.isNotBlank() && fileName == File(fileName).name) {
            "Illegal model file name: $fileName"
        }

        val target = File(File(context.filesDir, OCR_ASSET_DIR), fileName)
        if (target.isFile && target.length() > 0) {
            // openFd() would fail here: assets are deflate-compressed in the APK
            // unless listed in noCompress, so measure via the stream instead.
            val expectedLength = context.assets.open(assetPath(fileName)).use { it.available() }
            if (target.length() == expectedLength.toLong()) {
                return target
            }
        }

        context.assets.open(assetPath(fileName)).use { input ->
            File(context.filesDir, OCR_ASSET_DIR).mkdirs()
            val tmp = File(target.parentFile, "${fileName}.tmp")
            tmp.outputStream().use { output -> input.copyTo(output) }
            if (target.exists()) target.delete()
            if (!tmp.renameTo(target)) {
                tmp.copyTo(target, overwrite = true)
                tmp.delete()
            }
        }
        return target
    }

    private fun assetPath(fileName: String) = "$OCR_ASSET_DIR/$fileName"

    companion object {
        const val OCR_ASSET_DIR = "ocr"
    }
}
