package com.luna.motioncapture

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.view.View
import kotlin.math.exp
import kotlin.math.min

/**
 * Prototipo de marioneta 2D con deformación de malla. Necesita calibración.
 * La plantilla de articulaciones está ajustada aproximadamente al avatar de ejemplo.
 * En futuras versiones se añadirá editor de huesos y separación por capas.
 */
class AvatarCanvasView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val guidePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(39, 228, 192)
        strokeWidth = 2.5f
    }
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var avatar: Bitmap? = null
    private var currentPose: PoseFrame? = null
    private var neutralPose: PoseFrame? = null

    var showCameraBehind: Boolean = false
        set(value) { field = value; invalidate() }
    var showRig: Boolean = false
        set(value) { field = value; invalidate() }
    var intensity: Float = 0.9f
        set(value) { field = value.coerceIn(0f, 2f); invalidate() }

    // x,y relativas al PNG; entradas MediaPipe nose, shoulder, elbow, wrist, hip, knee, ankle.
    private val rig = linkedMapOf(
        0 to (0.50f to 0.35f),
        11 to (0.34f to 0.53f), 12 to (0.69f to 0.53f),
        13 to (0.25f to 0.61f), 14 to (0.82f to 0.65f),
        15 to (0.34f to 0.66f), 16 to (0.83f to 0.75f),
        23 to (0.41f to 0.74f), 24 to (0.62f to 0.74f),
        25 to (0.35f to 0.84f), 26 to (0.69f to 0.84f),
        27 to (0.24f to 0.94f), 28 to (0.75f to 0.94f)
    )
    private val links = listOf(
        11 to 13, 13 to 15, 12 to 14, 14 to 16,
        11 to 12, 11 to 23, 12 to 24, 23 to 24,
        23 to 25, 25 to 27, 24 to 26, 26 to 28
    )

    fun setAvatar(bitmap: Bitmap) {
        avatar = bitmap
        invalidate()
    }

    fun updatePose(pose: PoseFrame) {
        currentPose = pose
        if (neutralPose == null) neutralPose = pose
        invalidate()
    }

    fun calibrate(): Boolean {
        neutralPose = currentPose ?: return false
        invalidate()
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!showCameraBehind) {
            backgroundPaint.shader = LinearGradient(
                0f, 0f, 0f, height.toFloat(),
                Color.rgb(17, 24, 39), Color.rgb(36, 54, 70), Shader.TileMode.CLAMP
            )
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)
            backgroundPaint.shader = null
        }
        val bitmap = avatar ?: return
        val scale = min(width.toFloat() * .96f / bitmap.width, height.toFloat() * .94f / bitmap.height)
        val dx = (width - bitmap.width * scale) / 2f
        val dy = (height - bitmap.height * scale) / 2f
        canvas.save()
        canvas.translate(dx, dy)
        canvas.scale(scale, scale)
        val vertices = makeMesh(bitmap)
        canvas.drawBitmapMesh(bitmap, MESH_COLUMNS, MESH_ROWS, vertices, 0, null, 0, paint)
        if (showRig) drawRig(canvas, bitmap, vertices)
        canvas.restore()
    }

    private fun makeMesh(bitmap: Bitmap): FloatArray {
        val cols = MESH_COLUMNS
        val rows = MESH_ROWS
        val vertices = FloatArray((cols + 1) * (rows + 1) * 2)
        val controls = mutableListOf<Control>()
        val baseline = neutralPose
        val live = currentPose
        if (baseline != null && live != null) {
            for ((index, anchor) in rig) {
                val prev = baseline.joint(index) ?: continue
                val curr = live.joint(index) ?: continue
                if (curr.visible < .35f || prev.visible < .35f) continue
                // El cambio real se escala y limita para evitar deformaciones extremas.
                val moveX = ((curr.x - prev.x) * intensity * .75f).coerceIn(-.25f, .25f)
                val moveY = ((curr.y - prev.y) * intensity * .75f).coerceIn(-.25f, .25f)
                val range = if (index == 0) .23f else if (index in listOf(15, 16, 27, 28)) .115f else .16f
                controls += Control(anchor.first, anchor.second, moveX, moveY, range)
            }
        }
        for (row in 0..rows) {
            for (col in 0..cols) {
                val x = col.toFloat() / cols
                val y = row.toFloat() / rows
                var influenceSum = .75f
                var moveX = 0f
                var moveY = 0f
                controls.forEach { c ->
                    val distance = ((x - c.x) * (x - c.x) + (y - c.y) * (y - c.y)) / (2f * c.radius * c.radius)
                    val weight = exp(-distance)
                    influenceSum += weight
                    moveX += c.moveX * weight
                    moveY += c.moveY * weight
                }
                val p = (row * (cols + 1) + col) * 2
                vertices[p] = (x + moveX / influenceSum) * bitmap.width
                vertices[p + 1] = (y + moveY / influenceSum) * bitmap.height
            }
        }
        return vertices
    }

    private fun drawRig(canvas: Canvas, bitmap: Bitmap, verts: FloatArray) {
        fun warped(x: Float, y: Float): Pair<Float, Float> {
            val c = (x * MESH_COLUMNS).toInt().coerceIn(0, MESH_COLUMNS)
            val r = (y * MESH_ROWS).toInt().coerceIn(0, MESH_ROWS)
            val p = (r * (MESH_COLUMNS + 1) + c) * 2
            return verts[p] to verts[p + 1]
        }
        links.forEach { (a, b) ->
            val from = rig[a] ?: return@forEach
            val to = rig[b] ?: return@forEach
            val p1 = warped(from.first, from.second)
            val p2 = warped(to.first, to.second)
            canvas.drawLine(p1.first, p1.second, p2.first, p2.second, guidePaint)
        }
        rig.values.forEach {
            val (x, y) = warped(it.first, it.second)
            canvas.drawCircle(x, y, bitmap.width * .012f, guidePaint)
        }
    }

    private data class Control(
        val x: Float, val y: Float, val moveX: Float, val moveY: Float, val radius: Float
    )

    companion object {
        private const val MESH_COLUMNS = 22
        private const val MESH_ROWS = 30
    }
}
