# Design: ScreenCaptureService

## 架构位置

```
FloatingWindowService (调用方)
    │
    ▼
ScreenCaptureService (临时前台服务)
    │
    ▼
MediaProjection → VirtualDisplay → ImageReader → Image → Bitmap
    │
    ▼ 写入 cacheDir
FileProvider Uri ──▶ SelectionOverlayActivity（读取截图）
```

## 核心类设计

### ScreenCaptureService

```kotlin
class ScreenCaptureService : Service() {
    companion object {
        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"
        const val EXTRA_RESULT_RECEIVER = "extra_result_receiver"

        fun start(context: Context, resultReceiver: ResultReceiver)
        fun stop(context: Context)
    }

    private var mediaProjection: MediaProjection? = null
    private var resultReceiver: ResultReceiver? = null

    override fun onBind(intent: Intent): IBinder? = null
    override fun onCreate()
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int
    override fun onDestroy()

    // 内部方法
    private fun startForegroundWithNotification()
    private fun setupMediaProjection(resultCode: Int, data: Intent)
    private suspend fun captureScreen(): Uri?
    private fun releaseMediaProjection()
}
```

### 授权管理

授权结果不放在全局 `object` 中，改为由 `ScreenPalApplication` 持有：

```kotlin
class ScreenPalApplication : Application() {
    // 授权状态
    var mediaProjection: MediaProjection? = null
        private set

    fun setMediaProjection(projection: MediaProjection) {
        mediaProjection = projection
    }

    fun hasValidMediaProjection(): Boolean {
        return mediaProjection != null
    }
}
```

或使用 DataStore 持久化授权标记（实际 MediaProjection 实例无法序列化，仅保存"已授权"标记，具体实例在内存中管理）。

### 截图流程

```
1. FloatingWindowService 调用 start(callback)
2. 验证 Application.mediaProjection 是否存活
3. 创建 VirtualDisplay（尺寸 = 屏幕物理分辨率）
4. 创建 ImageReader（PixelFormat.RGBA_8888）
5. 等待 onImageAvailable 回调（最多 3 秒超时）
6. 从 ImageReader 获取 Image
7. 将 Image 转换为 Bitmap（通过 Image.Plane 的 Buffer，处理 rowPadding）
8. 将 Bitmap 压缩为 JPEG 写入 cacheDir/screenshots/
9. 通过 FileProvider 获取 content Uri
10. 通过 ResultReceiver 回传 Uri 给调用方
11. 关闭 Image 和 VirtualDisplay
12. stopSelf() 停止服务
```

### 关键实现细节

#### VirtualDisplay 尺寸计算
```kotlin
val metrics = Resources.getSystem().displayMetrics
val width = metrics.widthPixels
val height = metrics.heightPixels
```

#### Image → Bitmap 转换
```kotlin
val image = imageReader.acquireNextImage()
val buffer = image.planes[0].buffer
val pixelStride = image.planes[0].pixelStride
val rowStride = image.planes[0].rowStride
val rowPadding = rowStride - pixelStride * width

val bitmap = Bitmap.createBitmap(
    width + rowPadding / pixelStride,
    height,
    Bitmap.Config.ARGB_8888
)
bitmap.copyPixelsFromBuffer(buffer)

// 裁剪掉 padding
val cropped = Bitmap.createBitmap(bitmap, 0, 0, width, height)
image.close()
bitmap.recycle() // 释放原始带 padding 的 bitmap
```

#### Bitmap → File → Uri 传递
```kotlin
private suspend fun saveBitmapAndGetUri(bitmap: Bitmap): Uri = withContext(Dispatchers.IO) {
    val screenshotsDir = File(cacheDir, "screenshots").apply { mkdirs() }
    val file = File(screenshotsDir, "screenshot_${System.currentTimeMillis()}.jpg")
    FileOutputStream(file).use { out ->
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
    }
    bitmap.recycle()
    FileProvider.getUriForFile(
        this@ScreenCaptureService,
        "${applicationContext.packageName}.fileprovider",
        file
    )
}
```

#### ResultReceiver 回传
```kotlin
// 启动时传入 ResultReceiver
val receiver = object : ResultReceiver(null) {
    override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
        if (resultCode == RESULT_OK) {
            val uri = resultData?.getParcelableExtra("screenshot_uri")
            // 通知 FloatingWindowService 截图完成
        }
    }
}
start(this, receiver)
```

### 前台服务通知

- 截图操作期间显示前台通知（约 3-5 秒）
- 通知渠道：`ScreenPal_Capture`
- 截图完成后立即 `stopSelf()`，通知自动消失
- API 34+ 设置 `FOREGROUND_SERVICE_MEDIA_PROJECTION` 类型

### 异常处理

| 异常场景 | 处理方式 |
|---------|---------|
| MediaProjection 授权被用户取消 | 通过 ResultReceiver 回传错误码，FloatingWindowService 提示重新授权 |
| ImageReader 超时（3 秒） | 返回错误码，记录日志 |
| Image 转换失败 | 返回错误码，释放所有资源 |
| 服务被系统杀死 | onStartCommand 返回 START_NOT_STICKY（不需要自动重启，下次点击时重新启动） |
| 内存不足 | Bitmap 及时 recycle，使用 JPEG 压缩减少内存占用 |
| FileProvider 异常 | 回退到内存传递（不推荐，仅在极端情况下使用） |

## 依赖

- 前置：`project-scaffold`（FileProvider、DataStore、Application 基类）

## 测试策略

### 测试金字塔

![测试金字塔](../docs/design/test-pyramid.svg)


### 分层策略

| 层级 | 目标 | 工具 | 覆盖率目标 |
|------|------|------|-----------|
| 单元测试 | 授权状态检查、坐标映射计算、异常处理 | JUnit 4 + MockK | 85% |
| 集成测试 | MediaProjection 授权流程（需模拟器/真机） | AndroidX Test + Robolectric | 手动验证 |
| 手动验证 | 截图功能、Uri 传递、内存释放 | 模拟器/真机 | 100% 流程通过 |

### 测试数据

- 模拟 1080×2400 截图 Bitmap
- 模拟授权成功/失败场景
- 模拟 ImageReader 超时场景

### 边界条件

- 授权被用户取消
- ImageReader 无可用 Image（超时）
- 极端屏幕尺寸（折叠屏、平板）
