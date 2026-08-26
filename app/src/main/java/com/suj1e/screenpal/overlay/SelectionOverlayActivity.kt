package com.suj1e.screenpal.overlay

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity

class SelectionOverlayActivity : ComponentActivity() {
    companion object {
        const val EXTRA_SCREENSHOT_URI = "extra_screenshot_uri"
        const val EXTRA_SELECTION_RECT = "extra_selection_rect"
    }

    private lateinit var screenshotBitmap: Bitmap
    private lateinit var selectionView: SelectionView
    private var startX = 0f
    private var startY = 0f
    private var isSelecting = false

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
        setContentView(selectionView)
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

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            val srcRect = Rect(0, 0, screenshotBitmap.width, screenshotBitmap.height)
            val dstRect = Rect(0, 0, width, height)
            canvas.drawBitmap(screenshotBitmap, srcRect, dstRect, null)

            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), overlayPaint)

            currentRect?.let { rect ->
                canvas.drawRect(rect, overlayPaint.apply { xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.CLEAR) })
                canvas.drawRect(rect, borderPaint)
            }
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.x
                    startY = event.y
                    currentRect = null
                    isSelecting = true
                    invalidate()
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (isSelecting) {
                        val left = Math.min(startX, event.x)
                        val top = Math.min(startY, event.y)
                        val right = Math.max(startX, event.x)
                        val bottom = Math.max(startY, event.y)
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
                                val result = Intent().apply {
                                    putExtra(
                                        EXTRA_SELECTION_RECT,
                                        Rect(rect.left.toInt(), rect.top.toInt(), rect.right.toInt(), rect.bottom.toInt())
                                    )
                                }
                                setResult(Activity.RESULT_OK, result)
                                finish()
                            }
                        }
                    }
                    return true
                }
                else -> return super.onTouchEvent(event)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (!screenshotBitmap.isRecycled) {
            screenshotBitmap.recycle()
        }
    }
}
