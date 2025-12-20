package com.guardian.mesh.ui

import android.os.Bundle
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.text.method.PasswordTransformationMethod
import android.text.method.HideReturnsTransformationMethod
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.guardian.mesh.R
import kotlinx.coroutines.*
import java.net.URL
import android.graphics.BitmapFactory

class CredentialDetailActivity : AppCompatActivity() {

    private var isPasswordVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_credential_detail)

        val credential = intent.getParcelableExtra<Credential>("credential")
        if (credential == null) {
            finish()
            return
        }

        // Setup Toolbar
        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        // Bind Views
        val imgLogo = findViewById<ImageView>(R.id.imgLogoLarge)
        val textService = findViewById<TextView>(R.id.textServiceName)
        val textDomain = findViewById<TextView>(R.id.textDomainName)
        val textUsername = findViewById<TextView>(R.id.detailUsername)
        val textPassword = findViewById<TextView>(R.id.detailPassword)
        
        val btnCopyUser = findViewById<ImageView>(R.id.btnCopyUsername)
        val btnCopyPass = findViewById<ImageView>(R.id.btnCopyPassword)
        val btnTogglePass = findViewById<ImageView>(R.id.btnTogglePassword)
        val btnEdit = findViewById<Button>(R.id.btnEdit)
        val btnDelete = findViewById<Button>(R.id.btnDelete)

        // Set Data
        textService.text = credential.serviceName
        textDomain.text = credential.domain
        textUsername.text = credential.username
        
        // Initial Password State
        textPassword.text = credential.password
        textPassword.transformationMethod = PasswordTransformationMethod.getInstance()

        // Load Logo (Same logic as Adapter)
        val faviconUrl = "https://www.google.com/s2/favicons?domain=${credential.domain}&sz=128"
        CoroutineScope(Dispatchers.IO).launch {
             try {
                val url = URL(faviconUrl)
                val bitmap = BitmapFactory.decodeStream(url.openConnection().getInputStream())
                withContext(Dispatchers.Main) {
                    imgLogo.setImageBitmap(bitmap)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Actions
        btnCopyUser.setOnClickListener {
            copyToClipboard("Username", credential.username)
        }

        btnCopyPass.setOnClickListener {
            copyToClipboard("Password", credential.password)
        }

        btnTogglePass.setOnClickListener {
            isPasswordVisible = !isPasswordVisible
            if (isPasswordVisible) {
                textPassword.transformationMethod = HideReturnsTransformationMethod.getInstance()
                btnTogglePass.setColorFilter(android.graphics.Color.parseColor("#00E676"))
            } else {
                textPassword.transformationMethod = PasswordTransformationMethod.getInstance()
                btnTogglePass.setColorFilter(android.graphics.Color.parseColor("#888888"))
            }
        }

        btnEdit.setOnClickListener {
            Toast.makeText(this, "Edit feature coming soon", Toast.LENGTH_SHORT).show()
        }

        btnDelete.setOnClickListener {
             Toast.makeText(this, "Delete feature coming soon", Toast.LENGTH_SHORT).show()
        }
    }

    private fun copyToClipboard(label: String, text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "$label copied", Toast.LENGTH_SHORT).show()
    }
}
