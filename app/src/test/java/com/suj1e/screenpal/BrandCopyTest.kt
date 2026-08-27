package com.suj1e.screenpal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Brand copy contract (2026-08-27-brand-copy-niannian): user-visible copy
 * must use the Chinese brand 念念 sourced from strings.xml. Dev-facing
 * literals (channel IDs, log tags, utterance IDs) are whitelisted — they
 * must keep the ScreenPal spelling to avoid orphan notification channels.
 */
class BrandCopyTest {

    private val srcDir = "src/main/java/com/suj1e/screenpal"

    /**
     * Dev-facing tokens that legitimately keep the ScreenPal spelling:
     * class/theme names, log tag values, notification channel IDs,
     * utterance ID, and the package name itself.
     */
    private val allowedScreenPalTokens = Regex(
        "ScreenPalApplication|ScreenPalTheme|ScreenPalFlow|" +
            "ScreenPal_Floating|ScreenPal_Capture|ScreenPal_Utterance|" +
            "com\\.suj1e\\.screenpal"
    )

    @Test
    fun consumerSourcesHaveNoUserVisibleScreenPal() {
        val offenders = listOf(
            "MainActivity.kt",
            "service/FloatingWindowService.kt",
            "service/ScreenCaptureService.kt",
            "overlay/SelectionOverlayActivity.kt"
        ).flatMap { rel ->
            val stripped = File("$srcDir/$rel").readText().replace(allowedScreenPalTokens, "")
            if (stripped.contains("ScreenPal")) {
                stripped.split("\n").filter { it.contains("ScreenPal") }.map { "$rel: ${it.trim()}" }
            } else {
                emptyList()
            }
        }
        assertTrue(
            "User-visible copy must not hardcode ScreenPal (moved to strings.xml):\n" +
                offenders.joinToString("\n"),
            offenders.isEmpty()
        )
    }

    @Test
    fun stringsXmlDefinesBrandEntries() {
        val content = File("src/main/res/values/strings.xml").readText()
        mapOf(
            "app_title" to "念念 · 屏幕识别 + 语音播报",
            "notification_floating_title" to "念念悬浮窗运行中",
            "channel_floating_name" to "念念悬浮窗",
            "channel_capture_name" to "念念截图"
        ).forEach { (name, value) ->
            assertEquals(
                "strings.xml entry $name must match the agreed brand copy",
                "<string name=\"$name\">$value</string>",
                Regex("<string name=\"$name\">([^<]*)</string>").find(content)?.value
            )
        }
    }

    @Test
    fun clipboardLabelIsChineseBrand() {
        val content = File("$srcDir/overlay/SelectionOverlayActivity.kt").readText()
        assertTrue(
            "Clipboard label should be 念念",
            content.contains("""newPlainText("念念"""")
        )
    }

    @Test
    fun notificationChannelIdsAreUntouched() {
        assertTrue(
            "FloatingWindowService CHANNEL_ID must stay ScreenPal_Floating (renaming orphans existing channels)",
            File("$srcDir/service/FloatingWindowService.kt").readText().contains("""CHANNEL_ID = "ScreenPal_Floating"""")
        )
        assertTrue(
            "ScreenCaptureService CHANNEL_ID must stay ScreenPal_Capture (renaming orphans existing channels)",
            File("$srcDir/service/ScreenCaptureService.kt").readText().contains("""CHANNEL_ID = "ScreenPal_Capture"""")
        )
    }

    @Test
    fun mainTitleUsesStringResource() {
        val content = File("$srcDir/MainActivity.kt").readText()
        assertFalse(
            "Main title must not be hardcoded",
            content.contains("""Text("ScreenPal""")
        )
        assertTrue(
            "Main title should reference R.string.app_title",
            content.contains("stringResource(R.string.app_title)")
        )
    }
}
