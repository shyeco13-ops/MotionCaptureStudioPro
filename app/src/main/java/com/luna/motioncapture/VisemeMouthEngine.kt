package com.luna.motioncapture

import android.graphics.Bitmap
import android.graphics.Canvas
import java.util.EnumMap
import kotlin.math.max

/**
 * Mouth atlas: additional user-supplied expressions, no synthetic teeth/tongue.
 * All photos remain local. CLOSED is always the original PNG.
 *
 * Visemes here are approximate visual targets from MediaPipe blendshapes,
 * NOT phoneme / speech recognition. No microphone or third-party API.
 */
class VisemeMouthEngine {
    enum class Slot(val label: String) {
        HALF("Semiabierta"),
        OPEN("Abierta"),
        O("Labios O"),
        SMILE("Sonrisa"),
        WIDE("Estirada"),
        LOWER("Labio inferior"),
        TEETH("Dientes")
    }

    private val images = EnumMap<Slot, Bitmap>(Slot::class.java)
    private val weights = EnumMap<Slot, Float>(Slot::class.java)
    private val renderer = MouthPhotoBlend()
    private var jaw = 0f
    private var smile = 0f
    private var pucker = 0f
    private var stretch = 0f
    private var lower = 0f
    private var teeth = 0f

    var blendIntensity: Float = 1f
        set(value) { field = value.coerceIn(.25f, 1.5f) }

    val hasLayers: Boolean get() = images.isNotEmpty()
    var sourceMode: MouthPhotoBlend.SourceMode
        get() = renderer.sourceMode
        set(value) { renderer.sourceMode = value }

    fun image(slot: Slot): Bitmap? = images[slot]
    fun setImage(slot: Slot, bitmap: Bitmap?) {
        if (bitmap == null) images.remove(slot) else images[slot] = bitmap
    }
    fun loadedDescription(): String = if (images.isEmpty()) "Sin capas"
        else Slot.entries.filter { images.containsKey(it) }.joinToString(", ") { it.label }

    private fun delta(frame: FaceFrame, neutral: FaceFrame, key: String): Float =
        (frame.expression(key) - neutral.expression(key) - .017f).coerceIn(0f, 1f)

    fun update(frame: FaceFrame, neutral: FaceFrame, jawTarget: Float,
               smileTarget: Float, puckerTarget: Float, lowerTarget: Float) {
        val leftStretch = delta(frame, neutral, "mouthStretchLeft")
        val rightStretch = delta(frame, neutral, "mouthStretchRight")
        val lowerLeft = delta(frame, neutral, "mouthLowerDownLeft")
        val lowerRight = delta(frame, neutral, "mouthLowerDownRight")
        val upperLeft = delta(frame, neutral, "mouthUpperUpLeft")
        val upperRight = delta(frame, neutral, "mouthUpperUpRight")
        val teethVisible = max(upperLeft, upperRight) * .62f +
            max(lowerLeft, lowerRight) * .38f

        blendTargets(
            jawTarget.coerceIn(0f, 1f),
            smileTarget.coerceIn(0f, 1f),
            puckerTarget.coerceIn(0f, 1f),
            max(leftStretch, rightStretch).times(2.2f).coerceIn(0f, 1f),
            max(lowerTarget, max(lowerLeft, lowerRight) * 1.3f).coerceIn(0f, 1f),
            teethVisible.coerceIn(0f, 1f),
            .44f
        )
    }

    fun updateManual(opening: Float) {
        // Mode used by the animated "Probar boca" button, even with no camera.
        blendTargets(opening.coerceIn(0f, 1f), smile, pucker, stretch, lower, teeth, .55f)
    }

    private fun blendTargets(j: Float, s: Float, p: Float, w: Float,
                             l: Float, t: Float, speed: Float) {
        fun filtered(old: Float, next: Float): Float = old + (next - old) * speed
        jaw = filtered(jaw, j)
        smile = filtered(smile, s)
        pucker = filtered(pucker, p)
        stretch = filtered(stretch, w)
        lower = filtered(lower, l)
        teeth = filtered(teeth, t)
        val open = ((jaw - .25f) / .72f).coerceIn(0f, 1f)
        val half = (jaw * 2.6f).coerceIn(0f, 1f) * (1f - open * .85f)
        val raw = mapOf(
            Slot.HALF to half,
            Slot.OPEN to open,
            Slot.O to (pucker * .96f).coerceIn(0f, 1f),
            Slot.SMILE to (smile * .93f).coerceIn(0f, 1f),
            Slot.WIDE to (stretch * .92f).coerceIn(0f, 1f),
            Slot.LOWER to (lower * max(.30f, jaw)).coerceIn(0f, 1f),
            Slot.TEETH to (teeth * max(.3f, jaw)).coerceIn(0f, 1f)
        )
        for (slot in Slot.entries) {
            val current = weights[slot] ?: 0f
            // Fast enough for speech; avoids hard image cuts.
            weights[slot] = filtered(current, raw[slot] ?: 0f).coerceIn(0f, 1f)
        }
    }

    fun render(
        canvas: Canvas, base: Bitmap, face: FaceFeatureAnimator,
        fw: Float, fh: Float, lowerCoverage: Float
    ) {
        if (images.isEmpty()) return
        // Pick the strongest two available shapes; missing shapes fall back
        // to OPEN/HALF instead of inventing facial details.
        val options = Slot.entries.mapNotNull { slot ->
            val photo = images[slot] ?: return@mapNotNull null
            var weight = weights[slot] ?: 0f
            // Compatibilidad con la versión previa: un solo PNG ABIERTO
            // también funciona sin obligar a cargar todos los demás visemas.
            if (slot == Slot.OPEN && !images.containsKey(Slot.HALF))
                weight = max(weight, jaw * .86f)
            if (slot == Slot.HALF && !images.containsKey(Slot.OPEN))
                weight = max(weight, jaw * .80f)
            Triple(slot, photo, weight)
        }.sortedByDescending { it.third }.take(2)
        if (options.isEmpty()) return

        // Preserve expressive upper/lower lip positions, but keep the patch
        // bounded to the mouth so cheek texture cannot get overwritten.
        for ((slot, photo, weight) in options.reversed()) {
            if (weight < .025f) continue
            val jawAmount = when (slot) {
                Slot.SMILE, Slot.WIDE, Slot.O -> max(.65f, jaw)
                Slot.LOWER, Slot.TEETH -> max(.67f, jaw)
                Slot.HALF -> max(.53f, jaw * .76f)
                Slot.OPEN -> max(.67f, jaw)
            }
            val aspectWidth = when (slot) {
                Slot.O -> .74f
                Slot.WIDE, Slot.SMILE -> 1.13f
                else -> 1f
            }
            renderer.draw(
                canvas, base, photo,
                face.mouthX, face.mouthY,
                face.upperLipY, face.lowerLipY,
                face.cornerLeftX, face.cornerLeftY,
                face.cornerRightX, face.cornerRightY,
                fw, fh, jawAmount, face.smile, face.lowerLipDrop,
                face.mouthWidth * aspectWidth, face.mouthGap,
                lowerCoverage, (weight * blendIntensity).coerceIn(0f, 1f)
            )
        }
    }
}
