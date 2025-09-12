package com.example.testui

import android.Manifest
import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.ProgressDialog
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import androidx.appcompat.widget.SearchView
import android.widget.TextView
import android.widget.Toast
import android.widget.SeekBar
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton

class MainActivity : AppCompatActivity(), VoiceWidgetManager.VoiceWidgetCallback {

    private lateinit var musicCard: CardView
    private lateinit var carImage: ImageView
    private lateinit var circleOverlay: IconCircleOverlayView
    private var isCircleShown = false

    // Keep refs to pulse animators so we can stop them later
    private lateinit var pulseAnimX: ObjectAnimator
    private lateinit var pulseAnimY: ObjectAnimator
    private lateinit var glowAnim: ObjectAnimator

    private val BIG_SCALE = 1.2f
    private val DIP_FACTOR = 0.95f
    private var pulseEnabled = true

    private lateinit var rootLayout: View
    private lateinit var timeText: TextView
    private lateinit var profileIcon: ImageView
    private lateinit var darkModeToggle: ImageView
    private lateinit var wifiIcon: ImageView
    private lateinit var bluetoothIcon: ImageView
    private lateinit var batteryIcon: ImageView
    private lateinit var leftNavInner: View
    private lateinit var homeIcon: ImageView
    private lateinit var homeText: TextView
    private lateinit var phoneIcon: ImageView
    private lateinit var phoneText: TextView
    private lateinit var updatesIcon: ImageView
    private lateinit var updatesText: TextView
    private lateinit var settingsIcon: ImageView
    private lateinit var settingsText: TextView
    private lateinit var musicInner: View
    private lateinit var albumArt: ImageView
    private lateinit var songTitle: TextView
    private lateinit var artistName: TextView
    private lateinit var previousBtn: ImageButton
    private lateinit var playBtn: ImageButton
    private lateinit var nextBtn: ImageButton
    private lateinit var weatherInner: View
    private lateinit var weatherIcon: ImageView
    private lateinit var tempText: TextView
    private lateinit var conditionText: TextView
    private lateinit var fuelInner: View
    private lateinit var fuelIcon: ImageView
    private lateinit var fuelTitle: TextView
    private lateinit var fuelLevel: TextView
    private lateinit var rightCard: CardView
    private lateinit var leftCard: CardView

    // Volume Controls
    private lateinit var volumeSpeakerBtn: ImageButton
    private lateinit var progressSeekBar: SeekBar
    private var currentVolume = 70 // Default volume level (0-100)

    private lateinit var musicSearchView: SearchView
    private lateinit var voiceFab: FloatingActionButton

    // Mini Voice Widget Components
    private lateinit var currentTimeText: TextView
    private lateinit var totalDurationText: TextView
    private lateinit var miniVoiceWidget: CardView
    private lateinit var widgetTitle: TextView
    private lateinit var widgetCloseBtn: ImageButton
    private lateinit var widgetWaveform: MiniWaveformView
    private lateinit var widgetStatusText: TextView
    private var isWidgetVisible = false

    // Voice functionality
    private lateinit var voiceWidgetManager: VoiceWidgetManager
    private var hasAudioPermission = false

    // Music Service
    private lateinit var musicService: MusicService

    private var isDarkMode = false

    private var progressDialog: ProgressDialog? = null
    private lateinit var updater: UpdateManager
    private lateinit var updateNotify: ImageView
    private val pulseAnims: MutableMap<View, AnimatorSet> = mutableMapOf()
    private lateinit var prefs: SharedPreferences

    // Voice command monitoring
    private var lastVoiceCommandTimestamp = 0L

