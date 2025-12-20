package com.guardian.mesh.autofill

import android.os.Build
import android.service.autofill.AutofillService
import android.service.autofill.FillCallback
import android.service.autofill.FillContext
import android.service.autofill.FillRequest
import android.service.autofill.FillResponse
import android.service.autofill.SaveCallback
import android.service.autofill.SaveRequest
import android.util.Log
import android.widget.RemoteViews
import android.service.autofill.Dataset
import android.view.autofill.AutofillValue
import com.guardian.mesh.R

class GuardianAutofillService : AutofillService() {

    override fun onConnected() {
        super.onConnected()
        Log.d("GuardianAutofill", "Service Connected! The system has bound to Guardian Autofill.")
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            android.widget.Toast.makeText(applicationContext, "Guardian Agent Active 🛡️", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    override fun onDisconnected() {
        super.onDisconnected()
        Log.d("GuardianAutofill", "Service Disconnected.")
    }

    override fun onFillRequest(request: FillRequest, cancellationSignal: android.os.CancellationSignal, callback: FillCallback) {
        // 1. Analyze the Request (Context)
        val context = request.fillContexts.last()
        val structure = context.structure
        val packageName = structure.activityComponent.packageName
        
        Log.d("GuardianAutofill", "onFillRequest: Intercepting login attempt for Package: $packageName")
        
        // DEBUG: Show Toast to user so they know we are working and what package it is
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            android.widget.Toast.makeText(applicationContext, "Guardian: Watching $packageName 👁️", android.widget.Toast.LENGTH_SHORT).show()
        }

        // 2. Check Risk Score
        val riskEngine = com.guardian.mesh.GuardianService.activeRiskEngine
        var isSafe = true
        var requiresBiometric = false
        
        if (riskEngine != null) {
            val trustScore = riskEngine.calculateTrustScore(0.0f).totalScore
            Log.d("GuardianAutofill", "Current Trust Score: $trustScore")
            
            if (trustScore < 0.3f) {
                isSafe = false // Low Trust - Block (Approx equiv to Risk > 0.7)
                Log.w("GuardianAutofill", "Trust is LOW ($trustScore). Blocking.")
            } else if (trustScore < 0.7f) {
                requiresBiometric = true // Medium Trust - Escalate
                Log.d("GuardianAutofill", "Trust is MEDIUM ($trustScore). Requiring Biometric.")
            } else {
                Log.d("GuardianAutofill", "Trust is HIGH ($trustScore). Safe to fill.")
            }
        } else {
            Log.w("GuardianAutofill", "RiskEngine not active. Defaulting to Safe.")
        }

        if (!isSafe) {
            Log.w("GuardianAutofill", "Risk High! Blocking Autofill.")
            callback.onSuccess(null)
            return
        }

        // 3. Get Credentials from Vault
        val credentials = CredentialVault.getLegacyCredentials(packageName)
        if (credentials == null) {
            Log.d("GuardianAutofill", "No credentials found for package: $packageName. Available: ${CredentialVault.getAllPackages()}")
            callback.onSuccess(null)
            return
        }

        // 4. Parse Structure to find Username/Password fields
        val parser = StructureParser(structure)
        val (usernameId, passwordId) = parser.parse()
        
        Log.d("GuardianAutofill", "Parser Results - UsernameID: $usernameId, PasswordID: $passwordId")

        Log.d("GuardianAutofill", "Parser Results - UsernameID: $usernameId, PasswordID: $passwordId")

        if (usernameId != null && passwordId != null) {
            Log.d("GuardianAutofill", "Found Login Fields. Offering Autofill.")
            
            val presentation = RemoteViews(packageName, android.R.layout.simple_list_item_1)
            
            if (requiresBiometric) {
                 presentation.setTextViewText(android.R.id.text1, "Guardian: Verify Identity to Fill")
                 Log.d("GuardianAutofill", "Biometric Authentication Required (Simulated)")
            } else {
                presentation.setTextViewText(android.R.id.text1, "Guardian Safe Fill: ${credentials.first}")
            }

            // Create a Dataset
            val datasetBuilder = Dataset.Builder()
            
            datasetBuilder.setValue(usernameId, AutofillValue.forText(credentials.first), presentation)
            datasetBuilder.setValue(passwordId, AutofillValue.forText(credentials.second), presentation)
            
            val fillResponse = FillResponse.Builder()
                .addDataset(datasetBuilder.build())
                .build()
            
            callback.onSuccess(fillResponse)
        } else {
            Log.d("GuardianAutofill", "Could not find Username/Password fields in structure.")
            callback.onSuccess(null)
        }
    }

    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        Log.d("GuardianAutofill", "onSaveRequest: App wants to save credentials")
        // We would save new credentials to our Vault here
        callback.onSuccess()
    }


}
