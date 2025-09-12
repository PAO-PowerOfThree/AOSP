package com.example.testui

import android.annotation.SuppressLint
import android.car.Car
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.ComponentName
import android.content.ServiceConnection
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.constraintlayout.widget.ConstraintLayout
import com.google.android.material.floatingactionbutton.FloatingActionButton
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient

class NavigationActivity : AppCompatActivity() {
    private lateinit var web: WebView
    private var car: Car? = null
    private var carProp: CarPropertyManager? = null
    private var isWebViewReady = false
    private var isDarkMode = false
    private lateinit var rootLayout: ConstraintLayout
    private lateinit var webCard: CardView
    private lateinit var backFab: FloatingActionButton

    // Matches AIDL: VENDOR_EXTENSION_FLOAT_GPS_LOCATION_PROPERTY = 560005384 (0x21106108)
    private val VENDOR_EXTENSION_FLOAT_GPS_LOCATION_PROPERTY = 560005384

    private val callback = object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(value: CarPropertyValue<*>) {
            if (value.propertyId != VENDOR_EXTENSION_FLOAT_GPS_LOCATION_PROPERTY) {
                Log.w(TAG, "Received event for unknown property: ${value.propertyId}")
                return
            }

            val floats: DoubleArray? = when (val v = value.value) {
                is FloatArray -> v.map { it.toDouble() }.toDoubleArray()
                is Array<*> -> v.filterIsInstance<Float>().map { it.toDouble() }.toDoubleArray()
                else -> null
            }

            if (floats == null || floats.size < 2) {
                Log.e(TAG, "Invalid GPS location data format: ${value.value}")
                return
            }

            val lat = floats[0]
            val lon = floats[1]

            if (lat == 0.0 && lon == 0.0) {
                Log.w(TAG, "Skipping invalid GPS coordinates: lat=$lat, lon=$lon")
                return
            }

            pushToLeaflet(lat, lon)
            Log.d(TAG, "GPS location updated: lat=$lat, lon=$lon")
        }

