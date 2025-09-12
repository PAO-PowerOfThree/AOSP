package com.example.testui

import android.car.Car
import android.car.VehicleAreaSeat
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.graphics.Color
import android.util.Log

class VhalManager(private val context: Context) {
    private var car: Car? = null
    private var carPropertyManager: CarPropertyManager? = null
    private var isVhalAvailable = false

    companion object {
        private const val TAG = "VhalManager"

        // HVAC Properties
        private const val VENDOR_HVAC_FAN_SPEED_PROPERTY = 0x25400110 // 624951568
        private const val VENDOR_TEMP_PROPERTY = 0x25400111 // Temperature control

        // Seat definitions (corrected mapping)
        const val SEAT_DRIVER = VehicleAreaSeat.SEAT_ROW_1_LEFT      // Front left (driver)
        const val SEAT_PASSENGER = VehicleAreaSeat.SEAT_ROW_1_RIGHT  // Front right (passenger)
        const val SEAT_REAR_LEFT = VehicleAreaSeat.SEAT_ROW_2_LEFT   // Rear left
        const val SEAT_REAR_RIGHT = VehicleAreaSeat.SEAT_ROW_2_RIGHT // Rear right

        val ALL_SEATS = listOf(SEAT_DRIVER, SEAT_PASSENGER, SEAT_REAR_LEFT, SEAT_REAR_RIGHT)
        val FRONT_SEATS = listOf(SEAT_DRIVER, SEAT_PASSENGER)
        val REAR_SEATS = listOf(SEAT_REAR_LEFT, SEAT_REAR_RIGHT)

        // LED strip control properties
        private const val LED_STRIP_CONTROL_PROPERTY = 0x26400110
        const val DOOR_FRONT = 0x1  // Front doors LED area
        const val DOOR_REAR = 0x4   // Rear doors LED area

        // LED zones for more granular control
        const val LED_ZONE_DRIVER = 0x1
        const val LED_ZONE_PASSENGER = 0x2
        const val LED_ZONE_REAR_LEFT = 0x4
        const val LED_ZONE_REAR_RIGHT = 0x8
        const val LED_ZONE_ALL = LED_ZONE_DRIVER or LED_ZONE_PASSENGER or LED_ZONE_REAR_LEFT or LED_ZONE_REAR_RIGHT

        // Default values
        private const val DEFAULT_FAN_SPEED = 3
        private const val DEFAULT_BRIGHTNESS = 50
        private const val MAX_FAN_SPEED = 7
        private const val MIN_TEMP = 16
        private const val MAX_TEMP = 30
    }

    init {
        initializeVhal()
    }

    private fun initializeVhal() {
        try {
            car = Car.createCar(context)
            carPropertyManager = car?.getCarManager(Car.PROPERTY_SERVICE) as? CarPropertyManager
            isVhalAvailable = carPropertyManager != null
            Log.d(TAG, "VHAL initialized successfully: $isVhalAvailable")
        } catch (e: Exception) {
            isVhalAvailable = false
            Log.e(TAG, "Failed to initialize VHAL: ${e.message}")
        }
    }

