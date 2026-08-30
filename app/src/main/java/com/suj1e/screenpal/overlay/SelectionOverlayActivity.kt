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
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.util.StateSet
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.suj1e.screenpal.ScreenPalApplication
import com.suj1e.screenpal.ocr.HybridOcrEngine
import com.suj1e.screenpal.ocr.OcrEngine
import com.suj1e.screenpal.ocr.OcrMode
import com.suj1e.screenpal.ocr.StepfunOcrProvider
import com.suj1e.screenpal.ocr.paddle.PaddleOcrProvider
import com.suj1e.screenpal.translate.BroadcastMode
import com.suj1e.screenpal.translate.BroadcastOutcome
import com.suj1e.screenpal.translate.ChineseBroadcastPipeline
import com.suj1e.screenpal.translate.StepfunTranslateClient
import com.suj1e.screenpal.translate.TranslateService
import com.suj1e.screenpal.util.UserSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

class SelectionOverlayActivity : ComponentActivity() {

    /**
     * 遮罩状态机状态（2026-08-29-selection-mode，两模式统一）：
     * DRAWING = 绘制期（初始/进行中/拒选/重新框选），无全屏遮罩，截图原亮度；
     * CONFIRMED = 确认后，全屏 #8F000000 + 按选区挖孔（套索挖包围盒、矩形挖选中矩形）。
     * 嵌套在类体（非 companion）以便测试用 SelectionOverlayActivity.SelectionPhase 引用。
     */
    internal enum class SelectionPhase {
        /** 绘制期（也是初始态）。 */
        DRAWING,

        /** 已确认：圈外变暗。 */
        CONFIRMED;

        companion object {
            val initial: SelectionPhase = DRAWING
        }
    }

    /** 驱动遮罩状态机的动作（与触摸/按钮语义一一对应）。 */
    internal enum class SelectionPhaseAction {
        /** 落笔开始新选区（DOWN）。 */
        GESTURE_START,

        /** 选区有效，确认（UP 过门槛）。 */
        CONFIRM,

        /** 选区过小被拒（UP 未过门槛，震动）。 */
        REJECT,

        /**「重新框选」按钮。 */
        RESELECT
    }

