# PocketCode — Claude Code para móvil

Cliente Android **no oficial** que lleva la experiencia de Claude Code al teléfono
(pensado para un Pixel con GrapheneOS, sin root). Hecho para uso personal con tu
propia cuenta de Claude.

## Qué hace

- **Chat agéntico con Claude**: streaming de respuestas y bucle de uso de herramientas,
  igual que Claude Code en el escritorio.
- **Herramientas reales** sobre el dispositivo:
  - `read_file`, `write_file`, `edit_file`, `list_dir`, `grep`
  - `run_shell`: ejecuta comandos de shell. Con **Shizuku** activo corren con permisos de
    sistema (adb), que es la vía limpia en GrapheneOS sin root; si no, corren en el sandbox
    de la app.
  - `build_apk`: compila y firma APKs **en el propio móvil** (ECJ → D8 → apksig, todo JVM
    puro). Modos: `sign`, `compile_dex`, `rebuild_apk`.
- **Inicio de sesión OAuth** con tu suscripción de Claude (mismo flujo que la CLI de
  Claude Code). Los tokens se guardan cifrados en el dispositivo.

## Cómo obtener el APK instalable

El APK se compila en **GitHub Actions** (el flujo está en `.github/workflows/android.yml`):

1. Cada push a este repo lanza la build.
2. Descarga el artefacto **`pocketcode-debug-apk`** desde la pestaña *Actions* del commit,
   o, en `main`, desde la *Release* `latest`.
3. Pásalo al Pixel e instálalo (permite "instalar apps desconocidas" para tu gestor de
   archivos/navegador).

El APK va firmado con una clave de depuración (`app/src/main/assets/debug.keystore`),
suficiente para instalar y usar la app.

## Primeros pasos en la app

1. Abre **Ajustes** (icono de engranaje).
2. **Iniciar sesión** → se abre el navegador con tu cuenta Claude → autoriza → copia el
   código que muestra la web → pégalo y pulsa *Canjear código*.
3. (Opcional) Instala la app **Shizuku**, iníciala y, en Ajustes de PocketCode, activa
   *Usar Shizuku* y concede el permiso. Así `run_shell` tendrá acceso de sistema.
4. (Opcional) Fija un *directorio de trabajo*.
5. Vuelve al chat y pídele cosas a Claude.

## Sobre `build_apk` (compilar APKs en el móvil)

En el teléfono no hay `aapt2` (recursos) ni compilador de Kotlin, así que el modelo
soportado es **"APK plantilla + código Java recompilado"**:

- `compile_dex`: compila `.java` (necesita un `android.jar`/framework jar como classpath)
  y genera `classes.dex`.
- `rebuild_apk`: compila tu código, inyecta el `classes.dex` nuevo en un APK plantilla y
  lo vuelve a firmar → APK instalable.
- `sign`: firma o re-firma cualquier APK con la clave de depuración incluida.

## Notas técnicas

- `minSdk 29`, `compileSdk/targetSdk 35`, Kotlin + Jetpack Compose (Material 3).
- Toolchain on-device: Eclipse ECJ (`app/libs/ecj.jar`) + D8/R8 (`app/libs/r8.jar`) +
  `apksig`.
- El cliente de la API usa el endpoint de Mensajes con cabecera OAuth. Si Anthropic
  cambia el flujo OAuth y el login falla, es el punto a ajustar
  (`oauth/ClaudeOAuth.kt`).
