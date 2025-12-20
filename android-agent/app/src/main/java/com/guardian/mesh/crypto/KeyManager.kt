package com.guardian.mesh.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.Signature
import java.security.interfaces.ECPublicKey

class KeyManager {
    private val KEY_ALIAS = "GuardianDeviceKey"
    private val KEYSTORE_PROVIDER = "AndroidKeyStore"

    init {
        generateKeyIfNotExists()
    }

    private fun generateKeyIfNotExists() {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER)
        keyStore.load(null)
        
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            Log.d("KeyManager", "Generating new Hardware KeyPair...")
            val kpg = KeyPairGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_EC,
                KEYSTORE_PROVIDER
            )
            val parameterSpec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
            ).run {
                setDigests(KeyProperties.DIGEST_SHA256)
                build()
            }
            kpg.initialize(parameterSpec)
            kpg.generateKeyPair()
        }
    }

    fun getPublicKeyPEM(): String {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER)
        keyStore.load(null)
        val entry = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.PrivateKeyEntry
        val publicKey = entry?.certificate?.publicKey as? ECPublicKey
        
        publicKey?.let {
            val encoded = Base64.encodeToString(it.encoded, Base64.NO_WRAP)
            return "-----BEGIN PUBLIC KEY-----\n$encoded\n-----END PUBLIC KEY-----"
        }
        return ""
    }

    fun signData(data: String): String? {
        return try {
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER)
            keyStore.load(null)
            val entry = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.PrivateKeyEntry
            
            val signature = Signature.getInstance("SHA256withECDSA")
            signature.initSign(entry?.privateKey)
            signature.update(data.toByteArray())
            
            val sigBytes = signature.sign()
            Base64.encodeToString(sigBytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.e("KeyManager", "Signing failed: ${e.message}")
            null
        }
    }
}
