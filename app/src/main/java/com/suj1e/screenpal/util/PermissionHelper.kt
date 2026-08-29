package com.suj1e.screenpal.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

object PermissionHelper {

    const val REQUEST_OVERLAY = 1001
    const val REQUEST_NOTIFICATION = 1002

    fun hasOverlayPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else true
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else true
    }

    fun requestOverlayPermission(activity: Activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return

        // MIUI/HyperOS 不认带 package URI 的标准悬浮窗 Intent，会回退到
        // 「所有应用」总列表要求逐个查找——直接跳本 App 的应用详情页，
        // 悬浮窗开关就在其中（全 ROM 可靠）。
        val manufacturer = Build.MANUFACTURER.uppercase()
        if (manufacturer.contains("XIAOMI") || manufacturer.contains("REDMI")) {
            activity.startActivityForResult(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:${activity.packageName}")
                ),
                REQUEST_OVERLAY
            )
            return
        }

        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${activity.packageName}")
        )
        activity.startActivityForResult(intent, REQUEST_OVERLAY)
    }

    fun requestNotificationPermission(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                REQUEST_NOTIFICATION
            )
        }
    }

    fun getAllPermissionStatus(context: Context): Map<String, Boolean> {
        return mapOf(
            "overlay" to hasOverlayPermission(context),
            "notification" to hasNotificationPermission(context)
        )
    }

    /**
     * Pure mapping from Build.MANUFACTURER to a normalized brand token.
     * Kept side-effect free so OEM handling is unit-testable.
     */
    fun oemBrandFor(manufacturer: String?): String? {
        if (manufacturer.isNullOrBlank()) return null
        return when {
            manufacturer.contains("Xiaomi", ignoreCase = true) ||
                manufacturer.contains("Redmi", ignoreCase = true) -> "XIAOMI"
            manufacturer.contains("Huawei", ignoreCase = true) ||
                manufacturer.contains("HONOR", ignoreCase = true) -> "HUAWEI"
            manufacturer.contains("OPPO", ignoreCase = true) -> "OPPO"
            manufacturer.contains("vivo", ignoreCase = true) ||
                manufacturer.contains("iQOO", ignoreCase = true) -> "VIVO"
            else -> null
        }
    }

    fun getOemSpecialIntent(context: Context): Intent? {
        return when (oemBrandFor(Build.MANUFACTURER)) {
            "XIAOMI" -> Intent("miui.intent.action.APP_PERM_EDITOR").apply {
                setClassName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.permissions.AppPermissionsEditorActivity"
                )
                putExtra("extra_pkgname", context.packageName)
            }
            "HUAWEI" -> Intent("com.huawei.systemmanager.optimize.process.ProtectActivity")
            "OPPO" -> Intent("oppo.intent.action.OPPO_PERMISSION")
            "VIVO" -> Intent("permission.intent.action.softdetail")
            else -> null
        }
    }
}
