package com.example.testui

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.TimeInterpolator
import android.animation.ValueAnimator
import android.car.Car
import android.car.CarOccupantZoneManager
import android.car.hardware.property.CarPropertyManager
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.floatingactionbutton.FloatingActionButton
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.testui.R

class AmbientLightActivity : AppCompatActivity() {

    private lateinit var car: Car
    private var carPropertyManager: CarPropertyManager? = null
    private var occupantZoneManager: CarOccupantZoneManager? = null
    private val TAG = "AmbientLightActivity"
    private val LED_STRIP_CONTROL_PROPERTY = 0x26400110

    object Constants {
        const val DOOR_1_LEFT = 0x1
        const val DOOR_2_RIGHT = 0x4 // Adjust to 0x2 if ROW_1_RIGHT
    }

    private lateinit var brightnessSliderLeft: SeekBar
    private lateinit var brightnessSliderRight: SeekBar
    private lateinit var brightnessValueLeft: TextView
    private lateinit var brightnessValueRight: TextView
    private lateinit var syncButton: Button
    private lateinit var offAllButton: Button
    private lateinit var modeButtonLeft: Button
    private lateinit var modeButtonRight: Button
    private lateinit var circleOverlay: ModeCircleOverlayView
    private lateinit var carImage: ImageView
    private lateinit var controlPanel: LinearLayout
    private lateinit var homeButton: FloatingActionButton
    private lateinit var leftLight: View
    private lateinit var rightLight: View

    private var currentColorLeft: Int = Color.WHITE
    private var currentColorRight: Int = Color.WHITE
    private var currentSide: String = ""
    private var pendingShow: String? = null
    private var isCircleShown = false

    private val modes = listOf("Warm", "Cozy", "Cool", "Light", "Night")
    private val modeColors = mapOf(
        "Warm" to Color.rgb(255, 150, 0),
        "Cozy" to Color.rgb(255, 200, 100),
        "Cool" to Color.rgb(0, 100, 255),
        "Light" to Color.WHITE,
        "Night" to Color.rgb(50, 50, 100)
    )

    private val ZOOM_SCALE = 1.5f
    private var transAmount = 0f
    private var isDarkMode = false
    private val ease: TimeInterpolator = AccelerateDecelerateInterpolator()

    override fun onCreate(savedInstanceState: Bundle?) {
        hideSystemUI()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ambient_light)

        isDarkMode = intent.getBooleanExtra("isDarkMode", false)

        // Initialize UI elements
        carImage = findViewById(R.id.carImage)
        modeButtonLeft = findViewById(R.id.modeButtonLeft)
        modeButtonRight = findViewById(R.id.modeButtonRight)
        brightnessSliderLeft = findViewById(R.id.brightnessSliderLeft)
        brightnessSliderRight = findViewById(R.id.brightnessSliderRight)
        brightnessValueLeft = findViewById(R.id.brightnessValueLeft)
        brightnessValueRight = findViewById(R.id.brightnessValueRight)
        syncButton = findViewById(R.id.syncButton)
        offAllButton = findViewById(R.id.offAllButton)
        circleOverlay = findViewById(R.id.circleOverlay)
        controlPanel = findViewById(R.id.controlPanel)
        homeButton = findViewById(R.id.homeButton)
        leftLight = findViewById(R.id.left_light)
        rightLight = findViewById(R.id.right_light)

        transAmount = resources.displayMetrics.widthPixels / 4f

        carImage.scaleX = 1f
        carImage.scaleY = 1f

        // Set initial modes
        val initialMode = "Select Mode"
        modeButtonLeft.text = initialMode
        modeButtonRight.text = initialMode
        currentColorLeft = Color.WHITE
        currentColorRight = Color.WHITE
        updateSliderTint(brightnessSliderLeft, currentColorLeft)
        updateSliderTint(brightnessSliderRight, currentColorRight)
        brightnessSliderLeft.max = 100
        brightnessSliderLeft.progress = 50
        brightnessSliderRight.max = 100
        brightnessSliderRight.progress = 50
        brightnessValueLeft.text = "50%"
        brightnessValueRight.text = "50%"
        setVhalProperty(LED_STRIP_CONTROL_PROPERTY, Constants.DOOR_1_LEFT, currentColorLeft, 50)
        setVhalProperty(LED_STRIP_CONTROL_PROPERTY, Constants.DOOR_2_RIGHT, currentColorRight, 50)
        updateLight("left", currentColorLeft, 50, animateTurnOn = true)
        updateLight("right", currentColorRight, 50, animateTurnOn = true)

