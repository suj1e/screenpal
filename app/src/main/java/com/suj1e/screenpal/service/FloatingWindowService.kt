package com.suj1e.screenpal.service

import android.app.AlertDialog
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.ResultReceiver
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import com.suj1e.screenpal.R
import com.suj1e.screenpal.ScreenPalApplication
import com.suj1e.screenpal.overlay.SelectionOverlayActivity
import com.suj1e.screenpal.util.AccessibilityHelper

/** 截屏路由：无障碍静默截屏 / 未开启引导 / MediaProjection 兜底。 */
internal enum class ScreenshotRoute { ACCESSIBILITY, GUIDE, MEDIA_PROJECTION }

class FloatingWindowService : Service() {

    private lateinit var windowManager: WindowManager
    private var floatingView: View? = null

    /** 无障碍截屏回调的落盘线程 → 主线程回投（launchSelection/Toast 都要求主线程）。 */
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    override fun onBind(intent: Intent): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundCompat()

        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "请先授予悬浮窗权限", Toast.LENGTH_LONG).show()
            stopSelf()
            return START_NOT_STICKY
        }

        if (floatingView == null) {
            showFloatingBall()
        }
        serviceRunning = true
        return START_STICKY
    }

    /**
     * The overlay ball is a persistent app-specific service, not a capture
     * session, so it must NOT use the mediaProjection FGS type (that type
     * throws SecurityException without an active projection).
     */
    private fun startForegroundCompat() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onDestroy() {
        removeFloatingBall()
        serviceRunning = false
        super.onDestroy()
    }

    private fun showFloatingBall() {
        val view = LayoutInflater.from(this).inflate(R.layout.view_floating_ball, null)

        val params = WindowManager.LayoutParams(
            BALL_SIZE_DP.dpToPx(),
            BALL_SIZE_DP.dpToPx(),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = resources.displayMetrics.widthPixels - BALL_SIZE_DP.dpToPx() - 16.dpToPx()
            y = 200.dpToPx()
        }

        setupTouchHandling(view)
        windowManager.addView(view, params)
        floatingView = view
    }

    private fun removeFloatingBall() {
        floatingView?.let { view ->
            try {
                windowManager.removeView(view)
            } catch (_: Exception) {
                // view not attached
            }
        }
        floatingView = null
    }

    private fun setupTouchHandling(view: View) {
        var downRawX = 0f
        var downRawY = 0f
        var startX = 0
        var startY = 0
        var dragging = false

        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    val lp = view.layoutParams as WindowManager.LayoutParams
                    startX = lp.x
                    startY = lp.y
                    dragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downRawX
                    val dy = event.rawY - downRawY
                    if (!dragging && Math.hypot(dx.toDouble(), dy.toDouble()) > DRAG_THRESHOLD_DP.dpToPx()) {
                        dragging = true
                    }
                    if (dragging) {
                        val lp = view.layoutParams as WindowManager.LayoutParams
                        lp.x = snap(startX + dx.toInt())
                        lp.y = (startY + dy.toInt()).coerceAtLeast(0)
                        windowManager.updateViewLayout(view, lp)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!dragging) onBallClicked()
                    true
                }
                else -> false
            }
        }
    }

    /** Snap to the right edge when within EDGE_SNAP_DP of the screen right border. */
    private fun snap(candidateX: Int): Int {
        val maxX = resources.displayMetrics.widthPixels - BALL_SIZE_DP.dpToPx()
        return when {
            candidateX >= maxX - EDGE_SNAP_DP.dpToPx() -> maxX
            candidateX <= EDGE_SNAP_DP.dpToPx() -> 0
            else -> candidateX.coerceIn(0, maxX)
        }
    }

    private fun onBallClicked() {
        android.util.Log.d(TAG, "ball clicked, hasProjection=${(application as ScreenPalApplication).hasValidMediaProjection()}")
        removeFloatingBall()
        when (
            resolveScreenshotRoute(
                sdkInt = Build.VERSION.SDK_INT,
                a11yEnabled = AccessibilityHelper.isEnabled(this),
                a11yServiceRunning = ScreenPalAccessibilityService.isRunning()
            )
        ) {
            ScreenshotRoute.ACCESSIBILITY -> captureViaAccessibility()
            ScreenshotRoute.GUIDE -> showAccessibilityGuide()
            ScreenshotRoute.MEDIA_PROJECTION -> legacyCapture()
        }
    }

    /**
     * 无障碍静默截屏：成功落盘转 URI 进框选；失败/限流 Toast 并恢复球。
     * 实例在路由判定后仍可能刚好被杀（isRunning 与取实例非原子），回落录屏不阻塞。
     */
    private fun captureViaAccessibility() {
        val service = ScreenPalAccessibilityService.instance ?: return legacyCapture()
        service.captureCurrentScreen { bitmap ->
            if (bitmap == null) {
                restoreAfterFailure("截屏失败或太频繁，请重试")
                return@captureCurrentScreen
            }
            Thread {
                val uri = saveScreenshotBitmap(bitmap)
                mainHandler.post {
                    if (uri != null) {
                        android.util.Log.d(TAG, "a11y capture success uri=$uri")
                        launchSelection(uri)
                    } else {
                        restoreAfterFailure("截图数据为空")
                    }
                }
            }.start()
        }
    }

    /**
     * 与 ScreenCaptureService.saveBitmapAndGetUri 同款落盘契约
     * （cache/screenshots + JPEG + FileProvider），供框选页跨进程读取。
     */
    internal fun saveScreenshotBitmap(bitmap: android.graphics.Bitmap): android.net.Uri? = try {
        val dir = java.io.File(cacheDir, ScreenCaptureService.CAPTURE_DIR).apply { mkdirs() }
        val file = java.io.File(dir, "screenshot_${System.currentTimeMillis()}.jpg")
        java.io.FileOutputStream(file).use { out ->
            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, ScreenCaptureService.JPEG_QUALITY, out)
        }
        androidx.core.content.FileProvider.getUriForFile(
            this,
            "${applicationContext.packageName}.fileprovider",
            file
        )
    } catch (e: Exception) {
        android.util.Log.e(TAG, "save screenshot bitmap failed", e)
        null
    } finally {
        bitmap.recycle()
    }

    /** 原 MediaProjection 兜底路径（API<30、无障碍未就绪、实例被杀时）。 */
    private fun legacyCapture() {
        captureScreen(object : CaptureCallback {
            override fun onSuccess(uri: android.net.Uri?) {
                android.util.Log.d(TAG, "capture success uri=$uri")
                uri?.let { launchSelection(it) } ?: restoreAfterFailure("截图数据为空")
            }

            override fun onError(error: Int) {
                android.util.Log.d(TAG, "capture error=$error")
                if (error == ERROR_NEED_AUTH) {
                    launchConsent()
                } else {
                    restoreAfterFailure("截图失败")
                }
            }
        })
    }

    /**
     * 未开启无障碍的引导对话框（2026-08-29-a11y-screenshot task 3）：
     * 用途说明 + 「去开启」（直跳系统无障碍设置）+「本次仍用录屏」（老路径）。
     * 按钮回调抽成 [showA11yGuideDialog] 参数，JVM 可直接验证接线。
     */
    private fun showAccessibilityGuide() {
        showA11yGuideDialog(
            onGoToSettings = ::openAccessibilitySettings,
            onUseProjection = ::useProjectionThisTime,
            onCancel = ::showFloatingBall
        )
    }

    /** 引导对话框本体；三个出口均以回调注入。 */
    internal fun showA11yGuideDialog(
        onGoToSettings: () -> Unit,
        onUseProjection: () -> Unit,
        onCancel: () -> Unit
    ) {
        AlertDialog.Builder(this)
            .setTitle("开启无障碍后可免授权一键识读")
            .setMessage("开启无障碍后，念念无需每次授权录屏即可静默截屏识别文字；截屏内容仅在本地处理。")
            .setPositiveButton("去开启") { _, _ -> onGoToSettings() }
            .setNegativeButton("本次仍用录屏") { _, _ -> onUseProjection() }
            .setOnCancelListener { onCancel() }
            .create()
            .also { dialog ->
                // Service context has no application window token: a default
                // dialog window would throw BadTokenException on a real device.
                // The overlay-type window is legal here — SYSTEM_ALERT_WINDOW
                // is a hard precondition of this very service (the ball itself
                // is a TYPE_APPLICATION_OVERLAY view).
                dialog.window?.setType(
                    android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                )
                dialog.show()
            }
    }

    /** 「去开启」：恢复悬浮球后直跳系统无障碍设置页。 */
    internal fun openAccessibilitySettings() {
        showFloatingBall()
        startActivity(AccessibilityHelper.settingsIntent().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** 「本次仍用录屏」：转入原 MediaProjection 路径（授权/捕获逻辑原样保留）。 */
    internal fun useProjectionThisTime() {
        legacyCapture()
    }

    private fun restoreAfterFailure(reason: String) {
        Toast.makeText(this, "$reason，悬浮球已恢复", Toast.LENGTH_SHORT).show()
        showFloatingBall()
    }

    private fun launchSelection(screenshotUri: android.net.Uri) {
        val intent = Intent(this, SelectionOverlayActivity::class.java).apply {
            putExtra(SelectionOverlayActivity.EXTRA_SCREENSHOT_URI, screenshotUri)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }

    private fun launchConsent() {
        val intent = Intent(this, CaptureConsentActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }

    internal fun captureScreen(callback: CaptureCallback) {
        val app = applicationContext as ScreenPalApplication

        if (!app.hasValidMediaProjection()) {
            callback.onError(ERROR_NEED_AUTH)
            return
        }

        val receiver = object : ResultReceiver(null) {
            override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
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

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.channel_floating_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply { setShowBadge(false) }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification =
        androidx.core.app.NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_floating_title))
            .setContentText("点击悬浮球框选识别屏幕文字")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .build()

    companion object {
        const val CHANNEL_ID = "ScreenPal_Floating"
        const val NOTIFICATION_ID = 1002
        const val DRAG_THRESHOLD_DP = 8
        const val EDGE_SNAP_DP = 30
        const val BALL_SIZE_DP = 56

        const val TAG = "ScreenPalFlow"
        const val ERROR_NEED_AUTH = 1001
        const val ERROR_CAPTURE_FAILED = 1002

        /**
         * 截屏路由决策（纯函数，供矩阵单测）：
         * - API < 30（无 takeScreenshot）→ MediaProjection 兜底；
         * - API ≥ 30 且未开启无障碍 → 引导对话框；
         * - API ≥ 30 且已开启且服务实例在 → 无障碍静默截屏；
         * - 系统显示已启用但实例被杀 → 回落 MediaProjection，不阻塞。
         */
        internal fun resolveScreenshotRoute(
            sdkInt: Int,
            a11yEnabled: Boolean,
            a11yServiceRunning: Boolean
        ): ScreenshotRoute = when {
            sdkInt < 30 -> ScreenshotRoute.MEDIA_PROJECTION
            !a11yEnabled -> ScreenshotRoute.GUIDE
            a11yServiceRunning -> ScreenshotRoute.ACCESSIBILITY
            else -> ScreenshotRoute.MEDIA_PROJECTION
        }

        /** Process-scoped liveness flag; the service dies with the process. */
        @Volatile
        var serviceRunning = false
            private set

        fun start(context: Context) {
            val intent = Intent(context, FloatingWindowService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, FloatingWindowService::class.java))
        }
    }

    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()
}
