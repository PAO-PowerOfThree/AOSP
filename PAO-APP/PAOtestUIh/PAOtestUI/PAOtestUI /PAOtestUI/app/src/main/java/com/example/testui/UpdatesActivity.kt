package com.example.testui

import android.app.ProgressDialog
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID

class UpdatesActivity : AppCompatActivity() {

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

    private var progressDialog: ProgressDialog? = null
    private var isDarkMode = false
    private lateinit var rootLayout: ConstraintLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hideSystemUI()
        isDarkMode = intent.getBooleanExtra("isDarkMode", false)

        val prefs = getSharedPreferences("updates", MODE_PRIVATE)
        val pendingVersion = prefs.getString("pending_version", null)

        rootLayout = ConstraintLayout(this).apply {
            setPadding(50, 50, 50, 50)
        }

        // Add back button
        val backBtn = Button(this).apply {
            id = View.generateViewId()
            text = "Back"
            setOnClickListener {
                finish()
            }
        }
        rootLayout.addView(backBtn)
        val backParams = ConstraintLayout.LayoutParams(
            ConstraintLayout.LayoutParams.WRAP_CONTENT,
            ConstraintLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topToTop = PARENT_ID
            startToStart = PARENT_ID
        }
        backBtn.layoutParams = backParams

        if (pendingVersion == null) {
            val noUpdateText = TextView(this).apply {
                id = View.generateViewId()
                text = "No pending updates"
                textSize = 18f
            }
            rootLayout.addView(noUpdateText)
            val params = ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.WRAP_CONTENT,
                ConstraintLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topToBottom = backBtn.id
                bottomToBottom = PARENT_ID
                startToStart = PARENT_ID
                endToEnd = PARENT_ID
            }
            noUpdateText.layoutParams = params
        } else {
            val innerLinear = LinearLayout(this).apply {
                id = View.generateViewId()
                orientation = LinearLayout.VERTICAL
            }
            rootLayout.addView(innerLinear)
            val linearParams = ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.WRAP_CONTENT,
                ConstraintLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topToBottom = backBtn.id
                bottomToBottom = PARENT_ID
                startToStart = PARENT_ID
                endToEnd = PARENT_ID
            }
            innerLinear.layoutParams = linearParams

            val updateText = TextView(this).apply {
                text = "Pending Update: Version $pendingVersion"
                textSize = 18f
            }
            innerLinear.addView(updateText)

            val updateBtn = Button(this).apply {
                text = "Update Now"
                setOnClickListener {
                    AlertDialog.Builder(this@UpdatesActivity)
                        .setTitle("Confirm Update")
                        .setMessage("Install version $pendingVersion now?")
                        .setPositiveButton("Update") { _, _ ->
                            val url = prefs.getString("pending_url", "") ?: return@setPositiveButton
                            val checksum = prefs.getString("pending_checksum", "") ?: return@setPositiveButton
                            val updater = UpdateManager(
                                context = this@UpdatesActivity,
                                onShowProgress = { showProgress() },
                                onHideProgress = { hideProgress() },
                                onUpdateAvailable = { _, _, _ -> } // Not used here
                            )
                            updater.downloadAndInstall(url, checksum)
                            // Clear after starting update
                            prefs.edit().clear().apply()
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
            }
            innerLinear.addView(updateBtn)

            val cancelBtn = Button(this).apply {
                text = "Cancel Update"
                setOnClickListener {
                    AlertDialog.Builder(this@UpdatesActivity)
                        .setTitle("Cancel Pending Update")
                        .setMessage("Remove this pending update?")
                        .setPositiveButton("Yes") { _, _ ->
                            prefs.edit().clear().apply()
                            finish()
                        }
                        .setNegativeButton("No", null)
                        .show()
                }
            }
            innerLinear.addView(cancelBtn)
        }

        setContentView(rootLayout)
        updateUI()
    }

    private fun updateUI() {
        // Dark/light theme switch
        val bgColor = if (isDarkMode) Color.DKGRAY else Color.WHITE
        val textColor = if (isDarkMode) Color.WHITE else Color.BLACK
        rootLayout.setBackgroundColor(bgColor)

        // Apply to children recursively
        setTextColorRecursive(rootLayout, textColor)
    }

    private fun setTextColorRecursive(view: View, color: Int) {
        if (view is TextView) {
            view.setTextColor(color)
        } else if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                setTextColorRecursive(view.getChildAt(i), color)
            }
        }
    }

    private fun showProgress() {
        progressDialog = ProgressDialog(this).apply {
            setMessage("Updating…")
            setCancelable(false)
            show()
        }
    }

    private fun hideProgress() {
        progressDialog?.dismiss()
    }

    override fun onDestroy() {
        hideProgress()
        super.onDestroy()
    }
}