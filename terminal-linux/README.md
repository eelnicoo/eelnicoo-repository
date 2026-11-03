# Terminal Linux

App Android (pensada para la tablet) con una **terminal de Linux de verdad**: Ubuntu 24.04 LTS
con `sudo`, `apt`, `git`, Python… y la posibilidad de instalar **Claude Code** para que cree
cosas en la tablet.

No hace falta rootear la tablet. Ubuntu funciona dentro de [proot](https://github.com/termux/proot),
que hace creer a los programas que están en un Linux normal: dentro de Ubuntu tu usuario es
administrador y `sudo` funciona sin contraseña (también para Claude Code).

## Instalar

1. Descarga `Terminal-Linux-<versión>.apk` (rama `apk-terminal-linux` o el artefacto de la
   última ejecución de *Terminal Linux (APK)* en Actions) y ábrelo en la tablet. Android pedirá
   permiso para instalar apps de origen desconocido.
2. Abre la app, elige tu nombre de usuario (por defecto `nico`) y, si quieres, pulsa
   **Dar acceso a los archivos de la tablet** (activa «Permitir acceso para gestionar todos los
   archivos»).
3. Pulsa **Instalar Ubuntu**: descarga unos 30 MB de cdimage.ubuntu.com y comprueba su SHA-256.
4. La primera terminal termina de configurar Ubuntu (DNS, `apt update`, `sudo`, `git`, `curl`,
   `python3`, tu usuario…). Tarda unos minutos y necesita internet.

## Uso

```bash
sudo apt update && sudo apt upgrade
sudo apt install build-essential nodejs   # lo que quieras
instalar-claude                           # instala Claude Code
claude                                    # y a trabajar
```

- **Claude Code**: `instalar-claude` usa el instalador oficial y, si su binario no arranca
  dentro de proot, instala Node.js 22 y lo instala con npm. Al iniciar sesión, `claude` abre el
  navegador de la tablet (gracias a `xdg-open` → `abrir`).
- **Archivos de la tablet**: `~/tablet` (es `/sdcard`). Lo que Claude cree ahí lo ves en la app
  Archivos.
- **`abrir <archivo o URL>`** lo abre con las apps de Android (navegador, visor de PDF…).
- **Teclas extra**: ESC, TAB, CTRL, ALT, flechas, HOME/END, PGUP/PGDN, pegar y mostrar/ocultar
  el teclado. CTRL y ALT se quedan pulsadas hasta la siguiente tecla.
- **Teclado físico**: Ctrl+Mayús+V pega, Ctrl+Mayús+T abre pestaña, Ctrl+Mayús+W la cierra.
- **Pestañas**: `+` abre otra; mantén pulsado `+` para abrir una como root o una shell de
  Android de emergencia. Mantén pulsada una pestaña para cerrarla.
- **Pellizca** para cambiar el tamaño de letra. Mantén pulsado para seleccionar y copiar.
- **Menú ⋮**: mantener activa con la pantalla apagada, permisos, ayuda, reinstalar Ubuntu, salir.

### Si Android cierra la terminal

Las sesiones viven en un servicio en primer plano (notificación «Terminal Linux»), pero Android
12+ mata los procesos hijos cuando hay demasiados. Si te pasa con tareas largas de Claude Code:

- Activa **Mantener activa con la pantalla apagada** en el menú.
- En Android 14 o superior: *Opciones de desarrollador → Desactivar restricciones de procesos
  secundarios*.
- En Android 12–13, desde un PC con adb:
  `adb shell device_config put activity_manager max_phantom_processes 2147483647`

## Límites

Es Ubuntu en proot, no una máquina virtual: no hay systemd (los servicios se arrancan a mano),
ni Docker, ni módulos del kernel, ni acceso al hardware. Todo lo demás —apt, compiladores,
Python, Node.js, git, servidores web en `localhost`…— funciona. Al ir sobre ptrace, va algo más
lento que un Linux nativo.

## Cómo está hecho

| Parte | Qué hace |
| --- | --- |
| `app/` | La app (Kotlin, sin dependencias de AndroidX). `Instalador` descarga y verifica Ubuntu Base, `TarExtractor` lo descomprime conservando enlaces y permisos (incluido setuid, necesario para `sudo`), `Linux` genera los scripts de arranque de proot y `TerminalService` mantiene las sesiones. |
| `app/src/main/assets/linux/` | Lo que se copia dentro de Ubuntu: `primer-arranque.sh`, `instalar-claude`, `abrir` (y `xdg-open`) y el perfil de bash. |
| `terminal-emulator/`, `terminal-view/` | Emulador y vista de terminal de Termux (Apache 2.0), copiados de termux-app v0.118.3. |
| `scripts/descargar-proot.sh` | Baja `proot` y `libtalloc` de los paquetes oficiales de Termux y los mete en `jniLibs` como `libproot.so`, `libproot-loader.so` y `libtalloc.so`. Desde Android 10 una app solo puede ejecutar binarios de su carpeta de librerías nativas. |
| `firma/` | Instrucciones de la clave de firma (la clave no está en git). Hay que firmar siempre con la misma: con otra habría que desinstalar, y eso borra Ubuntu. |

Compilación: GitHub Actions (`.github/workflows/terminal-linux.yml`) instala el NDK, ejecuta
`scripts/descargar-proot.sh`, compila con `./gradlew assembleRelease` y publica el APK en la rama
`apk-terminal-linux` (firmado si la clave está en los secrets del repositorio, ver `firma/LEEME.md`;
si no, sin firmar). En local hace falta el SDK de Android con la plataforma 36 y el
NDK 28.2.13676358.

Arquitecturas: arm64-v8a (la mayoría de tablets), armeabi-v7a y x86_64.

Licencias: proot es GPL-2.0 y talloc LGPL-3.0 (se distribuyen sin modificar, tal cual los
publica Termux); `terminal-emulator` y `terminal-view` son Apache 2.0.
