package com.example.testui

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.AssetManager
import android.graphics.Color
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class VoiceWidgetManager(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val callback: VoiceWidgetCallback
) : RecognitionListener {

    private var model: Model? = null
    private var speechService: SpeechService? = null
    private var recognizer: Recognizer? = null
    private var isListening = false
    private var isModelLoaded = false
    private var vhalManager: VhalManager? = null
    private var mediaPlayer: MediaPlayer? = null

    private val handler = Handler(Looper.getMainLooper())
    private val prefs: SharedPreferences = context.getSharedPreferences(SharedState.PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val TAG = "VoiceWidgetManager"
        private const val MODEL_NAME = "vosk-model-small-en-us-0.15"
        private const val SAMPLE_RATE = 16000.0f
        private const val LISTENING_TIMEOUT = 8000L // 8 seconds
    }

    interface VoiceWidgetCallback {
        fun onStatusUpdate(status: String)
        fun onListeningStateChanged(isListening: Boolean)
        fun onRecognizedText(text: String)
        fun onError(error: String)
    }

    init {
        initializeVhalManager()
        initializeModel()
    }

    private fun initializeVhalManager() {
        vhalManager = VhalManager(context)
    }

    private fun initializeModel() {
        lifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                withContext(Dispatchers.Main) {
                    callback.onStatusUpdate("Loading voice model...")
                }

                val modelPath = File(context.filesDir, MODEL_NAME).absolutePath
                val modelDir = File(modelPath)

                // Check if model is already extracted and valid
                if (!isModelValid(modelDir)) {
                    withContext(Dispatchers.Main) {
                        callback.onStatusUpdate("Extracting voice model...")
                    }
                    unpackModelToInternal(context, MODEL_NAME)
                }

                // Create the model on the background thread
                val loadedModel = Model(modelPath)
                Log.d(TAG, "Model loaded successfully from $modelPath")

                withContext(Dispatchers.Main) {
                    model = loadedModel
                    isModelLoaded = true
                    callback.onStatusUpdate("Ready - Tap to speak")
                }

            } catch (e: IOException) {
                withContext(Dispatchers.Main) {
                    Log.e(TAG, "Model initialization failed: ${e.message}")
                    callback.onError("Voice model failed to load")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Log.e(TAG, "Model creation failed: ${e.message}")
                    callback.onError("Voice system error")
                }
            }
        }
    }

    private fun isModelValid(modelDir: File): Boolean {
        if (!modelDir.exists()) return false

        val requiredFiles = listOf(
            "am/final.mdl",
            "graph/Gr.fst",
            "graph/HCLr.fst",
            "ivector/final.ie"
        )

        return requiredFiles.all { fileName ->
            File(modelDir, fileName).exists()
        }
    }

    private fun unpackModelToInternal(context: Context, modelName: String) {
        val assetManager = context.assets
        val modelDir = File(context.filesDir, modelName)

        if (modelDir.exists()) {
            modelDir.deleteRecursively()
        }

        try {
            modelDir.mkdirs()
            copyAssetFolder(assetManager, modelName, modelDir.absolutePath)
            Log.d(TAG, "Model unpacked to ${modelDir.absolutePath}")
        } catch (e: IOException) {
            Log.e(TAG, "Failed to unpack model: ${e.message}")
            throw e
        }
    }

    private fun copyAssetFolder(assetManager: AssetManager, srcPath: String, dstPath: String) {
        val files = assetManager.list(srcPath) ?: return
        val dstDir = File(dstPath)
        dstDir.mkdirs()

        for (fileName in files) {
            val srcFilePath = if (srcPath.isEmpty()) fileName else "$srcPath/$fileName"
            val dstFilePath = File(dstDir, fileName)
            val subFiles = assetManager.list(srcFilePath)

            if (subFiles?.isNotEmpty() == true) {
                copyAssetFolder(assetManager, srcFilePath, dstFilePath.absolutePath)
            } else {
                assetManager.open(srcFilePath).use { inputStream ->
                    FileOutputStream(dstFilePath).use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
            }
        }
    }

    fun startListening() {
        if (!isModelLoaded) {
            callback.onError("Voice model not ready")
            return
        }

        if (isListening) {
            Log.w(TAG, "Already listening")
            return
        }

        model?.let { model ->
            try {
                recognizer = Recognizer(model, SAMPLE_RATE).apply {
                    setMaxAlternatives(1)
                    setWords(true)
                }
                speechService = SpeechService(recognizer, SAMPLE_RATE)
                speechService?.startListening(this)

                isListening = true
                callback.onListeningStateChanged(true)
                callback.onStatusUpdate("Listening...")

                // Set timeout for listening
                handler.postDelayed({
                    if (isListening) {
                        stopListening()
                        callback.onStatusUpdate("No speech detected")
                        resetToReady()
                    }
                }, LISTENING_TIMEOUT)

                Log.d(TAG, "Started listening")

            } catch (e: Exception) {
                Log.e(TAG, "Failed to start recognition: ${e.message}")
                callback.onError("Failed to start listening")
                isListening = false
                callback.onListeningStateChanged(false)
            }
        } ?: run {
            callback.onError("Voice model not loaded")
        }
    }

    fun stopListening() {
        if (!isListening) return

        try {
            speechService?.stop()
            speechService = null
            recognizer = null

            isListening = false
            callback.onListeningStateChanged(false)

            Log.d(TAG, "Stopped listening")

        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recognition: ${e.message}")
        }
    }

    fun toggleListening() {
        if (isListening) {
            stopListening()
            resetToReady()
        } else {
            startListening()
        }
    }

    private fun resetToReady() {
        handler.postDelayed({
            if (!isListening) {
                callback.onStatusUpdate("Ready - Tap to speak")
            }
        }, 2000)
    }

    fun isCurrentlyListening(): Boolean = isListening

    fun cleanup() {
        handler.removeCallbacksAndMessages(null)
        stopListening()
        vhalManager?.cleanup()
        mediaPlayer?.release()
        mediaPlayer = null
        model?.close()
        model = null
    }

    // RecognitionListener implementation
    override fun onPartialResult(hypothesis: String?) {
        // Extract text from JSON-like response
        val extractedText = extractTextFromHypothesis(hypothesis)
        if (extractedText.isNotBlank()) {
            callback.onStatusUpdate("Hearing: ${extractedText.take(25)}...")
        }
    }

    override fun onResult(hypothesis: String?) {
        processResult(hypothesis)
    }

    override fun onFinalResult(hypothesis: String?) {
        processResult(hypothesis)
    }

    private fun processResult(hypothesis: String?) {
        val extractedText = extractTextFromHypothesis(hypothesis)
        Log.d(TAG, "Final result: '$extractedText' (from: $hypothesis)")

        if (extractedText.isNotBlank()) {
            callback.onRecognizedText(extractedText)
            callback.onStatusUpdate("Processing command...")

            // Process the command
            processVoiceCommand(extractedText)
        } else {
            callback.onStatusUpdate("No speech recognized")
            resetToReady()
        }

        stopListening()
    }

    override fun onError(exception: Exception?) {
        Log.e(TAG, "Recognition error: ${exception?.message}")
        callback.onError("Recognition error")
        stopListening()
        resetToReady()
    }

    override fun onTimeout() {
        Log.d(TAG, "Recognition timeout")
        callback.onStatusUpdate("Timeout - Try again")
        stopListening()
        resetToReady()
    }

    private fun extractTextFromHypothesis(hypothesis: String?): String {
        if (hypothesis.isNullOrBlank()) return ""

        // Try to extract from JSON format: {"text": "..."}
        val regex = Regex("\"text\"\\s*:\\s*\"([^\"]*)\"|\"partial\"\\s*:\\s*\"([^\"]*)\"")
        val match = regex.find(hypothesis)

        return match?.let {
            it.groupValues[1].takeIf { text -> text.isNotBlank() }
                ?: it.groupValues[2].takeIf { text -> text.isNotBlank() }
        }?.trim() ?: hypothesis.trim()
    }

    private fun processVoiceCommand(command: String) {
        val lowerCommand = command.lowercase().trim()
        Log.d(TAG, "Processing voice command: '$lowerCommand'")

        when {
            // Music Commands
            isPlayCommand(lowerCommand) -> {
                sendMusicCommand("PLAY")
                callback.onStatusUpdate("Playing music")
                playCommandSound()
            }
            isPauseCommand(lowerCommand) -> {
                sendMusicCommand("PAUSE")
                callback.onStatusUpdate("Music paused")
                playCommandSound()
            }
            isStopCommand(lowerCommand) -> {
                sendMusicCommand("STOP")
                callback.onStatusUpdate("Music stopped")
                playCommandSound()
            }
            isNextCommand(lowerCommand) -> {
                sendMusicCommand("NEXT")
                callback.onStatusUpdate("Next track")
                playCommandSound()
            }
            isPreviousCommand(lowerCommand) -> {
                sendMusicCommand("PREVIOUS")
                callback.onStatusUpdate("Previous track")
                playCommandSound()
            }
            isVolumeUpCommand(lowerCommand) -> {
                sendMusicCommand("VOLUME_UP")
                callback.onStatusUpdate("Volume up")
                playCommandSound()
            }
            isVolumeDownCommand(lowerCommand) -> {
                sendMusicCommand("VOLUME_DOWN")
                callback.onStatusUpdate("Volume down")
                playCommandSound()
            }
            isMuteCommand(lowerCommand) -> {
                sendMusicCommand("MUTE")
                callback.onStatusUpdate("Muted")
                playCommandSound()
            }

            // Music Genre Commands
            isRockMusicCommand(lowerCommand) -> {
                sendMusicCommand("PLAY_GENRE:rock")
                callback.onStatusUpdate("Playing rock music")
                playCommandSound()
            }
            isPopMusicCommand(lowerCommand) -> {
                sendMusicCommand("PLAY_GENRE:pop")
                callback.onStatusUpdate("Playing pop music")
                playCommandSound()
            }
            isJazzMusicCommand(lowerCommand) -> {
                sendMusicCommand("PLAY_GENRE:jazz")
                callback.onStatusUpdate("Playing jazz music")
                playCommandSound()
            }
            isElectronicMusicCommand(lowerCommand) -> {
                sendMusicCommand("PLAY_GENRE:electronic")
                callback.onStatusUpdate("Playing electronic music")
                playCommandSound()
            }
            isPopularMusicCommand(lowerCommand) -> {
                sendMusicCommand("PLAY_POPULAR")
                callback.onStatusUpdate("Playing popular tracks")
                playCommandSound()
            }

            // Navigation Commands
            isNavigationCommand(lowerCommand) -> {
                openNavigation()
                callback.onStatusUpdate("Opening navigation")
                playCommandSound()
            }
            isHomeNavigationCommand(lowerCommand) -> {
                openNavigation()
                callback.onStatusUpdate("Navigating home")
                playCommandSound()
            }

            // HVAC Commands
            isAcOnCommand(lowerCommand) -> {
                handleAcCommand(true)
                callback.onStatusUpdate("AC turned on")
                playCommandSound()
            }
            isAcOffCommand(lowerCommand) -> {
                handleAcCommand(false)
                callback.onStatusUpdate("AC turned off")
                playCommandSound()
            }
            isTempUpCommand(lowerCommand) -> {
                handleTempCommand(true)
                callback.onStatusUpdate("Temperature increased")
                playCommandSound()
            }
            isTempDownCommand(lowerCommand) -> {
                handleTempCommand(false)
                callback.onStatusUpdate("Temperature decreased")
                playCommandSound()
            }

            // Light Commands
            isLightOnCommand(lowerCommand) -> {
                handleLightCommand(true)
                callback.onStatusUpdate("Lights turned on")
                playCommandSound()
            }
            isLightOffCommand(lowerCommand) -> {
                handleLightCommand(false)
                callback.onStatusUpdate("Lights turned off")
                playCommandSound()
            }

            // Greetings
            isGreetingCommand(lowerCommand) -> {
                callback.onStatusUpdate("Hello! How can I help?")
                playCommandSound()
            }

            else -> {
                callback.onStatusUpdate("Command not recognized")
                Log.d(TAG, "Unrecognized command: '$lowerCommand'")
            }
        }

        resetToReady()
    }

    // Command recognition methods
    private fun isPlayCommand(cmd: String) =
        cmd.contains("play music") || cmd == "play"

    private fun isPauseCommand(cmd: String) =
        cmd.contains("pause") || cmd.contains("pause music")

    private fun isStopCommand(cmd: String) =
        cmd.contains("stop music") || cmd.contains("stop")

    private fun isNextCommand(cmd: String) =
        cmd.contains("next") || cmd.contains("skip")

    private fun isPreviousCommand(cmd: String) =
        cmd.contains("previous") || cmd.contains("back") || cmd.contains("last")

    private fun isVolumeUpCommand(cmd: String) =
        cmd.contains("volume up") || cmd.contains("louder")

    private fun isVolumeDownCommand(cmd: String) =
        cmd.contains("volume down") || cmd.contains("quieter")

    private fun isMuteCommand(cmd: String) =
        cmd.contains("mute")

    private fun isRockMusicCommand(cmd: String) =
        cmd.contains("rock") && cmd.contains("music") || cmd.contains("play rock")

    private fun isPopMusicCommand(cmd: String) =
        cmd.contains("pop") && cmd.contains("music") || cmd.contains("play pop")

    private fun isJazzMusicCommand(cmd: String) =
        cmd.contains("jazz") || cmd.contains("play jazz")

    private fun isElectronicMusicCommand(cmd: String) =
        cmd.contains("electronic") || cmd.contains("play electronic")

    private fun isPopularMusicCommand(cmd: String) =
        cmd.contains("popular") || cmd.contains("popular tracks")

    private fun isNavigationCommand(cmd: String) =
        cmd.contains("navigation") || cmd.contains("navigate") || cmd.contains("map") || cmd.contains("gps")

    private fun isHomeNavigationCommand(cmd: String) =
        cmd.contains("go home") || cmd.contains("navigate home")

    private fun isAcOnCommand(cmd: String) =
        cmd.contains("ac on") || cmd.contains("air conditioning on") || cmd.contains("air conditioner on")

    private fun isAcOffCommand(cmd: String) =
        cmd.contains("ac off") || cmd.contains("air conditioning off") || cmd.contains("air conditioner off")

    private fun isTempUpCommand(cmd: String) =
        cmd.contains("temperature up") || cmd.contains("temp up") || cmd.contains("warmer")

    private fun isTempDownCommand(cmd: String) =
        cmd.contains("temperature down") || cmd.contains("temp down") || cmd.contains("cooler")

    private fun isLightOnCommand(cmd: String) =
        cmd.contains("lights on") || cmd.contains("light on")

    private fun isLightOffCommand(cmd: String) =
        cmd.contains("lights off") || cmd.contains("light off")

    private fun isGreetingCommand(cmd: String) =
        cmd.contains("hello") || cmd.contains("hi") || cmd.contains("hey")

    // Command execution methods
    private fun sendMusicCommand(command: String) {
        try {
            prefs.edit()
                .putString("voice_music_command", command)
                .putLong("voice_command_timestamp", System.currentTimeMillis())
                .apply()
            Log.d(TAG, "Music command sent: $command")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send music command: ${e.message}")
        }
    }

    private fun openNavigation() {
        try {
            val intent = Intent(context, NavigationActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra("isDarkMode", prefs.getBoolean("isDarkMode", false))
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open navigation: ${e.message}")
        }
    }

    private fun handleAcCommand(turnOn: Boolean) {
        try {
            vhalManager?.setAcStatusForAllSeats(if (turnOn) 1 else 0)
            prefs.edit()
                .putInt(SharedState.VOSK_AC_STATE, if (turnOn) 1 else 0)
                .putBoolean(SharedState.KEY_AC_STATE, turnOn)
                .apply()
            Log.d(TAG, "AC turned ${if (turnOn) "ON" else "OFF"}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to control AC: ${e.message}")
        }
    }

    private fun handleTempCommand(increase: Boolean) {
        try {
            val currentTemp = prefs.getInt(SharedState.KEY_INNER_TEMP, 22)
            val newTemp = if (increase) {
                (currentTemp + 1).coerceAtMost(30)
            } else {
                (currentTemp - 1).coerceAtLeast(16)
            }

            vhalManager?.setTemperatureForAllSeats(newTemp)
            prefs.edit()
                .putInt(SharedState.KEY_INNER_TEMP, newTemp)
                .putInt(SharedState.VOSK_AC_STATE, if (increase) 3 else 2)
                .apply()

            Log.d(TAG, "Temperature ${if (increase) "increased" else "decreased"} to ${newTemp}°C")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to control temperature: ${e.message}")
        }
    }

    private fun handleLightCommand(turnOn: Boolean) {
        try {
            if (turnOn) {
                vhalManager?.setAmbientLighting(Color.CYAN, 50)
                prefs.edit()
                    .putInt(SharedState.KEY_AMBIENT_COLOR, Color.CYAN)
                    .apply()
            } else {
                vhalManager?.turnOffAllLeds()
            }
            Log.d(TAG, "Lights turned ${if (turnOn) "ON" else "OFF"}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to control lights: ${e.message}")
        }
    }

    private fun playCommandSound() {
        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer.create(context, R.raw.hey_car)
            mediaPlayer?.setOnCompletionListener { mp ->
                mp.release()
                mediaPlayer = null
            }
            mediaPlayer?.start()
        } catch (e: Exception) {
            Log.e(TAG, "Error playing command sound: ${e.message}")
        }
    }
}