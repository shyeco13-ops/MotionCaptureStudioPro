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
        widthScale: Float,
        gapScale: Float
    ) {
        if (jaw <= .025f || opened.width < 2 || opened.height < 2) return
        val width = closed.width.toFloat()
        val height = closed.height.toFloat()

        // Coordinates follow the actual four lip landmarks, not a fixed face box.
        val lipCenterX = (leftCornerX + rightCornerX) * .50f
        val lipCenterY = (upperLipY + lowerLipY) * .50f
        val cx = (lipCenterX * .65f + mouthX * .35f) * width
        val cy = (lipCenterY * .65f + mouthY * .35f) * height
        val cornerSpan = abs(rightCornerX - leftCornerX) * width
        val lipSpan = abs(lowerLipY - upperLipY) * height

        val rx = max(cornerSpan * .64f, width * faceWidth * .075f) *
            widthScale.coerceIn(.55f, 1.55f) * (1f + .05f * smile)
        val ry = max(lipSpan * .96f, height * faceHeight * .052f) *
            gapScale.coerceIn(.20f, 1.55f) *
            (.83f + .17f * jaw.coerceIn(0f, 1f))

        val safeRx = rx.coerceIn(4f, width * .25f)
        val safeRy = ry.coerceIn(3f, height * .16f)
        val bounds = RectF(cx - safeRx, cy - safeRy, cx + safeRx, cy + safeRy)
        val mode = effectiveMode(closed, opened)

        val src = if (mode == SourceMode.FULL_PORTRAIT) {
            // Matching full-frame image: sample the equivalent coordinates.
            val sx = opened.width / width
            val sy = opened.height / height
            val halfW = (safeRx * sx).toInt()
            val halfH = (safeRy * sy).toInt()
            val scx = (cx * sx).toInt()
            val scy = (cy * sy).toInt()
            Rect(
                (scx - halfW).coerceIn(0, opened.width - 1),
                (scy - halfH).coerceIn(0, opened.height - 1),
                (scx + halfW).coerceIn(1, opened.width),
                (scy + halfH).coerceIn(1, opened.height)
            )
        } else {
            // A crop is a mouth image, not an entire face.
            centeredCrop(opened.width, opened.height,
                (bounds.width() / bounds.height().coerceAtLeast(1f)))
        }
        if (src.width() < 2 || src.height() < 2) return

        val strength = ((jaw - .025f) / .60f).coerceIn(0f, 1f)
        val smooth = strength * strength * (3f - 2f * strength)

        // Everything is confined to this offscreen layer.
        val layer = canvas.saveLayer(bounds, null)
        try {
            photoPaint.alpha = (smooth * 255f).toInt().coerceIn(0, 255)
            canvas.drawBitmap(opened, src, bounds, photoPaint)
            photoPaint.alpha = 255

            // Elliptical radial gradient, opaque at center, transparent at edges.
            val shader = RadialGradient(
                0f, 0f, 1f,
                intArrayOf(Color.WHITE, Color.WHITE, Color.TRANSPARENT),
                floatArrayOf(0f, .47f, 1f), Shader.TileMode.CLAMP
            )
            val matrix = Matrix()
            matrix.setScale(safeRx, safeRy)
            matrix.postTranslate(cx, cy)
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
