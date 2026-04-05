package com.recordpricer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

class CropOverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val dimPaint = Paint().apply {
        color = Color.argb(140, 0, 0, 0)
    }
    private val clearPaint = Paint().apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }
    private val borderPaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 3f
        isAntiAlias = true
    }
    private val cornerPaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 6f
        strokeCap = Paint.Cap.SQUARE
        isAntiAlias = true
    }
    private val labelPaint = Paint().apply {
        color = Color.WHITE
        textSize = 36f
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
    }

    private val cropRect = RectF()

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val size = (minOf(width, height) * 0.82f)
        val cx = width / 2f
        val cy = height / 2f - 40f
        cropRect.set(cx - size / 2, cy - size / 2, cx + size / 2, cy + size / 2)

        // Dark overlay
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), dimPaint)
        // Punch out the clear square
        canvas.drawRect(cropRect, clearPaint)
        // Border
        canvas.drawRect(cropRect, borderPaint)

        // Corner accents (L-shaped, 36px long)
        val c = 36f
        val l = cropRect.left; val t = cropRect.top
        val r = cropRect.right; val b = cropRect.bottom
        // Top-left
        canvas.drawLine(l, t, l + c, t, cornerPaint)
        canvas.drawLine(l, t, l, t + c, cornerPaint)
        // Top-right
        canvas.drawLine(r - c, t, r, t, cornerPaint)
        canvas.drawLine(r, t, r, t + c, cornerPaint)
        // Bottom-left
        canvas.drawLine(l, b - c, l, b, cornerPaint)
        canvas.drawLine(l, b, l + c, b, cornerPaint)
        // Bottom-right
        canvas.drawLine(r, b - c, r, b, cornerPaint)
        canvas.drawLine(r - c, b, r, b, cornerPaint)

        // Hint text below box
        canvas.drawText("Align album cover inside box", cx, b + 52f, labelPaint)
    }

    fun getCropRect(): RectF = RectF(cropRect)
}
