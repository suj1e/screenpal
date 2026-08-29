# 2026-08-29-permission-tri-card 设计

## 方案设计

**图示**：[diagrams/tri-card.svg](diagrams/tri-card.svg)

1. **PermissionCard 三行**：前两行沿用 `PermissionRow`（label + 已授权/去授权）；第三行：
   - label「无障碍权限（免弹窗截屏）」
   - 状态：`AccessibilityHelper.isEnabled(context)`（onResume 刷新）
   - 未开启 → TextButton「去开启」→ `ACTION_ACCESSIBILITY_SETTINGS`
   - description 常显："开启后点悬浮球零弹窗识读（Android 10 及以下回退系统录制弹窗）"
2. **MainUiState** 新增 `accessibilityEnabled: Boolean`，refreshPermissions 时刷新。
3. README 权限表补第三行与"为何首次弹录制授权"FAQ（进程重启后 MediaProjection 授权不持久，是系统安全要求；开启无障碍后可完全避开）。

## 接口 / 数据契约

- MainUiState + `accessibilityEnabled`；无新 DataStore 键

## 实施步骤

1. AccessibilityHelper（a11y change 已建）→ PermissionCard 三行 + 状态刷新
2. README 权限表 + FAQ
3. 模拟器验收：开/关无障碍两态卡片

## 性能优化点

无。

## 设计模式建议

无。

## 风险与 Trade-off

- **风险：不同 ROM 无障碍设置页路径不一**——标准 ACTION_ACCESSIBILITY_SETTINGS 兜底，各 ROM 均可达
- **开放问题**：无

## 测试策略

| 层 | 内容 | 方式 |
|----|------|------|
| 单元测试 | MainUiState.accessibilityEnabled 刷新（Robolectric mock AccessibilityManager） | XCTest/JVM |
| 构建门禁 | assembleDebug | 本地 |
| 模拟器验收 | 开/关无障碍两态 + 去开启深链 | 截图 |

边界/异常：无障碍服务被杀后回到未开启态（onResume 刷新捕获）。
