package com.example.nanomaps.ui.map

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.util.AttributeSet
import android.view.View
import com.github.chrisbanes.photoview.PhotoView

class FantasyOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var photoView: PhotoView? = null
    var markerPosition: PointF? = null
    var directionEnd: PointF? = null

    private val markerPaint = Paint().apply {
        color = Color.parseColor("#4285F4")
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val markerStrokePaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 4f
        isAntiAlias = true
    }

    private val linePaint = Paint().apply {
        color = Color.parseColor("#EA4335")
        strokeWidth = 8f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        isAntiAlias = true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val photo = photoView ?: return
        val drawable = photo.drawable ?: return
        val marker = markerPosition ?: return

        val matrix = photo.imageMatrix
        val values = FloatArray(9)
        matrix.getValues(values)

        val scaleX = values[android.graphics.Matrix.MSCALE_X]
        val scaleY = values[android.graphics.Matrix.MSCALE_Y]
        val transX = values[android.graphics.Matrix.MTRANS_X]
        val transY = values[android.graphics.Matrix.MTRANS_Y]

        val markerScreenX = marker.x * drawable.intrinsicWidth * scaleX + transX
        val markerScreenY = marker.y * drawable.intrinsicHeight * scaleY + transY

        directionEnd?.let { end ->
            val endScreenX = end.x * drawable.intrinsicWidth * scaleX + transX
            val endScreenY = end.y * drawable.intrinsicHeight * scaleY + transY
            canvas.drawLine(markerScreenX, markerScreenY, endScreenX, endScreenY, linePaint)
        }

        canvas.drawCircle(markerScreenX, markerScreenY, 16f, markerPaint)
        canvas.drawCircle(markerScreenX, markerScreenY, 16f, markerStrokePaint)
        canvas.drawCircle(markerScreenX, markerScreenY, 6f, markerStrokePaint.apply { style = Paint.Style.FILL })
        markerStrokePaint.style = Paint.Style.STROKE
    }

    fun updateMarker(position: PointF?) {
        markerPosition = position
        invalidate()
    }

    fun updateDirection(end: PointF?) {
        directionEnd = end
        invalidate()
    }

    fun clearOverlay() {
        markerPosition = null
        directionEnd = null
        invalidate()
    }
}
