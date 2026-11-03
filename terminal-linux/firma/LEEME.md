# Firma de Terminal Linux

La app se firma con una clave propia (`terminal-linux.p12`, alias `terminal-linux`).
**La clave no está en el repositorio**: guárdala tú en un sitio seguro.

Hay que firmar siempre con la misma clave. Android solo instala una actualización encima si
va firmada igual que la versión instalada; con otra clave habría que desinstalar la app, y eso
borra el Ubuntu de la tablet con todo lo que tenga dentro.

## Firmar en local

Deja en esta carpeta `terminal-linux.p12` y un `keystore.properties` así:

```
storeFile=terminal-linux.p12
storePassword=<contraseña>
keyAlias=terminal-linux
keyPassword=<contraseña>
```

`./gradlew assembleRelease` saldrá firmado.

## Firmar en GitHub Actions

En el repositorio: *Settings → Secrets and variables → Actions → New repository secret*:

- `TERMINAL_LINUX_KEYSTORE`: el archivo en base64 (`base64 -w0 terminal-linux.p12`).
- `TERMINAL_LINUX_KEYSTORE_PASSWORD`: la contraseña.

Sin esos secrets, Actions publica el APK sin firmar (`…-sin-firmar.apk`) y hay que firmarlo con
`apksigner sign --ks terminal-linux.p12 --ks-key-alias terminal-linux …` antes de instalarlo.