    // HVAC Controls
    fun setAcStatusForSeat(seatArea: Int, status: Int, fanSpeed: Int = DEFAULT_FAN_SPEED) {
        if (!isVhalAvailable) {
            Log.w(TAG, "VHAL not available, cannot set AC status")
            return
        }

        try {
            carPropertyManager?.let { manager ->
                if (manager.isPropertyAvailable(VENDOR_HVAC_FAN_SPEED_PROPERTY, seatArea)) {
                    val speed = if (status == 0) 0 else fanSpeed.coerceIn(0, MAX_FAN_SPEED)
                    manager.setIntProperty(VENDOR_HVAC_FAN_SPEED_PROPERTY, seatArea, speed)
                    Log.d(TAG, "Set HVAC for ${getSeatName(seatArea)} to speed $speed (${if (status == 1) "ON" else "OFF"})")
                } else {
                    Log.e(TAG, "HVAC property not available for ${getSeatName(seatArea)}")
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied setting HVAC for ${getSeatName(seatArea)}: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting HVAC for ${getSeatName(seatArea)}: ${e.message}")
        }
    }

    fun setAcStatusForAllSeats(status: Int, fanSpeed: Int = DEFAULT_FAN_SPEED) {
        ALL_SEATS.forEach { seatArea ->
            setAcStatusForSeat(seatArea, status, fanSpeed)
        }
        Log.d(TAG, "Set HVAC for all seats to ${if (status == 1) "ON" else "OFF"}")
    }

    fun setDriverAcStatus(status: Int, fanSpeed: Int = DEFAULT_FAN_SPEED) {
        setAcStatusForSeat(SEAT_DRIVER, status, fanSpeed)
    }

    fun setPassengerAcStatus(status: Int, fanSpeed: Int = DEFAULT_FAN_SPEED) {
        setAcStatusForSeat(SEAT_PASSENGER, status, fanSpeed)
    }

    fun setRearAcStatus(status: Int, fanSpeed: Int = DEFAULT_FAN_SPEED) {
        REAR_SEATS.forEach { seatArea ->
            setAcStatusForSeat(seatArea, status, fanSpeed)
        }
        Log.d(TAG, "Set rear AC to ${if (status == 1) "ON" else "OFF"}")
    }

    fun setFrontAcStatus(status: Int, fanSpeed: Int = DEFAULT_FAN_SPEED) {
        FRONT_SEATS.forEach { seatArea ->
            setAcStatusForSeat(seatArea, status, fanSpeed)
        }
        Log.d(TAG, "Set front AC to ${if (status == 1) "ON" else "OFF"}")
    }

    // Temperature Controls
    fun setTemperatureForSeat(seatArea: Int, temperature: Int) {
        if (!isVhalAvailable) {
            Log.w(TAG, "VHAL not available, cannot set temperature")
            return
        }

        val clampedTemp = temperature.coerceIn(MIN_TEMP, MAX_TEMP)
        try {
            carPropertyManager?.let { manager ->
                if (manager.isPropertyAvailable(VENDOR_TEMP_PROPERTY, seatArea)) {
                    manager.setIntProperty(VENDOR_TEMP_PROPERTY, seatArea, clampedTemp)
                    Log.d(TAG, "Set temperature for ${getSeatName(seatArea)} to ${clampedTemp}°C")
                } else {
                    Log.w(TAG, "Temperature property not available for ${getSeatName(seatArea)}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting temperature for ${getSeatName(seatArea)}: ${e.message}")
        }
    }

    fun setTemperatureForAllSeats(temperature: Int) {
        ALL_SEATS.forEach { seatArea ->
            setTemperatureForSeat(seatArea, temperature)
        }
    }

    // LED Controls - Enhanced with Front/Rear door support
    fun setLedColor(areaId: Int, color: Int, brightness: Int = DEFAULT_BRIGHTNESS) {
        if (!isVhalAvailable) {
            Log.w(TAG, "VHAL not available, cannot set LED color")
            return
        }

        try {
            carPropertyManager?.let { manager ->
                if (manager.isPropertyAvailable(LED_STRIP_CONTROL_PROPERTY, areaId)) {
                    val packedColor = packColorWithBrightness(color, brightness)
                    manager.setIntProperty(LED_STRIP_CONTROL_PROPERTY, areaId, packedColor)

                    val areaName = when (areaId) {
                        DOOR_FRONT -> "Front doors"
                        DOOR_REAR -> "Rear doors"
                        else -> "Area $areaId"
                    }
                    Log.d(TAG, "Set LED color for $areaName: ${getColorName(color)}, brightness: $brightness%")
                } else {
                    Log.e(TAG, "LED property not available for area $areaId")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting LED color for area $areaId: ${e.message}")
        }
    }

    // Set LED color for front doors only
    fun setFrontLedColor(color: Int, brightness: Int = DEFAULT_BRIGHTNESS) {
        setLedColor(DOOR_FRONT, color, brightness)
    }

    // Set LED color for rear doors only
    fun setRearLedColor(color: Int, brightness: Int = DEFAULT_BRIGHTNESS) {
        setLedColor(DOOR_REAR, color, brightness)
    }

    // Set LED color for both front and rear doors
    fun setAllLedColors(color: Int, brightness: Int = DEFAULT_BRIGHTNESS) {
        setFrontLedColor(color, brightness)
        setRearLedColor(color, brightness)
    }

    // Set different colors for front and rear
    fun setLedColors(frontColor: Int, rearColor: Int, brightness: Int = DEFAULT_BRIGHTNESS) {
        setFrontLedColor(frontColor, brightness)
        setRearLedColor(rearColor, brightness)
    }

    // Turn off all LEDs
    fun turnOffAllLeds() {
        setAllLedColors(Color.BLACK, 0)
    }

    // Turn off front LEDs only
    fun turnOffFrontLeds() {
        setFrontLedColor(Color.BLACK, 0)
    }

    // Turn off rear LEDs only
    fun turnOffRearLeds() {
        setRearLedColor(Color.BLACK, 0)
    }

    // LED effects
    fun setAmbientLighting(color: Int, brightness: Int = 30) {
        setAllLedColors(color, brightness)
    }

    fun setWelcomeLighting() {
        // Gradually turn on front then rear with warm white
        setFrontLedColor(Color.parseColor("#FFF8DC"), 60) // Warm white
        setRearLedColor(Color.parseColor("#FFF8DC"), 40)
    }

    fun setAlertLighting(color: Int = Color.RED) {
        // Set bright red for alerts
        setAllLedColors(color, 80)
    }

    // Utility methods
    private fun packColorWithBrightness(color: Int, brightness: Int): Int {
        val red = Color.red(color)
        val green = Color.green(color)
        val blue = Color.blue(color)
        val adjustedBrightness = (brightness * 255 / 100).coerceIn(0, 255)

        return (adjustedBrightness shl 24) or (blue shl 16) or (green shl 8) or red
    }

    private fun getColorName(color: Int): String {
        return when (color) {
            Color.RED -> "Red"
            Color.GREEN -> "Green"
            Color.BLUE -> "Blue"
            Color.WHITE -> "White"
            Color.YELLOW -> "Yellow"
            Color.CYAN -> "Cyan"
            Color.MAGENTA -> "Magenta"
            Color.BLACK -> "Black/Off"
            else -> "Custom(#${Integer.toHexString(color).uppercase()})"
        }
    }

    private fun getSeatName(seatArea: Int): String {
        return when (seatArea) {
            SEAT_DRIVER -> "Driver"
            SEAT_PASSENGER -> "Passenger"
            SEAT_REAR_LEFT -> "Rear Left"
            SEAT_REAR_RIGHT -> "Rear Right"
            else -> "Unknown($seatArea)"
        }
    }

    // Status check methods
    fun isVhalReady(): Boolean = isVhalAvailable

    fun checkPropertyAvailability(propertyId: Int, areaId: Int): Boolean {
        return carPropertyManager?.isPropertyAvailable(propertyId, areaId) ?: false
    }

    // Cleanup
    fun cleanup() {
        try {
            car?.disconnect()
            isVhalAvailable = false
            Log.d(TAG, "VHAL disconnected and cleaned up")
        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup: ${e.message}")
        }
    }
}