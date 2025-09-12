package com.example.testui

import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.core.content.ContextCompat
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.abs
import kotlin.math.min

class ModeCircleOverlayView : View {

    constructor(context: Context) : super(context) { init(context) }
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) { init(context) }
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) { init(context) }

    interface OnModeSelectedListener {
        fun onModeSelected(modeIndex: Int)
    }

    private var listener: OnModeSelectedListener? = null

    fun setOnModeSelectedListener(l: OnModeSelectedListener) {
        listener = l
    }

    private val iconIds = intArrayOf(
        R.drawable.ic_warm,
        R.drawable.ic_cozy,
        R.drawable.ic_cold,
        R.drawable.ic_light,
        R.drawable.ic_night
    )

    private var icons: Array<android.graphics.drawable.Drawable?> = arrayOfNulls(iconIds.size)
    private var circleDrawable: android.graphics.drawable.Drawable? = null

    private val numItems = iconIds.size
    private val paddingDp = 20f
    private var padding = 0f
    private var rotationAngle = 0f
    private var previousAngle = 0f
    private var iconSizePx = 0
    private var circleSizePx = 0
    private var centerX = 0f
    private var centerY = 0f
    private var radiusX = 0f
    private var radiusY = 0f
    private var position: String = "center"
    private var downX = 0f
    private var downY = 0f

    // Store final positions for each icon
    private val finalPositions = Array(numItems) { Pair(0f, 0f) }

    var radiusFactor: Float = 0f
        set(value) {
            field = value
            invalidate()
        }

    private var isDarkMode = false

    private fun init(context: Context) {
        val density = context.resources.displayMetrics.density
        val iconSizeDp = 50f // Adjust this value as needed for icon size
        iconSizePx = (iconSizeDp * density).toInt()
        val circlePaddingDp = 20f // Padding around icon for circle size
        circleSizePx = iconSizePx + (circlePaddingDp * density * 2).toInt()
        padding = paddingDp * density

        for (i in iconIds.indices) {
            icons[i] = ContextCompat.getDrawable(context, iconIds[i])
        }
        setDarkMode(isDarkMode)  // Initial setup
    }

    fun setDarkMode(dark: Boolean) {
        isDarkMode = dark
        circleDrawable = ContextCompat.getDrawable(context, if (dark) R.drawable.circle_background_dark else R.drawable.circle_gradient)
        invalidate()
    }

    fun setPosition(pos: String) {
        position = pos
        updateCenter()
        calculateFinalPositions()
        invalidate()
    }

    private fun updateCenter() {
        centerX = when (position) {
            "left" -> width / 4f
            "right" -> 3 * width / 4f
            else -> width / 2f
        }
        val baseRadiusX = min(centerX, (width - centerX).toFloat())
        val baseRadiusY = height / 2f
        radiusX = baseRadiusX * 0.6f - padding
        radiusY = baseRadiusY * 0.6f - padding
    }

    private fun calculateFinalPositions() {
        for (i in 0 until numItems) {
            val baseAngle = 2 * PI * i / numItems.toDouble()
            val angle = baseAngle + rotationAngle * PI / 180.0
            finalPositions[i] = Pair(
                centerX + (radiusX * cos(angle)).toFloat(),
                centerY + (radiusY * sin(angle)).toFloat()
            )
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        centerY = h / 2f
        updateCenter()
        calculateFinalPositions()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val tintColor = if (isDarkMode) Color.WHITE else Color.BLACK

        // Draw circular cards and modes along the ellipse
        for (i in 0 until numItems) {
            val (finalX, finalY) = finalPositions[i]

            // Calculate current position based on radiusFactor (0 to 1)
            val currentX = centerX + (finalX - centerX) * radiusFactor
            val currentY = centerY + (finalY - centerY) * radiusFactor

            // Calculate current size based on radiusFactor
            val currentCircleSize = (circleSizePx * radiusFactor).toInt()
            val currentIconSize = (iconSizePx * radiusFactor).toInt()

            // Draw circle background
            circleDrawable?.let { drawable ->
                drawable.setBounds(
                    (currentX - currentCircleSize / 2).toInt(),
                    (currentY - currentCircleSize / 2).toInt(),
                    (currentX + currentCircleSize / 2).toInt(),
                    (currentY + currentCircleSize / 2).toInt()
                )
                drawable.alpha = (255 * radiusFactor).toInt()
                drawable.draw(canvas)
            }

            // Draw icon on top
            val icon = icons[i] ?: continue
            icon.setTint(tintColor)
            icon.setBounds(
                (currentX - currentIconSize / 2).toInt(),
                (currentY - currentIconSize / 2).toInt(),
                (currentX + currentIconSize / 2).toInt(),
                (currentY + currentIconSize / 2).toInt()
            )
            icon.alpha = (255 * radiusFactor).toInt()
            icon.draw(canvas)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y
        val normX = (x - centerX) / radiusX
        val normY = (y - centerY) / radiusY
        val normDist = sqrt(normX * normX + normY * normY)
        val touchBandMin = 0.5f
        val touchBandMax = 1.5f
        val isOnRing = normDist >= touchBandMin && normDist <= touchBandMax

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (isOnRing) {
                    downX = x
                    downY = y
                    previousAngle = Math.toDegrees(atan2((y - centerY).toDouble(), (x - centerX).toDouble())).toFloat()
                    return true
                } else {
                    return false // Pass touch to underlying views
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (isOnRing) {
                    val currentAngle = Math.toDegrees(atan2((y - centerY).toDouble(), (x - centerX).toDouble())).toFloat()
                    var delta = currentAngle - previousAngle
                    if (delta > 180) delta -= 360
                    if (delta < -180) delta += 360
                    rotationAngle += delta
                    previousAngle = currentAngle

                    // Update final positions with new rotation
                    calculateFinalPositions()

                    invalidate()
                    return true
                } else {
                    return false
                }
            }
            MotionEvent.ACTION_UP -> {
                if (isOnRing) {
                    val dist = sqrt((x - downX) * (x - downX) + (y - downY) * (y - downY))
                    val slop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
                    if (dist <= slop) {
                        // Handle tap: find closest mode
                        var touchAngle = Math.toDegrees(atan2((y - centerY).toDouble(), (x - centerX).toDouble()))
                        if (touchAngle < 0) touchAngle += 360
                        val halfSlice = 360.0 / (2 * numItems)
                        for (i in 0 until numItems) {
                            var itemAngle = (360.0 * i / numItems + rotationAngle) % 360
                            if (itemAngle < 0) itemAngle += 360
                            val diff = abs(touchAngle - itemAngle)
                            if (diff < halfSlice || abs(diff - 360) < halfSlice) {
                                listener?.onModeSelected(i)
                                break
                            }
                        }
                    }
                    return true
                } else {
                    return false
                }
            }
        }
        return super.onTouchEvent(event)
    }
}