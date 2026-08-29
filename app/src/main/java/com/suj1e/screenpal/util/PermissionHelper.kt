package com.suj1e.screenpal.util

import android.app.Activity
import android.content.ActivityNotFoundException
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
        val intent = overlayPermissionIntent(activity)
        try {
            activity.startActivityForResult(intent, REQUEST_OVERLAY)
        } catch (e: ActivityNotFoundException) {
            // Final fallback: the app-details page exists on every ROM.
            activity.startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:${activity.packageName}")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    /**
     * 悬浮窗授权 Intent（Activity 用 startActivityForResult；Service 语境
     * 需另加 FLAG_ACTIVITY_NEW_TASK）。三级回落：MIUI 权限编辑页 → 应用详情页
     * → 标准总列表。MIUI/HyperOS 不认带 package URI 的标准 Intent，且应用
     * 详情页里的「显示悬浮窗」开关并不等于真正的授权——真开关在「权限管理」
     * 编辑页（带 pkgname 直达）。
     */
    fun overlayPermissionIntent(context: Context): Intent {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val manufacturer = Build.MANUFACTURER.uppercase()
            if (manufacturer.contains("XIAOMI") || manufacturer.contains("REDMI")) {
                // HyperOS retired the old MIUI permission-editor component; an
                // explicit Intent to a missing activity = ActivityNotFoundException
                // crash. Only hand back the OEM deep link if it actually resolves.
                val oem = getOemSpecialIntent(context)
                if (oem != null &&
                    context.packageManager.resolveActivity(oem, 0) != null
                ) return oem
                return Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:${context.packageName}")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            return Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            )
        }
        return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
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
