# 2026-08-27-brand-copy-niannian 任务清单

- [ ] 1. strings.xml 新增 4 个品牌词条（app_title / notification_floating_title / channel_floating_name / channel_capture_name，值见 design.md）
  - 验收：4 词条存在、值与 design.md 文案逐字一致；含「念念」
- [ ] 2. 消费方替换：MainActivity 标题资源化（stringResource）、FloatingWindowService 通知标题与渠道名、ScreenCaptureService 渠道名改 getString、SelectionOverlayActivity 剪贴板 label 字面量改「念念」
  - 验收：4 个源文件用户可见文案无 "ScreenPal"；渠道 ID 常量、Log TAG、UTTERANCE_ID 原样保留
- [ ] 3. 新增 BrandCopyTest 契约断言（消费方无 "ScreenPal" 字面量 + strings 词条契约 + 渠道 ID 未被误改）
  - 验收：`gradle testDebugUnitTest` 全绿（含既有 37 项）
- [ ] 4. README 品牌改写：标题/叙事以「念念（ScreenPal）」呈现，代码块与包名等开发面字样保留
  - 验收：人工核对——正文品牌统一，构建命令、包名、OpenSpec 变更表中的 change 名不动
- [ ] 5. 模拟器验收：主界面标题「念念 · 屏幕识别 + 语音播报」、通知标题「念念悬浮窗运行中」、系统设置渠道名「念念悬浮窗/念念截图」、复制后剪贴板标签「念念」
  - 验收：四处截图核对
