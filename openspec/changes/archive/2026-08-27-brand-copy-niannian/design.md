# 2026-08-27-brand-copy-niannian 设计

## 现状分析（ScreenPal 出现点盘点）

| # | 位置 | 现文案 | 分类 | 处置 |
|---|------|--------|------|------|
| 1 | `MainActivity.kt:89` | `"ScreenPal · 屏幕识别 + 语音播报"`（Compose 硬编码） | 用户可见 | → `stringResource(app_title)`，文案「念念 · 屏幕识别 + 语音播报」 |
| 2 | `FloatingWindowService.kt:241` | `"ScreenPal 悬浮窗运行中"`（通知标题硬编码） | 用户可见 | → strings「念念悬浮窗运行中」 |
| 3 | `FloatingWindowService.kt:232` | channel name `"ScreenPal 悬浮窗"` | 用户可见（系统设置） | → strings「念念悬浮窗」 |
| 4 | `ScreenCaptureService.kt:124` | channel name `"ScreenPal Capture"` | 用户可见（系统设置） | → strings「念念截图」 |
| 5 | `SelectionOverlayActivity.kt:206` | ClipData label `"ScreenPal"` | 半可见（剪贴板预览） | → 字面量改「念念」 |
| 6 | `README.md` | 全文 ScreenPal 品牌 | 文档 | → 「念念（ScreenPal）」叙事改写 |
| — | `CHANNEL_ID` 常量（`ScreenPal_Floating`/`ScreenPal_Capture`） | — | **开发面，禁改** | 渠道 ID 变更会让老安装残留孤儿渠道；name 可随重新 create 自动更新（系统仅锁定 importance，name/description 会刷新） |
| — | Log TAG `ScreenPalFlow`、`UTTERANCE_ID`、类名、`Theme.ScreenPal`、包名 | — | 开发面，禁改 | 与品牌认知无关，改动纯增风险 |

## 方案设计

**思路**：品牌词全部走 strings.xml 单源，消费方一律资源引用；开发面常量保持原样。

**图示**：[diagrams/brand-strings-flow.svg](diagrams/strings-flow.svg)

1. **strings.xml 新增 4 词条**：
   - `app_title` = `念念 · 屏幕识别 + 语音播报`
   - `notification_floating_title` = `念念悬浮窗运行中`
   - `channel_floating_name` = `念念悬浮窗`
   - `channel_capture_name` = `念念截图`
2. **消费方改造**：MainActivity 标题改 `stringResource(R.string.app_title)`；FloatingWindowService 两处、ScreenCaptureService 渠道名改 `getString(...)`（该服务通知标题已是 strings 引用，不动）；SelectionOverlayActivity 剪贴板 label 字面量改「念念」（非 UI 文案，不资源化，最小惊讶）。
3. **README 改写**：标题与首段以「念念（ScreenPal）」双语叙事，正文品牌词统一「念念」，保留包名/命令行等开发面 ScreenPal 字样。
4. **防回归契约**：新增 BrandCopyTest（文本断言风格，同 AndroidManifestTest）：断言 4 个消费方源文件不含 `"ScreenPal` 字面量（白名单：CHANNEL_ID 常量行、TAG、UTTERANCE_ID），strings.xml 含 4 词条与「念念」。

**取舍记录**：
- 拒绝"连 CHANNEL_ID 一起改"——孤儿渠道风险 > 品牌洁癖收益，且渠道 ID 用户不可见。
- 拒绝为 ClipData label 资源化——它不进 UI 布局，走 strings 反而增加间接层。
- 渠道名依赖系统自动更新而非迁移代码——官方行为（re-create 更新 name/description），零代码成本。

## 接口 / 数据契约

无运行时接口变化。新增资源：`R.string.app_title`、`R.string.notification_floating_title`、`R.string.channel_floating_name`、`R.string.channel_capture_name`。

## 实施步骤

1. strings.xml 增 4 词条。
2. MainActivity/FloatingWindowService/ScreenCaptureService/SelectionOverlayActivity 按上表替换。
3. 新增 BrandCopyTest 契约断言。
4. README 品牌改写。
5. `gradle testDebugUnitTest` 全绿 + assembleDebug。
6. 模拟器验收：主界面标题 / 通知标题与渠道名（系统设置应用通知页）/ 复制剪贴板标签。

## 性能优化点

无。stringResource 与 getString 为零成本引用。

## 设计模式建议

不适用（文案资源化属 Android 标准实践，非模式选型）。

## 风险与 Trade-off

- **风险：通知渠道名更新时序**——系统在 App 下次调用 createNotificationChannel 时刷新 name；服务首启即调用，实际无感知延迟。
- **风险：README 中开发面 ScreenPal（包名、命令）误改**——验收时人工核对代码块保留。
- **开放问题**：无。

## 测试策略

| 层 | 内容 | 方式 |
|----|------|------|
| 单元测试 | BrandCopyTest：① 4 个消费方源文件不含 `"ScreenPal` 用户可见字面量（白名单外）；② strings.xml 含 4 词条且值为约定文案；③ 通知渠道 ID 常量未被误改（仍含 ScreenPal_Floating/Capture） | JVM 文本断言，`gradle testDebugUnitTest` |
| 构建门禁 | assembleDebug 通过；aapt2 badging application-label 仍为「念念」 | 本地构建 |
| 模拟器验收 | 主界面标题、通知标题、系统设置渠道名、剪贴板标签四处视觉核对 | 截图人工核对 |

边界/异常：BrandCopyTest 白名单机制覆盖"开发面合法 ScreenPal"（渠道 ID/TAG/UTTERANCE_ID/包名导入），防止一刀切断言误伤；README 代码块不在单测范围，人工验收兜底。

## 图示索引

| 图 | 相对路径 | 说明 |
|----|----------|------|
| 品牌文案资源流向图 | diagrams/strings-flow.svg | strings.xml 单源 → 四类消费方 + 禁改清单边界 |