    companion object {
        private const val AUDIO_PERMISSION_REQUEST_CODE = 1001
        private const val MIN_VOLUME = 0
        private const val MAX_VOLUME = 100
        private const val VOLUME_STEP = 5
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        isDarkMode = intent.getBooleanExtra("isDarkMode", false)
        if (savedInstanceState != null) {
            isDarkMode = savedInstanceState.getBoolean("isDarkMode", isDarkMode)
            currentVolume = savedInstanceState.getInt("currentVolume", currentVolume)
        }

        prefs = getSharedPreferences("updates", MODE_PRIVATE)

        // Hide system UI for full screen experience
        hideSystemUI()

        initializeViews()
        initializeMusicService()
        initializeVoiceManager()
        checkAudioPermission()
        startCarAttentionAnimation()
        setupClickListeners()
        updateUI()

        // Start monitoring voice commands
        startVoiceCommandMonitoring()

        updater = UpdateManager(
            context = this,
            onShowProgress = { showProgress() },
            onHideProgress = { hideProgress() },
            onUpdateAvailable = { latest, url, checksum ->
                val pending = prefs.getString("pending_version", null)
                if (pending == latest) {
                    return@UpdateManager
                }
                updateNotify.visibility = View.VISIBLE
                startPulse(updateNotify)
                updateNotify.setOnClickListener {
                    AlertDialog.Builder(this)
                        .setTitle("Update Available")
                        .setMessage("Version $latest available. Update now?")
                        .setPositiveButton("Yes") { _, _ ->
                            stopPulse(updateNotify)
                            updateNotify.visibility = View.GONE
                            updater.downloadAndInstall(url, checksum)
                        }
                        .setNegativeButton("Later") { _, _ ->
                            stopPulse(updateNotify)
                            updateNotify.visibility = View.GONE
                            prefs.edit()
                                .putString("pending_version", latest)
                                .putString("pending_url", url)
                                .putString("pending_checksum", checksum)
                                .apply()
                        }
                        .show()
                }
            }
        )

        updater.checkForUpdatesPeriodically()
    }

    private fun initializeMusicService() {
        musicService = MusicService(this)

        musicService.currentTrack.observe(this) { track ->
            track?.let {
                musicService.updateUI(albumArt, songTitle, artistName)
                // Set SeekBar max to song duration (in ms)
                progressSeekBar.max = it.duration.toInt()
                progressSeekBar.progress = 0  // Reset progress
                // Set total duration display
                totalDurationText.text = it.duration.formatDuration()
            }
        }

        musicService.currentPosition.observe(this) { position ->
            // Update SeekBar progress in real-time
            progressSeekBar.progress = position
            // Update current time display
            currentTimeText.text = position.toLong().formatDuration()
        }

        musicService.isPlaying.observe(this) { isPlaying ->
            if (isPlaying) {
                playBtn.setImageResource(android.R.drawable.ic_media_pause)
            } else {
                playBtn.setImageResource(android.R.drawable.ic_media_play)
            }
        }

        musicService.isLoading.observe(this) { isLoading ->
            if (isLoading) {
                showToast("Loading music...")
            }
        }
    }

    private fun initializeVoiceManager() {
        voiceWidgetManager = VoiceWidgetManager(this, this, this)
    }

    private fun startVoiceCommandMonitoring() {
        // Monitor for voice commands via SharedPreferences
        val thread = Thread {
            while (!isFinishing) {
                try {
                    val voicePrefs = getSharedPreferences(SharedState.PREFS_NAME, MODE_PRIVATE)
                    val command = voicePrefs.getString("voice_music_command", null)
                    val timestamp = voicePrefs.getLong("voice_command_timestamp", 0L)

                    if (command != null && timestamp > lastVoiceCommandTimestamp) {
                        lastVoiceCommandTimestamp = timestamp

                        runOnUiThread {
                            handleVoiceCommand(command)
                        }

                        // Clear the command
                        voicePrefs.edit()
                            .remove("voice_music_command")
                            .remove("voice_command_timestamp")
                            .apply()
                    }

                    Thread.sleep(500) // Check every 500ms
                } catch (e: Exception) {
                    android.util.Log.e("MainActivity", "Voice command monitoring error: ${e.message}")
                }
            }
        }
        thread.isDaemon = true
        thread.start()
    }

