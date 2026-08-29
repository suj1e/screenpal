# 2026-08-29-a11y-screenshot 任务清单

- [x] 1. ScreenPalAccessibilityService + 配置 XML + Manifest 声明 + AccessibilityHelper 启用判定 + 单测
  - 验收：服务可被系统启用；helper 判定单测绿
- [x] 2. captureCurrentScreen（HardwareBuffer→Bitmap）+ FloatingWindowService 截屏路由（a11y 优先/MediaProjection 兜底）+ 路由矩阵单测
  - 验收：API≥30+已启用 → 零弹窗截屏；未启用 → 引导对话框
- [ ] 3. 未开启引导对话框（用途说明 + 直跳系统无障碍设置 + 本次仍用录屏）
  - 验收：两出口均可用
- [ ] 4. 模拟器验收（zapply 执行）：零弹窗链路 + 引导链路 + API<30 路径代码审查
  - 验收：录屏 + logcat
