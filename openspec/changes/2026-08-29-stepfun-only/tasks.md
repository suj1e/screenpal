# 2026-08-29-stepfun-only 任务清单

- [ ] 1. 删除豆包四类与 VendorRouter 及其测试；三挂点直连 Stepfun*
  - 验收：编译过；grep 无 Doubao|VendorRouter|volcano|cloudApiKey 残留（源码/测试）
- [ ] 2. SettingsRepository/MainViewModel/MainUiState 清 5 键 + 设置卡改「StepFun 云服务」
  - 验收：设置页仅一组凭据；单测（键契约）绿
- [ ] 3. 文案契约测试更新 + 全量测试绿
  - 验收：`gradle testDebugUnitTest` 全绿
- [ ] 4. 模拟器验收（zapply 执行）：StepFun 链路回归 + 设置页截图
  - 验收：截图 + 链路 logcat
