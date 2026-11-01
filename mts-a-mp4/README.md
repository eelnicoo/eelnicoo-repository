# MTS a MP4

App Android que pasa los vídeos `.MTS` / `.M2TS` de las videocámaras AVCHD a
`.MP4` **sin perder calidad**: el vídeo se copia tal cual, sin recomprimir. Lo
único que cambia es el contenedor.

- **Vídeo**: se copia bit a bit (H.264, también 1080i y 1440×1080 anamórfico).
- **Audio**: el AC-3 (Dolby Digital), AAC o MP3 también se copia tal cual. El
  LPCM no cabe en un MP4 y se convierte a AAC de alta tasa. El interruptor
  «Audio compatible (AAC)» convierte el AC-3 a AAC por si algún reproductor no
  lo suena.
- **Fecha de grabación**: lee la fecha que guarda la cámara dentro del vídeo
  (bloque MDPM de AVCHD) y la escribe en el MP4, para que la galería lo ordene
  por el día en que se grabó. Si no la encuentra, usa la fecha del archivo.
- Elige archivos sueltos, una carpeta entera (por ejemplo `PRIVATE/AVCHD` de la
  tarjeta) o compártelos con la app desde Archivos.
- Los MP4 se guardan en `Películas/MTS a MP4`. La conversión sigue aunque cierres
  la app, con una notificación de progreso.

Paquete `dev.nico.mtsamp4`, minSdk 29, solo arm64-v8a.

## Cómo está hecho

- `app/src/main/cpp/remux.c`: el remuxado con FFmpeg 7.1.1 (MPEG-TS → MP4),
  la conversión opcional del audio a AAC y la lectura de la fecha MDPM.
- `app/src/main/cpp/jni.c`: puente JNI.
- `native/build-ffmpeg.sh`: compila un FFmpeg mínimo y estático (solo el
  demuxer MPEG-TS, el muxer MP4 y los códecs de audio necesarios).
- `native/test/`: pruebas del remuxado en el PC con vídeos AVCHD sintéticos.
- La interfaz es Kotlin + Jetpack Compose (Material You).

## Compilar

GitHub Actions (`.github/workflows/mts-a-mp4.yml`) compila FFmpeg con el NDK y
el APK, y lo deja en la rama `apk` sin firmar. Para firmarlo:

```
apksigner sign --ks mts-a-mp4.jks --out MTS-a-MP4.apk MTS-a-MP4-x.y.z-sin-firmar.apk
```

En local:

```
export ANDROID_NDK_HOME=~/Android/Sdk/ndk/28.2.13676358
native/build-ffmpeg.sh android
./gradlew assembleRelease     # firma con keystore.properties si existe
```

La clave de firma (`*.jks` + `keystore.properties`) no se sube al
repositorio. Si se pierde, las actualizaciones no se podrán instalar encima.

## Probar el remuxado en el PC

```
native/build-ffmpeg.sh host
gcc -O2 -I app/src/main/cpp -I native/ffmpeg-out/host/include \
    native/test/remux_cli.c app/src/main/cpp/remux.c -o remux_cli \
    native/ffmpeg-out/host/lib/{libavformat,libavcodec,libswresample,libavutil}.a -lm -lpthread
./remux_cli video.mts video.mp4          # copia el audio
./remux_cli video.mts video.mp4 aac      # audio a AAC
```
