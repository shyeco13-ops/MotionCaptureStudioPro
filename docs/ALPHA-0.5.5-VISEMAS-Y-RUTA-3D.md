# Motion Capture Studio Pro — Alpha 0.5.5 Viseme Mouth System

## Estado
Implementación experimental en la rama `feature/alpha-0.5.5-viseme-system`. El proyecto Alpha 0.5.4 permanece en su rama; no se modifica `main`. Compilación Android pendiente de revisión y prueba visual hasta que GitHub Actions termine.

## Qué cambia
- La foto cerrada del rostro es el estado base y **nunca se sobrescribe**.
- Se pueden cargar por separado imágenes de boca **semiabierta, abierta, O, sonrisa, estirada, labio inferior, dientes**.
- Las formas se interpolan con coeficientes faciales de MediaPipe como `jawOpen`, `mouthSmileLeft/Right`, `mouthPucker`, `mouthStretchLeft/Right`, `mouthLowerDown*`, `mouthUpperUp*` (visemas visuales aproximados, **no reconocimiento fonético**).
- Motor de malla con comisuras y labios superior e inferior desplazados localmente, en lugar de un bloque entero.
- Hasta dos formas activas combinadas en cada cuadro, evitando cambios abruptos.
- Modo **Probar boca** sin cámara para verificar apertura/cierre gradual.
- Intensidad ajustable de la mezcla de visemas.
- Máscara suave y zona recortada; ninguna capa cambia el PNG original.
- Los archivos y ajustes se guardan localmente por imagen.
- Si solo se cargó la imagen anterior **Boca abierta PNG**, el motor puede usarla sin exigir cargar todo el atlas.

## Uso en Android
1. Abrir **Modo rostro**, cargar el retrato original y guardar los puntos de ojos, labios y comisuras.
2. Abrir **Controles**. El botón **Visema: [nombre]** recorre las siete formas. El texto del botón muestra la selección actual.
3. Seleccionar una forma y pulsar **Cargar visema** para elegir una imagen PNG/JPG del mismo personaje. Repetir para las formas disponibles.
4. Pulsar **Ver visemas** para revisar cuáles existen, o **Borrar visema** para retirar únicamente la seleccionada.
5. Ajustar **Tipo de boca** (automático por defecto; cambiar a *recorte* o *retrato completo* si la identificación falla).
6. Pulsar **Probar boca** para comparar apertura gradual y después **Calibrar rostro** frente a cámara con la boca cerrada.
7. Reducir **Mezcla de visemas** si los bordes se notan, y ajustar manualmente comisuras/labio inferior si el gesto se descoloca.

Las imágenes de visema deben mostrar **la misma identidad** y orientación. En recortes, incluir labio superior, inferior, comisuras y poca piel alrededor. Para foto completa, conservar escala y alineación iguales al retrato base. Si falta una forma, el sistema usa las formas disponibles y no inventa dientes ni lengua.

## Limitaciones
- No es un modelo neuronal de lip-sync, no transcribe audio ni reconoce vocales por sonido. Las formas se seleccionan por señales visuales del rostro capturado.
- Un PNG plano no contiene información tridimensional de músculos, dientes o cavidad oral invisible. La máscara y deformación 2D puede seguir mostrando artefactos en giros pronunciados.
- No se garantiza semejanza fotorrealista en todos los retratos, ni que una forma dada por el usuario quede automáticamente alineada.
- No se incluye ninguna foto personal del usuario en el repositorio público.

## Criterios para aprobar la fase 2D
- Cargar 1, 3 y 7 formas opcionales sin cierres inesperados.
- Mover comisuras a izquierda y derecha sin mostrar cuadros ni cortes rectangulares.
- Apertura/cierre gradual perceptible, con el labio inferior visible.
- Conservar parpadeo, cejas, nariz, mejillas, calibración y modo corporal.
- Reabrir el mismo retrato y recuperar sus capas y puntos, sin subirlos a Internet.
- Verificar estabilidad durante captura facial y grabación real en dispositivo Android.

## Siguiente etapa: módulo 3D (propuesta)
Después de las pruebas y aprobación, diseñar un módulo 3D independiente:
- Malla de cabeza, mandíbula y ojos con topología apta para deformación.
- Rig de 52 blendshapes o un mapa propio compatible con MediaPipe, conservando asimetrías.
- Materiales PBR, cámara, iluminación y texturas de usuario; vista previa Android.
- Importación de GLB/GLTF con armadura y expresiones cuando estén presentes.
- Retargeting en vivo, grabación de animación y exportación compatible.
- Calidad adaptativa para el teléfono y gestión local de assets.
No prometer convertir cualquier foto 2D en una cabeza 3D realista sin reconstrucción adicional ni considerar limitaciones de rendimiento.