    private fun handleVoiceCommand(command: String) {
        android.util.Log.d("MainActivity", "Received voice command: $command")

        when {
            command == "PLAY" -> {
                musicService.play()
                showToast("Music started")
            }
            command == "PAUSE" -> {
                musicService.pause()
                showToast("Music paused")
            }
            command == "PLAY_PAUSE" -> {
                musicService.playPause()
                showToast(if (musicService.isPlaying.value == true) "Music paused" else "Music started")
            }
            command == "STOP" -> {
                musicService.pause()
                showToast("Music stopped")
            }
            command == "NEXT" -> {
                musicService.playNext()
                showToast("Next track")
            }
            command == "PREVIOUS" -> {
                musicService.playPrevious()
                showToast("Previous track")
            }
            command == "VOLUME_UP" -> {
                val newVolume = (currentVolume + VOLUME_STEP).coerceAtMost(MAX_VOLUME)
                setMusicVolume(newVolume)
                showToast("Volume: $newVolume%")
            }
            command == "VOLUME_DOWN" -> {
                val newVolume = (currentVolume - VOLUME_STEP).coerceAtLeast(MIN_VOLUME)
                setMusicVolume(newVolume)
                showToast("Volume: $newVolume%")
            }
            command == "MUTE" -> {
                setMusicVolume(0)
                showToast("Muted")
            }
            command.startsWith("PLAY_GENRE:") -> {
                val genre = command.removePrefix("PLAY_GENRE:")
                playMusicByGenre(genre)
            }
            command == "PLAY_POPULAR" -> {
                musicService.getPopularTracks { tracks ->
                    if (tracks.isNotEmpty()) {
                        showToast("Playing popular tracks")
                    } else {
                        showToast("No popular tracks found")
                    }
                }
            }
            command.startsWith("SEARCH:") -> {
                val query = command.removePrefix("SEARCH:")
                musicService.searchTracks(query) { tracks ->
                    if (tracks.isNotEmpty()) {
                        showToast("Found ${tracks.size} tracks for '$query'")
                    } else {
                        showToast("No tracks found for '$query'")
                    }
                }
            }
            else -> {
                android.util.Log.w("MainActivity", "Unknown voice command: $command")
            }
        }
    }

    private fun playMusicByGenre(genre: String) {
        musicService.searchByGenre(genre) { tracks ->
            if (tracks.isNotEmpty()) {
                showToast("Playing $genre music (${tracks.size} tracks)")
            } else {
                showToast("No $genre tracks found")
            }
        }
    }

    private fun setMusicVolume(volume: Int) {
        currentVolume = volume.coerceIn(MIN_VOLUME, MAX_VOLUME)
        val volumeLevel = currentVolume / 100f
        musicService.setVolume(volumeLevel)
        saveVolumePreference()
    }

