package com.jawad.tvlauncher

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

/**
 * Stores the alarm settings and (re)schedules the next ring with AlarmManager.
 *
 * Settings (SharedPreferences "alarm"):
 *  enabled   – on/off
 *  minutes   – minute of day (e.g. 6:30 → 390); ignored when fajr=true
 *  fajr      – ring at Fajr time (computed from PrayerTimes)
 *  daily     – repeat every day (false = once)
 *  sound     – "chime" or "custom" (/sdcard/Download/alarm.mp3)
 */
object AlarmScheduler {

    const val ACTION_RING = "com.jawad.tvlauncher.ALARM_RING"
    const val EXTRA_SNOOZE = "snooze"
    private const val REQ = 4101

    private const val LAT = 21.5433
    private const val LNG = 39.1728

    fun prefs(ctx: Context) = ctx.getSharedPreferences("alarm", Context.MODE_PRIVATE)

    fun isEnabled(ctx: Context) = prefs(ctx).getBoolean("enabled", false)
    fun minutes(ctx: Context) = prefs(ctx).getInt("minutes", 6 * 60 + 30)
    fun isFajr(ctx: Context) = prefs(ctx).getBoolean("fajr", false)
    fun isDaily(ctx: Context) = prefs(ctx).getBoolean("daily", true)
    fun sound(ctx: Context) = prefs(ctx).getString("sound", "chime") ?: "chime"

    fun setEnabled(ctx: Context, on: Boolean) { prefs(ctx).edit().putBoolean("enabled", on).apply(); reschedule(ctx) }
    fun setMinutes(ctx: Context, m: Int) { prefs(ctx).edit().putInt("minutes", ((m % 1440) + 1440) % 1440).apply(); reschedule(ctx) }
    fun setFajr(ctx: Context, on: Boolean) { prefs(ctx).edit().putBoolean("fajr", on).apply(); reschedule(ctx) }
    fun setDaily(ctx: Context, on: Boolean) { prefs(ctx).edit().putBoolean("daily", on).apply(); reschedule(ctx) }
    fun setSound(ctx: Context, s: String) { prefs(ctx).edit().putString("sound", s).apply() }

    /** Next ring time in millis, or -1 when disabled. */
    fun nextTrigger(ctx: Context, from: Long = System.currentTimeMillis()): Long {
        if (!isEnabled(ctx)) return -1
        val now = Calendar.getInstance().apply { timeInMillis = from }
        val cal = Calendar.getInstance().apply { timeInMillis = from }
        for (dayOffset in 0..7) {
            cal.timeInMillis = from
            cal.add(Calendar.DAY_OF_YEAR, dayOffset)
            val targetMin = if (isFajr(ctx)) {
                val t = PrayerTimes(LAT, LNG).forDay(cal)
                Math.round(t.fajr * 60.0).toInt()
            } else minutes(ctx)
            cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
            cal.add(Calendar.MINUTE, targetMin)
            if (cal.timeInMillis > now.timeInMillis + 1000) return cal.timeInMillis
        }
        return -1
    }

    fun reschedule(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pending(ctx)
        am.cancel(pi)
        val at = nextTrigger(ctx)
        if (at < 0) return
        scheduleAt(ctx, at)
    }

    fun scheduleAt(ctx: Context, at: Long) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pending(ctx)
        try {
            val show = PendingIntent.getActivity(
                ctx, REQ + 1, Intent(ctx, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            am.setAlarmClock(AlarmManager.AlarmClockInfo(at, show), pi)
        } catch (e: Exception) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
                else am.setExact(AlarmManager.RTC_WAKEUP, at, pi)
            } catch (e2: Exception) {
            }
        }
    }

    fun snooze(ctx: Context, minutes: Int = 5) {
        scheduleAt(ctx, System.currentTimeMillis() + minutes * 60_000L)
    }

    /** Called after a ring: schedules tomorrow when daily, otherwise turns the alarm off. */
    fun afterRing(ctx: Context) {
        if (isDaily(ctx)) reschedule(ctx) else prefs(ctx).edit().putBoolean("enabled", false).apply()
    }

    private fun pending(ctx: Context): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, REQ, Intent(ctx, AlarmReceiver::class.java).setAction(ACTION_RING),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    fun formatMinutes(m: Int): String {
        val h24 = m / 60
        val min = m % 60
        val h12 = when { h24 == 0 -> 12; h24 > 12 -> h24 - 12; else -> h24 }
        return String.format("%d:%02d %s", h12, min, if (h24 < 12) "AM" else "PM")
    }
}
