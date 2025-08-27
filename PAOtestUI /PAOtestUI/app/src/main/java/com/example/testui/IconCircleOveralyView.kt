// File: IconCircleOverlayView.kt
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
import kotlin.math.max
import kotlin.math.min

class IconCircleOverlayView : View {

    constructor(context: Context) : super(context) { init(context) }
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) { init(context) }
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) { init(context) }

    interface OnIconSelectedListener {
        fun onIconSelected(iconIndex: Int)
    }

    private var listener: OnIconSelectedListener? = null

    fun setOnIconSelectedListener(l: OnIconSelectedListener) {
        listener = l
    }

    private val iconIds = intArrayOf(
        R.drawable.fan,
        R.drawable.ic_child,
        R.drawable.led,
        R.drawable.ic_map,
        R.drawable.isettings,
        R.drawable.ic_microphone  // NEW: Added voice icon (assume drawable exists)
    )

    private var icons: Array<android.graphics.drawable.Drawable?> = arrayOfNulls(iconIds.size)
    private var circleDrawable: android.graphics.drawable.Drawable? = null

    private val numItems = iconIds.size
    private val paddingDp = 20f
    private var padding = 0f
    private var rotationAngle = 0f
    private var previousAngle = 0f
    private var isDragging = false
    private var iconSizePx = 0
    private var circleSizePx = 0
    private var centerX = 0f
    private var centerY = 0f
    private var radiusX = 0f
    private var radiusY = 0f

    // Store final positions for each icon
    private val finalPositions = Array(numItems) { Pair(0f, 0f) }

    public var radiusFactor: Float = 0f
        set(value) {
            field = value
            invalidate()
        }

    private var isDarkMode = false
    private var downX = 0f
    private var downY = 0f

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

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        centerX = w / 2f
        centerY = h / 2f
        val halfCircle = circleSizePx / 2f
        val effectivePadding = max(padding, halfCircle)
        radiusX = centerX - effectivePadding
        radiusY = centerY - effectivePadding

        // Calculate final positions for each icon
        for (i in 0 until numItems) {
            val baseAngle = 2 * PI * i / numItems.toDouble()
            val angle = baseAngle + rotationAngle * PI / 180.0
            finalPositions[i] = Pair(
                centerX + (radiusX * cos(angle)).toFloat(),
                centerY + (radiusY * sin(angle)).toFloat()
            )
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val tintColor = if (isDarkMode) Color.WHITE else Color.BLACK

        // Draw circular cards and icons along the ellipse
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
        val touchBandMin = 0.8f
        val touchBandMax = 1.2f
        val isOnRing = normDist >= touchBandMin && normDist <= touchBandMax

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (isOnRing) {
                    downX = x
                    downY = y
                    previousAngle = Math.toDegrees(atan2((y - centerY).toDouble(), (x - centerX).toDouble())).toFloat()
                    isDragging = false
                    return true
                } else {
                    return false // Pass touch to underlying views
                }
            }
            MotionEvent.ACTION_MOVE -> {
                val dist = sqrt((x - downX) * (x - downX) + (y - downY) * (y - downY))
                val slop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
                if (isOnRing && dist > slop) {
                    if (!isDragging) {
                        isDragging = true
                    }
                    val currentAngle = Math.toDegrees(atan2((y - centerY).toDouble(), (x - centerX).toDouble())).toFloat()
                    var delta = currentAngle - previousAngle
                    if (delta > 180) delta -= 360
                    if (delta < -180) delta += 360
                    rotationAngle += delta
                    previousAngle = currentAngle

                    // Update final positions with new rotation
                    for (i in 0 until numItems) {
                        val baseAngle = 2 * PI * i / numItems.toDouble()
                        val angle = baseAngle + rotationAngle * PI / 180.0
                        finalPositions[i] = Pair(
                            centerX + (radiusX * cos(angle)).toFloat(),
                            centerY + (radiusY * sin(angle)).toFloat()
                        )
                    }

                    invalidate()
                    return true
                } else {
                    return isOnRing // Consume if on ring but don't drag if within slop
                }
            }
            MotionEvent.ACTION_UP -> {
                if (isOnRing) {
                    val dist = sqrt((x - downX) * (x - downX) + (y - downY) * (y - downY))
                    val slop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
                    if (dist <= slop && !isDragging) {
                        // Handle tap: find closest icon
                        var touchAngle = Math.toDegrees(atan2((y - centerY).toDouble(), (x - centerX).toDouble()))
                        if (touchAngle < 0) touchAngle += 360
                        val halfSlice = 360.0 / (2 * numItems)
                        for (i in 0 until numItems) {
                            var itemAngle = (360.0 * i / numItems + rotationAngle) % 360
                            if (itemAngle < 0) itemAngle += 360
                            val diff = abs(touchAngle - itemAngle)
                            if (diff < halfSlice || abs(diff - 360) < halfSlice) {
                                listener?.onIconSelected(i)
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

    // Helper function to animate the appearance
    fun animateAppearance(duration: Long = 500) {
        val animator = ObjectAnimator.ofFloat(this, "radiusFactor", 0f, 1f)
        animator.duration = duration
        animator.start()
    }

    // Helper function to animate the disappearance
    fun animateDisappearance(duration: Long = 500) {
        val animator = ObjectAnimator.ofFloat(this, "radiusFactor", 1f, 0f)
        animator.duration = duration
        animator.start()
    }
}