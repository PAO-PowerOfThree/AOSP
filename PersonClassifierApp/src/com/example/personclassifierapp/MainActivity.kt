package com.example.personclassifierapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.opencv.android.CameraBridgeViewBase
import org.opencv.android.JavaCameraView
import org.opencv.android.OpenCVLoader
import org.opencv.imgproc.Imgproc
import org.opencv.core.Mat
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class MainActivity : AppCompatActivity(), CameraBridgeViewBase.CvCameraViewListener2 {

    private lateinit var genderDetectionTextView: TextView
    private var mOpenCvCameraView: JavaCameraView? = null
    private val nativeClassifier = YourNativeClass()

    // Averaging detection
    private val resultsList = mutableListOf<String>()
    private val maxDetections = 10
    private var hasNavigated = false

    private val cameraPermissionRequestCode = 1001
    private var cameraAvailable = false

    companion object {
        private const val TAG = "PersonClassifier"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        Log.d(TAG, "MainActivity onCreate started")

        genderDetectionTextView = findViewById(R.id.gender_detection_text)
        mOpenCvCameraView = findViewById(R.id.camera_view)

        // Check if the device actually has a camera
        cameraAvailable = packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
        Log.d(TAG, "Camera available: $cameraAvailable")

        // Initialize OpenCV
        if (!OpenCVLoader.initDebug()) {
            Log.e(TAG, "OpenCV initialization failed!")
            genderDetectionTextView.text = "Error: OpenCV initialization failed"
            return
        } else {
            Log.d(TAG, "OpenCV initialization successful")
        }

        // Load native library
        try {
            System.loadLibrary("person_classifier_native")
            Log.d(TAG, "Native lib loaded successfully")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to load JNI lib: ${e.message}", e)
            genderDetectionTextView.text = "Error: Failed to load native library"
            return
        }

        // Initialize in background thread
        Thread {
            initializeModels()
        }.start()

        genderDetectionTextView.text = "Initializing models..."

        // If camera is available, request permission or start camera
        if (cameraAvailable) {
            if (hasCameraPermission()) {
                setupCamera()
            } else {
                requestCameraPermission()
            }
        } else {
            // No camera? Show placeholder and run offline test
            genderDetectionTextView.text = "No camera detected (running in emulator?)"
            findViewById<ImageView>(R.id.static_image_view)?.apply {
                setImageResource(R.drawable.sample_face) // Put a test face in drawable
                visibility = ImageView.VISIBLE
            }
        }
    }

    private fun initializeModels() {
        Log.d(TAG, "Starting model initialization...")
        
        // Prepare model files
        val modelDir = "${filesDir.absolutePath}/models/"
        val dir = File(modelDir)
        if (!dir.exists()) {
            Log.d(TAG, "Creating models directory: ${dir.absolutePath}")
            dir.mkdirs()
        }

        val modelFiles = arrayOf(
            "age_deploy.prototxt",
            "age_net.caffemodel",
            "deploy.prototxt.txt",
            "res10_300x300_ssd_iter_140000_fp16.caffemodel",
            "gender_deploy.prototxt",
            "gender_net.caffemodel"
        )

        // Copy model files
        for (fileName in modelFiles) {
            val destPath = modelDir + fileName
            if (!File(destPath).exists()) {
                Log.d(TAG, "Copying $fileName...")
                copyAssetFile(fileName, destPath)
            } else {
                Log.d(TAG, "Model file already exists: $fileName")
            }
        }

        try {
            // Initialize classifier
            nativeClassifier.initClassifier(
                modelDir + "deploy.prototxt.txt",
                modelDir + "res10_300x300_ssd_iter_140000_fp16.caffemodel",
                modelDir + "age_deploy.prototxt",
                modelDir + "age_net.caffemodel",
                modelDir + "gender_deploy.prototxt",
                modelDir + "gender_net.caffemodel"
            )
            
            Log.d(TAG, "Native classifier initialized successfully")
            
            runOnUiThread {
                genderDetectionTextView.text = "Models loaded. Camera ready."
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize classifier: ${e.message}", e)
            runOnUiThread {
                genderDetectionTextView.text = "Error initializing models: ${e.message}"
            }
        }
    }

    private fun setupCamera() {
        Log.d(TAG, "Setting up camera")
        mOpenCvCameraView?.apply {
            visibility = CameraBridgeViewBase.VISIBLE
            setCvCameraViewListener(this@MainActivity)
            setCameraPermissionGranted()  // Inform view that permission is granted
            setCameraIndex(CameraBridgeViewBase.CAMERA_ID_ANY) // Allow any camera - this is key!
            enableFpsMeter()  // Enable FPS meter for debugging
            enableView()
        }
    }

    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestCameraPermission() {
        Log.d(TAG, "Requesting camera permission")
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.CAMERA),
            cameraPermissionRequestCode
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == cameraPermissionRequestCode &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            Log.d(TAG, "Camera permission granted by user")
            mOpenCvCameraView?.setCameraPermissionGranted()  // Inform view immediately after grant
            setupCamera()
        } else {
            Log.e(TAG, "Camera permission denied by user")
            genderDetectionTextView.text = "Camera permission denied"
        }
    }

    override fun onCameraViewStarted(width: Int, height: Int) {
        Log.d(TAG, "Camera view started successfully! Resolution: ${width}x${height}")
        mOpenCvCameraView?.setMaxFrameSize(width, height)
        runOnUiThread {
            genderDetectionTextView.text = "Camera started (${width}x${height}). Looking for faces..."
        }
    }

    override fun onCameraViewStopped() {
        Log.d(TAG, "Camera view stopped")
        runOnUiThread {
            genderDetectionTextView.text = "Camera stopped."
        }
    }

    override fun onCameraFrame(inputFrame: CameraBridgeViewBase.CvCameraViewFrame): Mat {
        val rgba = inputFrame.rgba()

        if (hasNavigated) {
            return rgba
        }

        try {
            // Convert to BGR (3 channels) for model compatibility
            val bgr = Mat()
            Imgproc.cvtColor(rgba, bgr, Imgproc.COLOR_RGBA2BGR)

            val result = nativeClassifier.processFrame(bgr.nativeObjAddr)

            runOnUiThread {
                if (result.isNotEmpty() && !hasNavigated) {
                    resultsList.add(result)

                    // Show progress to user
                    genderDetectionTextView.text = "Detecting... ${resultsList.size}/$maxDetections"

                    // Once we have exactly 10 results
                    if (resultsList.size == maxDetections) {
                        val finalDecision = resultsList
                            .groupingBy { it }
                            .eachCount()
                            .maxByOrNull { it.value }?.key ?: ""

                        genderDetectionTextView.text = "Final Decision: $finalDecision"

                        // Navigate based on final decision
                        when {
                            finalDecision.contains("Man", ignoreCase = true) -> {
                                startActivity(Intent(this@MainActivity, ManActivity::class.java))
                            }
                            finalDecision.contains("Woman", ignoreCase = true) -> {
                                startActivity(Intent(this@MainActivity, WomanActivity::class.java))
                            }
                            finalDecision.contains("Boy", ignoreCase = true) -> {
                                startActivity(Intent(this@MainActivity, BoyActivity::class.java))
                            }
                            finalDecision.contains("Girl", ignoreCase = true) -> {
                                startActivity(Intent(this@MainActivity, GirlActivity::class.java))
                            }
                        }

                        hasNavigated = true // Prevent further navigation
                    }
                } else if (result.isEmpty() && !hasNavigated) {
                    genderDetectionTextView.text = "Camera working - No faces detected"
                }
            }

            Log.d("NativeClassifier", result)
            
            // Convert back to RGBA for preview display
            val output = Mat()
            Imgproc.cvtColor(bgr, output, Imgproc.COLOR_BGR2RGBA)
            
            // Clean up intermediate Mat
            bgr.release()

            return output
            
        } catch (e: Exception) {
            Log.e(TAG, "Frame processing error: ${e.message}", e)
            return rgba
        }
    }

    private fun copyAssetFile(assetFileName: String, destinationPath: String) {
        try {
            assets.open(assetFileName).use { inputStream ->
                FileOutputStream(destinationPath).use { outputStream ->
                    val buffer = ByteArray(1024)
                    var read: Int
                    while (true) {
                        read = inputStream.read(buffer)
                        if (read == -1) break
                        outputStream.write(buffer, 0, read)
                    }
                }
            }
            Log.d(TAG, "Copied asset file: $assetFileName")
        } catch (e: IOException) {
            Log.e(TAG, "Failed to copy asset file: $assetFileName", e)
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume")
        if (cameraAvailable && hasCameraPermission()) {
            mOpenCvCameraView?.enableView()
        }
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "onPause")
        mOpenCvCameraView?.disableView()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy")
        mOpenCvCameraView?.disableView()
    }
}
