package com.suj1e.screenpal

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
}
