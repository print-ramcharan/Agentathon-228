package com.guardian.mesh.autofill

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class TrustedDevice(
    val id: String,
    val name: String,
    val publicKey: String,
    val type: String = "BROWSER", // BROWSER, LAPTOP, PHONE
    val timestamp: Long = System.currentTimeMillis()
)

object TrustedDeviceStore {
    private const val PREF_NAME = "trusted_devices"
    private const val KEY_DEVICES = "devices"
    private val gson = Gson()

    fun addDevice(context: Context, device: TrustedDevice) {
        val devices = getDevices(context).toMutableList()
        // Remove if exists (update)
        devices.removeAll { it.id == device.id }
        devices.add(device)
        saveDevices(context, devices)
    }

    fun getDevices(context: Context): List<TrustedDevice> {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_DEVICES, "[]")
        return try {
            val type = object : TypeToken<List<TrustedDevice>>() {}.type
            gson.fromJson(json, type)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveDevices(context: Context, list: List<TrustedDevice>) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_DEVICES, gson.toJson(list)).apply()
    }
    fun clearDevices(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
    }
}
