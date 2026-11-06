# Guerraz — control remoto Android

Proyecto Android nativo en Kotlin. El repositorio se organiza en dos variantes instalables, Host (teléfono que comparte su pantalla) y Client (teléfono operador). Se compilan desde el mismo código para evitar divergencias entre protocolos.

## Variantes APK

- assembleHostDebug: APK del teléfono Host.
- assembleClientDebug: APK del teléfono Client.
- Los artefactos se publican desde GitHub Actions.

## Estado de implementación

### Base inicial
- Proyecto Kotlin con variantes Host y Client.
- Pantalla inicial diferenciada por variante.
- Host: solicitud explícita de consentimiento mediante MediaProjectionManager.
- Host: servicio en primer plano declarado como mediaProjection, con notificación persistente y liberación de recursos al detenerse.

### Siguientes etapas
1. Emparejamiento local Host–Client con código temporal y autenticación mutua.
2. Transporte de vídeo de baja latencia por Wi-Fi local (sin servidor externo).
3. Decodificación y visualización inmersiva en Client, con control flotante para salir.
4. Canal de comandos autenticado y control de entrada mediante Shizuku, sujeto a las capacidades y permisos reales de cada versión de Android.

## Límites de seguridad de Android

- Android exige consentimiento del usuario Host para cada sesión de captura; Guerraz no intenta ocultar ni eludir ese diálogo.
- Shizuku no concede control universal por sí solo. La ejecución de comandos depende de la versión de Android, del modo de inicio de Shizuku y de los permisos concedidos.
- El control remoto solo debe habilitarse después de emparejar ambos teléfonos y mostrar claramente la sesión activa.

## Compilar en Android Studio

Requisitos: Android Studio reciente, JDK 17 y Android SDK 35.

Comandos: gradle assembleHostDebug y gradle assembleClientDebug.

Salidas:
- app/build/outputs/apk/host/debug/app-host-debug.apk
- app/build/outputs/apk/client/debug/app-client-debug.apk
