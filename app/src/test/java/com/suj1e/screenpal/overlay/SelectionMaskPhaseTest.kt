package com.suj1e.screenpal.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 遮罩状态机契约（2026-08-29-selection-mode，两模式统一）：
 * 绘制期（含初始/拒选/重新框选）一律无全屏遮罩（截图原亮度）；仅确认后
 * 圈外变暗。「重新框选」/新手势/拒选把 CONFIRMED 拉回 DRAWING。
 */
class SelectionMaskPhaseTest {

    // ---------- 状态机转移 ----------

    @Test
    fun initial_phase_isDrawing() {
        assertEquals(SelectionOverlayActivity.SelectionPhase.DRAWING, SelectionOverlayActivity.SelectionPhase.initial)
    }

    @Test
    fun confirm_fromDrawing_entersConfirmed() {
        assertEquals(
            SelectionOverlayActivity.SelectionPhase.CONFIRMED,
            SelectionOverlayActivity.nextSelectionPhase(
                SelectionOverlayActivity.SelectionPhase.DRAWING,
                SelectionOverlayActivity.SelectionPhaseAction.CONFIRM
            )
        )
    }

    @Test
    fun confirm_fromConfirmed_isIdempotent() {
        assertEquals(
            SelectionOverlayActivity.SelectionPhase.CONFIRMED,
            SelectionOverlayActivity.nextSelectionPhase(
                SelectionOverlayActivity.SelectionPhase.CONFIRMED,
                SelectionOverlayActivity.SelectionPhaseAction.CONFIRM
            )
        )
    }

    @Test
    fun gestureStart_fromConfirmed_returnsToDrawing() {
        // 确认后再次落笔画新选区：遮罩立刻退场（回到原亮度绘制态）。
        assertEquals(
            SelectionOverlayActivity.SelectionPhase.DRAWING,
            SelectionOverlayActivity.nextSelectionPhase(
                SelectionOverlayActivity.SelectionPhase.CONFIRMED,
                SelectionOverlayActivity.SelectionPhaseAction.GESTURE_START
            )
        )
    }

    @Test
    fun reselect_fromConfirmed_returnsToDrawing() {
        //「重新框选」按钮：回无遮罩态。
        assertEquals(
            SelectionOverlayActivity.SelectionPhase.DRAWING,
            SelectionOverlayActivity.nextSelectionPhase(
                SelectionOverlayActivity.SelectionPhase.CONFIRMED,
                SelectionOverlayActivity.SelectionPhaseAction.RESELECT
            )
        )
    }

    @Test
    fun reject_fromEitherPhase_returnsToDrawing() {
        // 拒选（单点/过小）两模式一致：不进遮罩态。
        assertEquals(
            SelectionOverlayActivity.SelectionPhase.DRAWING,
            SelectionOverlayActivity.nextSelectionPhase(
                SelectionOverlayActivity.SelectionPhase.DRAWING,
                SelectionOverlayActivity.SelectionPhaseAction.REJECT
            )
        )
        assertEquals(
            SelectionOverlayActivity.SelectionPhase.DRAWING,
            SelectionOverlayActivity.nextSelectionPhase(
                SelectionOverlayActivity.SelectionPhase.CONFIRMED,
                SelectionOverlayActivity.SelectionPhaseAction.REJECT
            )
        )
    }

    @Test
    fun gestureStart_fromDrawing_staysDrawing() {
        assertEquals(
            SelectionOverlayActivity.SelectionPhase.DRAWING,
            SelectionOverlayActivity.nextSelectionPhase(
                SelectionOverlayActivity.SelectionPhase.DRAWING,
                SelectionOverlayActivity.SelectionPhaseAction.GESTURE_START
            )
        )
    }

    // ---------- 遮罩可见性 ----------

    @Test
    fun mask_isHiddenWhileDrawing_andShownOnlyAfterConfirm() {
        assertFalse(
            "绘制期不得画全屏遮罩",
            SelectionOverlayActivity.shouldDrawMask(SelectionOverlayActivity.SelectionPhase.DRAWING)
        )
        assertTrue(
            "确认后才画全屏遮罩",
            SelectionOverlayActivity.shouldDrawMask(SelectionOverlayActivity.SelectionPhase.CONFIRMED)
        )
    }
}
