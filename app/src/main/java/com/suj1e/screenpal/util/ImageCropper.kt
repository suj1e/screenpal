package com.suj1e.screenpal.util

import android.graphics.Bitmap
import android.graphics.Rect

object ImageCropper {

    fun crop(
        bitmap: Bitmap,
        rect: Rect,
        screenWidth: Int,
        screenHeight: Int
    ): Bitmap? {
        if (bitmap.isRecycled) return null
        if (rect.isEmpty) return null

        val scaleX = bitmap.width.toFloat() / screenWidth
        val scaleY = bitmap.height.toFloat() / screenHeight

        val cropLeft = (rect.left * scaleX).toInt().coerceIn(0, bitmap.width - 1)
        val cropTop = (rect.top * scaleY).toInt().coerceIn(0, bitmap.height - 1)
        val cropRight = (rect.right * scaleX).toInt().coerceIn(cropLeft + 1, bitmap.width)
        val cropBottom = (rect.bottom * scaleY).toInt().coerceIn(cropTop + 1, bitmap.height)

        val cropWidth = cropRight - cropLeft
        val cropHeight = cropBottom - cropTop

        if (cropWidth <= 0 || cropHeight <= 0) return null

        return Bitmap.createBitmap(bitmap, cropLeft, cropTop, cropWidth, cropHeight)
    }
}