        override fun onErrorEvent(propId: Int, zone: Int) {
            Log.e(TAG, "Error for prop $propId, zone $zone")
            try {
                carProp?.registerCallback(this, propId, 0.0f)
                Log.d(TAG, "Re-registered callback for prop $propId")
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to re-register callback for prop $propId", t)
            }
        }
    }

    private val carConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.d(TAG, "Connected to Car service")
            carProp = car?.getCarManager(Car.PROPERTY_SERVICE) as? CarPropertyManager
            if (carProp == null) {
                Log.e(TAG, "Failed to get CarPropertyManager")
                return
            }

            // Register callback
            try {
                carProp?.registerCallback(
                    callback,
                    VENDOR_EXTENSION_FLOAT_GPS_LOCATION_PROPERTY,
                    0.0f
                )
                Log.d(TAG, "Registered callback for GPS location")
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to register callback", t)
            }

            // Fetch initial value
            fetchInitialGpsValue()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.e(TAG, "Car service disconnected")
            carProp = null
            // Attempt to reconnect
            connectToCarService(retryCount = 0)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_navigation) // Assume layout is renamed to activity_navigation.xml

        isDarkMode = intent.getBooleanExtra("isDarkMode", false)

        rootLayout = findViewById(R.id.root_layout)
        webCard = findViewById(R.id.webCard)
        web = findViewById(R.id.web)
        backFab = findViewById(R.id.back_fab)

        setupWebView()

        // Load the modern map interface
        web.loadUrl("file:///android_asset/map.html")

        // Connect to Car service
        connectToCarService()

        hideSystemUI()

        backFab.setOnClickListener {
            finish()
        }

        updateUI()
    }

    private fun setupWebView() {
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            setGeolocationEnabled(true)
            allowFileAccess = true
            allowContentAccess = true
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = false
            displayZoomControls = false
            setSupportZoom(false)
            cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
        }

        web.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                Log.d(TAG, "WebView loaded: $url")
                isWebViewReady = true

                // Inject additional JavaScript if needed
                val initJs = """
                    javascript:(function(){
                        if (window.AndroidBridge) return;
                        window.AndroidBridge = {
                            ready: true,
                            log: function(msg) {
                                console.log('[AndroidBridge] ' + msg);
                            }
                        };
                        window.AndroidBridge.log('Android bridge initialized');
                    })();
                """
                view?.evaluateJavascript(initJs, null)

                // Apply dark mode to WebView content
                val darkJs = "javascript:setDarkMode($isDarkMode)" // Assume 'setDarkMode' function exists in map.html to toggle dark mode
                view?.evaluateJavascript(darkJs, null)
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                Log.d(TAG, "WebView started loading: $url")
                isWebViewReady = false
            }
        }

        web.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: android.webkit.ConsoleMessage?): Boolean {
                Log.d(TAG, "WebView Console: ${consoleMessage?.message()}")
                return true
            }
        }
    }

    private fun updateUI() {
        val bgColor = if (isDarkMode) Color.parseColor("#1E1E2F") else Color.parseColor("#F0F0F0")
        rootLayout.setBackgroundColor(bgColor)

        val cardBgColor = if (isDarkMode) Color.parseColor("#2A2A3A") else Color.WHITE
        webCard.setCardBackgroundColor(cardBgColor)
        web.setBackgroundColor(cardBgColor)

        val fabBgColor = if (isDarkMode) Color.WHITE else Color.BLACK
        val fabIconColor = if (isDarkMode) Color.BLACK else Color.WHITE
        backFab.backgroundTintList = ColorStateList.valueOf(fabBgColor)
        backFab.imageTintList = ColorStateList.valueOf(fabIconColor)
    }

    private fun connectToCarService(retryCount: Int = 0) {
        if (retryCount >= 3) {
            Log.e(TAG, "Failed to connect to Car service after $retryCount retries")
            return
        }
        try {
            car = Car.createCar(this, carConnection)
            car?.connect()
            Log.d(TAG, "Initiated Car service connection, attempt ${retryCount + 1}")
        } catch (t: Throwable) {
            Log.e(TAG, "Car connection failed, retrying (${retryCount + 1}/3)", t)
            web.postDelayed({ connectToCarService(retryCount + 1) }, 1000)
        }
    }

    private fun fetchInitialGpsValue() {
        carProp?.let { propMgr ->
            try {
                val value = propMgr.getProperty<FloatArray>(
                    VENDOR_EXTENSION_FLOAT_GPS_LOCATION_PROPERTY, 0
                )
                if (value != null && value.value.size >= 2) {
                    val lat = value.value[0].toDouble()
                    val lon = value.value[1].toDouble()
                    if (lat != 0.0 || lon != 0.0) {
                        pushToLeaflet(lat, lon)
                        Log.d(TAG, "Initial GPS location: lat=$lat, lon=$lon")
                    } else {
                        Log.w(TAG, "Initial GPS location invalid: lat=$lat, lon=$lon")
                    }
                } else {
                    Log.w(TAG, "Initial GPS value unavailable: $value")
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to fetch initial GPS value", t)
            }
        }
    }

    private fun pushToLeaflet(lat: Double, lon: Double) {
        if (!isWebViewReady) {
            Log.w(TAG, "WebView not ready, queueing GPS update")
            web.postDelayed({ pushToLeaflet(lat, lon) }, 500)
            return
        }

        if (lat == 0.0 && lon == 0.0) {
            Log.w(TAG, "Skipping invalid GPS coordinates: lat=$lat, lon=$lon")
            return
        }

        val js = """
            javascript:(function(){
                try {
                    if (typeof updateLocation === 'function') {
                        updateLocation($lat, $lon);
                        console.log('Location updated: $lat, $lon');
                        return 'success';
                    } else {
                        console.error('updateLocation function not available');
                        return 'error: function not found';
                    }
                } catch (e) {
                    console.error('Error updating location:', e);
                    return 'error: ' + e.message;
                }
            })();
        """.trimIndent()

        runOnUiThread {
            web.evaluateJavascript(js) { result ->
                if (result != null && result != "null") {
                    Log.d(TAG, "Leaflet updated: lat=$lat, lon=$lon, result=$result")
                } else {
                    Log.e(TAG, "Failed to update Leaflet: lat=$lat, lon=$lon")
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        web.onResume()
        hideSystemUI()
    }

    override fun onPause() {
        super.onPause()
        web.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            carProp?.unregisterCallback(callback, VENDOR_EXTENSION_FLOAT_GPS_LOCATION_PROPERTY)
            Log.d(TAG, "Unregistered callback")
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to unregister callback", t)
        }

        car?.disconnect()
        car = null
        web.destroy()
        Log.d(TAG, "Resources cleaned up")
    }

    companion object {
        private const val TAG = "ModernGpsMap"
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