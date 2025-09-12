package com.example.testui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.animation.ValueAnimator
import android.view.animation.LinearInterpolator
import androidx.core.content.ContextCompat
import kotlin.math.PI
import kotlin.random.Random

class WaveformView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val barCount = 12
    private val amplitudes = FloatArray(barCount) { Random.nextFloat() * 0.5f + 0.1f }
    private val phaseShifts = FloatArray(barCount) { Random.nextFloat() * 2f * PI.toFloat() }

    private var isAnimating = false

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        strokeWidth = 6f
        isAntiAlias = true
    }

    private var currentGradientColors = intArrayOf(
        ContextCompat.getColor(context, R.color.dark_surface_lighter),
        ContextCompat.getColor(context, android.R.color.white)
    )

    // Cache the gradient to avoid recreating it on each draw
    private var cachedGradient: LinearGradient? = null
    private var lastGradientHeight = 0f

    private val animators = List(barCount) { index ->
        ValueAnimator.ofFloat(0.1f, 1f).apply {
            duration = (600 + Random.nextInt(400)).toLong()
            startDelay = index * 30L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = LinearInterpolator()
            addUpdateListener { animator ->
                if (isAnimating) {
                    amplitudes[index] = animator.animatedValue as Float
                    invalidate()
                }
            }
        }
    }

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null) // Use hardware acceleration if available
        Log.d(TAG, "WaveformView initialized")
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startAnimations()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopAnimations()
    }

    fun startAnimations() {
        if (!isAnimating) {
            isAnimating = true
            animators.forEach { animator ->
                if (!animator.isRunning) {
                    animator.start()
                }
            }
            Log.d(TAG, "Animations started")
        }
    }

    fun stopAnimations() {
        isAnimating = false
        animators.forEach { animator ->
            if (animator.isRunning) {
                animator.cancel()
            }
        }
        Log.d(TAG, "Animations stopped")
    }

    fun updateGradientColors(startColor: Int, endColor: Int) {
        currentGradientColors = intArrayOf(startColor, endColor)
        cachedGradient = null // Clear cache to force recreation
        invalidate()
        Log.d(TAG, "Gradient colors updated")
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()

        if (w <= 0 || h <= 0) {
            Log.w(TAG, "Invalid dimensions: width=$w, height=$h")
            return
        }

        val centerY = h / 2f
        val maxBarHeight = h * 0.8f
        val barWidth = w / (barCount * 1.8f)
        val barSpacing = barWidth * 0.6f
        val cornerRadius = barWidth / 3f

        // Create or reuse cached gradient
        if (cachedGradient == null || lastGradientHeight != h) {
            cachedGradient = LinearGradient(
                0f, centerY - maxBarHeight / 2,
                0f, centerY + maxBarHeight / 2,
                currentGradientColors,
                null,
                Shader.TileMode.CLAMP
            )
            lastGradientHeight = h
            paint.shader = cachedGradient
        }

        for (i in 0 until barCount) {
            val amplitude = amplitudes[i]
            val barHeight = maxBarHeight * amplitude

            val top = centerY - barHeight / 2
            val bottom = centerY + barHeight / 2
            val left = i * (barWidth + barSpacing) + barSpacing
            val right = left + barWidth

            canvas.drawRoundRect(
                left, top, right, bottom,
                cornerRadius, cornerRadius, paint
            )
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // Clear cached gradient when size changes
        cachedGradient = null
    }

    companion object {
        private const val TAG = "WaveformView"
    }
}