package com.guardian.mesh

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import com.guardian.mesh.crypto.KeyManager
import com.guardian.mesh.network.NetworkClient
import com.guardian.mesh.network.VerifyRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

class GuardianService : LifecycleService() {

    private lateinit var motionMonitor: MotionMonitor
    private lateinit var locationMonitor: LocationMonitor
    private lateinit var meshMonitor: MeshMonitor
    private lateinit var behavioralEngine: BehavioralEngine
    private lateinit var riskEngine: RiskEngine
    private lateinit var keyManager: KeyManager
    private lateinit var deviceId: String
    // Vision Enforcer (Passive Eyes)
    private lateinit var visionEnforcer: VisionEnforcer
    // Sentry (Brain) - Initialized in loop currently, could be property
    
    // Volatile Trust Metrics
    private var currentFaceConfidence: Float = 0.0f

    companion object {
        var activeRiskEngine: RiskEngine? = null
        var activeMeshMonitor: MeshMonitor? = null
    }

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    override fun onCreate() {
        super.onCreate()
        Log.d("GuardianService", "Service Created")
        startForegroundService()
        
        motionMonitor = MotionMonitor(this) {
            Log.e("GuardianService", "SHAKE DETECTED! DURESS!")
        }
        // Battery Optimization: Wake Eyes only on Motion
        motionMonitor.onMotionStateChange = { state ->
            if (state == MotionMonitor.MotionState.STATIONARY) {
                // Delay stop slightly to capture "setting down" face? 
                // For now, immediate stop to be battery aggressive as requested.
                // visionEnforcer.stopScanning() // FIXED: User reported issue holding phone still. Keeping scan active.
            } else {
                visionEnforcer.startScanning()
            }
        }

        locationMonitor = LocationMonitor(this)
        meshMonitor = MeshMonitor(this)
        behavioralEngine = BehavioralEngine(this)
        
        // Passive Vision Initialization
        visionEnforcer = VisionEnforcer(this, this)
        visionEnforcer.onFaceDetected = { count, isAlive ->
            // Update the volatile trust metric based on vision
            currentFaceConfidence = if (isAlive) 1.0f else if (count > 0) 0.5f else 0.0f
        }
        
        riskEngine = RiskEngine(motionMonitor, locationMonitor, meshMonitor, behavioralEngine)
        activeRiskEngine = riskEngine
        
        keyManager = KeyManager()
        deviceId = retrieveDeviceId() 

        motionMonitor.start()
        locationMonitor.start()
        meshMonitor.start()
        // visionEnforcer.startScanning() // Removed: Event-driven now
        
        startAuthLoop()
        startAgentLoop()
    }

