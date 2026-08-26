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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${activity.packageName}")
            )
            activity.startActivityForResult(intent, 1001)
        }
    }

    fun requestNotificationPermission(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                1002
            )
        }
    }

    fun getAllPermissionStatus(context: Context): Map<String, Boolean> {
        return mapOf(
            "overlay" to hasOverlayPermission(context),
            "notification" to hasNotificationPermission(context)
        )
    }

    fun getOemSpecialIntent(context: Context): Intent? {
        return when {
            Build.MANUFACTURER.contains("Xiaomi", ignoreCase = true) -> {
                Intent("miui.intent.action.APP_PERM_EDITOR").apply {
                    setClassName(
                        "com.miui.securitycenter",
                        "com.miui.permcenter.permissions.AppPermissionsEditorActivity"
                    )
                    putExtra("extra_pkgname", context.packageName)
                }
            }
            Build.MANUFACTURER.contains("Huawei", ignoreCase = true) -> {
                Intent("com.huawei.systemmanager.optimize.process.ProtectActivity")
            }
            Build.MANUFACTURER.contains("OPPO", ignoreCase = true) -> {
                Intent("oppo.intent.action.OPPO_PERMISSION")
            }
            Build.MANUFACTURER.contains("vivo", ignoreCase = true) -> {
                Intent("permission.intent.action.softdetail")
            }
            else -> null
        }
    }
}
