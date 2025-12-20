package com.guardian.mesh.ui

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.guardian.mesh.GuardianService
import com.guardian.mesh.R
import kotlinx.coroutines.launch

class MeshActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mesh)

        val radarView = findViewById<RadarView>(R.id.radarView)
        val statusText = findViewById<TextView>(R.id.meshStatusText)

        val meshMonitor = GuardianService.activeMeshMonitor
        
        // Show Trusted Devices immediately
        fun updateStatus(bleCount: Int) {
            val trustedDevices = com.guardian.mesh.autofill.TrustedDeviceStore.getDevices(this@MeshActivity)
            val cloudCount = trustedDevices.size
            statusText.text = "BLE Nodes: $bleCount | Cloud Peers: $cloudCount"
        }
        
        // Initial state
        updateStatus(0)

        if (meshMonitor != null) {
            lifecycleScope.launch {
                meshMonitor.detectedDevices.collect { bleDevices ->
                    radarView.updateDevices(bleDevices)
                    updateStatus(bleDevices.size)
                }
            }
        } else {
            // Even if mesh monitor is down, show cloud peers
             updateStatus(0)
             // statusText.append(" (Mesh Offline)")
        }
    }
}
