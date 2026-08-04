#!/bin/bash
set -euo pipefail

BASE_DIR="$(cd "$(dirname "$0")/.." && pwd)"
TEMPLATE_DIR="$BASE_DIR/deploy/region-scene-integration-app-deploy"
OUTPUT_DIR="$BASE_DIR/target/region-scene-integration-app-deploy"
JAR_NAME="ship-risk-data-0.0.1-SNAPSHOT.jar"
JAR_PATH="$BASE_DIR/target/$JAR_NAME"
RELEASE_NAME="phase1-global-base-v1"
DEFAULT_RELEASE="$BASE_DIR/var/maritime-scene-data/releases/$RELEASE_NAME"
SIBLING_RELEASE="$(dirname "$BASE_DIR")/ship-risk-data/var/maritime-scene-data/releases/$RELEASE_NAME"
RELEASE_DIR="${SCENE_RELEASE_SOURCE:-$DEFAULT_RELEASE}"
if test ! -d "$RELEASE_DIR" && test -d "$SIBLING_RELEASE"; then RELEASE_DIR="$SIBLING_RELEASE"; fi

test -f "$JAR_PATH"
test -f "$RELEASE_DIR/manifest.json"
test "$(jq -r '.validationSummary.releaseStatus' "$RELEASE_DIR/manifest.json")" = "SHADOW_READY"
FINGERPRINT="$(jq -r '.snapshotFingerprint' "$RELEASE_DIR/manifest.json")"
TILE_COUNT="$(find "$RELEASE_DIR/tiles" -type f -name '*.ndjson.gz' | wc -l | tr -d ' ')"

rm -rf "$OUTPUT_DIR"
mkdir -p "$OUTPUT_DIR/app"
cp -R "$TEMPLATE_DIR/." "$OUTPUT_DIR/"
cp "$JAR_PATH" "$OUTPUT_DIR/app/"
chmod +x "$OUTPUT_DIR/scripts/"*.sh
sed -i.bak "s#<SCENE_FINGERPRINT>#$FINGERPRINT#g; s#<SCENE_TILE_COUNT>#$TILE_COUNT#g" \
  "$OUTPUT_DIR/config/"* "$OUTPUT_DIR/jobs/"* "$OUTPUT_DIR/scripts/"* "$OUTPUT_DIR/docs/"*
find "$OUTPUT_DIR" -name '*.bak' -delete

JAR_SHA="$(shasum -a 256 "$JAR_PATH" | awk '{print $1}')"
JAR_SIZE="$(stat -f %z "$JAR_PATH" 2>/dev/null || stat -c %s "$JAR_PATH")"
GIT_HEAD="$(git -C "$BASE_DIR" rev-parse HEAD)"
jq -n --arg builtAt "$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
  --arg gitHead "$GIT_HEAD" --arg jar "$JAR_NAME" --arg jarSha "$JAR_SHA" \
  --argjson jarSize "$JAR_SIZE" --arg release "$RELEASE_NAME" \
  --arg fingerprint "$FINGERPRINT" --argjson tileCount "$TILE_COUNT" \
  '{schemaVersion:1,builtAt:$builtAt,gitHead:$gitHead,
    jar:{name:$jar,sizeBytes:$jarSize,sha256:$jarSha},
    sceneRelease:{name:$release,status:"SHADOW_READY",fingerprint:$fingerprint,tileFileCount:$tileCount},
    safety:{sceneMode:"SHADOW",regionDryRunOnly:true,databaseWrites:0,formalEventMutation:false,
      shadowAuditRunnerImplemented:false,forbiddenHandler:"shipMaritimeSceneShadowAuditJobHandler"}}' \
  > "$OUTPUT_DIR/manifest.json"
(
  cd "$OUTPUT_DIR"
  find . -type f ! -name checksums.sha256 -print | LC_ALL=C sort | while IFS= read -r file; do
    shasum -a 256 "$file"
  done > checksums.sha256
)

STAMP="$(date +%Y%m%d-%H%M%S)"
ARCHIVE="$BASE_DIR/target/region-scene-integration-app-deploy-$STAMP.tar.gz"
tar -C "$BASE_DIR/target" -czf "$ARCHIVE" region-scene-integration-app-deploy
shasum -a 256 "$ARCHIVE" > "$ARCHIVE.sha256"
echo "$OUTPUT_DIR"
echo "$ARCHIVE"
cat "$ARCHIVE.sha256"
