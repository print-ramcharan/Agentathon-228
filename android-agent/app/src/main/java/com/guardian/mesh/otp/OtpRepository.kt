package com.guardian.mesh.otp

import android.util.Log

data class OtpEntry(
    val code: String,
    val sourcePackage: String,
    val timestamp: Long = System.currentTimeMillis()
)

object OtpRepository {
    private var currentOtp: OtpEntry? = null
    private const val EXPIRATION_MS = 60_000 // 60 seconds validity

    fun saveOtp(code: String, source: String) {
        Log.d("GuardianOTP", "Saving OTP: $code from $source")
        currentOtp = OtpEntry(code, source)
    }

    fun getValidOtp(): String? {
        val otp = currentOtp ?: return null
        val age = System.currentTimeMillis() - otp.timestamp
        return if (age < EXPIRATION_MS) {
            otp.code
        } else {
            Log.d("GuardianOTP", "OTP Expired")
            currentOtp = null // Cleanup
            null
        }
    }
    
    fun clear() {
        currentOtp = null
    }
}
