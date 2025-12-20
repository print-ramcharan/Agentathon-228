package com.guardian.mesh.ui

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.guardian.mesh.R
import com.guardian.mesh.autofill.Credential
import com.guardian.mesh.autofill.CredentialVault
import java.util.concurrent.Executor

class VaultActivity : AppCompatActivity() {

    private lateinit var executor: Executor
    private lateinit var biometricPrompt: BiometricPrompt
    private lateinit var promptInfo: BiometricPrompt.PromptInfo
    private lateinit var adapter: VaultAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_vault)

        setupBiometrics()
        setupUI()
        
        // Check availability before authenticating
        val biometricManager = androidx.biometric.BiometricManager.from(this)
        when (biometricManager.canAuthenticate(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK)) {
            androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS -> {
                biometricPrompt.authenticate(promptInfo)
            }
            else -> {
                Toast.makeText(this, "Biometrics unavailable. Accessing Vault.", Toast.LENGTH_LONG).show()
                loadCredentials()
            }
        }
    }

    private fun setupUI() {
        val recyclerView = findViewById<RecyclerView>(R.id.vaultRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        // Initialize with empty list and dummy delete callback to avoid null issues before auth
        adapter = VaultAdapter(emptyList<Credential>()) {} 
        recyclerView.adapter = adapter

        findViewById<FloatingActionButton>(R.id.fabAddCredential).setOnClickListener {
            showAddDialog()
        }
    }

    private fun setupBiometrics() {
        executor = ContextCompat.getMainExecutor(this)
        biometricPrompt = BiometricPrompt(this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    // Ignore user cancellation to avoid loop, but close if error is fatal
                    if (errorCode != BiometricPrompt.ERROR_USER_CANCELED) {
                         Toast.makeText(applicationContext, "Auth Error: $errString", Toast.LENGTH_SHORT).show()
                         finish()
                    }
                }

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    Toast.makeText(applicationContext, "Access Granted", Toast.LENGTH_SHORT).show()
                    loadCredentials()
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    Toast.makeText(applicationContext, "Auth Failed", Toast.LENGTH_SHORT).show()
                }
            })

        promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Guardian Vault Access")
            .setSubtitle("Verify identity to view credentials")
            .setNegativeButtonText("Cancel")
            .build()
    }

    private fun loadCredentials() {
        val credentials = CredentialVault.getAllCredentials()
        adapter = VaultAdapter(credentials) { credential ->
            // Delete Action
            CredentialVault.removeCredential(credential.id)
            android.widget.Toast.makeText(this, "Deleted ${credential.appName}", android.widget.Toast.LENGTH_SHORT).show()
            loadCredentials() // Refresh list
        }
        val recyclerView = findViewById<RecyclerView>(R.id.vaultRecyclerView)
        recyclerView.adapter = adapter
    }

    private fun showAddDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_credential, null)
        val editApp = dialogView.findViewById<android.widget.EditText>(R.id.editAppName)
        val editPkg = dialogView.findViewById<android.widget.EditText>(R.id.editPackageName)
        val editUser = dialogView.findViewById<android.widget.EditText>(R.id.editUsername)
        val editPass = dialogView.findViewById<android.widget.EditText>(R.id.editPassword)

        AlertDialog.Builder(this)
            .setTitle("Add New Credential")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val app = editApp.text.toString()
                val pkg = editPkg.text.toString()
                val user = editUser.text.toString()
                val pass = editPass.text.toString()

                if (app.isNotEmpty() && pkg.isNotEmpty() && user.isNotEmpty() && pass.isNotEmpty()) {
                    val newCred = Credential(
                        id = System.currentTimeMillis().toString(),
                        packageName = pkg,
                        username = user,
                        password = pass,
                        appName = app
                    )
                    CredentialVault.addCredential(newCred)
                    loadCredentials()
                    Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "All fields required", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
