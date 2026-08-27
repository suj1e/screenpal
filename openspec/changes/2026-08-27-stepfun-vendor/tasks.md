# 2026-08-27-stepfun-vendor 任务清单

- [x] 1. StepfunTtsEngine（audio/speech）+ 单测
  - 验收：请求组装/音色/失败映射正确
- [x] 2. StepfunOcrProvider + StepfunTranslateClient + 单测
  - 验收：视觉 OCR 与转译解析正确
- [ ] 3. VendorRouter + 三处接线 + 路由矩阵单测
  - 验收：DOUBAO/STEPFUN 路由正确；缺凭据落 Piper 兜底
- [ ] 4. 设置：服务商单选 + 凭据区切换 + DataStore 三键
  - 验收：持久化往返；切换凭据区正确
- [ ] 5. 模拟器验收（主智能体执行）：StepFun 三路径 + 豆包回归 + 失败兜底
  - 验收：logcat + 听感 + 截图
