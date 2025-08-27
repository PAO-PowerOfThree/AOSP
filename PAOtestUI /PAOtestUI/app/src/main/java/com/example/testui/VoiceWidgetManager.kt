package com.example.testui

import android.content.Context
import android.content.SharedPreferences
import android.content.res.AssetManager
import android.graphics.Color
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

    private val handler = Handler(Looper.getMainLooper())
    private val prefs: SharedPreferences = context.getSharedPreferences("voice_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val TAG = "VoiceWidgetManager"
        private const val MODEL_NAME = "vosk-model-small-en-us-0.15"
        private const val SAMPLE_RATE = 16000.0f
        private const val LISTENING_TIMEOUT = 5000L // 5 seconds
    }

    interface VoiceWidgetCallback {
        fun onStatusUpdate(status: String)
        fun onListeningStateChanged(isListening: Boolean)
        fun onRecognizedText(text: String)
        fun onError(error: String)
    }

    init {
        initializeModel()
    }

    private fun initializeModel() {
        lifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                callback.onStatusUpdate("Loading voice model...")

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
                    callback.onStatusUpdate("Tap to start listening")
                }

            } catch (e: IOException) {
                withContext(Dispatchers.Main) {
                    Log.e(TAG, "Model initialization failed: ${e.message}")
                    callback.onError("Model initialization failed: ${e.message}")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Log.e(TAG, "Model creation failed: ${e.message}")
                    callback.onError("Model creation failed: ${e.message}")
                }
            }
        }
    }

    private fun isModelValid(modelDir: File): Boolean {
        if (!modelDir.exists()) return false

        val requiredFiles = listOf(
            "final.mdl",
            "graph/HCLG.fst",
            "graph/words.txt"
        )

        return requiredFiles.all { fileName ->
            File(modelDir, fileName).exists()
        }
    }

    private fun unpackModelToInternal(context: Context, modelName: String) {
        val assetManager = context.assets
        val modelDir = File(context.filesDir, modelName)

        if (!modelDir.exists()) {
            try {
                modelDir.mkdirs()
                copyAssetFolder(assetManager, modelName, modelDir.absolutePath)
                Log.d(TAG, "Model unpacked to ${modelDir.absolutePath}")
            } catch (e: IOException) {
                Log.e(TAG, "Failed to unpack model: ${e.message}")
                throw e
            }
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
                recognizer = Recognizer(model, SAMPLE_RATE)
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

                        // Auto-reset status after delay
                        handler.postDelayed({
                            callback.onStatusUpdate("Tap to start listening")
                        }, 2000)
                    }
                }, LISTENING_TIMEOUT)

                Log.d(TAG, "Started listening")

            } catch (e: Exception) {
                Log.e(TAG, "Failed to start recognition: ${e.message}")
                callback.onError("Failed to start listening: ${e.message}")
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
            callback.onStatusUpdate("Processing...")

            // Simulate processing delay
            handler.postDelayed({
                callback.onStatusUpdate("Tap to start listening")
            }, 1500)

            Log.d(TAG, "Stopped listening")

        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recognition: ${e.message}")
        }
    }

    fun toggleListening() {
        if (isListening) {
            stopListening()
        } else {
            startListening()
        }
    }

    fun isCurrentlyListening(): Boolean = isListening

    fun cleanup() {
        handler.removeCallbacksAndMessages(null)
        stopListening()
        model = null
    }

    // RecognitionListener implementation
    override fun onPartialResult(hypothesis: String?) {
        Log.d(TAG, "Partial result: $hypothesis")
        // Update UI with partial results if needed
        hypothesis?.let {
            if (it.isNotBlank()) {
                callback.onStatusUpdate("Hearing: ${it.take(30)}...")
            }
        }
    }

    override fun onResult(hypothesis: String?) {
        Log.d(TAG, "Final result: $hypothesis")

        hypothesis?.let { result ->
            if (result.isNotBlank()) {
                callback.onRecognizedText(result)
                callback.onStatusUpdate("Recognized: ${result.take(30)}")

                // Process the recognized text
                processRecognizedText(result)
            } else {
                callback.onStatusUpdate("No speech recognized")
            }
        }

        stopListening()
    }

    override fun onFinalResult(hypothesis: String?) {
        Log.d(TAG, "Final result: $hypothesis")
        onResult(hypothesis)
    }

    override fun onError(exception: Exception?) {
        Log.e(TAG, "Recognition error: ${exception?.message}")
        callback.onError("Recognition error: ${exception?.message ?: "Unknown error"}")
        stopListening()
    }

    override fun onTimeout() {
        Log.d(TAG, "Recognition timeout")
        callback.onStatusUpdate("Listening timeout")
        stopListening()
    }

    private fun processRecognizedText(text: String) {
        // Process voice commands here
        val lowerText = text.lowercase().trim()

        when {
            lowerText.contains("stop") || lowerText.contains("cancel") -> {
                callback.onStatusUpdate("Voice stopped")
            }
            lowerText.contains("hello") || lowerText.contains("hi") -> {
                callback.onStatusUpdate("Hello! How can I help?")
            }
            lowerText.contains("music") -> {
                callback.onStatusUpdate("Opening music player...")
            }
            lowerText.contains("weather") -> {
                callback.onStatusUpdate("Checking weather...")
            }
            lowerText.contains("navigation") || lowerText.contains("navigate") -> {
                callback.onStatusUpdate("Opening navigation...")
            }
            else -> {
                callback.onStatusUpdate("Command: $text")
            }
        }

        // Auto-reset status after showing command
        handler.postDelayed({
            callback.onStatusUpdate("Tap to start listening")
        }, 3000)
    }
}

