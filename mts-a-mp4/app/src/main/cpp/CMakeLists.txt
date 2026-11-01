cmake_minimum_required(VERSION 3.22.1)
project(mtsremux C)

# FFmpeg estático compilado con native/build-ffmpeg.sh android
if(NOT FFMPEG_DIR)
    set(FFMPEG_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../../../native/ffmpeg-out/android-arm64")
endif()
if(NOT EXISTS "${FFMPEG_DIR}/lib/libavformat.a")
    message(FATAL_ERROR "Falta FFmpeg en ${FFMPEG_DIR}. Ejecuta: native/build-ffmpeg.sh android")
endif()

add_library(mtsremux SHARED remux.c jni.c)
target_include_directories(mtsremux PRIVATE "${FFMPEG_DIR}/include")
target_compile_options(mtsremux PRIVATE -O2 -Wall -fstack-protector-strong)
target_link_libraries(mtsremux
    "${FFMPEG_DIR}/lib/libavformat.a"
    "${FFMPEG_DIR}/lib/libavcodec.a"
    "${FFMPEG_DIR}/lib/libswresample.a"
    "${FFMPEG_DIR}/lib/libavutil.a"
    log m)
# Páginas de 16 KB (Android 15+) y sin símbolos de FFmpeg exportados.
target_link_options(mtsremux PRIVATE
    "-Wl,-z,max-page-size=16384"
    "-Wl,--exclude-libs,ALL"
    "-Wl,--gc-sections"
    "-Wl,-z,noexecstack")
