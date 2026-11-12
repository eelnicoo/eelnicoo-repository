# Pulso

Panel de juego para el Pixel 10 con GrapheneOS, al estilo del de los realme GT.
Deslizas desde el borde de la pantalla y aparece, encima del juego, un panel de cristal
translúcido y desenfocado con:

- **Tres modos**: Rendimiento, Equilibrado y Ahorro. Ninguno activa el ahorro de batería del sistema.
  - **Rendimiento**: pantalla fija a la frecuencia máxima (120 Hz) y el juego en modo rendimiento
    (API de modos de juego de Android).
  - **Equilibrado**: lo que decida el sistema, sin tocar nada.
  - **Ahorro**: pantalla y juegos limitados a **60 FPS**, sin modo ahorro.
- **Datos en vivo**: temperatura de CPU, GPU y batería, FPS, consumo en vatios, RAM y estado térmico,
  con una gráfica del último minuto.
- **Botones rápidos**: HUD flotante de rendimiento, No molestar, brillo fijo, no apagar la pantalla,
  captura y liberar RAM.
- **HUD**: una píldora siempre visible con FPS, temperatura, consumo y batería. No recibe toques.
- Notificación silenciosa con los tres modos, un mosaico de ajustes rápidos y arranque al encender.

## Shizuku (recomendado)

Sin root, una app normal no puede limitar los FPS ni leer la temperatura de la CPU.
Pulso usa [Shizuku](https://shizuku.rikka.app/), que funciona sin root con la depuración inalámbrica:

1. Instala Shizuku y ábrelo.
2. En Opciones de desarrollador, activa la depuración inalámbrica. En Shizuku, pulsa «Emparejar» y sigue los pasos.
3. Pulsa «Iniciar» en Shizuku y, en Pulso, «Dar permiso».
4. Después de cada reinicio del móvil hay que volver a pulsar «Iniciar» en Shizuku.

| | Sin Shizuku | Con Shizuku |
|---|---|---|
| Límite de 60 FPS (Ahorro) | aproximado: lo pide la ventana del tirador | exacto: el mismo ajuste que «Pantalla fluida» |
| 120 Hz fijos (Rendimiento) | sí, mientras Pulso esté activo | sí, más el modo de juego en rendimiento |
| Temperatura CPU / GPU | solo si el sistema deja leer `/sys` (normalmente no) | sí (HAL térmico) |
| FPS reales | no (se muestra la frecuencia de la pantalla) | sí (contador de SurfaceFlinger) |
| Batería, consumo, RAM, estado térmico | sí | sí |
| Captura y liberar RAM | no | sí |

Con Shizuku, Pulso guarda los valores originales de `peak_refresh_rate` y `min_refresh_rate`
y los restaura al volver a Equilibrado o al desactivar el panel.

## Compilar

Sin Gradle ni Android Studio, como Folio: `./build.sh` (aapt, javac, dx, zipalign y apksigner de apt;
mira la cabecera del script). El APK firmado queda en `dist/`.

- Java 8 sin androidx ni lambdas (dx no las traduce). Paquete `dev.nico.pulso`, minSdk 31, targetSdk 35.
- `libs/shizuku-aidl-13.1.5.jar`: las interfaces AIDL de Shizuku-API (MIT, `libs/SHIZUKU-API-LICENSE.txt`).
  El cliente (`Shizuku.java`, `ShizukuProvider.java`, `Shell.java`) está hecho a mano porque la
  librería de Maven usa lambdas.
- Firma: `pulso.jks` + `keystore.properties`, fuera de git. Si se pierden, las actualizaciones no
  se podrán instalar encima.
- `.github/workflows/pulso.yml` compila el APK en GitHub Actions, lo prueba en un emulador con Android 15
  (`tools/emulador.sh`) y guarda las capturas en `capturas/`.
