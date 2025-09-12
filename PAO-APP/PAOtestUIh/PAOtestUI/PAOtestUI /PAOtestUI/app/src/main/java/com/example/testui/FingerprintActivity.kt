package com.example.testui

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.car.Car
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.View
import android.widget.ImageView
import androidx.activity.ComponentActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView


class FingerprintActivity : ComponentActivity() {
    companion object {
        private const val TAG = "FingerprintApp"
        private const val VENDOR_FINGERPRINT_STATUS = 0x21400107 // 558889991

        private const val STATUS_APPROVED = 0
        private const val STATUS_REFUSED = 1
    }

    private var car: Car? = null
    private var carPropertyManager: CarPropertyManager? = null

    private lateinit var playerView: PlayerView
    private lateinit var player: ExoPlayer
    private lateinit var lockIcon: ImageView

    private val propertyCallback = object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(value: CarPropertyValue<*>) {
            if (value.propertyId != VENDOR_FINGERPRINT_STATUS) return
            val status = value.value as? Int ?: return
            Log.d(TAG, "Fingerprint status changed: $status")

            if (status == STATUS_APPROVED) {
                runOnUiThread {
                    // Animate the transition
                    val fadeOut = ObjectAnimator.ofFloat(lockIcon, "alpha", 1f, 0f).apply {
                        duration = 500
                    }

                    fadeOut.addListener(object : android.animation.AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: android.animation.Animator) {
                            lockIcon.setImageResource(R.drawable.ic_unlock)
                            val fadeIn = ObjectAnimator.ofFloat(lockIcon, "alpha", 0f, 1f).apply {
                                duration = 500
                            }
                            fadeIn.start()
                        }
                    })

                    val scaleDownX = ObjectAnimator.ofFloat(lockIcon, "scaleX", 1f, 0.8f)
                    val scaleDownY = ObjectAnimator.ofFloat(lockIcon, "scaleY", 1f, 0.8f)
                    val scaleUpX = ObjectAnimator.ofFloat(lockIcon, "scaleX", 0.8f, 1f)
                    val scaleUpY = ObjectAnimator.ofFloat(lockIcon, "scaleY", 0.8f, 1f)

                    val animatorSet = AnimatorSet()
                    animatorSet.play(fadeOut).with(scaleDownX).with(scaleDownY)
                    animatorSet.play(scaleUpX).with(scaleUpY).after(fadeOut)
                    animatorSet.start()

                    // Delay for total animation time (1000ms) + extra 500ms to view
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        startActivity(Intent(this@FingerprintActivity, MainActivity::class.java))
                        finish()
                    }, 1500)
                }
            }
        }

        override fun onErrorEvent(propId: Int, zone: Int) {
            Log.e(TAG, "Error event for property: $propId, zone=$zone")
        }
    }

    private val carServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            Log.d(TAG, "Car service connected")
            car?.let {
                carPropertyManager = it.getCarManager(Car.PROPERTY_SERVICE) as? CarPropertyManager
                if (carPropertyManager == null) {
                    Log.e(TAG, "Failed to get CarPropertyManager")
                } else {
                    Log.d(TAG, "CarPropertyManager initialized")
                    setupPropertyListener()
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.d(TAG, "Car service disconnected")
            carPropertyManager = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.finger_print)

        playerView = findViewById(R.id.playerView)
        lockIcon = findViewById(R.id.lockIcon)

        // Setup ExoPlayer
        player = ExoPlayer.Builder(this).build()
        playerView.player = player

        // Put your mp4 inside res/raw/fingerprint.mp4
        val videoUri = Uri.parse("android.resource://${packageName}/${R.raw.volvomuted}")
        player.setMediaItem(MediaItem.fromUri(videoUri))
        player.repeatMode = Player.REPEAT_MODE_ONE
        player.prepare()
        player.play()

        initializeCarApi()
        hideSystemUI()
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
                VENDOR_FINGERPRINT_STATUS,
                CarPropertyManager.SENSOR_RATE_ONCHANGE
            )
            Log.d(TAG, "Property listener registered")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register property listener", e)
        }
    }

    override fun onPause() {
        super.onPause()
        player.pause()
    }

    override fun onResume() {
        super.onResume()
        player.play()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            carPropertyManager?.unregisterCallback(propertyCallback)
        } catch (e: Exception) {
            Log.w(TAG, "Error unregistering callback", e)
        }
        car?.disconnect()
        player.release()
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