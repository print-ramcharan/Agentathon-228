package com.guardian.mesh.ui

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.guardian.mesh.R
import com.guardian.mesh.data.CredentialRepository

class AddCredentialActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_credential)

        // Setup Toolbar
        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        val inputService = findViewById<EditText>(R.id.inputServiceName)
        val inputDomain = findViewById<AutoCompleteTextView>(R.id.inputDomain)
        val inputUsername = findViewById<EditText>(R.id.inputUsername)
        val inputPassword = findViewById<EditText>(R.id.inputPassword)
        val btnSave = findViewById<Button>(R.id.btnSave)

        // Setup Domain Autocomplete
        val existingDomains = CredentialRepository.getUniqueDomains(this)
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, existingDomains)
        inputDomain.setAdapter(adapter)

        btnSave.setOnClickListener {
            val service = inputService.text.toString().trim()
            val domain = inputDomain.text.toString().trim()
            val username = inputUsername.text.toString().trim()
            val password = inputPassword.text.toString().trim()

            if (service.isEmpty() || domain.isEmpty() || username.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val newCredential = Credential(domain, username, service, password)
            CredentialRepository.addCredential(this, newCredential)
            
            Toast.makeText(this, "Account Saved", Toast.LENGTH_SHORT).show()
            
            // Return to dashboard
            setResult(RESULT_OK)
            finish()
        }
    }
}
