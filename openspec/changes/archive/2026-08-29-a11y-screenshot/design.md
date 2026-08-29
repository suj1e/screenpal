# 2026-08-29-a11y-screenshot 设计

## 关键事实（已核实官方文档/CTS）

- `AccessibilityService.takeScreenshot(displayId, executor, callback)`：API 30+；服务配置需 `android:canTakeScreenshot="true"`（flagRequestTakeScreenshot）
- 限流：两次调用间隔过短回调 `ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT`（AOSP 常量 ≈333ms，约 3 次/秒）——手动点球节奏远低于限流
- 静默性：直接读硬件缓冲，无对话框、无通知、无快门声
- API<30 不存在该方法 → MediaProjection 兜底（参考先例：droidVNC-NG 同款双通道）

## 方案设计

**图示**：[diagrams/screenshot-routing.svg](diagrams/screenshot-routing.svg)

1. **`ScreenPalAccessibilityService : AccessibilityService`**：
   - 配置 XML（`res/xml/accessibility_service_config.xml`）：`canTakeScreenshot=true`、`accessibilityEventTypes="typeWindowsChanged"`（最小订阅，服务可常驻）、`notificationTimeout` 默认
   - 静态实例 `instance`（onServiceConnected 赋值/onUnbind 置空）+ `fun captureCurrentScreen(onBitmap: (Bitmap?) -> Unit)`：takeScreenshot → `HardwareRenderer`/`ScreenshotResult.hardwareBuffer` → `Bitmap.wrapHardwareBuffer` → 软位图拷贝（HardwareBuffer 及时 close）
2. **启用判断**：`AccessibilityManager.getEnabledAccessibilityServiceList(FEEDBACK_ALL_MASK)` 含本服务 → enabled（供路由与权限卡片共用，静态 helper 放 `util/AccessibilityHelper`）
3. **路由**（`FloatingWindowService.onBallClicked` 内）：API≥30 && a11yEnabled → `captureCurrentScreen` 成功 → 直接 `launchSelection(uri)`；失败（含 INTERVAL_SHORT）→ Toast"截屏太频繁/失败，请重试"；a11y 未开启或 API<30 → 引导/回落：
   - API≥30 但未开启：AlertDialog「开启无障碍后可免授权一键识读」→ 去开启（ACTION_ACCESSIBILITY_SETTINGS）/ 本次仍用录屏（老路径）
   - API<30：直接走 MediaProjection（现状）
4. **现状不动**：CaptureConsentActivity/ScreenCaptureService 原样保留为兜底路径。

## 接口 / 数据契约

- Manifest 新增 service（`permission=BIND_ACCESSIBILITY_SERVICE`、intent-filter accessibilityservice、meta-data 指向配置 XML）
- 无新 DataStore 键（无障碍状态实时查询系统）

## 实施步骤

1. service + 配置 XML + Manifest + AccessibilityHelper（启用判断）+ 单测（helper 判定、API 30 分支路由）
2. captureCurrentScreen（buffer→bitmap）+ FloatingWindowService 路由接线
3. 未开启引导对话框（直跳设置）
4. 模拟器验收：开启无障碍 → 点球零弹窗进框选；关闭 → 引导出现

## 性能优化点

截图直接来自硬件缓冲，比 VirtualDisplay 管线更轻；无 FGS 切换开销。

## 设计模式建议

策略分流（截屏提供者二选一），对上层（launchSelection）零改动。

## 风险与 Trade-off

- **风险：无障碍服务被系统/厂商杀**——回落 MediaProjection 引导，不阻塞
- **风险：无障碍开启入口深**——引导对话框直跳系统设置页
- **风险：部分 ROM 对无障碍服务限制后台**——同上回落
- **待确认**：无

## 测试策略

| 层 | 内容 | 方式 |
|----|------|------|
| 单元测试 | AccessibilityHelper 启用判定（mock AccessibilityManager）、路由分支（API/a11y 状态矩阵） | JVM + Robolectric |
| 构建门禁 | assembleDebug | 本地 |
| 模拟器验收 | Android 14 模拟器：开无障碍 → 点球零弹窗 → 框选识别；关无障碍 → 引导 + 录屏兜底 | 录屏 + logcat |

边界/异常：INTERVAL_TIME_SHORT 提示重试；buffer null 容错；服务被杀后 capture 返回 null。
