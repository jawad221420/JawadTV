package com.jawad.tvlauncher

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Full-screen ringing alarm: time, rising chime, snooze and dismiss. */
class AlarmActivity : AppCompatActivity() {

    private var player: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var volume = 0.05f
    private var wakeLock: PowerManager.WakeLock? = null

    private val ramp = object : Runnable {
        override fun run() {
            volume = (volume + 0.05f).coerceAtMost(1f)
            try { player?.setVolume(volume, volume) } catch (e: Exception) {}
            if (volume < 1f) handler.postDelayed(this, 4000)
        }
    }
    private val clockTick = object : Runnable {
        override fun run() {
            findViewById<TextView>(R.id.alarmClock).text = SimpleDateFormat("h:mm", Locale.ENGLISH).format(Date())
            handler.postDelayed(this, 1000)
        }
    }
    private val autoStop = Runnable { snooze() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )
        setContentView(R.layout.activity_alarm)

        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            @Suppress("DEPRECATION")
            wakeLock = pm.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP, "jawadtv:alarm"
            ).apply { acquire(11 * 60_000L) }
        } catch (e: Exception) {
        }

        // lift any dimming / screen-off overlay and make sure audio is audible
        DimService.update(this, 0f, false)
        getSharedPreferences("launcher", MODE_PRIVATE).edit().putInt("brightness", 100).apply()
        try {
            val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0)
            val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            if (am.getStreamVolume(AudioManager.STREAM_MUSIC) < max * 0.5) {
                am.setStreamVolume(AudioManager.STREAM_MUSIC, (max * 0.6).toInt(), 0)
            }
        } catch (e: Exception) {
        }

        findViewById<TextView>(R.id.btnSnooze).setOnClickListener { snooze() }
        findViewById<TextView>(R.id.btnDismiss).setOnClickListener { dismiss() }
        findViewById<TextView>(R.id.btnDismiss).requestFocus()

        startSound()
        handler.post(clockTick)
        handler.postDelayed(autoStop, 10 * 60_000L)
    }

    private fun startSound() {
        val mp = MediaPlayer()
        try {
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            var custom = false
            if (AlarmScheduler.sound(this) == "custom") {
                val f = File("/sdcard/Download/alarm.mp3")
                if (f.exists()) {
                    mp.setDataSource(this, Uri.fromFile(f)); custom = true
                }
            }
            if (!custom) {
                val afd = resources.openRawResourceFd(R.raw.chime)
                mp.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                afd.close()
            }
            mp.isLooping = true
            mp.setVolume(volume, volume)
            mp.prepare()
            mp.start()
            player = mp
            handler.postDelayed(ramp, 4000)
        } catch (e: Exception) {
            player = null
        }
    }

    private fun stopSound() {
        handler.removeCallbacks(ramp)
        try { player?.stop(); player?.release() } catch (e: Exception) {}
        player = null
    }

    private fun snooze() {
        stopSound()
        AlarmScheduler.snooze(this, 5)
        finish()
    }

    private fun dismiss() {
        stopSound()
        AlarmScheduler.afterRing(this)
        finish()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) { snooze(); return true }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        stopSound()
        try { wakeLock?.release() } catch (e: Exception) {}
        super.onDestroy()
    }
}
