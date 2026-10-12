#!/bin/sh
# Test JVM dell'app Android, compreso l'upload verso lo stesso server Python dell'app iOS.
set -e
cd "$(dirname "$0")"
# Gradle 8.12 non gira sul JDK 25 di sistema: si usa quello di Android Studio
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"

DATA=$(mktemp -d)
CALSNAP_DIR=$DATA CALSNAP_PORT=8767 CALSNAP_UPLOAD_KEY=test-upload CALSNAP_GUEST_TOKEN=test-guest \
  python3 ../CalSnap/server/calsnap_server.py 2>/dev/null &
SERVER=$!
trap 'kill $SERVER; wait $SERVER 2>/dev/null; rm -rf "$DATA"' EXIT
sleep 1

MORSIO_SERVER=http://localhost:8767 MORSIO_KEY=test-upload MORSIO_GUEST=test-guest \
  ./gradlew --quiet testDebugUnitTest "$@"
echo "Test eseguiti senza errori: $(grep -ho 'tests="[0-9]*"' app/build/test-results/testDebugUnitTest/*.xml | grep -o '[0-9]*') (saltati: $(grep -ho 'skipped="[0-9]*"' app/build/test-results/testDebugUnitTest/*.xml | grep -o '[0-9]*'))"
