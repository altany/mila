#!/usr/bin/env bash
# Publish an app bundle to Google Play's internal testing track.
#
# Expects an OAuth access token in $PLAY_ACCESS_TOKEN — in CI that comes from
# Google's own auth action, so no third-party code ever handles the service
# account credential. Publishing itself is four plain REST calls.
#
# Usage: scripts/publish-play.sh <bundle.aab> "<release notes>"
set -euo pipefail

BUNDLE=${1:?usage: publish-play.sh <bundle.aab> "<release notes>"}
NOTES=${2:-}
PACKAGE=dev.altany.mila
TRACK=internal
API="https://androidpublisher.googleapis.com/androidpublisher/v3/applications/$PACKAGE"
UPLOAD="https://androidpublisher.googleapis.com/upload/androidpublisher/v3/applications/$PACKAGE"

: "${PLAY_ACCESS_TOKEN:?PLAY_ACCESS_TOKEN is not set}"
[[ -f "$BUNDLE" ]] || { echo "no such bundle: $BUNDLE" >&2; exit 1; }

auth=(-H "Authorization: Bearer $PLAY_ACCESS_TOKEN")

# Fail loudly on an API error rather than carrying a null id forward.
check() {
  if echo "$1" | jq -e '.error' >/dev/null 2>&1; then
    echo "Play API error during $2:" >&2
    echo "$1" | jq '.error | {code, message}' >&2
    exit 1
  fi
}

# The Play API returns a 503 often enough that a single attempt is not a fair
# test of whether a release worked. Retry those; anything else is a real error
# and should surface immediately.
retry() {
  local what=$1; shift
  local attempt=1 max=4 response code
  while :; do
    response=$("$@")
    code=$(echo "$response" | jq -r '.error.code // empty')
    if [[ -z "$code" || $code -lt 500 || $attempt -ge $max ]]; then
      echo "$response"
      return 0
    fi
    echo "  $what: HTTP $code, retrying ($attempt/$((max - 1)))..." >&2
    sleep $((attempt * 5))
    attempt=$((attempt + 1))
  done
}

echo "Opening an edit..."
edit=$(retry "opening the edit" curl -sS -X POST "${auth[@]}" -H "Content-Length: 0" "$API/edits")
check "$edit" "creating the edit"
EDIT_ID=$(echo "$edit" | jq -r .id)
echo "  edit $EDIT_ID"

echo "Uploading $(basename "$BUNDLE")..."
uploaded=$(retry "uploading" curl -sS -X POST "${auth[@]}" \
  -H "Content-Type: application/octet-stream" \
  --data-binary @"$BUNDLE" \
  "$UPLOAD/edits/$EDIT_ID/bundles?uploadType=media")
check "$uploaded" "uploading the bundle"
VERSION_CODE=$(echo "$uploaded" | jq -r .versionCode)
echo "  version code $VERSION_CODE"

echo "Pointing the $TRACK track at it..."
release=$(jq -n --argjson vc "$VERSION_CODE" --arg notes "$NOTES" '{
  track: "'"$TRACK"'",
  releases: [{
    versionCodes: [$vc | tostring],
    status: "completed",
    releaseNotes: (if $notes == "" then [] else [{language: "en-GB", text: $notes}] end)
  }]
}')
tracked=$(retry "assigning the track" curl -sS -X PUT "${auth[@]}" \
  -H "Content-Type: application/json" \
  -d "$release" \
  "$API/edits/$EDIT_ID/tracks/$TRACK")
check "$tracked" "assigning the track"

echo "Committing..."
committed=$(retry "committing" curl -sS -X POST "${auth[@]}" -H "Content-Length: 0" \
  "$API/edits/$EDIT_ID:commit")
check "$committed" "committing the edit"

echo "Published version code $VERSION_CODE to the $TRACK track."
