package com.guardian.mesh.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

class LivenessOverlayView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    private val paint = Paint().apply {
        color = Color.GREEN
        style = Paint.Style.STROKE
        strokeWidth = 8f
        isAntiAlias = true
    }

    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 60f
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
        typeface = Typeface.DEFAULT_BOLD
    }

    private val clearPaint = Paint().apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }

    private val backgroundPaint = Paint().apply {
        color = Color.parseColor("#99000000") // Semi-transparent black
    }

    var instruction: String = "Center your face"
        set(value) {
            field = value
            invalidate()
        }
    var lastDetectedFaceBitmap: android.graphics.Bitmap? = null

    private val ovalRect = RectF()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Draw semi-transparent background
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)

        // Calculate Oval (Center of screen)
        val cx = width / 2f
        val cy = height / 2f
        val radiusX = width * 0.35f
        val radiusY = height * 0.3f
        
        ovalRect.set(cx - radiusX, cy - radiusY, cx + radiusX, cy + radiusY)

        // Cut out the oval (Clear mode)
        // We need a hardware layer for clear mode to work properly on some devices, 
        // but for simple overlay, drawing the rects around might be easier. 
        // Let's just draw the oval stroke for now to guide the user.
        
        // Actually, to make a "cutout" effect:
        // 1. Draw full dark background.
        // 2. Draw oval with CLEAR xfermode? No, easier to just draw the stroke guide.
        
        // Let's stick to a simple guide for now.
        canvas.drawOval(ovalRect, paint)

        // Draw Instruction Text
        canvas.drawText(instruction, cx, cy - radiusY - 50f, textPaint)
    }
}
