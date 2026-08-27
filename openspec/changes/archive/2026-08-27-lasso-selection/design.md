# 2026-08-27-lasso-selection 设计

## 方案设计

**图示**：[diagrams/lasso-interaction.svg](diagrams/lasso-interaction.svg)

1. **数据结构**：`val strokePoints = MutableList<PointF>`；MOVE 采样 `dist(last, cur) >= 8dp` 才入列（防点爆炸）；`Path` 由点集重建（quadTo 平滑：中点法）。
2. **触摸状态机**：DOWN 清空并起点 → MOVE 采样入列 + invalidate → UP 判定 `bounds ≥ 48dp` → `onSelectionConfirmed(boundsRect)`（沿用现有回调签名，坐标换算逻辑不变）；UP 不达标则轻震动提示 + 清空。
3. **绘制**：底层全屏半透明遮罩 → 笔迹区域"透亮"实现：`Canvas.saveLayer` + 遮罩填充后 `PorterDuff.Mode.DST_OUT` 沿笔迹路径以 24dp 粗描边挖孔 → 再画 4dp 紫色笔迹线。避免逐帧异或开销，一次 saveLayer 足够（全屏单层）。
4. **裁剪**：`SelectionViewModel.calculateCropRect` 现签名接收 Rect——套索 bounds 即 Rect，复用不变。
5. **文案**：首次进入 `TextView` 提示 3s 淡出（"用手指圈出要朗读的文字"）。

## 接口 / 数据契约

- `SelectionView` 对外仅暴露 `resetForReselection()` 与确认回调（不变）；内部矩形逻辑删除
- `onSelectionConfirmed(Rect)` 签名不变——上层（OCR/翻译链路）零改动

## 实施步骤

1. 点集采集 + 平滑 Path 绘制 + 采样单测（点距过滤）
2. 遮罩挖孔视觉 + UP 判定 + 震动提示
3. 坐标映射回归验证（复用 calculateCropRect 既有逻辑 + 新增 bounds 单测）
4. 提示文案 + 模拟器验收

## 性能优化点

采样限流（8dp）保证长笔画点数可控；单 saveLayer 无逐帧位图操作。

## 设计模式建议

不引入新模式；状态机内聚在 View 内部（与现状一致），保持轻量。

## 风险与 Trade-off

- **风险：包围盒大于用户意图**（画 L 形圈到无关内容）→ OCR 对多文字容错高，卡片可复制可重画；V1 接受
- **风险：斜屏/旋转**——Activity 已锁 portrait，无此边界
- **开放问题**：后续增强（像素级 mask、多笔合并）视实测反馈再立项

## 测试策略

| 层 | 内容 | 方式 |
|----|------|------|
| 单元测试 | 采样过滤（<8dp 丢弃）、bounds 计算（L 形点集 → 正确包围盒）、坐标换算（view→bitmap 映射） | JVM 纯 Kotlin（点集/换算抽为可测函数） |
| 构建门禁 | assembleDebug | 本地构建 |
| 模拟器验收 | 画圆圈/L 形/细长条三类笔迹 → 识别区域正确；重新框选；最小 48dp 门槛 | 截图 + 实操录屏对照 |

边界/异常：单点点击（无拖动）视为无效笔迹不触发识别；超大笔画出屏边界 clamp。
