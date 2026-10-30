package com.luna.motioncapture

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Alpha 0.5.1 — Smart Mouth Layer.
 *
 * CRITICAL FIX: The previous implementation used DST_IN only inside drawCircle;
 * the rectangular part of the bitmap outside that circle was NEVER erased.
 * We now draw the alpha mask over the ENTIRE saveLayer rectangle. An elliptical
 * shader with transparent corners makes the rectangle impossible to leak.
 *
 * Full portrait: samples the mouth from matching coordinates of the open portrait.
 * Mouth crop: maps the supplied crop into the original mouth's landmarks.
 * Both stay local to the phone, and the underlying PNG is never modified.
 */
class MouthPhotoBlend {
    enum class SourceMode { AUTO, FULL_PORTRAIT, MOUTH_CROP }

    var sourceMode: SourceMode = SourceMode.AUTO

    private val photoPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
    }

    fun effectiveMode(base: Bitmap, alternative: Bitmap): SourceMode {
        if (sourceMode != SourceMode.AUTO) return sourceMode
        val aspectBase = base.width.toFloat() / base.height.coerceAtLeast(1)
        val aspectOther = alternative.width.toFloat() / alternative.height.coerceAtLeast(1)
        val resemblesFullPortrait =
            abs(aspectBase - aspectOther) < .075f &&
            alternative.width >= base.width * .55f &&
            alternative.height >= base.height * .55f
        return if (resemblesFullPortrait) SourceMode.FULL_PORTRAIT
            else SourceMode.MOUTH_CROP
    }

    private fun centeredCrop(width: Int, height: Int, ratio: Float): Rect {
        val sourceRatio = width.toFloat() / height.coerceAtLeast(1)
        return if (sourceRatio > ratio) {
            val croppedWidth = (height * ratio).toInt().coerceIn(1, width)
            Rect((width - croppedWidth) / 2, 0, (width + croppedWidth) / 2, height)
        } else {
            val croppedHeight = (width / ratio).toInt().coerceIn(1, height)
            Rect(0, (height - croppedHeight) / 2, width, (height + croppedHeight) / 2)
        }
    }

    fun draw(
        canvas: Canvas,
        closed: Bitmap,
        opened: Bitmap,
        mouthX: Float,
        mouthY: Float,
        upperLipY: Float,
        lowerLipY: Float,
        leftCornerX: Float,
        leftCornerY: Float,
        rightCornerX: Float,
        rightCornerY: Float,
        faceWidth: Float,
        faceHeight: Float,
        jaw: Float,
        smile: Float,
        lowerLipDrop: Float,
        widthScale: Float,
        gapScale: Float,
        lowerCoverage: Float,
        layerWeight: Float = 1f
    ) {
        if (jaw <= .018f || opened.width < 2 || opened.height < 2) return
        val width = closed.width.toFloat()
        val height = closed.height.toFloat()

        // La foto abierta representa el FINAL del gesto. En los estados
        // intermedios se comprime hacia la linea de cierre y se revela
        // progresivamente. No debe aparecer de golpe un parche ya abierto.
        val raw = ((jaw - .018f) / .88f).coerceIn(0f, 1f)
        val eased = raw * raw * (3f - 2f * raw)
        val centerX = (leftCornerX + rightCornerX) * .50f
        val lipCenterY = (upperLipY + lowerLipY) * .50f
        val cx = (centerX * .82f + mouthX * .18f) * width
        val cornerSpan = abs(rightCornerX - leftCornerX) * width
        val lipSpan = abs(lowerLipY - upperLipY) * height
        // Restringido a labios/comisuras. La mascara antigua (64% del ancho)
        // invadia mejillas y mostraba diferencias de color de la segunda foto.
        val fullRx = (max(cornerSpan * .49f, width * faceWidth * .055f) *
            widthScale.coerceIn(.55f, 1.55f) * (1f + .025f * smile))
            .coerceIn(4f, width * .19f)
        val fullRy = (max(lipSpan * 1.13f, height * faceHeight * .073f) +
            height * faceHeight * (.027f * lowerLipDrop)) *
            gapScale.coerceIn(.20f, 1.55f)
        val safeRy = fullRy.coerceIn(3f, height * .11f)
        val rx = fullRx * (.85f + .15f * eased)
        val ry = safeRy * (.25f + .75f * eased)
        // La mandibula se desplaza, pero la comisura no salta bruscamente.
        val cy = (lipCenterY * .78f + mouthY * .22f) * height +
            height * faceHeight * (.008f * eased + .014f * lowerLipDrop * eased)
        val upperReach = ry * 1.04f
        val lowerReach = ry * (1.05f + .28f * lowerCoverage.coerceIn(.35f, 2f))
        val bounds = RectF(cx - rx, cy - upperReach, cx + rx, cy + lowerReach)
        // Muestra final, sin comprimir. La transformacion al rectangulo
        // bounds produce todos los fotogramas intermedios del movimiento.
        val sourceBounds = RectF(
            cx - fullRx, (lipCenterY * .78f + mouthY * .22f) * height -
                safeRy * 1.04f,
            cx + fullRx, (lipCenterY * .78f + mouthY * .22f) * height +
                safeRy * (1.05f + .28f * lowerCoverage.coerceIn(.35f, 2f))
        )
        val mode = effectiveMode(closed, opened)

        val src = if (mode == SourceMode.FULL_PORTRAIT) {
            // Matching full-frame image: sample the equivalent coordinates.
            val sx = opened.width / width
            val sy = opened.height / height
            // Usar el rectángulo completo de destino para tomar la misma
            // región de la foto abierta, incluyendo el labio inferior.
            Rect(
                (sourceBounds.left * sx).toInt().coerceIn(0, opened.width - 2),
                (sourceBounds.top * sy).toInt().coerceIn(0, opened.height - 2),
                (sourceBounds.right * sx).toInt().coerceIn(2, opened.width),
                (sourceBounds.bottom * sy).toInt().coerceIn(2, opened.height)
            )
        } else {
            // A crop is a mouth image, not an entire face.
            centeredCrop(opened.width, opened.height,
                (sourceBounds.width() / sourceBounds.height().coerceAtLeast(1f)))
        }
        if (src.width() < 2 || src.height() < 2) return

        // El movimiento principal lo hace la malla, no un crossfade fuerte.
        // Una fotografia fija nunca debe mostrarse entera al 20% de abertura.
        val smooth = eased * eased * (3f - 2f * eased)

        // Everything is confined to this offscreen layer.
        val layer = canvas.saveLayer(bounds, null)
        try {
            photoPaint.alpha = (smooth * layerWeight.coerceIn(0f, 1f) * 255f).toInt().coerceIn(0, 255)
            canvas.drawBitmap(opened, src, bounds, photoPaint)
            photoPaint.alpha = 255

            // Elliptical radial gradient, opaque at center, transparent at edges.
            val shader = RadialGradient(
                0f, 0f, 1f,
                intArrayOf(Color.WHITE, Color.WHITE, Color.TRANSPARENT),
                floatArrayOf(0f, .58f, 1f), Shader.TileMode.CLAMP
            )
            val matrix = Matrix()
            // Más textura del labio inferior, manteniendo el contorno
            // completamente transparente antes de llegar al borde.
            matrix.setScale(
                rx * .985f,
                bounds.height() * .51f
            )
            matrix.postTranslate(cx, bounds.centerY())
            shader.setLocalMatrix(matrix)
            maskPaint.shader = shader

            // IMPORTANT: DST_IN must cover the FULL rectangle, including all
            // four corners. drawCircle previously left square corners untouched.
            canvas.drawRect(bounds, maskPaint)
            maskPaint.shader = null
        } finally {
            canvas.restoreToCount(layer)
        }
    }
}
