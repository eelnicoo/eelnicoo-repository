#!/usr/bin/env bash
# Compila Folio sin Gradle ni Android Studio: aapt + javac + dx + zipalign + apksigner.
#
# Requisitos (Ubuntu/Debian): sudo apt install openjdk-21-jdk aapt dalvik-exchange zipalign apksigner
# y los android.jar de las API 35 (ANDROID_JAR, para Java) y 34 (RES_JAR, para aapt: el aapt
# de Debian no sabe leer el formato de recursos de la 35). Para firmar hacen falta folio.jks y
# keystore.properties en esta carpeta (no están en git). Si faltan, se crean nuevos,
# pero entonces el APK no se podrá instalar encima de uno firmado con la clave anterior.
set -euo pipefail
cd "$(dirname "$0")"

SDK="${ANDROID_HOME:-$HOME/android-sdk}"
ANDROID_JAR="${ANDROID_JAR:-$SDK/platforms/android-35/android.jar}"
RES_JAR="${RES_JAR:-$SDK/platforms/android-34/android.jar}"
for j in "$ANDROID_JAR" "$RES_JAR"; do
    [ -f "$j" ] || { echo "No encuentro $j (define ANDROID_JAR y RES_JAR)"; exit 1; }
done
DX="$(command -v dalvik-exchange || command -v dx)"
VERSION="$(sed -n 's/.*android:versionName="\([^"]*\)".*/\1/p' AndroidManifest.xml)"
OUT=build

rm -rf "$OUT"
mkdir -p "$OUT/classes" "$OUT/dex" dist

echo "› Recursos"
aapt package -f -M AndroidManifest.xml -S res -I "$RES_JAR" -F "$OUT/res.apk"

echo "› Java"
javac -source 8 -target 8 -encoding UTF-8 -Xlint:all -Xlint:-options -Xlint:-deprecation \
    -bootclasspath "$ANDROID_JAR" -d "$OUT/classes" $(find src -name '*.java')

echo "› Dex"
"$DX" --dex --min-sdk-version=29 --output="$OUT/dex/classes.dex" "$OUT/classes"

echo "› APK"
# resources.arsc tiene que ir sin comprimir (obligatorio desde Android 11).
mkdir -p "$OUT/apk"
(cd "$OUT/apk" && unzip -q ../res.apk)
cp "$OUT/dex/classes.dex" "$OUT/apk/"
(cd "$OUT/apk" && zip -q -X -0 ../unsigned.apk resources.arsc AndroidManifest.xml \
    && zip -q -X -r -9 ../unsigned.apk . -x resources.arsc AndroidManifest.xml)
zipalign -f -p 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"

echo "› Firma"
if [ ! -f keystore.properties ]; then
    PASS="$(head -c 18 /dev/urandom | base64 | tr -d '/+=')"
    keytool -genkeypair -keystore folio.jks -storetype PKCS12 -alias folio -keyalg RSA -keysize 4096 \
        -validity 36500 -dname "CN=Folio, O=Nico" -storepass "$PASS" -keypass "$PASS" >/dev/null 2>&1
    printf 'storeFile=folio.jks\nstorePassword=%s\nkeyAlias=folio\nkeyPassword=%s\n' "$PASS" "$PASS" > keystore.properties
    echo "  (clave nueva creada: guarda folio.jks y keystore.properties)"
fi
prop() { sed -n "s/^$1=//p" keystore.properties; }
APK="dist/Folio-$VERSION.apk"
apksigner sign --ks "$(prop storeFile)" --ks-pass "pass:$(prop storePassword)" \
    --ks-key-alias "$(prop keyAlias)" --key-pass "pass:$(prop keyPassword)" --out "$APK" "$OUT/aligned.apk"
rm -f "$APK.idsig"
apksigner verify "$APK"
echo "✓ $APK ($(du -h "$APK" | cut -f1))"
