# Design: FloatingWindow + SelectionOverlay

## 架构概览

```
┌───────────────────┐     startService +      ┌──────────────────────┐
│ FloatingWindow    │ ─── ResultReceiver ────▶ │ ScreenCaptureService  │
│ Service           │ ◀────────────────────── │ (截图 → FileProvider  │
│ (前台服务，持久)    │   screenshot_uri        │  Uri → stopSelf)      │
└────────┬──────────┘                         └──────────────────────┘
         │
         │ startActivityForResult（携带 screenshot_uri）
         ▼
┌───────────────────┐     setResult(rect)      ┌──────────────────┐
│ SelectionOverlay  │ ◀──────────────────────── │ FloatingWindow   │
│ Activity          │ ────────────────────────▶ │ Service          │
│ (透明 Activity)   │    确认选区 Rect          │ (接收 Rect)      │
└────────┬──────────┘                          └────────┬─────────┘
         │                                               │
         │ Canvas 手势框选                               │ 触发 OCR
         ▼                                               ▼
┌───────────────────┐                           ┌──────────────────┐
│ SelectionCanvas   │                           │ OcrEngine        │
│ (Compose Canvas)  │                           │ (识别文字)        │
│ 半透明遮罩 + 选区  │                           └──────────────────┘
└───────────────────┘
```

## FloatingWindowService 设计

### 类结构
```kotlin
class FloatingWindowService : Service() {
    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: View

    companion object {
        fun start(context: Context)
        fun stop(context: Context)
        fun isRunning(context: Context): Boolean
    }

    override fun onBind(intent: Intent): IBinder? = null
    override fun onCreate()
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int
    override fun onDestroy()

    private fun createFloatingView(): View
    private fun updateFloatingViewLayout(x: Int, y: Int)
    private fun setBallState(state: BallState)
    private fun onScreenshotReady(uri: Uri?)
}
```

### 悬浮球布局

悬浮球使用传统 View（避免在 Service 中引入 Compose）：
```kotlin
val inflater = LayoutInflater.from(this)
val view = inflater.inflate(R.layout.view_floating_ball, null)
```

**view_floating_ball.xml** 结构：
```xml
<FrameLayout> <!-- 56dp x 56dp 圆形 -->
    <ImageView>  <!-- 背景渐变圆形 -->
    <ImageView>  <!-- SVG 波形图标 -->
    <View>       <!-- 状态指示点（右上角） -->
    <TextView>   <!-- 状态 Tooltip（悬浮时显示） -->
</FrameLayout>
```

### 布局参数
```kotlin
val params = WindowManager.LayoutParams(
    56.dpToPx(), 56.dpToPx(),
    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
    PixelFormat.TRANSLUCENT
).apply {
    gravity = Gravity.TOP or Gravity.END
    x = 16.dpToPx()
    y = 200.dpToPx()
}
```

### 拖拽实现
- 使用 `View.OnTouchListener` 监听 touch 事件
- `ACTION_DOWN`：记录起始位置
- `ACTION_MOVE`：计算位移，更新 LayoutParams.x/y
- `ACTION_UP`：判断是否超过移动阈值（8dp），超过则判定为拖拽，否则判定为点击
- 边缘吸附：拖拽到屏幕边缘 30dp 范围内时自动吸附

### 三态切换
```kotlin
sealed class BallState {
    object Idle : BallState()       // 紫色渐变
    object Recognizing : BallState() // 橙色脉冲
    object Speaking : BallState()   // 绿色脉冲
}
```

通过修改 View 的背景 drawable 和动画来实现状态切换。

## SelectionOverlayActivity 设计

### 类结构
```kotlin
class SelectionOverlayActivity : ComponentActivity() {
    private val viewModel: SelectionViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val screenshotUri = intent.getParcelableExtra(EXTRA_SCREENSHOT_URI)
        setContent {
            SelectionScreen(
                screenshotUri = screenshotUri,
                onSelectionComplete = { rect -> handleSelection(rect) },
                onCancel = { finishWithCancel() }
            )
        }
    }

    private fun handleSelection(rect: Rect) {
        // 1. 从 Uri 解码 Bitmap（如果还没解码）
        // 2. 计算裁剪坐标
        // 3. 返回 Rect 给 FloatingWindowService
        val result = Intent().apply {
            putExtra(EXTRA_SELECTION_RECT, rect)
        }
        setResult(RESULT_OK, result)
        finish()
    }
}
```

### Compose UI 结构
```kotlin
@Composable
fun SelectionScreen(
    screenshotUri: Uri?,
    onSelectionComplete: (Rect) -> Unit,
    onCancel: () -> Unit
) {
    val bitmap = remember(screenshotUri) {
        screenshotUri?.let { uri ->
            contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 底层：截图
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds
            )
        }

        // 上层：Canvas 遮罩 + 选区
        SelectionCanvas(
            onSelectionComplete = onSelectionComplete,
            onCancel = onCancel
        )
    }
}
```

