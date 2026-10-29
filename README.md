# Motion Capture Studio Pro · Alpha 0.1 (Android)

**Primer prototipo técnico.** No es todavía la Alpha 1.0 final, ni una APK verificada.

## Qué hay implementado
- Cámara frontal con CameraX, permisos en tiempo de ejecución y vista espejo.
- Detección corporal local con **MediaPipe Pose Landmarker** (33 articulaciones).
- Avatar PNG de ejemplo (avatar de ejemplo) incluido y botón **Cargar PNG**.
- Animación inicial por deformación de malla 2D controlada por los cambios de postura de la cámara.
- Botón **Calibrar postura**, intensidad ajustable, guías de articulaciones.
- Modo **Mostrar/Ocultar cámara**: el seguimiento permanece activo aunque solo se muestre el personaje.
- **Grabar movimiento** guarda un archivo `.json` con las articulaciones detectadas y tiempos; NO es MP4.
- No solicita Internet ni envía fotos o datos corporales a servidores durante el uso normal.

## Qué falta
- Rig automático preciso para cualquier fotografía (actualmente plantilla de huesos aproximada al avatar de ejemplo).
- Capas independientes, deformación avanzada, movimiento de mandíbula y seguimiento facial.
- Modelos 3D, exportación BVH/GLB/FBX, video MP4 y segunda pantalla.
- Evaluación de latencia y compatibilidad en hardware real.

## Abrir en Android Studio (PC Windows)
1. Descomprime **MotionCaptureStudioPro-Alpha-0.1.zip**.
2. Abre una terminal de Windows en la carpeta extraída y ejecuta `gradlew.bat --version`. Esta preparación descarga el `gradle-wrapper.jar` oficial si hace falta.
3. Abre Android Studio → **Open** → selecciona la carpeta `MotionCaptureStudioPro`.
4. Permite la **sincronización de Gradle**. La primera compilación descargará bibliotecas y el modelo público de Google (`pose_landmarker_lite.task`). Hace falta Internet en este paso.
5. Conecta el móvil Android con depuración USB, pulsa **Run**, o usa **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
6. Abre la aplicación, pulsa **Iniciar cámara** y permite el uso de la cámara.
7. Coloca el teléfono fijo, con suficiente luz y con el cuerpo completo visible. Pon los brazos hacia abajo y toca **Calibrar postura**. Después mueve brazos y piernas suavemente.
8. Si lo deseas, pulsa **Ocultar cámara**. El personaje sigue visible y debe deformarse con tus movimientos.
9. **Grabar movimiento** y luego **Detener grabación** guarda datos de articulaciones en JSON, no captura video.

### Requisitos recomendados
- Android Studio reciente; JDK 17; Android SDK 35; teléfono Android 8.0+ (API 26), preferiblemente hardware reciente.
- Conexión a Internet durante el primer Gradle Sync/build, porque los archivos de dependencias y el modelo no están incluidos en el ZIP.

## Estructura
- `app/src/main/java/com/luna/motioncapture/MainActivity.kt` — cámara e interfaz
- `PoseEngine.kt` — inferencia corporal MediaPipe
- `AvatarCanvasView.kt` — avatar PNG + malla de deformación
- `MotionRecorder.kt` — grabación de posiciones JSON
- `app/src/main/assets/avatar_demo.png` — imagen de prueba con fondo transparente

## Avisos técnicos
- El ZIP contiene código fuente Android Studio; **no contiene una APK**, y no se ha compilado ni probado en un dispositivo en este entorno (no hay SDK de Android ni Gradle instalados).
- El rig inicial de cualquier PNG que no sea el ejemplo puede estar desalineado. La importación de una foto no equivale todavía a un recorte o rig inteligente.
- La primera calibración se realiza automáticamente al detectar una persona; se recomienda pulsar *Calibrar postura* estando quieto en posición neutral.
- CameraX 1.5.2 y MediaPipe 0.10.35; Gradle 8.9 / AGP 8.7.3 / Kotlin 2.0.21. Los números se fijaron para reproducibilidad; pueden necesitar ajustes en tu instalación.
- Este prototipo solo trabaja con una persona y la cámara frontal del móvil.
- La biblioteca MediaPipe puede enviar métricas de uso a Google según su aviso de privacidad; el procesamiento de imágenes y video se realiza en el dispositivo. Consultar https://developers.google.com/edge/mediapipe/solutions/vision/pose_landmarker/android

## Próxima iteración
1. Asegurar compilación y detectar problemas reales de cámara en el teléfono.
2. Herramienta manual de articulaciones sobre la imagen (arrastrar hombros, codos, muñecas, rodillas).
3. Editar deformación de malla y mejorar continuidad en hombros y brazos.
4. Registro/reproducción de capturas JSON y exportación de video.
