package com.guardian.mesh

import android.app.Application
import android.util.Log

class GuardianApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Log.d("GuardianApp", "Guardian Mesh Agent Initialized")

        // "Never Crash" Protocol: Catch all unhandled exceptions and restart cleanly
        Thread.setDefaultUncaughtExceptionHandler { _, throwable ->
            Log.e("GuardianApp", "FATAL EXCEPTION CAUGHT: ${throwable.message}", throwable)
            
            try {
                val intent = packageManager.getLaunchIntentForPackage(packageName)
                if (intent != null) {
                    intent.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or 
                                  android.content.Intent.FLAG_ACTIVITY_NEW_TASK or 
                                  android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    startActivity(intent)
                }
            } catch (e: Exception) {
                Log.e("GuardianApp", "Failed to restart after crash", e)
            } finally {
                android.os.Process.killProcess(android.os.Process.myPid())
                System.exit(1)
            }
        }
    }
}
