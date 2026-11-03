#!/bin/bash
# Terminal Linux · primer arranque de Ubuntu.
#
# La app lo ejecuta como root dentro de proot cada vez que abres una terminal
# hasta que termina bien (deja /etc/terminal-linux/setup-ok). Si algo falla
# (por ejemplo, sin internet), basta con abrir otra pestaña para reintentarlo.
#
# Uso: primer-arranque.sh <usuario> [zona horaria] [uid]

USUARIO="${1:-nico}"
ZONA="${2:-Etc/UTC}"
UID_USUARIO="${3:-1000}"

export DEBIAN_FRONTEND=noninteractive
export LANG=C.UTF-8
export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin

paso() { printf '\n\033[1;36m==> %s\033[0m\n' "$*"; }
error() {
    printf '\n\033[1;31m✗ %s\033[0m\n' "$*" >&2
    exit 1
}

paso "Preparando Ubuntu para funcionar dentro de Android"

# Android no tiene /etc/resolv.conf: Ubuntu necesita sus propios DNS.
rm -f /etc/resolv.conf
printf 'nameserver 1.1.1.1\nnameserver 8.8.8.8\nnameserver 2606:4700:4700::1111\n' >/etc/resolv.conf
printf '127.0.0.1\tlocalhost\n::1\tlocalhost ip6-localhost ip6-loopback\n' >/etc/hosts

# Dentro de proot apt no puede pasar al usuario _apt para descargar.
cat >/etc/apt/apt.conf.d/99-terminal-linux <<'EOF'
// Terminal Linux: dentro de proot las descargas de apt se hacen como root.
APT::Sandbox::User "root";
Acquire::Retries "3";
EOF

# Grupos de Android (inet, everybody…) con nombre, para que «id» y «groups»
# no se quejen de números sin nombre.
for g in $(id -G 2>/dev/null); do
    getent group "$g" >/dev/null 2>&1 || groupadd -g "$g" "android_$g" 2>/dev/null || true
done

paso "Actualizando la lista de paquetes (apt update)"
apt-get update || error "No se pudo descargar la lista de paquetes. Revisa la conexión a internet y abre otra pestaña para reintentarlo."

# Si un intento anterior se cortó a medias, se termina antes de seguir.
dpkg --configure -a >/dev/null 2>&1
apt-get -y -f install >/dev/null 2>&1

paso "Instalando sudo y herramientas básicas (tarda unos minutos)"
apt-get install -y \
    sudo ca-certificates curl wget git nano less procps psmisc bash-completion \
    unzip zip xz-utils tzdata file openssh-client python3 ripgrep jq ||
    error "Falló la instalación de paquetes. Abre otra pestaña para reintentarlo."

# Zona horaria de la tablet.
if [ -f "/usr/share/zoneinfo/$ZONA" ]; then
    ln -sf "/usr/share/zoneinfo/$ZONA" /etc/localtime
    echo "$ZONA" >/etc/timezone
fi

paso "Creando tu usuario «$USUARIO» con sudo"
if ! id -u "$USUARIO" >/dev/null 2>&1; then
    # La app entra con --change-id=$UID_USUARIO, así que ese uid tiene que ser suyo.
    otro="$(getent passwd "$UID_USUARIO" | cut -d: -f1)"
    [ -n "$otro" ] && userdel -r "$otro" >/dev/null 2>&1
    otro="$(getent group "$UID_USUARIO" | cut -d: -f1)"
    [ -n "$otro" ] && groupdel "$otro" >/dev/null 2>&1
    useradd -m -u "$UID_USUARIO" -U -s /bin/bash -G sudo "$USUARIO" ||
        error "No se pudo crear el usuario $USUARIO"
fi
# Sin contraseña: sudo no la pide (así Claude Code también puede usar sudo).
passwd -d "$USUARIO" >/dev/null 2>&1
echo "$USUARIO ALL=(ALL:ALL) NOPASSWD: ALL" >/etc/sudoers.d/terminal-linux
chmod 0440 /etc/sudoers.d/terminal-linux

# Dentro de proot todos los archivos parecen de root: sin esto git se
# negaría a trabajar en tus repositorios («dubious ownership»).
git config --system --add safe.directory '*'

# Acceso directo a los archivos de la tablet (/sdcard).
ln -sfn /sdcard "/home/$USUARIO/tablet"
ln -sfn /sdcard /root/tablet

mkdir -p /etc/terminal-linux
date >/etc/terminal-linux/setup-ok

paso "¡Ubuntu está listo!"
