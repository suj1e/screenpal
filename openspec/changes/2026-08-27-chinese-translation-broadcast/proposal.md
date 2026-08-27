# 2026-08-27-chinese-translation-broadcast

## Why

产品核心诉求「无论框选的是什么语言，都要播报中文」：现有链路 OCR 出什么读什么。本 change 在 OCR 与 TTS 之间插入翻译层——非中文文本先翻译为中文再播报，结果卡片双语呈现（译文主显、原文小字）。翻译服务选百度翻译开放平台（国内成熟、标准版有免费额度、接入最简），与百度云 OCR/TTS 的账号体系独立但同生态。

## What Changes

- 新增 `TranslateService` 接口与 `BaiduTranslateClient`（`translate` REST：q/from=auto/to=zh + APP ID + 签名 MD5(appid+q+salt+密钥)）
- 新增 `ChineseBroadcastPipeline`：语言启发式判定（中文字符占比 ≥50% 视为中文，直读；否则翻译）→ 译文交 `TtsManager.speak`
- `SelectionOverlayActivity` 接入 pipeline：播报译文；结果卡片译文主显 + 原文小字展示
- 设置新增「自动翻译播报」开关（默认开）与「翻译 APP ID / 密钥」输入项（DataStore 持久化）
- 失败降级：翻译失败（无 Key/网络/超时 3s）→ 播报原文并卡片标注「翻译不可用」，不阻塞
- 被否选项：本地离线翻译模型（体积与质量不匹配 V1）；按 OCR 语种字段判定（PaddleOCR 不输出语种，字符集启发式已够）

## 成功标准

- 框选英文屏幕 → 播报中文译文，卡片主显译文、小字原文
- 框选中文 → 不调翻译直接播报（logcat 证据）
- 翻译开关关闭 → 行为退回直读原文
- 无 Key/断网 → 播报原文不崩溃，卡片「翻译不可用」
- 单测全绿（启发式/签名/解析/降级矩阵）

## 优先级

- P1：产品核心诉求「核心就是中文」的直接实现。

## 依赖

- 前置:openspec/changes/2026-08-27-ocr-paddle-onnx/（OCR 文本来源切换，接口不变故弱依赖——ML Kit 时代亦可实现本 change）
