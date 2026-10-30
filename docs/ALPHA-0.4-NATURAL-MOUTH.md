# Motion Capture Studio Pro — Alpha 0.4 Natural Mouth Upgrade

## Problema observado en Alpha 0.3.1
El reconocimiento facial y la apertura funcionaban, pero el render dibujaba una forma enorme, oscura y artificial encima de los labios.

## Correcciones
- Deformación 2D de la textura **original** alrededor del punto de boca arrastrable. Los labios se separan suavemente, sin mover todo el rostro.
- El modo **Boca: suave** representa una apertura pequeña, proporcional y teñida usando colores muestreados de los labios originales; elimina la forma triangular anterior.
- El modo **Solo textura** elimina por completo cualquier cavidad añadida. Útil para personajes cuya foto tiene boca cerrada y no admite simular el interior.
- Control de **Apertura máxima** y botón de **Ancho labios** para ajustar la forma a diferentes proporciones.
- Conservados: seguimiento de ojos, rostro, grabación JSON, detección MediaPipe, modo corporal y test manual de boca.

## Prueba recomendada
1. Abrir Modo rostro y cargar PNG de frente, con rostro claro y buena resolución.
2. En Ajustar puntos, seleccionar BOCA y colocar el marcador justo entre ambos labios originales.
3. Calibrar con boca cerrada. Probar hablar y abrir progresivamente.
4. Comparar Boca: suave y Solo textura, ajustar Apertura máxima hasta lograr la menor deformación artificial.
5. Comprobar que los ojos siguen respondiendo y la imagen original no cambia de color.
6. Presionar Probar boca para verificar la deformación sin cámara y regresar a automático.

## Limitación
Una fotografía PNG frontal no incluye interior de boca, lengua ni dientes. Este sistema no recrea esas superficies, por lo que no es fotorrealista y puede verse extraño en algunas fotos. La siguiente fase, para una mejora sustancial, requiere extracción/edición de labios en capas y material de boca opcional.

## Privacidad
El modelo de cámara funciona localmente después de instalar. No se incluyen rostros de usuarios en el repositorio público.
