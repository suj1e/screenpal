# 2026-08-27-ocr-paddle-onnx

## Why

产品决策去 Google 化：现有端侧 OCR 用 ML Kit Text Recognition（Google 系组件）。OCR 是全链路源头（识别 → 翻译 → 播报），须先替换为国内成熟方案。百度开源 PP-OCR 系列是中文 OCR 国内事实标准，其 ONNX 导出版（RapidOCR 模式）可复用项目已有的 onnxruntime-android 依赖（Piper 在用），零新增重型依赖。

## What Changes

- 新增 `PaddleOcrProvider`（实现既有 `OcrEngine` 接口）：RapidOCR 三模型流水线（DBNet 文本检测 det + 方向分类 cls + CRNN 识别 rec），模型资产随 APK 打包（~16MB）
- DBNet 后处理（概率图 → 二值化 → 连通域/最小包围框 → 文本行 box）与 CTC 解码（字典查表）为纯 Kotlin 实现，可 JVM 单测
- 彻底移除 ML Kit 依赖（`com.google.mlkit:text-recognition-chinese`）与 `MlKitOcrProvider`
- OcrMode.LOCAL 的实现指向 PaddleOcrProvider；Hybrid 接口不变
- 被否选项：PaddleLite 官方 .nb 路线（引入整套 PaddleLite 运行时，依赖重）、纯云 OCR（离线不可用）

## 成功标准

- 模拟器框选中文/英文屏幕文字，端侧识别结果与 ML Kit 时代相当（中文可读、置信度合理）
- APK 内无 ML Kit 痕迹；`gradle testDebugUnitTest` 全绿；新增几何/解码单测通过
- APK 体积增量 ≈ 模型资产（~16MB）+ 后处理代码

## 优先级

- P1：OCR 是识别链路源头，本批次（国内化 + 中文播报）的地基。
