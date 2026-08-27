# 2026-08-27-ocr-paddle-onnx 任务清单

- [x] 1. 模型资产落地：assets/ocr/ 四文件（det/cls/rec onnx + 字典）+ AssetModelLoader（复制 filesDir 缓存）+ 资产完整性单测
  - 验收：构建通过；单测断言 4 文件存在、det<8MB/rec<15MB、字典 6623 行
- [x] 2. 检测后处理：DetPreProcessor + DbPostProcessor（二值化/连通域/包围框）+ 单测（合成概率图两文本块 → 2 box 且坐标 ±2px）
  - 验收：单测绿；空图返回空列表不崩溃
- [x] 3. 识别解码：ClsProcessor + RecRecognizer（CTC 贪心解码 + 字典）+ 单测（构造 logits 三用例：正常拼句/连续重复去重/blank 跳过）
  - 验收：单测绿；解码置信度均值正确
- [ ] 4. PaddleOcrProvider 编排接入 + resolveOcrEngine LOCAL 分支切换 + 懒加载单例
  - 验收：OcrEngine 接口不变；既有 Hybrid/OcrEngine 测试全绿
- [ ] 5. 移除 ML Kit：gradle 依赖、MlKitOcrProvider、设置相关引用清理
  - 验收：APK 无 mlkit 条目（aapt2 核查）；全仓 grep 无 text-recognition
- [ ] 6. 模拟器验收（主智能体执行）：中文/英文混合屏幕框选识别对照 + 耗时记录
  - 验收：中文识别可读、结果卡片正常、识别 ≤5s（模拟器）
