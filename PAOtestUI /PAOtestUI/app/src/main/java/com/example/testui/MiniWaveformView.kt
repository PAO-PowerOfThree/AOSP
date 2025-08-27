package com.example.testui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import kotlin.math.sin
import kotlin.random.Random

class MiniWaveformView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var isListening = false
    private var isDarkMode = false

    // Animation properties
    private var animationProgress = 0f
    private var waveAnimator: ValueAnimator? = null

    // Wave properties
    private val waveCount = 5
    private val baseAmplitude = 20f
    private var currentAmplitudes = FloatArray(waveCount) { baseAmplitude }
    private val waveFrequencies = floatArrayOf(0.02f, 0.025f, 0.03f, 0.035f, 0.04f)
    private val wavePhases = FloatArray(waveCount) { Random.nextFloat() * 2 * Math.PI.toFloat() }

    // Colors
    private var primaryColor = Color.BLACK
    private var secondaryColor = Color.GRAY
    private var backgroundColor = Color.WHITE

    init {
        setupPaints()
        updateColors(false)
    }

    private fun setupPaints() {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        paint.strokeCap = Paint.Cap.ROUND

        backgroundPaint.style = Paint.Style.FILL
    }

    fun updateColors(darkMode: Boolean) {
        isDarkMode = darkMode

        if (darkMode) {
            primaryColor = ContextCompat.getColor(context, R.color.white)
            secondaryColor = ContextCompat.getColor(context, R.color.hint_text)
            backgroundColor = ContextCompat.getColor(context, R.color.dark_background)
        } else {
            primaryColor = ContextCompat.getColor(context, R.color.black)
            secondaryColor = ContextCompat.getColor(context, R.color.hint_text)
            backgroundColor = ContextCompat.getColor(context, R.color.white)
        }

        invalidate()
    }

    fun startListening() {
        isListening = true
        startWaveAnimation()
    }

    fun stopListening() {
        isListening = false
        stopWaveAnimation()
    }

    private fun startWaveAnimation() {
        waveAnimator?.cancel()

        waveAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = Long.MAX_VALUE
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener { animator ->
                animationProgress = animator.animatedValue as Float
                updateWaveAmplitudes()
                invalidate()
            }
            start()
        }
    }

    private fun stopWaveAnimation() {
        waveAnimator?.cancel()

        // Animate back to baseline
        ValueAnimator.ofFloat(1f, 0f).apply {
            duration = 500
            addUpdateListener { animator ->
                val progress = animator.animatedValue as Float
                for (i in currentAmplitudes.indices) {
                    currentAmplitudes[i] = baseAmplitude * progress * 0.3f
                }
                invalidate()
            }
            start()
        }
    }

    private fun updateWaveAmplitudes() {
        if (!isListening) return

        val time = System.currentTimeMillis() * 0.001f

        for (i in currentAmplitudes.indices) {
            // Create varying amplitudes with some randomness
            val baseVariation = sin(time * waveFrequencies[i] + wavePhases[i])
            val randomVariation = (Random.nextFloat() - 0.5f) * 0.5f
            val amplitude = baseAmplitude * (1f + baseVariation * 0.8f + randomVariation)

            currentAmplitudes[i] = amplitude.coerceIn(baseAmplitude * 0.2f, baseAmplitude * 2f)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val width = width.toFloat()
        val height = height.toFloat()
        val centerY = height / 2f

        // Draw background
        backgroundPaint.color = backgroundColor
        canvas.drawRoundRect(0f, 0f, width, height, 12f, 12f, backgroundPaint)

        if (!isListening && currentAmplitudes.all { it < baseAmplitude * 0.1f }) {
            // Draw idle state - simple centered line
            paint.color = primaryColor
            paint.alpha = 100
            canvas.drawLine(width * 0.2f, centerY, width * 0.8f, centerY, paint)
            return
        }

        // Draw animated waveforms
        val waveWidth = width * 0.8f
        val startX = width * 0.1f
        val points = 100
        val stepX = waveWidth / points

        for (waveIndex in 0 until waveCount) {
            val path = Path()
            val amplitude = currentAmplitudes[waveIndex]
            val frequency = waveFrequencies[waveIndex] * 50f
            val phase = wavePhases[waveIndex] + animationProgress * 10f

            // Set color with varying alpha for depth effect
            val alpha = (255 * (1f - waveIndex * 0.15f)).toInt().coerceIn(50, 255)
            val color = if (waveIndex % 2 == 0) primaryColor else secondaryColor
            paint.color = color
            paint.alpha = alpha

            var isFirstPoint = true

            for (i in 0..points) {
                val x = startX + i * stepX
                val waveValue = sin(i * frequency + phase) * amplitude
                val y = centerY + waveValue

                if (isFirstPoint) {
                    path.moveTo(x, y)
                    isFirstPoint = false
                } else {
                    path.lineTo(x, y)
                }
            }

            canvas.drawPath(path, paint)
        }

        // Draw center indicator dot when listening
        if (isListening) {
            paint.color = primaryColor
            paint.alpha = 255
            paint.style = Paint.Style.FILL
            val dotRadius = 4f + sin(animationProgress * 20f) * 2f
            canvas.drawCircle(width / 2f, centerY, dotRadius, paint)
            paint.style = Paint.Style.STROKE
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        waveAnimator?.cancel()
    }
}

