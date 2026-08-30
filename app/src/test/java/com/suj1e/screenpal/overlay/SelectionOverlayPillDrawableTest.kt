package com.suj1e.screenpal.overlay

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.util.StateSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

/**
 * 结果卡胶囊按钮 drawable 工厂契约（2026-08-29-result-card-polish）：
 * [SelectionOverlayActivity.pillBackground] 常态 = 白底 + 品牌紫 40% 透明描边 +
 * 胶囊圆角；[SelectionOverlayActivity.pillPressed] StateListDrawable pressed 态
 * 白底叠 #14000000、兜底态同常态胶囊。
 *
 * 断言手段说明：GradientDrawable 无圆角/描边公开 getter，圆角走 Robolectric
 * 真实实现的 mGradientState.mRadius 反射（本仓库已有 ReflectionHelpers 先例）；
 * pressed 态不能用 LayerDrawable 叠层——Robolectric 下 addState 会深拷贝其子层
 * 导致 shadow 记录丢失，故工厂用叠色合成等价实现。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SelectionOverlayPillDrawableTest {

    private val strokeColor = 0x667B68EE
    private val strokeWidthPx = 4
    private val cornerRadiusPx = 999f

    /** 真实 GradientState 里的圆角（GradientDrawable 无公开 getter）。 */
    private fun GradientDrawable.cornerViaState(): Float {
        val state = ReflectionHelpers.getField<Any>(this, "mGradientState")
        return ReflectionHelpers.getField(state, "mRadius")
    }

    @Test
    fun pillConstants_matchDesignSpec() {
        // 品牌紫文字（#FF7B68EE 不透明）
        assertEquals(0xFF7B68EE.toInt(), SelectionOverlayActivity.PILL_TEXT_COLOR)
        // 描边 = 品牌紫 40% 透明（0x66 + #7B68EE）
        assertEquals(0x667B68EE, SelectionOverlayActivity.PILL_STROKE_COLOR)
        // pressed 叠底 8% 黑
        assertEquals(0x14000000, SelectionOverlayActivity.PILL_PRESSED_OVERLAY)
        assertEquals(999f, SelectionOverlayActivity.PILL_CORNER_RADIUS_DP, 0f)
        assertEquals(1.5f, SelectionOverlayActivity.PILL_STROKE_DP, 0f)
        assertEquals(44, SelectionOverlayActivity.PILL_HEIGHT_DP)
        assertEquals(4, SelectionOverlayActivity.PILL_MARGIN_DP)
        assertEquals(13f, SelectionOverlayActivity.PILL_TEXT_SP, 0f)
    }

    @Test
    fun pillBackground_fillsWhite_withTransparentPurpleStroke() {
        val d = SelectionOverlayActivity.pillBackground(strokeColor, strokeWidthPx, cornerRadiusPx)

        assertEquals(Color.WHITE, shadowOf(d).lastSetColor)
        assertEquals(strokeColor, shadowOf(d).strokeColor)
        assertEquals(strokeWidthPx, shadowOf(d).strokeWidth)
    }

    @Test
    fun pillBackground_usesPillCornerRadius() {
        val d = SelectionOverlayActivity.pillBackground(strokeColor, strokeWidthPx, cornerRadiusPx)
        assertEquals(cornerRadiusPx, d.cornerViaState(), 0.01f)
    }

    @Test
    fun overlayColor_whiteUnder8PercentBlack_matchesDirectBlend() {
        // 白底叠 #14000000：0.0784 黑 + 0.9216 白 = #EBEBEB（独立手算期望值）
        val blended = SelectionOverlayActivity.overlayColor(Color.WHITE, 0x14000000)
        assertEquals(0xFFEBEBEB.toInt(), blended)
        // 不透明输入 → 不透明输出
        assertEquals(255, Color.alpha(blended))
    }

    @Test
    fun overlayColor_fullyTransparentOverlayKeepsBase() {
        assertEquals(
            "全透明叠层不改底色",
            Color.WHITE,
            SelectionOverlayActivity.overlayColor(Color.WHITE, 0x00000000)
        )
    }

    @Test
    fun pillPressed_pressedState_isWhitePillUnderOverlay_withSameStroke() {
        val d = SelectionOverlayActivity.pillPressed(strokeColor, strokeWidthPx, cornerRadiusPx)

        val pressed = shadowOf(d).getDrawableForState(intArrayOf(android.R.attr.state_pressed))
        assertTrue("pressed 态应有专属胶囊 drawable", pressed is GradientDrawable)
        pressed as GradientDrawable
        // 白底叠 #14000000 = #EBEBEB
        assertEquals(
            SelectionOverlayActivity.overlayColor(Color.WHITE, SelectionOverlayActivity.PILL_PRESSED_OVERLAY),
            shadowOf(pressed).lastSetColor
        )
        assertEquals(0xFFEBEBEB.toInt(), shadowOf(pressed).lastSetColor)
        // 描边规格与常态一致
        assertEquals(strokeColor, shadowOf(pressed).strokeColor)
        assertEquals(strokeWidthPx, shadowOf(pressed).strokeWidth)
        assertEquals(cornerRadiusPx, pressed.cornerViaState(), 0.01f)
    }

    @Test
    fun pillPressed_defaultState_isPlainWhitePill() {
        val d = SelectionOverlayActivity.pillPressed(strokeColor, strokeWidthPx, cornerRadiusPx)

        val normal = shadowOf(d).getDrawableForState(StateSet.WILD_CARD)
        assertTrue("兜底态应为常态白底胶囊", normal is GradientDrawable)
        normal as GradientDrawable
        assertEquals(Color.WHITE, shadowOf(normal).lastSetColor)
        assertEquals(strokeColor, shadowOf(normal).strokeColor)
        assertEquals(cornerRadiusPx, normal.cornerViaState(), 0.01f)
    }

    @Test
    fun pillPressed_registeredStateOrder_pressedBeforeWildCard() {
        // StateListDrawable 取第一个命中态：pressed 必须先于兜底注册，否则按压无反馈。
        val d = SelectionOverlayActivity.pillPressed(strokeColor, strokeWidthPx, cornerRadiusPx)
        val pressed = shadowOf(d).getDrawableForState(intArrayOf(android.R.attr.state_pressed))
        assertTrue("按压态必须能命中（先于兜底注册）", pressed is GradientDrawable)
        val normal = shadowOf(d).getDrawableForState(StateSet.WILD_CARD)
        assertTrue("兜底态必须能命中", normal is GradientDrawable)
    }
}
