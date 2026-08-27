package com.suj1e.screenpal.ocr.paddle

import android.graphics.Bitmap
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Prepares the det model input from a screenshot crop: scales the longest side
 * down to [MAX_SIDE_LIMIT] (keeping both sides multiples of 32), then produces
 * a normalized RGB FloatArray in NCHW layout (R plane, then G, then B).
 */
class DetPreProcessor {

    data class DetInput(
        val data: FloatArray,
        val width: Int,
        val height: Int,
        /** scaled / original ratios used to map detection boxes back. */
        val ratioX: Float,
        val ratioY: Float
    )

    fun process(bitmap: Bitmap): DetInput {
        check(!bitmap.isRecycled) { "DetPreProcessor input bitmap is recycled" }

        val (width, height) = computeResize(bitmap.width, bitmap.height)

        val scaled = if (width != bitmap.width || height != bitmap.height) {
            Bitmap.createScaledBitmap(bitmap, width, height, true)
        } else bitmap

        val pixelCount = width * height
        val pixels = IntArray(pixelCount)
        scaled.getPixels(pixels, 0, width, 0, 0, width, height)
        if (scaled !== bitmap) scaled.recycle()

        val data = FloatArray(pixelCount * 3)
        for (i in 0 until pixelCount) {
            val r = (pixels[i] shr 16 and 0xFF) / 255f
            val g = (pixels[i] shr 8 and 0xFF) / 255f
            val b = (pixels[i] and 0xFF) / 255f
            data[i] = (r - MEAN_R) / STD_R
            data[pixelCount + i] = (g - MEAN_G) / STD_G
            data[2 * pixelCount + i] = (b - MEAN_B) / STD_B
        }

        return DetInput(
            data = data,
            width = width,
            height = height,
            ratioX = width.toFloat() / bitmap.width,
            ratioY = height.toFloat() / bitmap.height
        )
    }

    companion object {
        const val MAX_SIDE_LIMIT = 960
        const val MULTIPLE_OF = 32

        const val MEAN_R = 0.485f
        const val MEAN_G = 0.456f
        const val MEAN_B = 0.406f
        const val STD_R = 0.229f
        const val STD_G = 0.224f
        const val STD_B = 0.225f

        /** Returns the (width, height) the bitmap should be resized to. */
        fun computeResize(width: Int, height: Int): Pair<Int, Int> {
            val longest = max(width, height)
            val scale = if (longest > MAX_SIDE_LIMIT) MAX_SIDE_LIMIT.toFloat() / longest else 1f
            return roundToMultiple(width * scale) to roundToMultiple(height * scale)
        }

        private fun roundToMultiple(value: Float): Int =
            max(1, (value / MULTIPLE_OF).roundToInt()) * MULTIPLE_OF
    }
}
