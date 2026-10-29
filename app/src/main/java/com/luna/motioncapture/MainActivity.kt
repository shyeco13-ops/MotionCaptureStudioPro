package com.luna.motioncapture

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Color
import android.os.Bundle
import android.os.SystemClock
import android.view.Gravity
import android.view.ViewGroup
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
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private lateinit var previewView: PreviewView
    private lateinit var avatarView: AvatarCanvasView
    private lateinit var statusText: TextView
    private lateinit var recordButton: Button
    private lateinit var cameraButton: Button
    private lateinit var rigButton: Button
    private var cameraVisible = false
    private var rigVisible = false
    private var poseEngine: PoseEngine? = null
    private val recorder = MotionRecorder()
    private var lastResultTime = 0L
    private var trackingStarted = false

    private val permissionRequest = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (it) startTracking() else updateStatus("Permite la cámara para capturar movimientos")
    }
    private val imagePicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            try {
                contentResolver.openInputStream(uri)?.use { stream ->
                    val source = BitmapFactory.decodeStream(stream) ?: error("Imagen no válida")
                    val maxDimension = maxOf(source.width, source.height)
                    val resized = if (maxDimension > 2000) {
                        val ratio = 2000f / maxDimension
                        Bitmap.createScaledBitmap(source, (source.width * ratio).toInt(), (source.height * ratio).toInt(), true)
                    } else source
                    avatarView.setAvatar(resized)
                    updateStatus("Imagen cargada. El rig básico es aproximado: calibra tu postura")
                }
            } catch (e: Exception) { updateStatus("Error al abrir la imagen: ${e.message}") }
        }
    }
    private val saveMotion = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            try {
                contentResolver.openOutputStream(uri)?.use { it.write(recorder.toJson().toByteArray(Charsets.UTF_8)) }
                    ?: error("No se pudo abrir el archivo")
                updateStatus("Captura de movimiento guardada en JSON")
            } catch (e: Exception) { updateStatus("No se pudo guardar: ${e.message}") }
        } else updateStatus("Guardado cancelado")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(16, 24, 32)
        window.navigationBarColor = Color.rgb(16, 24, 32)
        buildInterface()
        assets.open("avatar_demo.png").use { avatarView.setAvatar(BitmapFactory.decodeStream(it)) }
        updateStatus("Carga lista. Activa cámara, sitúate frente al teléfono y calibra")
    }

    private fun buildInterface() {
        val root = FrameLayout(this).apply { setBackgroundColor(Color.rgb(16, 24, 32)) }
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
        avatarView = AvatarCanvasView(this)
        root.addView(previewView, FrameLayout.LayoutParams(-1, -1))
        root.addView(avatarView, FrameLayout.LayoutParams(-1, -1))

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(15), dp(12), dp(15), dp(10))
            setBackgroundColor(Color.argb(215, 12, 23, 32))
        }
        val title = TextView(this).apply {
            text = "MOTION CAPTURE STUDIO PRO"
            setTextColor(Color.WHITE)
            textSize = 17f
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        statusText = TextView(this).apply {
            textSize = 12f
            setTextColor(Color.rgb(173, 225, 215))
            setPadding(0, dp(5), 0, 0)
        }
        header.addView(title)
        header.addView(statusText)
        root.addView(header, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))

        val footer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(5), dp(8), dp(5), dp(12))
            setBackgroundColor(Color.argb(240, 12, 23, 32))
        }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val scroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(row)
        }
        fun addAction(text: String, action: () -> Unit): Button {
            val button = Button(this).apply {
                this.text = text
                textSize = 11f
                isAllCaps = false
                setOnClickListener { action() }
            }
            row.addView(button, LinearLayout.LayoutParams(-2, dp(47)))
            return button
        }
        addAction("Modo rostro") { startActivity(Intent(this, FaceCaptureActivity::class.java)) }
        addAction("Iniciar cámara") { requestCamera() }
        addAction("Cargar PNG") { imagePicker.launch("image/*") }
        cameraButton = addAction("Mostrar cámara") { toggleCamera() }
        addAction("Calibrar postura") {
            updateStatus(if (avatarView.calibrate()) "Postura calibrada: mueve los brazos y piernas" else "Activa cámara y ponte frente al teléfono")
        }
        rigButton = addAction("Ver articulaciones") {
            rigVisible = !rigVisible
            avatarView.showRig = rigVisible
            rigButton.text = if (rigVisible) "Ocultar articulaciones" else "Ver articulaciones"
        }
        recordButton = addAction("Grabar movimiento") { toggleRecord() }
        val intensityLabel = TextView(this).apply {
            text = "Intensidad de movimiento (malla 2D)"
            textSize = 12f
            setTextColor(Color.WHITE)
            setPadding(dp(14), dp(5), 0, 0)
        }
        val slider = SeekBar(this).apply {
            max = 150
            progress = 90
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    avatarView.intensity = progress / 100f
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        }
        footer.addView(scroll)
        footer.addView(intensityLabel)
        footer.addView(slider)
        root.addView(footer, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        setContentView(root)
    }

    private fun requestCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startTracking()
        } else permissionRequest.launch(Manifest.permission.CAMERA)
    }

    private fun startTracking() {
        if (trackingStarted) {
            updateStatus("Seguimiento activo. Muévete frente a la cámara")
            return
        }
        trackingStarted = true
        updateStatus("Cargando modelo corporal local...")
        cameraExecutor.execute {
            try {
                poseEngine = PoseEngine(applicationContext)
                runOnUiThread { bindCamera() }
            } catch (e: Exception) {
                trackingStarted = false
                runOnUiThread { updateStatus("Error al cargar MediaPipe: ${e.localizedMessage}") }
            }
        }
    }

    private fun bindCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            try {
                val provider = future.get()
                provider.unbindAll()
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                val analysis = ImageAnalysis.Builder()
                    .setTargetResolution(android.util.Size(480, 640))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(cameraExecutor) { image ->
                    try {
                        val timeNow = SystemClock.elapsedRealtime()
                        if (timeNow - lastResultTime < 65L) return@setAnalyzer
                        lastResultTime = timeNow
                        val bitmap = image.toBitmap()
                        val rotationMatrix = Matrix().apply {
                            postRotate(image.imageInfo.rotationDegrees.toFloat())
                            postScale(-1f, 1f) // cámara frontal / vista espejo
                        }
                        val upright = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, rotationMatrix, true)
                        val timestamp = TimeUnit.NANOSECONDS.toMillis(image.imageInfo.timestamp)
                        val frame = poseEngine?.detect(upright, timestamp)
                        if (upright !== bitmap) upright.recycle()
                        bitmap.recycle()
                        if (frame != null) runOnUiThread {
                            avatarView.updatePose(frame)
                            recorder.push(frame)
                            if (recorder.recording && recorder.count() % 20 == 0) {
                                updateStatus("Grabando: ${recorder.count()} cuadros corporales")
                            }
                        }
                    } catch (e: Exception) {
                        runOnUiThread { updateStatus("Error de captura: ${e.localizedMessage}") }
                    } finally { image.close() }
                }
                val selector = CameraSelector.DEFAULT_FRONT_CAMERA
                provider.bindToLifecycle(this, selector, preview, analysis)
                updateStatus("Detectando movimientos. Colócate de cuerpo completo y calibra")
            } catch (e: Exception) {
                trackingStarted = false
                updateStatus("No se pudo iniciar la cámara: ${e.localizedMessage}")
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun toggleCamera() {
        cameraVisible = !cameraVisible
        previewView.alpha = if (cameraVisible) 1f else 0f
        avatarView.showCameraBehind = cameraVisible
        cameraButton.text = if (cameraVisible) "Ocultar cámara" else "Mostrar cámara"
    }

    private fun toggleRecord() {
        if (!trackingStarted) {
            Toast.makeText(this, "Activa la cámara primero", Toast.LENGTH_SHORT).show()
            return
        }
        if (!recorder.recording) {
            recorder.start()
            recordButton.text = "Detener grabación"
            updateStatus("Grabando datos corporales (formato JSON, no video)")
        } else {
            val hasData = recorder.stop()
            recordButton.text = "Grabar movimiento"
            if (hasData) {
                saveMotion.launch("movimiento_${System.currentTimeMillis()}.json")
            } else updateStatus("No se detectó ninguna persona durante la grabación")
        }
    }

    private fun updateStatus(message: String) { statusText.text = message }
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + .5f).toInt()

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.execute { poseEngine?.close() }
        cameraExecutor.shutdown()
    }
}
