# 2026-08-27-brand-copy-niannian

## Why

上一个 change（2026-08-27-launcher-icon-and-label）把 launcher 名称定为「念念」，但应用内仍有多处用户可见的 "ScreenPal" 硬编码文案：主界面标题、悬浮窗通知标题、两个通知渠道名（系统设置可见）、剪贴板复制标签，README 也仍以 ScreenPal 为品牌叙事。图标已中文而界面文案仍英文，品牌认知割裂。本 change 完成品牌文案统一收尾。

## What Changes

- 6 处用户可见 "ScreenPal" 文案改为「念念」，硬编码顺手资源化到 strings.xml
- README 品牌叙事改为「念念（ScreenPal）」
- **明确不动**：包名/applicationId、CHANNEL_ID 常量值（改了会产生孤儿渠道）、日志 TAG `ScreenPalFlow`、UTTERANCE_ID、类名/主题名、prototypes 原型稿
- 用户已拍板：范围 = 用户可见 + README；风格 = 直连无空格（「念念悬浮窗运行中」，标题保留中点分隔副题「念念 · 屏幕识别 + 语音播报」）

## 成功标准

- 应用内 grep 不到任何用户可见的 "ScreenPal" 字样（源码级断言钉住，白名单仅 CHANNEL_ID/TAG/UTTERANCE_ID 等开发面常量）
- 系统设置的应用通知页渠道名显示「念念悬浮窗」「念念截图」（老安装升级后随 createNotificationChannel 自动更新，无需迁移代码）
- 复制功能剪贴板标签为「念念」
- 主界面标题、通知标题均为中文品牌
- 现有 37 项单测全绿，新增文案契约断言通过

## 优先级

- P2：品牌收尾项，紧随 launcher-icon-and-label（P1）之后交付。
