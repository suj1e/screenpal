# 2026-08-27-tts-domestic-online

## Why

TTS 去 Google 化并接国内头部方案：选**豆包语音合成（火山引擎语音技术）**——大模型音色为国内第一梯队（含豆包 App 同款音色），有免费额度与低价计费，用户拍板以豆包替代先前方案的百度。Piper 保留为离线兜底（开源非 Google）。与 OCR/翻译的火山方舟 Key 分属两个控制台（语音技术 vs 方舟），共两个凭据。

## What Changes

- 新增 `DoubaoTtsEngine`（实现 `TtsEngine`）：火山语音 HTTP API（`openspeech.bytedance.com/api/v1/tts`，AppID + Access Token 鉴权）合成 MP3 → MediaPlayer 播放（复用 Piper 播放路径）
- 降级链重排：**豆包在线 → Piper → System**（`TtsEngineType.CLOUD` 语义改为豆包）
- 删除 `GoogleCloudTtsProvider`；设置文案改「豆包在线语音（火山引擎）」+ 音色（语音包）下拉
- 音色选择：`UserSettings.ttsVoice: String`（voice_type，默认豆包中文女声，合法值**待确认**以火山文档为准）
- 被否选项：百度在线 TTS（用户否）、讯飞离线包（商用授权）、PaddleSpeech 端侧（音质/体积）

## 成功标准

- 引擎选「豆包在线」且配置 AppID/Token：播报为所选音色，无 Google 残留
- 豆包失败（无网/配额/鉴权）自动降级 Piper；降级矩阵单测覆盖
- 45+ 项既有测试全绿

## 优先级

- P2：播报主路径升级（Piper 兜底已被 piper-warmup 修复保障）。

## 依赖

- 无硬依赖（自备火山引擎语音技术控制台 AppID + Access Token，**待用户开通**）
