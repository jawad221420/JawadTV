package com.jawad.tvlauncher

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.media.tv.TvContract
import android.media.tv.TvInputManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
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

    // ---- views
    private lateinit var bgView: View
    private lateinit var dimView: View
    private lateinit var warmView: View
    private lateinit var tvScreenOffHint: TextView
    private lateinit var tvClock: TextView
    private lateinit var tvAmPm: TextView
    private lateinit var tvDate: TextView
    private lateinit var tvHijri: TextView
    private lateinit var tvNextPrayer: TextView
    private lateinit var tvCountdown: TextView
    private lateinit var tvWifi: TextView
    private lateinit var wifiChip: View
    private lateinit var bars: List<View>
    private lateinit var slotViews: List<View>
    private lateinit var brightnessRow: View
    private lateinit var tvBrightness: TextView
    private lateinit var brightnessFill: View
    private lateinit var presetChips: Map<Int, TextView>
    private lateinit var toggleWarm: TextView
    private lateinit var toggleAutoIsha: TextView
    private lateinit var toggleIdle: TextView
    private lateinit var btnScreenOff: TextView
    private lateinit var volumeFill: View
    private lateinit var btnMute: TextView
    private lateinit var inputsRow: LinearLayout
    private lateinit var tvSleepLabel: TextView
    private lateinit var sleepChips: Map<Int, TextView>
    private lateinit var continueSection: View
    private lateinit var tvContinueLabel: TextView
    private lateinit var continueRow: RecyclerView
    private lateinit var appsRow: RecyclerView
    private lateinit var tvMoveHint: TextView

    // ---- state
    private val prayer = PrayerTimes(latitude, longitude)
    private val adapter = AppAdapter(onOpen = ::openApp, onLongPress = ::startMove)
    private val continueAdapter = ContinueAdapter(onOpen = ::openContinue)
    private val handler = Handler(Looper.getMainLooper())
    private var lastMinute = -1
    private var ishaTodayMillis = 0L
    private var sunriseTodayMillis = 0L

    private var brightness = 100          // 15..100
    private var warm = false
    private var autoIsha = false
    private var idleDim = false
    private var screenOff = false
    private var idleDimmed = false
    private var sleepEndMillis = 0L
    private var muted = false

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
    private val wifiTick = object : Runnable {
        override fun run() {
            updateWifi()
            handler.postDelayed(this, 5000)
        }
    }
    private val idleRunnable = Runnable {
        if (idleDim && !screenOff) {
            idleDimmed = true
            applyDisplay()
        }
    }
    private val sleepRunnable = Runnable { fireSleep() }

    // ------------------------------------------------------------------ lifecycle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        bindViews()
        loadPrefs()

        appsRow.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        appsRow.adapter = adapter
        continueRow.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        continueRow.adapter = continueAdapter

        setupDisplayPanel()
        setupControlsPanel()
        wifiChip.setOnClickListener { openSettings(Settings.ACTION_WIFI_SETTINGS) }

        requestWatchNextPermission()
    }

    override fun onResume() {
        super.onResume()
        loadApps()
        loadContinue()
        loadInputs()
        lastMinute = -1
        handler.post(tick)
        handler.post(wifiTick)
        updateVolume()
        applyDisplay()
        resetIdleTimer()
        updateSleepChips()
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(tick)
        handler.removeCallbacks(wifiTick)
        handler.removeCallbacks(idleRunnable)
    }

    override fun onBackPressed() {
        if (adapter.movingPosition >= 0) {
            finishMove()
            return
        }
        // Home screen: Back does nothing else.
    }

    private fun bindViews() {
        bgView = findViewById(R.id.bgView)
        dimView = findViewById(R.id.dimView)
        warmView = findViewById(R.id.warmView)
        tvScreenOffHint = findViewById(R.id.tvScreenOffHint)
        tvClock = findViewById(R.id.tvClock)
        tvAmPm = findViewById(R.id.tvAmPm)
        tvDate = findViewById(R.id.tvDate)
        tvHijri = findViewById(R.id.tvHijri)
        tvNextPrayer = findViewById(R.id.tvNextPrayer)
        tvCountdown = findViewById(R.id.tvCountdown)
        tvWifi = findViewById(R.id.tvWifi)
        wifiChip = findViewById(R.id.wifiChip)
        bars = listOf(findViewById(R.id.bar1), findViewById(R.id.bar2), findViewById(R.id.bar3), findViewById(R.id.bar4))
        slotViews = listOf(
            findViewById(R.id.slotFajr), findViewById(R.id.slotDhuhr), findViewById(R.id.slotAsr),
            findViewById(R.id.slotMaghrib), findViewById(R.id.slotIsha)
        )
        listOf("Fajr", "Dhuhr", "Asr", "Maghrib", "Isha").forEachIndexed { i, n ->
            slotViews[i].findViewById<TextView>(R.id.slotName).text = n
        }
        brightnessRow = findViewById(R.id.brightnessRow)
        tvBrightness = findViewById(R.id.tvBrightness)
        brightnessFill = findViewById(R.id.brightnessFill)
        presetChips = mapOf(
            100 to findViewById(R.id.presetDay),
            70 to findViewById(R.id.presetEvening),
            35 to findViewById(R.id.presetNight),
            15 to findViewById(R.id.presetCinema)
        )
        toggleWarm = findViewById(R.id.toggleWarm)
        toggleAutoIsha = findViewById(R.id.toggleAutoIsha)
        toggleIdle = findViewById(R.id.toggleIdle)
        btnScreenOff = findViewById(R.id.btnScreenOff)
        volumeFill = findViewById(R.id.volumeFill)
        btnMute = findViewById(R.id.btnMute)
        inputsRow = findViewById(R.id.inputsRow)
        tvSleepLabel = findViewById(R.id.tvSleepLabel)
        sleepChips = mapOf(
            30 to findViewById(R.id.sleep30),
            60 to findViewById(R.id.sleep60),
            90 to findViewById(R.id.sleep90),
            0 to findViewById(R.id.sleepOff)
        )
        continueSection = findViewById(R.id.continueSection)
        tvContinueLabel = findViewById(R.id.tvContinueLabel)
        continueRow = findViewById(R.id.continueRow)
        appsRow = findViewById(R.id.appsRow)
        tvMoveHint = findViewById(R.id.tvMoveHint)
    }

    // ------------------------------------------------------------------ prefs

    private fun prefs() = getSharedPreferences("launcher", MODE_PRIVATE)

    private fun loadPrefs() {
        val p = prefs()
        brightness = p.getInt(KEY_BRIGHTNESS, 100)
        warm = p.getBoolean(KEY_WARM, false)
        autoIsha = p.getBoolean(KEY_AUTO_ISHA, false)
        idleDim = p.getBoolean(KEY_IDLE, false)
        sleepEndMillis = p.getLong(KEY_SLEEP_END, 0L)
        if (sleepEndMillis > 0L) {
            val left = sleepEndMillis - System.currentTimeMillis()
            if (left > 0) handler.postDelayed(sleepRunnable, left) else sleepEndMillis = 0L
        }
    }

    private fun savePrefs() {
        prefs().edit()
            .putInt(KEY_BRIGHTNESS, brightness)
            .putBoolean(KEY_WARM, warm)
            .putBoolean(KEY_AUTO_ISHA, autoIsha)
            .putBoolean(KEY_IDLE, idleDim)
            .putLong(KEY_SLEEP_END, sleepEndMillis)
            .apply()
    }

    // ------------------------------------------------------------------ clock / prayers / background

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
            updateBackground(now)
            checkAutoIsha(now)
            updateSleepChips()
        }
    }

    private fun hijriDate(now: Calendar): String = try {
        val uloc = android.icu.util.ULocale("en@calendar=islamic-umalqura")
        android.icu.text.SimpleDateFormat("d MMMM yyyy", uloc).format(now.time)
    } catch (e: Exception) {
        ""
    }

    private fun updatePrayers(now: Calendar) {
        val tz = now.timeZone
        val today = prayer.forDay(now)
        val names = listOf("Fajr", "Dhuhr", "Asr", "Maghrib", "Isha")
        val hours = listOf(today.fajr, today.dhuhr, today.asr, today.maghrib, today.isha)
        val times = hours.map { PrayerTimes.toCalendar(now, it, tz) }
        ishaTodayMillis = times[4].timeInMillis
        sunriseTodayMillis = PrayerTimes.toCalendar(now, today.sunrise, tz).timeInMillis

        slotViews.forEachIndexed { i, view ->
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
            nextName = "Fajr"
            nextTime = PrayerTimes.toCalendar(tomorrow, prayer.forDay(tomorrow).fajr, tz)
            nextIndex = 0
        }

        tvNextPrayer.text = "$nextName ${timeFormat.format(nextTime.time)}"
        val diffMin = ((nextTime.timeInMillis - now.timeInMillis) / 60000L).toInt().coerceAtLeast(0)
        tvCountdown.text = if (diffMin >= 60) "in ${diffMin / 60}h ${diffMin % 60}m" else "in ${diffMin}m"

        slotViews.forEachIndexed { i, view ->
            val hl = i == nextIndex
            view.findViewById<TextView>(R.id.slotTime).setTextColor(getColor(if (hl) R.color.red_soft else R.color.text_secondary))
            view.findViewById<TextView>(R.id.slotName).setTextColor(getColor(if (hl) R.color.text_primary else R.color.text_muted))
        }
    }

    /** Deep black → wine-red gradient whose intensity follows the time of day. */
    private fun updateBackground(now: Calendar) {
        val h = now.get(Calendar.HOUR_OF_DAY)
        val colors = when {
            h in 6..16 -> intArrayOf(0xFF121214.toInt(), 0xFF2A0D12.toInt(), 0xFF6E1020.toInt())
            h in 17..21 -> intArrayOf(0xFF0B0B0C.toInt(), 0xFF15090B.toInt(), 0xFF3B0A12.toInt())
            else -> intArrayOf(0xFF070708.toInt(), 0xFF0E0608.toInt(), 0xFF220710.toInt())
        }
        val g = GradientDrawable(GradientDrawable.Orientation.TL_BR, colors)
        g.gradientType = GradientDrawable.LINEAR_GRADIENT
        bgView.background = g
    }

    private fun checkAutoIsha(now: Calendar) {
        if (!autoIsha) return
        val p = prefs()
        val dayKey = now.get(Calendar.YEAR) * 1000 + now.get(Calendar.DAY_OF_YEAR)
        val t = now.timeInMillis
        if (ishaTodayMillis > 0 && t >= ishaTodayMillis && p.getInt(KEY_AUTO_NIGHT_DAY, -1) != dayKey) {
            p.edit().putInt(KEY_AUTO_NIGHT_DAY, dayKey).apply()
            setBrightness(35)
        }
        if (sunriseTodayMillis > 0 && t >= sunriseTodayMillis && t < ishaTodayMillis &&
            p.getInt(KEY_AUTO_DAY_DAY, -1) != dayKey) {
            p.edit().putInt(KEY_AUTO_DAY_DAY, dayKey).apply()
            setBrightness(100)
        }
    }

    // ------------------------------------------------------------------ display panel

    private fun setupDisplayPanel() {
        brightnessRow.setOnKeyListener { _, keyCode, event ->
            if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> { setBrightness(brightness - 5); true }
                KeyEvent.KEYCODE_DPAD_RIGHT -> { setBrightness(brightness + 5); true }
                else -> false
            }
        }
        presetChips.forEach { (value, chip) -> chip.setOnClickListener { setBrightness(value) } }
        toggleWarm.setOnClickListener { warm = !warm; savePrefs(); applyDisplay() }
        toggleAutoIsha.setOnClickListener { autoIsha = !autoIsha; savePrefs(); applyDisplay() }
        toggleIdle.setOnClickListener { idleDim = !idleDim; savePrefs(); applyDisplay(); resetIdleTimer() }
        btnScreenOff.setOnClickListener { setScreenOff(true) }
    }

    private fun setBrightness(value: Int) {
        brightness = value.coerceIn(15, 100)
        idleDimmed = false
        savePrefs()
        applyDisplay()
    }

    private fun applyDisplay() {
        tvBrightness.text = "$brightness%"
        brightnessFill.post {
            val parent = brightnessFill.parent as View
            brightnessFill.layoutParams = brightnessFill.layoutParams.apply { width = parent.width * brightness / 100 }
        }
        presetChips.forEach { (value, chip) -> chip.isSelected = value == brightness }
        toggleWarm.isSelected = warm
        toggleAutoIsha.isSelected = autoIsha
        toggleIdle.isSelected = idleDim

        val effective = if (idleDimmed) minOf(brightness, 25) else brightness
        val dim = if (screenOff) 1f else (1f - effective / 100f).coerceIn(0f, 0.9f)

        if (Settings.canDrawOverlays(this)) {
            DimService.update(this, dim, warm)
            // keep in-app layers off so they don't stack with the global ones
            dimView.alpha = 0f
            warmView.visibility = View.GONE
        } else {
            dimView.alpha = dim
            warmView.visibility = if (warm) View.VISIBLE else View.GONE
        }
        tvScreenOffHint.visibility = if (screenOff) View.VISIBLE else View.GONE
    }

    private fun setScreenOff(on: Boolean) {
        screenOff = on
        idleDimmed = false
        if (on) {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, R.string.overlay_needed, Toast.LENGTH_LONG).show()
                requestOverlayPermission()
            }
        }
        applyDisplay()
    }

    private fun requestOverlayPermission() {
        if (Settings.canDrawOverlays(this)) return
        try {
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
            // Some TVs hide this screen; in-app dimming still works.
        }
    }

    private fun resetIdleTimer() {
        handler.removeCallbacks(idleRunnable)
        if (idleDimmed) {
            idleDimmed = false
            applyDisplay()
        }
        if (idleDim && !screenOff) handler.postDelayed(idleRunnable, IDLE_MS)
    }

    // ------------------------------------------------------------------ controls panel

    private fun audio() = getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private fun setupControlsPanel() {
        findViewById<View>(R.id.btnVolDown).setOnClickListener { adjustVolume(AudioManager.ADJUST_LOWER) }
        findViewById<View>(R.id.btnVolUp).setOnClickListener { adjustVolume(AudioManager.ADJUST_RAISE) }
        btnMute.setOnClickListener {
            muted = !muted
            try {
                audio().adjustStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    if (muted) AudioManager.ADJUST_MUTE else AudioManager.ADJUST_UNMUTE,
                    AudioManager.FLAG_SHOW_UI
                )
            } catch (e: Exception) {
            }
            updateVolume()
        }
        sleepChips.forEach { (minutes, chip) -> chip.setOnClickListener { setSleep(minutes) } }
    }

    private fun adjustVolume(direction: Int) {
        try {
            audio().adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
        } catch (e: Exception) {
        }
        muted = false
        updateVolume()
    }

    private fun updateVolume() {
        val am = audio()
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val cur = am.getStreamVolume(AudioManager.STREAM_MUSIC)
        btnMute.isSelected = muted || cur == 0
        volumeFill.post {
            val parent = volumeFill.parent as View
            volumeFill.layoutParams = volumeFill.layoutParams.apply { width = parent.width * cur / max }
        }
    }

    private fun loadInputs() {
        inputsRow.removeAllViews()
        val tim = getSystemService(Context.TV_INPUT_SERVICE) as? TvInputManager ?: return
        val inputs = try { tim.tvInputList.filter { it.isPassthroughInput } } catch (e: Exception) { emptyList() }
        if (inputs.isEmpty()) {
            inputsRow.addView(makeChip("—", enabled = false))
            return
        }
        for (info in inputs.take(5)) {
            val label = try { info.loadLabel(this).toString() } catch (e: Exception) { "HDMI" }
            val chip = makeChip(label.replace("HDMI ", "HDMI").take(8))
            chip.setOnClickListener {
                try {
                    val uri = TvContract.buildChannelUriForPassthroughInput(info.id)
                    startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (e: Exception) {
                    Toast.makeText(this, R.string.cannot_open, Toast.LENGTH_SHORT).show()
                }
            }
            inputsRow.addView(chip)
        }
    }

    private fun makeChip(text: String, enabled: Boolean = true): TextView {
        val chip = TextView(this, null, 0, R.style.Chip)
        chip.text = text
        chip.setBackgroundResource(R.drawable.chip_bg)
        chip.isFocusable = enabled
        chip.isClickable = enabled
        chip.gravity = android.view.Gravity.CENTER
        chip.maxLines = 1
        chip.setTextColor(getColor(if (enabled) R.color.text_secondary else R.color.text_faint))
        chip.textSize = 11f
        val pad = dp(6)
        chip.setPadding(dp(4), pad, dp(4), pad)
        val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        lp.marginEnd = dp(6)
        chip.layoutParams = lp
        return chip
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    // ------------------------------------------------------------------ sleep timer

    private fun setSleep(minutes: Int) {
        handler.removeCallbacks(sleepRunnable)
        if (minutes <= 0) {
            sleepEndMillis = 0L
        } else {
            sleepEndMillis = System.currentTimeMillis() + minutes * 60_000L
            handler.postDelayed(sleepRunnable, minutes * 60_000L)
            ensureDeviceAdmin()
        }
        savePrefs()
        updateSleepChips()
    }

    private fun updateSleepChips() {
        val left = if (sleepEndMillis > 0) ((sleepEndMillis - System.currentTimeMillis()) / 60_000L).toInt() else -1
        sleepChips.forEach { (m, chip) -> chip.isSelected = false }
        if (left >= 0) {
            tvSleepLabel.text = "Sleep ${left + 1}m"
            val closest = listOf(30, 60, 90).minByOrNull { kotlin.math.abs(it - (left + 1)) } ?: 30
            sleepChips[closest]?.isSelected = true
        } else {
            tvSleepLabel.text = getString(R.string.sleep)
            sleepChips[0]?.isSelected = true
        }
    }

    private fun fireSleep() {
        sleepEndMillis = 0L
        savePrefs()
        updateSleepChips()
        try {
            audio().adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0)
            muted = true
        } catch (e: Exception) {
        }
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = ComponentName(this, SleepAdminReceiver::class.java)
        if (dpm.isAdminActive(admin)) {
            try {
                dpm.lockNow()
                return
            } catch (e: Exception) {
            }
        }
        setScreenOff(true)
    }

    private fun ensureDeviceAdmin() {
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = ComponentName(this, SleepAdminReceiver::class.java)
        if (dpm.isAdminActive(admin)) return
        try {
            startActivity(
                Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                    .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin)
                    .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, getString(R.string.admin_explain))
            )
        } catch (e: Exception) {
            // Without admin the timer still blacks the screen and mutes.
        }
    }

    // ------------------------------------------------------------------ wifi

    private fun updateWifi() {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.activeNetwork?.let { cm.getNetworkCapabilities(it) }
        var level = 0
        var label: String
        when {
            caps == null || !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) -> {
                label = getString(R.string.offline)
            }
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> {
                label = getString(R.string.ethernet); level = 4
            }
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
                label = getString(R.string.wifi)
                try {
                    val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                    @Suppress("DEPRECATION") val info = wm.connectionInfo
                    level = WifiManager.calculateSignalLevel(info.rssi, 5).coerceIn(0, 4)
                    val ssid = info.ssid?.trim('"') ?: ""
                    if (ssid.isNotEmpty() && ssid != "<unknown ssid>" && ssid != "0x") label = ssid
                } catch (e: Exception) {
                    level = 4
                }
            }
            else -> { label = "Online"; level = 4 }
        }
        tvWifi.text = label
        bars.forEachIndexed { i, bar -> bar.alpha = if (i < level) 1f else 0.2f }
        if (level == 0) bars.forEach { it.alpha = 0.2f }
        tvWifi.setTextColor(getColor(if (level == 0) R.color.red_soft else R.color.text_secondary))
    }

    private fun openSettings(action: String) {
        try {
            startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            try { startActivity(Intent(Settings.ACTION_SETTINGS)) } catch (e2: Exception) {
                Toast.makeText(this, R.string.cannot_open, Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ------------------------------------------------------------------ apps + ordering

    private fun loadApps() {
        val pm = packageManager
        val tvIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        val mobileIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)

        val byPackage = LinkedHashMap<String, ResolveInfo>()
        for (ri in pm.queryIntentActivities(tvIntent, 0)) byPackage[ri.activityInfo.packageName] = ri
        for (ri in pm.queryIntentActivities(mobileIntent, 0)) byPackage.putIfAbsent(ri.activityInfo.packageName, ri)
        byPackage.remove(packageName)

        val entries = ArrayList<AppEntry>()
        for ((pkg, ri) in byPackage) {
            val banner = try { ri.activityInfo.loadBanner(pm) } catch (e: Exception) { null }
            val icon = try { ri.loadIcon(pm) } catch (e: Exception) { null }
            entries.add(AppEntry(pkg, ri.loadLabel(pm).toString(), banner, icon))
        }

        val saved = prefs().getString(KEY_ORDER, "")!!.split(',').filter { it.isNotEmpty() }
        val rank = saved.withIndex().associate { it.value to it.index }
        entries.sortWith(
            compareBy<AppEntry> { rank[it.packageName] ?: Int.MAX_VALUE }
                .thenBy { it.label.lowercase(Locale.ENGLISH) }
        )

        entries.add(AppEntry("settings", getString(R.string.settings), null, getDrawable(R.drawable.ic_settings), isSettings = true))
        adapter.submit(entries)
        tvMoveHint.text = getString(R.string.move_hint)
    }

    private fun saveOrder() {
        val order = adapter.items.filter { !it.isSettings }.joinToString(",") { it.packageName }
        prefs().edit().putString(KEY_ORDER, order).apply()
    }

    private fun startMove(position: Int) {
        adapter.startMoving(position)
        tvMoveHint.text = getString(R.string.moving_hint)
        tvMoveHint.setTextColor(getColor(R.color.red_soft))
    }

    private fun finishMove() {
        adapter.stopMoving()
        saveOrder()
        tvMoveHint.text = getString(R.string.move_hint)
        tvMoveHint.setTextColor(getColor(R.color.text_faint))
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            resetIdleTimer()
            if (screenOff) {
                setScreenOff(false)
                return true
            }
            if (adapter.movingPosition >= 0) {
                when (event.keyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        val to = adapter.moveBy(if (event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT) -1 else 1)
                        if (to >= 0) {
                            appsRow.scrollToPosition(to)
                            appsRow.post { appsRow.findViewHolderForAdapterPosition(to)?.itemView?.requestFocus() }
                        }
                        return true
                    }
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_BACK -> {
                        finishMove()
                        return true
                    }
                    KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun openApp(entry: AppEntry) {
        if (adapter.movingPosition >= 0) { finishMove(); return }
        if (entry.isSettings) { openSettings(Settings.ACTION_SETTINGS); return }
        val pm = packageManager
        val intent = pm.getLeanbackLaunchIntentForPackage(entry.packageName)
            ?: pm.getLaunchIntentForPackage(entry.packageName)
        if (intent == null) {
            Toast.makeText(this, R.string.cannot_open, Toast.LENGTH_SHORT).show()
            return
        }
        rememberRecent(entry.packageName)
        try {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            Toast.makeText(this, R.string.cannot_open, Toast.LENGTH_SHORT).show()
        }
    }

    private fun rememberRecent(pkg: String) {
        val list = prefs().getString(KEY_RECENT, "")!!.split(',').filter { it.isNotEmpty() && it != pkg }.toMutableList()
        list.add(0, pkg)
        prefs().edit().putString(KEY_RECENT, list.take(8).joinToString(",")).apply()
    }

    // ------------------------------------------------------------------ continue watching

    private fun requestWatchNextPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        if (checkSelfPermission(PERM_EPG) != PackageManager.PERMISSION_GRANTED && !prefs().getBoolean(KEY_EPG_ASKED, false)) {
            prefs().edit().putBoolean(KEY_EPG_ASKED, true).apply()
            try { requestPermissions(arrayOf(PERM_EPG), 1) } catch (e: Exception) { }
        }
    }

    private fun loadContinue() {
        Thread {
            var items = ContinueAdapter.loadWatchNext(this)
            val fromWatchNext = items.isNotEmpty()
            if (!fromWatchNext) {
                val recent = prefs().getString(KEY_RECENT, "")!!.split(',').filter { it.isNotEmpty() }
                items = ContinueAdapter.loadRecent(this, recent)
            }
            runOnUiThread {
                continueAdapter.submit(items)
                continueSection.visibility = if (items.isEmpty()) View.GONE else View.VISIBLE
                tvContinueLabel.text = getString(if (fromWatchNext) R.string.continue_watching else R.string.recent_apps)
            }
        }.start()
    }

    private fun openContinue(item: ContinueItem) {
        val intent = ContinueAdapter.openIntent(this, item)
        if (intent == null) {
            Toast.makeText(this, R.string.cannot_open, Toast.LENGTH_SHORT).show()
            return
        }
        item.packageName?.let { rememberRecent(it) }
        try { startActivity(intent) } catch (e: Exception) {
            Toast.makeText(this, R.string.cannot_open, Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        private const val KEY_ORDER = "order"
        private const val KEY_RECENT = "recent"
        private const val KEY_BRIGHTNESS = "brightness"
        private const val KEY_WARM = "warm"
        private const val KEY_AUTO_ISHA = "auto_isha"
        private const val KEY_IDLE = "idle_dim"
        private const val KEY_SLEEP_END = "sleep_end"
        private const val KEY_AUTO_NIGHT_DAY = "auto_night_day"
        private const val KEY_AUTO_DAY_DAY = "auto_day_day"
        private const val KEY_EPG_ASKED = "epg_asked"
        private const val PERM_EPG = "com.android.providers.tv.permission.READ_EPG_DATA"
        private const val IDLE_MS = 5 * 60_000L
    }
}
