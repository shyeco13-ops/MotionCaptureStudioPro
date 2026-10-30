# Motion Capture Studio Pro — Alpha 0.5 · Advanced Facial Rig (2.5D)

**Estado:** diseño aprobado como dirección de desarrollo; implementación y APK aún pendientes.  
**Base que debe conservarse:** Alpha 0.4, cámara y detección Face Landmarker / Pose, PNG de usuario, ajuste de puntos, cuerpo completo, JSON, calibración y grabación de captura.

## 1. Problema validado
- La detección facial en vivo funciona, pero el movimiento de labios no genera una apertura realista.
- Una imagen PNG plana, incluso transparente, no contiene el interior de la boca, piel tapada por párpados ni información lateral suficiente al girar la cabeza.
- La deformación global de la imagen produce un aspecto rígido o gomoso.
- Los efectos de apertura oscura añadidos artificialmente se ven como elementos pegados.

## 2. Objetivo
Convertir movimientos faciales del usuario en animación *local* del rostro del personaje, con anclas y mallas regionales. Conservar la identidad visual. No afirmar reconstrucción fotográfica completa desde un único PNG.

## 3. Arquitectura propuesta
1. **FaceCapture**: usar MediaPipe Face Landmarker para 478 landmarks + 52 blendshapes + matriz facial, con cámara frontal, calibración neutra e indicadores de confianza.
2. **RigEditor2D**: ajustar por separado contorno, ojos, cejas, nariz, mejillas, comisuras, labio superior/inferior, barbilla. Autoajuste inicial más anclas arrastrables y guardado del rig en un archivo del proyecto.
3. **Retargeter**: traducir intensidades faciales (coeficientes calibrados, reflejo izquierdo/derecho, filtros y límites por región) a controladores del rig del personaje.
4. **MeshDeformer**: malla de triángulos y deformación localizada; dividir mallas faciales para evitar estirar toda la cara.
5. **MouthLayers**: capturar o importar *boca cerrada / ligeramente abierta / abierta*, o crear manualmente una capa interior editable. No pintar círculos, elipses o triángulos opacos como solución única.
6. **EyeLayers**: recorte del ojo original y dos capas de párpado con transformación suave; el modo básico conserva solo deformación por textura.
7. **NaturalSmoothing**: filtrado con velocidad adaptativa; respuesta rápida de parpadeos y consonantes, suavizado más gradual de mejillas y cabeza.
8. **Renderer**: dibujo que mantiene el resto de la foto inalterada; transparencia alfa, máscaras suaves, previsualización 2D con cámara oculta.
9. **CaptureRecorder**: JSON de datos originales y curvas de rig, con posibilidad posterior de exportación a formato animado.

## 4. Controles faciales prioritarios

| Zona | Señales | Movimiento |
| --- | --- | --- |
| Párpados y ojos | eyeBlink*, eyeWide*, eyeLook* | parpadeo, apertura, mirada |
| Cejas y frente | browInnerUp, browOuterUp*, browDown* | expresión de sorpresa, preocupación |
| Boca y mandíbula | jawOpen, mouthClose, mouthFunnel, mouthPucker | apertura, cierre, vocales aproximadas |
| Comisuras y labios | mouthSmile*, mouthFrown*, mouthStretch*, mouthRoll*, mouthPress* | sonrisa, gesto triste, fruncir |
| Mejillas | cheekSquint*, cheekPuff | sonrisa y expresión suave |
| Nariz | noseSneer* | gestos sutiles |
| Cabeza | matriz de transformación, landmarks | giro e inclinación dentro de límites 2.5D |

Los nombres exactos se verifican con el modelo MediaPipe instalado. Las partes ocluidas requieren capas o imágenes adicionales.

## 5. Primera implementación: Alpha 0.5.0
- **No cambiar inicialmente Alpha 0.4 publicada**: trabajar en rama aislada.
- Añadir modos *Boca solo textura / Boca con capas*, sin utilizar el antiguo óvalo negro.
- Agregar editor con 2 anclas para labios, 2 comisuras, 2 ojos, 2 cejas, nariz, mandíbula.
- Guardar y recuperar ajustes para cada imagen.
- Añadir panel técnico de blendshapes (detección vs resultado).
- Integrar primeras mallas locales de labios y comisuras; los demás controladores en iteraciones sucesivas.
- Incluir botón A/B para comparar Alpha 0.4 vs rig nuevo sobre el mismo PNG.
- Grabar pruebas de cámara al menos 30 s sin cierres.

## 6. Objetivos posteriores
- Alpha 0.5.1: párpados por capas y seguimiento independiente de mirada.
- Alpha 0.5.2: mejillas, nariz, cejas, frente y transiciones más suaves.
- Alpha 0.6: preparación automática de capas de labios y reconstrucción asistida, con corrección manual; evaluar si puede operar sin conexión en teléfonos Android.

## 7. Criterios de calidad
- Boca abierta debe responder al usuario y verse integrada, sin un círculo oscuro.
- Ojos abiertos/cerrados reconocibles y sin cubrir erróneamente los ojos del PNG.
- Mallas que no deforman el cabello, las orejas ni el fondo fuera del rostro.
- Sin cambio de colores en la imagen original.
- Modo corporal, captura y exportación siguen funcionando.
- Un mal ajuste en el PNG no debe cerrar la aplicación.
- La compilación exitosa NO equivale a validación visual en Android: se requieren pruebas reales.

## 8. Limitaciones y expectativas
El sistema puede proporcionar movimientos naturales *para un personaje 2D preparado*; no puede reproducir fielmente todas las partes de cualquier cara y todos los ángulos partiendo de una sola fotografía sin estimar píxeles faltantes. Las salidas de redes neuronales para animar retratos se pueden explorar como otra ruta experimental, pero su rendimiento en vivo en Android y licencias deben verificarse.

## 9. Privacidad
No incorporar ni publicar fotografías personales en el repositorio público. Importar imágenes en el teléfono, procesar localmente cuando sea posible.
