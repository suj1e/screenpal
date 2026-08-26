package com.suj1e.screenpal.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.ResultReceiver
import com.suj1e.screenpal.ScreenPalApplication

class FloatingWindowService : Service() {
    override fun onBind(intent: Intent): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Floating window interaction is handled in Change 3
        // For Change 2, we just keep the service alive
        return START_STICKY
    }

    fun captureScreen(callback: CaptureCallback) {
        val app = applicationContext as ScreenPalApplication

        if (!app.hasValidMediaProjection()) {
            // Need to request MediaProjection authorization
            // This is handled by launching an Activity (Change 3)
            callback.onError(ERROR_NEED_AUTH)
            return
        }

        val receiver = object : ResultReceiver(null) {
            override fun onReceiveResult(resultCode: Int, resultData: android.os.Bundle?) {
                if (resultCode == ScreenCaptureService.RESULT_OK) {
                    val uri = resultData?.getParcelable<android.net.Uri>("screenshot_uri")
                    callback.onSuccess(uri)
                } else {
                    callback.onError(ERROR_CAPTURE_FAILED)
                }
            }
        }

        ScreenCaptureService.start(this, receiver)
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
