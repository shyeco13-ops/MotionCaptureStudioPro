package com.luna.motioncapture

import org.json.JSONArray
import org.json.JSONObject

/** Guarda posiciones 2D para análisis y reproducción futura; todavía NO graba MP4. */
class MotionRecorder {
    private val frames = mutableListOf<PoseFrame>()
    var recording: Boolean = false
        private set

    fun start() {
        frames.clear()
        recording = true
    }
    fun push(frame: PoseFrame) {
        if (recording) frames.add(frame)
    }
    fun stop(): Boolean {
        recording = false
        return frames.isNotEmpty()
    }
    fun count() = frames.size

    fun toJson(): String {
        val origin = frames.firstOrNull()?.timestampMs ?: 0L
        val jsonFrames = JSONArray()
        frames.forEach { frame ->
            val points = JSONArray()
            frame.joints.forEach { joint ->
                points.put(JSONArray().put(joint.x.toDouble()).put(joint.y.toDouble()).put(joint.visible.toDouble()))
            }
            jsonFrames.put(JSONObject().put("timeMs", frame.timestampMs - origin).put("landmarks", points))
        }
        return JSONObject()
            .put("format", "motioncapture-pose2d-v1")
            .put("version", 1)
            .put("units", "normalized-image")
            .put("mirrored", true)
            .put("frames", jsonFrames)
            .toString(2)
    }
}