        // Mode button listeners
        modeButtonLeft.setOnClickListener { handleModeButtonClick("left") }
        modeButtonRight.setOnClickListener { handleModeButtonClick("right") }

        // Brightness sliders (Standard SeekBar listener)
        brightnessSliderLeft.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    brightnessValueLeft.text = "$progress%"
                    updateLight("left", currentColorLeft, progress, animateTurnOn = false)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {
                val value = seekBar.progress
                setVhalProperty(LED_STRIP_CONTROL_PROPERTY, Constants.DOOR_1_LEFT, currentColorLeft, value)
                updateLight("left", currentColorLeft, value, animateTurnOn = false)
            }
        })

        brightnessSliderRight.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    brightnessValueRight.text = "$progress%"
                    updateLight("right", currentColorRight, progress, animateTurnOn = false)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {
                val value = seekBar.progress
                setVhalProperty(LED_STRIP_CONTROL_PROPERTY, Constants.DOOR_2_RIGHT, currentColorRight, value)
                updateLight("right", currentColorRight, value, animateTurnOn = false)
            }
        })

        // Initialize Car instance
        car = Car.createCar(this)
        try {
            carPropertyManager = car.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager
            occupantZoneManager = car.getCarManager(Car.CAR_OCCUPANT_ZONE_SERVICE) as CarOccupantZoneManager
            Log.i(TAG, "CarPropertyManager initialized")
            val config = carPropertyManager?.getCarPropertyConfig(LED_STRIP_CONTROL_PROPERTY)
            Log.i(TAG, "Property config: type=${config?.propertyType}, access=${config?.access}, areaIds=${config?.areaIds?.joinToString()}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize CarPropertyManager", e)
            showToast("Car service initialization failed")
        }

        // Sync Left to Right
        syncButton.setOnClickListener {
            currentColorRight = currentColorLeft
            updateSliderTint(brightnessSliderRight, currentColorRight)
            brightnessSliderRight.progress = brightnessSliderLeft.progress
            val b = brightnessSliderRight.progress
            setVhalProperty(LED_STRIP_CONTROL_PROPERTY, Constants.DOOR_2_RIGHT, currentColorRight, b)
            updateLight("right", currentColorRight, b, animateTurnOn = true)
        }

        // Turn Off All
        offAllButton.setOnClickListener {
            brightnessSliderLeft.progress = 0
            brightnessSliderRight.progress = 0
            updateLight("left", currentColorLeft, 0, animateTurnOn = false)
            updateLight("right", currentColorRight, 0, animateTurnOn = false)
        }

        // Home button click listener - navigate to MainActivity
        homeButton.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            intent.putExtra("isDarkMode", isDarkMode)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            finish()
        }

        // Mode selected listener
        circleOverlay.setOnModeSelectedListener(object : ModeCircleOverlayView.OnModeSelectedListener {
            override fun onModeSelected(modeIndex: Int) {
                val selectedMode = modes[modeIndex]
                val color = modeColors[selectedMode]!!
                if (currentSide == "left") {
                    modeButtonLeft.text = selectedMode
                    currentColorLeft = color
                    updateSliderTint(brightnessSliderLeft, currentColorLeft)
                    val brightness = brightnessSliderLeft.progress
                    setVhalProperty(LED_STRIP_CONTROL_PROPERTY, Constants.DOOR_1_LEFT, currentColorLeft, brightness)
                    updateLight("left", currentColorLeft, brightness, animateTurnOn = false)
                } else if (currentSide == "right") {
                    modeButtonRight.text = selectedMode
                    currentColorRight = color
                    updateSliderTint(brightnessSliderRight, currentColorRight)
                    val brightness = brightnessSliderRight.progress
                    setVhalProperty(LED_STRIP_CONTROL_PROPERTY, Constants.DOOR_2_RIGHT, currentColorRight, brightness)
                    updateLight("right", currentColorRight, brightness, animateTurnOn = false)
                }
                hideModeSelector()
            }
        })

        // Hide overlay if shown when clicking car
        carImage.setOnClickListener {
            if (isCircleShown) hideModeSelector()
        }

        updateUI()
    }

    private fun updateUI() {
        circleOverlay.setDarkMode(isDarkMode)
        val gradientRes = if (isDarkMode) R.drawable.gradient_background_dark else R.drawable.gradient_background_light
        findViewById<View>(R.id.rootLayout).setBackgroundResource(gradientRes)
        carImage.setBackgroundResource(gradientRes)

        // Update FloatingActionButton appearance based on dark mode
        if (isDarkMode) {
            homeButton.backgroundTintList = ContextCompat.getColorStateList(this, R.color.gradient_background_dark)
            homeButton.imageTintList = ContextCompat.getColorStateList(this, R.color.white)
        } else {
            homeButton.backgroundTintList = ContextCompat.getColorStateList(this, R.color.gradient_background_dark)
            homeButton.imageTintList = ContextCompat.getColorStateList(this, R.color.white)
        }

        // Update mode, sync, and off buttons for dark mode
        val textColor = if (isDarkMode) Color.WHITE else Color.parseColor("#666666")
        val buttonTextColor = if (isDarkMode) Color.BLACK else textColor
        modeButtonLeft.setTextColor(buttonTextColor)
        modeButtonRight.setTextColor(buttonTextColor)
        syncButton.setTextColor(buttonTextColor)
        offAllButton.setTextColor(buttonTextColor)

        if (isDarkMode) {
            modeButtonLeft.backgroundTintList = null
            modeButtonRight.backgroundTintList = null
            syncButton.backgroundTintList = null
            offAllButton.backgroundTintList = null
            brightnessSliderLeft.backgroundTintList = null
            brightnessSliderRight.backgroundTintList = null
            modeButtonLeft.background = ContextCompat.getDrawable(this, R.drawable.circle_gradient)
            modeButtonRight.background = ContextCompat.getDrawable(this, R.drawable.circle_gradient)
            syncButton.background = ContextCompat.getDrawable(this, R.drawable.circle_gradient)
            offAllButton.background = ContextCompat.getDrawable(this, R.drawable.circle_gradient)
            brightnessSliderLeft.background = ContextCompat.getDrawable(this, R.drawable.circle_gradient)
            brightnessSliderRight.background = ContextCompat.getDrawable(this, R.drawable.circle_gradient)
        } else {
            // Reset to default (assuming no custom background set originally; adjust if needed)
            modeButtonLeft.backgroundTintList = null
            modeButtonRight.backgroundTintList = null
            syncButton.backgroundTintList = null
            offAllButton.backgroundTintList = null
            brightnessSliderLeft.backgroundTintList = null
            brightnessSliderRight.backgroundTintList = null
        }
    }

    private fun handleModeButtonClick(side: String) {
        if (isCircleShown) {
            hideModeSelector()
            if (currentSide != side) pendingShow = side
        } else {
            currentSide = side
            showModeSelector()
        }
    }

    private fun showModeSelector() {
        circleOverlay.setPosition(currentSide)
        controlPanel.visibility = View.GONE
        val transTo = if (currentSide == "left") transAmount else -transAmount
        circleOverlay.visibility = View.VISIBLE
        circleOverlay.alpha = 0f
        circleOverlay.radiusFactor = 0f

        val alphaAnim = ObjectAnimator.ofFloat(circleOverlay, "alpha", 0f, 1f)
        val radiusAnim = ObjectAnimator.ofFloat(circleOverlay, "radiusFactor", 0f, 1f)

        val carScaleX = ObjectAnimator.ofFloat(carImage, "scaleX", 1f, ZOOM_SCALE)
        val carScaleY = ObjectAnimator.ofFloat(carImage, "scaleY", 1f, ZOOM_SCALE)
        val carTransX = ObjectAnimator.ofFloat(carImage, "translationX", 0f, transTo)

        val showSet = AnimatorSet().apply {
            playTogether(alphaAnim, radiusAnim, carScaleX, carScaleY, carTransX)
            duration = 500
            interpolator = ease
        }
        showSet.start()
        isCircleShown = true
    }

    private fun hideModeSelector() {
        val transFrom = if (currentSide == "left") transAmount else -transAmount
        val alphaAnim = ObjectAnimator.ofFloat(circleOverlay, "alpha", 1f, 0f)
        val radiusAnim = ObjectAnimator.ofFloat(circleOverlay, "radiusFactor", 1f, 0f)

        val carScaleX = ObjectAnimator.ofFloat(carImage, "scaleX", ZOOM_SCALE, 1f)
        val carScaleY = ObjectAnimator.ofFloat(carImage, "scaleY", ZOOM_SCALE, 1f)
        val carTransX = ObjectAnimator.ofFloat(carImage, "translationX", transFrom, 0f)

        val hideSet = AnimatorSet().apply {
            playTogether(alphaAnim, radiusAnim, carScaleX, carScaleY, carTransX)
            duration = 500
            interpolator = ease
        }
        hideSet.addListener(object : Animator.AnimatorListener {
            override fun onAnimationStart(animation: Animator) {}
            override fun onAnimationEnd(animation: Animator) {
                circleOverlay.visibility = View.GONE
                isCircleShown = false
                if (pendingShow != null) {
                    currentSide = pendingShow!!
                    pendingShow = null
                    showModeSelector()
                } else {
                    controlPanel.visibility = View.VISIBLE
                }
            }
            override fun onAnimationCancel(animation: Animator) {}
            override fun onAnimationRepeat(animation: Animator) {}
        })
        hideSet.start()
    }

    private fun updateSliderTint(slider: SeekBar, color: Int) {
        slider.progressTintList = ColorStateList.valueOf(color)
        slider.thumbTintList = ColorStateList.valueOf(color)
    }

    private fun updateButtonTint(button: Button, color: Int) {
        button.backgroundTintList = ColorStateList.valueOf(color)
    }

    /**
     * Create a realistic multi-layer glow (inner hot core + soft halo).
     * Radius scales with brightness; alpha scales with brightness.
     */
    private fun buildGlowLayeredDrawable(color: Int, brightness: Int, centerX: Float, centerY: Float): LayerDrawable {
        val w = resources.displayMetrics.widthPixels.toFloat()

        // Alpha scales with brightness
        val alpha = (brightness * 255 / 100).coerceIn(0, 255)
        val coreColor = Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))

        // Radii scale smoothly with brightness (+ baseline so it's visible even at low %)
        val base = w / 10f
        val coreRadius = base + (w / 6f) * (brightness / 100f.toFloat())
        val midRadius  = base * 1.6f + (w / 4.5f) * (brightness / 100f.toFloat())
        val haloRadius = base * 2.3f + (w / 3.2f) * (brightness / 100f.toFloat())

        val inner = radialDrawable(
            colors = intArrayOf(coreColor, Color.TRANSPARENT),
            radius = coreRadius,
            centerX = centerX,
            centerY = centerY
        )

        val mid = radialDrawable(
            colors = intArrayOf(
                Color.argb((alpha * 0.6f).toInt(), Color.red(color), Color.green(color), Color.blue(color)),
                Color.TRANSPARENT
            ),
            radius = midRadius,
            centerX = centerX,
            centerY = centerY
        )

        val halo = radialDrawable(
            colors = intArrayOf(
                Color.argb((alpha * 0.35f).toInt(), Color.red(color), Color.green(color), Color.blue(color)),
                Color.TRANSPARENT
            ),
            radius = haloRadius,
            centerX = centerX,
            centerY = centerY
        )


        return LayerDrawable(arrayOf(halo, mid, inner))
    }

    /**
     * Realistic "turning on" update:
     * - Multi-layer radial glow with radius/alpha tied to brightness
     * - Smooth fade/scale on first turn-on when requested
     */
    private fun updateLight(side: String, color: Int, brightness: Int, animateTurnOn: Boolean) {
        val targetView = if (side == "left") leftLight else rightLight

        if (brightness == 0) {
            targetView.animate().alpha(0f).setDuration(250).withEndAction {
                targetView.background = null
            }.start()
            return
        }

        // Center of door glow (bottom-middle inside each zone feels natural)
        val centerX = 0.6f
        val centerY = 0.75f

        val layers = buildGlowLayeredDrawable(color, brightness, centerX, centerY)
        val wasOff = targetView.alpha <= 0.02f || targetView.background == null
        targetView.background = layers

        if (animateTurnOn || wasOff) {
            // Small "bloom" on power on
            targetView.scaleX = 0.9f
            targetView.scaleY = 0.9f
            targetView.alpha = 0f
            val a = ObjectAnimator.ofFloat(targetView, "alpha", 0f, 1f).apply { duration = 420; interpolator = ease }
            val sx = ObjectAnimator.ofFloat(targetView, "scaleX", 0.9f, 1f).apply { duration = 420; interpolator = ease }
            val sy = ObjectAnimator.ofFloat(targetView, "scaleY", 0.9f, 1f).apply { duration = 420; interpolator = ease }
            AnimatorSet().apply { playTogether(a, sx, sy); start() }
        } else {
            // Subtle cross-fade when changing brightness/color
            targetView.animate().alpha(1f).setDuration(180).start()
        }
    }

    // Helper to build a radial GradientDrawable cleanly
    private fun radialDrawable(
        colors: IntArray,
        radius: Float,
        centerX: Float,
        centerY: Float
    ): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setGradientType(GradientDrawable.RADIAL_GRADIENT)   // or: gradientType = GradientDrawable.RADIAL_GRADIENT
            setGradientCenter(centerX, centerY)
            setGradientRadius(radius)
            setColors(colors)                                   // uses the gradient color array
        }
    }



    private fun setVhalProperty(propertyId: Int, areaId: Int, color: Int, brightness: Int) {
        carPropertyManager?.let { manager ->
            try {
                val config = manager.getCarPropertyConfig(propertyId)
                if (config == null) {
                    Log.e(TAG, "Property $propertyId not found")
                    showToast("Property $propertyId not found")
                    return
                }
                Log.i(TAG, "Property config: type=${config.propertyType}, access=${config.access}, areaIds=${config.areaIds.joinToString()}")

                if (areaId !in config.areaIds) {
                    Log.e(TAG, "Area ID $areaId not supported for property $propertyId")
                    showToast("Area ID $areaId not supported")
                    return
                }

                val red = Color.red(color)
                val green = Color.green(color)
                val blue = Color.blue(color)

                if (red !in 0..255 || green !in 0..255 || blue !in 0..255 || brightness !in 0..100) {
                    Log.e(TAG, "Invalid LED values: R=$red, G=$green, B=$blue, Brightness=$brightness")
                    showToast("Invalid LED values")
                    return
                }

                val adjustedBrightness = (brightness * 255 / 100).coerceIn(0, 255)
                val packedColor = (adjustedBrightness shl 24) or (blue shl 16) or (green shl 8) or red
                manager.setIntProperty(propertyId, areaId, packedColor)
                Log.d(TAG, "Set VHAL property: RGB=0x${packedColor.toString(16).padStart(8, '0')} (R=$red, G=$green, B=$blue, Bright=$adjustedBrightness)")
            } catch (e: IllegalArgumentException) {
                val red = Color.red(color)
                val green = Color.green(color)
                val blue = Color.blue(color)

                Log.e(TAG, "Type mismatch setting VHAL property: ${e.message}", e)
                showToast("Type mismatch setting LED")
                try {
                    val values = intArrayOf(red, green, blue, brightness)
                    manager.setProperty(IntArray::class.java, propertyId, areaId, values)
                    Log.i(TAG, "Fallback: Successfully set LED values as IntArray: ${values.joinToString()}")
                } catch (e2: Exception) {
                    Log.e(TAG, "Fallback to IntArray failed: ${e2.message}", e2)
                    showToast("Fallback to IntArray failed")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error setting VHAL property: ${e.message}", e)
                showToast("Error setting LED")
            }
        } ?: run {
            Log.w(TAG, "CarPropertyManager not initialized")
            showToast("Car service not initialized")
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun hideSystemUI() {
        window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_FULLSCREEN
                )
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::car.isInitialized) {
            car.disconnect()
        }
    }
}