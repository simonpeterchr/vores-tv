#!/bin/zsh
# Bygger Vores TV (live-tv + film/serier) uden Gradle. Biblioteker (Media3, Guava, zxing) for-dexes én gang.
set -e
cd "${0:A:h}"
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
export PATH=$JAVA_HOME/bin:$PATH
SDK=/opt/homebrew/share/android-commandlinetools
BT=$SDK/build-tools/34.0.0
AJ=$SDK/platforms/android-34/android.jar
VERSION_CODE=${VERSION_CODE:-1}
VERSION_NAME=${VERSION_NAME:-0.1}
[ -f jars/guava.jar ] || ./fetch-libs.sh
LIBS=(jars/*.jar)
CP=${(j.:.)LIBS}

if [ ! -f build-libs/classes.dex ]; then
  rm -rf build-libs && mkdir -p build-libs
  $BT/d8 --release --min-api 28 --lib $AJ --output build-libs jars/media3-*.jar jars/guava.jar jars/failureaccess.jar jars/collection.jar jars/zxing-core.jar 2>&1 | grep -v -i 'warning\|missing class' | head -5 || true
fi

rm -rf build && mkdir -p build/classes build/dex build/gen
$BT/aapt2 compile --dir res -o build/res.zip
$BT/aapt2 link -I $AJ --manifest AndroidManifest.xml -A assets \
  --min-sdk-version 28 --target-sdk-version 34 \
  --version-code $VERSION_CODE --version-name $VERSION_NAME \
  --java build/gen -o build/unsigned.apk build/res.zip
javac -nowarn -Xlint:-options -source 8 -target 8 -encoding UTF-8 -bootclasspath $AJ:$BT/core-lambda-stubs.jar -cp $CP -d build/classes $(find src build/gen -name '*.java')
$BT/d8 --min-api 28 --lib $AJ --classpath $AJ $(for j in $LIBS; do echo --classpath $j; done) --output build/dex $(find build/classes -name '*.class')
# App-dex først, bibliotekerne som classes2..N
cp build/dex/classes.dex build/apkdex-classes.dex
i=2; mkdir -p build/apk && cp build/dex/classes.dex build/apk/classes.dex
for d in build-libs/classes*.dex; do cp $d build/apk/classes$i.dex; i=$((i+1)); done
(cd build/apk && zip -q ../unsigned.apk classes*.dex)
$BT/zipalign -f 4 build/unsigned.apk build/aligned.apk
# Signering: nøgle og kode ligger i signing.local (ikke i git):  KS=/sti/til/nøgle.keystore  KS_PASS=…
[ -f signing.local ] && source ./signing.local
if [ -z "$KS" ]; then
  KS=debug.keystore; KS_PASS=android
  [ -f $KS ] || keytool -genkeypair -keystore $KS -storepass $KS_PASS -keypass $KS_PASS -alias debug -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Vores TV debug" >/dev/null 2>&1
fi
$BT/apksigner sign --ks "$KS" --ks-pass pass:$KS_PASS --out VoresTV.apk build/aligned.apk
ls -la VoresTV.apk
