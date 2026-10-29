#!/bin/sh
# Bootstrap: recupera el wrapper oficial de Gradle si todavía no existe.
set -e
cd "$(dirname "$0")"
JAR="gradle/wrapper/gradle-wrapper.jar"
if [ ! -f "$JAR" ]; then
  echo "Descargando Gradle Wrapper oficial (una sola vez)..."
  curl -fL --retry 3 "https://raw.githubusercontent.com/gradle/gradle/v8.9.0/gradle/wrapper/gradle-wrapper.jar" -o "$JAR" || exit 1
fi
exec java -cp "$JAR" org.gradle.wrapper.GradleWrapperMain "$@"
