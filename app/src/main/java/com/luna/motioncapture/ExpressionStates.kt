package com.luna.motioncapture

import kotlin.math.max
import kotlin.math.min

/**
 * Alpha 0.5.2. Retargeter de expresiones con valores normalizados.
 * El modelo entrega coeficientes faciales; estos estados son aproximaciones
 * visuales, NO clasificación de emociones reales de una persona.
 */
class ExpressionStates {
    enum class Preset { AUTO, FELIZ, SORPRESA, ENOJO }
    var preset: Preset = Preset.AUTO
        private set
    var gain: Float = 1f
        set(value) { field = value.coerceIn(.25f, 2f) }

    var happy = 0f
        private set
    var surprised = 0f
        private set
    var angry = 0f
        private set
    var eyeWide = 0f
        private set
    var browDown = 0f
        private set
    var mouthFrown = 0f
        private set
    var lowerLip = 0f
        private set

    fun setPreset(value: Preset) {
        preset = value
        if (value != Preset.AUTO) setTargets(
            if (value == Preset.FELIZ) .8f else 0f,
            if (value == Preset.SORPRESA) .86f else 0f,
            if (value == Preset.ENOJO) .8f else 0f,
            immediate = true
        )
    }

    fun reset() {
        happy = 0f; surprised = 0f; angry = 0f
        eyeWide = 0f; browDown = 0f; mouthFrown = 0f; lowerLip = 0f
    }

    private fun setTargets(h: Float, s: Float, a: Float, immediate: Boolean) {
        val t = if (immediate) 1f else .38f
        happy += (h * gain - happy) * t
        surprised += (s * gain - surprised) * t
        angry += (a * gain - angry) * t
        happy = happy.coerceIn(0f, 1f)
        surprised = surprised.coerceIn(0f, 1f)
        angry = angry.coerceIn(0f, 1f)
        eyeWide += ((surprised * .90f) - eyeWide) * t
        browDown += ((angry * .85f) - browDown) * t
        mouthFrown += ((angry * .65f) - mouthFrown) * t
        lowerLip += ((surprised * .72f + happy * .12f) - lowerLip) * t
    }

    fun update(frame: FaceFrame, neutral: FaceFrame) {
        if (preset != Preset.AUTO) {
            setTargets(
                if (preset == Preset.FELIZ) .8f else 0f,
                if (preset == Preset.SORPRESA) .86f else 0f,
                if (preset == Preset.ENOJO) .8f else 0f, immediate = false
            )
            return
        }
        fun delta(name: String, amplification: Float): Float =
            ((frame.expression(name) - neutral.expression(name) - .022f) *
                amplification).coerceIn(0f, 1f)
        val smile = (delta("mouthSmileLeft", 1.35f) +
            delta("mouthSmileRight", 1.35f)) * .5f
        val cheek = (delta("cheekSquintLeft", 1.15f) +
            delta("cheekSquintRight", 1.15f)) * .5f
        val wide = max(delta("eyeWideLeft", 1.5f), delta("eyeWideRight", 1.5f))
        val browsUp = max(delta("browInnerUp", 1.3f),
            max(delta("browOuterUpLeft", 1f), delta("browOuterUpRight", 1f)))
        val browDownL = delta("browDownLeft", 1.4f)
        val browDownR = delta("browDownRight", 1.4f)
        val frown = max(delta("mouthFrownLeft", 1.25f),
            delta("mouthFrownRight", 1.25f))
        val nose = max(delta("noseSneerLeft", 1f), delta("noseSneerRight", 1f))
        val h = (smile * .76f + cheek * .36f).coerceIn(0f, 1f)
        val s = (wide * .57f + browsUp * .46f).coerceIn(0f, 1f)
        val a = ((browDownL + browDownR) * .34f + frown * .27f +
            nose * .18f).coerceIn(0f, 1f)
        setTargets(h, s, a, immediate = false)
        eyeWide = (eyeWide * .62f + wide * .38f * gain).coerceIn(0f, 1f)
        browDown = (browDown * .62f +
            max(browDownL, browDownR) * .38f * gain).coerceIn(0f, 1f)
        mouthFrown = (mouthFrown * .62f + frown * .38f * gain).coerceIn(0f, 1f)
        lowerLip = (lowerLip * .65f +
            max(delta("mouthLowerDownLeft", 1.2f),
                delta("mouthLowerDownRight", 1.2f)) * .35f * gain)
            .coerceIn(0f, 1f)
    }
}
