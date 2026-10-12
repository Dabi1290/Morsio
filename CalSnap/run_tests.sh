#!/bin/sh
# Test completi in locale: server Python + test app sul simulatore.
# Gemini viene testato solo se esporti GEMINI_KEY=... (opzionale FOOD_PHOTO=/percorso/foto.jpg)
set -e
cd "$(dirname "$0")"
python3 server/test_server.py 2>/dev/null

DATA=$(mktemp -d)
CALSNAP_DIR=$DATA CALSNAP_PORT=8766 CALSNAP_UPLOAD_KEY=test-upload CALSNAP_GUEST_TOKEN=test-guest \
  python3 server/calsnap_server.py 2>/dev/null &
SERVER=$!
trap 'kill $SERVER; rm -rf "$DATA"' EXIT

xcodegen generate --quiet
TEST_RUNNER_GEMINI_KEY="$GEMINI_KEY" TEST_RUNNER_FOOD_PHOTO="$FOOD_PHOTO" xcodebuild test -project CalSnap.xcodeproj -scheme CalSnap \
  -destination "platform=iOS Simulator,${SIM:-name=iPhone 17 Pro}" \
  CODE_SIGN_IDENTITY=- DEVELOPMENT_TEAM= 2>&1 | grep -E "Test Case|error:|JPEG|Gemini:|TEST (SUCCEEDED|FAILED)|skipped"