    companion object {
        /** True while a selection screen is foreground-able (BAL-block watchdog). */
        @Volatile
        var isAlive: Boolean = false
            private set

        const val EXTRA_SCREENSHOT_URI = "extra_screenshot_uri"
        const val EXTRA_SELECTION_RECT = "extra_selection_rect"
        const val TAG = "SelectionOverlay"

        /** Minimum distance between two sampled lasso points. */
        internal const val MIN_SAMPLE_DISTANCE_DP = 8f

        /** Width of the visible purple lasso trace. */
        internal const val STROKE_LINE_DP = 4f

        /** Width of the white secondary stroke that keeps the trace readable on light backgrounds. */
        internal const val SECONDARY_STROKE_DP = 2f

        /** Minimum bounding-box size (width OR height) for a valid selection. */
        internal const val MIN_SELECTION_SIZE_DP = 48f

        /** Delay before the first-entry hint fades out. */
        internal const val HINT_DELAY_MS = 3000L

        /**
         * Pure mask state machine (2026-08-29-selection-mode): any gesture
         * start / reject / reselect lands back in DRAWING (no mask); only a
         * confirmed selection holds CONFIRMED. Exposed for JVM unit tests.
         */
        // `current` is intentionally unused: every action maps to the same
        // target phase regardless of where we are (kept for call-site clarity
        // and future per-state transitions).
        internal fun nextSelectionPhase(
            current: SelectionPhase,
            action: SelectionPhaseAction
        ): SelectionPhase = when (action) {
            SelectionPhaseAction.CONFIRM -> SelectionPhase.CONFIRMED
            SelectionPhaseAction.GESTURE_START,
            SelectionPhaseAction.REJECT,
            SelectionPhaseAction.RESELECT -> SelectionPhase.DRAWING
        }

        /** 绘制期不画全屏遮罩（截图原亮度）；确认后才圈外变暗。Exposed for JVM unit tests. */
        internal fun shouldDrawMask(phase: SelectionPhase): Boolean =
            phase == SelectionPhase.CONFIRMED

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
         * Pure RECT-drag normalization (2026-08-29-selection-mode): the drag
         * rect is built from min/max of the start and current points, so any
         * drag direction yields a proper Rect(start,end). A single tap
         * degenerates to a zero-size box, which the shared size gate rejects.
         * Exposed for JVM unit tests.
         */
        internal fun normalizeRect(x1: Float, y1: Float, x2: Float, y2: Float): RectF =
            RectF(minOf(x1, x2), minOf(y1, y2), maxOf(x1, x2), maxOf(y1, y2))

        /**
         * 结果卡 meta 标注映射（2026-08-29-broadcast-mode 起按播报模式区分降级语义）：
         * 翻译发生 → 「AI 转译」；讲解成功 → 「AI 讲解」；降级按 mode 分流——TRANSLATE
         * 是「翻译不可用」，EXPLAIN 是「AI 讲解不可用」（讲解失败 ≠ 翻译失败）；中文
         * 直读/空文本 → 不标注。Exposed for JVM unit tests.
         */
        internal fun metaAnnotation(
            outcome: BroadcastOutcome,
            mode: BroadcastMode = BroadcastMode.TRANSLATE
        ): String? = when (outcome) {
            BroadcastOutcome.Translated -> " · AI 转译"
            BroadcastOutcome.EXPLAINED -> " · AI 讲解"
            BroadcastOutcome.FallbackOriginal ->
                if (mode == BroadcastMode.EXPLAIN) " · AI 讲解不可用" else " · 翻译不可用"
            BroadcastOutcome.Direct -> null
        }

        /**
         * 云侧 OCR 引擎（CLOUD 模式 / HYBRID 云侧）直连 StepFun：Key 非空 →
         * [StepfunOcrProvider]；否则 null（上层落端侧 Paddle）。
         */
        internal fun cloudOcrEngine(settings: UserSettings): OcrEngine? =
            settings.stepfunApiKey.takeIf { it.isNotBlank() }
                ?.let { StepfunOcrProvider(apiKey = it) }

        /**
         * AI 转译客户端（中文播报管道）直连 StepFun：Key 非空 →
         * [StepfunTranslateClient]；否则 null（上层播原文）。
         */
        internal fun translateClient(settings: UserSettings): TranslateService? =
            settings.stepfunApiKey.takeIf { it.isNotBlank() }
                ?.let { StepfunTranslateClient(apiKey = it) }

        // ---- 结果卡胶囊按钮（2026-08-29-result-card-polish）----

        /** 品牌紫文字色：#FF7B68EE（不透明），与选区高亮描边同色系。 */
        internal const val PILL_TEXT_COLOR = 0xFF7B68EE.toInt()

        /**
         * 胶囊描边色：品牌紫 40% 透明度（alpha 0x66 + #7B68EE）。
         * design 记法「0x66FF7B68EE」按 Android ARGB 语义取 alpha 0x66 + 紫 RGB。
         */
        internal const val PILL_STROKE_COLOR = 0x667B68EE

        /** pressed 叠底色：8% 黑（#14000000）。 */
        internal const val PILL_PRESSED_OVERLAY = 0x14000000

        /** 胶囊圆角（dp）：远超按钮对角线，等效全圆角胶囊。 */
        internal const val PILL_CORNER_RADIUS_DP = 999f

        /** 胶囊描边宽（dp）。 */
        internal const val PILL_STROKE_DP = 1.5f

        /** 胶囊按钮高（dp）。 */
        internal const val PILL_HEIGHT_DP = 44

        /** 胶囊按钮水平 margin（dp）。 */
        internal const val PILL_MARGIN_DP = 4

        /** 胶囊按钮文字字号（sp）。 */
        internal const val PILL_TEXT_SP = 13f

        /** 识别文本滚动区最大高度占屏高比例（40%）。 */
        internal const val RESULT_TEXT_MAX_HEIGHT_FRACTION = 0.4f

        /**
         * 识别文本滚动区最大高度（px）：屏高 40%。Exposed for JVM unit tests。
         */
        internal fun resultTextMaxHeightPx(screenHeightPx: Int): Int =
            (screenHeightPx * RESULT_TEXT_MAX_HEIGHT_FRACTION).toInt()

        /**
         * 常态胶囊背景（程序化工厂，无资源文件）：白底 + 品牌紫描边 + 胶囊圆角。
         * Exposed for JVM unit tests (Robolectric)。
         */
        internal fun pillBackground(
            strokeColor: Int,
            strokeWidthPx: Int,
            cornerRadiusPx: Float
        ): GradientDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = cornerRadiusPx
            setColor(Color.WHITE)
            setStroke(strokeWidthPx, strokeColor)
        }

