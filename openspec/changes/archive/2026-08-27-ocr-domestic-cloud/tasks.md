# 2026-08-27-ocr-domestic-cloud 任务清单

- [x] 1. CloudOcrProvider 重写（方舟 vision：请求组装/解析）+ 单测
  - 验收：mock 单测绿；多行文本正确切分；空 content 异常
- [x] 2. 设置收敛：cloudApiKey 标签改「火山方舟 API Key」+ CloudOcrConfig 单字段
  - 验收：设置往返；UI 文案正确
- [x] 3. 旧实现残留清理（Google/百度）
  - 验收：grep 无 vision.googleapis / aip.baidubce OCR 残留
- [ ] 4. 模拟器验收（主智能体执行）：有 Key 云路径 + 无 Key 降级
  - 验收：logcat 两路径证据
