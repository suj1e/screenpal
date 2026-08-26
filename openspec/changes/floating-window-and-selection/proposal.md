# Proposal: FloatingWindow + SelectionOverlay

## Summary

实现 Android 悬浮窗前台服务（FloatingWindowService）和透明框选 Activity（SelectionOverlayActivity），完成用户从点击悬浮球到完成区域框选的核心交互流程。

## Motivation

- 悬浮球是用户触发识别的最便捷入口，必须在任何应用界面都可访问
- 框选模式需要覆盖全屏，同时展示截图和接收手势输入
- 框选交互（拖拽绘制矩形）需要精确的手势处理和视觉反馈
- 悬浮球需要区分"点击"和"拖拽"，避免误触
- 截图通过 FileProvider Uri 传递，避免 Binder 1MB 限制

## Goals

1. FloatingWindowService 作为前台服务持续运行，持有 TYPE_APPLICATION_OVERLAY 悬浮窗
2. 悬浮球支持拖拽移动（全屏幕自由拖动 + 边缘吸附）
3. 悬浮球点击时触发识别流程，区分点击和拖拽
4. 悬浮球三态视觉反馈：待机（紫色）、识别中（橙色脉冲）、播报中（绿色脉冲）
5. SelectionOverlayActivity 启动后展示全屏截图 + 半透明遮罩 + 手势框选
6. 手指按下记录起点，移动时实时绘制矩形选区（遮罩镂空 + 发光边框）
7. 手指松开确认选区，显示底部操作栏（取消 / 识别）
8. 框选坐标正确映射到截图 Bitmap（考虑屏幕缩放）

## Non-goals

- 不支持悬浮球最小化为小圆点（首版保持 56dp 固定大小）
- 不实现悬浮球自动隐藏（首版始终显示）
- 不实现多选区域（单矩形选区）
- 不提供选区缩放/旋转手柄（仅四角定位点）

## 依赖

- 前置：`project-scaffold`（需要 FileProvider、PermissionHelper、主题资源）
- 前置：`screen-capture-service`（需要截图 Uri 回传机制）
