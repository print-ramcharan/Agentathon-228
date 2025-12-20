package com.guardian.mesh

import android.content.Context
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class VisionEnforcer(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner
) {
    
    // Callback to send results back to GuardianService
    var onFaceDetected: ((faceCount: Int, livenessHigh: Boolean) -> Unit)? = null
    
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val faceDetector by lazy {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL) // For Eyes Open
            .build()
        FaceDetection.getClient(options)
    }

    private var isScanning = false

    fun startScanning() {
        if (isScanning) return
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                bindCameraUseCases(cameraProvider)
                isScanning = true
                Log.d("VisionEnforcer", "Camera Scanning Started")
            } catch (exc: Exception) {
                Log.e("VisionEnforcer", "Use case binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun stopScanning() {
        // We probably don't want to completely unbind if we want quick resume, 
        // but for battery we should.
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                cameraProvider.unbindAll()
                isScanning = false
                Log.d("VisionEnforcer", "Camera Scanning Stopped")
            } catch (exc: Exception) {
                Log.e("VisionEnforcer", "Unbinding failed", exc)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun bindCameraUseCases(cameraProvider: ProcessCameraProvider) {
        val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

        val imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()

        imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
            analyzeFace(imageProxy)
        }

        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                imageAnalysis
            )
        } catch (exc: Exception) {
            Log.e("VisionEnforcer", "Binding failed. Camera might be unavailable in background.", exc)
        }
    }

    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    private fun analyzeFace(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            
            faceDetector.process(image)
                .addOnSuccessListener { faces ->
                    processFaces(faces)
                    imageProxy.close()
                }
                .addOnFailureListener { e ->
                    // Log.e("VisionEnforcer", "Face detection failed", e)
                    imageProxy.close()
                }
        } else {
            imageProxy.close()
        }
    }

    private fun processFaces(faces: List<Face>) {
        if (faces.isEmpty()) {
            onFaceDetected?.invoke(0, false)
            return
        }

        // Check for Liveness (Eyes Open)
        // We assume the largest face is the User
        val userFace = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }
        
        var isAlive = false
        if (userFace != null) {
            val leftEye = userFace.leftEyeOpenProbability ?: 0.0f
            val rightEye = userFace.rightEyeOpenProbability ?: 0.0f
            val smile = userFace.smilingProbability ?: 0.0f
            
            // "Liveness": Eyes open > 0.4 OR Smiling > 0.5
            // Low threshold because looking down at phone might narrow eyes
            if (leftEye > 0.4f || rightEye > 0.4f || smile > 0.5f) {
                isAlive = true
            }
        }
        
        // Log.d("VisionEnforcer", "Faces: ${faces.size}, Liveness: $isAlive")
        onFaceDetected?.invoke(faces.size, isAlive)
    }
}
