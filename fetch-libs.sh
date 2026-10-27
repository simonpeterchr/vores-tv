#!/bin/zsh
# Henter tredjepartsbiblioteker (Apache 2.0) fra Google Maven og Maven Central og tjekker SHA-1.
set -e
cd "${0:A:h}"
mkdir -p jars
G=https://dl.google.com/android/maven2; C=https://repo1.maven.org/maven2; V=1.4.1
get() { # url fil [aar]
  curl -sfL -o /tmp/vt_lib "$1"
  want=$(curl -sfL "$1.sha1" | cut -c1-40); have=$(shasum /tmp/vt_lib | cut -c1-40)
  [ "$want" = "$have" ] || { echo "Checksum passer ikke: $1"; exit 1; }
  if [ "$3" = aar ]; then unzip -p /tmp/vt_lib classes.jar > "jars/$2"; else mv /tmp/vt_lib "jars/$2"; fi
}
for a in common exoplayer exoplayer-hls datasource decoder extractor container database; do
  get $G/androidx/media3/media3-$a/$V/media3-$a-$V.aar media3-$a.jar aar
done
get $G/androidx/annotation/annotation-experimental/1.3.1/annotation-experimental-1.3.1.aar annotation-experimental.jar aar
get $G/androidx/annotation/annotation/1.3.0/annotation-1.3.0.jar annotation.jar
get $G/androidx/collection/collection/1.2.0/collection-1.2.0.jar collection.jar
get $C/com/google/guava/guava/33.0.0-android/guava-33.0.0-android.jar guava.jar
get $C/com/google/guava/failureaccess/1.0.2/failureaccess-1.0.2.jar failureaccess.jar
get $C/com/google/zxing/core/3.5.3/core-3.5.3.jar zxing-core.jar
rm -f /tmp/vt_lib
echo "Biblioteker hentet til jars/"
