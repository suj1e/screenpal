# Proposal: OCR 引擎

## Summary

实现统一的 OCR 引擎架构，包含端侧 ML Kit 文字识别（默认优先）和云端 OCR 增强（Google Cloud Vision API，可选），支持混合识别策略。

## Motivation

- ML Kit 端侧识别速度快、无需联网，但复杂场景准确率有限
- 端侧识别置信度低时，云端 API 可以提供更精准的结果
- 用户在不同场景（印刷体 / 手写体 / 多语言）需要不同的识别策略
- 统一的 OCR 接口让上层业务代码无需关心底层实现

## Goals

1. 提供统一的 `OcrEngine` 接口，上层代码只依赖接口不依赖具体实现
2. ML Kit 端侧识别作为默认引擎，零配置开箱即用
3. 云端 OCR（Google Cloud Vision API）作为可选增强，用户配置 API Key 后激活
4. 混合模式：先端侧识别，置信度低于阈值时自动调用云端增强
5. 识别结果包含文本内容、置信度、文本块位置信息
6. 支持裁剪后的 Bitmap 作为输入

## Non-goals

- 不实现手写体特殊优化（依赖 ML Kit 和云端 API 自身能力）
- 不提供离线云端 OCR 缓存
- 不支持多语言混合识别（单次识别单一语言）
- 不实现表格 / 表单结构化识别（仅纯文本提取）

## 依赖

- 前置：`project-scaffold`（需要基础工具类、DataStore）
- 前置：`floating-window-and-selection`（需要框选裁剪功能）
