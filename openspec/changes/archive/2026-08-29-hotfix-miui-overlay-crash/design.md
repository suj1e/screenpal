# 2026-08-29-hotfix-miui-overlay-crash 设计

## 根因

getOemSpecialIntent 返回显式组件 Intent（setClassName 到 MIUI securitycenter 的 Activity）。HyperOS 弃用该组件后，显式 Intent 解析失败 → startActivityForResult 抛 ActivityNotFoundException → 未捕获 → 闪退。单测盲区：Robolectric 不解析真机组件存在性，故 257 项单测全绿仍崩。

## 方案设计

1. overlayPermissionIntent：OEM intent 返回前 packageManager.resolveActivity(oem, 0) 校验；解析失败 → 应用详情页 Intent（ACTION_APPLICATION_DETAILS_SETTINGS + package URI，全 ROM 必达）。
2. requestOverlayPermission：try/catch ActivityNotFoundException → 应用详情页（终极兜底）。
3. 防回归测试：MIUI 分支在 PM 解析不到 OEM 组件时返回应用详情页 Intent。

## 接口 / 数据契约

无变化（overlayPermissionIntent(context): Intent、requestOverlayPermission(activity)）。

## 实施步骤

1. resolveActivity 校验 + try/catch 兜底 + 防回归单测
2. assembleDebug + 模拟器点「去授权」验证不崩、落点正确

## 风险与 Trade-off

- 应用详情页需要用户多找一层开关——比崩溃好；MIUI 权限编辑页组件存在时仍直达
- 待确认：HyperOS 上 MIUI 权限编辑页组件新名称（如有），可在 getOemSpecialIntent 增加分支

## 测试策略

| 层 | 内容 | 方式 |
|----|------|------|
| 单元测试 | MIUI 分支：PM 解析到 OEM 组件 → 返回 OEM intent；解析不到 → 应用详情页 intent；requestOverlayPermission 对 ActivityNotFoundException 的兜底 | Robolectric + PM stub |
| 构建门禁 | assembleDebug | 本地 |
| 模拟器/真机验收 | 点「去授权」不崩 + 落点正确 | 截图 |
