package com.suj1e.screenpal.ocr.paddle

import android.graphics.Bitmap

/**
 * Text-line orientation classifier: decides whether a cropped text line is
 * upside down (180 deg) and, if so, provides the corrected bitmap.
 */
class ClsProcessor {

    /** True when the classifier predicts the line is rotated by 180 degrees. */
    fun needsRotation(probs: FloatArray): Boolean {
        require(probs.size >= 2) { "cls output must have 2 classes" }
        return probs[1] > probs[0]
    }

    /** Returns a copy of [bitmap] rotated by 180 degrees. */
    fun rotate180(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val src = IntArray(width * height)
        bitmap.getPixels(src, 0, width, 0, 0, width, height)
        val dst = IntArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                dst[(height - 1 - y) * width + (width - 1 - x)] = src[y * width + x]
            }
        }
        return Bitmap.createBitmap(width, height, bitmap.config ?: Bitmap.Config.ARGB_8888)
            .apply { setPixels(dst, 0, width, 0, 0, width, height) }
    }
}
