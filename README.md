# Guerraz — control remoto Android

Aplicación Android nativa en Kotlin con dos variantes instalables: **Host** (teléfono que comparte pantalla) y **Client** (teléfono que recibe vídeo y envía gestos).

## Estado actual de la implementación

- [x] Variantes Host y Client.
- [x] Consentimiento del sistema para captura de pantalla mediante MediaProjection.
- [x] Servicio foreground de captura con notificación persistente.
- [x] Servidor TCP local en el Host, puerto `47832`.
- [x] Emparejamiento por PIN aleatorio de seis dígitos para cada sesión.
- [x] Codificación H.264 mediante MediaCodec y transmisión por Wi-Fi local.
- [x] Recepción, decodificación y visualización en Client.
- [x] Vista inmersiva con control visible para salir.
- [x] Envío de toques y deslizamientos desde Client.
- [x] Ejecución de gestos en Host mediante un AccessibilityService optativo.

## Cómo conectar los dos teléfonos

1. Instala la variante **Host** en el teléfono que vas a controlar y la variante **Client** en el teléfono desde el que vas a mirar.
2. Conecta ambos a la misma red Wi-Fi. Evita redes públicas o redes con aislamiento entre clientes.
3. En Host, pulsa **Compartir pantalla y esperar conexión** y acepta el diálogo de Android.
4. Host muestra su IP local y un PIN temporal. Introduce ambos en Client y pulsa **Conectar y ver pantalla**.
5. Para enviar toques y deslizamientos, habilita manualmente **Guerraz · Gestos remotos** en Ajustes → Accesibilidad del teléfono Host.

## Requisitos y límites de Android

- El usuario del Host debe aceptar explícitamente MediaProjection para cada sesión.
- El Host y el Client deben poder comunicarse directamente por TCP en el puerto `47832`; algunas redes Wi-Fi bloquean el tráfico entre dispositivos.
- El PIN evita conexiones casuales no autorizadas, pero no sustituye el cifrado TLS. Usa únicamente una red de confianza; el vídeo y el canal de gestos aún no están cifrados.
- El control de gestos utiliza AccessibilityService, que requiere activación explícita desde Ajustes. Android puede restringir gestos según el dispositivo, la pantalla protegida o la política del sistema.
- Esta versión no usa Shizuku. No ofrece control universal, teclado remoto ni acceso a pantallas protegidas/DRM.
- La compatibilidad y estabilidad de codificadores H.264 depende del hardware del teléfono.

## Compilar

Requisitos: Android Studio reciente, JDK 17 y Android SDK 35.

```bash
gradle assembleHostDebug assembleClientDebug
```

APK resultantes:
- `app/build/outputs/apk/host/debug/app-host-debug.apk`
- `app/build/outputs/apk/client/debug/app-client-debug.apk`

Las APK de GitHub Actions son compilaciones de desarrollo. Antes de considerarlas listas para uso general se necesitan pruebas reales en dos teléfonos y revisión de seguridad del transporte.
