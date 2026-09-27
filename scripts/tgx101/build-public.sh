#!/bin/zsh
# TGx101 public release build (package com.tgx101.app).
# Builds Android 7+ (arm64, arm7, universal) and Android 6, 5, 4.1–4.4 APKs into
# $TGX101_OUT/TGx101-<version>/ (default ~/Desktop/TGX/Версии). Pass "--no-old" to skip Android 4–6,
# "--no-android4" to skip only Android 4.1–4.4.
set -u
cd "$(dirname "$0")/../.."
export JAVA_HOME=${JAVA_HOME:-/opt/homebrew/opt/openjdk@21} ANDROID_HOME=${ANDROID_HOME:-$HOME/Library/Android/sdk}
export ANDROID_SDK_ROOT=$ANDROID_HOME PATH="$JAVA_HOME/bin:$PATH"
VER=0.1.$(git rev-list --count --author=188923247+Ivan-k0@users.noreply.github.com HEAD)
OUT=${TGX101_OUT:-$HOME/Desktop/TGX/Версии}/TGx101-$VER
LOGS=$(mktemp -d)
mkdir -p "$OUT"
sed -i '' 's/^app.id=org.thunderdog.challegram$/app.id=com.tgx101.app/' local.properties
trap "sed -i '' 's/^app.id=com.tgx101.app\$/app.id=org.thunderdog.challegram/' '$PWD/local.properties'" EXIT
AAPT=$(ls $ANDROID_HOME/build-tools/*/aapt2 | tail -1)
build () { # gradle variant, output dir, extra flag, target file name
  rm -rf vkryl/leveldb/jni/leveldb/out vkryl/leveldb/.cxx
  ./gradlew :app:assemble$1Release $3 > $LOGS/$1.log 2>&1 || { echo "$1 FAILED, log: $LOGS/$1.log"; return 1; }
  for f in app/build/outputs/apk/$2/release/*.apk(N); do
    case "$($AAPT dump badging $f | head -1)" in *com.tgx101.app*) cp "$f" "$OUT/$4"; echo "$4 ok" ;; esac
  done
}
build LatestArm64 latestArm64 "" TGx101-$VER-arm64.apk
build LatestArm32 latestArm32 "" TGx101-$VER-arm7.apk
build LatestUniversal latestUniversal "" TGx101-$VER-universal.apk
if [[ "${1:-}" != "--no-old" ]]; then
  build LollipopUniversal lollipopUniversal "" TGx101-$VER-android5.apk
  build MarshmallowUniversal marshmallowUniversal "" TGx101-$VER-android6.apk
  [[ "${1:-}" != "--no-android4" ]] && build LegacyArm32 legacyArm32 "-PuseLegacyNdk=true" TGx101-$VER-android4.apk
fi
rm -rf vkryl/leveldb/jni/leveldb/out vkryl/leveldb/.cxx
cp app/src/main/res/mipmap-xxxhdpi/app_launcher.png "$OUT/TGx101-icon.png" 2>/dev/null || true
ls -la "$OUT"
