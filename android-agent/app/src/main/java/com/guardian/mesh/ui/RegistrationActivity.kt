package com.guardian.mesh.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.*
import com.guardian.mesh.R
import com.guardian.mesh.autofill.CredentialVault
import com.guardian.mesh.crypto.IdentityManager
import com.guardian.mesh.ui.LivenessOverlayView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject
import java.util.concurrent.Executors

class RegistrationActivity : AppCompatActivity() {

    private val identityManager = IdentityManager()
    private var imageCapture: ImageCapture? = null
    private lateinit var livenessOverlay: LivenessOverlayView
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private var cameraProvider: ProcessCameraProvider? = null
    private var faceLivenessAnalyzer: FaceLivenessAnalyzer? = null

    // Liveness State Machine
    private enum class LivenessState {
        CENTER, TURN_RIGHT, TURN_LEFT, BLINK, DONE
    }
    private var currentState = LivenessState.CENTER
    private var isProcessing = false

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            startCamera()
        } else {
            Toast.makeText(this, "Camera permission required for identity", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_registration)

        livenessOverlay = findViewById(R.id.livenessOverlay)

        // Camera started only in Step 2

        val emailInput = findViewById<EditText>(R.id.inputEmail)
        val passwordInput = findViewById<EditText>(R.id.inputPassword)
        val phoneInput = findViewById<EditText>(R.id.inputPhone)
        val btnNext = findViewById<Button>(R.id.btnNext)
        val textSignIn = findViewById<android.widget.TextView>(R.id.textSignIn)
        val formContainer = findViewById<android.view.View>(R.id.formContainer)
        val cameraContainer = findViewById<android.view.View>(R.id.cameraContainer)

        textSignIn.setOnClickListener {
            // Navigate to Login
            val intent = android.content.Intent(this, LoginActivity::class.java)
            startActivity(intent)
            finish()
        }

        btnNext.setOnClickListener {
            val email = emailInput.text.toString()
            val password = passwordInput.text.toString()
            val rawPhone = phoneInput.text.toString()
            
            // Hardcode Prefix
            val phone = "+91$rawPhone"
            
            if (email.isBlank() || password.isBlank() || rawPhone.isBlank()) {
                Toast.makeText(this, "Please fill in all details", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 1. Save Default Identity
            if (email.isNotBlank()) CredentialVault.defaultEmail = email
            if (phone.isNotBlank()) CredentialVault.defaultPhone = phone
            
            // 2. Switch to Camera Step
            formContainer.visibility = android.view.View.GONE
            cameraContainer.visibility = android.view.View.VISIBLE
            
            // 3. Start Camera & Liveness
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                startCamera()
            } else {
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
            
            currentState = LivenessState.CENTER
            livenessOverlay.instruction = "Center your face"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopCamera()
        cameraExecutor.shutdown()
    }

    private fun stopCamera() {
        try {
            cameraProvider?.unbindAll()
            faceLivenessAnalyzer?.detector?.close()
        } catch (e: Exception) {
            Log.e("Registration", "Error stopping camera", e)
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(findViewById<androidx.camera.view.PreviewView>(R.id.viewFinder).surfaceProvider)
            }
            
            imageCapture = ImageCapture.Builder().build()
            
            faceLivenessAnalyzer = FaceLivenessAnalyzer()
            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, faceLivenessAnalyzer!!)
                }

            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            try {
                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(this, cameraSelector, preview, imageCapture, imageAnalysis)
            } catch (exc: Exception) {
                Log.e("Registration", "Use case binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private inner class FaceLivenessAnalyzer : ImageAnalysis.Analyzer {
        val detector = FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .enableTracking()
                .build()
        )

        @androidx.annotation.OptIn(ExperimentalGetImage::class)
        override fun analyze(imageProxy: ImageProxy) {
            if (isDestroyed || isFinishing || cameraExecutor.isShutdown) {
                imageProxy.close()
                return
            }

            val mediaImage = imageProxy.image
            if (mediaImage != null) {
                val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                
                detector.process(image)
                    .addOnSuccessListener { faces ->
                        if (!isDestroyed && !isFinishing && faces.isNotEmpty()) {
                            processFace(faces[0])
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.e("Liveness", "Face detection failed", e)
                    }
                    .addOnCompleteListener {
                        imageProxy.close()
                    }
            } else {
                imageProxy.close()
            }
        }

        private fun processFace(face: Face) {
            if (isProcessing || currentState == LivenessState.DONE) return

            val yaw = face.headEulerAngleY // Left/Right
            val pitch = face.headEulerAngleX // Up/Down
            val leftEye = face.leftEyeOpenProbability ?: 1.0f
            val rightEye = face.rightEyeOpenProbability ?: 1.0f

            runOnUiThread {
                if (isDestroyed || isFinishing) return@runOnUiThread
                when (currentState) {
                    LivenessState.CENTER -> {
                        // Relaxed threshold: 20 degrees (Very forgiving)
                        if (kotlin.math.abs(yaw) < 20 && kotlin.math.abs(pitch) < 20) {
                            currentState = LivenessState.TURN_RIGHT
                            livenessOverlay.instruction = "Turn Head Right ->"
                        } else {
                            livenessOverlay.instruction = "Center Face"
                        }
                    }
                    LivenessState.TURN_RIGHT -> {
                        if (yaw < -20) { 
                            currentState = LivenessState.TURN_LEFT
                            livenessOverlay.instruction = "<- Turn Head Left"
                        }
                    }
                    LivenessState.TURN_LEFT -> {
                        if (yaw > 20) {
                            currentState = LivenessState.BLINK
                            livenessOverlay.instruction = "Blink Eyes (-_-)"
                        }
                    }
                    LivenessState.BLINK -> {
                        if (leftEye < 0.1 && rightEye < 0.1) {
                            currentState = LivenessState.DONE
                            livenessOverlay.instruction = "Processing Biometrics..."
                            isProcessing = true
                            
                            findViewById<android.view.View>(R.id.progressBar).visibility = android.view.View.VISIBLE
                            
                            // Capture Bitmap from Preview
                            val previewView = findViewById<androidx.camera.view.PreviewView>(R.id.viewFinder)
                            val fullBitmap = previewView.bitmap
                            if (fullBitmap != null) {
                                // Crop the center oval region (approx where user face is)
                                // LivenessOverlay uses 0.35 width radius (0.7 w) and 0.3 height radius (0.6 h)
                                // centered at 0.5, 0.5
                                // Rect: [0.15w, 0.20h, 0.85w, 0.80h]
                                
                                val w = fullBitmap.width
                                val h = fullBitmap.height
                                val cropX = (w * 0.15).toInt()
                                val cropY = (h * 0.20).toInt()
                                val cropW = (w * 0.70).toInt()
                                val cropH = (h * 0.60).toInt()
                                
                                // Ensure bounds
                                val safeX = cropX.coerceAtLeast(0)
                                val safeY = cropY.coerceAtLeast(0)
                                val safeW = cropW.coerceAtMost(w - safeX)
                                val safeH = cropH.coerceAtMost(h - safeY)
                                
                                val cropped = Bitmap.createBitmap(fullBitmap, safeX, safeY, safeW, safeH)
                                livenessOverlay.lastDetectedFaceBitmap = cropped
                            }
                            
                            findViewById<android.widget.TextView>(R.id.textStatus).text = "Capturing Selfie..."
                            
                            // Capture and Register
                            val email = findViewById<EditText>(R.id.inputEmail).text.toString()
                            val password = findViewById<EditText>(R.id.inputPassword).text.toString()
                            val phone = findViewById<EditText>(R.id.inputPhone).text.toString()
                            takePhotoAndRegister(email, password, phone)
                        }
                    }
                    else -> {}
                }
            }
        }
    }

    private fun takePhotoAndRegister(email: String, password: String, phone: String) {
        val imageCapture = imageCapture ?: return

        imageCapture.takePicture(
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: androidx.camera.core.ImageProxy) {
                    runOnUiThread { findViewById<android.widget.TextView>(R.id.textStatus).text = "Processing Image..." }
                    
                    // Convert to Bitmap/Bytes
                    val buffer = image.planes[0].buffer
                    val bytes = ByteArray(buffer.remaining())
                    buffer.get(bytes)
                    image.close()

                    // Process in background
                    lifecycleScope.launch(Dispatchers.IO) {
                        registerIdentity(email, password, phone, bytes)
                    }
                }

                override fun onError(exc: ImageCaptureException) {
                    Log.e("Registration", "Photo capture failed: ${exc.message}", exc)
                    runOnUiThread { 
                        Toast.makeText(this@RegistrationActivity, "Capture Failed", Toast.LENGTH_SHORT).show()
                        isProcessing = false 
                        findViewById<android.view.View>(R.id.progressBar).visibility = android.view.View.GONE
                    }
                }
            }
        )
    }

    private suspend fun registerIdentity(email: String, password: String, phone: String, faceBytes: ByteArray) {
        try {
            withContext(Dispatchers.Main) { findViewById<android.widget.TextView>(R.id.textStatus).text = "Generating Secure Keys..." }
            
            // A. Generate Keys
            // Delete any existing key to ensure we regenerate with correct flags
            identityManager.deleteKey()
            identityManager.generateIdentityKey()
            val pubKey = identityManager.getPublicKey() ?: return

            // Pre-calculate faceBase64 as it's needed for embedding and signing
            val faceBase64 = Base64.encodeToString(faceBytes, Base64.NO_WRAP)

            // B. Generate Face Embedding
            val faceBitmap = livenessOverlay.lastDetectedFaceBitmap // We need to capture this
            val embedding = if (faceBitmap != null) {
                com.guardian.mesh.ml.FaceRecognitionProcessor(this).getFaceEmbedding(faceBitmap)
            } else {
                FloatArray(192) // Empty fallback
            }

            // C. Sign Data
            withContext(Dispatchers.Main) { findViewById<android.widget.TextView>(R.id.textStatus).text = "Signing Identity..." }
            val signatureObj = identityManager.createSignatureObject()
            val signature = if (signatureObj != null) {
                signatureObj.update(faceBase64.toByteArray())
                Base64.encodeToString(signatureObj.sign(), Base64.NO_WRAP)
            } else {
                "simulate_sig"
            }

            // D. Send to Backend
            withContext(Dispatchers.Main) { findViewById<android.widget.TextView>(R.id.textStatus).text = "Uploading to Server..." }
            
            val json = JSONObject()
            json.put("email", email)
            json.put("password", password)
            json.put("mobile", phone)
            json.put("publicKey", pubKey)
            json.put("faceData", faceBase64)
            json.put("signature", signature)
            
            // Add Embedding to JSON
            val embeddingJson = org.json.JSONArray()
            for (f in embedding) embeddingJson.put(f.toDouble())
            json.put("faceEmbedding", embeddingJson)

            val url = URL("https://unexempt-danial-unousted.ngrok-free.dev/register")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.connectTimeout = 5000 // 5s timeout
            connection.readTimeout = 5000
            
            connection.outputStream.use { it.write(json.toString().toByteArray()) }
            
            val responseCode = connection.responseCode
            withContext(Dispatchers.Main) {
                if (responseCode == 200) {
                    findViewById<android.widget.TextView>(R.id.textStatus).text = "Success!"
                    Toast.makeText(this@RegistrationActivity, "Identity Verified & Registered! ✅", Toast.LENGTH_LONG).show()
                    
                    // Save User Session (Persistent)
                    val prefs = getSharedPreferences("user_session", android.content.Context.MODE_PRIVATE)
                    prefs.edit()
                        .putString("logged_in_email", email)
                        .putString("logged_in_mobile", phone)
                        .apply()
                    
                    // Navigate to Main Dashboard
                    val intent = android.content.Intent(this@RegistrationActivity, com.guardian.mesh.MainActivity::class.java)
                    intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                } else {
                    findViewById<android.widget.TextView>(R.id.textStatus).text = "Server Error: $responseCode"
                    Toast.makeText(this@RegistrationActivity, "Server Error: $responseCode", Toast.LENGTH_LONG).show()
                    isProcessing = false
                    findViewById<android.view.View>(R.id.progressBar).visibility = android.view.View.GONE
                }
            }
        } catch (e: Exception) {
            Log.e("Registration", "Error", e)
            withContext(Dispatchers.Main) {
                findViewById<android.widget.TextView>(R.id.textStatus).text = "Error: ${e.message}"
                Toast.makeText(this@RegistrationActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                isProcessing = false
                findViewById<android.view.View>(R.id.progressBar).visibility = android.view.View.GONE
            }
        }
    }
}
