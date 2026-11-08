#!/usr/bin/env bash
# Android Gradle Plugin(Google Maven)에 접근할 수 없는 환경에서 mock 디버그 APK 를 직접 만드는 스크립트.
# 평소에는 Android Studio 또는 ./gradlew assembleMockDebug 를 쓰면 된다.
#
# 필요 도구: aapt2, zipalign, apksigner, dalvik-exchange(dx), kotlinc, JDK 17+
#   ANDROID_JAR : resources.arsc 가 들어 있는 API 34 android.jar (예: Robolectric android-all-14)
#   KOTLIN_HOME : kotlinc 압축 해제 경로
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ANDROID_JAR="${ANDROID_JAR:?ANDROID_JAR 를 지정하세요}"
KOTLIN_HOME="${KOTLIN_HOME:?KOTLIN_HOME 을 지정하세요}"
FLAVOR="${FLAVOR:-mock}"
APP_ID="com.otiumframe.mock"
OUT="$ROOT/build/offline"
APK="$OUT/OtiumFrame-$FLAVOR-debug.apk"

rm -rf "$OUT" && mkdir -p "$OUT"/{res,gen,classes,dex}

echo "[1/6] 리소스 컴파일"
aapt2 compile --dir "$ROOT/app/src/main/res" -o "$OUT/res/main.zip"

echo "[2/6] 리소스 링크 + R 생성"
sed "s#<manifest #<manifest package=\"com.otiumframe\" #" "$ROOT/app/src/main/AndroidManifest.xml" > "$OUT/AndroidManifest.xml"
aapt2 link -I "$ANDROID_JAR" --manifest "$OUT/AndroidManifest.xml" \
  --rename-manifest-package "$APP_ID" \
  --min-sdk-version 28 --target-sdk-version 35 \
  --version-code 1 --version-name "0.1.0-$FLAVOR" \
  --java "$OUT/gen" -o "$OUT/base.apk" "$OUT/res/main.zip"

echo "[3/6] Kotlin 컴파일 (main + $FLAVOR)"
SRC=$(find "$ROOT/app/src/main/java" "$ROOT/app/src/$FLAVOR/java" "$OUT/gen" -name '*.kt' -o -name '*.java')
"$KOTLIN_HOME/bin/kotlinc" -nowarn -jvm-target 1.8 -Xlambdas=class -Xsam-conversions=class -Xstring-concat=inline \
  -no-reflect -classpath "$ANDROID_JAR" -d "$OUT/classes" $SRC
JAVA_SRC=$(find "$OUT/gen" -name '*.java')
javac -nowarn --release 8 -classpath "$ANDROID_JAR:$OUT/classes" -d "$OUT/classes" $JAVA_SRC 2>&1 | grep -v '^warning' || true

echo "[4/6] DEX 변환 (앱 + kotlin-stdlib)"
cp "$KOTLIN_HOME/lib/kotlin-stdlib.jar" "$OUT/stdlib.jar"
zip -q -d "$OUT/stdlib.jar" 'META-INF/versions/*' 'META-INF/*.kotlin_module' 2>/dev/null || true
dalvik-exchange --dex --min-sdk-version=26 --output="$OUT/dex/classes.dex" "$OUT/classes" "$OUT/stdlib.jar"

echo "[5/6] APK 패키징"
cp "$OUT/base.apk" "$OUT/unsigned.apk"
(cd "$OUT/dex" && zip -q "$OUT/unsigned.apk" classes.dex)
zipalign -f -p 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"

echo "[6/6] 디버그 키로 서명"
KS="$ROOT/build/debug.keystore"
if [ ! -f "$KS" ]; then
  keytool -genkeypair -keystore "$KS" -storepass android -keypass android -alias androiddebugkey \
    -dname "CN=Android Debug,O=Android,C=US" -keyalg RSA -keysize 2048 -validity 10000 >/dev/null 2>&1
fi
apksigner sign --ks "$KS" --ks-pass pass:android --key-pass pass:android --out "$APK" "$OUT/aligned.apk"
apksigner verify "$APK"
echo "완료: $APK"
