# Folio

App Android que junta varias imágenes en un único PDF. Interfaz oscura y minimalista.

- **Añadir**: desde la galería (selector de fotos del sistema, en el orden en que las tocas) o desde Archivos. También aparece en «Compartir» de cualquier app de fotos.
- **Ordenar**: mantén pulsada una miniatura y arrástrala. Al tocar una página: girar, mover al principio o al final, quitar. Menú «⋯»: invertir, ordenar por nombre o por fecha, quitar todas (con «Deshacer»).
- **Crear PDF**: nombre, tamaño (A4, Carta o como la imagen), márgenes y calidad (Alta, Media, Ligera). Se guarda en `Descargas/Folio`, con botones para abrirlo o compartirlo.

Las fotos van al PDF como JPEG, sin pasar por `PdfDocument`, así que el archivo pesa poco. No pide ningún permiso. Android 10 o superior.

## Compilar

`./build.sh` (aapt + javac + dx + zipalign + apksigner, sin Gradle). El APK queda en `dist/`.
La firma (`folio.jks` y `keystore.properties`) no está en git: guárdala, o las actualizaciones no se podrán instalar encima.
