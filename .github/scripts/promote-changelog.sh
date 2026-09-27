#!/usr/bin/env bash
#
# Promotes the first "## [Unreleased]" section of a CHANGELOG.md into a released
# version header, mirroring the behaviour of the plugin's `releaseChangelog` task.
#
# Usage: promote-changelog.sh <changelog-file> <version> [date]
#
set -euo pipefail

FILE="${1:-CHANGELOG.md}"
VERSION="${2:?Usage: promote-changelog.sh <changelog-file> <version> [date]}"
VERSION="${VERSION#v}"
DATE="${3:-$(date +%Y-%m-%d)}"

if [ ! -f "$FILE" ]; then
  echo "::error::File '$FILE' not found."
  exit 1
fi

if ! grep -qE '^## \[Unreleased\]' "$FILE"; then
  echo "::error::Section '## [Unreleased]' was not found in $FILE."
  exit 1
fi

NOTES="$(awk '/^## \[Unreleased\]/{f=1;next} f&&/^## /{exit} f' "$FILE")"
if [ -z "$(printf '%s' "$NOTES" | tr -d '[:space:]')" ]; then
  echo "::error::Section '## [Unreleased]' in $FILE is empty. Add release notes before releasing."
  exit 1
fi

if grep -qE "^## \[v?${VERSION}\]" "$FILE"; then
  echo "::error::Version '$VERSION' is already present in $FILE. Bump the version first."
  exit 1
fi

awk -v ver="$VERSION" -v date="$DATE" '
  { print }
  /^## \[Unreleased\]/ && !done {
    print ""
    print "## [" ver "] - " date
    done = 1
  }
' "$FILE" > "${FILE}.tmp"
mv "${FILE}.tmp" "$FILE"

echo "Promoted '## [Unreleased]' to '## [${VERSION}] - ${DATE}' in ${FILE}."
