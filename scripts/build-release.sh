#!/usr/bin/env bash
# Build a signed release APK locally. Generates the keystore first if needed.
set -euo pipefail
cd "$(dirname "$0")/.."

if [[ ! -f keystore.properties ]]; then
  ./scripts/make-keystore.sh
fi

./gradlew assembleRelease

APK=app/build/outputs/apk/release/app-release.apk
echo
echo "Signed release APK: $(pwd)/$APK"
