package com.suj1e.screenpal.overlay

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.suj1e.screenpal.ScreenPalApplication
import com.suj1e.screenpal.ocr.CloudOcrConfig
import com.suj1e.screenpal.ocr.CloudOcrProvider
import com.suj1e.screenpal.ocr.HybridOcrEngine
import com.suj1e.screenpal.ocr.OcrEngine
import com.suj1e.screenpal.ocr.OcrMode
import com.suj1e.screenpal.ocr.paddle.PaddleOcrProvider
import com.suj1e.screenpal.translate.BroadcastOutcome
import com.suj1e.screenpal.translate.ChineseBroadcastPipeline
import com.suj1e.screenpal.translate.DoubaoTranslateClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SelectionOverlayActivity : ComponentActivity() {
    companion object {
        const val EXTRA_SCREENSHOT_URI = "extra_screenshot_uri"
        const val EXTRA_SELECTION_RECT = "extra_selection_rect"
        const val TAG = "SelectionOverlay"

        /** Minimum distance between two sampled lasso points. */
        internal const val MIN_SAMPLE_DISTANCE_DP = 8f

        /** Width of the visible purple lasso trace. */
        internal const val STROKE_LINE_DP = 4f

        /** Width of the mask hole punched along the lasso trace. */
        internal const val HOLE_STROKE_DP = 24f

        /** Minimum bounding-box size (width OR height) for a valid selection. */
        internal const val MIN_SELECTION_SIZE_DP = 48f

        /** Delay before the first-entry hint fades out. */
        internal const val HINT_DELAY_MS = 3000L

        /**
         * Pure sampling filter for lasso MOVE events: [candidate] joins the
         * stroke only when it is at least [minDistancePx] away from the last
         * sampled point (an empty stroke always accepts its first point).
         * Returns a new list; the input is never mutated. Exposed for JVM
         * unit tests.
         */
        internal fun filterStrokePoints(
            existing: List<PointF>,
            candidate: PointF,
            minDistancePx: Float
        ): List<PointF> {
            val last = existing.lastOrNull() ?: return listOf(candidate)
            val dx = candidate.x - last.x
            val dy = candidate.y - last.y
            return if (dx * dx + dy * dy >= minDistancePx * minDistancePx) {
                existing + candidate
            } else {
                existing
            }
        }

        /**
         * Pure bounding box of a sampled stroke; null for an empty stroke
         * (a single point yields a zero-size box, rejected by the size gate).
         */
        internal fun computeBounds(points: List<PointF>): RectF? {
            if (points.isEmpty()) return null
            var left = Float.MAX_VALUE
            var top = Float.MAX_VALUE
            var right = -Float.MAX_VALUE
            var bottom = -Float.MAX_VALUE
            for (p in points) {
                if (p.x < left) left = p.x
                if (p.y < top) top = p.y
                if (p.x > right) right = p.x
                if (p.y > bottom) bottom = p.y
            }
            return RectF(left, top, right, bottom)
        }

        /**
         * UP gate: a stroke is valid when its bounding box is at least
         * [minSizePx] wide OR tall, so thin strips still qualify while a
         * single tap (zero size) does not.
         */
        internal fun isSelectionLargeEnough(bounds: RectF, minSizePx: Float): Boolean =
            bounds.width() >= minSizePx || bounds.height() >= minSizePx

        /**
         * 结果卡 meta 标注映射：翻译发生 → 「AI 转译」；降级 → 「翻译不可用」；
         * 中文直读 → 不标注。Exposed for JVM unit tests.
         */
        internal fun metaAnnotation(outcome: BroadcastOutcome): String? = when (outcome) {
            BroadcastOutcome.Translated -> " · AI 转译"
            BroadcastOutcome.FallbackOriginal -> " · 翻译不可用"
            BroadcastOutcome.Direct -> null
        }
    }

    private lateinit var screenshotBitmap: Bitmap
    private lateinit var selectionView: SelectionView
    private lateinit var resultCard: LinearLayout
    private lateinit var resultText: TextView
    private lateinit var resultMeta: TextView
    private val viewModel = SelectionViewModel()
    private var lastRecognizedText: String = ""
    private var hintText: TextView? = null
    private var hintFadeRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // NOTE: do NOT set FLAG_NOT_FOCUSABLE here (a leftover from the
        // floating-window service requirements) — a focusable activity must
        // keep receiving BACK so the user can always leave this screen.

        val screenshotUri = intent.getParcelableExtra<Uri>(EXTRA_SCREENSHOT_URI)
        screenshotBitmap = screenshotUri?.let { uri ->
            contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input)
            }
        } ?: Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)

        selectionView = SelectionView(this)
        resultCard = buildResultCard()

        val root = android.widget.FrameLayout(this).apply {
            addView(selectionView, android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT
            ))
            addView(resultCard, android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            ).apply { setMargins(0, 0, 0, 0) })
        }
        addFirstEntryHint(root)
        setContentView(root)
    }

    /** First-entry coaching hint: "用手指圈出要朗读的文字", fades out after 3s. */
    private fun addFirstEntryHint(root: android.widget.FrameLayout) {
        val hint = TextView(this).apply {
            text = "用手指圈出要朗读的文字"
            textSize = 15f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#66000000"))
            setPadding(40, 24, 40, 24)
            gravity = Gravity.CENTER
        }
        root.addView(hint, android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
            android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER_HORIZONTAL or Gravity.TOP
        ).apply { topMargin = 120 })

        hintText = hint
        val fade = Runnable {
            hint.animate().alpha(0f).setDuration(400).withEndAction {
                hint.visibility = View.GONE
            }
        }
        hintFadeRunnable = fade
        hint.postDelayed(fade, HINT_DELAY_MS)
    }

    /**
     * Runs when the user confirms a selection rectangle:
     * crop -> OCR (mode from settings) -> broadcast (translate-to-Chinese when
     * needed, falling back to the original text) -> show result card.
     */
    internal fun onSelectionConfirmed(screenRect: Rect) {
        viewModel.selectionRect = screenRect
        val cropRect = viewModel.calculateCropRect(
            screenRect,
            screenshotBitmap.width,
            screenshotBitmap.height,
            selectionView.width.coerceAtLeast(1),
            selectionView.height.coerceAtLeast(1)
        )
        val cropped = cropScreenshot(cropRect) ?: run {
            Toast.makeText(this, "裁剪失败，请重新框选", Toast.LENGTH_SHORT).show()
            return
        }

        val app = application as ScreenPalApplication
        lifecycleScope.launch {
            try {
                val engine = withContext(Dispatchers.IO) { resolveOcrEngine(app) }
                resultCard.visibility = View.VISIBLE
                resultText.text = "识别中…"
                resultMeta.text = ""

                val result = try {
                    withContext(Dispatchers.Default) { engine.recognize(cropped) }
                } finally {
                    cropped.recycle()
                }

                lastRecognizedText = result.text
                // 卡片先出 OCR 原文；翻译完成后把主显更新为实际播报文本。
                resultText.text = result.text.ifBlank { "（未识别到文字）" }
                resultMeta.text = "置信度 %.0f%% · ${result.blocks.size} 个文本块".format(result.confidence * 100)

                if (result.text.isNotBlank()) {
                    try {
                        val settings = app.settingsRepository.userSettings.first()
                        val pipeline = ChineseBroadcastPipeline(
                            DoubaoTranslateClient(settings.cloudApiKey)
                        )
                        val outcome = pipeline.broadcast(
                            result.text,
                            app.ttsManager,
                            translationEnabled = settings.translationEnabled
                        )
                        // 主显播报文本（译文或降级原文），标注后附原文小字供校对。
                        metaAnnotation(outcome)?.let { resultMeta.append(it) }
                        pipeline.lastSpokenText?.let { spoken ->
                            if (spoken != result.text) {
                                resultText.text = spoken
                                val original = result.text.take(120) + if (result.text.length > 120) "…" else ""
                                resultMeta.text = resultMeta.text.toString() + " · 原文：" + original
                            }
                        }
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // Keep the recognized text visible; just annotate that
                        // speech is unavailable (e.g. device has no TTS engine).
                        Log.w(TAG, "TTS playback failed", e)
                        resultMeta.append(" · 播报不可用")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "OCR/TTS pipeline failed", e)
                resultCard.visibility = View.VISIBLE
                resultText.text = "识别失败：${e.message ?: "未知错误"}"
                resultMeta.text = ""
            }
        }
    }

    /** Choose LOCAL/CLOUD/HYBRID per settings; degrade to LOCAL if cloud config missing. */
    private suspend fun resolveOcrEngine(app: ScreenPalApplication): OcrEngine {
        val settings = app.settingsRepository.userSettings.first()
        val mode = runCatching { OcrMode.valueOf(settings.ocrMode.uppercase()) }
            .getOrDefault(OcrMode.HYBRID)
        val apiKey = settings.cloudApiKey

        return withContext(Dispatchers.Default) {
            when (mode) {
                OcrMode.LOCAL -> PaddleOcrProvider.getInstance(app)
                OcrMode.CLOUD -> if (apiKey.isBlank()) {
                    Log.w(TAG, "Cloud OCR without API key; degrading to local")
                    PaddleOcrProvider.getInstance(app)
                } else {
                    CloudOcrProvider(CloudOcrConfig(arkApiKey = apiKey))
                }
                OcrMode.HYBRID -> HybridOcrEngine(
                    PaddleOcrProvider.getInstance(app),
                    cloudProvider = apiKey.takeIf { it.isNotBlank() }?.let {
                        CloudOcrProvider(CloudOcrConfig(arkApiKey = it))
                    },
                    confidenceThreshold = 0.75f
                )
            }
        }
    }

    private fun cropScreenshot(cropRect: Rect): Bitmap? {
        if (screenshotBitmap.isRecycled || cropRect.width() <= 0 || cropRect.height() <= 0) return null
        return Bitmap.createBitmap(
            screenshotBitmap,
            cropRect.left.coerceIn(0, screenshotBitmap.width - 1),
            cropRect.top.coerceIn(0, screenshotBitmap.height - 1),
            cropRect.width().coerceAtMost(screenshotBitmap.width - cropRect.left.coerceIn(0, screenshotBitmap.width - 1)),
            cropRect.height().coerceAtMost(screenshotBitmap.height - cropRect.top.coerceIn(0, screenshotBitmap.height - 1))
        )
    }

    private fun buildResultCard(): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#F2141419"))
            setPadding(48, 32, 48, 40)
            visibility = View.GONE
        }

        resultText = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 16f
            maxLines = 8
            setTextIsSelectable(true)
        }
        resultMeta = TextView(this).apply {
            setTextColor(Color.parseColor("#99FFFFFF"))
            textSize = 12f
        }

        fun actionButton(label: String, onClick: () -> Unit): Button =
            Button(this).apply {
                text = label
                textSize = 13f
                setOnClickListener { onClick() }
            }

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        actions.addView(actionButton("停止播报") { (application as ScreenPalApplication).ttsManager.stop() })
        actions.addView(actionButton("复制") {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("念念", lastRecognizedText))
            Toast.makeText(this@SelectionOverlayActivity, "已复制", Toast.LENGTH_SHORT).show()
        })
        actions.addView(actionButton("重新框选") {
            resultCard.visibility = View.GONE
            selectionView.resetForReselection()
        })
        actions.addView(actionButton("完成") { finish() })

        card.addView(resultText)
        card.addView(resultMeta)
        card.addView(actions)
        return card
    }

    private inner class SelectionView(context: android.content.Context) : View(context) {
        private val overlayPaint = Paint().apply {
            color = Color.parseColor("#8F000000")
            style = Paint.Style.FILL
        }

        // Punches the semi-transparent mask away along the lasso trace so the
        // screenshot underneath stays fully visible (bright band).
        private val holePaint = Paint().apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = HOLE_STROKE_DP * resources.displayMetrics.density
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_OUT)
        }

        private val strokePaint = Paint().apply {
            color = Color.parseColor("#FF7B68EE")
            style = Paint.Style.STROKE
            strokeWidth = STROKE_LINE_DP * resources.displayMetrics.density
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            isAntiAlias = true
        }

        /** Sampled lasso points (>= [MIN_SAMPLE_DISTANCE_DP] apart). */
        private val strokePoints = mutableListOf<PointF>()

        /** Smooth path rebuilt from [strokePoints] via the midpoint quadTo technique. */
        private val strokePath = Path()
        private var isSelecting = false

        fun resetForReselection() {
            isSelecting = false
            clearStroke()
        }

        private fun clearStroke() {
            strokePoints.clear()
            strokePath.reset()
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            val srcRect = Rect(0, 0, screenshotBitmap.width, screenshotBitmap.height)
            val dstRect = Rect(0, 0, width, height)
            canvas.drawBitmap(screenshotBitmap, srcRect, dstRect, null)

            // Single offscreen layer: fill the mask, punch the hole along the
            // trace with DST_OUT, then paint the purple trace on top. One
            // saveLayer per frame, no per-pixel bitmap work.
            val saveCount = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), overlayPaint)
            if (strokePoints.isNotEmpty()) {
                canvas.drawPath(strokePath, holePaint)
                canvas.drawPath(strokePath, strokePaint)
            }
            canvas.restoreToCount(saveCount)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    isSelecting = true
                    strokePoints.clear()
                    strokePath.reset()
                    addSampledPoint(event.x, event.y)
                    invalidate()
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (isSelecting) {
                        addSampledPoint(event.x, event.y)
                        invalidate()
                    }
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    if (isSelecting) {
                        isSelecting = false
                        finishStroke()
                    }
                    return true
                }
                MotionEvent.ACTION_CANCEL -> {
                    isSelecting = false
                    clearStroke()
                    return true
                }
                else -> return super.onTouchEvent(event)
            }
        }

        /**
         * UP judgment: confirm via bounding box when it clears the minimum
         * size (48dp wide OR tall); otherwise buzz and clear so the user can
         * redraw. A single tap has a zero-size box and lands in the reject
         * branch, so it never triggers recognition.
         */
        private fun finishStroke() {
            val bounds = computeBounds(strokePoints)
            val minSizePx = MIN_SELECTION_SIZE_DP * resources.displayMetrics.density
            if (bounds != null && isSelectionLargeEnough(bounds, minSizePx)) {
                onSelectionConfirmed(
                    Rect(
                        bounds.left.toInt(), bounds.top.toInt(),
                        bounds.right.toInt(), bounds.bottom.toInt()
                    )
                )
            } else {
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                clearStroke()
            }
        }

        private fun addSampledPoint(rawX: Float, rawY: Float) {
            // Clamp out-of-screen coordinates into the view (design edge case).
            val x = rawX.coerceIn(0f, width.coerceAtLeast(0).toFloat())
            val y = rawY.coerceIn(0f, height.coerceAtLeast(0).toFloat())
            val minDistancePx = MIN_SAMPLE_DISTANCE_DP * resources.displayMetrics.density
            val filtered = filterStrokePoints(strokePoints, PointF(x, y), minDistancePx)
            if (filtered.size != strokePoints.size) {
                strokePoints.clear()
                strokePoints.addAll(filtered)
                rebuildStrokePath()
            }
        }

        /**
         * Rebuilds the smooth stroke path from the sampled points: each
         * segment is a quadratic curve whose control point is the previous
         * sample and whose end is the midpoint to the next sample.
         */
        private fun rebuildStrokePath() {
            strokePath.reset()
            if (strokePoints.isEmpty()) return
            val first = strokePoints[0]
            strokePath.moveTo(first.x, first.y)
            if (strokePoints.size == 1) {
                // Zero-length line + round cap renders a dot for a single tap.
                strokePath.lineTo(first.x, first.y + 0.01f)
                return
            }
            for (i in 1 until strokePoints.size) {
                val prev = strokePoints[i - 1]
                val cur = strokePoints[i]
                strokePath.quadTo(prev.x, prev.y, (prev.x + cur.x) / 2f, (prev.y + cur.y) / 2f)
            }
            val last = strokePoints.last()
            strokePath.lineTo(last.x, last.y)
        }
    }

    override fun onStop() {
        super.onStop()
        // User left the selection session (e.g. pressed HOME); bring the ball
        // back so it never stays missing while the app is idle in background.
        com.suj1e.screenpal.service.FloatingWindowService.start(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        hintText?.let { hint ->
            hintFadeRunnable?.let { hint.removeCallbacks(it) }
        }
        hintText = null
        hintFadeRunnable = null
        (application as ScreenPalApplication).ttsManager.stop()
        if (!screenshotBitmap.isRecycled) {
            screenshotBitmap.recycle()
        }
        // Bring the floating ball back now that the selection session is over.
        com.suj1e.screenpal.service.FloatingWindowService.start(this)
    }
}
