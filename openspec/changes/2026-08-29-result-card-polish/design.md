# 2026-08-29-result-card-polish 设计

## 方案设计

**图示**：[diagrams/result-card.svg](diagrams/result-card.svg)

1. **胶囊背景**：`GradientDrawable().apply { cornerRadius = 999dp; setColor(WHITE); setStroke(1dp, 0x66FF7B68EE) }`；pressed 态 `StateListDrawable` 叠加 `#14000000` 底色反馈。
2. **按钮行**：LinearLayout horizontal，四按钮各 `layoutParams(weight=1, margin horizontal 4dp)`，高 44dp，文字 15sp medium `#FF7B68EE`，全大写关闭。
3. **文本区**：resultText 外包 ScrollView（maxHeight 由卡片约束），maxLines 移除改滚动，保证长文不挤按钮。
4. 复用现状逻辑：四按钮 onClick 行为零改动。

## 接口 / 数据契约

无（纯视觉）。

## 实施步骤

1. 胶囊 drawable 工厂函数 + 按钮行改造
2. 文本区 ScrollView
3. 模拟器验收（长文/短文两态截图）

## 性能优化点

无。

## 设计模式建议

无（drawable 工厂小函数）。

## 风险与 Trade-off

- **风险：程序化 View 的按下态**——StateListDrawable 手写，已覆盖
- **开放问题**：无

## 测试策略

| 层 | 内容 | 方式 |
|----|------|------|
| 单元测试 | 无逻辑改动，既有全量回归 | JVM |
| 构建门禁 | assembleDebug | 本地 |
| 模拟器验收 | 长/短文两态截图对照 | 截图 |

边界/异常：长文本滚动区高度约束（最多占屏 40%）。
