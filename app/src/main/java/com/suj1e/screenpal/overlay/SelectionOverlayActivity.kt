package com.suj1e.screenpal.overlay

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
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
import com.suj1e.screenpal.ocr.MlKitOcrProvider
import com.suj1e.screenpal.ocr.OcrEngine
import com.suj1e.screenpal.ocr.OcrMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SelectionOverlayActivity : ComponentActivity() {
    companion object {
        const val EXTRA_SCREENSHOT_URI = "extra_screenshot_uri"
        const val EXTRA_SELECTION_RECT = "extra_selection_rect"
        const val TAG = "SelectionOverlay"
    }

    private lateinit var screenshotBitmap: Bitmap
    private lateinit var selectionView: SelectionView
    private lateinit var resultCard: LinearLayout
    private lateinit var resultText: TextView
    private lateinit var resultMeta: TextView
    private val viewModel = SelectionViewModel()
    private var lastRecognizedText: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setFlags(
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        )
        window.setFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        )

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
        setContentView(root)
    }

    /**
     * Runs when the user confirms a selection rectangle:
     * crop -> OCR (mode from settings) -> auto speak -> show result card.
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

                val result = withContext(Dispatchers.Default) { engine.recognize(cropped) }
                cropped.recycle()

                lastRecognizedText = result.text
                resultText.text = result.text.ifBlank { "（未识别到文字）" }
                resultMeta.text = "置信度 %.0f%% · ${result.blocks.size} 个文本块".format(result.confidence * 100)

                if (result.text.isNotBlank()) {
                    try {
                        app.ttsManager.speak(result.text)
                    } catch (e: Exception) {
                        // Keep the recognized text visible; just annotate that
                        // speech is unavailable (e.g. device has no TTS engine).
                        Log.w(TAG, "TTS playback failed", e)
                        runOnUiThread { resultMeta.append(" · 播报不可用") }
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
                OcrMode.LOCAL -> MlKitOcrProvider()
                OcrMode.CLOUD -> if (apiKey.isBlank()) {
                    Log.w(TAG, "Cloud OCR without API key; degrading to local")
                    MlKitOcrProvider()
                } else {
                    CloudOcrProvider(CloudOcrConfig(apiKey))
                }
                OcrMode.HYBRID -> HybridOcrEngine(
                    mlKitProvider = MlKitOcrProvider(),
                    cloudProvider = apiKey.takeIf { it.isNotBlank() }?.let {
                        CloudOcrProvider(CloudOcrConfig(it))
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
            cm.setPrimaryClip(ClipData.newPlainText("ScreenPal", lastRecognizedText))
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

        private val borderPaint = Paint().apply {
            color = Color.parseColor("#FF7B68EE")
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }

        var currentRect: RectF? = null

        fun resetForReselection() {
            currentRect = null
            isSelecting = false
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            val srcRect = Rect(0, 0, screenshotBitmap.width, screenshotBitmap.height)
            val dstRect = Rect(0, 0, width, height)
            canvas.drawBitmap(screenshotBitmap, srcRect, dstRect, null)

            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), overlayPaint)

            currentRect?.let { rect ->
                val clearPaint = Paint().apply {
                    xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.CLEAR)
                    style = Paint.Style.FILL
                }
                canvas.drawRect(rect, clearPaint)
                canvas.drawRect(rect, borderPaint)
            }
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.x
                    downY = event.y
                    isSelecting = true
                    currentRect = null
                    invalidate()
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (isSelecting) {
                        val left = minOf(downX, event.x)
                        val top = minOf(downY, event.y)
                        val right = maxOf(downX, event.x)
                        val bottom = maxOf(downY, event.y)
                        currentRect = RectF(left, top, right, bottom)
                        invalidate()
                    }
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    if (isSelecting) {
                        isSelecting = false
                        currentRect?.let { rect ->
                            if (rect.width() > 48 && rect.height() > 48) {
                                onSelectionConfirmed(
                                    Rect(
                                        rect.left.toInt(), rect.top.toInt(),
                                        rect.right.toInt(), rect.bottom.toInt()
                                    )
                                )
                            }
                        }
                    }
                    return true
                }
                else -> return super.onTouchEvent(event)
            }
        }
    }

    private var downX = 0f
    private var downY = 0f
    private var isSelecting = false

    override fun onDestroy() {
        super.onDestroy()
        (application as ScreenPalApplication).ttsManager.stop()
        if (!screenshotBitmap.isRecycled) {
            screenshotBitmap.recycle()
        }
        // Bring the floating ball back now that the selection session is over.
        com.suj1e.screenpal.service.FloatingWindowService.start(this)
    }
}
