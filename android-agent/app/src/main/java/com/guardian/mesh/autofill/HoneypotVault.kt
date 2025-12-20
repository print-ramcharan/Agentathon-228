package com.guardian.mesh.autofill
// Credential is in the same package, no import needed usually, but good to be safe if moved.
// import com.guardian.mesh.autofill.Credential

object HoneypotVault {
    
    // Decoy credentials that look real but fail (or lead to restricted accounts)
    // Constructor: id, packageName, username, password, appName
    private val decoys = listOf(
        Credential("d1", "com.netflix.mediaclient", "user_decoy@gmail.com", "password123", "netflix.com"),
        Credential("d2", "com.google.android.gm", "decoy.target@gmail.com", "Summer2024!", "google.com"),
        Credential("d3", "com.infonow.bofa", "client_temp", "secret123", "bankofamerica.com")
    )
    
    fun getDecoy(appNameOrPackage: String): List<Credential> {
        return decoys.filter { 
            it.appName.contains(appNameOrPackage, ignoreCase = true) || 
            it.packageName.contains(appNameOrPackage, ignoreCase = true) 
        }
    }
}
