package com.example.testui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.floatingactionbutton.FloatingActionButton

class VoiceActivity : AppCompatActivity() {

    private lateinit var homeButton: Button
    private lateinit var fragmentContainer: FrameLayout
    private var isDarkMode = false
    private var voskFragment: VoskDialogFragment? = null

    companion object {
        private const val PERMISSION_REQUEST_CODE = 1001
        private const val TAG = "VoiceActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        isDarkMode = intent.getBooleanExtra("isDarkMode", false)

        hideSystemUI()
        setContentView(R.layout.activity_voice)

        initializeViews()
        setupClickListeners()
        updateUI()

        // Load the voice fragment
        loadVoiceFragment()

        // Check and request permissions
        checkAndRequestPermissions()
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

    private fun initializeViews() {
        homeButton = findViewById(R.id.home_button)
        fragmentContainer = findViewById(R.id.fragment_container)
    }

    private fun setupClickListeners() {
        homeButton.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            intent.putExtra("isDarkMode", isDarkMode)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            finish()
        }
    }

    private fun updateUI() {
        val gradientRes = if (isDarkMode) R.drawable.gradient_background_dark else R.drawable.gradient_background_light
        val textColor = if (isDarkMode) Color.WHITE else Color.parseColor("#666666")

        findViewById<View>(R.id.rootLayout).setBackgroundResource(gradientRes)
        homeButton.setTextColor(textColor)

        if (isDarkMode) {
            homeButton.background = ContextCompat.getDrawable(this, R.drawable.circle_gradient)
        }
    }

    private fun loadVoiceFragment() {
        if (supportFragmentManager.findFragmentById(R.id.fragment_container) == null) {
            voskFragment = VoskDialogFragment()
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, voskFragment!!)
                .commitNow()
        } else {
            voskFragment = supportFragmentManager.findFragmentById(R.id.fragment_container) as? VoskDialogFragment
        }
    }

    private fun checkAndRequestPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                PERMISSION_REQUEST_CODE
            )
        } else {
            // Permission already granted, notify fragment
            notifyFragmentPermissionGranted()
        }
    }

    private fun notifyFragmentPermissionGranted() {
        // Post to ensure fragment is ready
        findViewById<View>(android.R.id.content).post {
            voskFragment?.onPermissionGranted()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                notifyFragmentPermissionGranted()
            } else {
                // Handle permission denied - show message or disable functionality
                Log.w(TAG, "Microphone permission denied")
            }
        }
    }

    override fun onResume() {
        super.onResume()
        hideSystemUI()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemUI()
        }
    }
}