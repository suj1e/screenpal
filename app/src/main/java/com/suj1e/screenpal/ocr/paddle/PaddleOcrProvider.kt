package com.suj1e.screenpal.ocr.paddle

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import com.suj1e.screenpal.ocr.OcrEngine
import com.suj1e.screenpal.ocr.OcrResult
import com.suj1e.screenpal.ocr.TextBlock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.nio.FloatBuffer

/**
 * End-to-end PP-OCR pipeline provider (det -> per-line cls/rec) backed by the
 * bundled ONNX models. Lazily initialized as a process-wide singleton because
 * ONNX session creation can take seconds.
 */
class PaddleOcrProvider private constructor(
    private val context: Context
) : OcrEngine {

    private val loader = AssetModelLoader(context)
    private val detPreProcessor = DetPreProcessor()
    private val dbPostProcessor = DbPostProcessor()
    private val clsProcessor = ClsProcessor()

    private val environment: OrtEnvironment by lazy { OrtEnvironment.getEnvironment() }

    private val initMutex = kotlinx.coroutines.sync.Mutex()

    @Volatile
    private var initialized = false

    private var detSession: OrtSession? = null
    private var clsSession: OrtSession? = null
    private var recSession: OrtSession? = null
    private var recognizer: RecRecognizer? = null

    private suspend fun ensureInitialized() {
        if (initialized) return
        initMutex.withLock {
            if (initialized) return
            // Session creation can take 10s+ on cold start; keep it off
            // whatever thread first triggers recognition.
            withContext(Dispatchers.IO) {
                val dict = loader.ensureOcrModel(DICT_FILE).inputStream().use {
                    RecRecognizer.loadDictionary(it)
                }
                recognizer = RecRecognizer({ recSession }, dict)
                detSession = createSession(loader.ensureOcrModel(DET_FILE))
                clsSession = createSession(loader.ensureOcrModel(CLS_FILE))
                recSession = createSession(loader.ensureOcrModel(REC_FILE))
            }
            initialized = true
        }
    }

    private fun createSession(modelFile: java.io.File): OrtSession =
        environment.createSession(modelFile.absolutePath, OrtSession.SessionOptions())

    override suspend fun recognize(bitmap: Bitmap): OcrResult = withContext(Dispatchers.Default) {
        ensureInitialized()
        val detSession = detSession ?: throw IllegalStateException("det model not initialized")

        // 1. Detect text regions on a downscaled copy of the input.
        val detInput = detPreProcessor.process(bitmap)
        val probability = runDet(detSession, detInput)
        val boxes = dbPostProcessor.process(
            probability,
            detInput.width,
            detInput.height,
            ratioX = detInput.ratioX,
            ratioY = detInput.ratioY,
            origWidth = bitmap.width,
            origHeight = bitmap.height
        )

        if (boxes.isEmpty()) return@withContext OcrResult("", 0f, emptyList())

        // 2. Recognize each line (orientation check + CRNN).
        val recognizer = recognizer ?: throw IllegalStateException("rec model not initialized")
        val clsSession = clsSession
        val blocks = mutableListOf<TextBlock>()
        for (box in boxes) {
            val crop = cropLine(bitmap, box) ?: continue
            val oriented = if (clsSession != null && classifyRotated(clsSession, crop)) {
                val rotated = clsProcessor.rotate180(crop)
                if (rotated !== crop) crop.recycle()
                rotated
            } else crop

            val line = try {
                recognizer.recognizeLine(oriented)
            } finally {
                oriented.recycle()
            }
            if (line.text.isBlank()) continue
            blocks += TextBlock(line.text, line.confidence, toRect(box))
        }

        val average = if (blocks.isNotEmpty()) {
            blocks.map { it.confidence }.average().toFloat()
        } else 0f
        OcrResult(
            text = blocks.joinToString("\n") { it.text },
            confidence = average,
            blocks = blocks
        )
    }

    /** Runs the det head and returns the flat [1,1,H,W] probability map. */
    private fun runDet(session: OrtSession, input: DetPreProcessor.DetInput): FloatArray {
        val shape = longArrayOf(1, 3, input.height.toLong(), input.width.toLong())
        OnnxTensor.createTensor(environment, FloatBuffer.wrap(input.data), shape).use { tensor ->
            session.run(mapOf(INPUT_X to tensor)).use { results ->
                val output = results[0] as OnnxTensor
                val buffer = output.floatBuffer
                return FloatArray(buffer.remaining()).also { buffer.get(it) }
            }
        }
    }

    /** Returns true when the classifier thinks the line is upside down. */
    private fun classifyRotated(session: OrtSession, line: Bitmap): Boolean {
        val input = normalizeCls(line)
        OnnxTensor.createTensor(environment, FloatBuffer.wrap(input.first), input.second).use { tensor ->
            session.run(mapOf(INPUT_X to tensor)).use { results ->
                val output = results[0] as OnnxTensor
                val probs = FloatArray(output.floatBuffer.remaining())
                output.floatBuffer.get(probs)
                return clsProcessor.needsRotation(probs)
            }
        }
    }

    private fun normalizeCls(line: Bitmap): Pair<FloatArray, LongArray> {
        val width = minOf(CLS_WIDTH, maxOf(CLS_MIN_WIDTH, line.width))
        val scaled = if (width != line.width || CLS_HEIGHT != line.height) {
            Bitmap.createScaledBitmap(line, width, CLS_HEIGHT, true)
        } else line
        val pixels = IntArray(width * CLS_HEIGHT)
        scaled.getPixels(pixels, 0, width, 0, 0, width, CLS_HEIGHT)
        if (scaled !== line) scaled.recycle()

        val pixelCount = width * CLS_HEIGHT
        val data = FloatArray(pixelCount * 3)
        for (i in 0 until pixelCount) {
            val r = (pixels[i] shr 16 and 0xFF) / 255f
            val g = (pixels[i] shr 8 and 0xFF) / 255f
            val b = (pixels[i] and 0xFF) / 255f
            data[i] = (r - 0.5f) / 0.5f
            data[pixelCount + i] = (g - 0.5f) / 0.5f
            data[2 * pixelCount + i] = (b - 0.5f) / 0.5f
        }
        return data to longArrayOf(1, 3, CLS_HEIGHT.toLong(), width.toLong())
    }

    private fun cropLine(bitmap: Bitmap, box: DbPostProcessor.DetectedBox): Bitmap? {
        if (bitmap.isRecycled) return null
        val left = box.left.coerceIn(0, bitmap.width - 1)
        val top = box.top.coerceIn(0, bitmap.height - 1)
        val width = (box.right - box.left + 1).coerceIn(1, bitmap.width - left)
        val height = (box.bottom - box.top + 1).coerceIn(1, bitmap.height - top)
        return Bitmap.createBitmap(bitmap, left, top, width, height)
    }

    companion object {
        const val DET_FILE = "det.onnx"
        const val CLS_FILE = "cls.onnx"
        const val REC_FILE = "rec.onnx"
        const val DICT_FILE = "ppocr_keys_v1.txt"
        const val INPUT_X = "x"
        const val CLS_HEIGHT = 48
        const val CLS_WIDTH = 192
        const val CLS_MIN_WIDTH = 16

        @Volatile
        private var instance: PaddleOcrProvider? = null

        fun getInstance(context: Context): PaddleOcrProvider =
            instance ?: synchronized(this) {
                instance ?: PaddleOcrProvider(context.applicationContext).also { instance = it }
            }

        /** DetectedBox is inclusive; android.graphics.Rect bottom/right are exclusive. */
        fun toRect(box: DbPostProcessor.DetectedBox): Rect =
            Rect(box.left, box.top, box.right + 1, box.bottom + 1)
    }
}
