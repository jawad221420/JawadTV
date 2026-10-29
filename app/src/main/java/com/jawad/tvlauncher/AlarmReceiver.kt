package com.jawad.tvlauncher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, "android.intent.action.QUICKBOOT_POWERON" -> {
                AlarmScheduler.reschedule(context)
            }
            AlarmScheduler.ACTION_RING -> {
                val i = Intent(context, AlarmActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                try {
                    context.startActivity(i)
                } catch (e: Exception) {
                }
            }
        }
    }
}
