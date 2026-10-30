import java.net.URL

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.luna.motioncapture"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.luna.motioncapture"
        minSdk = 26
        targetSdk = 35
        versionCode = 4
        versionName = "0.3.0-alpha"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildTypes { release { isMinifyEnabled = false } }
    androidResources { noCompress += "task" }
}

// Se descarga durante la primera compilación; después el reconocimiento es local/offline.
val modelDestination = layout.projectDirectory.file("src/main/assets/pose_landmarker_lite.task")
val getPoseModel = tasks.register("getPoseModel") {
    val modelUrl = "https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_lite/float16/1/pose_landmarker_lite.task"
    inputs.property("modelUrl", modelUrl)
    outputs.file(modelDestination)
    doLast {
        val target = modelDestination.asFile
        if (!target.exists() || target.length() < 100_000) {
            target.parentFile.mkdirs()
            val temp = target.resolveSibling(target.name + ".tmp")
            try {
                URL(modelUrl).openConnection().apply {
                    connectTimeout = 20000
                    readTimeout = 60000
                }.getInputStream().use { input -> temp.outputStream().use { output -> input.copyTo(output) } }
                require(temp.length() > 100_000) { "La descarga del modelo está incompleta" }
                require(temp.renameTo(target)) { "No se pudo instalar el modelo" }
            } finally { temp.delete() }
        }
    }
}
val faceDestination = layout.projectDirectory.file("src/main/assets/face_landmarker.task")
val getFaceModel = tasks.register("getFaceModel") {
    val modelUrl = "https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker/float16/1/face_landmarker.task"
    inputs.property("modelUrl", modelUrl)
    outputs.file(faceDestination)
    doLast {
        val target = faceDestination.asFile
        if (!target.exists() || target.length() < 100_000) {
            target.parentFile.mkdirs()
            val temp = target.resolveSibling(target.name + ".tmp")
            try {
                URL(modelUrl).openConnection().apply {
                    connectTimeout = 20000
                    readTimeout = 60000
                }.getInputStream().use { source ->
                    temp.outputStream().use { sink -> source.copyTo(sink) }
                }
                require(temp.length() > 100_000) { "Descarga facial incompleta" }
                require(temp.renameTo(target)) { "No se pudo instalar el modelo facial" }
            } finally { temp.delete() }
        }
    }
}
tasks.named("preBuild") { dependsOn(getPoseModel, getFaceModel) }

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.camera:camera-core:1.5.2")
    implementation("androidx.camera:camera-camera2:1.5.2")
    implementation("androidx.camera:camera-lifecycle:1.5.2")
    implementation("androidx.camera:camera-view:1.5.2")
    implementation("com.google.mediapipe:tasks-vision:0.10.35")
}
