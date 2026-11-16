#!/usr/bin/env bash
# Instala proton-cal (CLI no oficial de Proton Calendar) en la Pi, sin root.
# Baja el binario estático linux-arm64 de la versión fijada, comprueba su SHA-256
# contra checksums.txt y contra la huella apuntada aquí, y lo deja en ~/servicios/proton-cal/.
# Se puede lanzar varias veces: si ya está esa versión, no hace nada.
set -euo pipefail

VERSION="v0.1.3"
ARCHIVO="proton-cal_linux_arm64"
# Huella publicada en checksums.txt de la v0.1.3 (comprobada el 2026-11-16).
HUELLA="69793a024440c50c5616d64345681a8e45836325f52a7196c1a2b51b14bccdc0"
BASE="https://github.com/cheeseandcereal/proton-cal/releases/download/${VERSION}"

DESTINO="$HOME/servicios/proton-cal"
mkdir -p "$DESTINO/config"
chmod 700 "$DESTINO/config"

if [ -x "$DESTINO/proton-cal" ] && [ "$(sha256sum "$DESTINO/proton-cal" | cut -d' ' -f1)" = "$HUELLA" ]; then
  echo "proton-cal ${VERSION} ya está instalado en $DESTINO."
  exit 0
fi

if [ "$(uname -m)" != "aarch64" ]; then
  echo "Aviso: el kernel dice $(uname -m). El binario es arm64 y necesita el kernel de 64 bits." >&2
fi

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
curl -fsSL -o "$TMP/$ARCHIVO" "$BASE/$ARCHIVO"
curl -fsSL -o "$TMP/checksums.txt" "$BASE/checksums.txt"

(cd "$TMP" && sha256sum -c checksums.txt --ignore-missing --status) || {
  echo "ERROR: el binario no coincide con checksums.txt. No instalo nada." >&2
  exit 1
}
[ "$(sha256sum "$TMP/$ARCHIVO" | cut -d' ' -f1)" = "$HUELLA" ] || {
  echo "ERROR: checksums.txt ha cambiado respecto a la huella apuntada. No instalo nada." >&2
  exit 1
}

install -m 755 "$TMP/$ARCHIVO" "$DESTINO/proton-cal"
"$DESTINO/proton-cal" --version
echo "Instalado en $DESTINO/proton-cal. Ahora: bash $DESTINO/iniciar-sesion.sh"
