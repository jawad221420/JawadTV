package com.jawad.tvlauncher

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent

/**
 * Some Google TV builds (TCL included) refuse to hand the Home button to a
 * third-party launcher. This accessibility service watches for the stock
 * home screen coming to the front and immediately brings Jawad TV on top.
 */
class HomeWatchService : AccessibilityService() {

    private var lastJump = 0L
    private val handler = Handler(Looper.getMainLooper())

    override fun onServiceConnected() {
        super.onServiceConnected()
        // If the stock home is already showing when the service starts, take over.
        handler.postDelayed({ checkActiveWindow() }, 500)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkg = event.packageName?.toString()
        if (pkg != null && pkg in STOCK_HOMES) {
            jump()
            return
        }
        if (event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            checkActiveWindow()
        }
    }

    private fun checkActiveWindow() {
        val active = try { rootInActiveWindow?.packageName?.toString() } catch (e: Exception) { null }
        if (active != null && active in STOCK_HOMES) jump()
    }

    private fun jump() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastJump < 350) return
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
        // Google TV sometimes re-asserts itself a moment later; check again.
        handler.postDelayed({ checkActiveWindow() }, 700)
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
