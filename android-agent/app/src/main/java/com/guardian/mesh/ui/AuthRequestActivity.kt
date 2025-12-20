package com.guardian.mesh.ui

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.guardian.mesh.R
import com.guardian.mesh.autofill.CredentialVault
import com.guardian.mesh.network.AgentResponse
import com.guardian.mesh.network.NetworkClient
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class AuthRequestActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_auth_request)

        val requestId = intent.getStringExtra("requestId") ?: return
        val service = intent.getStringExtra("service") ?: "Unknown"
        val source = intent.getStringExtra("source") ?: "Unknown"
        val publicKey = intent.getStringExtra("publicKey")

        val trustDetails = intent.getStringExtra("trustDetails") ?: "N/A"

        findViewById<TextView>(R.id.textRequestInfo).text = 
            "Device: $source\nService: $service\n\nTrust Score Analysis:\n$trustDetails"

        findViewById<Button>(R.id.btnApprove).setOnClickListener {
            handleApproval(requestId, service, publicKey)
        }

        findViewById<Button>(R.id.btnDeny).setOnClickListener {
            finish()
        }
    }

    private fun handleApproval(requestId: String, service: String, publicKey: String?) {
        // Retrieve Credentials
        val creds = CredentialVault.getAllCredentials()
        val matches = creds.filter { 
            it.appName.contains(service, ignoreCase = true) || 
            it.packageName.contains(service, ignoreCase = true) 
        }

        if (matches.isEmpty()) {
            Toast.makeText(this, "No credentials found for $service", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Build Payload (Same logic as Service)
        val jsonBuilder = StringBuilder("[")
        matches.forEachIndexed { index, match ->
            jsonBuilder.append("{")
            jsonBuilder.append("\"username\":\"${match.username}\",")
            jsonBuilder.append("\"password\":\"${match.password}\",")
            jsonBuilder.append("\"appName\":\"${match.appName}\"")
            jsonBuilder.append("}")
            if (index < matches.size - 1) jsonBuilder.append(",")
        }
        jsonBuilder.append("]")
        
        var payload = jsonBuilder.toString()

        // Encrypt if needed
        if (publicKey != null) {
            try {
                payload = encryptPayload(payload, publicKey)
            } catch (e: Exception) {
                Toast.makeText(this, "Encryption Failed", Toast.LENGTH_SHORT).show()
                return
            }
        }

        // Send Response
        val resp = AgentResponse(requestId, payload)
        NetworkClient.authService.respondToRequest(resp).enqueue(object : Callback<Void> {
            override fun onResponse(call: Call<Void>, response: Response<Void>) {
                Toast.makeText(applicationContext, "Approved!", Toast.LENGTH_SHORT).show()
                finish()
            }

            override fun onFailure(call: Call<Void>, t: Throwable) {
                Toast.makeText(applicationContext, "Network Failed", Toast.LENGTH_SHORT).show()
            }
        })
    }
    
    // Duplicate Encryption Logic (Should be in Util, but keeping valid for now)
    private fun encryptPayload(data: String, publicKeyPem: String): String {
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
    }
}
