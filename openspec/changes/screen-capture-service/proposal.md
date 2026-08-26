# Proposal: ScreenCaptureService

## Summary

实现 `ScreenCaptureService` 临时前台服务，封装 Android MediaProjection API 的屏幕截图能力，提供稳定的截图接口供悬浮窗调用。

## Motivation

- 框选模式必须截取当前屏幕，MediaProjection 是 Android 官方唯一的跨应用截图 API
- MediaProjection 授权结果需持久化保存，避免每次框选都重新请求用户授权
- 截图操作需要在前台服务中执行（API 34+ 强制要求 `FOREGROUND_SERVICE_MEDIA_PROJECTION`）
- VirtualDisplay 尺寸需要精确匹配屏幕物理分辨率，否则 OCR 坐标映射会出错

## Goals

1. 用户首次使用时触发 MediaProjection 授权请求
2. 授权结果持久化保存（通过 DataStore + Application 持有，不用全局 object）
3. 每次截图前验证授权有效性，失效时自动降级并提示重新授权
4. 截图服务为**临时服务**：启动 → 截图 → 回调结果 → stopSelf()（非持久服务）
5. 正确处理 Image 的 acquire/release 生命周期，避免内存泄漏
6. 截图结果通过 FileProvider 传递 Uri，避免 Binder 1MB 限制

## Non-goals

- 不实现连续录屏（仅单帧截图）
- 不提供截图保存到相册（纯内存传递，用完即删）
- 不处理多屏幕 / 虚拟屏幕场景（仅主屏幕）

## 依赖

- 前置：`project-scaffold`（需要 FileProvider、DataStore、基础工具类）
