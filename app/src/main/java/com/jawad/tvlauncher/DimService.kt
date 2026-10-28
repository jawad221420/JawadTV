package com.jawad.tvlauncher

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager

/**
 * Draws a full-screen dim (and optional warm tint) layer above every app,
 * so brightness presets, night light and "screen off" also apply while
 * Netflix, YouTube, HDMI inputs etc. are in front.
 *
 * Needs the "Display over other apps" permission. Without it the launcher
 * falls back to dimming only its own screen.
 */
class DimService : Service() {

    private var wm: WindowManager? = null
    private var dimView: View? = null
    private var warmView: View? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startAsForeground()
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val dim = intent?.getFloatExtra(EXTRA_DIM, 0f) ?: 0f
        val warm = intent?.getBooleanExtra(EXTRA_WARM, false) ?: false
        apply(dim.coerceIn(0f, 1f), warm)
        if (dim <= 0f && !warm) {
            stopSelf()
        }
        return START_STICKY
    }

    private fun apply(dim: Float, warm: Boolean) {
        if (!Settings.canDrawOverlays(this)) return
        val manager = wm ?: return

        if (warm) {
            if (warmView == null) {
                warmView = View(this).apply { setBackgroundColor(WARM_COLOR) }
                try { manager.addView(warmView, params()) } catch (e: Exception) { warmView = null }
            }
        } else {
            warmView?.let { try { manager.removeView(it) } catch (e: Exception) {} }
            warmView = null
        }

        if (dim > 0f) {
            if (dimView == null) {
                dimView = View(this).apply { setBackgroundColor(Color.BLACK) }
                try { manager.addView(dimView, params()) } catch (e: Exception) { dimView = null }
            }
            dimView?.alpha = dim
        } else {
            dimView?.let { try { manager.removeView(it) } catch (e: Exception) {} }
            dimView = null
        }
    }

    private fun params(): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }
    }

    private fun startAsForeground() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, getString(R.string.dim_notification), NotificationManager.IMPORTANCE_MIN)
            )
        }
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            Notification.Builder(this, CHANNEL)
        else
            @Suppress("DEPRECATION") Notification.Builder(this)
        val notification = builder
            .setContentTitle(getString(R.string.dim_notification))
            .setSmallIcon(R.drawable.ic_launcher)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(1, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, notification)
        }
    }

    override fun onDestroy() {
        apply(0f, false)
        super.onDestroy()
    }

    companion object {
        const val EXTRA_DIM = "dim"
        const val EXTRA_WARM = "warm"
        private const val CHANNEL = "dim"
        private const val WARM_COLOR = 0x2EFF8C1A

        fun update(context: Context, dim: Float, warm: Boolean) {
            if (!Settings.canDrawOverlays(context)) return
            val i = Intent(context, DimService::class.java)
                .putExtra(EXTRA_DIM, dim)
                .putExtra(EXTRA_WARM, warm)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(i)
                else context.startService(i)
            } catch (e: Exception) {
                // ignore: fall back to in-app dimming
            }
        }
    }
}