    private fun startAgentLoop() {
        serviceScope.launch {
            // Sentry AI Initialization
            val sentryAI = SentryAI(riskEngine)
            
            while (isActive) {
                try {
                    val response = NetworkClient.authService.getPendingRequests().execute()
                    if (response.isSuccessful && response.body() != null) {
                        val requests = response.body()!!
                        for (req in requests) {
                            Log.d("GuardianService", "CLOUD AGENT: Request from ${req.source} for ${req.service}")
                            
                            // Wake Vision on Request (if not already acting)
                            visionEnforcer.startScanning()
                            delay(500) // Give camera a split second to warm up/capture if it was off
                            
                            // 1. Sentry Evaluation
                            // Use live Vision confidence + Context/Motion
                            val sentryState = sentryAI.evaluate(currentFaceConfidence)
                            
                            // 2. Check Device Trust (Pairing)
                            val trustedDevices = com.guardian.mesh.autofill.TrustedDeviceStore.getDevices(this@GuardianService)
                            val isTrustedDevice = trustedDevices.any { 
                                val stored = it.publicKey.replace("\\s".toRegex(), "")
                                val incoming = req.publicKey?.replace("\\s".toRegex(), "") ?: ""
                                if (stored.length < 50 || incoming.length < 50) return@any false
                                stored == incoming 
                            }
                            
                            Log.d("GuardianService", "Sentry State: $sentryState | Device Trusted: $isTrustedDevice")

                            // Special Service: Pairing Check
                            if (req.service == "pairing_check") {
                                Log.d("GuardianService", "CLOUD AGENT: Pairing Check from ${req.source}")
                                val status = if (isTrustedDevice) "PAIRED" else "UNPAIRED"
                                val responsePayload = "{\"status\": \"$status\"}"
                                
                                val agentResp = com.guardian.mesh.network.AgentResponse(req.requestId, responsePayload)
                                NetworkClient.authService.respondToRequest(agentResp).execute()
                                continue 
                            }
                            
                            // Special Service: OTP / Identity (Skip for brevity, keeping existing logic effectively or simplifying)
                            // For prototype, we apply Sentry Logic mainly to Credentials

                            // 3. Sentry Decision Policy
                            var matches = emptyList<Map<String, String>>()
                            var isDecoy = false

                            when (sentryState) {
                                SentryState.SAFE -> {
                                    // High Trust -> Access Real Vault
                                    if (isTrustedDevice) {
                                        Log.d("GuardianService", "SENTRY: SAFE. Configuring Real Credentials.")
                                        val creds = com.guardian.mesh.autofill.CredentialVault.getAllCredentials()
                                        matches = findMatches(creds, req.service)
                                    } else {
                                        Log.w("GuardianService", "SENTRY: SAFE but Device Unknown. Downgrading to Suspicious.")
                                        // Fallthrough logic handled by IF below? No, explicit handle.
                                        // We will Request Approval.
                                    }
                                }
                                SentryState.DANGER -> {
                                    // Duress -> Access Honeypot Vault
                                    Log.e("GuardianService", "SENTRY: DANGER! ENGAGING HONEYPOT PROTOCOL.")
                                    // We serve decoys REGARDLESS of device trust to deceive the attacker completely
                                    val decoys = com.guardian.mesh.autofill.HoneypotVault.getDecoy(req.service)
                                    matches = decoys.map { 
                                        mapOf("username" to it.username, "password" to it.password, "appName" to it.appName) 
                                    }
                                    isDecoy = true
                                    // TODO: Send silent SOS to backend
                                }
                                SentryState.SUSPICIOUS -> {
                                    Log.w("GuardianService", "SENTRY: SUSPICIOUS. Holding for approval.")
                                }
                            }
                            
                            // Execute Response
                            if (matches.isNotEmpty() && (sentryState == SentryState.SAFE || sentryState == SentryState.DANGER)) {
                                if (sentryState == SentryState.SAFE && !isTrustedDevice) {
                                    // Safe State but Untrusted Device -> Manual
                                    requestManualApproval(req, "New Device detected. Trust Score High but Verification Needed.")
                                } else {
                                    // Proceed with Auto-Fill (Real or Decoy)
                                    sendCredentials(req, matches)
                                    if (isDecoy) {
                                        Log.d("GuardianService", "SENTRY: Decoy sent. Threat Agent neutralized.")
                                    }
                                }
                            } else {
                                // Default / Suspicious -> Manual Approval
                                if (sentryState != SentryState.DANGER) { // Don't ask for approval in Danger, it reveals the trick!
                                    // But if we have NO decoys, what do we do? 
                                    // In Danger, if no decoys, we should probably timeout or send empty "Network Error"
                                    if (sentryState == SentryState.DANGER) {
                                        Log.e("GuardianService", "SENTRY: DANGER but no decoy found. Simulating timeout.")
                                    } else {
                                        requestManualApproval(req, "Sentry Check: ${sentryState.name}")
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("GuardianService", "Agent Loop Error: ${e.message}")
                }
                delay(3000) 
            }
        }
    }
    
    // Helper methods for cleanliness
    private fun findMatches(creds: List<com.guardian.mesh.autofill.Credential>, service: String): List<Map<String, String>> {
        val matches = mutableListOf<Map<String, String>>()
        for (c in creds) {
            if (c.appName.contains(service, ignoreCase = true) || c.packageName.contains(service, ignoreCase = true)) {
                matches.add(mapOf(
                    "username" to c.username,
                    "password" to c.password,
                    "appName" to c.appName
                ))
            }
        }
        return matches
    }
    
    private fun sendCredentials(req: com.guardian.mesh.network.AgentRequest, matches: List<Map<String, String>>) {
        try {
            val jsonBuilder = StringBuilder("[")
            matches.forEachIndexed { index, match ->
                jsonBuilder.append("{")
                jsonBuilder.append("\"username\":\"${match["username"]}\",")
                jsonBuilder.append("\"password\":\"${match["password"]}\",")
                jsonBuilder.append("\"appName\":\"${match["appName"]}\"")
                jsonBuilder.append("}")
                if (index < matches.size - 1) jsonBuilder.append(",")
            }
            jsonBuilder.append("]")
            
            val payload = jsonBuilder.toString()
            val finalCredentials = if (req.publicKey != null && req.publicKey.isNotEmpty()) {
                encryptPayload(payload, req.publicKey)
            } else {
                payload
            }

            val agentResp = com.guardian.mesh.network.AgentResponse(req.requestId, finalCredentials)
            NetworkClient.authService.respondToRequest(agentResp).execute()
            Log.d("GuardianService", "CLOUD AGENT: Sent Credentials for ${req.requestId}")
        } catch (e: Exception) {
            Log.e("GuardianService", "Failed to send creds: ${e.message}")
        }
    }
    
    private fun requestManualApproval(req: com.guardian.mesh.network.AgentRequest, reason: String) {
        Log.w("GuardianService", "Requesting User Approval. Reason: $reason")
        
        val intent = Intent(this@GuardianService, com.guardian.mesh.ui.AuthRequestActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("requestId", req.requestId)
            putExtra("service", req.service)
            putExtra("source", req.source)
            putExtra("publicKey", req.publicKey)
            putExtra("trustDetails", reason)
        }
        
        val pendingIntent = android.app.PendingIntent.getActivity(
            this@GuardianService, 
            req.requestId.hashCode(), 
            intent, 
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        val notification = NotificationCompat.Builder(this@GuardianService, "GuardianServiceChannel")
            .setContentTitle("Login Request: ${req.service}")
            .setContentText(reason)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
            
        getSystemService(NotificationManager::class.java).notify(req.requestId.hashCode(), notification)
    }

    // Helper for E2EE (Unchanged)
    private fun encryptPayload(data: String, publicKeyPem: String): String {
        try {
            val pemContent = publicKeyPem
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replace("\\s".toRegex(), "") 

            val keyBytes = android.util.Base64.decode(pemContent, android.util.Base64.DEFAULT)
            val spec = java.security.spec.X509EncodedKeySpec(keyBytes)
            val keyFactory = java.security.KeyFactory.getInstance("RSA")
            val publicKey = keyFactory.generatePublic(spec)

            val cipher = javax.crypto.Cipher.getInstance("RSA/ECB/OAEPWithSHA-1AndMGF1Padding")
            cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, publicKey)
            
            val encryptedBytes = cipher.doFinal(data.toByteArray(Charsets.UTF_8))
            return android.util.Base64.encodeToString(encryptedBytes, android.util.Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.e("GuardianService", "Crypto Error: ${e.message}")
            throw e
        }
    }
    
    private fun startAuthLoop() {
        serviceScope.launch {
            while (isActive) {
                try {
                    val response = NetworkClient.authService.getChallenge().execute()
                    if (response.isSuccessful && response.body() != null) {
                        val challengeResponse = response.body()!!

                        val signature = keyManager.signData(challengeResponse.challenge)
                        val trustMetric = riskEngine.calculateTrustScore(currentFaceConfidence) // Background check using LIVE confidence
                        val publicKey = keyManager.getPublicKeyPEM()

                        if (signature != null) {
                            val verifyCall = NetworkClient.authService.verify(
                                VerifyRequest(
                                    deviceId = deviceId,
                                    publicKey = publicKey,
                                    challenge = challengeResponse.challenge,
                                    signature = signature,
                                    riskScore = trustMetric.totalScore // Using "riskScore" field to transport Trust Score for now
                                )
                            )
                            val verifyResponse = verifyCall.execute()
                            
                            if (verifyResponse.isSuccessful) {
                                Log.d("GuardianService", "AUTH SUCCESS! Device Trusted. Score: ${trustMetric.totalScore}")
                            } else {
                                Log.e("GuardianService", "AUTH FAILED: ${verifyResponse.code()}")
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("GuardianService", "Auth Loop Error: ${e.message}")
                }
                delay(10000) 
            }
        }
    }

    private fun retrieveDeviceId(): String {
        return android.provider.Settings.Secure.getString(
            contentResolver,
            android.provider.Settings.Secure.ANDROID_ID
        ) ?: UUID.randomUUID().toString()
    }

    override fun onDestroy() {
        super.onDestroy()
        motionMonitor.stop()
        locationMonitor.stop()
        meshMonitor.stop()
        activeRiskEngine = null
        activeMeshMonitor = null
        serviceJob.cancel()
    }

    override fun onBind(intent: Intent): IBinder? {
        return super.onBind(intent)
    }

    private fun startForegroundService() {
        val channelId = "GuardianServiceChannel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Guardian Mesh Service",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Guardian Mesh Active")
            .setContentText("Protecting your identity in the background")
            .setSmallIcon(android.R.drawable.ic_secure)
            .build()

        startForeground(1, notification)
    }
}
