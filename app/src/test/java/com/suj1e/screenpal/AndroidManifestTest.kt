package com.suj1e.screenpal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AndroidManifestTest {

    private val manifestFile: File
        get() = File("src/main/AndroidManifest.xml")

    @Test
    fun manifestFileExists() {
        assertTrue("AndroidManifest.xml should exist", manifestFile.exists())
    }

    @Test
    fun manifestContainsRequiredPermissions() {
        val content = manifestFile.readText()
        val requiredPermissions = listOf(
            "android.permission.POST_NOTIFICATIONS",
            "android.permission.FOREGROUND_SERVICE",
            "android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION",
            "android.permission.WAKE_LOCK",
            "android.permission.SYSTEM_ALERT_WINDOW",
            "android.permission.INTERNET"
        )
        requiredPermissions.forEach { permission ->
            assertTrue("Manifest should contain permission: $permission", content.contains(permission))
        }
    }

    @Test
    fun manifestContainsRequiredComponents() {
        val content = manifestFile.readText()
        val requiredComponents = listOf(
            "ScreenPalApplication",
            "MainActivity",
            "SelectionOverlayActivity",
            "ScreenCaptureService",
            "FloatingWindowService",
            "FileProvider"
        )
        requiredComponents.forEach { component ->
            assertTrue("Manifest should contain component: $component", content.contains(component))
        }
    }

    @Test
    fun manifestContainsFileProviderConfiguration() {
        val content = manifestFile.readText()
        assertTrue("Manifest should contain FileProvider authorities pattern",
            content.contains("\${applicationId}.fileprovider"))
        assertTrue("Manifest should reference file_paths.xml",
            content.contains("@xml/file_paths"))
    }

    private val stringsFile: File
        get() = File("src/main/res/values/strings.xml")

    // region Launcher icon & label contract (2026-08-27-launcher-icon-and-label)

    @Test
    fun manifestReferencesMipmapLauncherIcons() {
        val content = manifestFile.readText()
        assertTrue(
            "Application should reference @mipmap/ic_launcher as icon",
            content.contains("""android:icon="@mipmap/ic_launcher"""")
        )
        assertTrue(
            "Application should reference @mipmap/ic_launcher_round as roundIcon",
            content.contains("""android:roundIcon="@mipmap/ic_launcher_round"""")
        )
    }

    @Test
    fun manifestApplicationIconNoLongerReferencesDrawableForeground() {
        val content = manifestFile.readText()
        assertFalse(
            "Application android:icon must not reference a drawable layer like ic_launcher_foreground",
            content.contains("""android:icon="@drawable/ic_launcher_foreground"""")
        )
        assertFalse(
            "Application android:roundIcon must not reference a drawable layer like ic_launcher_foreground",
            content.contains("""android:roundIcon="@drawable/ic_launcher_foreground"""")
        )
    }

    @Test
    fun manifestReferencesAppNameLabel() {
        val content = manifestFile.readText()
        assertTrue(
            "Application should declare android:label=\"@string/app_name\"",
            content.contains("""android:label="@string/app_name"""")
        )
    }

    @Test
    fun appNameIsChineseNiannian() {
        val content = stringsFile.readText()
        assertTrue(
            "strings.xml should define app_name as 念念, got: ${Regex("<string name=\"app_name\">([^<]*)</string>").find(content)?.groupValues?.get(1)}",
            content.contains("<string name=\"app_name\">念念</string>")
        )
    }

    @Test
    fun launcherDrawablesAreRedrawnVectorsNotSelectors() {
        val background = File("src/main/res/drawable/ic_launcher_background.xml")
        val foreground = File("src/main/res/drawable/ic_launcher_foreground.xml")
        assertTrue("ic_launcher_background.xml should exist", background.exists())
        assertTrue("ic_launcher_foreground.xml should exist", foreground.exists())
        listOf(background to "background", foreground to "foreground").forEach { (file, name) ->
            val content = file.readText()
            assertTrue("$name should be a <vector>, got a selector", content.contains("<vector"))
            assertFalse("$name must not be an empty <selector> shell", content.contains("<selector"))
        }
    }

    @Test
    fun launcherBackgroundMatchesFloatingBallGradientColors() {
        val content = File("src/main/res/drawable/ic_launcher_background.xml").readText()
        listOf("#FF6366F1", "#FF7C3AED", "#FFA855F7").forEach { color ->
            assertTrue("Background gradient should use floating ball color $color", content.contains(color))
        }
        // A bare <gradient> child of <path> is silently dropped by AAPT2 (icon
        // renders without its fill); gradients must be aapt:attr-wrapped.
        assertTrue(
            "Gradient must be wrapped in <aapt:attr name=\"android:fillColor\"> to survive AAPT2",
            content.contains("<aapt:attr name=\"android:fillColor\">")
        )
    }

    @Test
    fun launcherForegroundUsesWhiteWaveformBars() {
        val content = File("src/main/res/drawable/ic_launcher_foreground.xml").readText()
        assertTrue(
            "Foreground waveform bars should use the floating ball white #E6FFFFFF",
            content.contains("#E6FFFFFF")
        )
    }

    /**
     * Pins the adaptive-icon safe zone requirement: after applying the declared
     * <group> transform, every coordinate of every waveform path must fall
     * inside 21dp..87dp of the 108x108 viewport.
     */
    @Test
    fun launcherForegroundWaveformEnvelopeStaysInSafeZone() {
        val content = File("src/main/res/drawable/ic_launcher_foreground.xml").readText()
        val viewportWidth = attrValue(Regex("""android:viewportWidth="([0-9.]+)""""), content)?.toDouble()
        val viewportHeight = attrValue(Regex("""android:viewportHeight="([0-9.]+)""""), content)?.toDouble()
        assertEquals("Foreground must use the 108x108 adaptive icon viewport", 108.0, viewportWidth!!, 0.001)
        assertEquals("Foreground must use the 108x108 adaptive icon viewport", 108.0, viewportHeight!!, 0.001)

        val group = Regex("<group[^>]*>").find(content)?.value
        assertNotNull("Waveform paths should be wrapped in one transform <group>", group)
        val attrs = group!!.let { g ->
            Regex("""android:([a-zA-Z]+)="([-+]?[0-9.]+)"""").findAll(g)
                .associate { it.groupValues[1] to it.groupValues[2].toDouble() }
        }

        fun transform(v: Double, scaleKey: String, pivotKey: String, translateKey: String): Double {
            val s = attrs[scaleKey] ?: 1.0
            val p = attrs[pivotKey] ?: 0.0
            val t = attrs[translateKey] ?: 0.0
            return (v - p) * s + p + t
        }

        var minX = Double.MAX_VALUE; var maxX = -Double.MAX_VALUE
        var minY = Double.MAX_VALUE; var maxY = -Double.MAX_VALUE
        Regex("""android:pathData="([^"]+)"""").findAll(content).forEach { match ->
            parseAbsolutePoints(match.groupValues[1]).forEach { (x, y) ->
                val tx = transform(x, "scaleX", "pivotX", "translateX")
                val ty = transform(y, "scaleY", "pivotY", "translateY")
                minX = minOf(minX, tx); maxX = maxOf(maxX, tx)
                minY = minOf(minY, ty); maxY = maxOf(maxY, ty)
            }
        }
        assertTrue("Envelope should not be empty", minX != Double.MAX_VALUE)
        assertTrue(
            "Waveform envelope x [$minX..$maxX] must stay within safe zone 21..87",
            minX >= 21 && maxX <= 87
        )
        assertTrue(
            "Waveform envelope y [$minY..$maxY] must stay within safe zone 21..87",
            minY >= 21 && maxY <= 87
        )
    }

    /** Extracts all absolute-coordinate points from M/L/C path data (control points included). */
    private fun parseAbsolutePoints(pathData: String): List<Pair<Double, Double>> {
        val token = Regex("[MLCZ]|[-+]?(?:\\d*\\.\\d+|\\d+)")
        val points = mutableListOf<Pair<Double, Double>>()
        var numbers = mutableListOf<Double>()
        for (t in token.findAll(pathData)) {
            val v = t.value
            if (v.length == 1 && v[0].isLetter()) {
                check(v in setOf("M", "L", "C", "Z")) { "Unsupported path command '$v' in test parser" }
                if (!numbers.isEmpty()) {
                    points.addAll(pairs(numbers))
                    numbers = mutableListOf()
                }
            } else {
                numbers.add(v.toDouble())
            }
        }
        points.addAll(pairs(numbers))
        return points
    }

    private fun pairs(nums: List<Double>): List<Pair<Double, Double>> =
        nums.toList().chunked(2).filter { it.size == 2 }.map { it[0] to it[1] }

    private fun attrValue(regex: Regex, content: String): String? =
        regex.find(content)?.groupValues?.get(1)

    @Test
    fun anydpiFallbackLauncherIconsExistAndArePlainVectors() {
        val fallbacks = listOf("ic_launcher.xml", "ic_launcher_round.xml")
        fallbacks.forEach { name ->
            val file = File("src/main/res/mipmap-anydpi/$name")
            assertTrue("mipmap-anydpi/$name should exist for API 24/25 fallback", file.exists())
            val content = file.readText()
            assertTrue("mipmap-anydpi/$name must be a plain <vector>", content.contains("<vector"))
            assertFalse(
                "mipmap-anydpi/$name must NOT use adaptive-icon (unsupported below API 26)",
                content.contains("adaptive-icon")
            )
            // Same AAPT2 rule as the background layer: no bare <gradient>.
            if (content.contains("<gradient")) {
                assertTrue(
                    "mipmap-anydpi/$name gradient must be wrapped in <aapt:attr name=\"android:fillColor\">",
                    content.contains("<aapt:attr name=\"android:fillColor\">")
                )
            }
        }
    }

    // endregion
}
