package com.luna.motioncapture

import android.content.Context
import android.graphics.Bitmap
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker

/** Los puntos se expresan en coordenadas normalizadas de cámara frontal reflejada. */
data class FacePoint(val x: Float, val y: Float)
data class FaceFrame(
    val timestampMs: Long,
    val landmarks: List<FacePoint>,
    val expressions: Map<String, Float>
) {
    fun point(index: Int): FacePoint? = landmarks.getOrNull(index)
    fun expression(name: String): Float = expressions[name] ?: 0f
}

/** Solo se accede desde el ejecutor de análisis de cámara. */
class FaceEngine(context: Context) : AutoCloseable {
    private val detector = FaceLandmarker.createFromOptions(
        context,
        FaceLandmarker.FaceLandmarkerOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetPath("face_landmarker.task").build())
            .setRunningMode(RunningMode.VIDEO)
            .setNumFaces(1)
            .setOutputFaceBlendshapes(true)
            .setMinFaceDetectionConfidence(0.5f)
            .setMinFacePresenceConfidence(0.5f)
            .setMinTrackingConfidence(0.5f)
            .build()
    )
    private var lastTimestamp = -1L

    fun detect(bitmap: Bitmap, timestampMs: Long): FaceFrame? {
        val timestamp = maxOf(timestampMs, lastTimestamp + 1)
        lastTimestamp = timestamp
        val result = detector.detectForVideo(BitmapImageBuilder(bitmap).build(), timestamp)
        val points = result.faceLandmarks().firstOrNull() ?: return null
        val scores = result.faceBlendshapes().orElse(emptyList()).firstOrNull()
            ?.associate { it.categoryName() to it.score() } ?: emptyMap()
        return FaceFrame(timestamp, points.map { FacePoint(it.x(), it.y()) }, scores)
    }

    override fun close() = detector.close()
}
