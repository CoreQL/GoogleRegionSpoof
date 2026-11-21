#!/bin/sh
# 谷歌区域伪装 —— 手动工具链构建脚本
# 用法：先 source ~/workspace/android-tools/env.sh，再执行 ./build.sh
# 产物：build/GoogleRegionSpoof-v1.2.apk（muse 测试钥签名）
set -e

PROJ="$(cd "$(dirname "$0")" && pwd)"
SRC="$PROJ/app/src/main"
BUILD="$PROJ/build"
ANDROID_JAR="$ANDROID_HOME/platforms/android-35/android.jar"
XPOSED_JAR="$PROJ/libs/api-82.jar"
KS="$HOME/workspace/android-tools/muse-test.keystore"

: "${ANDROID_HOME:?请先 source ~/workspace/android-tools/env.sh}"

rm -rf "$BUILD"
mkdir -p "$BUILD/gen" "$BUILD/classes" "$BUILD/dex"

echo "== aapt2 compile =="
aapt2 compile --dir "$SRC/res" -o "$BUILD/res.zip"

echo "== aapt2 link =="
aapt2 link -o "$BUILD/base.apk" \
    -I "$ANDROID_JAR" \
    --manifest "$SRC/AndroidManifest.xml" \
    -A "$SRC/assets" \
    --java "$BUILD/gen" \
    --min-sdk-version 26 \
    --target-sdk-version 35 \
    --version-code 4 \
    --version-name "1.2" \
    "$BUILD/res.zip"

echo "== javac =="
find "$SRC/java" "$BUILD/gen" -name '*.java' > "$BUILD/sources.txt"
javac --release 8 -encoding UTF-8 \
    -cp "$ANDROID_JAR:$XPOSED_JAR" \
    -d "$BUILD/classes" @"$BUILD/sources.txt"

echo "== d8 =="
# 只打包本模块的类；Xposed API 是框架提供的，不进 dex
d8 --lib "$ANDROID_JAR" --output "$BUILD/dex" \
    $(find "$BUILD/classes" -name '*.class')

echo "== package =="
cp "$BUILD/base.apk" "$BUILD/app.apk"
cd "$BUILD/dex" && zip -q "$BUILD/app.apk" classes.dex && cd "$PROJ"

echo "== zipalign =="
zipalign -p -f 4 "$BUILD/app.apk" "$BUILD/aligned.apk"

echo "== sign =="
apksigner sign \
    --ks "$KS" --ks-pass pass:muse1234 --key-pass pass:muse1234 \
    --out "$BUILD/GoogleRegionSpoof-v1.2.apk" "$BUILD/aligned.apk"

echo "== verify =="
apksigner verify --print-certs "$BUILD/GoogleRegionSpoof-v1.2.apk" | head -3
ls -la "$BUILD/GoogleRegionSpoof-v1.2.apk"
echo "BUILD OK"
