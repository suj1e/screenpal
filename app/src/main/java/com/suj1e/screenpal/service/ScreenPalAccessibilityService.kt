package com.suj1e.screenpal.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

/**
 * 静默截屏通道（2026-08-29-a11y-screenshot）。
 *
 * 系统绑定的无障碍服务：开启后（API 30+）可直接 takeScreenshot，
 * 免去 MediaProjection 授权弹窗。仅订阅 typeWindowsChanged（最小事件集），
 * 不消费任何无障碍事件；实例随系统连接/解绑增减，[isRunning] 与
 * 静态 [instance] 是 FloatingWindowService 截屏路由的回落依据。
 *
 * open 仅为单测驱动受保护的系统生命周期回调（DrivableService）。
 */
open class ScreenPalAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    companion object {
        const val TAG = "ScreenPalFlow"

        /** 当前被系统绑定的服务实例；未启用或被杀为 null。 */
        @Volatile
        var instance: ScreenPalAccessibilityService? = null
            private set

        fun isRunning(): Boolean = instance != null
    }
}
