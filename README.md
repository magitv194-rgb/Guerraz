# Traductor de voz para Android

Aplicación Android nativa para reconocer voz y traducir segmentos al idioma elegido mediante MyMemory.

## Requisitos
- Android 6.0 o superior.
- Conexión a Internet.
- Servicio de reconocimiento de voz instalado y habilitado (por ejemplo, el de Google).
- Permiso de micrófono.

## Compilar
Con JDK 17 y Gradle 8.9:

`gradle assembleDebug`

APK: `app/build/outputs/apk/debug/app-debug.apk`

## Descargar desde GitHub
Cada push a `main` ejecuta GitHub Actions y publica el APK como artefacto del workflow. Abre Actions, selecciona **Compilar APK Android** y descarga **Traductor-de-voz-APK**.

La opción automática toma el idioma del teléfono como idioma de reconocimiento; no realiza detección universal del idioma hablado. MyMemory y el reconocimiento de voz requieren Internet.
