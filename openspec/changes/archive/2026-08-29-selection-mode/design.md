# 2026-08-29-selection-mode 设计

## 方案设计

**图示**：[diagrams/selection-states.svg](diagrams/selection-states.svg)

1. **模式枚举**：`SelectionMode { LASSO, RECT }`，`SelectionView` 构造注入；两模式共享：采样（8dp）、最小门槛（48dp 宽或高）、确认回调、震动拒选。
2. **RECT 模式绘制**：DOWN 记起点 → MOVE 画半透明矩形（#337B68EE 填充 + 4dp 描边）→ UP ≥48dp 确认（Rect(start,end) 归一化 min/max）。
3. **遮罩时序**（`drawMode` 状态机：IDLE_DRAWING（无遮罩）→ CONFIRMED（圈外遮罩））：
   - IDLE_DRAWING：`onDraw` 只画截图 + 笔迹/矩形，**不画全屏黑**
   - CONFIRMED：画全屏 #8F000000，再 `PorterDuff.Mode.DST_OUT` 按 bounds 挖孔（矩形挖矩形、套索挖包围盒——与裁剪语义一致）
   - 「重新框选」/拒选 → 回 IDLE_DRAWING
   - 笔迹可见性：紫色主描边 + 2dp 白色副描边（浅色背景可辨）
4. **设置**：DataStore `selectionMode`（String，默认 "LASSO"）；「框选方式」卡两单选；SelectionOverlayActivity 读设置构造 SelectionView。

## 接口 / 数据契约

- DataStore 新键 `selectionMode`；SelectionView 对外契约不变（resetForReselection/onSelectionConfirmed）

## 实施步骤

1. SelectionMode 枚举 + 设置键 + 卡片 UI
2. SelectionView 双模式分支 + RECT 恢复
3. 遮罩时序重做（IDLE 无遮罩 / CONFIRMED 圈外遮罩）
4. 单测（模式构造、RECT 归一化、遮罩状态机——可测纯函数抽取）+ 模拟器验收

## 性能优化点

无遮罩绘制比现状更省一层 saveLayer；CONFIRMED 态单层挖孔与现状同。

## 设计模式建议

状态机内聚 View；模式经构造注入（策略）。

## 风险与 Trade-off

- **风险：无遮罩绘制时笔迹在浅色壁纸上看不清** → 白色副描边解决
- **风险：确认后圈内若含大量留白，用户可能误选** → 圈外变暗对比已足够指示
- **开放问题**：无

## 测试策略

| 层 | 内容 | 方式 |
|----|------|------|
| 单元测试 | RECT min/max 归一化、门槛共用逻辑、遮罩状态机转移（IDLE→CONFIRMED→reset） | JVM（纯函数抽取） |
| 构建门禁 | assembleDebug | 本地 |
| 模拟器验收 | 两模式各画一次：绘制期原亮度、确认后圈外变暗；设置切换持久化 | 截图 |

边界/异常：单点/过小拒选两模式一致；出屏 clamp 沿用。
