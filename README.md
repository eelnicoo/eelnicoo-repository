# PocketCode — Claude Code para móvil

Cliente Android **no oficial** que lleva la experiencia de Claude Code al teléfono
(pensado para un Pixel con GrapheneOS, sin root). Hecho para uso personal con tu
propia cuenta de Claude.

## Qué hace

- **Interfaz como la app de Claude**: menú lateral con el historial, saludo con el
  logo, respuestas en tipografía serif (o sans, a elegir), tema claro/oscuro, selector
  de modelo con "pensamiento extendido", botón de enviar/parar.
- **Historial de chats** guardado en el móvil: buscar, destacar, renombrar y borrar;
  agrupado por fecha; títulos automáticos. Las respuestas siguen en segundo plano si
  cambias de chat (una chispa animada lo indica en el menú).
- **Mensajes**: copiar, compartir, reintentar o "continuar" una respuesta cortada,
  editar un mensaje enviado (mantén pulsado), razonamiento plegable y pasos de
  herramientas agrupados.
- **Adjuntos**: fotos, cámara, archivos de texto y PDF.
- **Markdown completo**: bloques de código con resaltado y botón "Copiar", tablas,
  listas (también numeradas y con casillas), citas, enlaces.
- **Herramientas reales** sobre el dispositivo: `read_file`, `write_file`,
  `edit_file`, `list_dir`, `grep`, `run_shell` (con **Shizuku**, con permisos de
  depuración; sin él, dentro del sandbox de la app) y `build_apk` (compila y firma
  APKs en el propio móvil con ECJ → D8 → apksig).
- **Inicio de sesión OAuth** con tu suscripción de Claude (mismo flujo que la CLI de
  Claude Code). Los tokens se guardan cifrados en el dispositivo.

## Cómo obtener el APK instalable

El APK se compila en **GitHub Actions** (`.github/workflows/android.yml`), que también
ejecuta los tests unitarios:

1. Cada push lanza la build.
2. Descarga el artefacto **`pocketcode-debug-apk`** desde la pestaña *Actions* (es un
   `.zip` con el `.apk` dentro), o, en `main`, desde la *Release* `latest`.
3. Instálalo encima de la versión anterior (no se pierde la sesión ni el historial).

El APK va firmado con una clave de depuración (`app/src/main/assets/debug.keystore`).

## Primeros pasos

1. Menú lateral → tu perfil → **Ajustes** → *Iniciar sesión* → autoriza en el
   navegador → pega el código → *Canjear código*.
2. (Opcional) Pon tu nombre para el saludo y elige tema y fuente.
3. (Opcional) Shizuku: instala la app **Shizuku** de *Rikka*, iníciala con depuración
   inalámbrica, activa *Usar Shizuku* en Ajustes y concede el permiso.

## Notas técnicas

- `minSdk 29`, `compileSdk/targetSdk 35`, Kotlin + Jetpack Compose (Material 3).
- Cliente HTTP + SSE propio para la API de Mensajes: conserva los bloques tal cual
  (incluidas las firmas del razonamiento), reintenta con espera, refresca el token y
  desactiva por modelo cualquier opción que el servidor rechace.
- Los chats se guardan como JSON (uno por chat + índice) con escritura atómica.
- Toolchain on-device: Eclipse ECJ (`app/libs/ecj.jar`) + D8/R8 (`app/libs/r8.jar`) +
  `apksig`. En el móvil no hay `aapt2` ni compilador de Kotlin, así que `build_apk`
  usa el modelo "APK plantilla + código Java recompilado".
- Si Anthropic cambia el flujo OAuth y el login falla, el punto a ajustar es
  `oauth/ClaudeOAuth.kt`.
