package com.jawad.tvlauncher

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.sin

/**
 * "Velvet glow": near-black velvet with a deep crimson glow rising from the
 * bottom-right corner and a faint cool highlight top-left. The glow breathes
 * very slowly (one cycle per minute). [intensity] follows the time of day.
 */
class VelvetBackgroundView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    /** 0..1 — how strong the red glow is (day = 1, night ≈ 0.45). */
    var intensity = 1f
        set(value) {
            field = value.coerceIn(0f, 1f)
            rebuild()
            invalidate()
        }

    private val base = Paint()
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glow2 = Paint(Paint.ANTI_ALIAS_FLAG)
    private val highlight = Paint(Paint.ANTI_ALIAS_FLAG)
    private var phase = 0f
    private var animator: ValueAnimator? = null

    init {
        base.color = 0xFF0A0A0B.toInt()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        animator = ValueAnimator.ofFloat(0f, (2 * Math.PI).toFloat()).apply {
            duration = 60_000L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                phase = it.animatedValue as Float
                rebuild()
                invalidate()
            }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        animator = null
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rebuild()
    }

    private fun rebuild() {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val breath = 0.92f + 0.08f * sin(phase)          // 0.84..1.0
        val k = intensity

        // main crimson glow, bottom-right
        val r1 = w * 0.95f * breath
        val c1 = argb(0.85f * k, 0x7A, 0x0F, 0x22)
        val c2 = argb(0.55f * k, 0x3A, 0x08, 0x12)
        glow.shader = RadialGradient(
            w * 1.02f, h * 1.05f, r1,
            intArrayOf(c1, c2, Color.TRANSPARENT),
            floatArrayOf(0f, 0.38f, 1f),
            Shader.TileMode.CLAMP
        )

        // secondary warm glow a bit higher, adds depth
        val r2 = w * 0.55f * (2f - breath)
        glow2.shader = RadialGradient(
            w * 0.78f, h * 0.70f, r2,
            intArrayOf(argb(0.22f * k, 0xC8, 0x1E, 0x3C), Color.TRANSPARENT),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )

        // faint cool highlight, top-left
        highlight.shader = RadialGradient(
            0f, 0f, w * 0.65f,
            intArrayOf(argb(0.055f, 0xFF, 0xFF, 0xFF), Color.TRANSPARENT),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )
    }

    private fun argb(alpha: Float, r: Int, g: Int, b: Int): Int =
        Color.argb((alpha.coerceIn(0f, 1f) * 255).toInt(), r, g, b)

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        canvas.drawRect(0f, 0f, w, h, base)
        if (glow.shader != null) {
            canvas.drawRect(0f, 0f, w, h, glow)
            canvas.drawRect(0f, 0f, w, h, glow2)
            canvas.drawRect(0f, 0f, w, h, highlight)
        }
    }
}
