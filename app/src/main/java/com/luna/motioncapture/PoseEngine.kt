package com.luna.motioncapture

import android.content.Context
import android.graphics.Bitmap
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker

/** Coordinadas normalizadas de la imagen ya rotada y reflejada. */
data class Landmark2D(val x: Float, val y: Float, val visible: Float = 1f)
data class PoseFrame(val timestampMs: Long, val joints: List<Landmark2D>) {
    fun joint(index: Int): Landmark2D? = joints.getOrNull(index)
}

/** Se crea y usa exclusivamente en el hilo de análisis de cámara. */
class PoseEngine(context: Context) : AutoCloseable {
    private val detector = PoseLandmarker.createFromOptions(
        context,
        PoseLandmarker.PoseLandmarkerOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetPath("pose_landmarker_lite.task").build())
            .setRunningMode(RunningMode.VIDEO)
            .setNumPoses(1)
            .setMinPoseDetectionConfidence(0.5f)
            .setMinPosePresenceConfidence(0.5f)
            .setMinTrackingConfidence(0.5f)
            .build()
    )
    private var lastTimestampMs = -1L

    fun detect(bitmap: Bitmap, timestampMs: Long): PoseFrame? {
        val timestamp = maxOf(timestampMs, lastTimestampMs + 1)
        lastTimestampMs = timestamp
        val result = detector.detectForVideo(BitmapImageBuilder(bitmap).build(), timestamp)
        val detected = result.landmarks().firstOrNull() ?: return null
        val points = detected.map { Landmark2D(it.x(), it.y(), it.visibility().orElse(1f)) }
        return PoseFrame(timestamp, points)
    }

    override fun close() = detector.close()
}
