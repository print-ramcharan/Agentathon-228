package com.guardian.mesh

import android.util.Log

class RiskEngine(
    private val motionMonitor: MotionMonitor,
    private val locationMonitor: LocationMonitor,
    private val meshMonitor: MeshMonitor,
    private val behavioralEngine: BehavioralEngine
) {

    data class TrustScore(
        val totalScore: Float, // 0.0 to 1.0 (1.0 = High Trust)
        val details: String    // Debug string for UI/Logs
    )

    fun calculateTrustScore(faceConfidence: Float): TrustScore {
        // Base Score from Biometrics (Max 0.6)
        // If Face is high confidence (e.g. > 0.9), we give full 0.6.
        // If Face is absent (0.0), we rely purely on context (Max 0.4) -> Partial Trust
        var score = (faceConfidence * 0.6f).coerceAtMost(0.6f)
        val sb = StringBuilder()
        sb.append("Face: ${(faceConfidence * 100).toInt()}% ")

        // 1. Behavioral Context (Max 0.2)
        // includes Time of Day and Location familiarity
        val location = locationMonitor.getLastLocation()
        if (location != null) {
            behavioralEngine.learnLocation(location) // Constant learning
        }
        
        val contextSignal = behavioralEngine.getTrustSignal(location) // 0.0 to 1.0
        val contextScore = contextSignal * 0.2f
        score += contextScore
        sb.append("| Context: ${(contextSignal * 100).toInt()}% ")

        // 2. Motion Context (Max 0.1)
        val motionState = motionMonitor.currentMotionState
        val motionScore = when (motionState) {
            MotionMonitor.MotionState.STATIONARY -> 0.1f // Stable, likely user holding it
            MotionMonitor.MotionState.MOVING -> 0.05f    // Walking, neutral
            MotionMonitor.MotionState.HIGH_VELOCITY -> 0.0f // Driving/Running, risky
        }
        score += motionScore
        sb.append("| Motion: $motionState ")

        // 3. Mesh Context (Max 0.1)
        // Nearby trusted devices boost confidence
        val neighbors = meshMonitor.getNeighborCount()
        val meshScore = if (neighbors > 0) 0.1f else 0.0f
        score += meshScore
        sb.append("| Mesh: $neighbors ")

        val finalScore = score.coerceIn(0.0f, 1.0f)
        Log.d("RiskEngine", "Calculated Trust Score: $finalScore. Details: $sb")
        
        return TrustScore(finalScore, sb.toString())
    }
}
