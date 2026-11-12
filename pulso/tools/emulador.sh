#!/usr/bin/env bash
# Prueba Pulso en un emulador (GitHub Actions): instala el APK, lo activa, abre el panel
# deslizando desde el borde, en vertical y en horizontal, y saca capturas a pulso/capturas.
# Si hay shizuku.apk en la raíz, también prueba las funciones avanzadas con Shizuku.
set -u
cd "$(dirname "$0")/../.."
OUT=pulso/capturas
rm -rf "$OUT"
mkdir -p "$OUT"
PKG=dev.nico.pulso

shot() { sleep "${2:-1.5}"; adb exec-out screencap -p > "$OUT/$1.png"; echo "· captura $1"; }
ui() { adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; adb shell cat /sdcard/ui.xml; }
# bounds <atributo> <valor> → "x1 y1 x2 y2" del primer nodo cuyo atributo vale eso
bounds() { ui | tr '>' '\n' | grep -F "$1=\"$2\"" | head -1 | grep -o 'bounds="[^"]*"' | grep -o '[0-9]\+' | tr '\n' ' '; }
tap_on() {
    local b
    b=$(bounds "$1" "$2")
    if [ -z "$b" ]; then echo "· no encuentro $1=\"$2\""; return 1; fi
    set -- $b
    adb shell input tap $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 ))
    echo "· toque en «$2»"
}
# modo <0|1|2>: toca Rendimiento, Equilibrado o Ahorro en el selector del panel
modo() {
    local b
    b=$(bounds content-desc "Modo")
    if [ -z "$b" ]; then echo "· no encuentro el selector de modo"; return 1; fi
    set -- $b
    local w=$(( $3 - $1 ))
    adb shell input tap $(( $1 + w * (2 * MODO + 1) / 6 )) $(( ($2 + $4) / 2 ))
}
size() { adb shell wm size | grep -o '[0-9]*x[0-9]*' | tail -1; }
# Desliza desde el borde izquierdo a la altura del tirador (34 % de la altura actual)
abrir_panel() {
    local h=$1
    local y=$(( h * 34 / 100 ))
    adb shell input swipe 2 $y 420 $y 300
}

read PW PH < <(size | tr 'x' ' ')
echo "Pantalla ${PW}x${PH}"

adb install -r pulso/dist/Pulso-*.apk
adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS
adb shell appops set $PKG SYSTEM_ALERT_WINDOW allow
adb shell settings put system accelerometer_rotation 0
adb shell settings put system user_rotation 0

echo "== App"
adb shell am start -W -n $PKG/.MainActivity
shot 01-app 3
adb shell input swipe 540 1900 540 700 400
shot 02-app-2
adb shell input swipe 540 1900 540 700 400
shot 03-app-3
adb shell input swipe 540 1900 540 300 400
shot 04-app-4

adb shell am force-stop $PKG
adb shell am start -W -n $PKG/.MainActivity
sleep 2
tap_on text "Activar"
shot 05-app-activa 3

echo "== Panel en vertical"
adb shell input keyevent HOME
shot 06-inicio-tirador 2
abrir_panel $PH
shot 07-panel 3
MODO=0 modo && shot 08-panel-rendimiento 2
tap_on content-desc "HUD" && shot 09-panel-hud 2
adb shell input tap $(( PW - 30 )) $(( PH - 300 ))
shot 10-hud 2

echo "== Panel en horizontal"
adb shell am start -W -n $PKG/.MainActivity
adb shell settings put system user_rotation 1
sleep 3
shot 11-app-horizontal 1
abrir_panel $PW
shot 12-panel-horizontal 3
MODO=2 modo && shot 13-panel-horizontal-ahorro 2
adb shell input tap $(( PH - 30 )) $(( PW - 60 ))
adb shell settings put system user_rotation 0
sleep 2

if [ -f shizuku.apk ]; then
    echo "== Shizuku"
    adb install -r shizuku.apk
    adb shell monkey -p moe.shizuku.privileged.api -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
    sleep 5
    shot 14-shizuku-app 1
    adb shell input keyevent HOME
    adb shell sh /storage/emulated/0/Android/data/moe.shizuku.privileged.api/start.sh 2>&1 | tail -5
    APKDIR=$(adb shell pm path moe.shizuku.privileged.api | head -1 | sed 's/^package://; s/base\.apk.*$//' | tr -d '\r')
    adb shell "ls ${APKDIR}lib/*/ 2>/dev/null; ${APKDIR}lib/x86_64/libshizuku.so" 2>&1 | tail -5
    sleep 4
    adb shell am force-stop $PKG
    adb shell am start -W -n $PKG/.MainActivity
    sleep 4
    shot 15-app-shizuku 1
    tap_on text "Dar permiso"
    sleep 3
    shot 16-permiso 1
    for t in "Allow all the time" "Allow" "ALLOW" "Permitir siempre" "Permitir"; do
        tap_on text "$t" && break
    done
    sleep 3
    shot 17-app-shizuku-listo 1
    tap_on text "Activar"
    sleep 2
    adb shell input keyevent HOME
    abrir_panel $PH
    shot 18-panel-shizuku 6
    MODO=2 modo
    sleep 4
    shot 19-panel-shizuku-ahorro 2
    {
        echo "peak_refresh_rate en Ahorro: $(adb shell settings get system peak_refresh_rate)"
        echo "min_refresh_rate en Ahorro: $(adb shell settings get system min_refresh_rate)"
    } | tee "$OUT/shizuku.txt"
    MODO=1 modo
    sleep 4
    {
        echo "peak_refresh_rate en Equilibrado: $(adb shell settings get system peak_refresh_rate)"
        echo "min_refresh_rate en Equilibrado: $(adb shell settings get system min_refresh_rate)"
    } | tee -a "$OUT/shizuku.txt"
    adb shell input tap $(( PW - 30 )) $(( PH - 300 ))
fi

echo "== Registros"
adb logcat -d -b crash > "$OUT/crash.txt"
adb logcat -d | grep -iE "pulso|AndroidRuntime|FATAL" | tail -300 > "$OUT/logcat.txt"
echo "--- crash ---"
cat "$OUT/crash.txt"
echo "--- fin ---"
