package com.guardian.mesh

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.util.Log
import androidx.core.app.ActivityCompat

class LocationMonitor(private val context: Context) : LocationListener {

    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private var lastLocation: Location? = null

    fun start() {
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e("LocationMonitor", "Missing Location Permissions")
            return
        }
        
        // Request updates from GPS and Network
        try {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 10000L, 10f, this)
            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 10000L, 10f, this)
            
            // Try to get last known location immediately
            val lastGps = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            val lastNetwork = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            
            if (lastGps != null) {
                lastLocation = lastGps
            } else if (lastNetwork != null) {
                lastLocation = lastNetwork
            }
            
            Log.d("LocationMonitor", "Location Monitoring Started. Initial Location: $lastLocation")
        } catch (e: SecurityException) {
            Log.e("LocationMonitor", "Security Exception: ${e.message}")
        } catch (e: Exception) {
            Log.e("LocationMonitor", "Error starting location updates: ${e.message}")
        }
    }

    fun stop() {
        locationManager.removeUpdates(this)
        Log.d("LocationMonitor", "Location Monitoring Stopped")
    }

    fun getLastLocation(): Location? {
        return lastLocation
    }

    override fun onLocationChanged(location: Location) {
        lastLocation = location
        Log.d("LocationMonitor", "New Location: ${location.latitude}, ${location.longitude}")
        // Here we would feed this into the Risk Engine
    }

    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}
}
