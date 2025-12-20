package com.guardian.mesh

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.guardian.mesh.crypto.IdentityManager
import com.guardian.mesh.ui.LoginActivity
import com.guardian.mesh.ui.RegistrationActivity

class LauncherActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val identityManager = IdentityManager()
        val hasIdentity = identityManager.hasIdentity()

        if (hasIdentity) {
            // User registered -> Go to Login
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
        } else {
            // New User -> Go to Registration
            val intent = Intent(this, RegistrationActivity::class.java)
            startActivity(intent)
        }
        
        finish()
    }
}
