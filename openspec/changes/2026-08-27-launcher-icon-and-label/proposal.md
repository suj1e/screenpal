# 2026-08-27-launcher-icon-and-label

## 需求复述

用户反馈两个现象：① 模拟器桌面上看不到 ScreenPal；② 应用看起来没有图标（应用抽屉里渲染为纯白方块，系统应用选择器中同样如此）。

## 要解决的问题

1. **图标渲染为白块（真 bug）**，根因两层：
   - `AndroidManifest.xml` 的 `android:icon="@drawable/ic_launcher_foreground"` 引用错误：直接指向自适应图标的前景层 selector，而非 `@mipmap/ic_launcher` 自适应图标入口（`mipmap-anydpi-v26/ic_launcher.xml` 已存在但从未被引用）。
   - 图标资源本身是占位空壳：`drawable/ic_launcher_foreground.xml` 为 selector → `ic_launcher_background`；`drawable/ic_launcher_background.xml` 为 selector → `@android:color/transparent`（全透明）。无论 manifest 引用哪一层，渲染结果都是透明。
2. **抽屉内 App 名显示为包名 `com.suj1e.screenpal`**：`<application>` 未设置 `android:label`。用户已拍板采用中文 App 名「**念念**」（演绎：把屏幕**念**给你听 + 悬浮球**黏**在屏上；被否候选：屏声听、屏伴、声临其屏）。
3. **桌面上没有快捷方式（非 bug，不改代码）**：Android 机制即新装应用只进应用抽屉，长按图标拖拽才会在桌面创建快捷方式。本 change 仅在文档中说明，不做任何"自动上桌面"的代码行为。

## 成功标准

- 应用抽屉、系统应用选择器、最近任务中 ScreenPal 图标渲染为悬浮球同款视觉（紫色渐变圆底 + 白色声波纹），非白块。
- 抽屉与系统设置应用列表中名称显示为中文「念念」。
- API 24/25（minSdk）设备上图标有可渲染的兜底资源，构建无资源链接错误。
- 通知栏小图标（复用 `ic_launcher_foreground`）仍符合通知图标规范（alpha 图形）。
- 现有单元测试全部通过；manifest 断言测试补充 icon/label 检查。

## 优先级

- P1：图标是产品的第一视觉触点，当前白块 + 包名直接显得未完成；修复仅涉及资源与 manifest，无逻辑风险。
