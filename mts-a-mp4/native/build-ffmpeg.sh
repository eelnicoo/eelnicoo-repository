#!/usr/bin/env bash
# Compila un FFmpeg mínimo (solo lo necesario para MTS -> MP4) como librerías
# estáticas.
#
#   native/build-ffmpeg.sh android   -> native/ffmpeg-out/android-arm64  (necesita ANDROID_NDK_HOME)
#   native/build-ffmpeg.sh host      -> native/ffmpeg-out/host           (para las pruebas en el PC)
#
# Descarga el código de ffmpeg.org y comprueba su SHA-256.
set -euo pipefail

TARGET="${1:-android}"
FFMPEG_VERSION="7.1.1"
FFMPEG_SHA256="733984395e0dbbe5c046abda2dc49a5544e7e0e1e2366bba849222ae9e3a03b1"
FFMPEG_URL="https://ffmpeg.org/releases/ffmpeg-${FFMPEG_VERSION}.tar.xz"
ANDROID_API=29

HERE="$(cd "$(dirname "$0")" && pwd)"
WORK="${HERE}/ffmpeg-build"
SRC="${WORK}/ffmpeg-${FFMPEG_VERSION}"
TARBALL="${FFMPEG_TARBALL:-${WORK}/ffmpeg-${FFMPEG_VERSION}.tar.xz}"
JOBS="$(getconf _NPROCESSORS_ONLN 2>/dev/null || echo 4)"

mkdir -p "${WORK}"
if [ ! -f "${TARBALL}" ]; then
  echo "Descargando FFmpeg ${FFMPEG_VERSION}..."
  curl -fL --retry 4 -o "${TARBALL}.part" "${FFMPEG_URL}"
  mv "${TARBALL}.part" "${TARBALL}"
fi
echo "${FFMPEG_SHA256}  ${TARBALL}" | sha256sum -c -
if [ ! -d "${SRC}" ]; then
  tar -xf "${TARBALL}" -C "${WORK}"
fi

# Componentes: demuxer MPEG-TS, muxer MP4, parsers para copiar sin decodificar,
# decodificadores de audio + codificador AAC para la opción de audio compatible.
COMMON=(
  --disable-everything
  --disable-programs
  --disable-doc
  --disable-network
  --disable-autodetect
  --disable-avdevice
  --disable-avfilter
  --disable-swscale
  --disable-debug
  --enable-static
  --disable-shared
  --enable-pic
  --enable-swresample
  --enable-demuxer=mpegts
  --enable-muxer=mp4
  --enable-parser=h264,hevc,mpegvideo,mpeg4video,vc1,ac3,aac,aac_latm,mpegaudio
  --enable-bsf=aac_adtstoasc,extract_extradata
  --enable-decoder=h264,hevc,ac3,eac3,aac,aac_latm,mp2,mp3,pcm_bluray,pcm_dvd
  --enable-encoder=aac
)

case "${TARGET}" in
  android)
    : "${ANDROID_NDK_HOME:?Define ANDROID_NDK_HOME con la ruta del NDK}"
    TC="${ANDROID_NDK_HOME}/toolchains/llvm/prebuilt/linux-x86_64"
    PREFIX="${HERE}/ffmpeg-out/android-arm64"
    BUILD="${WORK}/build-android-arm64"
    EXTRA=(
      --target-os=android
      --arch=aarch64
      --cpu=armv8-a
      --enable-cross-compile
      --cc="${TC}/bin/aarch64-linux-android${ANDROID_API}-clang"
      --cxx="${TC}/bin/aarch64-linux-android${ANDROID_API}-clang++"
      --ar="${TC}/bin/llvm-ar"
      --nm="${TC}/bin/llvm-nm"
      --ranlib="${TC}/bin/llvm-ranlib"
      --strip="${TC}/bin/llvm-strip"
      --sysroot="${TC}/sysroot"
      --enable-neon
      --extra-cflags="-O2 -fPIC -fstack-protector-strong"
    )
    ;;
  host)
    PREFIX="${HERE}/ffmpeg-out/host"
    BUILD="${WORK}/build-host"
    EXTRA=(--extra-cflags="-O2 -fPIC")
    ;;
  *)
    echo "Uso: $0 android|host" >&2
    exit 1
    ;;
esac

rm -rf "${BUILD}"
mkdir -p "${BUILD}"
cd "${BUILD}"
"${SRC}/configure" --prefix="${PREFIX}" "${COMMON[@]}" "${EXTRA[@]}"
make -j"${JOBS}"
rm -rf "${PREFIX}"
make install
echo "FFmpeg listo en ${PREFIX}"
