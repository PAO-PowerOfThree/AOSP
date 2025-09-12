package com.example.testui

import android.car.Car
import android.car.CarOccupantZoneManager
import android.car.VehicleAreaSeat
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Color
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.imageview.ShapeableImageView
import me.tankery.lib.circularseekbar.CircularSeekBar
import kotlin.math.roundToInt

class HvacActivity : ComponentActivity() {
    companion object {
        private const val TAG = "FanControlApp"
        private const val VENDOR_HVAC_FAN_SPEED_PROPERTY = 0x25400110 // 624951568

        const val SEAT_ROW_1_LEFT = VehicleAreaSeat.SEAT_ROW_2_LEFT   // Adjusted to match rear left
        const val SEAT_ROW_1_RIGHT = VehicleAreaSeat.SEAT_ROW_2_RIGHT // Adjusted to match rear right
        const val SEAT_ROW_2_LEFT = VehicleAreaSeat.SEAT_ROW_1_LEFT   // Adjusted to match driver
        const val SEAT_ROW_2_RIGHT = VehicleAreaSeat.SEAT_ROW_1_RIGHT // Adjusted to match passenger

        val SEATS = listOf(SEAT_ROW_2_LEFT, SEAT_ROW_2_RIGHT, SEAT_ROW_1_LEFT, SEAT_ROW_1_RIGHT)
    }

    private var isDarkMode = false
    private lateinit var homeButton: FloatingActionButton
    private var car: Car? = null
    private var carPropertyManager: CarPropertyManager? = null
    private var carOccupantZoneManager: CarOccupantZoneManager? = null

    private val fanSpeedState = mutableMapOf<Int, Int>() // Stores UI speeds (0-5)

    private val propertyCallback = object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(value: CarPropertyValue<*>) {
            val hvacSpeed = value.value as? Int ?: return
            val uiSpeed = (hvacSpeed / 20.0).roundToInt() // Map HVAC (0-100) to UI (0-5)
            fanSpeedState[value.areaId] = uiSpeed
            Log.d(TAG, "Property changed: ${value.propertyId}, Area: ${value.areaId}, HVAC Value: $hvacSpeed, UI Value: $uiSpeed")
            updateFanSpeedUI(value.areaId, uiSpeed)
        }