        /**
         * source-over 叠色：[overlay]（含 alpha）叠在 [base] 上的合成结果。
         * 用于 pressed 叠底的白底合成（白 + 8% 黑 = #EBEBEB），视觉等价
         * LayerDrawable 叠加。Exposed for JVM unit tests。
         */
        internal fun overlayColor(base: Int, overlay: Int): Int {
            val srcA = Color.alpha(overlay) / 255f
            val dstA = Color.alpha(base) / 255f
            val outA = srcA + dstA * (1 - srcA)
            fun channel(src: Int, dst: Int): Int {
                if (outA == 0f) return 0
                val value = (src * srcA + dst * dstA * (1 - srcA)) / outA
                return value.roundToInt().coerceIn(0, 255)
            }
            return Color.argb(
                (outA * 255).roundToInt().coerceIn(0, 255),
                channel(Color.red(overlay), Color.red(base)),
                channel(Color.green(overlay), Color.green(base)),
                channel(Color.blue(overlay), Color.blue(base))
            )
        }

        /**
         * 按钮背景（含按压反馈）：常态白底胶囊；pressed = 白底叠
         * [PILL_PRESSED_OVERLAY] 的合成色胶囊（同描边同圆角）。pressed 态必须
         * 先于兜底态注册（StateListDrawable 取第一个命中态）。
         * Exposed for JVM unit tests (Robolectric)。
         */
        internal fun pillPressed(
            strokeColor: Int,
            strokeWidthPx: Int,
            cornerRadiusPx: Float
        ): StateListDrawable = StateListDrawable().apply {
            addState(
                intArrayOf(android.R.attr.state_pressed),
                GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = cornerRadiusPx
                    setColor(overlayColor(Color.WHITE, PILL_PRESSED_OVERLAY))
                    setStroke(strokeWidthPx, strokeColor)
                }
            )
            addState(StateSet.WILD_CARD, pillBackground(strokeColor, strokeWidthPx, cornerRadiusPx))
        }
    }

    private lateinit var screenshotBitmap: Bitmap
    private lateinit var selectionView: SelectionView
    private lateinit var resultCard: LinearLayout
    private lateinit var resultText: TextView
    private var textScroll: android.widget.ScrollView? = null
    private lateinit var resultMeta: TextView
    private val viewModel = SelectionViewModel()
    private var lastRecognizedText: String = ""
    private var hintText: TextView? = null
    private var hintFadeRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isAlive = true

        // 沉浸式全屏：截图位图含截屏那一刻的系统栏像素，活系统栏必须
        // 隐藏，否则与位图顶部叠印（MIUI 上「上下重复」的根因）。
        // 注意：不要 setDecorFitsSystemWindows(false)——它会禁用
        // FLAG_FULLSCREEN；MIUI 尊重老式 flag 而无视 insets-controller。
        @Suppress("DEPRECATION")
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN)
        applyLegacyImmersiveFlags()

        // NOTE: do NOT set FLAG_NOT_FOCUSABLE here (a leftover from the
        // floating-window service requirements) — a focusable activity must
        // keep receiving BACK so the user can always leave this screen.

        val screenshotUri = intent.getParcelableExtra<Uri>(EXTRA_SCREENSHOT_URI)
        screenshotBitmap = screenshotUri?.let { uri ->
            contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input)
            }
        } ?: Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)

        selectionView = SelectionView(this, mode = loadSelectionMode())
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
        // 沉浸式后卡片会顶到手势条下面——用导航栏 inset 做底部避让，
        // 保证四个胶囊按钮完整可见。
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val nav = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.navigationBars())
            view.setPadding(0, 0, 0, nav.bottom)
            insets
        }
        addFirstEntryHint(root)
        addExitButton(root)
        setContentView(root)
    }

    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()

    /** MIUI 对 insets-hide 常不生效——legacy 沉浸式 flag 是它认的方式。 */
    private fun applyLegacyImmersiveFlags() {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility =
            android.view.View.SYSTEM_UI_FLAG_FULLSCREEN or
            android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyLegacyImmersiveFlags()
    }

    /** 右上角显式退出按钮：误点悬浮球的用户不必知道「轻点空白」也能离开。 */
    private fun addExitButton(root: android.widget.FrameLayout) {
        val close = android.widget.TextView(this).apply {
            text = "✕ 退出"
            textSize = 14f
            setTextColor(android.graphics.Color.WHITE)
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 24.dpToPx().toFloat()
                setColor(0x66000000)
            }
            gravity = Gravity.CENTER
            val lp = android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.END
            )
            lp.setMargins(0, 0, 24.dpToPx(), 0)
            layoutParams = lp
            setPadding(24.dpToPx(), 10.dpToPx(), 24.dpToPx(), 10.dpToPx())
            setOnClickListener { finish() }
        }
        root.addView(close)
    }

    /** First-entry coaching hint: "用手指圈出要朗读的文字", fades out after 3s. */
    private fun addFirstEntryHint(root: android.widget.FrameLayout) {
        val hint = TextView(this).apply {
            text = "圈出要朗读的文字 · 轻点空白退出"
            textSize = 14f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#66000000"))
            setPadding(40, 24, 200, 24)
            gravity = Gravity.CENTER
        }
        root.addView(hint, android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
            android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.TOP
        ).apply { topMargin = 0 })

        hintText = hint
        // 3s 后降为半透明常驻（不消失）：顶部的提示条正好盖住截图位图中
        // 烤入的状态栏像素（a11y 全屏截屏含系统栏，隐藏活系统栏后会露出）。
        val fade = Runnable {
            hint.animate().alpha(0.45f).setDuration(400).start()
        }
        hintFadeRunnable = fade
        hint.postDelayed(fade, HINT_DELAY_MS)
        hint.post {
            // 立即盖住位图顶部的状态栏像素（淡出前就位）。
            hint.translationY = 0f
        }
    }

    /**
     * 框选方式（2026-08-29-selection-mode）：构造 [SelectionView] 前同步读一次
     * 设置（DataStore 小文件，首帧读一次可接受）；脏值/未知值一律回退 LASSO，
     * 升级用户行为不变。
     */
    private fun loadSelectionMode(): SelectionMode {
        val app = application as ScreenPalApplication
        val raw = runCatching {
            kotlinx.coroutines.runBlocking {
                app.settingsRepository.userSettings.first().selectionMode
            }
        }.getOrDefault("LASSO")
        return SelectionMode.fromStorageValue(raw)
    }

    /**
     * Runs when the user confirms a selection rectangle:
     * crop -> OCR (mode from settings) -> broadcast (translate-to-Chinese when
     * needed, falling back to the original text) -> show result card.
     */
    internal fun onSelectionConfirmed(screenRect: Rect) {
        viewModel.selectionRect = screenRect
        Log.i(TAG, "lasso viewRect=$screenRect view=${selectionView.width}x${selectionView.height} bitmap=${screenshotBitmap.width}x${screenshotBitmap.height}")
        val cropRect = viewModel.calculateCropRect(
            screenRect,
            screenshotBitmap.width,
            screenshotBitmap.height,
            selectionView.width.coerceAtLeast(1),
            selectionView.height.coerceAtLeast(1)
        )
        Log.i(TAG, "cropRect=$cropRect")
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
                textScroll?.post { textScroll?.scrollTo(0, 0) }
                resultMeta.text = "置信度 %.0f%% · ${result.blocks.size} 个文本块".format(result.confidence * 100)

                if (result.text.isNotBlank()) {
                    try {
                        val settings = app.settingsRepository.userSettings.first()
                        // 播报模式（2026-08-29-broadcast-mode）：从设置解析（脏值回退
                        // TRANSLATE），决定管道分支与降级标注语义。
                        val mode = BroadcastMode.fromStorageValue(settings.broadcastMode)
                        // 转译客户端直连 StepFun；缺凭据（null）直接落「播原文」语义，
                        // 与管道内 AI 失败降级一致（降级标注按 mode 分流）。
                        val pipeline = translateClient(settings)
                            ?.let { ChineseBroadcastPipeline(it) }
                        val outcome = if (pipeline != null) {
                            pipeline.broadcast(
                                result.text,
                                app.ttsManager,
                                translationEnabled = settings.translationEnabled,
                                mode = mode
                            )
                        } else {
                            // 缺凭据等价于"无 AI 能力"：EXPLAIN 显式选择即总是走 AI
                            //（一律讲解不可用）；TRANSLATE 维持原语义——开关关或本就
                            // 中文时直读（Direct，无标注），确实需要翻译才标「翻译不可用」。
                            val needsAi = mode == BroadcastMode.EXPLAIN ||
                                settings.translationEnabled &&
                                !com.suj1e.screenpal.translate.ChineseHeuristic.isMostlyChinese(result.text)
                            Log.w(TAG, "StepFun AI 凭据缺失；跳过转译/讲解直读原文")
                            app.ttsManager.speak(result.text)
                            if (needsAi) BroadcastOutcome.FallbackOriginal else BroadcastOutcome.Direct
                        }
                        // 主显播报文本（译文/讲解或降级原文），标注后附原文小字供校对。
                        metaAnnotation(outcome, mode)?.let { resultMeta.append(it) }
                        pipeline?.lastSpokenText?.let { spoken ->
                            if (spoken != result.text) {
                                resultText.text = spoken
                                textScroll?.post { textScroll?.scrollTo(0, 0) }
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

    /** Choose LOCAL/CLOUD/HYBRID per settings; cloud side is StepFun; degrade to LOCAL if key missing. */
    private suspend fun resolveOcrEngine(app: ScreenPalApplication): OcrEngine {
        val settings = app.settingsRepository.userSettings.first()
        val mode = runCatching { OcrMode.valueOf(settings.ocrMode.uppercase()) }
            .getOrDefault(OcrMode.HYBRID)

        return withContext(Dispatchers.Default) {
            when (mode) {
                OcrMode.LOCAL -> PaddleOcrProvider.getInstance(app)
                OcrMode.CLOUD -> cloudOcrEngine(settings) ?: run {
                    Log.w(TAG, "Cloud OCR without StepFun credentials; degrading to local")
                    PaddleOcrProvider.getInstance(app)
                }
                OcrMode.HYBRID -> HybridOcrEngine(
                    PaddleOcrProvider.getInstance(app),
                    cloudProvider = cloudOcrEngine(settings),
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
            setTextIsSelectable(true)
        }
        resultMeta = TextView(this).apply {
            setTextColor(Color.parseColor("#99FFFFFF"))
            textSize = 12f
        }

        // 识别文本滚动区（2026-08-29-result-card-polish）：maxLines 改 ScrollView
        // 滚动，限高屏高 40%（onMeasure 超限截断），长文不再挤压按钮行。
        val maxTextHeightPx = resultTextMaxHeightPx(resources.displayMetrics.heightPixels)
        textScroll = object : ScrollView(this) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec)
                if (measuredHeight > maxTextHeightPx) {
                    setMeasuredDimension(measuredWidth, maxTextHeightPx)
                }
            }
        }
        textScroll!!.addView(resultText)

        // 胶囊按钮（2026-08-29-result-card-polish）：白底品牌紫、1.5dp 40% 透明描边、
        // 999dp 圆角、44dp 高、等权重均分、水平 4dp 间距、15sp medium；按压反馈由
        // pillPressed 的 StateListDrawable 提供。onClick 回调行为不变。
        val density = resources.displayMetrics.density

        fun actionButton(label: String, onClick: () -> Unit): Button =
            Button(this).apply {
                text = label
                textSize = PILL_TEXT_SP
                setTextColor(PILL_TEXT_COLOR)
                setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL))
                isAllCaps = false
                isSingleLine = true
                setPadding(8, 0, 8, 0)
                background = pillPressed(
                    PILL_STROKE_COLOR,
                    (PILL_STROKE_DP * density).roundToInt(),
                    PILL_CORNER_RADIUS_DP * density
                )
                // width=0 + weight=1 → 四按钮等宽均分；height 44dp。
                layoutParams = LinearLayout.LayoutParams(0, (PILL_HEIGHT_DP * density).roundToInt(), 1f).apply {
                    leftMargin = (PILL_MARGIN_DP * density).roundToInt()
                    rightMargin = (PILL_MARGIN_DP * density).roundToInt()
                }
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

        card.addView(textScroll)
        card.addView(resultMeta)
        card.addView(actions)
        return card
    }

    private inner class SelectionView(
        context: android.content.Context,
        private val mode: SelectionMode
    ) : View(context) {
        private val overlayPaint = Paint().apply {
            color = Color.parseColor("#8F000000")
            style = Paint.Style.FILL
        }

        // CONFIRMED 态挖孔：按选区（套索包围盒 / 矩形）整块 DST_OUT 挖透遮罩，
        // 与裁剪语义一致（挖的就是将被识别的区域）。
        private val confirmedHolePaint = Paint().apply {
            color = Color.BLACK
            style = Paint.Style.FILL
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

        // 白色副描边（2dp，画在紫描边之上成芯线）：浅背景看紫边、深背景看白芯，
        // 绘制期无遮罩时保证笔迹/矩形在任意壁纸上可辨。
        private val secondaryStrokePaint = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = SECONDARY_STROKE_DP * resources.displayMetrics.density
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            isAntiAlias = true
        }

        // RECT 模式（2026-08-29-selection-mode）：半透明紫填充 + 共用 4dp 紫描边。
        private val rectFillPaint = Paint().apply {
            color = Color.parseColor("#337B68EE")
            style = Paint.Style.FILL
        }

        /** 遮罩状态机当前状态（两模式共用）：初始即绘制期，无遮罩。 */
        private var phase: SelectionPhase = SelectionPhase.initial

        /** Sampled lasso points (>= [MIN_SAMPLE_DISTANCE_DP] apart). */
        private val strokePoints = mutableListOf<PointF>()

        /** Smooth path rebuilt from [strokePoints] via the midpoint quadTo technique. */
        private val strokePath = Path()

        /** RECT mode: normalized drag rect, non-null while a selection exists. */
        private var dragRect: RectF? = null
        private var rectStartX = 0f
        private var rectStartY = 0f
        private var isSelecting = false

        fun resetForReselection() {
            isSelecting = false
            phase = nextSelectionPhase(phase, SelectionPhaseAction.RESELECT)
            clearStroke()
            clearRect()
        }

        private fun clearStroke() {
            strokePoints.clear()
            strokePath.reset()
            invalidate()
        }

        private fun clearRect() {
            dragRect = null
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            val srcRect = Rect(0, 0, screenshotBitmap.width, screenshotBitmap.height)
            val dstRect = Rect(0, 0, width, height)
            canvas.drawBitmap(screenshotBitmap, srcRect, dstRect, null)

            // 遮罩时序（两模式统一）：绘制期只画选区高亮（原亮度）；确认后
            // 全屏变暗 + 按选区挖孔，再在挖孔上叠选区高亮保持所见一致。
            if (shouldDrawMask(phase)) {
                drawConfirmedMask(canvas)
            }
            drawSelectionVisuals(canvas)
        }

        /** CONFIRMED：单层 saveLayer 内画全屏遮罩并 DST_OUT 挖透选区。 */
        private fun drawConfirmedMask(canvas: Canvas) {
            val hole = confirmedHoleRect() ?: return
            val saveCount = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), overlayPaint)
            canvas.drawRect(hole, confirmedHolePaint)
            canvas.restoreToCount(saveCount)
        }

        /** 挖孔形状与裁剪语义一致：套索挖包围盒，矩形挖选中矩形；无选区不挖。 */
        private fun confirmedHoleRect(): RectF? = when (mode) {
            SelectionMode.RECT -> dragRect
            SelectionMode.LASSO -> computeBounds(strokePoints)
        }

        /** 选区高亮：套索紫描边+白芯 / 矩形半透明填充+描边+白芯（两模式同规格）。 */
        private fun drawSelectionVisuals(canvas: Canvas) {
            if (mode == SelectionMode.RECT) {
                val rect = dragRect ?: return
                canvas.drawRect(rect, rectFillPaint)
                canvas.drawRect(rect, strokePaint)
                canvas.drawRect(rect, secondaryStrokePaint)
            } else {
                if (strokePoints.isEmpty()) return
                canvas.drawPath(strokePath, strokePaint)
                canvas.drawPath(strokePath, secondaryStrokePaint)
            }
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    // 落笔即回绘制期：确认态的遮罩立刻退场。
                    phase = nextSelectionPhase(phase, SelectionPhaseAction.GESTURE_START)
                    isSelecting = true
                    if (mode == SelectionMode.RECT) {
                        val (x, y) = clampPoint(event.x, event.y)
                        rectStartX = x
                        rectStartY = y
                        dragRect = RectF(x, y, x, y)
                        invalidate()
                    } else {
                        strokePoints.clear()
                        strokePath.reset()
                        addSampledPoint(event.x, event.y)
                        invalidate()
                    }
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (isSelecting) {
                        if (mode == SelectionMode.RECT) {
                            val (x, y) = clampPoint(event.x, event.y)
                            dragRect = normalizeRect(rectStartX, rectStartY, x, y)
                            invalidate()
                        } else {
                            addSampledPoint(event.x, event.y)
                            invalidate()
                        }
                    }
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    if (isSelecting) {
                        isSelecting = false
                        if (mode == SelectionMode.RECT) {
                            finishRect()
                        } else {
                            finishStroke()
                        }
                    }
                    return true
                }
                MotionEvent.ACTION_CANCEL -> {
                    isSelecting = false
                    phase = nextSelectionPhase(phase, SelectionPhaseAction.REJECT)
                    if (mode == SelectionMode.RECT) {
                        clearRect()
                    } else {
                        clearStroke()
                    }
                    return true
                }
                else -> return super.onTouchEvent(event)
            }
        }

        /**
         * UP judgment (RECT): confirm via the normalized drag rect when it
         * clears the minimum size (48dp wide OR tall — shared with LASSO);
         * otherwise buzz and clear so the user can redraw. A single tap has a
         * zero-size rect and lands in the reject branch.
         */
        private fun finishRect() {
            val rect = dragRect
            val minSizePx = MIN_SELECTION_SIZE_DP * resources.displayMetrics.density
            if (rect != null && isSelectionLargeEnough(rect, minSizePx)) {
                phase = nextSelectionPhase(phase, SelectionPhaseAction.CONFIRM)
                invalidate()
                onSelectionConfirmed(
                    Rect(
                        rect.left.toInt(), rect.top.toInt(),
                        rect.right.toInt(), rect.bottom.toInt()
                    )
                )
            } else {
                phase = nextSelectionPhase(phase, SelectionPhaseAction.REJECT)
                if (dragRect?.isEmpty == true) {
                    // 轻点空白 = 退出框选流程（与 LASSO 的轻点语义一致）。
                    this@SelectionOverlayActivity.finish()
                } else {
                    performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    clearRect()
                }
            }
        }

        /**
         * UP judgment (LASSO): confirm via bounding box when it clears the
         * minimum size (48dp wide OR tall); otherwise buzz and clear so the
         * user can redraw. A single tap has a zero-size box and lands in the
         * reject branch, so it never triggers recognition.
         */
        private fun finishStroke() {
            val bounds = computeBounds(strokePoints)
            val minSizePx = MIN_SELECTION_SIZE_DP * resources.displayMetrics.density
            if (bounds != null && isSelectionLargeEnough(bounds, minSizePx)) {
                phase = nextSelectionPhase(phase, SelectionPhaseAction.CONFIRM)
                invalidate()
                onSelectionConfirmed(
                    Rect(
                        bounds.left.toInt(), bounds.top.toInt(),
                        bounds.right.toInt(), bounds.bottom.toInt()
                    )
                )
            } else {
                val tapped = strokePoints.size <= 1
                phase = nextSelectionPhase(phase, SelectionPhaseAction.REJECT)
                clearStroke()
                if (tapped) {
                    // 轻点空白 = 「我点错了」，退出整个框选流程。
                    this@SelectionOverlayActivity.finish()
                } else {
                    performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                }
            }
        }

        /** Shared out-of-screen clamp: both modes coerce touch points into the view. */
        private fun clampPoint(rawX: Float, rawY: Float): Pair<Float, Float> =
            rawX.coerceIn(0f, width.coerceAtLeast(0).toFloat()) to
                rawY.coerceIn(0f, height.coerceAtLeast(0).toFloat())

        private fun addSampledPoint(rawX: Float, rawY: Float) {
            // Clamp out-of-screen coordinates into the view (design edge case).
            val (x, y) = clampPoint(rawX, rawY)
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
        isAlive = false
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
