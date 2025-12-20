package com.guardian.mesh

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.ParcelUuid
import android.util.Log
import androidx.core.app.ActivityCompat
import java.util.UUID

class MeshMonitor(private val context: Context) {

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private var advertiser: BluetoothLeAdvertiser? = null
    private var scanner: BluetoothLeScanner? = null

    // Unique UUID for Guardian Mesh Service
    private val SERVICE_UUID = ParcelUuid(UUID.fromString("0000180D-0000-1000-8000-00805F9B34FB")) // Example: Heart Rate Service UUID for demo

    private var isScanning = false
    private var isAdvertising = false

    fun start() {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            Log.e("MeshMonitor", "Bluetooth not enabled")
            return
        }

        if (!hasPermissions()) {
            Log.e("MeshMonitor", "Missing Bluetooth Permissions")
            return
        }

        startAdvertising()
        startScanning()
    }

    fun stop() {
        stopAdvertising()
        stopScanning()
    }

    private fun hasPermissions(): Boolean {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            return ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED &&
                   ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                   ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        }
        return true // Pre-Android 12 permissions handled in Manifest
    }

    private fun startAdvertising() {
        advertiser = bluetoothAdapter?.bluetoothLeAdvertiser
        if (advertiser == null) return

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(false)
            .build()

        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .addServiceUuid(SERVICE_UUID)
            .build()

        try {
            advertiser?.startAdvertising(settings, data, advertiseCallback)
            isAdvertising = true
            Log.d("MeshMonitor", "BLE Advertising Started")
        } catch (e: SecurityException) {
            Log.e("MeshMonitor", "Advertising failed: ${e.message}")
        }
    }

    private fun stopAdvertising() {
        try {
            advertiser?.stopAdvertising(advertiseCallback)
            isAdvertising = false
            Log.d("MeshMonitor", "BLE Advertising Stopped")
        } catch (e: SecurityException) {
            // Ignore
        }
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            Log.d("MeshMonitor", "Advertising Success")
        }

        override fun onStartFailure(errorCode: Int) {
            Log.e("MeshMonitor", "Advertising Failure: $errorCode")
        }
    }

    private fun startScanning() {
        scanner = bluetoothAdapter?.bluetoothLeScanner
        if (scanner == null) return

        val filter = ScanFilter.Builder()
            .setServiceUuid(SERVICE_UUID)
            .build()

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        try {
            scanner?.startScan(listOf(filter), settings, scanCallback)
            isScanning = true
            Log.d("MeshMonitor", "BLE Scanning Started")
        } catch (e: SecurityException) {
            Log.e("MeshMonitor", "Scanning failed: ${e.message}")
        }
    }

    private fun stopScanning() {
        try {
            scanner?.stopScan(scanCallback)
            isScanning = false
            Log.d("MeshMonitor", "BLE Scanning Stopped")
        } catch (e: SecurityException) {
            // Ignore
        }
    }

    private val _detectedDevices = kotlinx.coroutines.flow.MutableStateFlow<List<MeshDevice>>(emptyList())
    val detectedDevices: kotlinx.coroutines.flow.StateFlow<List<MeshDevice>> = _detectedDevices

    private val foundDevicesMap = mutableMapOf<String, MeshDevice>()
    
    fun getNeighborCount(): Int {
        return foundDevicesMap.size
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result?.let {
                val deviceAddress = it.device.address
                val rssi = it.rssi
                val timestamp = System.currentTimeMillis()
                
                val device = MeshDevice(deviceAddress, rssi, timestamp)
                foundDevicesMap[deviceAddress] = device
                
                // Emit update
                _detectedDevices.value = foundDevicesMap.values.toList().sortedByDescending { d -> d.rssi }
                
                Log.d("MeshMonitor", "Mesh Device: $deviceAddress, RSSI: $rssi")
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e("MeshMonitor", "Scan Failed: $errorCode")
        }
    }
}

data class MeshDevice(
    val address: String,
    val rssi: Int,
    val timestamp: Long
)
