#!/bin/bash
set -euo pipefail

BASE_DIR="$(cd "$(dirname "$0")/.." && pwd)"
RELEASE_NAME="phase1-global-base-v1"
DEFAULT_RELEASE="$BASE_DIR/var/maritime-scene-data/releases/$RELEASE_NAME"
SIBLING_RELEASE="$(dirname "$BASE_DIR")/ship-risk-data/var/maritime-scene-data/releases/$RELEASE_NAME"
RELEASE_DIR="${SCENE_RELEASE_SOURCE:-$DEFAULT_RELEASE}"
if test ! -d "$RELEASE_DIR" && test -d "$SIBLING_RELEASE"; then RELEASE_DIR="$SIBLING_RELEASE"; fi
ARCHIVE="$BASE_DIR/target/$RELEASE_NAME-runtime.tar.gz"

test -f "$RELEASE_DIR/manifest.json"
test "$(jq -r '.validationSummary.releaseStatus' "$RELEASE_DIR/manifest.json")" = "SHADOW_READY"
for required in manifest.json checksums.sha256 tiles source-manifests validation validation-report.json license-attribution.md; do
  test -e "$RELEASE_DIR/$required"
done
(
  cd "$RELEASE_DIR"
  shasum -a 256 -c checksums.sha256 >/dev/null
)
if test -f "$ARCHIVE" && test -f "$ARCHIVE.sha256" && (cd "$(dirname "$ARCHIVE")" && shasum -a 256 -c "$(basename "$ARCHIVE").sha256" >/dev/null); then
  echo "REUSED: $ARCHIVE"
  exit 0
fi
tar -C "$(dirname "$RELEASE_DIR")" -czf "$ARCHIVE" \
  "$RELEASE_NAME/manifest.json" "$RELEASE_NAME/checksums.sha256" \
  "$RELEASE_NAME/tiles" "$RELEASE_NAME/source-manifests" "$RELEASE_NAME/validation" \
  "$RELEASE_NAME/validation-report.json" "$RELEASE_NAME/license-attribution.md"
shasum -a 256 "$ARCHIVE" > "$ARCHIVE.sha256"
if tar -tzf "$ARCHIVE" | grep -E '/(qa|staging|raw|\.venv)(/|$)' >/dev/null; then
  echo "runtime archive contains forbidden path" >&2
  exit 1
fi
echo "$ARCHIVE"
cat "$ARCHIVE.sha256"
