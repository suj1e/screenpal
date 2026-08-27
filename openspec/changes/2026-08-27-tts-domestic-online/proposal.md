# 2026-08-27-tts-domestic-online

## Why

TTS 去 Google 化：现有 Cloud 引擎为 Google Cloud TTS。产品决策接国内在线 TTS（音质优先于 Piper 的离线合成），选百度智能云短文本在线合成——与国内云 OCR 同一百度云账号体系（一套 Key 管两服务），有免费额度，发音人（"语音包"）丰富。Piper 保留为离线兜底（开源非 Google）。

## What Changes

- 新增 `BaiduTtsEngine`（实现 `TtsEngine`）：REST 合成 → MP3 落盘 → MediaPlayer 播放（Piper 同款播放路径）
- 降级链重排：**百度在线 → Piper → System**（原 Google Cloud 位置由百度顶替，TtsEngineType.CLOUD 语义改为百度）
- 删除 `GoogleCloudTtsProvider`；设置文案「云端 Google Cloud TTS（需 API Key）」→「百度在线语音（需 API Key）」
- 复用百度云 access_token 缓存（与 OCR 共用 BaiduAuthClient）
- 被否选项：讯飞在线（音质更优但 HMAC 鉴权复杂 + 独立账号，记录为后续可选引擎）；离线语音包（商用授权采购，用户已否）

## 成功标准

- 引擎选「百度在线」且有 Key：识别后播报为选定中文发音人，无 Google 残留
- 百度失败（无网/配额）自动降级 Piper；全链路单测覆盖降级矩阵
- 45+ 项既有测试全绿

## 优先级

- P2：播报主路径升级（Piper 兜底可用性已被 piper-warmup 修复保障）。

## 依赖

- 弱依赖:openspec/changes/2026-08-27-ocr-domestic-cloud/（共用 BaiduAuthClient 与 cloudApiSecret 设置键；该 change 未实施时本 change 自带轻量 token 获取亦可）
