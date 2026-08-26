# Tasks: ScreenCaptureService

## Task 2.1 创建 ScreenCaptureService 类结构

- [x] 创建 `ScreenCaptureService.kt`，继承 `Service`
- [x] 声明 `companion object`（EXTRA 常量 + start/stop）
- [x] 实现 `onCreate`、`onBind`、`onStartCommand`、`onDestroy` 生命周期
- [x] `onStartCommand` 返回 `START_NOT_STICKY`（临时服务，不需要自动重启）
- **测试验收标准**：服务类结构完整，生命周期方法可正常调用

## Task 2.2 实现 MediaProjection 授权管理

- [x] 接收 FloatingWindowService 传入的授权数据（resultCode + data Intent）
- [x] 将 MediaProjection 实例保存在 ScreenPalApplication 中
- [x] 实现 `hasValidMediaProjection()` 检查方法
- [x] 实现 `releaseMediaProjection()` 释放方法
- [x] 授权被用户取消时通过 ResultReceiver 回传错误
- **测试验收标准**：
  - 单元测试：授权成功后 Application 持有有效 MediaProjection
  - 单元测试：授权取消后正确释放并回传错误

## Task 2.3 实现截图核心逻辑

- [x] 实现 `captureScreen(): Uri?` 挂起函数
- [x] 获取屏幕物理分辨率（displayMetrics）
- [x] 创建 VirtualDisplay 和 ImageReader
- [x] 实现 Image 到 Bitmap 的转换（Buffer → Bitmap，处理 rowPadding）
- [x] 将 Bitmap 压缩为 JPEG 写入 cacheDir/screenshots/
- [x] 通过 FileProvider 获取 content Uri
- [x] 添加 3 秒超时机制（Coroutine withTimeout）
- [x] 确保 Image 资源正确 close，Bitmap 及时 recycle
- **测试验收标准**：
  - 单元测试：坐标映射计算正确（1080×2400 截图，选区坐标映射无偏差）
  - 单元测试：rowPadding 处理正确（不产生黑边/变形）
  - 集成测试：在模拟器上截图并确认结果正确

## Task 2.4 实现前台服务通知

- [x] 创建通知渠道 `ScreenPal_Capture`（API 26+）
- [x] 实现 `startForegroundWithNotification()` 方法
- [x] 配置通知图标、标题、优先级
- [x] API 34+ 设置 `FOREGROUND_SERVICE_MEDIA_PROJECTION` 类型
- [x] 截图完成后立即 stopSelf()，通知自动消失
- **测试验收标准**：API 26+ 通知正常显示，截图完成后服务自动停止

## Task 2.5 实现授权流程集成

- [x] FloatingWindowService 准备 MediaProjection 授权 Intent
- [x] 通过 startActivityForResult 获取授权结果
- [x] 将 resultCode/data 传递给 ScreenCaptureService
- [x] 授权成功后 MediaProjection 保存在 Application 中
- [x] 授权成功后自动停止服务
- **测试验收标准**：完整授权流程在模拟器上可正常走通

## Task 2.6 实现 ResultReceiver 回调

- [x] 定义 ResultReceiver 回调协议（RESULT_OK / RESULT_ERROR）
- [x] 截图成功：回传 Uri
- [x] 截图失败：回传错误码 + 错误信息
- [x] FloatingWindowService 接收回调并处理
- **测试验收标准**：成功/失败场景下 ResultReceiver 正确触发

## Task 2.7 异常处理与边界情况

- [x] 处理授权被取消的情况
- [x] 处理 ImageReader 超时
- [x] 处理 Bitmap 转换中的 rowPadding
- [x] 处理 OOM（大截图 Bitmap 及时 recycle，JPEG 压缩）
- [x] 处理服务被系统杀死（不自动重启，下次点击时重新触发）
- [x] 处理 FileProvider 异常（fallback 到内存传递）
- **测试验收标准**：
  - 单元测试：超时场景返回错误码
  - 单元测试：授权取消后不持有无效 MediaProjection

## Task 2.8 构建验证

- [x] 执行 `./gradlew assembleDebug`
- [x] 在模拟器上手动验证 MediaProjection 授权流程
- [x] 确认截图结果正确（尺寸、清晰度、Uri 传递）
- [x] 确认服务生命周期正确（启动 → 截图 → 停止）
- **测试验收标准**：构建通过，模拟器上截图流程完整可用
