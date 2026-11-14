#!/bin/bash
set -e

SDK=/opt/android-sdk
BT=$SDK/build-tools/36.0.0
AAPT2=/opt/aapt2-wrapper
AJ=$SDK/platforms/android-36/android.jar
ROOT=/workspace/carsickfix
B=$ROOT/build
PKG=io.github.carsickfix

rm -rf "$B"
mkdir -p "$B/stub" "$B/mod" "$B/apk/assets"

echo "[1/7] compile xposed api stubs"
javac -source 8 -target 8 -nowarn -d "$B/stub" $(find "$ROOT/src/de" -name '*.java') 2>&1 | grep -v 'bootstrap class path' || true
jar cf "$B/stubs.jar" -C "$B/stub" .

echo "[2/7] compile module"
javac -source 8 -target 8 -nowarn -cp "$B/stub:$AJ" -d "$B/mod" $(find "$ROOT/src/io" -name '*.java') 2>&1 | grep -v 'bootstrap class path' || true

echo "[3/7] dex"
"$BT/d8" --lib "$AJ" --classpath "$B/stubs.jar" --min-api 30 --output "$B" $(find "$B/mod" -name '*.class')

echo "[4/7] aapt2 compile"
"$AAPT2" compile --dir "$ROOT/res" -o "$B/res.zip"

echo "[5/7] aapt2 link"
"$AAPT2" link -o "$B/base.apk" -I "$AJ" --manifest "$ROOT/AndroidManifest.xml" "$B/res.zip"

echo "[6/7] package"
cp "$B/base.apk" "$B/unsigned.apk"
cp "$B/classes.dex" "$B/apk/classes.dex"
cp "$ROOT/assets/xposed_init" "$B/apk/assets/xposed_init"
( cd "$B/apk" && zip -q -r ../unsigned.apk classes.dex assets/xposed_init )

echo "[7/7] sign"
if [ ! -f "$ROOT/keystore.jks" ]; then
  keytool -genkeypair -v -keystore "$ROOT/keystore.jks" -storepass carsickfix -keypass carsickfix \
    -alias carsickfix -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=CarSickFix, OU=Eta, O=Eta, L=CN, ST=CN, C=CN" >/dev/null 2>&1
fi
zipalign -f 4 "$B/unsigned.apk" "$B/aligned.apk"
apksigner sign --ks "$ROOT/keystore.jks" --ks-pass pass:carsickfix --key-pass pass:carsickfix \
  --ks-key-alias carsickfix --out "$B/carsickfix.apk" "$B/aligned.apk"
apksigner verify --print-certs "$B/carsickfix.apk" | head -3

ls -l "$B/carsickfix.apk"