### SelectionCanvas 手势处理
```kotlin
@Composable
fun SelectionCanvas(
    onSelectionComplete: (Rect) -> Unit,
    onCancel: () -> Unit
) {
    var startPoint by remember { mutableStateOf<Offset?>(null) }
    var currentPoint by remember { mutableStateOf<Offset?>(null) }
    var isDrawing by remember { mutableStateOf(false) }

    Canvas(modifier = Modifier
        .fillMaxSize()
        .pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown()
                startPoint = it.position
                currentPoint = it.position
                isDrawing = true

                onGesture { event ->
                    currentPoint = event.position
                    waitForUpOrCancellation()
                }

                val endEvent = waitForUpOrCancellation() ?: return@awaitEachGesture
                isDrawing = false

                val start = startPoint!!
                val end = endEvent.position
                val rect = Rect(
                    min(start.x, end.x),
                    min(start.y, end.y),
                    max(start.x, end.x),
                    max(start.y, end.y)
                )

                if (rect.width > 48.dp && rect.height > 48.dp) {
                    onSelectionComplete(rect)
                }
            }
        }
    ) {
        // 绘制半透明遮罩 + 选区镂空 + 边框
        drawSelectionOverlay(startPoint, currentPoint, isDrawing)
    }
}
```

### Canvas 绘制逻辑
1. 全屏填充半透明黑色遮罩（`rgba(0,0,0,0.55)`）
2. 用 `clearRect` 或 `drawRect` + `PorterDuff.Mode.Clear` 镂空选区
3. 绘制选区边框（紫色发光，2dp 宽）
4. 四角绘制圆形手柄（7dp 直径）
5. 选区上方显示尺寸标签（如 "320 × 240"）

### 坐标缩放计算

```kotlin
// SelectionViewModel 中计算实际 Bitmap 坐标
fun calculateCropRect(
    selectionRect: Rect,      // 屏幕坐标
    bitmapWidth: Int,
    bitmapHeight: Int,
    screenWidth: Int,
    screenHeight: Int
): Rect {
    val scaleX = bitmapWidth.toFloat() / screenWidth
    val scaleY = bitmapHeight.toFloat() / screenHeight
    return Rect(
        (selectionRect.left * scaleX).toInt().coerceIn(0, bitmapWidth),
        (selectionRect.top * scaleY).toInt().coerceIn(0, bitmapHeight),
        (selectionRect.right * scaleX).toInt().coerceIn(0, bitmapWidth),
        (selectionRect.bottom * scaleY).toInt().coerceIn(0, bitmapHeight)
    )
}
```

## 生命周期协调

```
FloatingWindowService
    │ 悬浮球点击（不是拖拽）
    ▼
1. 隐藏悬浮球
2. 启动 ScreenCaptureService（传入 ResultReceiver）
3. ScreenCaptureService 截图 → FileProvider Uri
4. ResultReceiver 收到 Uri
    │
    ▼
5. startActivityForResult(SelectionOverlayActivity, screenshotUri)
    │
    ▼ 用户框选确认
6. SelectionOverlayActivity.setResult(RESULT_OK, rect)
7. finish()
    │
    ▼
8. FloatingWindowService.onActivityResult 收到 Rect
9. 触发 OCR 流程
```

## 性能与体验

- 截图通过 FileProvider Uri 传递，避免 Binder 1MB 限制
- Compose Canvas 使用 `drawWithContent` 优化绘制性能
- 框选过程中避免不必要的重组（使用 `derivedStateOf`）
- Bitmap 在 Activity onDestroy 时主动 recycle
- 框选最小尺寸改为 48dp × 48dp（更符合手指操作习惯）

## 测试策略

### 测试金字塔

![测试金字塔](../docs/design/test-pyramid.svg)


### 分层策略

| 层级 | 目标 | 工具 | 覆盖率目标 |
|------|------|------|-----------|
| 单元测试 | SelectionViewModel 坐标映射、Canvas 绘制逻辑 | JUnit 4 + MockK | 85% |
| 集成测试 | 悬浮窗显示/拖拽、框选手势识别 | AndroidX Test + UI Automator | 手动验证 |
| 手动验证 | 悬浮球点击→截图→框选→返回完整流程 | 模拟器/真机 | 100% 流程通过 |

### 测试数据

- 模拟屏幕尺寸（1080×2400、720×1600）
- 模拟选区坐标（标准、边界溢出、极小选区）
- 模拟截图 Bitmap（通过 Bitmap.createBitmap 创建）

### 边界条件

- 选区小于最小阈值（48dp × 48dp）→ 不触发回调
- 选区超出屏幕边界 → clamp 处理
- 快速点击（防止重复触发）
