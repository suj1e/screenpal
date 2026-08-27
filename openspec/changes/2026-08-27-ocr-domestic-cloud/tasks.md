# 2026-08-27-ocr-domestic-cloud 任务清单

- [ ] 1. BaiduAuthClient（token 获取+缓存）+ 单测
  - 验收：参数组装正确；二次获取走缓存；过期刷新
- [ ] 2. CloudOcrProvider 重写（accurate_basic 默认）+ 响应解析单测（样例 words_result/location）
  - 验收：解析出 text/TextBlock/confidence=0.99；error_code 抛明确异常
- [ ] 3. 设置扩展 cloudApiSecret（DataStore 键 + SettingsRepository + 主界面 Secret 输入框）
  - 验收：持久化往返；无 Key 行为与现状一致
- [ ] 4. Google Vision 残留清理（imports/常量/文案）
  - 验收：全仓 grep 无 vision.googleapis
- [ ] 5. 模拟器验收（主智能体执行）：有 Key 云路径触发 + 无 Key 降级回归
  - 验收：logcat 两条路径各一条证据
