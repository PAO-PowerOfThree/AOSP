package com.example.testui

import android.content.Context
import android.content.SharedPreferences
import android.content.res.AssetManager
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.children
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class VoskDialogFragment : Fragment(), RecognitionListener {
    private var model: Model? = null
    private var speechService: SpeechService? = null
    private var vhalManager: VhalManager? = null

    private val viewModel: PaoVoiceViewModel by activityViewModels {
        PaoVoiceViewModel.Companion.ViewModelFactory(requireContext().applicationContext)
    }

    private lateinit var titleText: TextView
    private lateinit var waveformView: WaveformView
    private lateinit var recognizedTextView: TextView

    // UI containers for better state management
    private lateinit var idleContainer: View
    private lateinit var waveformContainer: View
    private lateinit var textDisplayContainer: View

    private lateinit var prefs: SharedPreferences
    private var recognizedTextDisplayJob: Job? = null

    // Preference listener for ambient color changes
    private val ambientColorListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == SharedState.KEY_AMBIENT_COLOR) {
            val newColor = prefs.getInt(SharedState.KEY_AMBIENT_COLOR, Color.CYAN)
            waveformView.updateGradientColors(newColor, Color.WHITE)
            Log.d(TAG, "Waveform color updated to: $newColor")
        }
    }

    companion object {
        private const val TAG = "VoskFragment"
        private const val MODEL_NAME = "vosk-model-small-en-us-0.15" // Updated model name
        private const val SAMPLE_RATE = 16000.0f
        private const val DISPLAY_DURATION_MS = 4000L
        // REMOVED: Permission request code - MainActivity handles all permissions
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        Log.d(TAG, "onCreateView called")
        val configuration = resources.configuration
        val layoutRes = if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            R.layout.fragment_vosk_land
        } else {
            R.layout.fragment_vosk
        }

        val view = inflater.inflate(layoutRes, container, false)
        initializeViews(view)
        initializePreferences()
        initializeVhalManager()
        observeViewModel()
        showWaitingForPermission()

        return view
    }

    private fun showWaitingForPermission() {
        viewModel.updateRecognizedText(
            "Waiting for microphone permission...",
            DisplayState.Text,
            vhalManager
        )
    }

    // Called by MainActivity when permission is granted
    fun onPermissionGranted() {
        Log.d(TAG, "Permission granted by MainActivity")
        debugAssets()
        initializeModel()
    }

    private fun initializeViews(view: View) {
        titleText = view.findViewById(R.id.title_text)
        waveformView = view.findViewById(R.id.waveform_view)
        recognizedTextView = view.findViewById(R.id.recognized_text)

        idleContainer = view.findViewById(R.id.idle_container)
        waveformContainer = view.findViewById(R.id.waveform_container)
        textDisplayContainer = view.findViewById(R.id.text_display_container)

        // Initialize all containers as hidden
        hideAllContainers()

        // Show idle state by default
        idleContainer.visibility = View.VISIBLE

        // Set up mic button click listener (left panel FAB)
        val voiceToggleButton = view.findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.voice_toggle_button)
        voiceToggleButton?.setOnClickListener {
            Log.d(TAG, "Voice toggle button clicked")
            handleMicrophoneClick()
        }

        // Set up center microphone icon click listener (in idle container)
        val microphoneIcon = view.findViewById<ImageView>(R.id.microphone_icon)
        microphoneIcon?.setOnClickListener {
            Log.d(TAG, "Center microphone icon clicked")
            handleMicrophoneClick()
        }

        // Make the entire microphone frame clickable for better UX
        val microphoneFrame = idleContainer.findViewById<FrameLayout>(R.id.microphone_frame)
        microphoneFrame?.apply {
            setOnClickListener {
                Log.d(TAG, "Microphone frame clicked")
                handleMicrophoneClick()
            }
            isClickable = true
            isFocusable = true
        }

        // Set up home button click listener
        val homeButton = view.findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.home_button)
        homeButton?.setOnClickListener {
            Log.d(TAG, "Home button clicked")
            handleHomeClick()
        }
    }

    private fun handleMicrophoneClick() {
        if (model != null) {
            viewModel.toggleListening()
        } else {
            Log.w(TAG, "Model not ready, cannot start listening")
            viewModel.updateRecognizedText(
                "Voice model still loading...",
                DisplayState.Text,
                vhalManager
            )
        }
    }

    private fun handleHomeClick() {
        Log.d(TAG, "Home button clicked - returning to main activity")

        // Stop any ongoing recognition
        if (viewModel.isListening.value == true) {
            viewModel.toggleListening()
        }

        // Return to previous screen
        parentFragmentManager.popBackStack()
    }

    private fun initializePreferences() {
        prefs = requireContext().getSharedPreferences(SharedState.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.registerOnSharedPreferenceChangeListener(ambientColorListener)

        // Set initial waveform color
        val initialColor = prefs.getInt(SharedState.KEY_AMBIENT_COLOR, Color.CYAN)
        waveformView.updateGradientColors(initialColor, Color.WHITE)
    }

    private fun initializeVhalManager() {
        vhalManager = VhalManager(requireContext())
    }

    private fun observeViewModel() {
        val voiceToggleButton = view?.findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.voice_toggle_button)

        viewModel.isListening.observe(viewLifecycleOwner) { isListening ->
            Log.d(TAG, "isListening changed: $isListening")

            // Update button icon
            voiceToggleButton?.setImageResource(
                if (isListening) R.drawable.ic_mic_off else R.drawable.ic_mic_on
            )

            if (isListening) {
                startRecognition()
            } else {
                stopRecognition()
            }
        }

        viewModel.displayState.observe(viewLifecycleOwner) { state ->
            Log.d(TAG, "DisplayState changed: $state")
            updateUIForState(state)
        }

        viewModel.recognizedText.observe(viewLifecycleOwner) { text ->
            Log.d(TAG, "Recognized text: $text")
            handleRecognizedText(text)
        }
    }

    private fun updateUIForState(state: DisplayState) {
        // Cancel any pending text display jobs
        recognizedTextDisplayJob?.cancel()

        when (state) {
            DisplayState.Idle -> {
                hideAllContainers()
                idleContainer.visibility = View.VISIBLE
                waveformView.stopAnimations()
            }

            DisplayState.Listening -> {
                hideAllContainers()
                waveformContainer.visibility = View.VISIBLE
                waveformView.startAnimations()
            }

            DisplayState.PlayingSound -> {
                hideAllContainers()
                waveformContainer.visibility = View.VISIBLE
                waveformView.startAnimations()
            }

            DisplayState.Text -> {
                hideAllContainers()
                textDisplayContainer.visibility = View.VISIBLE
                waveformView.stopAnimations()
            }
        }
    }

    private fun hideAllContainers() {
        idleContainer.visibility = View.GONE
        waveformContainer.visibility = View.GONE
        textDisplayContainer.visibility = View.GONE
    }

    private fun handleRecognizedText(text: String) {
        if (text.isBlank()) {
            recognizedTextView.visibility = View.GONE
            return
        }

        // Only show text for stop phrases or error messages
        if (viewModel.isStopPhrase(text) || isErrorMessage(text)) {
            recognizedTextView.text = text
            recognizedTextView.visibility = View.VISIBLE

            // Apply glow animation for stop phrases
            if (viewModel.isStopPhrase(text)) {
                applyGlowAnimation()
            }

            // Auto-hide text after delay
            recognizedTextDisplayJob = viewLifecycleOwner.lifecycleScope.launch {
                delay(DISPLAY_DURATION_MS)
                recognizedTextView.visibility = View.GONE
                recognizedTextView.text = ""

                // Return to idle if not listening
                if (viewModel.isListening.value == false) {
                    viewModel.setDisplayState(DisplayState.Idle)
                }
            }
        } else {
            recognizedTextView.visibility = View.GONE
        }
    }

    private fun isErrorMessage(text: String): Boolean {
        return text.contains("Error") ||
                text.contains("Permission denied") ||
                text.contains("Model not loaded") ||
                text.contains("timed out") ||
                text.contains("No text recognized") ||
                text.contains("Microphone permission required") ||
                text.contains("Waiting for microphone permission")
    }

    private fun applyGlowAnimation() {
        val glowIn = AlphaAnimation(0f, 0.8f).apply {
            duration = 300
            setAnimationListener(object : Animation.AnimationListener {
                override fun onAnimationStart(animation: Animation?) {}
                override fun onAnimationEnd(animation: Animation?) {
                    val glowOut = AlphaAnimation(0.8f, 1f).apply {
                        duration = 600
                    }
                    recognizedTextView.startAnimation(glowOut)
                }
                override fun onAnimationRepeat(animation: Animation?) {}
            })
        }
        recognizedTextView.startAnimation(glowIn)
    }

    private fun isModelValid(modelDir: File): Boolean {
        if (!modelDir.exists()) {
            Log.d(TAG, "Model directory does not exist: ${modelDir.absolutePath}")
            return false
        }

        // Check for essential model files based on the actual structure we see in logs
        val requiredFiles = listOf(
            "am/final.mdl",           // Acoustic model - in 'am' subdirectory
            "graph/Gr.fst",           // Grammar FST
            "graph/HCLr.fst",         // HCL FST
            "ivector/final.ie"        // ivector extractor
        )

        Log.d(TAG, "Validating model files...")
        return requiredFiles.all { fileName ->
            val file = File(modelDir, fileName)
            val exists = file.exists()
            Log.d(TAG, "Checking ${fileName}: exists=$exists${if (exists) " (${file.length()} bytes)" else ""}")
            exists
        }
    }

    // Also update the initialization validation to match
    private fun initializeModel() {
        Log.d(TAG, "Starting model initialization...")
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val modelPath = File(requireContext().filesDir, MODEL_NAME).absolutePath
                val modelDir = File(modelPath)

                Log.d(TAG, "Model path: $modelPath")
                Log.d(TAG, "Model directory exists: ${modelDir.exists()}")

                // Check if model is already extracted and valid
                if (!isModelValid(modelDir)) {
                    Log.d(TAG, "Model not valid, extracting...")
                    withContext(Dispatchers.Main) {
                        viewModel.updateRecognizedText(
                            "Loading voice model... Please wait",
                            DisplayState.Text,
                            vhalManager
                        )
                    }

                    unpackModelToInternal(requireContext(), MODEL_NAME)
                    Log.d(TAG, "Model extraction completed")

                    // Re-validate after extraction
                    if (!isModelValid(modelDir)) {
                        throw IOException("Model validation failed after extraction")
                    }
                } else {
                    Log.d(TAG, "Model already exists and is valid")
                }

                Log.d(TAG, "Creating Model object...")
                // Create the model on the background thread
                val loadedModel = Model(modelPath)
                Log.d(TAG, "Model loaded successfully from $modelPath")

                withContext(Dispatchers.Main) {
                    model = loadedModel
                    Log.d(TAG, "Model set successfully, voice recognition ready")
                    // Clear waiting message and set to idle
                    viewModel.updateRecognizedText("", DisplayState.Idle, vhalManager)
                }

            } catch (e: IOException) {
                Log.e(TAG, "Model initialization failed - IOException: ${e.message}")
                Log.e(TAG, "Stack trace: ${e.stackTraceToString()}")
                withContext(Dispatchers.Main) {
                    viewModel.updateRecognizedText(
                        "Model initialization failed: ${e.message}",
                        DisplayState.Text,
                        vhalManager
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Model creation failed - Exception: ${e.message}")
                Log.e(TAG, "Stack trace: ${e.stackTraceToString()}")
                withContext(Dispatchers.Main) {
                    viewModel.updateRecognizedText(
                        "Model creation failed: ${e.message}",
                        DisplayState.Text,
                        vhalManager
                    )
                }
            }
        }
    }

    private fun copyAssetFolder(assetManager: AssetManager, srcPath: String, dstPath: String) {
        Log.d(TAG, "copyAssetFolder: srcPath='$srcPath', dstPath='$dstPath'")

        try {
            val files = assetManager.list(srcPath)
            Log.d(TAG, "Files in '$srcPath': ${files?.joinToString(", ") ?: "null"}")

            if (files == null || files.isEmpty()) {
                Log.w(TAG, "No files found in asset path: $srcPath")
                return
            }

            val dstDir = File(dstPath)
            if (!dstDir.exists()) {
                val created = dstDir.mkdirs()
                Log.d(TAG, "Created directory '$dstPath': $created")
            }

            for (fileName in files) {
                val srcFilePath = if (srcPath.isEmpty()) fileName else "$srcPath/$fileName"
                val dstFilePath = File(dstDir, fileName)

                Log.d(TAG, "Processing: '$srcFilePath' -> '${dstFilePath.absolutePath}'")

                val subFiles = assetManager.list(srcFilePath)
                Log.d(TAG, "Subfiles in '$srcFilePath': ${subFiles?.joinToString(", ") ?: "null"}")

                if (subFiles?.isNotEmpty() == true) {
                    // It's a directory
                    Log.d(TAG, "Copying directory: $srcFilePath")
                    copyAssetFolder(assetManager, srcFilePath, dstFilePath.absolutePath)
                } else {
                    // It's a file
                    Log.d(TAG, "Copying file: $srcFilePath")
                    try {
                        assetManager.open(srcFilePath).use { inputStream ->
                            FileOutputStream(dstFilePath).use { outputStream ->
                                val bytesCopied = inputStream.copyTo(outputStream)
                                Log.d(TAG, "Copied $bytesCopied bytes to ${dstFilePath.absolutePath}")
                            }
                        }

                        // Verify the file was created
                        if (dstFilePath.exists()) {
                            Log.d(TAG, "✓ File created successfully: ${dstFilePath.absolutePath} (${dstFilePath.length()} bytes)")
                        } else {
                            Log.e(TAG, "✗ File NOT created: ${dstFilePath.absolutePath}")
                        }

                    } catch (e: Exception) {
                        Log.e(TAG, "Error copying file '$srcFilePath': ${e.message}")
                        throw e
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in copyAssetFolder for '$srcPath': ${e.message}")
            throw e
        }
    }

    // Also enhance the unpackModelToInternal function
    private fun unpackModelToInternal(context: Context, modelName: String) {
        val assetManager = context.assets
        val modelDir = File(context.filesDir, modelName)

        Log.d(TAG, "unpackModelToInternal: modelName='$modelName'")
        Log.d(TAG, "Target directory: ${modelDir.absolutePath}")
        Log.d(TAG, "Target directory exists: ${modelDir.exists()}")

        // Always clean up and re-extract to ensure clean state
        if (modelDir.exists()) {
            Log.d(TAG, "Removing existing model directory...")
            val deleted = modelDir.deleteRecursively()
            Log.d(TAG, "Deletion successful: $deleted")
        }

        try {
            modelDir.mkdirs()
            Log.d(TAG, "Created model directory: ${modelDir.exists()}")

            // Update progress during extraction
            lifecycleScope.launch(Dispatchers.Main) {
                viewModel.updateRecognizedText(
                    "Extracting voice model files...",
                    DisplayState.Text,
                    vhalManager
                )
            }

            copyAssetFolder(assetManager, modelName, modelDir.absolutePath)
            Log.d(TAG, "Model unpacked to ${modelDir.absolutePath}")

            // Verify extraction by listing all extracted files
            Log.d(TAG, "=== Extracted Files ===")
            listDirectory(modelDir, "")
            Log.d(TAG, "=== End Extracted Files ===")

            // Update progress after extraction
            lifecycleScope.launch(Dispatchers.Main) {
                viewModel.updateRecognizedText(
                    "Initializing voice recognition...",
                    DisplayState.Text,
                    vhalManager
                )
            }

        } catch (e: IOException) {
            Log.e(TAG, "Failed to unpack model: ${e.message}")
            throw e
        }
    }

    // Helper function to list directory contents recursively
    private fun listDirectory(dir: File, indent: String) {
        if (!dir.exists()) {
            Log.d(TAG, "${indent}Directory does not exist: ${dir.absolutePath}")
            return
        }

        val files = dir.listFiles()
        if (files == null) {
            Log.d(TAG, "${indent}Cannot list files in: ${dir.absolutePath}")
            return
        }

        for (file in files) {
            if (file.isDirectory()) {
                Log.d(TAG, "${indent}DIR:  ${file.name}/")
                listDirectory(file, "$indent  ")
            } else {
                Log.d(TAG, "${indent}FILE: ${file.name} (${file.length()} bytes)")
            }
        }
    }

    // Add this function to help debug the assets folder
    private fun debugAssets() {
        try {
            val assetManager = requireContext().assets
            Log.d(TAG, "=== Assets Debug Info ===")

            // List all files in assets root
            val rootFiles = assetManager.list("") ?: emptyArray()
            Log.d(TAG, "Assets root contains: ${rootFiles.joinToString(", ")}")

            // Check if our model folder exists
            val modelExists = rootFiles.contains(MODEL_NAME)
            Log.d(TAG, "Model folder '$MODEL_NAME' exists in assets: $modelExists")

            if (modelExists) {
                // List contents of model folder
                val modelFiles = assetManager.list(MODEL_NAME) ?: emptyArray()
                Log.d(TAG, "Model folder contains: ${modelFiles.joinToString(", ")}")

                // Check for graph subfolder
                if (modelFiles.contains("graph")) {
                    val graphFiles = assetManager.list("$MODEL_NAME/graph") ?: emptyArray()
                    Log.d(TAG, "Graph folder contains: ${graphFiles.joinToString(", ")}")
                }
            }

            Log.d(TAG, "=== End Assets Debug ===")
        } catch (e: Exception) {
            Log.e(TAG, "Error debugging assets: ${e.message}")
        }
    }

    private fun startRecognition() {
        model?.let { model ->
            try {
                // Create recognizer with the desired settings
                val recognizer = Recognizer(model, SAMPLE_RATE).apply {
                    setMaxAlternatives(1)
                    setWords(true)
                }

                speechService = SpeechService(recognizer, SAMPLE_RATE).apply {
                    startListening(this@VoskDialogFragment)
                }
                Log.d(TAG, "Speech recognition started")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start recognition: ${e.message}")
                viewModel.updateRecognizedText("Failed to start recognition", DisplayState.Text, vhalManager)
            }
        } ?: run {
            Log.e(TAG, "Cannot start recognition, model is null")
            viewModel.updateRecognizedText("Model not loaded", DisplayState.Text, vhalManager)
        }
    }

    private fun stopRecognition() {
        speechService?.stop()
        Log.d(TAG, "Speech recognition stopped")
    }

    // RecognitionListener implementation
    override fun onPartialResult(hypothesis: String?) {
        // Keep waveform visible during partial results
        // Don't show text for partial results
    }

    override fun onResult(hypothesis: String?) {
        onFinalResult(hypothesis)
    }

    override fun onFinalResult(hypothesis: String?) {
        viewModel.updateRecognizedText(
            hypothesis ?: "No text recognized",
            DisplayState.Text,
            vhalManager
        )
    }

    override fun onError(exception: Exception?) {
        Log.e(TAG, "Recognition error: ${exception?.message}")
        viewModel.updateRecognizedText(
            "Error: ${exception?.message}",
            DisplayState.Text,
            vhalManager
        )
    }

    override fun onTimeout() {
        Log.w(TAG, "Recognition timed out")
        viewModel.updateRecognizedText("Recognition timed out", DisplayState.Text, vhalManager)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        cleanup()
    }

    private fun cleanup() {
        recognizedTextDisplayJob?.cancel()
        speechService?.stop()
        speechService = null
        model?.close()
        model = null
        vhalManager?.cleanup()
        vhalManager = null
        prefs.unregisterOnSharedPreferenceChangeListener(ambientColorListener)
        Log.d(TAG, "Fragment cleanup completed")
    }
}