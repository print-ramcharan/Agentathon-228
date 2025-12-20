package com.guardian.mesh.autofill

data class Credential(
    val id: String,
    val packageName: String,
    val username: String,
    val password: String,
    val appName: String
)

object CredentialVault {
    private val credentials = mutableListOf<Credential>()
    
    // Default Identity
    var defaultEmail: String? = "ramcharanpolabathina@gmail.com" // Updated from User Logs
    var defaultPhone: String? = "+15550001234" // Default Phone
    
    init {
        credentials.addAll(listOf(
            Credential("1", "com.instagram.android", "insta_fan_2024", "social_secure_77", "Instagram"),
            Credential("2", "com.facebook.katana", "fb_user_prime", "meta_verse_99", "Facebook"),
            Credential("3", "com.netflix.mediaclient", "movie_buff_01", "chill_mode_on_88", "Netflix"),
            Credential("3_b", "com.netflix.mediaclient", "family_plan_admin", "kids_profile_123", "Netflix (Family)"),
            Credential("4", "com.android.chrome", "chrome_surfer", "web_safe_pass", "Chrome"),
            Credential("5", "org.mozilla.firefox", "firefox_user", "browser_pass_789", "Firefox"),
            Credential("6", "com.sec.android.app.sbrowser", "samsung_user", "galaxy_pass_000", "Samsung Internet"),
            Credential("7", "com.instagram.barcelona", "threads_user", "threads_pass_222", "Threads"),
            Credential("8", "www.roblox.com", "gamer_pro_99", "roblox_pass_123", "Roblox"),
            Credential("10", "www.roblox.com", "alt_account_2025", "secret_pass_456", "Roblox Alt"),
            Credential("9", "com.google.android.gm", "ramcharanpolabathina@gmail.com", "secure_google_pass", "Gmail")
        ))
    }

    fun getCredentials(packageName: String): List<Credential> {
        return credentials.filter { it.packageName == packageName }
    }
    
    fun getAllCredentials(): List<Credential> {
        return credentials.toList()
    }

    fun addCredential(credential: Credential) {
        credentials.add(credential)
    }
    
    fun removeCredential(id: String) {
        credentials.removeAll { it.id == id }
    }
    
    fun getAllPackages(): Set<String> {
        return credentials.map { it.packageName }.toSet()
    }
    
    // Legacy support helper (returns first match)
    fun getLegacyCredentials(packageName: String): Pair<String, String>? {
        val match = credentials.firstOrNull { it.packageName == packageName }
        return match?.let { Pair(it.username, it.password) }
    }
}
