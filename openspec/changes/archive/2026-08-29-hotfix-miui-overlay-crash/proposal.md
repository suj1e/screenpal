# 2026-08-29-hotfix-miui-overlay-crash

## Why

真机（小米/HyperOS）上点悬浮窗「去授权」**立即闪退**。根因：上一轮 MIUI 修复（stepfun-only 批内引入的 ROM 分支）让小米设备走 getOemSpecialIntent 显式组件 Intent（com.miui.securitycenter/...AppPermissionsEditorActivity），而 HyperOS 已弃用该组件——显式 Intent 指向不存在的 Activity 时 startActivityForResult 抛 ActivityNotFoundException，调用点无捕获 → 闪退。旧包（标准 Intent 无兜底）不崩但跳错页面（应用总列表）。

## What Changes

- overlayPermissionIntent：OEM 深链返回前用 packageManager.resolveActivity 校验目标真实存在，不存在自动回落应用详情页（全 ROM 必达）
- requestOverlayPermission：try/catch ActivityNotFoundException 终极兜底 → 应用详情页
- 降级链：MIUI 权限编辑页（若组件存在）→ 应用详情页（必然可达）→ （极端）标准总列表

## 成功标准

- HyperOS 真机点「去授权」不闪退，落到念念应用详情页或 MIUI 权限编辑页（组件存在时）
- 原生 Android 路径零回归

## 优先级

- P1：崩溃级，主路径不可用。
