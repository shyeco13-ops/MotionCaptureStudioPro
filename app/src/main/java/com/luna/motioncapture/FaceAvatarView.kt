package com.luna.motioncapture

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import org.json.JSONObject
import android.view.MotionEvent
import android.view.View
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin

/**
 * Rig facial 2D experimental para imágenes planas.
 * La imagen original siempre permanece intacta; solo se deforma la malla al dibujar.
 * El usuario puede ajustar el centro de la cara arrastrando la guía.
 */
class FaceAvatarView(context: Context) : View(context) {
    private var avatar: Bitmap? = null
    private var openedMouthPortrait: Bitmap? = null
    private val mouthPhotoBlend = MouthPhotoBlend()
    private var current: FaceFrame? = null
    private var neutral: FaceFrame? = null
    private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val guidePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(40, 230, 195)
        strokeWidth = 3f
        style = Paint.Style.STROKE
    }
    private val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(40, 230, 195)
        style = Paint.Style.FILL
    }
    private val bg = Paint()
    var showCameraBehind = false
        set(value) { field = value; invalidate() }
    var showGuides = true
        set(value) { field = value; invalidate() }
    var intensity = 0.9f
        set(value) { field = value.coerceIn(0f, 2f); invalidate() }
    private var centerX = 0.50f
    private var centerY = 0.46f
    private var faceWidth = 0.58f
    private var faceHeight = 0.54f
    private var layoutX = 0f
    private var layoutY = 0f
    private var layoutScale = 1f
    private val features = FaceFeatureAnimator()
    private var activeAnchor = 0

    val hasOpenMouthPhoto: Boolean
        get() = openedMouthPortrait != null

    fun setOpenMouthPhoto(bitmap: Bitmap?) {
        openedMouthPortrait = bitmap
        invalidate()
    }

    val mouthPhotoMode: String
        get() = mouthPhotoBlend.sourceMode.name

    fun setMouthPhotoMode(mode: String) {
        mouthPhotoBlend.sourceMode = try {
            MouthPhotoBlend.SourceMode.valueOf(mode)
        } catch (_: IllegalArgumentException) {
            MouthPhotoBlend.SourceMode.AUTO
        }
        invalidate()
    }

    fun nextMouthPhotoMode(): String {
        val modes = MouthPhotoBlend.SourceMode.entries
        val next = (modes.indexOf(mouthPhotoBlend.sourceMode) + 1) % modes.size
        mouthPhotoBlend.sourceMode = modes[next]
        invalidate()
        return when (mouthPhotoBlend.sourceMode) {
            MouthPhotoBlend.SourceMode.AUTO -> "Automático"
            MouthPhotoBlend.SourceMode.FULL_PORTRAIT -> "Rostro completo"
            MouthPhotoBlend.SourceMode.MOUTH_CROP -> "Recorte de boca"
        }
    }

    fun detectedMouthPhotoMode(): String {
        val original = avatar ?: return "Sin PNG"
        val alternative = openedMouthPortrait ?: return "Sin foto alternativa"
        return when (mouthPhotoBlend.effectiveMode(original, alternative)) {
            MouthPhotoBlend.SourceMode.AUTO -> "Automático"
            MouthPhotoBlend.SourceMode.FULL_PORTRAIT -> "Rostro completo"
            MouthPhotoBlend.SourceMode.MOUTH_CROP -> "Recorte de boca"
        }
    }

    fun saveRig(): String = JSONObject().apply {
        put("version", 2)
        put("centerX", centerX); put("centerY", centerY)
        put("faceWidth", faceWidth); put("faceHeight", faceHeight)
        put("features", features.toJson())
    }.toString()

    fun loadRig(raw: String): Boolean = try {
        val obj = JSONObject(raw)
        centerX = obj.optDouble("centerX", centerX.toDouble()).toFloat().coerceIn(.03f, .97f)
        centerY = obj.optDouble("centerY", centerY.toDouble()).toFloat().coerceIn(.03f, .97f)
        faceWidth = obj.optDouble("faceWidth", faceWidth.toDouble()).toFloat().coerceIn(.20f, .95f)
        faceHeight = obj.optDouble("faceHeight", faceHeight.toDouble()).toFloat().coerceIn(.20f, .95f)
        obj.optJSONObject("features")?.let { features.fromJson(it) }
        invalidate()
        true
    } catch (_: Exception) { false }

    var expressionSensitivity: Float
        get() = features.expressionGain
        set(value) { features.expressionGain = value; invalidate() }

    private var presetIndex = 0
    fun nextExpressionPreset(): String {
        val presets = arrayOf("AUTO", "FELIZ", "SORPRESA", "ENOJO")
        presetIndex = (presetIndex + 1) % presets.size
        features.selectExpression(presets[presetIndex])
        invalidate()
        return presets[presetIndex]
    }

    val expressionReadout: String
        get() = "Feliz ${(features.happy * 100).toInt()}% · " +
            "Sorpresa ${(features.surprised * 100).toInt()}% · " +
            "Enojo ${(features.angry * 100).toInt()}%"

    val mouthOpening: Float
        get() = features.jaw
    val detectedJaw: Float
        get() = features.mouthBlendshape
    val detectedLipDelta: Float
        get() = features.mouthLandmark

    /** Boca fija de prueba: así se diferencia un problema de dibujo de uno de detección. */
    fun previewMouth(value: Float?) {
        features.setManualMouth(value)
        invalidate()
    }

    var lipWidth: Float
        get() = features.mouthWidth
        set(value) { features.mouthWidth = value; invalidate() }
    var lipGap: Float
        get() = features.mouthGap
        set(value) { features.mouthGap = value; invalidate() }

    fun toggleMouthInterior(): Boolean {
        features.mouthInteriorVisible = !features.mouthInteriorVisible
        invalidate()
        return features.mouthInteriorVisible
    }

    var eyeSensitivity: Float
        get() = features.eyeGain
        set(value) { features.eyeGain = value.coerceIn(.25f, 2.5f); invalidate() }
    var mouthSensitivity: Float
        get() = features.mouthGain
        set(value) { features.mouthGain = value.coerceIn(.25f, 2.5f); invalidate() }

    fun nextAnchor(): String {
        activeAnchor = (activeAnchor + 1) % 8
        showGuides = true
        invalidate()
        return when (activeAnchor) {
            1 -> "OJO IZQUIERDO"
            2 -> "OJO DERECHO"
            3 -> "BOCA"
            4 -> "LABIO SUPERIOR"
            5 -> "LABIO INFERIOR"
            6 -> "COMISURA IZQUIERDA"
            7 -> "COMISURA DERECHA"
            else -> "ROSTRO"
        }
    }

    fun setAvatar(value: Bitmap) {
        avatar = value
        openedMouthPortrait = null
        neutral = null
        current = null
        setPresetPortrait()
        invalidate()
    }

    fun updateFace(frame: FaceFrame) {
        current = frame
        if (neutral == null) neutral = frame
        neutral?.let { features.update(frame, it, intensity) }
        invalidate()
    }

    fun calibrate(): Boolean {
        val frame = current ?: return false
        neutral = frame
        features.resetExpressions()
        invalidate()
        return true
    }

    fun setPresetPortrait() {
        centerX = .50f
        centerY = .46f
        faceWidth = .58f
        faceHeight = .54f
        features.reset(centerX, centerY, faceWidth, faceHeight)
        activeAnchor = 0
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!showGuides || layoutScale <= 0f || avatar == null) return false
        if (event.actionMasked == MotionEvent.ACTION_DOWN || event.actionMasked == MotionEvent.ACTION_MOVE) {
            val bmp = avatar ?: return false
            val nx = ((event.x - layoutX) / (layoutScale * bmp.width)).coerceIn(.03f, .97f)
            val ny = ((event.y - layoutY) / (layoutScale * bmp.height)).coerceIn(.03f, .97f)
            if (activeAnchor == 0) {
                features.translate(nx - centerX, ny - centerY)
                centerX = nx
                centerY = ny
            } else {
                features.setAnchor(activeAnchor, nx, ny)
            }
            invalidate()
            return true
        }
        return event.actionMasked == MotionEvent.ACTION_UP
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!showCameraBehind) {
            bg.shader = LinearGradient(0f, 0f, 0f, height.toFloat(),
                Color.rgb(14, 24, 39), Color.rgb(32, 55, 68), Shader.TileMode.CLAMP)
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bg)
            bg.shader = null
        }
        val bitmap = avatar ?: return
        layoutScale = min(width * .94f / bitmap.width, height * .90f / bitmap.height)
        layoutX = (width - bitmap.width * layoutScale) / 2f
        layoutY = (height - bitmap.height * layoutScale) / 2f
        canvas.save()
        canvas.translate(layoutX, layoutY)
        canvas.scale(layoutScale, layoutScale)
        val vertices = makeMesh(bitmap)
        canvas.drawBitmapMesh(bitmap, COLS, ROWS, vertices, 0, null, 0, imagePaint)
        features.draw(canvas, bitmap, faceWidth, faceHeight, openedMouthPortrait == null)
        openedMouthPortrait?.let { opened ->
            mouthPhotoBlend.draw(
                canvas, bitmap, opened,
                features.mouthX, features.mouthY,
                features.upperLipY, features.lowerLipY,
                features.cornerLeftX, features.cornerLeftY,
                features.cornerRightX, features.cornerRightY,
                faceWidth, faceHeight, features.jaw,
                features.smile, features.lowerLipDrop,
                features.mouthWidth, features.mouthGap
            )
        }
        if (showGuides) {
            guidePaint.strokeWidth = bitmap.width * .003f
            canvas.drawOval(
                (centerX - faceWidth / 2f) * bitmap.width,
                (centerY - faceHeight / 2f) * bitmap.height,
                (centerX + faceWidth / 2f) * bitmap.width,
                (centerY + faceHeight / 2f) * bitmap.height,
                guidePaint
            )
            features.drawGuides(canvas, bitmap, activeAnchor, centerX, centerY)
        }
        canvas.restore()
    }

    private data class Warp(val x: Float, val y: Float, val dx: Float, val dy: Float, val radius: Float)

    private fun makeMesh(bitmap: Bitmap): FloatArray {
        val vertices = FloatArray((COLS + 1) * (ROWS + 1) * 2)
        val live = current
        val base = neutral
        val warps = mutableListOf<Warp>()
        var headDx = 0f
        var headDy = 0f
        var headAngle = 0f

        if (live != null && base != null) {
            val eyeL = live.point(33)
            val eyeR = live.point(263)
            val baseL = base.point(33)
            val baseR = base.point(263)
            val nose = live.point(4)
            val baseNose = base.point(4)
            if (eyeL != null && eyeR != null && baseL != null && baseR != null
                && nose != null && baseNose != null) {
                val width = kotlin.math.abs(baseR.x - baseL.x).coerceAtLeast(.07f)
                headDx = ((nose.x - baseNose.x) / width * .052f * intensity).coerceIn(-.10f, .10f)
                headDy = ((nose.y - baseNose.y) / width * .052f * intensity).coerceIn(-.10f, .10f)
                headAngle = (atan2(eyeR.y - eyeL.y, eyeR.x - eyeL.x) -
                    atan2(baseR.y - baseL.y, baseR.x - baseL.x)).coerceIn(-.35f, .35f) * intensity
            }
            fun delta(key: String): Float =
                (live.expression(key) - base.expression(key)).coerceIn(0f, 1f) * intensity
            val smileL = delta("mouthSmileLeft")
            val smileR = delta("mouthSmileRight")

            fun warp(fx: Float, fy: Float, dx: Float, dy: Float, radius: Float = .08f) {
                warps.add(Warp(centerX + fx * faceWidth, centerY + fy * faceHeight,
                    dx * faceWidth, dy * faceHeight, radius))
            }
            // La cara del PNG debe ser frontal. Izquierda/derecha corresponden a la pantalla.
            // Expresiones regionales suavizadas en el retargeter.
            warp(-.21f, -.20f, 0f, -.045f * features.browLeft, .083f)
            warp(.21f, -.20f, 0f, -.045f * features.browRight, .083f)
            // Ceño fruncido: extremos internos de cejas hacia adentro y abajo.
            warp(-.095f, -.18f, .018f * features.browDown,
                .041f * features.browDown, .060f)
            warp(.095f, -.18f, -.018f * features.browDown,
                .041f * features.browDown, .060f)
            // Ojos sorprendidos: abrir sin dibujar óvalos artificiales.
            warp(-.20f, -.083f, 0f, -.026f * features.eyeWide, .063f)
            warp(.20f, -.083f, 0f, -.026f * features.eyeWide, .063f)
            warp(-.20f, .012f, 0f, .020f * features.eyeWide, .062f)
            warp(.20f, .012f, 0f, .020f * features.eyeWide, .062f)
            warp(-.27f, .10f, -.010f * features.cheekLeft,
                -.025f * features.cheekLeft, .085f)
            warp(.27f, .10f, .010f * features.cheekRight,
                -.025f * features.cheekRight, .085f)
            warp(-.11f, .08f, -.010f * features.noseLeft,
                -.011f * features.noseLeft, .045f)
            warp(.11f, .08f, .010f * features.noseRight,
                -.011f * features.noseRight, .045f)
            warp(-.17f, .21f,
                -.033f * smileL + .030f * features.pucker,
                -.026f * smileL + .041f * features.mouthFrown, .075f)
            warp(.17f, .21f,
                .033f * smileR - .030f * features.pucker,
                -.026f * smileR + .041f * features.mouthFrown, .075f)
            // Labio inferior y barbilla bajan de forma localizada.
            warp(0f, .34f, 0f,
                .045f * features.lowerLipDrop, .075f)
            // Boca: la deformación se calcula por vértice en el ancla real del PNG.
        }

        for (r in 0..ROWS) for (c in 0..COLS) {
            val x = c.toFloat() / COLS
            val y = r.toFloat() / ROWS
            val distX = (x - centerX) / faceWidth
            val distY = (y - centerY) / faceHeight
            val mask = exp(-((distX * distX + distY * distY) / .55f)).coerceIn(0f, 1f)
            val rx = x - centerX
            val ry = y - centerY
            var moveX = mask * (headDx + rx * (cos(headAngle) - 1f) - ry * sin(headAngle))
            var moveY = mask * (headDy + rx * sin(headAngle) + ry * (cos(headAngle) - 1f))
            var weight = .75f
            warps.forEach { warp ->
                val xx = (x - warp.x) / warp.radius
                val yy = (y - warp.y) / warp.radius
                val influence = exp(-(xx * xx + yy * yy) / 2f)
                weight += influence
                moveX += warp.dx * influence
                moveY += warp.dy * influence
            }
            val i = (r * (COLS + 1) + c) * 2
            vertices[i] = (x + moveX / weight) * bitmap.width
            vertices[i + 1] = (y + moveY / weight +
                features.eyeDisplacementY(x, y, faceWidth, faceHeight) +
                if (openedMouthPortrait == null)
                    features.mouthDisplacementY(x, y, faceWidth, faceHeight) else 0f) * bitmap.height
        }
        return vertices
    }

    companion object {
        private const val COLS = 56
        private const val ROWS = 80
    }
}
