#!/usr/bin/env bash
# One-time: generate the release signing keystore + keystore.properties.
# Both are gitignored. Re-running refuses to overwrite an existing keystore,
# because replacing the key would break install-over-update on the phone.
set -euo pipefail
cd "$(dirname "$0")/.."

KEYSTORE=keystore/mila.jks
ALIAS=mila

if [[ -f "$KEYSTORE" ]]; then
  echo "Keystore already exists at $KEYSTORE — refusing to overwrite."
  echo "Delete it manually only if you really want a new signing identity."
  exit 1
fi

mkdir -p keystore
STORE_PASS=$(openssl rand -base64 24 | tr -d '/+=')
KEY_PASS=$STORE_PASS

keytool -genkeypair \
  -keystore "$KEYSTORE" \
  -alias "$ALIAS" \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -storepass "$STORE_PASS" -keypass "$KEY_PASS" \
  -dname "CN=Mila, OU=Personal, O=Personal, L=Athens, C=GR"

cat > keystore.properties <<EOF
storeFile=$KEYSTORE
storePassword=$STORE_PASS
keyAlias=$ALIAS
keyPassword=$KEY_PASS
EOF

echo
echo "Keystore created: $KEYSTORE"
echo "Credentials written to keystore.properties (gitignored)."
echo
echo "To give CI the same key, set these GitHub Actions secrets:"
echo "  MILA_KEYSTORE_BASE64   = base64 of $KEYSTORE"
echo "  MILA_KEYSTORE_PASSWORD = the storePassword"
echo "  MILA_KEY_ALIAS         = $ALIAS"
echo "  MILA_KEY_PASSWORD      = the keyPassword"
echo "e.g.:  base64 -i $KEYSTORE | gh secret set MILA_KEYSTORE_BASE64"
