package com.jawad.tvlauncher

import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.media.audiofx.Visualizer
import android.util.AttributeSet
import android.view.View
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

/**
 * Neon sign ("JAWAD") with a mood tag and a neon equalizer underneath.
 * The equalizer follows the TV's real audio output when RECORD_AUDIO is
 * granted (android.media.audiofx.Visualizer on the output mix); otherwise
 * it dances to a smooth idle pattern.
 */
class NeonSignView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    var text = "JAWAD"
    var mood = "CHILL MODE"
        set(value) { field = value; invalidate() }

    private val neon = 0xFFFF2D55.toInt()
    private val neonSoft = 0xFFFF6B7D.toInt()
    private val textCore = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFE4E8.toInt()
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        textAlign = Paint.Align.CENTER
        letterSpacing = 0.22f
    }
    private val textGlow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = neon
        typeface = textCore.typeface
        textAlign = Paint.Align.CENTER
        letterSpacing = 0.22f
    }
    private val tagText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = neonSoft
        textAlign = Paint.Align.CENTER
        letterSpacing = 0.18f
    }
    private val tagStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = neon
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val tagGlow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = neon
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }
    private val bar = Paint(Paint.ANTI_ALIAS_FLAG)
    private val barGlow = Paint(Paint.ANTI_ALIAS_FLAG)

    private val barCount = 21
    private val levels = FloatArray(barCount)
    private val targets = FloatArray(barCount)
    private var visualizer: Visualizer? = null
    private var live = false
    private var t = 0f
    private var flicker = 1f
    private var nextFlickerAt = 0L

    private val frame = object : Runnable {
        override fun run() {
            t += 0.05f
            if (!live) idlePattern()
            for (i in 0 until barCount) levels[i] += (targets[i] - levels[i]) * 0.25f
            val now = System.currentTimeMillis()
            if (now > nextFlickerAt) {
                flicker = if (Random.nextFloat() < 0.12f) 0.55f + Random.nextFloat() * 0.3f else 1f
                nextFlickerAt = now + (80 + Random.nextInt(900))
            }
            invalidate()
            postDelayed(this, 33)
        }
    }

    private fun idlePattern() {
        for (i in 0 until barCount) {
            val x = i / (barCount - 1f)
            val v = 0.25f + 0.35f * abs(sin(t * 1.3f + x * 6f)) * (0.6f + 0.4f * sin(t * 0.7f + i)) +
                0.15f * abs(sin(t * 2.9f + i * 1.7f))
            targets[i] = v.coerceIn(0.08f, 1f)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        startVisualizer()
        post(frame)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(frame)
        stopVisualizer()
        super.onDetachedFromWindow()
    }

    private fun startVisualizer() {
        try {
            val v = Visualizer(0)
            v.captureSize = Visualizer.getCaptureSizeRange()[1].coerceAtMost(512)
            v.setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                override fun onWaveFormDataCapture(vis: Visualizer?, waveform: ByteArray?, rate: Int) {}
                override fun onFftDataCapture(vis: Visualizer?, fft: ByteArray?, rate: Int) {
                    if (fft == null) return
                    var any = false
                    val bins = fft.size / 2
                    for (i in 0 until barCount) {
                        // emphasise low/mid frequencies
                        val idx = (1 + (i.toFloat() / barCount).let { it * it } * (bins - 2)).toInt().coerceIn(1, bins - 1)
                        val re = fft[idx * 2].toInt()
                        val im = fft[idx * 2 + 1].toInt()
                        val mag = Math.hypot(re.toDouble(), im.toDouble()).toFloat() / 128f
                        val lv = (mag * (1.2f + i * 0.06f)).coerceIn(0f, 1f)
                        if (lv > 0.04f) any = true
                        targets[i] = max(0.06f, lv)
                    }
                    live = any
                }
            }, Visualizer.getMaxCaptureRate() / 2, false, true)
            v.enabled = true
            visualizer = v
        } catch (e: Throwable) {
            visualizer = null
            live = false
        }
    }

    private fun stopVisualizer() {
        try { visualizer?.enabled = false; visualizer?.release() } catch (e: Throwable) {}
        visualizer = null
        live = false
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val cx = w / 2f

        // ---- neon text
        val textSize = h * 0.34f
        textCore.textSize = textSize
        textGlow.textSize = textSize
        val baseline = textSize * 0.95f
        textGlow.maskFilter = BlurMaskFilter(textSize * 0.45f, BlurMaskFilter.Blur.NORMAL)
        textGlow.alpha = (150 * flicker).toInt()
        canvas.drawText(text, cx, baseline, textGlow)
        textGlow.maskFilter = BlurMaskFilter(textSize * 0.12f, BlurMaskFilter.Blur.NORMAL)
        textGlow.alpha = (220 * flicker).toInt()
        canvas.drawText(text, cx, baseline, textGlow)
        textCore.alpha = (255 * (0.75f + 0.25f * flicker)).toInt()
        canvas.drawText(text, cx, baseline, textCore)

        // ---- mood pill
        tagText.textSize = h * 0.10f
        val tw = tagText.measureText(mood)
        val ph = h * 0.17f
        val pw = tw + ph * 1.6f
        val top = baseline + h * 0.08f
        val rect = RectF(cx - pw / 2f, top, cx + pw / 2f, top + ph)
        tagGlow.maskFilter = BlurMaskFilter(ph * 0.35f, BlurMaskFilter.Blur.NORMAL)
        tagGlow.alpha = (110 * flicker).toInt()
        canvas.drawRoundRect(rect, ph / 2f, ph / 2f, tagGlow)
        canvas.drawRoundRect(rect, ph / 2f, ph / 2f, tagStroke)
        val ty = rect.centerY() - (tagText.descent() + tagText.ascent()) / 2f
        canvas.drawText(mood, cx, ty, tagText)

        // ---- equalizer
        val eqTop = rect.bottom + h * 0.08f
        val eqBottom = h - h * 0.02f
        val eqH = eqBottom - eqTop
        if (eqH <= 4f) return
        val totalW = w * 0.62f
        val gap = totalW / barCount * 0.42f
        val bw = (totalW - gap * (barCount - 1)) / barCount
        var x = cx - totalW / 2f
        barGlow.maskFilter = BlurMaskFilter(bw * 0.9f, BlurMaskFilter.Blur.NORMAL)
        for (i in 0 until barCount) {
            val lv = levels[i].coerceIn(0.05f, 1f)
            val bh = eqH * lv
            val r = RectF(x, eqBottom - bh, x + bw, eqBottom)
            val c = if (i % 2 == 0) neon else neonSoft
            barGlow.color = c
            barGlow.alpha = 120
            canvas.drawRoundRect(r, bw / 2f, bw / 2f, barGlow)
            bar.color = c
            canvas.drawRoundRect(r, bw / 2f, bw / 2f, bar)
            x += bw + gap
        }
    }

    companion object {
        fun moodFor(hour: Int): String = when (hour) {
            in 5..10 -> "GOOD MORNING"
            in 11..16 -> "CHILL MODE"
            in 17..22 -> "MOVIE NIGHT"
            else -> "LATE NIGHT"
        }
        val MOODS = listOf("CHILL MODE", "MOVIE NIGHT", "GAME ON", "FOCUS", "LATE NIGHT")
        private const val UNUSED = Color.BLACK
    }
}
