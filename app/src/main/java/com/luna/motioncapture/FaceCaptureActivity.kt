package com.luna.motioncapture

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.os.Bundle
import android.os.SystemClock
import android.view.Gravity
import android.widget.Button
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Actividad independiente: el modo corporal Alpha 0.1 sigue intacto. */
class FaceCaptureActivity : AppCompatActivity() {
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private lateinit var previewView: PreviewView
    private lateinit var avatarView: FaceAvatarView
    private lateinit var status: TextView
    private lateinit var cameraButton: Button
    private lateinit var guideButton: Button
    private lateinit var recordButton: Button
    private var engine: FaceEngine? = null
    private var running = false
    private var cameraVisible = false
    private var lastResultTime = 0L
    private val frames = mutableListOf<FaceFrame>()
    private var recording = false
    private var mouthTestOn = false
    private var mouthWidthStep = 1
    private var lastMouthReadout = 0L

    private val permissionRequest = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (it) startCapture() else update("Permite el acceso a la cámara")
    }
    private val imagePicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@registerForActivityResult
        try {
            contentResolver.openInputStream(uri)?.use { input ->
                val original = BitmapFactory.decodeStream(input) ?: error("Archivo no válido")
                val largest = maxOf(original.width, original.height)
                val bitmap = if (largest > 2000) {
                    val ratio = 2000f / largest
                    Bitmap.createScaledBitmap(original,
                        (original.width * ratio).toInt().coerceAtLeast(1),
                        (original.height * ratio).toInt().coerceAtLeast(1), true)
                } else original
                avatarView.setAvatar(bitmap)
                avatarView.setPresetPortrait()
                update("PNG cargado. Usa AJUSTAR PUNTOS para colocar ojos y boca")
            }
        } catch (e: Exception) { update("No se pudo abrir la imagen: ${e.localizedMessage}") }
    }
    private val saveJson = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            try {
                contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(toJson().toByteArray(Charsets.UTF_8))
                } ?: error("No se puede escribir el archivo")
                update("Animación facial JSON guardada")
            } catch (e: Exception) { update("Error al guardar: ${e.localizedMessage}") }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(13, 24, 36)
        window.navigationBarColor = Color.rgb(13, 24, 36)
        buildUi()
        assets.open("avatar_demo.png").use {
            avatarView.setAvatar(BitmapFactory.decodeStream(it))
        }
        update("Modo rostro · abre cámara y coloca tu cara de frente")
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density + .5f).toInt()
    private fun update(message: String) { status.text = message }

    private fun buildUi() {
        val root = FrameLayout(this).apply { setBackgroundColor(Color.rgb(13, 24, 36)) }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        previewView = PreviewView(this).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
            alpha = 0f
        }
        avatarView = FaceAvatarView(this)
        root.addView(previewView, FrameLayout.LayoutParams(-1, -1))
        root.addView(avatarView, FrameLayout.LayoutParams(-1, -1))

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(11))
            setBackgroundColor(Color.argb(237, 12, 22, 35))
        }
        header.addView(TextView(this).apply {
            text = "FACE CAPTURE · ALPHA 0.4"
            textSize = 18f
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
        })
        status = TextView(this).apply {
            setTextColor(Color.rgb(150, 231, 215))
            textSize = 12f
        }
        header.addView(status)
        root.addView(header, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))

        val footer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(5), dp(8), dp(5), dp(12))
            setBackgroundColor(Color.argb(244, 12, 22, 35))
        }
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val actionScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(actions)
        }
        fun button(label: String, click: () -> Unit): Button {
            val b = Button(this).apply {
                text = label
                textSize = 11f
                isAllCaps = false
                setOnClickListener { click() }
            }
            actions.addView(b, LinearLayout.LayoutParams(-2, dp(47)))
            return b
        }
        button("Iniciar cámara") { requestCamera() }
        button("Cargar PNG") { imagePicker.launch("image/*") }
        cameraButton = button("Mostrar cámara") {
            cameraVisible = !cameraVisible
            previewView.alpha = if (cameraVisible) 1f else 0f
            avatarView.showCameraBehind = cameraVisible
            cameraButton.text = if (cameraVisible) "Ocultar cámara" else "Mostrar cámara"
        }
        button("Calibrar rostro") {
            update(if (avatarView.calibrate()) "Rostro calibrado. Habla, parpadea y sonríe"
                else "Coloca el rostro frente a la cámara y vuelve a intentar")
        }
        guideButton = button("Ocultar puntos") {
            avatarView.showGuides = !avatarView.showGuides
            guideButton.text = if (avatarView.showGuides) "Ocultar puntos" else "Mostrar puntos"
        }
        button("Ajustar puntos: cara") {
            val selected = avatarView.nextAnchor()
            update("Toca o arrastra en el PNG para colocar: " + selected)
        }
        button("Probar boca") {
            mouthTestOn = !mouthTestOn
            avatarView.previewMouth(if (mouthTestOn) .92f else null)
            update(if (mouthTestOn)
                "PRUEBA MANUAL: la boca debe abrirse. Toca de nuevo para volver al seguimiento"
                else "Boca automática: abre la boca frente a la cámara con el rostro calibrado")
        }
        button("Boca: suave") {
            val interior = avatarView.toggleMouthInterior()
            update(if (interior)
                "Apertura suave: separación de labios y sombra discreta"
                else "Solo textura: sin cavidad oscura, deformación original del PNG")
        }
        button("Ancho labios") {
            mouthWidthStep = (mouthWidthStep + 1) % 3
            val value = when (mouthWidthStep) {
                0 -> .75f
                1 -> 1f
                else -> 1.25f
            }
            avatarView.lipWidth = value
            update("Ancho de la apertura: ${(value * 100).toInt()}%")
        }
        button("Centrar rig") {
            avatarView.setPresetPortrait()
            update("Rig restablecido. Ajusta cada ojo y la boca usando Ajustar puntos")
        }
        recordButton = button("Grabar JSON") { toggleRecord() }
        button("Volver a cuerpo") { finish() }

        val caption = TextView(this).apply {
            text = "Movimiento general · Ajustar puntos: cara → ojos → boca"
            textSize = 12f
            setTextColor(Color.WHITE)
            setPadding(dp(13), dp(5), 0, 0)
        }
        val slider = SeekBar(this).apply {
            max = 180
            progress = 90
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar?, value: Int, user: Boolean) {
                    avatarView.intensity = value / 100f
                }
                override fun onStartTrackingTouch(bar: SeekBar?) {}
                override fun onStopTrackingTouch(bar: SeekBar?) {}
            })
        }
        footer.addView(actionScroll)
        footer.addView(caption)
        footer.addView(slider)

        fun extraControl(title: String, onValue: (Float) -> Unit) {
            val line = TextView(this).apply {
                text = "$title (100%)"
                textSize = 12f
                setTextColor(Color.rgb(171, 232, 217))
                setPadding(dp(13), dp(2), 0, 0)
            }
            val control = SeekBar(this).apply {
                max = 200
                progress = 100
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(bar: SeekBar?, value: Int, user: Boolean) {
                        val bounded = value.coerceAtLeast(25)
                        line.text = "$title ($bounded%)"
                        onValue(bounded / 100f)
                    }
                    override fun onStartTrackingTouch(bar: SeekBar?) {}
                    override fun onStopTrackingTouch(bar: SeekBar?) {}
                })
            }
            footer.addView(line)
            footer.addView(control, LinearLayout.LayoutParams(-1, dp(32)))
        }
        extraControl("Sensibilidad de ojos") { avatarView.eyeSensitivity = it }
        extraControl("Sensibilidad de boca") { avatarView.mouthSensitivity = it }
        extraControl("Apertura máxima") { avatarView.lipGap = it.coerceIn(.20f, 1.5f) }
        root.addView(footer, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        setContentView(root)
    }

    private fun requestCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED) startCapture()
        else permissionRequest.launch(Manifest.permission.CAMERA)
    }

    private fun startCapture() {
        if (running) { update("Seguimiento facial activo"); return }
        running = true
        update("Cargando modelo facial local...")
        cameraExecutor.execute {
            try {
                engine = FaceEngine(applicationContext)
                runOnUiThread { bindCamera() }
            } catch (e: Exception) {
                running = false
                runOnUiThread { update("Error al iniciar Face Landmarker: ${e.localizedMessage}") }
            }
        }
    }

    private fun bindCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            try {
                val provider = future.get()
                provider.unbindAll()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                val analysis = ImageAnalysis.Builder()
                    .setTargetResolution(android.util.Size(480, 640))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(cameraExecutor) { proxy ->
                    try {
                        val now = SystemClock.elapsedRealtime()
                        if (now - lastResultTime < 65L) return@setAnalyzer
                        lastResultTime = now
                        val bitmap = proxy.toBitmap()
                        val matrix = Matrix().apply {
                            postRotate(proxy.imageInfo.rotationDegrees.toFloat())
                            postScale(-1f, 1f)
                        }
                        val upright = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width,
                            bitmap.height, matrix, true)
                        val timestamp = TimeUnit.NANOSECONDS.toMillis(proxy.imageInfo.timestamp)
                        val frame = engine?.detect(upright, timestamp)
                        if (upright !== bitmap) upright.recycle()
                        bitmap.recycle()
                        if (frame != null) runOnUiThread {
                            avatarView.updateFace(frame)
                            if (!mouthTestOn && now - lastMouthReadout > 450L) {
                                lastMouthReadout = now
                                val blend = (avatarView.detectedJaw * 100f).toInt()
                                val opening = (avatarView.mouthOpening * 100f).toInt()
                                val lips = (avatarView.detectedLipDelta * 100f).toInt()
                                update("Boca cámara: $blend% · labios Δ$lips% · personaje: $opening%")
                            }
                            if (recording) {
                                if (frames.size < 6000) frames.add(frame)
                                if (frames.size % 25 == 0)
                                    update("Grabando rostro: ${frames.size} cuadros")
                                if (frames.size >= 6000) {
                                    recording = false
                                    recordButton.text = "Grabar JSON"
                                    update("Límite de 6000 cuadros: guarda tu captura")
                                    saveJson.launch("rostro_${System.currentTimeMillis()}.json")
                                }
                            }
                        }
                    } catch (e: Exception) {
                        runOnUiThread { update("Error de cámara: ${e.localizedMessage}") }
                    } finally {
                        proxy.close()
                    }
                }
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, preview, analysis)
                update("Natural Mouth 0.4: ajusta el punto BOCA, calibra y prueba labios")
            } catch (e: Exception) {
                running = false
                update("No se pudo iniciar la cámara: ${e.localizedMessage}")
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun toggleRecord() {
        if (!running) {
            Toast.makeText(this, "Inicia cámara antes de grabar", Toast.LENGTH_SHORT).show()
            return
        }
        if (!recording) {
            frames.clear()
            recording = true
            recordButton.text = "Detener grabación"
            update("Grabando puntos y expresiones faciales, no video")
        } else {
            recording = false
            recordButton.text = "Grabar JSON"
            if (frames.isEmpty()) update("No se detectó un rostro")
            else saveJson.launch("rostro_${System.currentTimeMillis()}.json")
        }
    }

    private fun toJson(): String {
        val result = JSONArray()
        val start = frames.firstOrNull()?.timestampMs ?: 0L
        frames.forEach { f ->
            val landmarks = JSONArray()
            // Puntos relevantes en lugar de 478 puntos por cuadro para archivos compactos.
            for (index in listOf(4, 33, 263, 105, 334, 159, 386, 145, 374, 61, 291, 13, 14, 152)) {
                val p = f.point(index) ?: continue
                landmarks.put(JSONObject().put("id", index).put("x", p.x.toDouble()).put("y", p.y.toDouble()))
            }
            val expressions = JSONObject()
            for ((key, value) in f.expressions) expressions.put(key, value.toDouble())
            result.put(JSONObject().put("timeMs", f.timestampMs - start)
                .put("points", landmarks).put("blendshapes", expressions))
        }
        return JSONObject().put("format", "motioncapture-face2d-v1")
            .put("version", 1).put("mirrored", true)
            .put("unit", "normalized-image").put("frames", result).toString(2)
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.execute { engine?.close() }
        cameraExecutor.shutdown()
    }
}
