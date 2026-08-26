package com.suj1e.screenpal.overlay

import android.graphics.Rect
import androidx.lifecycle.ViewModel

class SelectionViewModel : ViewModel() {
    var screenshotUri: android.net.Uri? = null
    var selectionRect: Rect? = null

    fun calculateCropRect(
        selectionRect: Rect,
        bitmapWidth: Int,
        bitmapHeight: Int,
        screenWidth: Int,
        screenHeight: Int
    ): Rect {
        val scaleX = bitmapWidth.toFloat() / screenWidth
        val scaleY = bitmapHeight.toFloat() / screenHeight

        var left = (selectionRect.left * scaleX).toInt()
        var top = (selectionRect.top * scaleY).toInt()
        var right = (selectionRect.right * scaleX).toInt()
        var bottom = (selectionRect.bottom * scaleY).toInt()

        if (left < 0) left = 0
        if (top < 0) top = 0
        if (right > bitmapWidth) right = bitmapWidth
        if (bottom > bitmapHeight) bottom = bitmapHeight

        return Rect(left, top, right, bottom)
    }
}
