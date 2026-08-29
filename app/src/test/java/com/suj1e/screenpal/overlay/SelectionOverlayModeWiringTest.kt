package com.suj1e.screenpal.overlay

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 双模式接线契约（2026-08-29-selection-mode）：SelectionView 按构造注入的
 * [SelectionMode] 分支；RECT 拖拽画 #337B68EE 半透明填充 + 4dp 描边；
 * Activity 在构造前从设置读取模式（脏值回退由 [SelectionMode.fromStorageValue] 承担）。
 */
class SelectionOverlayModeWiringTest {

    private val overlaySrc =
        File("src/main/java/com/suj1e/screenpal/overlay/SelectionOverlayActivity.kt").readText()

    @Test
    fun selectionView_receivesModeViaConstructorInjection() {
        assertTrue(
            "SelectionView must be constructed with the injected mode",
            overlaySrc.contains("SelectionView(this, mode =")
        )
        assertTrue(
            "Activity must resolve the persisted mode via SelectionMode.fromStorageValue",
            overlaySrc.contains("SelectionMode.fromStorageValue")
        )
    }

    @Test
    fun rectMode_branchesOnInjectedMode_inTouchAndDraw() {
        assertTrue(
            "Touch handling must branch on the injected mode",
            overlaySrc.contains("if (mode == SelectionMode.RECT)")
        )
        assertTrue(
            "Draw path must branch on the injected mode",
            overlaySrc.contains("mode == SelectionMode.RECT")
        )
    }

    @Test
    fun rectMode_paintsSemiTransparentFill_withSharedStroke() {
        assertTrue(
            "RECT fill must be #337B68EE (semi-transparent purple)",
            overlaySrc.contains("#337B68EE")
        )
        assertTrue(
            "RECT stroke must reuse the 4dp shared strokePaint",
            overlaySrc.contains("canvas.drawRect(rect, strokePaint)")
        )
    }

    @Test
    fun maskTiming_drawingUnmasked_confirmedDimmed() {
        assertTrue(
            "Mask must be gated by shouldDrawMask(phase), not drawn unconditionally",
            overlaySrc.contains("if (shouldDrawMask(phase))")
        )
        assertTrue(
            "CONFIRMED hole must be punched with the shared DST_OUT paint",
            overlaySrc.contains("canvas.drawRect(hole, confirmedHolePaint)")
        )
        assertTrue(
            "White secondary stroke must exist for light backgrounds",
            overlaySrc.contains("canvas.drawPath(strokePath, secondaryStrokePaint)") &&
                overlaySrc.contains("canvas.drawRect(rect, secondaryStrokePaint)")
        )
        assertTrue(
            "Secondary stroke must be 2dp",
            overlaySrc.contains("SECONDARY_STROKE_DP = 2f")
        )
    }

    @Test
    fun maskTiming_stateMachineWiredIntoGesturesAndReselect() {
        assertTrue(
            "Gesture start must feed the state machine",
            overlaySrc.contains("SelectionPhaseAction.GESTURE_START")
        )
        assertTrue(
            "Confirm must feed the state machine",
            overlaySrc.contains("SelectionPhaseAction.CONFIRM")
        )
        assertTrue(
            "Reject must feed the state machine",
            overlaySrc.contains("SelectionPhaseAction.REJECT")
        )
        assertTrue(
            "Reselect must feed the state machine",
            overlaySrc.contains("SelectionPhaseAction.RESELECT")
        )
    }
}
