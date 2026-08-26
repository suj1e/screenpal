package com.suj1e.screenpal.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.ResultReceiver
import androidx.core.app.NotificationCompat
import com.suj1e.screenpal.R
import com.suj1e.screenpal.ScreenPalApplication
import com.suj1e.screenpal.overlay.SelectionOverlayActivity

class FloatingWindowService : Service() {
    override fun onBind(intent: Intent): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    fun captureScreen(callback: CaptureCallback) {
        val app = applicationContext as ScreenPalApplication

        if (!app.hasValidMediaProjection()) {
            callback.onError(ERROR_NEED_AUTH)
            return
        }

        val receiver = object : ResultReceiver(null) {
            override fun onReceiveResult(resultCode: Int, resultData: android.os.Bundle?) {
                if (resultCode == com.suj1e.screenpal.service.ScreenCaptureService.RESULT_OK) {
                    val uri = resultData?.getParcelable<android.net.Uri>("screenshot_uri")
                    callback.onSuccess(uri)
                } else {
                    callback.onError(ERROR_CAPTURE_FAILED)
                }
            }
        }

        com.suj1e.screenpal.service.ScreenCaptureService.start(this, receiver)
    }

    interface CaptureCallback {
        fun onSuccess(uri: android.net.Uri?)
        fun onError(error: Int)
    }

    companion object {
        const val ERROR_NEED_AUTH = 1001
        const val ERROR_CAPTURE_FAILED = 1002
    }
}
