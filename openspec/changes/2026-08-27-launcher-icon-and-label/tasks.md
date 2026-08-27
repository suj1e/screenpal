# 2026-08-27-launcher-icon-and-label 任务清单

- [ ] 1. 重画图标 drawable：`ic_launcher_background.xml`（108dp 紫渐变 + 高光 vector）与 `ic_launcher_foreground.xml`（108dp 白色声波纹，66dp 安全区内）
  - 验收：两文件为 `<vector>` 非 selector；渐变色值与 bg_floating_ball 一致（#6366F1/#7C3AED/#A855F7）；波纹包络坐标落在 21–87dp 安全区
- [ ] 2. 新增 API 24/25 兜底：`mipmap-anydpi/ic_launcher.xml`、`mipmap-anydpi/ic_launcher_round.xml`（纯 vector 静态圆图标）
  - 验收：minSdk 24 下资源链接通过；单测断言兜底文件存在
- [ ] 3. manifest 修正：`android:icon="@mipmap/ic_launcher"`、`android:roundIcon="@mipmap/ic_launcher_round"`、`android:label="@string/app_name"`
  - 验收：`AndroidManifestTest` 新增断言通过（icon/roundIcon/label 三属性存在，application icon 不再直接引用 drawable 前景层）
- [ ] 4. 单元测试补充：`AndroidManifestTest` 增加图标资源断言 + 兜底资源存在性断言
  - 验收：`gradle testDebugUnitTest` 全绿
- [ ] 5. 模拟器视觉验收：构建安装 → 抽屉图标为紫底白波纹、名称显示 "ScreenPal"、前台服务通知栏小图标可见 → 长按拖拽可在桌面创建快捷方式
  - 验收：截图对照 prototypes/02_floating_window.html 悬浮球视觉一致
