# Especificación técnica oficial — Alpha 0.2 Face Capture
Fecha: 28 de octubre de 2026
Proyecto: Motion Capture Studio Pro (Android, Kotlin)

## Objetivo
Animar una imagen 2D importada desde el teléfono mediante movimientos faciales detectados en vivo por la cámara frontal, sin requerir el cuerpo completo. Preservar el modo corporal Alpha 0.1.

## Módulos
- FaceEngine: MediaPipe Face Landmarker en modo VIDEO sobre un ejecutor de cámara, un rostro, 478 puntos y 52 coeficientes faciales.
- FaceCaptureActivity: pantalla facial dedicada, cámara CameraX, cargar imagen PNG/JPG, calibración, mostrar/ocultar cámara, ajuste de sensibilidad, guía movible y grabación de datos.
- FaceAvatarView: representación de malla 2D deformable sobre PNG; ajustes aproximados de mandíbula, sonrisa, cejas, parpadeo e inclinación de cabeza. La textura no se altera en disco.
- Exportación local: selector de documentos de Android, JSON con tiempos relativos, puntos de cara seleccionados y blendshapes por cuadro.

## Flujo de uso
1. Abrir el modo corporal existente; presionar "Modo rostro".
2. Cargar una foto PNG (idealmente frontal y con fondo transparente).
3. Mostrar puntos; arrastrar la guía al centro del rostro en la imagen.
4. Activar cámara y sentarse frente al teléfono.
5. Calibrar una expresión neutra.
6. Probar apertura de boca, sonrisa, cejas, parpadeo y giro/inclinación de cabeza.
7. Ocultar cámara y grabar; detener y guardar JSON.

## Formato de movimiento
motioncapture-face2d-v1: version, mirrored, unit, frames[];
cada cuadro contiene timeMs (desde el inicio), points[] con id/x/y de 14 puntos, y blendshapes con los puntajes disponibles.
Límite inicial: 6000 cuadros en memoria. No se graba video en esta versión.

## Arquitectura y privacidad
- Android 8+ (minSdk 26), Kotlin, CameraX y MediaPipe Tasks Vision.
- Modelos Pose y Face incorporados al APK durante la compilación: uso sin Internet una vez instalado.
- Las imágenes y coordenadas faciales se procesan localmente; sin envío deliberado a una API.
- No se guardan automáticamente fotografías ni videos de la cámara.
- El repositorio público no incluye fotografías de personas ni el Garuda personalizado.

## Límites / riesgos conocidos
- Es una deformación *aproximada* de una sola fotografía: no reconstruye partes ocultas ni abre una boca realista con dientes/interior de boca.
- El rig predeterminado requiere ajuste para diferentes fotografías y proporciones.
- El parpadeo, labios y expresiones son aproximaciones visuales, no animación profesional por capas.
- Deben probarse estabilidad, latencia y orientación de expresiones (la entrada está reflejada).
- La Alpha 0.2 todavía no tiene seguimiento de manos, modelos 3D, MP4, Live Streaming o exportación de personajes animados.

## Plan de prueba en Android
- Abre el modo corporal para confirmar que sigue funcionando.
- En Modo rostro, comprueba permisos, detección de un rostro y respuesta al parpadear/hablar.
- Importa un PNG frontal; alinea la guía arrastrando; calibra expresión neutra.
- Prueba ocultar/mostrar cámara y guarda un JSON de captura breve.
- Prueba sin Internet tras instalar el APK.
- Reporta cierre inesperado, deformaciones y respuesta de boca/ojos.

## Criterio para pasar a Alpha 0.3
Seguimiento estable sin cierre, imagen respetada, respuesta visible y controlable de cabeza y boca, captura JSON y modo corporal conservado.
