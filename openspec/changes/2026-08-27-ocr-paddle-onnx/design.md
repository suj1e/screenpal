# 2026-08-27-ocr-paddle-onnx 设计

## 现状分析

| 组件 | 现状 | 处置 |
|------|------|------|
| `MlKitOcrProvider` | ML Kit 中文 bundled 模型 | 删除 |
| `OcrEngine` 接口 / `OcrResult` / `TextBlock` | 抽象稳定 | 不变 |
| `HybridOcrEngine` | 置信度阈值切换 | 不变（LOCAL 实现换 Paddle） |
| onnxruntime-android 1.17.0 | Piper 在用 | 复用 |
| `SelectionOverlayActivity.resolveOcrEngine` | LOCAL→MlKit | LOCAL→Paddle |

## 方案设计

**图示**：[diagrams/architecture-target.svg](diagrams/architecture-target.svg)（本批次总体目标架构）

1. **模型资产**：RapidOCR 转换的 PP-OCRv4 ONNX 三模型（det 4.7MB / cls 1.4MB / rec 10.5MB）+ 识别字典 `ppocr_keys_v1.txt`（6623 字符），置 `app/src/main/assets/ocr/`。来源为开源社区转换产物（Apache-2.0），落地时锁定具体版本并在提交中附校验和。**待确认**：具体模型文件以实施时从 RapidOCR 官方 release 拉取的当前稳定版为准。
2. **流水线**（`ocr/paddle/` 包，全纯 Kotlin）：
   - `DetPreProcessor`：bitmap → 归一化 RGB FloatArray（det 输入 960 上限，等比缩放）
   - `DbPostProcessor`：Sigmoid 概率图 → 0.3 阈值二值化 → 连通域标记（BFS）→ 每域最小外接矩形（旋转框简化为轴对齐 box，V1 取包围盒）→ 坐标映射回原图
   - `ClsProcessor`：文本行角度分类（0/180°），小图旋转
   - `RecRecognizer`：行图归一化（高 48）→ CRNN 输出 [T, 6623] → 贪心 CTC 解码（去重 + 去 blank）→ 字典查表拼文本；置信度取均值
   - `PaddleOcrProvider : OcrEngine`：编排三段，输出 `OcrResult`（text 按行拼接，blocks 保留 box+conf）
3. **性能**：det/rec 各一次 ONNX run，模拟器 arm64 预估全流程 1–3s（真机更快）；全部在 Dispatchers.Default 执行（上层已切）。
4. **Google 移除**：gradle 删 `text-recognition-chinese`；删 `MlKitOcrProvider.kt`；resolveOcrEngine LOCAL 分支换 PaddleOcrProvider。

## 接口 / 数据契约

- `OcrEngine` 接口不变；`PaddleOcrProvider` 为无参构造（模型从 assets 加载，懒加载单例避免重复初始化）
- 新增 assets：`ocr/det.onnx`、`ocr/cls.onnx`、`ocr/rec.onnx`、`ocr/ppocr_keys_v1.txt`

## 实施步骤

1. assets 模型落地 + `AssetModelLoader`（复制到 filesDir 缓存，供 onnxruntime 读路径）
2. DetPreProcessor + DbPostProcessor + 单测（合成概率图 → 期望 box）
3. RecRecognizer（CTC 解码 + 字典）+ 单测（构造 logits → 期望文本，含去重/blank 用例）
4. PaddleOcrProvider 编排 + resolveOcrEngine 切换
5. 移除 ML Kit（gradle + MlKitOcrProvider + 设置文案核查）
6. 模拟器验收：中文/英文混合截图识别对照

## 性能优化点

- det 输入尺寸上限 960、rec 批处理文本行（逐行即可，V1 不并行）
- 模型懒加载单例：首次识别 ~1s 额外加载，后续复用

## 设计模式建议

沿用既有 `OcrEngine` 策略模式；流水线各段为纯函数对象（输入输出明确、可独立单测），不引入新框架。

## 风险与 Trade-off

- **风险：轴对齐 box 简化**（斜排文本框偏大）→ V1 接受，识别质量以实测为准；后续可引入多边形 NMS
- **风险：CTC 字典与模型版本必须配对**（字典错位=乱码）→ 模型+字典同源同版本锁定，提交附校验和
- **风险：APK +16MB** → debug 已全 ABI、release 有 ABI 过滤；模型按 arm64 单一变体打包即可（ONNX 模型与 ABI 无关，无增量）
- **待确认**：RapidOCR 模型转换产物的确切版本/下载源在实施时锁定

## 测试策略

| 层 | 内容 | 方式 |
|----|------|------|
| 单元测试 | DbPostProcessor（合成概率图→box 数量/位置）、CTC 解码（重复/blank/正常三用例）、资产完整性（4 个 assets 存在且大小合理） | JVM 纯 Kotlin，`gradle testDebugUnitTest` |
| 构建门禁 | assembleDebug 通过、APK 无 ML Kit、assets 打包 | 本地构建 |
| 模拟器验收 | 中文+英文混合页面框选识别，对照 ML Kit 时代结果；识别耗时记录 | 截图 + logcat 人工核对 |

边界/异常：空概率图（无文本）返回空 OcrResult 不崩溃；超大截图缩放上限；bitmap 回收时机沿用上层约定。
