# 2026-08-29-a11y-screenshot

## Why

用户质疑"必须录屏吗，有没有更好的方式"。现状 MediaProjection 路径有两个痛点：① 每次进程重启后弹系统 "Start recording" 授权对话框（吓人且打断）；② 名分是"录屏"，与产品"读屏辅助"的气质不符。Android 11+ 提供 `AccessibilityService.takeScreenshot()`：**静默截屏、零弹窗**，一次性开启无障碍服务后永久有效——读屏类产品用无障碍能力名正言顺。

## What Changes

- 新增 `ScreenPalAccessibilityService`（canTakeScreenshot 能力，最小事件订阅）：`takeScreenshot()` → HardwareBuffer → Bitmap → 复用现有 FileProvider/overlay 链
- 截屏路由：悬浮球点击 → API≥30 且无障碍服务已启用 → 无障碍静默截屏（**零弹窗**）；否则回落 MediaProjection 路径（Android 10 及以下 / 未开启时兜底，行为同现状）
- 权限引导：无障碍未开启时，点球 → 引导对话框（说明用途 + 直跳系统无障碍设置），不再直接弹录制授权
- Manifest：service 声明（BIND_ACCESSIBILITY_SERVICE + accessibility-meta XML）
- 被否选项：纯 MediaProjection 维持现状（弹窗痛点不解决）；ReplayKit 式方案（Android 无对应）

## 成功标准

- Android 11+ 模拟器：开启无障碍后，点悬浮球 → **无任何系统弹窗**直接进框选，识别播报链路完整
- 无障碍未开启 → 引导对话框可直跳系统设置；开启后回来重试成功
- API<30 路径回归：MediaProjection 弹窗行为不变
- 限流容错：连续点球触发 INTERVAL_TIME_SHORT 时提示"稍候再试"不崩溃

## 优先级

- P1：体验核心痛点，与 selection-mode 同批交付"所见即所画"。

## 依赖

- 无硬依赖（独立于其他三个 change；与 permission-tri-card 的第三行状态检查共用无障碍启用判断）
