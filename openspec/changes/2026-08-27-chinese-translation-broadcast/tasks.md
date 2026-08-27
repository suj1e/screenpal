# 2026-08-27-chinese-translation-broadcast 任务清单

- [ ] 1. ChineseHeuristic + 单测（纯中/纯英/混合/数字符号）
  - 验收：四用例绿；中文判定阈值 0.5
- [ ] 2. BaiduTranslateClient（签名/请求/解析/错误码）+ 单测
  - 验收：签名与官方示例一致；多段 trans_result 拼接正确
- [ ] 3. ChineseBroadcastPipeline + 降级矩阵单测（开关×Key×超时五用例）
  - 验收：五用例绿；3s 超时降级原文
- [ ] 4. 卡片双语 UI（译文主显+原文小字）+ 设置三键（开关/APP ID/密钥）
  - 验收：持久化往返；卡片两种呈现正确
- [ ] 5. SelectionOverlayActivity 接入 pipeline（播报译文路径替换）
  - 验收：既有链路测试适配全绿
- [ ] 6. 模拟器验收（主智能体执行）：英文框选→中文播报+双语卡片；中文直读；关开关；无 Key 降级
  - 验收：logcat（翻译请求有无）+ 听感 + 截图四路径
