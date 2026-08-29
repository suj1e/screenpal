package com.suj1e.screenpal.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 结果卡按钮行接线契约（2026-08-29-result-card-polish）：
 * 四按钮 onClick 回调行为零改动（源码逐字核对），仅视觉胶囊化——
 * 等权重均分、44dp 高、水平 4dp 间距、15sp medium 品牌紫、pillPressed 背景。
 */
class SelectionOverlayResultCardWiringTest {

    private val overlaySrc =
        File("src/main/java/com/suj1e/screenpal/overlay/SelectionOverlayActivity.kt").readText()

    // ---- 回调零改动（源码契约）----

    @Test
    fun stopBroadcastCallback_unchanged() {
        assertTrue(
            "「停止播报」回调必须仍是 ttsManager.stop()",
            overlaySrc.contains("""actionButton("停止播报") { (application as ScreenPalApplication).ttsManager.stop() }""")
        )
    }

    @Test
    fun copyCallback_unchanged() {
        assertTrue(
            "「复制」回调必须仍写剪贴板（念念）",
            overlaySrc.contains("""cm.setPrimaryClip(ClipData.newPlainText("念念", lastRecognizedText))""")
        )
        assertTrue(
            "「复制」回调必须仍 toast 已复制",
            overlaySrc.contains("""Toast.makeText(this@SelectionOverlayActivity, "已复制", Toast.LENGTH_SHORT).show()""")
        )
    }

    @Test
    fun reselectCallback_unchanged() {
        assertTrue(
            "「重新框选」回调必须仍隐藏结果卡",
            overlaySrc.contains("""actionButton("重新框选") {
            resultCard.visibility = View.GONE
            selectionView.resetForReselection()
        }""")
        )
    }

    @Test
    fun finishCallback_unchanged() {
        assertTrue(
            "「完成」回调必须仍是 finish()",
            overlaySrc.contains("""actionButton("完成") { finish() }""")
        )
    }

    @Test
    fun fourButtons_wiredWithSameSingleListenerContract() {
        assertTrue(
            "按钮监听器接线必须保持单一 onClick 转发",
            overlaySrc.contains("setOnClickListener { onClick() }")
        )
    }

    // ---- 胶囊样式接线 ----

    @Test
    fun buttons_usePillBackgroundFactory() {
        assertTrue(
            "按钮背景必须来自 pillPressed 工厂",
            overlaySrc.contains("background = pillPressed(")
        )
    }

    @Test
    fun buttons_useBrandPurpleText_15sp_medium_noAllCaps() {
        assertTrue(
            "按钮文字必须用品牌紫 PILL_TEXT_COLOR",
            overlaySrc.contains("setTextColor(PILL_TEXT_COLOR)")
        )
        assertTrue(
            "按钮文字必须 15sp（PILL_TEXT_SP）",
            overlaySrc.contains("textSize = PILL_TEXT_SP")
        )
        assertTrue(
            "按钮文字必须 medium（sans-serif-medium）",
            overlaySrc.contains("""Typeface.create("sans-serif-medium", Typeface.NORMAL)""")
        )
        assertTrue(
            "按钮文字必须关闭全大写",
            overlaySrc.contains("isAllCaps = false")
        )
    }

    @Test
    fun buttons_equalWeight_and44dpHeight() {
        assertTrue(
            "四按钮必须等权重均分（width=0 + weight=1，高 44dp）",
            overlaySrc.contains("LinearLayout.LayoutParams(0, (PILL_HEIGHT_DP * density).roundToInt(), 1f)")
        )
    }

    @Test
    fun buttons_horizontalMargin4dp() {
        assertTrue(
            "按钮必须有水平 4dp margin",
            overlaySrc.contains("leftMargin = (PILL_MARGIN_DP * density).roundToInt()") &&
                overlaySrc.contains("rightMargin = (PILL_MARGIN_DP * density).roundToInt()")
        )
    }

    // ---- 设计护栏：胶囊参数不得被随手改动 ----

    @Test
    fun pillDimensions_constantsAreGroundTruth() {
        // 字号/高度走常量而非裸数字，防止实现漂移
        assertFalse(
            "actionButton 内不得再出现裸 textSize = 13f",
            overlaySrc.contains("textSize = 13f")
        )
    }

    // ---- 识别文本滚动区（2026-08-29-result-card-polish tasks 2）----

    @Test
    fun maxHeight_is40PercentOfScreen() {
        assertEquals(320, SelectionOverlayActivity.resultTextMaxHeightPx(800))
        assertEquals(864, SelectionOverlayActivity.resultTextMaxHeightPx(2160))
        assertEquals(0, SelectionOverlayActivity.resultTextMaxHeightPx(0))
    }

    @Test
    fun maxHeight_fractionIsGroundTruth() {
        assertEquals(
            "限高比例必须是屏高 40%",
            0.4f,
            SelectionOverlayActivity.RESULT_TEXT_MAX_HEIGHT_FRACTION,
            0f
        )
    }

    @Test
    fun textArea_wrappedInScrollView_withMeasureClamp() {
        assertTrue(
            "识别文本必须包在 ScrollView 内滚动",
            overlaySrc.contains("object : ScrollView(this)")
        )
        assertTrue(
            "超出 40% 屏高必须被 onMeasure clamp 截断",
            overlaySrc.contains("setMeasuredDimension(measuredWidth, maxTextHeightPx)")
        )
        assertTrue(
            "限高必须按运行时屏高动态计算",
            overlaySrc.contains("resultTextMaxHeightPx(resources.displayMetrics.heightPixels)")
        )
    }

    @Test
    fun textArea_maxLinesRemoved_selectableKept() {
        assertFalse(
            "maxLines = 8 必须移除（改滚动）",
            overlaySrc.contains("maxLines = 8")
        )
        assertTrue(
            "长按选择文本能力必须保留",
            overlaySrc.contains("setTextIsSelectable(true)")
        )
    }

    @Test
    fun textArea_scrollerAddedBeforeMetaAndActions() {
        assertTrue(
            "文本必须挂在滚动区里",
            overlaySrc.contains("textScroll!!.addView(resultText)")
        )
        val scrollAdds = overlaySrc.indexOf("card.addView(textScroll)")
        val metaAdds = overlaySrc.indexOf("card.addView(resultMeta)")
        val actionsAdds = overlaySrc.indexOf("card.addView(actions)")
        assertTrue(
            "卡片顺序必须是 滚动文本 → meta → 按钮行",
            scrollAdds in 0 until metaAdds && metaAdds in 0 until actionsAdds
        )
    }
}
