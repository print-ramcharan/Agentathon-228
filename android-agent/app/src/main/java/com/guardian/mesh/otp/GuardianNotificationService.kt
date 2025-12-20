package com.guardian.mesh.otp

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import java.util.regex.Pattern

class GuardianNotificationService : NotificationListenerService() {

    // Regex to find 4-8 digit codes. 
    // Looks for patterns like "Code: 123456", "OTP is 1234", or just standalone digits if clear.
    // We use a simple one for now: look for 4-8 consecutive digits.
    private val otpPattern = Pattern.compile("(?<!\\d)(\\d{4,8})(?!\\d)")

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return

        val extras = sbn.notification.extras
        val title = extras.getString("android.title")
        val text = extras.getCharSequence("android.text")?.toString()
        val bigText = extras.getCharSequence("android.bigText")?.toString()
        val subText = extras.getCharSequence("android.subText")?.toString()

        // Prioritize body content over title to avoid matching sender numbers (e.g. "555-123")
        // Use listOfNotNull to avoid "null" string literals
        val fullText = listOfNotNull(text, bigText, subText, title).joinToString(" ")
        
        Log.d("GuardianOTP", "Notification received from ${sbn.packageName}: $fullText")
        
        // Filter out our own app to avoid loops or irrelevant notifs
        if (sbn.packageName != "com.guardian.mesh") {
            extractOtp(fullText, sbn.packageName)
        }
    }

    private fun extractOtp(text: String, sourcePackage: String) {
        val matcher = otpPattern.matcher(text)
        if (matcher.find()) {
            val otp = matcher.group(1)
            Log.d("GuardianOTP", "Extracted OTP: $otp from $sourcePackage")
            OtpRepository.saveOtp(otp!!, sourcePackage)
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d("GuardianOTP", "Notification Listener Connected")
    }
}
