#!/bin/zsh
# PlumaGram test builds («PlumaGram Т», package com.tgx101.app, icon with «Т», diagnostics log
# PlumaGram-T-diagnostics.txt): install next to the public app. Builds Android 7+ universal and Android 4.1–4.4
# into $TGX101_OUT/PlumaGram-T-<version>/ (default ~/Desktop/TGX/Версии).
set -u
cd "$(dirname "$0")/../.."
export JAVA_HOME=${JAVA_HOME:-/opt/homebrew/opt/openjdk@25} ANDROID_HOME=${ANDROID_HOME:-$HOME/Library/Android/sdk}
export ANDROID_SDK_ROOT=$ANDROID_HOME PATH="$JAVA_HOME/bin:$PATH"
VER=0.1.$(git rev-list --count --author=188923247+Ivan-k0@users.noreply.github.com HEAD)
OUT=${TGX101_OUT:-$HOME/Desktop/TGX/Версии}/PlumaGram-T-$VER
LOGS=$(mktemp -d)
mkdir -p "$OUT"
AAPT=$(ls $ANDROID_HOME/build-tools/*/aapt2 | tail -1)
build () { # gradle variant, output dir, extra flag, target file name
  rm -rf vkryl/leveldb/jni/leveldb/out vkryl/leveldb/.cxx
  ./gradlew :app:assemble$1Release -Ptgx101Test=true -Ptgx101Diag=true $3 > $LOGS/$1.log 2>&1 || { echo "$1 FAILED, log: $LOGS/$1.log"; return 1; }
  for f in app/build/outputs/apk/$2/release/*.apk(N); do
    case "$($AAPT dump badging $f | head -1)" in *com.tgx101.app*) cp "$f" "$OUT/$4"; echo "$4 ok" ;; esac
  done
}
build LatestUniversal latestUniversal "" PlumaGram-T-$VER-universal.apk
build LegacyArm32 legacyArm32 "-PuseLegacyNdk=true" PlumaGram-T-$VER-android4.apk
rm -rf vkryl/leveldb/jni/leveldb/out vkryl/leveldb/.cxx
ls -la "$OUT"
