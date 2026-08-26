# Tasks: FloatingWindow + SelectionOverlay

## Task 3.1 创建 FloatingWindowService 框架

- [ ] 创建 `FloatingWindowService.kt`，继承 `Service`
- [ ] 实现 `onCreate`、`onBind`、`onStartCommand`、`onDestroy`
- [ ] 实现 `start()` / `stop()` / `isRunning()` 静态方法
- [ ] 实现 `createFloatingView()` 方法
- **测试验收标准**：服务可正常启动和停止

## Task 3.2 实现悬浮窗 UI

- [ ] 创建 `view_floating_ball.xml` 布局（56dp 圆形 FrameLayout + ImageView + 状态指示点）
- [ ] 创建悬浮球背景 drawable（圆形渐变，紫色主色）
- [ ] 加载 SVG 波形图标（通过 Vector Asset 或自定义 Drawable）
- [ ] 实现 `updateFloatingViewLayout()` 方法
- **测试验收标准**：悬浮窗在屏幕上正确显示，UI 元素完整

## Task 3.3 实现 WindowManager 悬浮窗

- [ ] 创建 `WindowManager.LayoutParams`（TYPE_APPLICATION_OVERLAY + FLAG_NOT_FOCUSABLE）
- [ ] 将悬浮 View 添加到 WindowManager
- [ ] 实现悬浮窗位置初始化（右侧垂直居中）
- [ ] 处理权限未授予时的降级提示
- [ ] 权限检查：启动前验证 SYSTEM_ALERT_WINDOW
- **测试验收标准**：
  - 权限已授予：悬浮窗正常显示
  - 权限未授予：提示用户跳转设置页

## Task 3.4 实现拖拽逻辑

- [ ] 为悬浮 View 设置 `OnTouchListener`
- [ ] 实现 `ACTION_DOWN` / `MOVE` / `UP` 手势判断
- [ ] 计算位移并更新 LayoutParams.x/y
- [ ] 实现 8dp 移动阈值判断（区分点击和拖拽）
- [ ] 实现边缘吸附（靠近屏幕边缘 30dp 自动对齐）
- **测试验收标准**：
  - 拖拽距离 < 8dp 判定为点击
  - 拖拽距离 > 8dp 判定为拖拽
  - 边缘吸附生效

## Task 3.5 实现点击触发截图流程

- [ ] 悬浮球点击后隐藏悬浮球
- [ ] 启动 ScreenCaptureService（传入 ResultReceiver）
- [ ] ResultReceiver 收到截图 Uri 后启动 SelectionOverlayActivity
- [ ] 截图失败时恢复悬浮球显示并提示错误
- **测试验收标准**：点击悬浮球后正确启动截图流程

## Task 3.6 实现三态切换

- [ ] 定义 `BallState` sealed class（Idle / Recognizing / Speaking）
- [ ] 实现 `setBallState()` 方法，切换背景颜色和动画
- [ ] Idle 状态：紫色渐变 + 静态
- [ ] Recognizing 状态：橙色渐变 + 脉冲动画
- [ ] Speaking 状态：绿色渐变 + 脉冲动画 + 波形条
- [ ] 使用 `ObjectAnimator` 或 `ValueAnimator` 实现脉冲
- **测试验收标准**：三种状态切换正确，动画流畅

## Task 3.7 实现前台服务通知

- [ ] 创建通知渠道 `ScreenPal_Floating`
- [ ] 实现 `startForeground` 调用
- [ ] 配置通知图标和文字
- [ ] API 34+ 使用正确的 foregroundServiceType
- **测试验收标准**：前台通知正常显示，服务保活

## Task 3.8 创建 SelectionOverlayActivity

- [ ] 创建 `SelectionOverlayActivity.kt`，继承 `ComponentActivity`
- [ ] 设置透明主题（在 AndroidManifest 中配置）
- [ ] 从 Intent 获取截图 Uri（Parcelable）
- [ ] 实现 `setContent` 渲染 SelectionScreen
- [ ] 实现 `onActivityResult` 接收 FloatingWindowService 的结果
- **测试验收标准**：Activity 正常启动，截图 Uri 正确传递

## Task 3.9 实现 SelectionScreen Compose UI

- [ ] 实现 `SelectionScreen` Composable
- [ ] 从 Uri 解码 Bitmap（使用 contentResolver）
- [ ] 底层展示截图 Image（ContentScale.FillBounds）
- [ ] 上层叠加 SelectionCanvas
- [ ] 顶部添加取消按钮和标题
- [ ] 添加初始引导提示动画
- **测试验收标准**：截图正确全屏展示，UI 元素完整

## Task 3.10 实现 SelectionCanvas 手势框选

- [ ] 使用 `pointerInput` + `awaitEachGesture` 实现手势
- [ ] 实现 `awaitFirstDown` 记录起点
- [ ] 实现 `onGesture` 实时更新终点
- [ ] 实现 `waitForUpOrCancellation` 确认结束
- [ ] 最小选区阈值判断（48dp × 48dp，比原方案增大）
- **测试验收标准**：
  - 手势识别正确（down → move → up）
  - 小于 48dp 的选区不触发回调
  - 反向拖拽（右下到左上）正确计算 Rect

## Task 3.11 实现 Canvas 绘制

- [ ] 绘制全屏半透明遮罩（深色 + 紫色调）
- [ ] 实现选区镂空（clearRect）
- [ ] 绘制选区边框（紫色发光效果）
- [ ] 绘制四角手柄（圆形 + 白色描边）
- [ ] 绘制尺寸标签（选区宽高，如 "320 × 240"）
- [ ] 使用 `drawWithContent` 优化性能
- **测试验收标准**：Canvas 绘制正确，性能流畅（60fps）

## Task 3.12 实现 SelectionViewModel

- [ ] 创建 `SelectionViewModel`，持有截图 Uri 和选区状态
- [ ] 实现 `calculateCropRect()` 坐标映射方法
- [ ] 处理屏幕尺寸与 Bitmap 尺寸的缩放比例
- [ ] 实现边界 clamp 逻辑
- **测试验收标准**：
  - 单元测试：标准坐标映射正确
  - 单元测试：边界溢出 clamp 正确

## Task 3.13 实现结果返回

- [ ] 框选确认后，通过 setResult 返回 Rect 坐标给 FloatingWindowService
- [ ] FloatingWindowService 在 onActivityResult 中接收结果
- [ ] 调用 OCR 引擎进行识别（Change 4）
- [ ] 截图临时文件在使用后删除
- **测试验收标准**：Rect 坐标正确返回，临时文件已清理

## Task 3.14 构建验证

- [ ] 执行 `./gradlew assembleDebug`
- [ ] 在模拟器上验证悬浮窗显示和拖拽
- [ ] 验证框选 Activity 启动和手势识别
- [ ] 验证坐标映射准确性
- **测试验收标准**：构建通过，核心流程在模拟器上可用