        override fun onErrorEvent(propId: Int, zone: Int) {
            Log.e(TAG, "Property error: $propId, Zone: $zone")
        }
    }

    private val carServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            Log.d(TAG, "Car service connected")
            car?.let {
                carPropertyManager = it.getCarManager(Car.PROPERTY_SERVICE) as? CarPropertyManager
                carOccupantZoneManager = it.getCarManager(Car.CAR_OCCUPANT_ZONE_SERVICE) as? CarOccupantZoneManager

                if (carPropertyManager == null || carOccupantZoneManager == null) {
                    Log.e(TAG, "Failed to get CarPropertyManager or CarOccupantZoneManager")
                    showToast("Failed to connect to vehicle system")
                } else {
                    Log.d(TAG, "Car managers initialized")
                    setupPropertyListener()
                    initializeFanSpeeds()
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.d(TAG, "Car service disconnected")
            carPropertyManager = null
            carOccupantZoneManager = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hvac)
        homeButton = findViewById(R.id.home_button)
        initializeCarApi()
        setupUIListeners()
        updateUI()
        hideSystemUI()
    }

    private fun updateUI() {
        val gradientRes = if (isDarkMode) R.drawable.gradient_background_dark else R.drawable.gradient_background_light

        findViewById<View>(R.id.rootLayout).setBackgroundResource(gradientRes)

        // Update FloatingActionButton appearance based on dark mode
        if (isDarkMode) {
            homeButton.backgroundTintList = ContextCompat.getColorStateList(this, R.color.gradient_background_dark)
            homeButton.imageTintList = ContextCompat.getColorStateList(this, R.color.white)
        } else {
            homeButton.backgroundTintList = ContextCompat.getColorStateList(this, R.color.gradient_background_dark)
            homeButton.imageTintList = ContextCompat.getColorStateList(this, R.color.white)
        }
    }

    private fun initializeCarApi() {
        Log.d(TAG, "Initializing Car API")
        car = Car.createCar(this, carServiceConnection)
        car?.connect()
    }

    private fun setupPropertyListener() {
        try {
            carPropertyManager?.registerCallback(
                propertyCallback,
                VENDOR_HVAC_FAN_SPEED_PROPERTY,
                CarPropertyManager.SENSOR_RATE_ONCHANGE
            )
            Log.d(TAG, "Property listener registered")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register property listener", e)
        }
    }

    private fun initializeFanSpeeds() {
        SEATS.forEach { seat ->
            val speed = getFanSpeed(seat)
            fanSpeedState[seat] = speed
            updateFanSpeedUI(seat, speed)
        }
    }

    private fun setFanSpeed(seatArea: Int, uiSpeed: Int) {
        val hvacSpeed = uiSpeed * 20 // Map UI (0-5) to HVAC (0-100)
        Log.d(TAG, "Setting fan speed: Area=$seatArea, UI Speed=$uiSpeed, HVAC Speed=$hvacSpeed")
        try {
            carPropertyManager?.setIntProperty(VENDOR_HVAC_FAN_SPEED_PROPERTY, seatArea, hvacSpeed)
            Log.d(TAG, "Fan speed set successfully")
            showToast("Fan speed set to $uiSpeed")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set fan speed", e)
            showToast("Failed to set fan speed: ${e.message}")
        }
    }

    private fun getFanSpeed(seatArea: Int): Int {
        return try {
            val hvacSpeed = carPropertyManager?.getIntProperty(VENDOR_HVAC_FAN_SPEED_PROPERTY, seatArea) ?: 0
            (hvacSpeed / 20.0).roundToInt() // Map HVAC (0-100) to UI (0-5)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get fan speed for area $seatArea", e)
            0
        }
    }

    private fun showToast(message: String) {
        runOnUiThread {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateFanSpeedUI(seatArea: Int, uiSpeed: Int) {
        runOnUiThread {
            val (seekBar, textView, imageView) = when (seatArea) {
                SEAT_ROW_2_LEFT -> Triple(
                    findViewById<CircularSeekBar>(R.id.circularSeekBarRearLeft),
                    findViewById<TextView>(R.id.progress_value_rear_left),
                    findViewById<ShapeableImageView>(R.id.gif_imageview_Rare_left)
                )
                SEAT_ROW_2_RIGHT -> Triple(
                    findViewById<CircularSeekBar>(R.id.circularSeekBarRearRight),
                    findViewById<TextView>(R.id.progress_value_rear_right),
                    findViewById<ShapeableImageView>(R.id.gif_imageview_Rare_right)
                )
                SEAT_ROW_1_LEFT -> Triple(
                    findViewById<CircularSeekBar>(R.id.circularSeekBarDriver),
                    findViewById<TextView>(R.id.progress_value_driver),
                    findViewById<ShapeableImageView>(R.id.gif_imageview_driver)
                )
                SEAT_ROW_1_RIGHT -> Triple(
                    findViewById<CircularSeekBar>(R.id.circularSeekBarPassenger),
                    findViewById<TextView>(R.id.progress_value_passenger),
                    findViewById<ShapeableImageView>(R.id.gif_imageview_passenger)
                )
                else -> return@runOnUiThread
            }

            seekBar.progress = uiSpeed.toFloat()
            textView.text = uiSpeed.toString()

            // Control GIF visibility and loading with fade animation
            if (uiSpeed > 0) {
                if (imageView.visibility != View.VISIBLE) {
                    Glide.with(this)
                        .asGif()
                        .load(R.drawable.gif25)
                        .into(imageView)
                    imageView.visibility = View.VISIBLE
                    imageView.animate()
                        .alpha(1f)
                        .setDuration(500)
                        .start()
                }
            } else {
                imageView.animate()
                    .alpha(0f)
                    .setDuration(300)
                    .withEndAction {
                        imageView.visibility = View.GONE
                        Glide.with(this).clear(imageView)
                    }
                    .start()
            }
        }
    }

    private fun setupUIListeners() {
        // Set up SeekBar listeners to update car properties
        val seekBars = listOf(
            findViewById<CircularSeekBar>(R.id.circularSeekBarRearLeft) to SEAT_ROW_2_LEFT,
            findViewById<CircularSeekBar>(R.id.circularSeekBarRearRight) to SEAT_ROW_2_RIGHT,
            findViewById<CircularSeekBar>(R.id.circularSeekBarDriver) to SEAT_ROW_1_LEFT,
            findViewById<CircularSeekBar>(R.id.circularSeekBarPassenger) to SEAT_ROW_1_RIGHT
        )

        seekBars.forEach { (seekBar, seatArea) ->
            seekBar.setOnSeekBarChangeListener(object : CircularSeekBar.OnCircularSeekBarChangeListener {
                override fun onProgressChanged(seekBar: CircularSeekBar?, progress: Float, fromUser: Boolean) {
                    if (fromUser) {
                        val uiSpeed = progress.toInt().coerceIn(0, 5)
                        setFanSpeed(seatArea, uiSpeed)
                        fanSpeedState[seatArea] = uiSpeed
                        updateFanSpeedUI(seatArea, uiSpeed)
                    }
                }

                override fun onStopTrackingTouch(seekBar: CircularSeekBar?) {}
                override fun onStartTrackingTouch(seekBar: CircularSeekBar?) {}
            })
        }

        // Set up FloatingActionButton click listener
        homeButton.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            intent.putExtra("isDarkMode", isDarkMode)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            finish()
        }

        findViewById<MaterialButton>(R.id.btn_all_max).setOnClickListener {
            SEATS.forEach { seatArea ->
                setFanSpeed(seatArea, 5)
                fanSpeedState[seatArea] = 5
                updateFanSpeedUI(seatArea, 5)
            }
        }

        findViewById<MaterialButton>(R.id.btn_all_off).setOnClickListener {
            SEATS.forEach { seatArea ->
                setFanSpeed(seatArea, 0)
                fanSpeedState[seatArea] = 0
                updateFanSpeedUI(seatArea, 0)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            carPropertyManager?.unregisterCallback(propertyCallback)
        } catch (e: Exception) {
            Log.w(TAG, "Error unregistering callback", e)
        }
        car?.disconnect()
        Log.d(TAG, "Car API disconnected")
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
}