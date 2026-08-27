package com.suj1e.screenpal.service

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle

/**
 * Transparent host for the system MediaProjection consent dialog.
 *
 * On API 34+ getMediaProjection() requires an already-running FGS of type
 * mediaProjection, so consent (resultCode + data) is handed straight to
 * ScreenCaptureService, which enters the foreground first and then creates
 * the projection and captures.
 */
class CaptureConsentActivity : Activity() {

    private lateinit var projectionManager: MediaProjectionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(projectionManager.createScreenCaptureIntent(), REQUEST_CONSENT)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        android.util.Log.d("ScreenPalFlow", "consent result: requestCode=$requestCode resultCode=$resultCode")
        if (requestCode != REQUEST_CONSENT) {
            finish()
            return
        }

        if (resultCode == RESULT_OK && data != null) {
            // Chain straight into capture + selection so the tap feels atomic.
            // Use the app context: this activity finishes right away and must
            // not be the context that later launches the selection screen.
            val receiver = object : android.os.ResultReceiver(null) {
                override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                    if (resultCode == ScreenCaptureService.RESULT_OK) {
                        val uri = resultData?.getParcelable<android.net.Uri>("screenshot_uri")
                        uri?.let { openSelection(it) }
                    }
                }
            }
            ScreenCaptureService.start(applicationContext, receiver, resultCode, data)
        } else {
            // Consent denied: bring the ball back for another try.
            FloatingWindowService.start(applicationContext)
        }

        finish()
    }

    private fun openSelection(screenshotUri: android.net.Uri) {
        applicationContext.startActivity(
            Intent(applicationContext, com.suj1e.screenpal.overlay.SelectionOverlayActivity::class.java).apply {
                putExtra(
                    com.suj1e.screenpal.overlay.SelectionOverlayActivity.EXTRA_SCREENSHOT_URI,
                    screenshotUri
                )
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }

    companion object {
        const val REQUEST_CONSENT = 2001
    }
}
