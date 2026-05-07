package com.example.signdecipher

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.mediapipe.tasks.vision.core.RunningMode
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraActivity : AppCompatActivity(), GestureRecognizerHelper.GestureListener {
    private val TAG = "CameraActivity"
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var viewFinder: PreviewView
    private lateinit var overlayView: OverlayView
    private lateinit var resultTextView: TextView
    private var preview: Preview? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var cameraFacing = CameraSelector.LENS_FACING_BACK
    private lateinit var gestureRecognizerHelper: GestureRecognizerHelper

    private val activityResultLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                Log.d(TAG, "Camera permission granted")
                startCamera()
            } else {
                val message = "Camera permission is required to use this feature"
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                Log.e(TAG, message)
                finish()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val rootLayout = FrameLayout(this)
        rootLayout.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        viewFinder = PreviewView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
        rootLayout.addView(viewFinder)

        overlayView = OverlayView(this, null).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        rootLayout.addView(overlayView)

        resultTextView = TextView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                setMargins(0, 0, 0, 100)
            }
            textSize = 32f
            setTextColor(Color.WHITE)
            setShadowLayer(4f, 0f, 0f, Color.BLACK)
            text = "Waiting for gesture..."
        }
        rootLayout.addView(resultTextView)

        setContentView(rootLayout)
        
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val windowInsetsController = WindowInsetsControllerCompat(window, viewFinder)
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
        windowInsetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        cameraExecutor = Executors.newSingleThreadExecutor()

        viewFinder.post {
            setUpGestureRecognizer()
        }
    }

    private fun setUpGestureRecognizer() {
        gestureRecognizerHelper = GestureRecognizerHelper(
            context = this,
            runningMode = RunningMode.LIVE_STREAM,
            minHandDetectionConfidence = 0.5f,
            minHandTrackingConfidence = 0.5f,
            minHandPresenceConfidence = 0.5f,
            currentDelegate = GestureRecognizerHelper.DELEGATE_GPU,
            gestureRecognizerListener = this
        )
    }

    override fun onResume() {
        super.onResume()
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestCameraPermission()
        }
    }

    override fun onPause() {
        super.onPause()
        if (this::gestureRecognizerHelper.isInitialized) {
            gestureRecognizerHelper.clearGestureRecognizer()
        }
    }

    private fun allPermissionsGranted() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    private fun requestCameraPermission() {
        if (allPermissionsGranted()) {
            Log.d(TAG, "Camera permission already granted")
            startCamera()
        } else {
            Log.d(TAG, "Requesting camera permission")
            activityResultLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startCamera() {
        Log.d(TAG, "Starting camera...")
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                
                preview = Preview.Builder()
                    .setTargetAspectRatio(AspectRatio.RATIO_16_9)
                    .setTargetRotation(viewFinder.display?.rotation ?: android.view.Surface.ROTATION_0)
                    .build()
                    .also {
                        it.setSurfaceProvider(viewFinder.surfaceProvider)
                    }

                val cameraSelector = CameraSelector.Builder()
                    .requireLensFacing(cameraFacing)
                    .build()

                imageAnalyzer =
                    ImageAnalysis.Builder()
                        .setTargetAspectRatio(AspectRatio.RATIO_16_9)
                        .setTargetRotation(viewFinder.display.rotation)
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                        .build()
                        .also {
                            it.setAnalyzer(cameraExecutor) { image ->
                                if (!::gestureRecognizerHelper.isInitialized) {
                                     setUpGestureRecognizer()
                                }
                                gestureRecognizerHelper.recognizeLiveStream(
                                    imageProxy = image,
                                    isFrontCamera = cameraFacing == CameraSelector.LENS_FACING_FRONT
                                )
                            }
                        }

                try {
                    cameraProvider?.unbindAll()

                    camera = cameraProvider?.bindToLifecycle(
                        this, cameraSelector, preview, imageAnalyzer
                    )
                    
                    Log.d(TAG, "Camera successfully started")

                } catch (exc: Exception) {
                    Log.e(TAG, "Use case binding failed", exc)
                }
            } catch (exc: Exception) {
                Log.e(TAG, "Camera initialization failed", exc)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        gestureRecognizerHelper.clearGestureRecognizer()
    }

    override fun onError(error: String, errorCode: Int) {
        runOnUiThread {
            Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResults(resultBundle: GestureRecognizerHelper.ResultBundle) {
        runOnUiThread {
            if (resultBundle.results.isNotEmpty()) {
                val result = resultBundle.results[0]
                
                // Show gesture name
                if (result.gestures().isNotEmpty()) {
                     val topGesture = result.gestures()[0][0]
                     val categoryName = topGesture.categoryName()
                     val score = topGesture.score()
                     
                     if (categoryName != "None" && score > 0.5) {
                         resultTextView.text = "$categoryName"
                     } else {
                         resultTextView.text = "No Gesture Detected"
                     }
                } else {
                    resultTextView.text = "No Hand Detected"
                }
                
                // Show landmarks
                overlayView.setResults(
                    result,
                    resultBundle.inputImageHeight,
                    resultBundle.inputImageWidth,
                    RunningMode.LIVE_STREAM
                )
            } else {
                overlayView.clear()
            }
        }
    }
}
