package com.guardian.mesh.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.Signature

class IdentityManager {

    private val KEY_ALIAS = "GuardianIdentityKey"
    private val KEYSTORE_PROVIDER = "AndroidKeyStore"

    fun hasIdentity(): Boolean {
        try {
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER)
            keyStore.load(null)
            return keyStore.containsAlias(KEY_ALIAS)
        } catch (e: Exception) {
            Log.e("IdentityManager", "Error checking identity: ${e.message}")
            return false
        }
    }

    fun generateIdentityKey() {
        try {
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER)
            keyStore.load(null)

            if (keyStore.containsAlias(KEY_ALIAS)) {
                Log.d("IdentityManager", "Key already exists. Skipping generation.")
                return
            }

            val keyPairGenerator = KeyPairGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_EC,
                KEYSTORE_PROVIDER
            )

            val builder = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
            )
                .setDigests(KeyProperties.DIGEST_SHA256)
                .setAlgorithmParameterSpec(java.security.spec.ECGenParameterSpec("secp256r1"))
                // Removed setUserAuthenticationRequired(true) to allow usage after custom Face Liveness
                // without triggering a second OS-level BiometricPrompt. 

            keyPairGenerator.initialize(builder.build())
            keyPairGenerator.generateKeyPair()
            
            Log.d("IdentityManager", "Identity Key Generated Successfully")

        } catch (e: Exception) {
            Log.e("IdentityManager", "Error generating key: ${e.message}")
            throw e
        }
    }

    fun getPublicKey(): String? {
        try {
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER)
            keyStore.load(null)
            val entry = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.PrivateKeyEntry
            
            if (entry != null) {
                val publicKey = entry.certificate.publicKey
                return Base64.encodeToString(publicKey.encoded, Base64.NO_WRAP)
            }
        } catch (e: Exception) {
            Log.e("IdentityManager", "Error getting public key: ${e.message}")
        }
        return null
    }

    fun createSignatureObject(): Signature? {
        try {
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER)
            keyStore.load(null)
            val entry = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.PrivateKeyEntry
            
            if (entry != null) {
                val signature = Signature.getInstance("SHA256withECDSA")
                signature.initSign(entry.privateKey)
                return signature
            }
        } catch (e: Exception) {
            Log.e("IdentityManager", "Error creating signature object: ${e.message}")
        }
        return null
    }
    
    fun deleteKey() {
        try {
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER)
            keyStore.load(null)
            keyStore.deleteEntry(KEY_ALIAS)
        } catch (e: Exception) {
            Log.e("IdentityManager", "Error deleting key: ${e.message}")
        }
    }
}
