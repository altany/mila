#!/usr/bin/env bash
# Release in one step: bump version, commit, tag, push.
# CI then builds the signed APK and attaches it to the GitHub Release.
#
# Usage:
#   scripts/release.sh          # bump patch (0.1.0 -> 0.1.1)
#   scripts/release.sh 0.2.0    # set explicit version
set -euo pipefail
cd "$(dirname "$0")/.."

if [[ -n "$(git status --porcelain)" ]]; then
  echo "Working tree not clean — commit or stash first." >&2
  exit 1
fi

CURRENT=$(grep '^VERSION_NAME=' version.properties | cut -d= -f2)
CODE=$(grep '^VERSION_CODE=' version.properties | cut -d= -f2)

if [[ $# -ge 1 ]]; then
  NEXT=$1
else
  IFS=. read -r MAJ MIN PAT <<< "$CURRENT"
  NEXT="$MAJ.$MIN.$((PAT + 1))"
fi
NEXT_CODE=$((CODE + 1))

printf 'VERSION_NAME=%s\nVERSION_CODE=%s\n' "$NEXT" "$NEXT_CODE" > version.properties

git add version.properties
git commit -m "Release v$NEXT"
git tag "v$NEXT"
git push origin main "v$NEXT"

echo
echo "Pushed v$NEXT — CI is building the signed APK."
echo "It will appear at: https://github.com/$(gh repo view --json nameWithOwner -q .nameWithOwner)/releases/tag/v$NEXT"
