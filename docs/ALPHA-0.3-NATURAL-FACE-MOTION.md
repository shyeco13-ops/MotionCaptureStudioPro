# Motion Capture Studio Pro · Alpha 0.3 — Natural Face Motion

## Meta principal
Reemplazar los óvalos artificiales de ojos y el círculo negro de boca de Alpha 0.2.1 por un tratamiento más natural para fotografías PNG de una sola pieza.

## Cambios implementados
- **Párpados:** cierre basado en deformación vertical de la **textura original de la fotografía** en una malla más detallada (56 × 80), sin pintar óvalos sólidos.
- **Labios:** contorno anatómico aproximado mediante curvas Bézier, sombra con color de la propia imagen y apertura proporcional al seguimiento facial.
- **Sensibilidad independiente:** controles nuevos para boca, ojos y movimiento general.
- **Expresiones suavizadas:** filtrado diferente para la mandíbula, el parpadeo y la sonrisa.
- **Menor deformación de rostro:** movimientos de mandíbula y párpados concentrados cerca de cada región.
- **Conservados:** modo corporal, carga local PNG, editor de puntos y calibración, opción de ocultar cámara y exportación facial JSON.

## Prueba guiada
1. Abre **Modo rostro** e importa un retrato PNG.
2. En **Ajustar puntos** alinea por separado ojo izquierdo, ojo derecho y boca; la guía identifica el punto seleccionado.
3. Presiona **Calibrar rostro** estando neutro, con los ojos abiertos y la boca cerrada.
4. Prueba cinco parpadeos y varias aperturas progresivas de boca.
5. Ajusta **Sensibilidad de ojos** y **Sensibilidad de boca** si el efecto es débil o exagerado.
6. Oculta la cámara para ver solo el personaje y comprueba que el modo corporal continúa funcionando.

## Alcances reales
Esta versión **NO** crea piel oculta, dientes, lengua ni nuevos píxeles anatómicos. El PNG carece de esas vistas, por lo que la boca sigue siendo una simulación visual. Para expresiones fotorealistas necesitaremos un sistema posterior de **capas faciales** o reconstrucción de zonas faltantes. La animación aún no es equivalente a un personaje 3D preparado.

## Privacidad
No contiene imágenes personales. La fotografía cargada permanece en el teléfono. El reconocimiento MediaPipe se ejecuta sin Internet después de instalar.

## Pruebas necesarias
- ¿El parpadeo se ve menos artificial?
- ¿El contorno de boca es más convincente que el círculo negro?
- ¿Funciona el ajuste individual de ojos y boca?
- ¿Se mueve fluido y sin cierres inesperados en un Android real?
