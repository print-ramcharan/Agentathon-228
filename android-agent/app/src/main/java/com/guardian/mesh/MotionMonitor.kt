package com.guardian.mesh

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log

class MotionMonitor(context: Context, private val onShake: () -> Unit) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private var lastShakeTime: Long = 0

    enum class MotionState {
        STATIONARY,
        MOVING,
        HIGH_VELOCITY
    }

    var currentMotionState: MotionState = MotionState.STATIONARY
        private set

    var onMotionStateChange: ((MotionState) -> Unit)? = null

    fun start() {
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
            Log.d("MotionMonitor", "Motion Monitoring Started")
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
        Log.d("MotionMonitor", "Motion Monitoring Stopped")
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            if (it.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                val x = it.values[0]
                val y = it.values[1]
                val z = it.values[2]

                // Simple linear acceleration (removing gravity roughly)
                val g = 9.8f
                val acceleration = kotlin.math.sqrt(x*x + y*y + z*z)
                val linearAcceleration = kotlin.math.abs(acceleration - g)

                val newState = when {
                    linearAcceleration < 0.5 -> MotionState.STATIONARY
                    linearAcceleration < 3.0 -> MotionState.MOVING
                    else -> MotionState.HIGH_VELOCITY
                }
                
                if (newState != currentMotionState) {
                    currentMotionState = newState
                    onMotionStateChange?.invoke(newState)
                }

                // Shake Detection for Duress (Legacy logic kept)
                if (linearAcceleration > 12) {
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastShakeTime > 2000) { // Debounce 2 seconds
                        lastShakeTime = currentTime
                        Log.w("MotionMonitor", "SHAKE DETECTED! Triggering Duress Mode.")
                        onShake()
                    }
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No-op
    }
}
