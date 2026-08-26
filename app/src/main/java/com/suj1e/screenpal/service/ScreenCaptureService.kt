package com.suj1e.screenpal.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.media.Image
import android.media.ImageReader
import android.os.Build
import android.os.IBinder
import android.os.ResultReceiver
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import com.suj1e.screenpal.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import java.io.FileOutputStream

class ScreenCaptureService : Service() {
    companion object {
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "ScreenPal_Capture"
        const val CAPTURE_TIMEOUT_MS = 3000L
        const val JPEG_QUALITY = 85
        const val CAPTURE_DIR = "screenshots"

        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"
        const val EXTRA_RESULT_RECEIVER = "extra_result_receiver"

        const val RESULT_OK = -1
        const val RESULT_ERROR = 1

        fun start(context: Context, resultReceiver: ResultReceiver) {
            val intent = Intent(context, ScreenCaptureService::class.java).apply {
                putExtra(EXTRA_RESULT_RECEIVER, resultReceiver)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ScreenCaptureService::class.java))
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var mediaProjection: android.media.projection.MediaProjection? = null
    private var virtualDisplay: android.hardware.display.VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    override fun onBind(intent: Intent): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val resultReceiver = intent?.getParcelableExtra<ResultReceiver>(EXTRA_RESULT_RECEIVER)
        if (resultReceiver == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, buildNotification())

        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, -1)
        val resultData = intent.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)

        serviceScope.launch(Dispatchers.Main) {
            try {
                val uri = captureScreen(resultCode, resultData)
                if (uri != null) {
                    val bundle = android.os.Bundle().apply {
                        putParcelable("screenshot_uri", uri)
                    }
                    resultReceiver.send(RESULT_OK, bundle)
                } else {
                    resultReceiver.send(RESULT_ERROR, null)
                }
            } catch (e: Exception) {
                resultReceiver.send(RESULT_ERROR, null)
            } finally {
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseMediaProjection()
        serviceScope.cancel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "ScreenPal Capture",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Screen capture in progress"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.capture_notification_title))
            .setContentText(getString(R.string.capture_notification_text))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .build()
    }

    private suspend fun captureScreen(resultCode: Int, data: Intent?): android.net.Uri? {
        return withTimeout(CAPTURE_TIMEOUT_MS) {
            try {
                ensureMediaProjection(resultCode, data)
                val bitmap = acquireScreenshot()
                bitmap?.let { saveBitmapAndGetUri(it) }
            } catch (e: TimeoutCancellationException) {
                null
            } finally {
                releaseMediaProjection()
            }
        }
    }

    private fun ensureMediaProjection(resultCode: Int, data: Intent?) {
        val app = applicationContext as com.suj1e.screenpal.ScreenPalApplication
        mediaProjection = app.mediaProjection

        if (mediaProjection == null && resultCode != -1 && data != null) {
            val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as android.media.projection.MediaProjectionManager
            mediaProjection = projectionManager.getMediaProjection(resultCode, data)
            app.setMediaProjection(mediaProjection!!)
        }
    }

    private fun acquireScreenshot(): Bitmap? {
        val metrics = android.util.DisplayMetrics()
        val displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val defaultDisplay = displayManager.getDisplay(android.view.Display.DEFAULT_DISPLAY)
        defaultDisplay.getMetrics(metrics)

        val width = metrics.widthPixels
        val height = metrics.heightPixels

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)

        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ScreenPal_Capture",
            width,
            height,
            metrics.densityDpi,
            android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null,
            null
        )

        return try {
            readImageFromReader(width, height)
        } finally {
            imageReader?.close()
            imageReader = null
        }
    }

    private fun readImageFromReader(width: Int, height: Int): Bitmap? {
        val reader = imageReader ?: return null
        var image: Image? = null
        return try {
            var acquired = reader.acquireNextImage()
            if (acquired == null) {
                acquired = reader.acquireNextImage()
            }
            image = acquired

            val plane = image.planes[0]
            val buffer = plane.buffer
            val pixelStride = plane.pixelStride
            val rowStride = plane.rowStride
            val rowPadding = rowStride - pixelStride * width

            val bitmapWidth = width + rowPadding / pixelStride
            val bitmap = Bitmap.createBitmap(bitmapWidth, height, Bitmap.Config.ARGB_8888)
            bitmap.copyPixelsFromBuffer(buffer)

            val cropped = Bitmap.createBitmap(bitmap, 0, 0, width, height)
            bitmap.recycle()
            cropped
        } catch (e: Exception) {
            null
        } finally {
            image?.close()
        }
    }

    private suspend fun saveBitmapAndGetUri(bitmap: Bitmap): android.net.Uri? {
        return withContext(Dispatchers.IO) {
            try {
                val screenshotsDir = File(cacheDir, CAPTURE_DIR).apply { mkdirs() }
                val file = File(screenshotsDir, "screenshot_${System.currentTimeMillis()}.jpg")
                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
                }
                bitmap.recycle()
                FileProvider.getUriForFile(
                    this@ScreenCaptureService,
                    "${applicationContext.packageName}.fileprovider",
                    file
                )
            } catch (e: Exception) {
                null
            }
        }
    }

    private fun releaseMediaProjection() {
        try {
            virtualDisplay?.release()
        } catch (e: Exception) {
            // ignore
        } finally {
            virtualDisplay = null
            try {
                mediaProjection?.stop()
            } catch (e: Exception) {
                // ignore
            } finally {
                mediaProjection = null
            }
        }
    }
}
