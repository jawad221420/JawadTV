package com.jawad.tvlauncher

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent

/**
 * Some Google TV builds (TCL included) refuse to hand the Home button to a
 * third-party launcher. This accessibility service watches for the stock
 * home screen coming to the front and immediately brings Jawad TV on top.
 */
class HomeWatchService : AccessibilityService() {

    private var lastJump = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg !in STOCK_HOMES) return

        val now = SystemClock.elapsedRealtime()
        if (now - lastJump < 800) return
        lastJump = now

        val i = Intent(this, MainActivity::class.java)
            .addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            )
        try {
            startActivity(i)
        } catch (e: Exception) {
        }
    }

    override fun onInterrupt() {}

    companion object {
        val STOCK_HOMES = setOf(
            "com.google.android.apps.tv.launcherx",
            "com.google.android.tvlauncher",
            "com.tcl.tv.launcher",
            "com.tcl.launcher"
        )
    }
}
