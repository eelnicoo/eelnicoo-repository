#!/usr/bin/env bash
# Descarga proot (con su cargador) y talloc de los paquetes oficiales de Termux y los deja en
# app/src/main/jniLibs/<abi>/ con nombre de librería (libproot.so, libproot-loader.so,
# libtalloc.so). Así Android los instala en nativeLibraryDir, la única carpeta desde la que
# una app con targetSdk >= 29 puede ejecutar binarios.
#
# Necesita: curl, dpkg-deb, sha256sum y readelf (o llvm-readelf).
set -euo pipefail
cd "$(dirname "$0")/.."

REPOS=(
    "https://packages.termux.dev/apt/termux-main"
    "https://packages-cf.termux.dev/apt/termux-main"
    "https://grimler.se/termux/termux-main"
)
declare -A ABI=([aarch64]=arm64-v8a [arm]=armeabi-v7a [x86_64]=x86_64)
OUT=app/src/main/jniLibs
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

READELF="$(command -v readelf || command -v llvm-readelf || true)"

# Campo de un paquete del índice Packages: campo <Packages> <paquete> <campo>
campo() {
    awk -v pkg="$2" -v f="$3" '
        /^Package: / { p = ($2 == pkg) }
        p && index($0, f ": ") == 1 { sub("^" f ": ", ""); v = $0 }
        END { print v }' "$1"
}

descargar_indice() { # <arch> <destino>
    local arch="$1" dst="$2" repo
    for repo in "${REPOS[@]}"; do
        if curl -fsSL --retry 3 -o "$dst" "$repo/dists/stable/main/binary-$arch/Packages"; then
            echo "$repo"
            return 0
        fi
    done
    echo "No se pudo descargar el índice de Termux para $arch" >&2
    return 1
}

# Librerías de Android que ya están en el sistema.
SISTEMA=" libc.so libdl.so libm.so liblog.so "

for arch in aarch64 arm x86_64; do
    abi="${ABI[$arch]}"
    echo "== $arch ($abi)"
    work="$TMP/$arch"
    mkdir -p "$work/root"
    repo="$(descargar_indice "$arch" "$work/Packages")"

    # proot y todas sus dependencias (recursivas).
    pendientes="proot"
    hechos=" "
    while [ -n "$pendientes" ]; do
        pkg="${pendientes%% *}"
        pendientes="$(echo "${pendientes#"$pkg"}" | xargs)"
        case "$hechos" in *" $pkg "*) continue ;; esac
        hechos="$hechos$pkg "
        file="$(campo "$work/Packages" "$pkg" Filename)"
        sha="$(campo "$work/Packages" "$pkg" SHA256)"
        ver="$(campo "$work/Packages" "$pkg" Version)"
        deps="$(campo "$work/Packages" "$pkg" Depends | sed 's/([^)]*)//g; s/|[^,]*//g; s/,/ /g' | xargs)"
        if [ -z "$file" ] || [ -z "$sha" ]; then
            echo "No encuentro $pkg para $arch" >&2
            exit 1
        fi
        echo "   $pkg $ver (depende de: ${deps:-nada})"
        curl -fsSL --retry 3 -o "$work/$pkg.deb" "$repo/$file"
        echo "$sha  $work/$pkg.deb" | sha256sum -c --quiet -
        dpkg-deb -x "$work/$pkg.deb" "$work/root"
        pendientes="$(echo "$pendientes $deps" | xargs)"
    done

    usr="$work/root/data/data/com.termux/files/usr"
    dst="$OUT/$abi"
    rm -rf "$dst"
    mkdir -p "$dst"
    install -m 0755 "$usr/bin/proot" "$dst/libproot.so"
    install -m 0755 "$usr/libexec/proot/loader" "$dst/libproot-loader.so"
    if [ -f "$usr/libexec/proot/loader32" ]; then
        install -m 0755 "$usr/libexec/proot/loader32" "$dst/libproot-loader32.so"
    fi

    # Copia las librerías que necesita proot (y las que necesitan ellas). Android solo
    # instala archivos lib*.so, así que libtalloc.so.2 va como libtalloc.so y la app crea
    # el enlace libtalloc.so.2 al arrancar (Linux.kt, VERSIONADAS).
    if [ -z "$READELF" ]; then
        echo "Hace falta readelf" >&2
        exit 1
    fi
    revisar="$dst/libproot.so"
    while [ -n "$revisar" ]; do
        bin="${revisar%% *}"
        revisar="$(echo "${revisar#"$bin"}" | xargs)"
        needed="$("$READELF" -d "$bin" | sed -n 's/.*(NEEDED).*\[\(.*\)\]/\1/p' | xargs)"
        echo "   $(basename "$bin") necesita: $needed"
        for lib in $needed; do
            case "$SISTEMA" in *" $lib "*) continue ;; esac
            case "$lib" in
            lib*.so) nombre="$lib" ;;
            libtalloc.so.2) nombre="libtalloc.so" ;;
            *)
                echo "proot necesita $lib y la app no sabe instalarla (añádela a VERSIONADAS en Linux.kt)" >&2
                exit 1
                ;;
            esac
            [ -f "$dst/$nombre" ] && continue
            origen="$usr/lib/$lib"
            if [ ! -e "$origen" ]; then
                echo "No encuentro $lib en los paquetes de Termux" >&2
                exit 1
            fi
            install -m 0644 "$(readlink -f "$origen")" "$dst/$nombre"
            revisar="$(echo "$revisar $dst/$nombre" | xargs)"
        done
    done
    ls -l "$dst"
done
