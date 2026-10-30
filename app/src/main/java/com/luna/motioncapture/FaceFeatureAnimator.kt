package com.luna.motioncapture

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import org.json.JSONObject
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
    var upperLipX = .50f
        private set
    var upperLipY = .574f
        private set
    var lowerLipX = .50f
        private set
    var lowerLipY = .620f
        private set
    var cornerLeftX = .432f
        private set
    var cornerLeftY = .595f
        private set
    var cornerRightX = .568f
        private set
    var cornerRightY = .595f
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
    var cheekLeft = 0f
        private set
    var cheekRight = 0f
        private set
    var noseLeft = 0f
        private set
    var noseRight = 0f
        private set
    var browLeft = 0f
        private set
    var browRight = 0f
        private set
    var pucker = 0f
        private set
    var mouthBlendshape: Float = 0f
        private set
    var mouthLandmark: Float = 0f
        private set
    private var manualMouth: Float? = null

    /** Prueba mecánica sin depender de la cámara. null vuelve a captura automática. */
    fun setManualMouth(value: Float?) {
        manualMouth = value?.coerceIn(0f, 1f)
        if (manualMouth != null) jaw = manualMouth!!
    }

    private fun openingRatio(frame: FaceFrame): Float {
        val top = frame.point(13) ?: return 0f
        val bottom = frame.point(14) ?: return 0f
        val left = frame.point(33) ?: return 0f
        val right = frame.point(263) ?: return 0f
        val eyeDistance = kotlin.math.hypot(right.x - left.x, right.y - left.y)
            .coerceAtLeast(.09f)
        val lipDistance = kotlin.math.hypot(bottom.x - top.x, bottom.y - top.y)
        return (lipDistance / eyeDistance).coerceIn(0f, 1f)
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val guidePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        setShadowLayer(3f, 1f, 1f, Color.BLACK)
    }
    private val path = Path()
    private val mouthRenderer = NaturalMouthRenderer()
    private val expressions = ExpressionStates()

    var expressionGain: Float
        get() = expressions.gain
        set(value) { expressions.gain = value }

    val happy: Float get() = expressions.happy
    val surprised: Float get() = expressions.surprised
    val angry: Float get() = expressions.angry
    val eyeWide: Float get() = expressions.eyeWide
    val browDown: Float get() = expressions.browDown
    val mouthFrown: Float get() = expressions.mouthFrown
    val lowerLipDrop: Float get() = expressions.lowerLip
    val expressionPreset: String get() = expressions.preset.name

    fun selectExpression(name: String) {
        val preset = try { ExpressionStates.Preset.valueOf(name) }
            catch (_: IllegalArgumentException) { ExpressionStates.Preset.AUTO }
        expressions.setPreset(preset)
        if (preset == ExpressionStates.Preset.AUTO) {
            expressions.reset()
            smile = 0f; browLeft = 0f; browRight = 0f
            cheekLeft = 0f; cheekRight = 0f
            jaw = 0f; blinkLeft = 0f; blinkRight = 0f
        } else {
            applyExpressionPreview()
        }
    }

    private fun applyExpressionPreview() {
        smile = max(smile, expressions.happy * .88f)
        cheekLeft = max(cheekLeft, expressions.happy * .80f)
        cheekRight = max(cheekRight, expressions.happy * .80f)
        browLeft = max(browLeft, expressions.surprised * .85f)
        browRight = max(browRight, expressions.surprised * .85f)
        jaw = max(jaw, expressions.surprised * .62f)
        noseLeft = max(noseLeft, expressions.angry * .22f)
        noseRight = max(noseRight, expressions.angry * .22f)
    }

    var mouthWidth: Float
        get() = mouthRenderer.widthScale
        set(value) { mouthRenderer.widthScale = value.coerceIn(.55f, 1.55f) }
    var mouthGap: Float
        get() = mouthRenderer.gapScale
        set(value) { mouthRenderer.gapScale = value.coerceIn(.15f, 1.55f) }
    var mouthInteriorVisible: Boolean
        get() = mouthRenderer.showInterior
        set(value) { mouthRenderer.showInterior = value }

    /** Deformación del PNG original alrededor del ancla de boca, no una figura pintada. */
    fun mouthDisplacementY(x: Float, y: Float, faceWidth: Float, faceHeight: Float): Float {
        if (jaw < .015f) return 0f
        val mouthWidth = (cornerRightX - cornerLeftX).let { kotlin.math.abs(it) }
            .coerceIn(faceWidth * .08f, faceWidth * .50f)
        val dx = (x - mouthX) / (mouthWidth * .65f).coerceAtLeast(.005f)
        val dy = (y - mouthY) / (faceHeight * .12f).coerceAtLeast(.005f)
        val influence = exp(-dx * dx * 1.20f - dy * dy * .78f)
        // Los labios se separan unos píxeles y el efecto se desvanece en la piel.
        val upperLower = dy / (kotlin.math.abs(dy) + .28f)
        return upperLower * influence * faceHeight * .032f * jaw * mouthGap
    }

    fun reset(cx: Float, cy: Float, fw: Float, fh: Float) {
        eyeLeftX = cx - .20f * fw
        eyeLeftY = cy - .04f * fh
        eyeRightX = cx + .20f * fw
        eyeRightY = cy - .04f * fh
        mouthX = cx
        mouthY = cy + .25f * fh
        upperLipX = mouthX
        upperLipY = mouthY - .044f * fh
        lowerLipX = mouthX
        lowerLipY = mouthY + .050f * fh
        cornerLeftX = mouthX - .132f * fw
        cornerLeftY = mouthY
        cornerRightX = mouthX + .132f * fw
        cornerRightY = mouthY
        resetExpressions()
    }

    fun resetExpressions() {
        expressions.reset()
        jaw = 0f
        blinkLeft = 0f
        blinkRight = 0f
        smile = 0f
        cheekLeft = 0f; cheekRight = 0f
        noseLeft = 0f; noseRight = 0f
        browLeft = 0f; browRight = 0f
        pucker = 0f
        mouthBlendshape = 0f
        mouthLandmark = 0f
    }

    fun translate(dx: Float, dy: Float) {
        eyeLeftX = (eyeLeftX + dx).coerceIn(.03f, .97f)
        eyeLeftY = (eyeLeftY + dy).coerceIn(.03f, .97f)
        eyeRightX = (eyeRightX + dx).coerceIn(.03f, .97f)
        eyeRightY = (eyeRightY + dy).coerceIn(.03f, .97f)
        mouthX = (mouthX + dx).coerceIn(.03f, .97f)
        mouthY = (mouthY + dy).coerceIn(.03f, .97f)
        upperLipX = (upperLipX + dx).coerceIn(.03f, .97f)
        upperLipY = (upperLipY + dy).coerceIn(.03f, .97f)
        lowerLipX = (lowerLipX + dx).coerceIn(.03f, .97f)
        lowerLipY = (lowerLipY + dy).coerceIn(.03f, .97f)
        cornerLeftX = (cornerLeftX + dx).coerceIn(.03f, .97f)
        cornerLeftY = (cornerLeftY + dy).coerceIn(.03f, .97f)
        cornerRightX = (cornerRightX + dx).coerceIn(.03f, .97f)
        cornerRightY = (cornerRightY + dy).coerceIn(.03f, .97f)
    }

    fun setAnchor(index: Int, x: Float, y: Float) {
        val px = x.coerceIn(.03f, .97f)
        val py = y.coerceIn(.03f, .97f)
        when (index) {
            1 -> { eyeLeftX = px; eyeLeftY = py }
            2 -> { eyeRightX = px; eyeRightY = py }
            3 -> {
                val dx = px - mouthX
                val dy = py - mouthY
                mouthX = px; mouthY = py
                upperLipX = (upperLipX + dx).coerceIn(.03f, .97f)
                upperLipY = (upperLipY + dy).coerceIn(.03f, .97f)
                lowerLipX = (lowerLipX + dx).coerceIn(.03f, .97f)
                lowerLipY = (lowerLipY + dy).coerceIn(.03f, .97f)
                cornerLeftX = (cornerLeftX + dx).coerceIn(.03f, .97f)
                cornerLeftY = (cornerLeftY + dy).coerceIn(.03f, .97f)
                cornerRightX = (cornerRightX + dx).coerceIn(.03f, .97f)
                cornerRightY = (cornerRightY + dy).coerceIn(.03f, .97f)
            }
            4 -> { upperLipX = px; upperLipY = py }
            5 -> { lowerLipX = px; lowerLipY = py }
            6 -> { cornerLeftX = px; cornerLeftY = py }
            7 -> { cornerRightX = px; cornerRightY = py }
        }
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("eyeLeftX", eyeLeftX); put("eyeLeftY", eyeLeftY)
        put("eyeRightX", eyeRightX); put("eyeRightY", eyeRightY)
        put("mouthX", mouthX); put("mouthY", mouthY)
        put("upperLipX", upperLipX); put("upperLipY", upperLipY)
        put("lowerLipX", lowerLipX); put("lowerLipY", lowerLipY)
        put("cornerLeftX", cornerLeftX); put("cornerLeftY", cornerLeftY)
        put("cornerRightX", cornerRightX); put("cornerRightY", cornerRightY)
    }

    fun fromJson(json: JSONObject) {
        fun get(key: String, fallback: Float): Float =
            json.optDouble(key, fallback.toDouble()).toFloat()
                .takeIf { it.isFinite() }?.coerceIn(.03f, .97f) ?: fallback
        eyeLeftX = get("eyeLeftX", eyeLeftX); eyeLeftY = get("eyeLeftY", eyeLeftY)
        eyeRightX = get("eyeRightX", eyeRightX); eyeRightY = get("eyeRightY", eyeRightY)
        mouthX = get("mouthX", mouthX); mouthY = get("mouthY", mouthY)
        upperLipX = get("upperLipX", upperLipX); upperLipY = get("upperLipY", upperLipY)
        lowerLipX = get("lowerLipX", lowerLipX); lowerLipY = get("lowerLipY", lowerLipY)
        cornerLeftX = get("cornerLeftX", cornerLeftX); cornerLeftY = get("cornerLeftY", cornerLeftY)
        cornerRightX = get("cornerRightX", cornerRightX); cornerRightY = get("cornerRightY", cornerRightY)
    }

    fun update(frame: FaceFrame, neutral: FaceFrame, intensity: Float) {
        expressions.update(frame, neutral)
        fun target(key: String, deadzone: Float, gain: Float): Float =
            ((frame.expression(key) - neutral.expression(key) - deadzone) *
                gain * intensity).coerceIn(0f, 1f)
        // Usar dos señales: jawOpen de MediaPipe y distancia labial 13-14.
        // Antes, restar una neutral alta y exigir 10% hacía la boca invisible.
        val jawBlend = frame.expression("jawOpen")
        val neutralBlend = neutral.expression("jawOpen")
        val jawDelta = (jawBlend - neutralBlend).coerceAtLeast(0f)
        val ratio = openingRatio(frame)
        val baseRatio = openingRatio(neutral)
        val ratioDelta = (ratio - baseRatio).coerceAtLeast(0f)
        mouthBlendshape = jawBlend
        mouthLandmark = ratioDelta

        val fromBlend = (jawDelta * 2.6f - .018f).coerceIn(0f, 1f)
        val fromLandmarks = ((ratioDelta - .010f) * 5.7f).coerceIn(0f, 1f)
        val open = (max(fromBlend, fromLandmarks) * mouthGain * intensity)
            .coerceIn(0f, 1f)
        // Subida rápida, bajada progresiva. El control manual es prioritario.
        val source = manualMouth ?: open
        val follow = if (source > jaw) .62f else .32f
        jaw = (jaw + (source - jaw) * follow).coerceIn(0f, 1f)
        val left = target("eyeBlinkLeft", .025f, 1.45f * eyeGain)
        val right = target("eyeBlinkRight", .025f, 1.45f * eyeGain)
        val smiling = max(
            target("mouthSmileLeft", .03f, 1.10f),
            target("mouthSmileRight", .03f, 1.10f)
        )
        blinkLeft = blinkLeft * .24f + left * .76f
        blinkRight = blinkRight * .24f + right * .76f
        smile = smile * .66f + smiling * .34f
        // Curvas suaves y separadas: mejillas y cejas responden más gradualmente.
        cheekLeft = cheekLeft * .68f + .32f * max(
            target("cheekSquintLeft", .025f, 1f),
            target("mouthSmileLeft", .03f, .65f)
        )
        cheekRight = cheekRight * .68f + .32f * max(
            target("cheekSquintRight", .025f, 1f),
            target("mouthSmileRight", .03f, .65f)
        )
        browLeft = browLeft * .62f + .38f * max(
            target("browOuterUpLeft", .02f, 1.1f),
            target("browInnerUp", .03f, .75f)
        )
        browRight = browRight * .62f + .38f * max(
            target("browOuterUpRight", .02f, 1.1f),
            target("browInnerUp", .03f, .75f)
        )
        noseLeft = noseLeft * .74f + .26f *
            target("noseSneerLeft", .025f, .7f)
        noseRight = noseRight * .74f + .26f *
            target("noseSneerRight", .025f, .7f)
        pucker = pucker * .60f + .40f * max(
            target("mouthPucker", .02f, 1f),
            target("mouthFunnel", .02f, .65f)
        )
        if (expressions.preset != ExpressionStates.Preset.AUTO) {
            applyExpressionPreview()
        } else {
            // Estado automático: la sonrisa suavizada acompaña mejillas y boca.
            smile = max(smile, expressions.happy * .65f)
            cheekLeft = max(cheekLeft, expressions.happy * .45f)
            cheekRight = max(cheekRight, expressions.happy * .45f)
            browLeft = max(browLeft, expressions.surprised * .48f)
            browRight = max(browRight, expressions.surprised * .48f)
        }
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

    fun draw(canvas: Canvas, bitmap: Bitmap, fwFraction: Float, fhFraction: Float, drawMouth: Boolean = true) {
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

        if (drawMouth) mouthRenderer.draw(
            canvas, bitmap, mouthX, mouthY, fwFraction, fhFraction, jaw, smile
        )
    }

    fun drawGuides(canvas: Canvas, bitmap: Bitmap, selected: Int, cx: Float, cy: Float) {
        val points = listOf(
            Triple(cx, cy, "ROSTRO"),
            Triple(eyeLeftX, eyeLeftY, "OJO IZQ."),
            Triple(eyeRightX, eyeRightY, "OJO DER."),
            Triple(mouthX, mouthY, "BOCA"),
            Triple(upperLipX, upperLipY, "LABIO SUP."),
            Triple(lowerLipX, lowerLipY, "LABIO INF."),
            Triple(cornerLeftX, cornerLeftY, "COM. IZQ."),
            Triple(cornerRightX, cornerRightY, "COM. DER.")
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
