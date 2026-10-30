package com.luna.motioncapture

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import kotlin.math.max

/**
 * Alpha 0.4 — apertura contenida y adaptativa al color de los labios reales.
 * La textura del PNG se deforma en FaceAvatarView: aquí solo se dibuja la fina
 * separación interior, opcional. Nunca una figura triangular opaca.
 *
 * Limitación: una sola fotografía no contiene dientes ni tejido oculto.
 */
class NaturalMouthRenderer {
    var showInterior: Boolean = true
    var widthScale: Float = 1f
    var gapScale: Float = 1f

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val shape = Path()

    private fun colorAt(bitmap: Bitmap, nx: Float, ny: Float): Int {
        val x = (nx.coerceIn(0f, 1f) * (bitmap.width - 1)).toInt()
        val y = (ny.coerceIn(0f, 1f) * (bitmap.height - 1)).toInt()
        return bitmap.getPixel(x, y)
    }

    private fun shade(original: Int, f: Float): Int = Color.rgb(
        (Color.red(original) * f).toInt().coerceIn(0, 255),
        (Color.green(original) * f).toInt().coerceIn(0, 255),
        (Color.blue(original) * f).toInt().coerceIn(0, 255)
    )

    fun draw(
        canvas: Canvas,
        bitmap: Bitmap,
        mouthX: Float,
        mouthY: Float,
        faceWidth: Float,
        faceHeight: Float,
        opening: Float,
        smile: Float
    ) {
        if (!showInterior || opening < .09f) return
        val cx = mouthX * bitmap.width
        val cy = mouthY * bitmap.height
        val fw = faceWidth * bitmap.width
        val fh = faceHeight * bitmap.height
        val t = ((opening - .07f) / .93f).coerceIn(0f, 1f)
        // Mucho menor que el antiguo hueco negro. La apertura máxima es ajustable.
        val halfWidth = fw * (.092f + smile * .015f) * widthScale.coerceIn(.55f, 1.55f)
        val halfHeight = fh * (.002f + .027f * t) * gapScale.coerceIn(.15f, 1.55f)
        val cornerY = -smile * fh * .010f

        shape.reset()
        shape.moveTo(cx - halfWidth, cy + cornerY)
        shape.cubicTo(
            cx - halfWidth * .60f, cy - halfHeight * .72f,
            cx - halfWidth * .22f, cy - halfHeight * .95f,
            cx, cy - halfHeight * .84f
        )
        shape.cubicTo(
            cx + halfWidth * .28f, cy - halfHeight * .95f,
            cx + halfWidth * .68f, cy - halfHeight * .58f,
            cx + halfWidth, cy + cornerY
        )
        shape.cubicTo(
            cx + halfWidth * .60f, cy + halfHeight * .56f,
            cx + halfWidth * .24f, cy + halfHeight,
            cx, cy + halfHeight * .91f
        )
        shape.cubicTo(
            cx - halfWidth * .26f, cy + halfHeight,
            cx - halfWidth * .66f, cy + halfHeight * .55f,
            cx - halfWidth, cy + cornerY
        )
        shape.close()

        // Tonalidad tomada de labios originales, sin formas negras artificiales.
        val lipColor = colorAt(bitmap, mouthX, mouthY)
        val topColor = shade(lipColor, .57f)
        val midColor = shade(lipColor, .31f)
        val bottomColor = shade(lipColor, .47f)
        paint.shader = LinearGradient(
            cx, cy - max(1f, halfHeight), cx, cy + max(1f, halfHeight),
            intArrayOf(topColor, midColor, bottomColor),
            floatArrayOf(0f, .50f, 1f), Shader.TileMode.CLAMP
        )
        paint.style = Paint.Style.FILL
        // Transparencia moderada: revela parte de la textura real y evita el hueco negro.
        paint.alpha = (t * 205f).toInt().coerceIn(0, 205)
        canvas.drawPath(shape, paint)
        paint.shader = null
        paint.alpha = 255
    }
}
