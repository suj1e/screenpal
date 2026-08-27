# 2026-08-27-launcher-icon-and-label 设计

## 现状分析

| 资源 | 现状 | 问题 |
|------|------|------|
| `AndroidManifest.xml` | `android:icon="@drawable/ic_launcher_foreground"`，无 `label`、无 `roundIcon` | 引用错层；label 缺失退化为包名 |
| `mipmap-anydpi-v26/ic_launcher{,_round}.xml` | adaptive-icon，引用两个 drawable | 从未被 manifest 使用 |
| `drawable/ic_launcher_foreground.xml` | selector → `ic_launcher_background` | 空壳占位 |
| `drawable/ic_launcher_background.xml` | selector → `@android:color/transparent` | 全透明 |
| `drawable/bg_floating_ball.xml` + `ic_ball_waveform.xml` | 悬浮球实际视觉：紫渐变球体 + 白色四条声波 | —— 视觉来源 —— |

## 方案设计

### 方案 A（采用）：悬浮球同款自适应图标 + manifest 修正

**图示**：[diagrams/icon-design.svg](diagrams/icon-design.svg)

1. **重画 `drawable/ic_launcher_background.xml`**：`<vector>` 108×108 viewport，铺满 108dp 的线性渐变（#6366F1 → #7C3AED → #A855F7，左上→右下，与 `bg_floating_ball` 的 angle=315 一致），叠加左上角白色 radial 高光（复刻悬浮球 shine）。
2. **重画 `drawable/ic_launcher_foreground.xml`**：`<vector>` 108×108 viewport，白色（#E6FFFFFF，与 `ic_ball_waveform` 同 alpha）四根圆角声波条，经 `<group>` 缩放 2.5×、平移至中心，整体落于 66dp 安全区内（波纹包络 29–78dp）。
3. **manifest 修正**：
   ```xml
   android:icon="@mipmap/ic_launcher"
   android:roundIcon="@mipmap/ic_launcher_round"
   android:label="@string/app_name"
   ```
   `@string/app_name`（"ScreenPal"）已存在于 strings.xml。
4. **API 24/25 兜底**：新增 `mipmap-anydpi/ic_launcher{,_round}.xml`，内容为单个 `<vector>`（紫底圆 + 白波纹合成的静态矢量圆图标）。24/25 解析到它，26+ 被 `mipmap-anydpi-v26` 的 adaptive-icon 覆盖。纯矢量、零位图、零新增依赖。

**取舍记录**：
- 拒绝"让 foreground 继续引用 background 修个 selector 引用"——治标不治本，图标仍是空壳。
- 拒绝引入 PNG 位图套图（mdpi–xxxhdpi）——需要外部工具生成二进制资源，维护成本高；矢量方案 minSdk 24 全覆盖。
- 高光放 background 层而非 foreground——避免前景层元素超出安全区被圆形 mask 裁切。

### 方案 B（否决）：仅修引用不重画

manifest 改指 `@mipmap/ic_launcher` 即可让 adaptive-icon 生效——但 background 仍是透明、foreground 仍是空壳，图标依旧是白块。仅作为方案 A 的第一步包含在内，不独立成立。

## 接口 / 数据契约

无运行时接口变化。资源契约变化：

- `R.mipmap.ic_launcher` / `R.mipmap.ic_launcher_round` 首次可用（24+ 全覆盖）。
- `R.drawable.ic_launcher_foreground` 内容变化：从"全透明"变为"白色声波图形（透明底）"。唯一使用方是通知 `setSmallIcon`（FloatingWindowService / ScreenCaptureService）——通知小图标规范恰好要求 alpha-only 白色图形，此变化使通知图标从"不可见"变为"正确可见"，属修复而非破坏。

## 实施步骤

1. 重画 `drawable/ic_launcher_background.xml`、`drawable/ic_launcher_foreground.xml`。
2. 新增 `mipmap-anydpi/ic_launcher.xml`、`mipmap-anydpi/ic_launcher_round.xml` 兜底。
3. manifest 补 `icon` / `roundIcon` / `label` 三行。
4. `AndroidManifestTest` 增加断言：manifest 含 `@mipmap/ic_launcher`、`android:label="@string/app_name"`、不再直接引用 `@drawable/ic_launcher_foreground` 作为应用图标。
5. 构建安装至模拟器，视觉验证抽屉图标、应用名、通知栏图标。

## 性能优化点

无运行时热点。图标资源由两个 selector 变为两个小 vector，体积与解析成本可忽略（均 <2KB）。

## 设计模式建议

不适用（纯资源/清单修复，无代码结构变更）。扩展性说明：后续替换正式品牌素材时，只需覆盖同名 4 个 vector/mipmap 文件，manifest 不再动。

## 风险与 Trade-off

- **风险：不同启动器对 vector 兜底图标渲染差异**（24/25 老 ROM）：缓解——兜底图标为简单纯色圆+波纹，无 mask 依赖；且 26+ 设备占比 99%+。
- **风险：通知小图标观感变化**：预期内，见"接口/数据契约"，实机验证即可。
- **开放问题**：正式品牌图标素材（如需营销级视觉）待用户提供后可无缝替换，本 change 先交付悬浮球同款。

## 桌面快捷方式说明（非代码项）

Android 不会为新装应用自动创建桌面快捷方式，ScreenPal 现仅存在于应用抽屉，属系统正常行为。用户长按抽屉图标拖至桌面即可创建。本 change 不实现"自动添加快捷方式"（需 `INSTALL_SHORTCUT` 广告式权限，侵扰性强，与工具类产品惯例不符）。

## 测试策略

分层（对齐 docs/design/test-pyramid.svg 的金字塔）：

| 层 | 内容 | 方式 |
|----|------|------|
| 单元测试 | `AndroidManifestTest` 新增 icon/label 断言；新增资源存在性断言（4 个图标资源文件存在且非 selector 空壳） | JVM 文本断言，`gradle testDebugUnitTest` |
| 构建门禁 | assembleDebug 资源链接通过（mipmap 24/25 兜底齐备） | CI/本地构建 |
| 真机/模拟器验收 | 抽屉图标视觉、应用名 "ScreenPal"、通知栏图标可见、桌面长按拖拽可创建快捷方式 | 模拟器截图人工核对（悬浮球视觉对照 prototypes/02_floating_window.html） |

边界/异常：无并发、无数据边界；重点覆盖资源解析异常路径——API 24/25 资源解析（兜底文件缺失会在运行期 crash，构建期 `AAPT` 无法发现），故单测中断言 `mipmap-anydpi/ic_launcher.xml` 文件存在。

## 图示索引

| 图 | 相对路径 | 说明 |
|----|----------|------|
| 图标设计稿 | diagrams/icon-design.svg | 自适应图标视觉规格（配色/安全区/波纹几何） |
