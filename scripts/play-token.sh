#!/usr/bin/env bash
# Exchange a Google service account key for a short-lived access token.
#
# This is the standard OAuth2 JWT-bearer flow: sign a claim with the key and
# swap it for a token. Doing it directly avoids the IAM Credentials API and the
# self-impersonation role that the generic auth action needs.
#
# Usage: PLAY_SERVICE_ACCOUNT_JSON="$(cat key.json)" scripts/play-token.sh
set -euo pipefail

: "${PLAY_SERVICE_ACCOUNT_JSON:?PLAY_SERVICE_ACCOUNT_JSON is not set}"

SCOPE="https://www.googleapis.com/auth/androidpublisher"
AUD="https://oauth2.googleapis.com/token"

client_email=$(echo "$PLAY_SERVICE_ACCOUNT_JSON" | jq -r .client_email)
private_key=$(echo "$PLAY_SERVICE_ACCOUNT_JSON" | jq -r .private_key)

b64url() { openssl base64 -A | tr '+/' '-_' | tr -d '='; }

now=$(date +%s)
header=$(printf '{"alg":"RS256","typ":"JWT"}' | b64url)
claims=$(jq -nc --arg iss "$client_email" --arg scope "$SCOPE" --arg aud "$AUD" \
  --argjson iat "$now" --argjson exp "$((now + 3600))" \
  '{iss:$iss, scope:$scope, aud:$aud, iat:$iat, exp:$exp}' | b64url)

keyfile=$(mktemp); trap 'rm -f "$keyfile"' EXIT
printf '%s' "$private_key" > "$keyfile"
signature=$(printf '%s.%s' "$header" "$claims" \
  | openssl dgst -sha256 -sign "$keyfile" -binary | b64url)

response=$(curl -sS -X POST "$AUD" \
  -d grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer \
  --data-urlencode "assertion=$header.$claims.$signature")

if ! echo "$response" | jq -e .access_token >/dev/null 2>&1; then
  echo "Could not get an access token:" >&2
  echo "$response" | jq . >&2
  exit 1
fi
echo "$response" | jq -r .access_token