    private fun checkAudioPermission() {
        hasAudioPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasAudioPermission) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                AUDIO_PERMISSION_REQUEST_CODE
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == AUDIO_PERMISSION_REQUEST_CODE) {
            hasAudioPermission = grantResults.isNotEmpty() &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED

            if (hasAudioPermission) {
                val fragment = supportFragmentManager.findFragmentById(R.id.fragment_container)
                if (fragment is VoskDialogFragment) {
                    fragment.onPermissionGranted()
                }
            } else {
                showToast("Microphone permission required for voice features")
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent?.let {
            isDarkMode = it.getBooleanExtra("isDarkMode", isDarkMode)
            updateUI()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("isDarkMode", isDarkMode)
        outState.putInt("currentVolume", currentVolume)
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
        rootLayout = findViewById(R.id.rootLayout)
        musicCard = findViewById(R.id.musicCard)
        carImage = findViewById(R.id.carImage)
        circleOverlay = findViewById(R.id.circleOverlay)
        timeText = findViewById(R.id.timeText)
        profileIcon = findViewById(R.id.profileIcon)
        darkModeToggle = findViewById(R.id.darkModeToggle)
        wifiIcon = findViewById(R.id.wifiIcon)
        bluetoothIcon = findViewById(R.id.bluetoothIcon)
        batteryIcon = findViewById(R.id.batteryIcon)
        leftNavInner = findViewById(R.id.leftNavInner)
        homeIcon = findViewById(R.id.homeIcon)
        homeText = findViewById(R.id.homeText)
        phoneIcon = findViewById(R.id.phoneIcon)
        phoneText = findViewById(R.id.phoneText)
        updatesIcon = findViewById(R.id.updatesIcon)
        updatesText = findViewById(R.id.updatesText)
        settingsIcon = findViewById(R.id.settingsIcon)
        settingsText = findViewById(R.id.settingsText)
        musicInner = findViewById(R.id.musicInner)
        albumArt = findViewById(R.id.albumArt)
        songTitle = findViewById(R.id.songTitle)
        artistName = findViewById(R.id.artistName)
        previousBtn = findViewById(R.id.previousBtn)
        playBtn = findViewById(R.id.playBtn)
        nextBtn = findViewById(R.id.nextBtn)
        rightCard = findViewById(R.id.rightCard)
        weatherInner = findViewById(R.id.weatherInner)
        weatherIcon = findViewById(R.id.weatherIcon)
        tempText = findViewById(R.id.tempText)
        conditionText = findViewById(R.id.conditionText)
        leftCard = findViewById(R.id.leftCard)
        fuelInner = findViewById(R.id.fuelInner)
        fuelIcon = findViewById(R.id.fuelIcon)
        fuelTitle = findViewById(R.id.fuelTitle)
        fuelLevel = findViewById(R.id.fuelLevel)
        updateNotify = findViewById(R.id.updateNotify)
        updateNotify.visibility = View.GONE

        // Volume Controls
        volumeSpeakerBtn = findViewById(R.id.volumeSpeakerBtn)
        // Song's duration bar
        progressSeekBar = findViewById(R.id.progressSeekBar)

        musicSearchView = findViewById(R.id.musicSearchView)

        voiceFab = findViewById(R.id.voice_fab)

        // Initialize Mini Voice Widget Components
        currentTimeText = findViewById(R.id.currentTimeText)
        totalDurationText = findViewById(R.id.totalDurationText)
        miniVoiceWidget = findViewById(R.id.mini_voice_widget)
        widgetTitle = findViewById(R.id.widget_title)
        widgetCloseBtn = findViewById(R.id.widget_close_btn)
        widgetWaveform = findViewById(R.id.widget_waveform)
        widgetStatusText = findViewById(R.id.widget_status_text)

        // Set initial big scale
        carImage.scaleX = BIG_SCALE
        carImage.scaleY = BIG_SCALE

        circleOverlay.setOnIconSelectedListener(object : IconCircleOverlayView.OnIconSelectedListener {
            override fun onIconSelected(iconIndex: Int) {
                when (iconIndex) {
                    0 -> { // Fan icon
                        val intent = Intent(this@MainActivity, HvacActivity::class.java)
                        intent.putExtra("isDarkMode", isDarkMode)
                        startActivity(intent)
                    }
                    1 -> { // Child icon
                        val launchIntent =
                            packageManager.getLaunchIntentForPackage("com.patrick.parentlauncher")
                        if (launchIntent != null) {
                            launchIntent.putExtra("isDarkMode", isDarkMode)
                            startActivity(launchIntent)
                        } else {
                            showToast("Parent Launcher app not found")
                        }
                    }
                    2 -> { // LED icon
                        val intent = Intent(this@MainActivity, AmbientLightActivity::class.java)
                        intent.putExtra("isDarkMode", isDarkMode)
                        startActivity(intent)
                    }
                    3 -> { // Map icon
                        val intent = Intent(this@MainActivity, NavigationActivity::class.java)
                        intent.putExtra("isDarkMode", isDarkMode)
                        startActivity(intent)
                    }
                    5 -> { // Voice icon
                        if (hasAudioPermission) {
                            val fragment = VoskDialogFragment()
                            supportFragmentManager.beginTransaction()
                                .add(R.id.frag_container, fragment)
                                .addToBackStack(null)
                                .commit()

                            supportFragmentManager.executePendingTransactions()
                            fragment.onPermissionGranted()
                        } else {
                            checkAudioPermission()
                            showToast("Microphone permission required")
                        }
                    }
                }
            }
        })
    }

    private fun setupClickListeners() {
        // Voice FAB click listener
        voiceFab.setOnClickListener {
            showMiniVoiceWidget()
        }

        // Widget close button
        widgetCloseBtn.setOnClickListener {
            hideMiniVoiceWidget()
        }

        // Widget waveform click to toggle listening
        widgetWaveform.setOnClickListener {
            toggleVoiceListening()
        }

        // Widget container click to toggle listening
        miniVoiceWidget.setOnClickListener {
            toggleVoiceListening()
        }

        // Music control listeners
        playBtn.setOnClickListener {
            musicService.playPause()
        }

        previousBtn.setOnClickListener {
            musicService.playPrevious()
        }

        nextBtn.setOnClickListener {
            musicService.playNext()
        }

        // Existing listeners for music, car, etc.
        musicCard.setOnClickListener { handleMusicClick() }
        carImage.setOnClickListener { handleCarClick() }

        // Dark mode toggle
        darkModeToggle.setOnClickListener {
            isDarkMode = !isDarkMode
            updateUI()
        }

        volumeSpeakerBtn.setOnClickListener {
            showVolumeDialog()
        }

        progressSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    musicService.seekTo(progress)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        musicSearchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                query?.let {
                    if (it.isNotBlank()) {
                        musicService.searchTracks(it) { tracks ->
                            if (tracks.isNotEmpty()) {
                                showToast("Found ${tracks.size} tracks for '$it'")
                            } else {
                                showToast("No tracks found for '$it'")
                            }
                        }
                    }
                }
                return true
            }
            override fun onQueryTextChange(newText: String?): Boolean = false
        })

        homeIcon.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(intent)
        }
        phoneIcon.setOnClickListener {
            val intent = Intent(Intent.ACTION_DIAL)
            startActivity(intent)
        }
        settingsIcon.setOnClickListener {
            val intent = Intent(Settings.ACTION_SETTINGS)
            startActivity(intent)
        }
        profileIcon.setOnClickListener {
            val intent = Intent("android.settings.USER_SETTINGS")
            intent.setPackage("com.android.settings")
            startActivity(intent)
        }
        updatesIcon.setOnClickListener {
            val intent = Intent(this, UpdatesActivity::class.java)
            intent.putExtra("isDarkMode", isDarkMode)
            startActivity(intent)
        }
    }

    private fun showVolumeDialog() {
        val dialogView = layoutInflater.inflate(R.layout.volume_dialog, null)
        val volumeSeekBarDialog: SeekBar = dialogView.findViewById(R.id.volumeSeekBarDialog)

        // Set initial progress
        volumeSeekBarDialog.max = MAX_VOLUME
        volumeSeekBarDialog.progress = (musicService.getCurrentVolume() * 100).toInt()

        // Listener for volume change
        volumeSeekBarDialog.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val volume = progress / 100f
                    musicService.setVolume(volume)
                    currentVolume = progress
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        AlertDialog.Builder(this)
            .setTitle("Volume Control")
            .setView(dialogView)
            .setPositiveButton("OK") { _, _ ->
                saveVolumePreference()
            }
            .show()
    }

    private fun updateMusicVolume() {
        val volumeLevel = currentVolume / 100f
        musicService.setVolume(volumeLevel)
    }

    private fun saveVolumePreference() {
        val volumePrefs = getSharedPreferences("music_settings", MODE_PRIVATE)
        volumePrefs.edit().putInt("volume_level", currentVolume).apply()
    }

    private fun showMiniVoiceWidget() {
        if (isWidgetVisible) return

        isWidgetVisible = true
        miniVoiceWidget.visibility = View.VISIBLE

        miniVoiceWidget.alpha = 0f
        miniVoiceWidget.scaleX = 0.8f
        miniVoiceWidget.scaleY = 0.8f

        val fadeIn = ObjectAnimator.ofFloat(miniVoiceWidget, "alpha", 0f, 1f)
        val scaleXIn = ObjectAnimator.ofFloat(miniVoiceWidget, "scaleX", 0.8f, 1f)
        val scaleYIn = ObjectAnimator.ofFloat(miniVoiceWidget, "scaleY", 0.8f, 1f)

        val animatorSet = AnimatorSet().apply {
            playTogether(fadeIn, scaleXIn, scaleYIn)
            duration = 300
        }
        animatorSet.start()

        voiceFab.setImageResource(R.drawable.ic_mic_on)
    }

    private fun hideMiniVoiceWidget() {
        if (!isWidgetVisible) return

        if (voiceWidgetManager.isCurrentlyListening()) {
            voiceWidgetManager.stopListening()
        }

        val fadeOut = ObjectAnimator.ofFloat(miniVoiceWidget, "alpha", 1f, 0f)
        val scaleXOut = ObjectAnimator.ofFloat(miniVoiceWidget, "scaleX", 1f, 0.8f)
        val scaleYOut = ObjectAnimator.ofFloat(miniVoiceWidget, "scaleY", 1f, 0.8f)

        val animatorSet = AnimatorSet().apply {
            playTogether(fadeOut, scaleXOut, scaleYOut)
            duration = 300
        }

        animatorSet.addListener(object : Animator.AnimatorListener {
            override fun onAnimationStart(animation: Animator) {}
            override fun onAnimationEnd(animation: Animator) {
                miniVoiceWidget.visibility = View.GONE
                isWidgetVisible = false
            }
            override fun onAnimationCancel(animation: Animator) {}
            override fun onAnimationRepeat(animation: Animator) {}
        })

        animatorSet.start()
        voiceFab.setImageResource(R.drawable.ic_microphone)
    }

    private fun toggleVoiceListening() {
        if (!hasAudioPermission) {
            showToast("Microphone permission required")
            checkAudioPermission()
            return
        }

        voiceWidgetManager.toggleListening()
    }

    // VoiceWidgetCallback implementation
    override fun onStatusUpdate(status: String) {
        runOnUiThread {
            widgetStatusText.text = status
        }
    }

    override fun onListeningStateChanged(isListening: Boolean) {
        runOnUiThread {
            if (isListening) {
                widgetWaveform.startListening()
            } else {
                widgetWaveform.stopListening()
            }
        }
    }

    override fun onRecognizedText(text: String) {
        runOnUiThread {
            showToast("Recognized: $text")
        }
    }

    override fun onError(error: String) {
        runOnUiThread {
            showToast("Voice Error: $error")
            widgetStatusText.text = "Error: $error"
        }
    }

    private fun updateUI() {
        val primaryColor = if (isDarkMode) Color.WHITE else Color.BLACK
        val secondaryColor = if (isDarkMode) Color.parseColor("#BBBBBB") else Color.parseColor("#666666")
        val cardBgColor = if (isDarkMode) Color.parseColor("#1E1E2F") else Color.parseColor("#F0F0F0")
        val gradientRes = if (isDarkMode) R.drawable.gradient_background_dark else R.drawable.gradient_background_light
        val circleBgRes = if (isDarkMode) R.drawable.circle_background_dark else R.drawable.circle_background
        val toggleIcon = if (isDarkMode) R.drawable.ic_dark_mode else R.drawable.ic_dark_mode

        // Backgrounds
        rootLayout.setBackgroundResource(gradientRes)
        leftNavInner.setBackgroundResource(gradientRes)
        musicInner.setBackgroundResource(gradientRes)
        weatherInner.setBackgroundResource(gradientRes)
        fuelInner.setBackgroundResource(gradientRes)
        profileIcon.setBackgroundResource(circleBgRes)

        // Card backgrounds
        musicCard.setCardBackgroundColor(cardBgColor)
        rightCard.setCardBackgroundColor(cardBgColor)
        leftCard.setCardBackgroundColor(cardBgColor)

        // Voice widget background
        val widgetBgColor = if (isDarkMode) Color.parseColor("#1E1E2F") else Color.WHITE
        miniVoiceWidget.setCardBackgroundColor(widgetBgColor)

        // Text colors
        timeText.setTextColor(primaryColor)
        homeText.setTextColor(primaryColor)
        phoneText.setTextColor(primaryColor)
        updatesText.setTextColor(primaryColor)
        settingsText.setTextColor(primaryColor)
        songTitle.setTextColor(primaryColor)
        artistName.setTextColor(secondaryColor)
        tempText.setTextColor(primaryColor)
        conditionText.setTextColor(secondaryColor)
        fuelTitle.setTextColor(primaryColor)
        fuelLevel.setTextColor(Color.parseColor("#2E7D32"))

        // Voice widget text colors
        widgetTitle.setTextColor(primaryColor)
        widgetStatusText.setTextColor(secondaryColor)

        // Icon tints
        homeIcon.setColorFilter(primaryColor)
        phoneIcon.setColorFilter(primaryColor)
        updatesIcon.setColorFilter(primaryColor)
        settingsIcon.setColorFilter(primaryColor)
        profileIcon.setColorFilter(primaryColor)
        wifiIcon.setColorFilter(primaryColor)
        bluetoothIcon.setColorFilter(primaryColor)
        batteryIcon.setColorFilter(primaryColor)
        previousBtn.setColorFilter(primaryColor)
        playBtn.setColorFilter(primaryColor)
        nextBtn.setColorFilter(primaryColor)
        volumeSpeakerBtn.setColorFilter(primaryColor)
        currentTimeText.setTextColor(primaryColor)
        totalDurationText.setTextColor(primaryColor)
        weatherIcon.setColorFilter(primaryColor)
        fuelIcon.setColorFilter(primaryColor)
        darkModeToggle.setColorFilter(primaryColor)
        widgetCloseBtn.setColorFilter(primaryColor)
        updateNotify.setColorFilter(primaryColor)

        // Seekbar colors
        musicSearchView.queryHint = "Search music..."
        val searchText = musicSearchView.findViewById<TextView>(androidx.appcompat.R.id.search_src_text)
        searchText?.setTextColor(primaryColor)
        searchText?.setHintTextColor(secondaryColor)

        // Voice FAB colors
        val fabBgColor = if (isDarkMode) Color.WHITE else Color.BLACK
        val fabIconColor = if (isDarkMode) Color.BLACK else Color.WHITE
        voiceFab.backgroundTintList = ColorStateList.valueOf(fabBgColor)
        voiceFab.imageTintList = ColorStateList.valueOf(fabIconColor)

        darkModeToggle.setImageResource(toggleIcon)
        circleOverlay.setDarkMode(isDarkMode)
        widgetWaveform.updateColors(isDarkMode)
    }

    private fun handleMusicClick() {
        val options = arrayOf("Popular Tracks", "Rock Music", "Electronic", "Jazz", "Search Custom")

        MaterialAlertDialogBuilder(this)
            .setTitle("Choose Music")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> musicService.getPopularTracks { tracks ->
                        if (tracks.isNotEmpty()) {
                            showToast("Loaded ${tracks.size} popular tracks")
                        } else {
                            showToast("No tracks found")
                        }
                    }
                    1 -> musicService.searchByGenre("rock") { tracks ->
                        if (tracks.isNotEmpty()) {
                            showToast("Loaded ${tracks.size} rock tracks")
                        } else {
                            showToast("No rock tracks found")
                        }
                    }
                    2 -> musicService.searchByGenre("electronic") { tracks ->
                        if (tracks.isNotEmpty()) {
                            showToast("Loaded ${tracks.size} electronic tracks")
                        } else {
                            showToast("No electronic tracks found")
                        }
                    }
                    3 -> musicService.searchByGenre("jazz") { tracks ->
                        if (tracks.isNotEmpty()) {
                            showToast("Loaded ${tracks.size} jazz tracks")
                        } else {
                            showToast("No jazz tracks found")
                        }
                    }
                    4 -> {
                        musicService.searchTracks("indie") { tracks ->
                            if (tracks.isNotEmpty()) {
                                showToast("Loaded ${tracks.size} indie tracks")
                            } else {
                                showToast("No tracks found")
                            }
                        }
                    }
                }
            }
            .show()
    }

    private fun handleCarClick() {
        stopCarAttentionAnimation()

        val intermediate = BIG_SCALE * DIP_FACTOR

        if (isCircleShown) {
            // Hide with animation, collapsing to center, and enlarge car with dip
            val alphaAnim = ObjectAnimator.ofFloat(circleOverlay, "alpha", 1f, 0f)
            val scaleXAnim = ObjectAnimator.ofFloat(circleOverlay, "scaleX", 1f, 0.5f)
            val scaleYAnim = ObjectAnimator.ofFloat(circleOverlay, "scaleY", 1f, 0.5f)
            val rotationAnim = ObjectAnimator.ofFloat(circleOverlay, "rotation", 0f, 360f)
            val radiusAnim = ObjectAnimator.ofFloat(circleOverlay, "radiusFactor", 1f, 0f)

            val carScaleX = ObjectAnimator.ofFloat(carImage, "scaleX", 1f, intermediate, BIG_SCALE)
            val carScaleY = ObjectAnimator.ofFloat(carImage, "scaleY", 1f, intermediate, BIG_SCALE)

            val hideSet = AnimatorSet().apply {
                playTogether(alphaAnim, scaleXAnim, scaleYAnim, rotationAnim, radiusAnim, carScaleX, carScaleY)
                duration = 500
            }
            hideSet.addListener(object : Animator.AnimatorListener {
                override fun onAnimationStart(animation: Animator) {}
                override fun onAnimationEnd(animation: Animator) {
                    circleOverlay.visibility = View.GONE
                    if (pulseEnabled) {
                        startCarAttentionAnimation()
                    }
                }
                override fun onAnimationCancel(animation: Animator) {}
                override fun onAnimationRepeat(animation: Animator) {}
            })
            hideSet.start()
        } else {
            // Show with animation, expanding from center, and shrink car with dip
            circleOverlay.visibility = View.VISIBLE
            circleOverlay.alpha = 0f
            circleOverlay.scaleX = 0.5f
            circleOverlay.scaleY = 0.5f
            circleOverlay.rotation = -360f
            circleOverlay.radiusFactor = 0f

            val alphaAnim = ObjectAnimator.ofFloat(circleOverlay, "alpha", 0f, 1f)
            val scaleXAnim = ObjectAnimator.ofFloat(circleOverlay, "scaleX", 0.5f, 1f)
            val scaleYAnim = ObjectAnimator.ofFloat(circleOverlay, "scaleY", 0.5f, 1f)
            val rotationAnim = ObjectAnimator.ofFloat(circleOverlay, "rotation", -360f, 0f)
            val radiusAnim = ObjectAnimator.ofFloat(circleOverlay, "radiusFactor", 0f, 1f)

            val carScaleX = ObjectAnimator.ofFloat(carImage, "scaleX", BIG_SCALE, intermediate, 1f)
            val carScaleY = ObjectAnimator.ofFloat(carImage, "scaleY", BIG_SCALE, intermediate, 1f)

            val showSet = AnimatorSet().apply {
                playTogether(alphaAnim, scaleXAnim, scaleYAnim, rotationAnim, radiusAnim, carScaleX, carScaleY)
                duration = 500
            }
            showSet.addListener(object : Animator.AnimatorListener {
                override fun onAnimationStart(animation: Animator) {}
                override fun onAnimationEnd(animation: Animator) {
                    pulseEnabled = false
                }
                override fun onAnimationCancel(animation: Animator) {}
                override fun onAnimationRepeat(animation: Animator) {}
            })
            showSet.start()
        }
        isCircleShown = !isCircleShown
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
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

    override fun onDestroy() {
        super.onDestroy()
        voiceWidgetManager.cleanup()
        hideProgress()
    }

    // Car attention pulse + glow animations
    private fun startCarAttentionAnimation() {
        val pulseFrom = BIG_SCALE
        val pulseTo = BIG_SCALE * 1.05f
        pulseAnimX = ObjectAnimator.ofFloat(carImage, "scaleX", pulseFrom, pulseTo).apply {
            duration = 1200
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
        }
        pulseAnimY = ObjectAnimator.ofFloat(carImage, "scaleY", pulseFrom, pulseTo).apply {
            duration = 1200
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
        }
        glowAnim = ObjectAnimator.ofFloat(carImage, "alpha", 1f, 0.85f, 1f).apply {
            duration = 2000
            repeatMode = ValueAnimator.RESTART
            repeatCount = ValueAnimator.INFINITE
        }

        pulseAnimX.start()
        pulseAnimY.start()
        glowAnim.start()
    }

    private fun stopCarAttentionAnimation() {
        if (::pulseAnimX.isInitialized) pulseAnimX.cancel()
        if (::pulseAnimY.isInitialized) pulseAnimY.cancel()
        if (::glowAnim.isInitialized) glowAnim.cancel()
        carImage.alpha = 1f
        val targetScale = if (isCircleShown) 1f else BIG_SCALE
        carImage.scaleX = targetScale
        carImage.scaleY = targetScale
    }

    private fun startPulse(view: View) {
        val pulseFrom = 1f
        val pulseTo = 1.2f
        val animX = ObjectAnimator.ofFloat(view, "scaleX", pulseFrom, pulseTo).apply {
            duration = 1200
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
        }
        val animY = ObjectAnimator.ofFloat(view, "scaleY", pulseFrom, pulseTo).apply {
            duration = 1200
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
        }
        val set = AnimatorSet().apply { playTogether(animX, animY) }
        set.start()
        pulseAnims[view] = set
    }

    private fun stopPulse(view: View) {
        pulseAnims[view]?.cancel()
        view.scaleX = 1f
        view.scaleY = 1f
        pulseAnims.remove(view)
    }

    private fun showProgress() {
        progressDialog = ProgressDialog(this).apply {
            setMessage("Updating...")
            setCancelable(false)
            show()
        }
    }

    private fun hideProgress() {
        progressDialog?.dismiss()
    }
}