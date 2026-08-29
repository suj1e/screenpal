package com.suj1e.screenpal.service

import android.app.AlertDialog
import android.content.Intent
import android.os.Looper
import android.provider.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlertDialog
import java.io.File

/**
 * 未开启无障碍的引导对话框（2026-08-29-a11y-screenshot，task 3）：
 * 用途说明 + 「去开启」（直跳系统无障碍设置）+「本次仍用录屏」（老路径），
 * 取消（返回键）恢复悬浮球。按钮回调抽成 [FloatingWindowService.showA11yGuideDialog]
 * 参数，JVM 可直接验证接线。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FloatingWindowServiceA11yGuideDialogTest {

    private val app = RuntimeEnvironment.getApplication()
    private val service = Robolectric.buildService(FloatingWindowService::class.java).create().get()

    /** 框架 AlertController 的按钮/取消事件经 Handler 投递，须 idle 主循环后才触发回调。 */
    private fun flushMain() = shadowOf(Looper.getMainLooper()).idle()

    // region 对话框行为（注入录制回调）

    @Test
    fun guideDialog_showsPurposeCopy_withTwoButtonExits() {
        service.showA11yGuideDialog({}, {}, {})

        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        assertNotNull("必须弹出引导对话框", dialog)
        assertTrue(
            "标题必须说明「免授权」价值",
            shadowOf(dialog).title.contains("免授权")
        )
        assertEquals("去开启", dialog.getButton(AlertDialog.BUTTON_POSITIVE).text)
        assertEquals("本次仍用录屏", dialog.getButton(AlertDialog.BUTTON_NEGATIVE).text)
    }

    @Test
    fun guideDialog_messageExplainsPurpose() {
        service.showA11yGuideDialog({}, {}, {})

        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        val message = shadowOf(dialog).getMessage()
        assertTrue(
            "正文必须说明用途（免授权静默截屏识别文字）",
            message?.contains("静默") == true && message?.contains("识别") == true
        )
    }

    @Test
    fun positiveButton_triggersGoToSettingsCallback() {
        var goToSettings = 0
        service.showA11yGuideDialog({ goToSettings++ }, { fail("正键不得走录屏") }, { fail("正键不得触发 cancel") })

        ShadowAlertDialog.getLatestAlertDialog()
            .getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        flushMain()

        assertEquals(1, goToSettings)
    }

    @Test
    fun negativeButton_triggersUseProjectionCallback() {
        var useProjection = 0
        service.showA11yGuideDialog({ fail("副键不得跳设置") }, { useProjection++ }, { fail("副键不得触发 cancel") })

        ShadowAlertDialog.getLatestAlertDialog()
            .getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
        flushMain()

        assertEquals(1, useProjection)
    }

    @Test
    fun cancel_triggersRestoreBallCallback() {
        var cancelled = 0
        service.showA11yGuideDialog({ fail("cancel 不得跳设置") }, { fail("cancel 不得走录屏") }, { cancelled++ })

        ShadowAlertDialog.getLatestAlertDialog().cancel()
        flushMain()

        assertEquals(1, cancelled)
    }

    // endregion

    // region 抽取回调的真实行为

    @Test
    fun openAccessibilitySettings_startsSystemSettingsWithNewTask() {
        service.openAccessibilitySettings()

        val intent = shadowOf(app).nextStartedActivity
        assertEquals(Settings.ACTION_ACCESSIBILITY_SETTINGS, intent.action)
        assertTrue(
            "Service 发起 Activity 必须带 NEW_TASK",
            intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0
        )
    }

    @Test
    fun useProjectionThisTime_keepsLegacyProjectionPath() {
        // 回调抽取层只做转发；录屏细节由 legacyCapture/captureScreen 承担（原样保留）。
        val src = File("src/main/java/com/suj1e/screenpal/service/FloatingWindowService.kt").readText()
        val body = Regex("internal fun useProjectionThisTime\\(\\)[^}]*}").find(src)?.value
        assertNotNull(body)
        assertTrue("本次仍用录屏必须转入 legacyCapture", body!!.contains("legacyCapture()"))
    }

    // endregion

    // region showAccessibilityGuide 接线契约

    @Test
    fun showAccessibilityGuide_wiresExtractedCallbacks() {
        val src = File("src/main/java/com/suj1e/screenpal/service/FloatingWindowService.kt").readText()
        assertTrue(
            "去开启必须接 openAccessibilitySettings",
            src.contains("onGoToSettings = ::openAccessibilitySettings")
        )
        assertTrue(
            "本次仍用录屏必须接 useProjectionThisTime",
            src.contains("onUseProjection = ::useProjectionThisTime")
        )
        assertTrue(
            "取消必须恢复悬浮球（showFloatingBall）",
            src.contains("onCancel = ::showFloatingBall")
        )
    }

    @Test
    fun guideBranch_keepsTask2Routing() {
        val src = File("src/main/java/com/suj1e/screenpal/service/FloatingWindowService.kt").readText()
        assertTrue(
            "路由 GUIDE 分支必须仍指向 showAccessibilityGuide",
            src.contains("ScreenshotRoute.GUIDE -> showAccessibilityGuide()")
        )
    }

    // endregion
}
