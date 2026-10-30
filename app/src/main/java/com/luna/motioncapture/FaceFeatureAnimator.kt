package com.luna.motioncapture

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.max

/**
 * Dibuja ojos y boca independientes por encima del rig 2D.
 *
 * Es una aproximación visual para imágenes planas. No sustituye el rig de capas
 * requerido para que el interior de boca y párpados coincidan con cualquier foto.
 */
class FaceFeatureAnimator {
    var eyeLeftX = .382f
        private set
    var eyeLeftY = .44f
        private set
    var eyeRightX = .618f
        private set
    var eyeRightY = .44f
        private set
    var mouthX = .50f
        private set
    var mouthY = .595f
        private set

    private var jaw = 0f
    private var blinkLeft = 0f
    private var blinkRight = 0f
    private var smile = 0f

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 21f
        setShadowLayer(3f, 1f, 1f, Color.BLACK)
    }

    fun reset(cx: Float, cy: Float, fw: Float, fh: Float) {
        eyeLeftX = cx - .20f * fw
        eyeLeftY = cy - .04f * fh
        eyeRightX = cx + .20f * fw
        eyeRightY = cy - .04f * fh
        mouthX = cx
        mouthY = cy + .25f * fh
        resetExpressions()
    }

    fun resetExpressions() {
        jaw = 0f
        blinkLeft = 0f
        blinkRight = 0f
        smile = 0f
    }

    fun translate(dx: Float, dy: Float) {
        eyeLeftX = (eyeLeftX + dx).coerceIn(.03f, .97f)
        eyeLeftY = (eyeLeftY + dy).coerceIn(.03f, .97f)
        eyeRightX = (eyeRightX + dx).coerceIn(.03f, .97f)
        eyeRightY = (eyeRightY + dy).coerceIn(.03f, .97f)
        mouthX = (mouthX + dx).coerceIn(.03f, .97f)
        mouthY = (mouthY + dy).coerceIn(.03f, .97f)
    }

    fun setAnchor(index: Int, x: Float, y: Float) {
        val px = x.coerceIn(.03f, .97f)
        val py = y.coerceIn(.03f, .97f)
        when(index) {
            1 -> { eyeLeftX = px; eyeLeftY = py }
            2 -> { eyeRightX = px; eyeRightY = py }
            3 -> { mouthX = px; mouthY = py }
        }
    }

    fun update(frame: FaceFrame, neutral: FaceFrame, intensity: Float) {
        fun expression(key: String, deadzone: Float, amplification: Float): Float =
            ((frame.expression(key) - neutral.expression(key) - deadzone) *
                amplification * intensity).coerceIn(0f, 1f)
        jaw = .30f * jaw + .70f * expression("jawOpen", .02f, 1.8f)
        blinkLeft = .18f * blinkLeft + .82f * expression("eyeBlinkLeft", .03f, 1.7f)
        blinkRight = .18f * blinkRight + .82f * expression("eyeBlinkRight", .03f, 1.7f)
        smile = .45f * smile + .55f * max(
            expression("mouthSmileLeft", .03f, 1.3f),
            expression("mouthSmileRight", .03f, 1.3f)
        )
    }

    private fun skinColor(bitmap: Bitmap, nx: Float, ny: Float): Int {
        val x = (nx.coerceIn(0f, 1f) * (bitmap.width - 1)).toInt()
        val y = (ny.coerceIn(0f, 1f) * (bitmap.height - 1)).toInt()
        val sample = bitmap.getPixel(x, y)
        return if (Color.alpha(sample) < 190) Color.rgb(190, 145, 121) else sample
    }

    fun draw(canvas: Canvas, bitmap: Bitmap, fwFraction: Float, fhFraction: Float) {
        val fw = fwFraction * bitmap.width
        val fh = fhFraction * bitmap.height

        fun drawEye(x: Float, y: Float, blink: Float) {
            if (blink < .08f) return
            val px = x * bitmap.width
            val py = y * bitmap.height
            val skin = skinColor(bitmap, x, y - fhFraction * .10f)
            val hw = fw * .137f
            val hh = fh * (.035f + .048f * blink)

            // Párpado del color de la zona próxima, no un rectángulo arbitrario.
            paint.style = Paint.Style.FILL
            paint.color = skin
            paint.alpha = (blink * 335f).toInt().coerceIn(0, 255)
            canvas.drawOval(RectF(px - hw, py - hh, px + hw, py + hh), paint)

            val dark = Color.rgb(
                (Color.red(skin) * .55f).toInt(),
                (Color.green(skin) * .53f).toInt(),
                (Color.blue(skin) * .53f).toInt()
            )
            paint.color = dark
            paint.alpha = (blink * 220f).toInt().coerceIn(0, 220)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = max(1f, fw * .012f)
            canvas.drawArc(RectF(px - hw * .78f, py - fh * .024f,
                px + hw * .78f, py + fh * .024f), 8f, 165f, false, paint)
            paint.style = Paint.Style.FILL
            paint.alpha = 255
        }

        drawEye(eyeLeftX, eyeLeftY, blinkLeft)
        drawEye(eyeRightX, eyeRightY, blinkRight)

        if (jaw > .065f) {
            val px = mouthX * bitmap.width
            val py = mouthY * bitmap.height
            val opening = (jaw * 1.25f).coerceIn(0f, 1f)
            val hw = fw * (.085f + smile * .055f)
            val hh = fh * (.012f + opening * .068f)

            // Cavidad y borde de labios; la foto base se conserva en memoria.
            paint.style = Paint.Style.FILL
            paint.color = Color.rgb(37, 13, 24)
            paint.alpha = (opening * 340f).toInt().coerceIn(0, 255)
            canvas.drawOval(RectF(px - hw, py - hh, px + hw, py + hh), paint)
            paint.color = Color.rgb(143, 72, 83)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = max(1f, fw * .012f)
            canvas.drawOval(RectF(px - hw, py - hh, px + hw, py + hh), paint)
            paint.style = Paint.Style.FILL
            paint.alpha = 255
        }
    }

    fun drawGuides(
        canvas: Canvas, bitmap: Bitmap, selected: Int,
        cx: Float, cy: Float
    ) {
        val points = listOf(
            Triple(cx, cy, "CARA"),
            Triple(eyeLeftX, eyeLeftY, "OJO IZQ."),
            Triple(eyeRightX, eyeRightY, "OJO DER."),
            Triple(mouthX, mouthY, "BOCA")
        )
        points.forEachIndexed { i, (x, y, name) ->
            paint.color = if (i == selected) Color.YELLOW else Color.rgb(40, 230, 195)
            paint.style = Paint.Style.FILL
            paint.alpha = 255
            val px = x * bitmap.width
            val py = y * bitmap.height
            canvas.drawCircle(px, py, bitmap.width * .011f, paint)
            label.textSize = bitmap.width * .026f
            canvas.drawText(name, px + bitmap.width * .014f,
                py - bitmap.height * .016f, label)
        }
    }
}
