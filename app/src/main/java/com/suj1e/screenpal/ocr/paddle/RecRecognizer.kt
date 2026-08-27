package com.suj1e.screenpal.ocr.paddle

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.graphics.Bitmap
import kotlin.math.roundToInt

/**
 * CRNN text-line recognizer: normalizes a line crop to height 48, runs the rec
 * model ([1, T, dict+2] logits), and decodes greedily via CTC (collapse
 * consecutive duplicates, skip blank) against the bundled dictionary.
 *
 * Layout of the RapidOCR PP-OCRv4 rec head: class 0 = CTC blank, classes
 * 1..dict.size = dictionary lines, class dict.size+1 = space.
 */
class RecRecognizer(
    private val sessionProvider: () -> OrtSession?,
    private val dictionary: List<String>
) {

    data class LineText(
        val text: String,
        val confidence: Float
    )

    fun recognizeLine(bitmap: Bitmap): LineText {
        val session = sessionProvider()
            ?: throw IllegalStateException("rec model session not initialized")

        val input = normalizeLine(bitmap)
        val environment = OrtEnvironment.getEnvironment()
        OnnxTensor.createTensor(
            environment,
            java.nio.FloatBuffer.wrap(input.data),
            input.shape
        ).use { tensor ->
            session.run(mapOf(INPUT_NAME to tensor)).use { results ->
                val output = results[0] as OnnxTensor
                // Read the flat buffer (never cast to a fixed-rank array, see
                // the PiperTtsEngine lesson) and derive T from its size.
                val buffer = output.floatBuffer
                val numClasses = dictionary.size + 2
                val timeSteps = buffer.remaining() / numClasses
                val logits = FloatArray(buffer.remaining())
                buffer.get(logits)
                return decodeLogits(logits, timeSteps, numClasses)
            }
        }
    }

    internal fun decodeLogits(
        logits: FloatArray,
        timeSteps: Int,
        numClasses: Int
    ): LineText {
        if (timeSteps <= 0) return LineText("", 0f)

        val blankId = 0
        val spaceId = dictionary.size + 1

        val text = StringBuilder()
        var probabilitySum = 0f
        var selectedSteps = 0
        var previousClass = -1

        for (t in 0 until timeSteps) {
            val offset = t * numClasses
            var bestClass = 0
            var bestScore = Float.NEGATIVE_INFINITY
            for (c in 0 until numClasses) {
                if (logits[offset + c] > bestScore) {
                    bestScore = logits[offset + c]
                    bestClass = c
                }
            }

            // The rec head already emits softmax probabilities
            // (output tensor "softmax_11.tmp_0"), so the max value is the
            // per-step confidence directly.
            if (bestClass != blankId && bestClass != previousClass) {
                text.append(
                    when (bestClass) {
                        spaceId -> " "
                        else -> dictionary.getOrNull(bestClass - 1) ?: ""
                    }
                )
                probabilitySum += bestScore
                selectedSteps++
            }
            previousClass = bestClass
        }

        val confidence = if (selectedSteps > 0) probabilitySum / selectedSteps else 0f
        return LineText(text.toString(), confidence)
    }

    private fun normalizeLine(bitmap: Bitmap): NormalizedInput {
        val ratio = TARGET_HEIGHT.toFloat() / bitmap.height
        val targetWidth = (bitmap.width * ratio).roundToInt()
            .coerceIn(MIN_WIDTH, TARGET_WIDTH)

        val scaled = if (targetWidth != bitmap.width || TARGET_HEIGHT != bitmap.height) {
            Bitmap.createScaledBitmap(bitmap, targetWidth, TARGET_HEIGHT, true)
        } else bitmap

        val pixels = IntArray(TARGET_WIDTH * TARGET_HEIGHT)
        // Draw the scaled bitmap onto a white canvas to right-pad short lines.
        val canvasBitmap = Bitmap.createBitmap(TARGET_WIDTH, TARGET_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(canvasBitmap)
        canvas.drawColor(android.graphics.Color.WHITE)
        canvas.drawBitmap(scaled, 0f, 0f, null)
        canvasBitmap.getPixels(pixels, 0, TARGET_WIDTH, 0, 0, TARGET_WIDTH, TARGET_HEIGHT)
        if (scaled !== bitmap) scaled.recycle()
        canvasBitmap.recycle()

        val data = FloatArray(3 * TARGET_WIDTH * TARGET_HEIGHT)
        val pixelCount = TARGET_WIDTH * TARGET_HEIGHT
        for (i in 0 until pixelCount) {
            val r = (pixels[i] shr 16 and 0xFF) / 255f
            val g = (pixels[i] shr 8 and 0xFF) / 255f
            val b = (pixels[i] and 0xFF) / 255f
            data[i] = (r - 0.5f) / 0.5f
            data[pixelCount + i] = (g - 0.5f) / 0.5f
            data[2 * pixelCount + i] = (b - 0.5f) / 0.5f
        }
        return NormalizedInput(data, longArrayOf(1, 3, TARGET_HEIGHT.toLong(), TARGET_WIDTH.toLong()))
    }

    private data class NormalizedInput(val data: FloatArray, val shape: LongArray)

    companion object {
        const val INPUT_NAME = "x"
        const val TARGET_HEIGHT = 48
        const val TARGET_WIDTH = 320
        const val MIN_WIDTH = 16

        /** Reads dictionary lines; blanks (newlines) never produce entries. */
        fun loadDictionary(stream: java.io.InputStream): List<String> =
            stream.bufferedReader(Charsets.UTF_8).readLines().filter { it.isNotEmpty() }
    }
}
