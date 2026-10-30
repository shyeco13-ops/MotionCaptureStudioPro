package com.luna.motioncapture

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.min

/**
 * Mezcla local de una segunda imagen (misma cara, boca abierta) en el PNG base.
 * Solo se reemplaza la región elíptica suavizada alrededor de la boca.
 * No se altera la imagen original, no se envía información a servidores.
 */
class MouthPhotoBlend {
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
    }

    fun draw(
        canvas: Canvas,
        closed: Bitmap,
        opened: Bitmap,
        x: Float,
        y: Float,
        fw: Float,
        fh: Float,
        jaw: Float,
        smile: Float,
        widthScale: Float,
        gapScale: Float
    ) {
        if (jaw <= .015f || closed.width <= 0 || closed.height <= 0) return
        val opacity = (jaw * 1.4f).coerceIn(0f, 1f)
        val cx = x * closed.width
        val cy = y * closed.height
        val rx = (closed.width * fw * (.15f + .014f * smile) *
            widthScale.coerceIn(.55f, 1.55f)).coerceAtLeast(2f)
        val ry = (closed.height * fh * .125f *
            gapScale.coerceIn(.25f, 1.55f)).coerceAtLeast(2f)
        val bounds = RectF(cx - rx, cy - ry, cx + rx, cy + ry)
        val layer = canvas.saveLayer(bounds, null)
        try {
            // Es indispensable usar una foto alternativa con mismo encuadre.
            // Escalamos al mismo sistema de coordenadas de la foto principal.
            bitmapPaint.alpha = (255f * opacity).toInt().coerceIn(0, 255)
            canvas.drawBitmap(opened, null, RectF(
                0f, 0f, closed.width.toFloat(), closed.height.toFloat()
            ), bitmapPaint)
            bitmapPaint.alpha = 255

            val colors = intArrayOf(
                Color.WHITE, Color.WHITE, Color.TRANSPARENT
            )
            maskPaint.shader = RadialGradient(
                cx, cy, rx, colors, floatArrayOf(0f, .60f, 1f),
                Shader.TileMode.CLAMP
            )
            // Estirar la máscara circular para lograr un óvalo sin bordes duros.
            val stretch = canvas.save()
            canvas.scale(1f, ry / rx, cx, cy)
            canvas.drawCircle(cx, cy, rx, maskPaint)
            canvas.restoreToCount(stretch)
            maskPaint.shader = null
        } finally {
            canvas.restoreToCount(layer)
        }
    }
}
