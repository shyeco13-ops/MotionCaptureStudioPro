package com.luna.motioncapture

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max

/**
 * Natural Face Motion — Alpha 0.3
 *
 * El párpado se simula con deformación de la textura original del PNG (la malla
 * del AvatarView); no se cubre el ojo con un óvalo de color artificial.
 *
 * La boca usa un contorno de labios variable, no un círculo negro. Como el PNG
 * contiene solo una vista estática, el interior bucal es una aproximación.
 * Todo se procesa localmente y no se modifica la fotografía original.
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

    var eyeGain = 1f
    var mouthGain = 1f

    var jaw: Float = 0f
        private set
    var blinkLeft: Float = 0f
        private set
    var blinkRight: Float = 0f
        private set
    var smile: Float = 0f
        private set

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val guidePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        setShadowLayer(3f, 1f, 1f, Color.BLACK)
    }
    private val path = Path()

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
        when (index) {
            1 -> { eyeLeftX = px; eyeLeftY = py }
            2 -> { eyeRightX = px; eyeRightY = py }
            3 -> { mouthX = px; mouthY = py }
        }
    }

    fun update(frame: FaceFrame, neutral: FaceFrame, intensity: Float) {
        fun target(key: String, deadzone: Float, gain: Float): Float =
            ((frame.expression(key) - neutral.expression(key) - deadzone) *
                gain * intensity).coerceIn(0f, 1f)
        // Apertura relativamente suave; párpados rápidos para no perder parpadeos cortos.
        val open = target("jawOpen", .025f, 1.25f * mouthGain)
        val left = target("eyeBlinkLeft", .025f, 1.45f * eyeGain)
        val right = target("eyeBlinkRight", .025f, 1.45f * eyeGain)
        val smiling = max(
            target("mouthSmileLeft", .03f, 1.10f),
            target("mouthSmileRight", .03f, 1.10f)
        )
        jaw = jaw * .53f + open * .47f
        blinkLeft = blinkLeft * .24f + left * .76f
        blinkRight = blinkRight * .24f + right * .76f
        smile = smile * .66f + smiling * .34f
    }

    /**
     * Desplazamiento vertical de los vértices de la malla original para contraer
     * la región ocular. Los píxeles del ojo se comprimen y la piel vecina ocupa
     * su lugar; no dibujamos formas sólidas encima.
     */
    fun eyeDisplacementY(x: Float, y: Float, fw: Float, fh: Float): Float {
        fun around(ex: Float, ey: Float, blink: Float): Float {
            if (blink < .025f) return 0f
            val rx = (x - ex) / max(.0001f, fw * .175f)
            val ry = (y - ey) / max(.0001f, fh * .095f)
            val influence = exp(-(rx * rx * 1.25f + ry * ry * .70f))
            return -(y - ey) * blink * .92f * influence
        }
        return around(eyeLeftX, eyeLeftY, blinkLeft) +
            around(eyeRightX, eyeRightY, blinkRight)
    }

    private fun pixel(bitmap: Bitmap, x: Float, y: Float): Int {
        val px = (x.coerceIn(0f, 1f) * (bitmap.width - 1)).toInt()
        val py = (y.coerceIn(0f, 1f) * (bitmap.height - 1)).toInt()
        return bitmap.getPixel(px, py)
    }

    private fun shade(color: Int, amount: Float): Int {
        return Color.rgb(
            (Color.red(color) * amount).toInt().coerceIn(0, 255),
            (Color.green(color) * amount).toInt().coerceIn(0, 255),
            (Color.blue(color) * amount).toInt().coerceIn(0, 255)
        )
    }

    fun draw(canvas: Canvas, bitmap: Bitmap, fwFraction: Float, fhFraction: Float) {
        val fw = fwFraction * bitmap.width
        val fh = fhFraction * bitmap.height

        // Las arrugas del párpado son muy finas. La contracción visible la hace
        // la malla, que mantiene los colores y textura de la foto.
        fun crease(ex: Float, ey: Float, blink: Float) {
            if (blink < .28f) return
            val px = ex * bitmap.width
            val py = ey * bitmap.height
            val w = fw * .095f
            val skin = pixel(bitmap, ex, ey - fhFraction * .095f)
            paint.shader = null
            paint.color = shade(skin, .66f)
            paint.alpha = ((blink - .28f) * 125f).toInt().coerceIn(0, 100)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = max(1f, fw * .0045f)
            path.reset()
            path.moveTo(px - w, py + fh * .003f)
            path.quadTo(px, py + fh * .018f, px + w, py + fh * .003f)
            canvas.drawPath(path, paint)
            paint.style = Paint.Style.FILL
            paint.alpha = 255
        }
        crease(eyeLeftX, eyeLeftY, blinkLeft)
        crease(eyeRightX, eyeRightY, blinkRight)

        if (jaw < .10f) return

        val cx = mouthX * bitmap.width
        val cy = mouthY * bitmap.height
        // Comisuras a los lados y borde superior en forma de arco de Cupido.
        val hw = fw * (.098f + smile * .025f)
        val opening = (jaw - .07f).coerceIn(0f, 1f)
        val h = fh * (.008f + opening * .070f)
        val corner = fh * .010f - smile * fh * .026f

        path.reset()
        path.moveTo(cx - hw, cy + corner)
        path.cubicTo(cx - hw * .65f, cy - h * .45f,
            cx - hw * .22f, cy - h * .72f, cx, cy - h * .62f)
        path.cubicTo(cx + hw * .26f, cy - h * .74f,
            cx + hw * .70f, cy - h * .37f, cx + hw, cy + corner)
        path.cubicTo(cx + hw * .72f, cy + h * .65f,
            cx + hw * .27f, cy + h * 1.10f, cx, cy + h)
        path.cubicTo(cx - hw * .28f, cy + h * 1.12f,
            cx - hw * .72f, cy + h * .63f, cx - hw, cy + corner)
        path.close()

        val lipSample = pixel(bitmap, mouthX, mouthY)
        val dark = shade(lipSample, .30f)
        val bottom = shade(lipSample, .43f)
        paint.shader = LinearGradient(cx, cy - h, cx, cy + h,
            intArrayOf(dark, shade(dark, .77f), bottom),
            floatArrayOf(0f, .45f, 1f), Shader.TileMode.CLAMP)
        paint.style = Paint.Style.FILL
        paint.alpha = (opening * 250f).toInt().coerceIn(0, 245)
        canvas.drawPath(path, paint)
        paint.shader = null

        // Perfil del labio inferior: color tomado de la foto, no borde morado.
        paint.color = shade(lipSample, .75f)
        paint.alpha = (opening * 115f).toInt().coerceIn(0, 115)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = max(1f, fw * .008f)
        canvas.drawPath(path, paint)
        paint.alpha = 255
        paint.style = Paint.Style.FILL
    }

    fun drawGuides(canvas: Canvas, bitmap: Bitmap, selected: Int, cx: Float, cy: Float) {
        val points = listOf(
            Triple(cx, cy, "ROSTRO"),
            Triple(eyeLeftX, eyeLeftY, "OJO IZQ."),
            Triple(eyeRightX, eyeRightY, "OJO DER."),
            Triple(mouthX, mouthY, "BOCA")
        )
        points.forEachIndexed { i, (x, y, name) ->
            paint.shader = null
            paint.alpha = 255
            paint.style = Paint.Style.FILL
            paint.color = if (i == selected) Color.YELLOW else Color.rgb(40, 230, 195)
            val px = x * bitmap.width
            val py = y * bitmap.height
            canvas.drawCircle(px, py, bitmap.width * .011f, paint)
            guidePaint.textSize = bitmap.width * .025f
            canvas.drawText(name, px + bitmap.width * .014f,
                py - bitmap.height * .014f, guidePaint)
        }
    }
}
