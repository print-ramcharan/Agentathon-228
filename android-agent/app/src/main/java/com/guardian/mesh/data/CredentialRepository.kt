package com.guardian.mesh.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.guardian.mesh.ui.Credential
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

object CredentialRepository {
    private const val FILENAME = "credentials.json"
    private val gson = Gson()
    private val credentials = CopyOnWriteArrayList<Credential>()
    private var isLoaded = false

    fun getAllCredentials(context: Context): List<Credential> {
        if (!isLoaded) {
            load(context)
        }
        return ArrayList(credentials)
    }
    
    fun getUniqueDomains(context: Context): List<String> {
        return getAllCredentials(context)
            .map { it.domain }
            .distinct()
            .sorted()
    }

    fun addCredential(context: Context, credential: Credential) {
        if (!isLoaded) load(context)
        credentials.add(0, credential) // Add to top
        save(context)
    }
    
    fun deleteCredential(context: Context, credential: Credential) {
         if (!isLoaded) load(context)
         credentials.remove(credential)
         save(context)
    }

    private fun load(context: Context) {
        val file = File(context.filesDir, FILENAME)
        if (file.exists()) {
            try {
                val json = file.readText()
                val type = object : TypeToken<List<Credential>>() {}.type
                val loaded: List<Credential>? = gson.fromJson(json, type)
                if (loaded != null) {
                    credentials.clear()
                    credentials.addAll(loaded)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        isLoaded = true
    }

    private fun save(context: Context) {
        try {
            val json = gson.toJson(credentials)
            val file = File(context.filesDir, FILENAME)
            file.writeText(json)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
