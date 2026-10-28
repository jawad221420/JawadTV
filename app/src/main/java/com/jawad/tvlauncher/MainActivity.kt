package com.jawad.tvlauncher

import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class MainActivity : AppCompatActivity() {

    // Jeddah
    private val latitude = 21.5433
    private val longitude = 39.1728

    private lateinit var tvClock: TextView
    private lateinit var tvAmPm: TextView
    private lateinit var tvDate: TextView
    private lateinit var tvHijri: TextView
    private lateinit var tvNextPrayer: TextView
    private lateinit var tvCountdown: TextView
    private lateinit var appsRow: RecyclerView

    private lateinit var slotViews: List<Pair<View, String>>

    private val prayer = PrayerTimes(latitude, longitude)
    private val adapter = AppAdapter(onOpen = ::openApp, onLongPress = ::togglePin)
    private val handler = Handler(Looper.getMainLooper())
    private var lastMinute = -1

    private val clockFormat = SimpleDateFormat("h:mm", Locale.ENGLISH)
    private val amPmFormat = SimpleDateFormat("a", Locale.ENGLISH)
    private val dateFormat = SimpleDateFormat("EEEE, d MMMM yyyy", Locale.ENGLISH)
    private val timeFormat = SimpleDateFormat("h:mm", Locale.ENGLISH)

    private val tick = object : Runnable {
        override fun run() {
            updateClock()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvClock = findViewById(R.id.tvClock)
        tvAmPm = findViewById(R.id.tvAmPm)
        tvDate = findViewById(R.id.tvDate)
        tvHijri = findViewById(R.id.tvHijri)
        tvNextPrayer = findViewById(R.id.tvNextPrayer)
        tvCountdown = findViewById(R.id.tvCountdown)
        appsRow = findViewById(R.id.appsRow)

        slotViews = listOf(
            findViewById<View>(R.id.slotFajr) to "Fajr",
            findViewById<View>(R.id.slotDhuhr) to "Dhuhr",
            findViewById<View>(R.id.slotAsr) to "Asr",
            findViewById<View>(R.id.slotMaghrib) to "Maghrib",
            findViewById<View>(R.id.slotIsha) to "Isha"
        )
        slotViews.forEach { (view, name) ->
            view.findViewById<TextView>(R.id.slotName).text = name
        }

        appsRow.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        appsRow.adapter = adapter
        appsRow.setHasFixedSize(true)
    }

    override fun onResume() {
        super.onResume()
        loadApps()
        lastMinute = -1
        handler.post(tick)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(tick)
    }

    // ---------------------------------------------------------------- clock + prayers

    private fun updateClock() {
        val now = Calendar.getInstance()
        tvClock.text = clockFormat.format(now.time)
        tvAmPm.text = amPmFormat.format(now.time)

        val minute = now.get(Calendar.MINUTE) + now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.DAY_OF_YEAR) * 1440
        if (minute != lastMinute) {
            lastMinute = minute
            tvDate.text = dateFormat.format(now.time)
            tvHijri.text = hijriDate(now)
            updatePrayers(now)
        }
    }

    private fun hijriDate(now: Calendar): String {
        return try {
            val uloc = android.icu.util.ULocale("en@calendar=islamic-umalqura")
            val fmt = android.icu.text.SimpleDateFormat("d MMMM yyyy", uloc)
            fmt.format(now.time)
        } catch (e: Exception) {
            ""
        }
    }

    private fun updatePrayers(now: Calendar) {
        val tz = now.timeZone
        val today = prayer.forDay(now)
        val names = listOf("Fajr", "Dhuhr", "Asr", "Maghrib", "Isha")
        val hours = listOf(today.fajr, today.dhuhr, today.asr, today.maghrib, today.isha)
        val times = hours.map { PrayerTimes.toCalendar(now, it, tz) }

        slotViews.forEachIndexed { i, (view, _) ->
            view.findViewById<TextView>(R.id.slotTime).text = timeFormat.format(times[i].time)
        }

        var nextIndex = times.indexOfFirst { it.timeInMillis > now.timeInMillis }
        val nextName: String
        val nextTime: Calendar
        if (nextIndex >= 0) {
            nextName = names[nextIndex]
            nextTime = times[nextIndex]
        } else {
            val tomorrow = Calendar.getInstance(tz)
            tomorrow.timeInMillis = now.timeInMillis
            tomorrow.add(Calendar.DAY_OF_YEAR, 1)
            val t = prayer.forDay(tomorrow)
            nextName = "Fajr"
            nextTime = PrayerTimes.toCalendar(tomorrow, t.fajr, tz)
            nextIndex = 0
        }

        tvNextPrayer.text = "$nextName ${timeFormat.format(nextTime.time)}"
        val diffMin = ((nextTime.timeInMillis - now.timeInMillis) / 60000L).toInt().coerceAtLeast(0)
        val h = diffMin / 60
        val m = diffMin % 60
        tvCountdown.text = if (h > 0) "in ${h}h ${m}m" else "in ${m}m"

        slotViews.forEachIndexed { i, (view, _) ->
            val highlight = i == nextIndex
            view.findViewById<TextView>(R.id.slotTime).setTextColor(
                getColor(if (highlight) R.color.accent else R.color.text_secondary)
            )
            view.findViewById<TextView>(R.id.slotName).setTextColor(
                getColor(if (highlight) R.color.text_primary else R.color.text_muted)
            )
        }
    }

    // ---------------------------------------------------------------- apps

    private fun loadApps() {
        val pm = packageManager
        val pinned = prefs().getStringSet(KEY_PINNED, emptySet()) ?: emptySet()

        val tvIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        val mobileIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)

        val byPackage = LinkedHashMap<String, ResolveInfo>()
        for (ri in pm.queryIntentActivities(tvIntent, 0)) {
            byPackage[ri.activityInfo.packageName] = ri
        }
        for (ri in pm.queryIntentActivities(mobileIntent, 0)) {
            byPackage.putIfAbsent(ri.activityInfo.packageName, ri)
        }
        byPackage.remove(packageName)

        val entries = ArrayList<AppEntry>()
        for ((pkg, ri) in byPackage) {
            val banner = try { ri.activityInfo.loadBanner(pm) } catch (e: Exception) { null }
            val icon = try { ri.loadIcon(pm) } catch (e: Exception) { null }
            entries.add(
                AppEntry(
                    packageName = pkg,
                    label = ri.loadLabel(pm).toString(),
                    banner = banner,
                    icon = icon,
                    pinned = pinned.contains(pkg)
                )
            )
        }

        entries.sortWith(compareBy<AppEntry> { !it.pinned }.thenBy { it.label.lowercase(Locale.ENGLISH) })

        entries.add(
            AppEntry(
                packageName = "settings",
                label = getString(R.string.settings),
                banner = null,
                icon = getDrawable(R.drawable.ic_settings),
                isSettings = true
            )
        )

        adapter.submit(entries)
    }

    private fun openApp(entry: AppEntry) {
        if (entry.isSettings) {
            try {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            } catch (e: Exception) {
                Toast.makeText(this, R.string.cannot_open, Toast.LENGTH_SHORT).show()
            }
            return
        }
        val pm = packageManager
        val intent = pm.getLeanbackLaunchIntentForPackage(entry.packageName)
            ?: pm.getLaunchIntentForPackage(entry.packageName)
        if (intent == null) {
            Toast.makeText(this, R.string.cannot_open, Toast.LENGTH_SHORT).show()
            return
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, R.string.cannot_open, Toast.LENGTH_SHORT).show()
        }
    }

    private fun togglePin(entry: AppEntry) {
        if (entry.isSettings) return
        val set = HashSet(prefs().getStringSet(KEY_PINNED, emptySet()) ?: emptySet())
        val nowPinned: Boolean
        if (set.contains(entry.packageName)) {
            set.remove(entry.packageName)
            nowPinned = false
        } else {
            set.add(entry.packageName)
            nowPinned = true
        }
        prefs().edit().putStringSet(KEY_PINNED, set).apply()
        Toast.makeText(this, if (nowPinned) R.string.pinned else R.string.unpinned, Toast.LENGTH_SHORT).show()
        loadApps()
    }

    private fun prefs() = getSharedPreferences("launcher", MODE_PRIVATE)

    override fun onBackPressed() {
        // Home screen: Back does nothing.
    }

    companion object {
        private const val KEY_PINNED = "pinned"
    }
}
