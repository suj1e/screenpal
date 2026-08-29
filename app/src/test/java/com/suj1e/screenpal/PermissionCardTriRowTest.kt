package com.suj1e.screenpal

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 权限卡片三行契约 (2026-08-29-permission-tri-card)：悬浮窗 / 通知 / 无障碍
 * （免弹窗截屏）三行齐；无障碍行状态读 MainUiState.accessibilityEnabled，
 * 未开启「去开启」经 AccessibilityHelper.settingsIntent + FLAG_ACTIVITY_NEW_TASK
 * 深链系统无障碍设置；行说明常显。
 */
class PermissionCardTriRowTest {

    private val mainActivitySrc = File("src/main/java/com/suj1e/screenpal/MainActivity.kt").readText()

    private val permissionCardBody: String
        get() = mainActivitySrc.substringAfter("fun PermissionCard(")

    @Test
    fun card_rendersThreePermissionRows_inOrder() {
        val overlayPos = permissionCardBody.indexOf("悬浮窗权限")
        val notificationPos = permissionCardBody.indexOf("通知权限")
        val accessibilityPos = permissionCardBody.indexOf("无障碍权限（免弹窗截屏）")
        assertTrue(
            "悬浮窗权限 row must render first",
            overlayPos in 0 until notificationPos
        )
        assertTrue(
            "通知权限 row must render before 无障碍权限",
            notificationPos in 0 until accessibilityPos
        )
    }

    @Test
    fun accessibilityRow_enabledShowsConfirmedText_disabledShowsEnableButton() {
        assertTrue(
            "enabled state must show「已开启」text (no button)",
            permissionCardBody.contains("已开启")
        )
        assertTrue(
            "disabled state must show「去开启」TextButton",
            permissionCardBody.contains("去开启")
        )
    }

    @Test
    fun accessibilityRow_descriptionAlwaysVisible() {
        assertTrue(
            "row description must be always visible: 开启后点悬浮球零弹窗识读（Android 10 及以下回退系统录制弹窗）",
            mainActivitySrc.contains("开启后点悬浮球零弹窗识读（Android 10 及以下回退系统录制弹窗）")
        )
    }

    @Test
    fun accessibilityRow_deepLinksToSystemAccessibilitySettings() {
        val uiSrc = mainActivitySrc
        assertTrue(
            "must deep link via AccessibilityHelper.settingsIntent()",
            uiSrc.contains("AccessibilityHelper.settingsIntent()")
        )
        assertTrue(
            "deep link must add FLAG_ACTIVITY_NEW_TASK",
            uiSrc.contains("FLAG_ACTIVITY_NEW_TASK")
        )
        assertTrue(
            "deep link must startActivity",
            uiSrc.contains("startActivity(")
        )
    }

    @Test
    fun accessibilityRow_stateWiredFromMainUiState() {
        assertTrue(
            "PermissionCard must take accessibilityEnabled parameter",
            permissionCardBody.contains("accessibilityEnabled: Boolean")
        )
        assertTrue(
            "MainScreen must pass state.accessibilityEnabled into PermissionCard",
            mainActivitySrc.contains("accessibilityEnabled = state.accessibilityEnabled")
        )
    }

    @Test
    fun accessibilityRow_stateRefreshedOnResume() {
        // onResume 刷新已在 (MainViewModel.refreshPermissions)；无障碍徽章同源刷新由
        // MainViewModelAccessibilityStateTest 钉住，这里确认 Activity 接线不丢。
        val activitySrc = mainActivitySrc.substringAfter("class MainActivity")
        assertTrue(
            "MainActivity.onResume must call viewModel.refreshPermissions",
            activitySrc.contains("viewModel.refreshPermissions(this)")
        )
    }
}
