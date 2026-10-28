package com.jawad.tvlauncher

import java.util.Calendar
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.tan

/**
 * Offline prayer-time calculator (Umm al-Qura convention):
 * Fajr at 18.5° below the horizon, Isha fixed at 90 minutes after Maghrib,
 * Asr with the standard (Shafi'i) shadow factor.
 * Based on the well-known PrayTimes.org astronomical method.
 */
class PrayerTimes(
    private val latitude: Double,
    private val longitude: Double
) {
    data class Day(
        val fajr: Double,
        val sunrise: Double,
        val dhuhr: Double,
        val asr: Double,
        val maghrib: Double,
        val isha: Double
    )

    private val fajrAngle = 18.5
    private val ishaMinutesAfterMaghrib = 90.0
    private val asrFactor = 1.0

    /** Returns the five prayer times (in decimal hours, local time) for the given calendar day. */
    fun forDay(cal: Calendar): Day {
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH) + 1
        val day = cal.get(Calendar.DAY_OF_MONTH)

        val tz = cal.timeZone
        val tzHours = tz.getOffset(cal.timeInMillis) / 3600000.0

        val jDate = julian(year, month, day) - longitude / (15.0 * 24.0)

        var fajr = sunAngleTime(jDate, fajrAngle, 5.0 / 24.0, ccw = true)
        var sunrise = sunAngleTime(jDate, 0.833, 6.0 / 24.0, ccw = true)
        var dhuhr = midDay(jDate, 12.0 / 24.0)
        var asr = asrTime(jDate, asrFactor, 13.0 / 24.0)
        var maghrib = sunAngleTime(jDate, 0.833, 18.0 / 24.0, ccw = false)

        val shift = tzHours - longitude / 15.0
        fajr += shift
        sunrise += shift
        dhuhr += shift + 1.0 / 60.0
        asr += shift
        maghrib += shift
        val isha = maghrib + ishaMinutesAfterMaghrib / 60.0

        return Day(fajr, sunrise, dhuhr, asr, maghrib, isha)
    }

    private fun julian(y0: Int, m0: Int, d: Int): Double {
        var y = y0
        var m = m0
        if (m <= 2) {
            y -= 1
            m += 12
        }
        val a = floor(y / 100.0)
        val b = 2 - a + floor(a / 4.0)
        return floor(365.25 * (y + 4716)) + floor(30.6001 * (m + 1)) + d + b - 1524.5
    }

    private data class SunPos(val declination: Double, val equation: Double)

    private fun sunPosition(jd: Double): SunPos {
        val dd = jd - 2451545.0
        val g = fixAngle(357.529 + 0.98560028 * dd)
        val q = fixAngle(280.459 + 0.98564736 * dd)
        val l = fixAngle(q + 1.915 * dsin(g) + 0.020 * dsin(2 * g))
        val e = 23.439 - 0.00000036 * dd
        val ra = darctan2(dcos(e) * dsin(l), dcos(l)) / 15.0
        val eqt = q / 15.0 - fixHour(ra)
        val decl = darcsin(dsin(e) * dsin(l))
        return SunPos(decl, eqt)
    }

    private fun midDay(jDate: Double, time: Double): Double {
        val eqt = sunPosition(jDate + time).equation
        return fixHour(12 - eqt)
    }

    private fun sunAngleTime(jDate: Double, angle: Double, time: Double, ccw: Boolean): Double {
        val decl = sunPosition(jDate + time).declination
        val noon = midDay(jDate, time)
        val t = (1.0 / 15.0) * darccos(
            (-dsin(angle) - dsin(decl) * dsin(latitude)) / (dcos(decl) * dcos(latitude))
        )
        return noon + if (ccw) -t else t
    }

    private fun asrTime(jDate: Double, factor: Double, time: Double): Double {
        val decl = sunPosition(jDate + time).declination
        val angle = -darccot(factor + dtan(abs(latitude - decl)))
        return sunAngleTime(jDate, angle, time, ccw = false)
    }

    private fun fixAngle(a: Double) = fix(a, 360.0)
    private fun fixHour(a: Double) = fix(a, 24.0)
    private fun fix(a0: Double, b: Double): Double {
        var a = a0 - b * floor(a0 / b)
        if (a < 0) a += b
        return a
    }

    private fun dtr(d: Double) = d * Math.PI / 180.0
    private fun rtd(r: Double) = r * 180.0 / Math.PI
    private fun dsin(d: Double) = sin(dtr(d))
    private fun dcos(d: Double) = cos(dtr(d))
    private fun dtan(d: Double) = tan(dtr(d))
    private fun darcsin(x: Double) = rtd(asin(x.coerceIn(-1.0, 1.0)))
    private fun darccos(x: Double) = rtd(acos(x.coerceIn(-1.0, 1.0)))
    private fun darctan2(y: Double, x: Double) = rtd(atan2(y, x))
    private fun darccot(x: Double) = rtd(atan(1.0 / x))

    companion object {
        /** Turns decimal hours into a Calendar on the same day as [base] (rounded to the minute). */
        fun toCalendar(base: Calendar, hours: Double, tz: TimeZone): Calendar {
            val c = Calendar.getInstance(tz)
            c.timeInMillis = base.timeInMillis
            val totalMinutes = Math.round(hours * 60.0).toInt()
            c.set(Calendar.HOUR_OF_DAY, 0)
            c.set(Calendar.MINUTE, 0)
            c.set(Calendar.SECOND, 0)
            c.set(Calendar.MILLISECOND, 0)
            c.add(Calendar.MINUTE, totalMinutes)
            return c
        }
    }
}
