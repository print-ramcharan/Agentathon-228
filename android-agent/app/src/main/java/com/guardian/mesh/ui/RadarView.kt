package com.guardian.mesh.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import com.guardian.mesh.MeshDevice
import kotlin.math.cos
import kotlin.math.sin

class RadarView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val gridPaint = Paint().apply {
        color = Color.parseColor("#3300E676") // Faint Green
        style = Paint.Style.STROKE
        strokeWidth = 2f
        isAntiAlias = true
    }

    private val sweepPaint = Paint().apply {
        color = Color.parseColor("#5500E676")
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val devicePaint = Paint().apply {
        color = Color.parseColor("#00E676") // Bright Green
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 30f
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
    }

    private var sweepAngle = 0f
    private var devices = listOf<MeshDevice>()
    private val deviceAngles = mutableMapOf<String, Float>() // Store random angle for each device

    init {
        val animator = ValueAnimator.ofFloat(0f, 360f)
        animator.duration = 2000
        animator.repeatCount = ValueAnimator.INFINITE
        animator.interpolator = LinearInterpolator()
        animator.addUpdateListener {
            sweepAngle = it.animatedValue as Float
            invalidate()
        }
        animator.start()
    }

    fun updateDevices(newDevices: List<MeshDevice>) {
        devices = newDevices
        // Assign random angles to new devices to keep them stable
        newDevices.forEach {
            if (!deviceAngles.containsKey(it.address)) {
                deviceAngles[it.address] = (Math.random() * 360).toFloat()
            }
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val cx = width / 2f
        val cy = height / 2f
        val radius = (Math.min(width, height) / 2f) * 0.9f

        // Draw Grid (Concentric Circles)
        canvas.drawCircle(cx, cy, radius, gridPaint)
        canvas.drawCircle(cx, cy, radius * 0.75f, gridPaint)
        canvas.drawCircle(cx, cy, radius * 0.5f, gridPaint)
        canvas.drawCircle(cx, cy, radius * 0.25f, gridPaint)

        // Draw Sweep Line
        val sweepX = cx + radius * cos(Math.toRadians(sweepAngle.toDouble())).toFloat()
        val sweepY = cy + radius * sin(Math.toRadians(sweepAngle.toDouble())).toFloat()
        canvas.drawLine(cx, cy, sweepX, sweepY, gridPaint)

        // Draw Devices
        devices.forEach { device ->
            val angle = deviceAngles[device.address] ?: 0f
            // Map RSSI (-100 to -30) to Distance (radius to 0)
            // Stronger signal (closer to -30) -> Closer to center
            val normalizedRssi = (device.rssi + 100).coerceIn(0, 70) / 70f // 0.0 to 1.0
            val distance = radius * (1f - normalizedRssi)

            val dx = cx + distance * cos(Math.toRadians(angle.toDouble())).toFloat()
            val dy = cy + distance * sin(Math.toRadians(angle.toDouble())).toFloat()

            canvas.drawCircle(dx, dy, 15f, devicePaint)
            // Draw last 4 chars of address
            val label = device.address.takeLast(4)
            canvas.drawText(label, dx, dy + 40f, textPaint)
        }
        
        // Draw Center (Self)
        canvas.drawCircle(cx, cy, 10f, devicePaint)
    }
}
