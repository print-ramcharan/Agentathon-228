package com.guardian.mesh

import android.util.Log

enum class SentryState {
    SAFE,       // Normal operation
    SUSPICIOUS, // Ask for step-up (Scanning needed)
    DANGER      // Duress / Kidnapping Mode (Honeypot)
}

class SentryAI(private val riskEngine: RiskEngine) {
    
    private var currentState: SentryState = SentryState.SAFE
    
    fun evaluate(faceConfidence: Float): SentryState {
        val trustMetric = riskEngine.calculateTrustScore(faceConfidence)
        val score = trustMetric.totalScore
        val details = trustMetric.details
        
        currentState = when {
            // Immediate Duress Triggers
            details.contains("Motion: HIGH_VELOCITY") -> SentryState.DANGER
            // details.contains("PanicGesture") -> SentryState.DANGER // Future
            
            // Standard Trust Tiers
            score > 0.8 -> SentryState.SAFE
            score > 0.4 -> SentryState.SUSPICIOUS
            else -> SentryState.SUSPICIOUS // We default to suspicious/blocking for low score, DANGER is special
        }
        
        Log.d("SentryAI", "Evaluated State: $currentState (Score: $score)")
        return currentState
    }
}
