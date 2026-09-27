#!/usr/bin/env bash
#
# Updates the plugin version snippet in README.md to the released version.
#
# Usage: update-readme.sh <readme-file> <version>
#
set -euo pipefail

FILE="${1:-README.md}"
VERSION="${2:?Usage: update-readme.sh <readme-file> <version>}"
VERSION="${VERSION#v}"

if [ ! -f "$FILE" ]; then
  echo "::error::File '$FILE' not found."
  exit 1
fi

sed -i -E "s/(id\(\"io\.github\.nndwn\.changelog-publish\"\)\s+version\s+\")[^\"]+(\")/\1${VERSION}\2/g" "$FILE"

echo "Updated plugin version in ${FILE} to ${VERSION}."
