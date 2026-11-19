#!/usr/bin/env bash
# Builds GameAssistantSetup.apk without Gradle: aapt2 -> javac -> d8 -> zipalign -> apksigner.
# Needs: JDK (javac/keytool) on PATH, Android SDK build-tools + platform. Override SDK/BT/PLAT if yours differ.
set -euo pipefail
cd "$(dirname "$0")"

# App version: VERSION follows SemVer (see CHANGELOG.md); VERSION_CODE must go up by 1 for every release.
VERSION=1.1.0
VERSION_CODE=2

SDK="${SDK:-$LOCALAPPDATA/Android/Sdk}"
BT="${BT:-$SDK/build-tools/android-37.0}"
JAR="${PLAT:-$SDK/platforms/android-37.0}/android.jar"
OUT=build

rm -rf "$OUT" && mkdir -p "$OUT/classes"

"$BT/aapt2" compile --dir res -o "$OUT/res.zip"
"$BT/aapt2" link -o "$OUT/base.apk" -I "$JAR" --manifest AndroidManifest.xml -R "$OUT/res.zip" \
  --min-sdk-version 30 --target-sdk-version 35 --version-code "$VERSION_CODE" --version-name "$VERSION"

javac -source 8 -target 8 -Xlint:-options -encoding UTF-8 -bootclasspath "$JAR" -classpath "$BT/core-lambda-stubs.jar" \
  -d "$OUT/classes" $(find src -name '*.java')

"$BT/d8.bat" --release --min-api 30 --lib "$JAR" --output "$OUT" $(find "$OUT/classes" -name '*.class')

(cd "$OUT" && "$BT/aapt" add base.apk classes.dex >/dev/null)
"$BT/zipalign" -f 4 "$OUT/base.apk" "$OUT/aligned.apk"

if [ -n "${GASH_KS:-}" ]; then
  # Release: sign with your own key. apksigner asks for the password, so it never lands in a file or shell history.
  APK=GASH-release.apk
  "$BT/apksigner.bat" sign --ks "$GASH_KS" --ks-key-alias "${GASH_KS_ALIAS:-gash}" --out "$APK" "$OUT/aligned.apk"
else
  # Development: throwaway debug key, kept next to the project.
  APK=GameAssistantSetup.apk
  KS=debug.keystore
  [ -f "$KS" ] || keytool -genkeypair -keystore "$KS" -storepass android -keypass android -alias key \
    -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=GameAssistantSetup" >/dev/null
  "$BT/apksigner.bat" sign --ks "$KS" --ks-pass pass:android --out "$APK" "$OUT/aligned.apk"
fi

echo "OK: $(pwd)/$APK"
