# Instalar Motion Capture Studio Pro usando solo un Android

Esta carpeta es **código fuente**. No es instalable como APK sin compilar.
Incluye `.github/workflows/compilar-apk.yml` para que GitHub Actions compile una APK **debug** en la nube.
**Aún no existe un APK verificado**: si GitHub marca error, necesitamos revisar los registros y corregir el proyecto.

## Camino con GitHub desde el teléfono

1. Abre github.com desde Chrome y entra a tu cuenta. Desde el menú de Chrome activa **Sitio de escritorio** si no ves alguna opción.
2. Crea un repositorio nuevo, idealmente privado, llamado `MotionCaptureStudioPro`.
3. Desde el gestor de archivos de Android descomprime el ZIP descargado.
4. Sube al repositorio **el contenido de la carpeta `MotionCaptureStudioPro`**, manteniendo las subcarpetas (`app`, `gradle`, `.github`). NO subas únicamente el ZIP: GitHub Actions necesita los archivos descomprimidos. En móvil, subir una carpeta completa desde el navegador puede resultar incómodo; también puedes usar un cliente Git para Android que permita importar repositorios y hacer push, o solicitar ayuda para automatizar la subida a través de GitHub conectado a ChatGPT.
5. Al subir los archivos, la acción de compilación se ejecutará por un `push`. También puedes ir a **Actions > Compilar APK Android > Run workflow** para iniciarla manualmente.
6. Cuando la ejecución tenga marca verde, entra en ella y busca **Artifacts > MotionCaptureStudioPro-Alpha-0.1-APK**. Descarga el ZIP del artifact.
7. Extrae `app-debug.apk` de ese ZIP, ábrelo desde Archivos y autoriza temporalmente **Instalar apps desconocidas** para el navegador o gestor de archivos correspondiente. Después puedes desactivar ese permiso.
8. Abre Motion Capture Studio Pro, acepta el permiso de cámara, usa `Iniciar cámara`, `Calibrar postura` y comprueba si avatar de ejemplo responde.

## Notas
- Instalar el ZIP fuente no funcionará. El resultado de GitHub Actions, cuando compile, es una APK.
- No necesitas Android Studio en el teléfono. Sí necesitas Internet para la subida y la compilación.
- En GitHub, una ejecución fallida aparece roja. No podrás instalar nada hasta que se genere un APK.
- La APK debug está firmada automáticamente para pruebas, no para publicar en Google Play. Para actualizaciones consistentes y distribución comercial hará falta una estrategia de firma estable.
- El APK no se ha compilado ni probado por el asistente en este entorno. La primera versión es experimental.
