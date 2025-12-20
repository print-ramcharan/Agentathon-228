package com.guardian.mesh

import android.content.Context
import android.content.SharedPreferences
import android.location.Location
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.Calendar

class BehavioralEngine(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("BehavioralPrefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    // Persistent State
    private var knownLocations: MutableList<LocationData> = loadKnownLocations()
    private var activeHoursStart: Int = prefs.getInt("active_start", 7) // Default 7 AM
    private var activeHoursEnd: Int = prefs.getInt("active_end", 22)   // Default 10 PM
    
    data class LocationData(val lat: Double, val lng: Double, var visitCount: Int) {
        fun toLocation(): Location {
            val l = Location("BehavioralEngine")
            l.latitude = lat
            l.longitude = lng
            return l
        }
    }

    fun learnLocation(location: Location) {
        var found = false
        for (known in knownLocations) {
            val results = FloatArray(1)
            Location.distanceBetween(known.lat, known.lng, location.latitude, location.longitude, results)
            if (results[0] < 100) { // 100m radius
                known.visitCount++
                found = true
                Log.d("BehavioralEngine", "Reinforcing location. Visits: ${known.visitCount}")
                break
            }
        }
        
        if (!found) {
            knownLocations.add(LocationData(location.latitude, location.longitude, 1))
            Log.d("BehavioralEngine", "Learned NEW location: ${location.latitude}, ${location.longitude}")
        }
        
        saveKnownLocations()
        learnTime()
    }

    private fun learnTime() {
        // Simple heuristic: Expand active window if user is active outside it
        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        if (currentHour < activeHoursStart) {
            activeHoursStart = currentHour
            prefs.edit().putInt("active_start", activeHoursStart).apply()
        }
        if (currentHour > activeHoursEnd) {
            activeHoursEnd = currentHour
            prefs.edit().putInt("active_end", activeHoursEnd).apply()
        }
    }

    fun isNormalContext(currentLocation: Location?): Boolean {
        if (currentLocation == null) return false
        
        val isTimeNormal = checkTime()
        val isLocationNormal = checkLocation(currentLocation)
        
        return isTimeNormal && isLocationNormal
    }
    
    fun getTrustSignal(currentLocation: Location?): Float {
        // Returns 0.0 to 1.0 based on context strength
        if (currentLocation == null) return 0.0f
        
        var signal = 0.0f
        if (checkLocation(currentLocation)) signal += 0.6f
        if (checkTime()) signal += 0.4f
        return signal
    }

    private fun checkTime(): Boolean {
        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        // Allow 1 hour buffer
        return currentHour in (activeHoursStart - 1)..(activeHoursEnd + 1)
    }

    private fun checkLocation(currentLocation: Location): Boolean {
        if (knownLocations.isEmpty()) return true // Trust first use to bootstrap

        for (known in knownLocations) {
            val results = FloatArray(1)
            Location.distanceBetween(known.lat, known.lng, currentLocation.latitude, currentLocation.longitude, results)
            
            // Trusted if < 200m AND visited at least 3 times
            if (results[0] < 200 && known.visitCount >= 3) { 
                return true
            }
        }
        return false
    }

    private fun saveKnownLocations() {
        val json = gson.toJson(knownLocations)
        prefs.edit().putString("known_locations", json).apply()
    }

    private fun loadKnownLocations(): MutableList<LocationData> {
        val json = prefs.getString("known_locations", null)
        return if (json != null) {
            val type = object : TypeToken<MutableList<LocationData>>() {}.type
            gson.fromJson(json, type)
        } else {
            mutableListOf()
        }
    }
}
