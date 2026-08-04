#!/bin/bash
set -euo pipefail

BASE_DIR="$(cd "$(dirname "$0")/.." && pwd)"
OUTPUT_DIR="$BASE_DIR/target/maritime-scene-global-base-preflight-deploy"
TEMPLATE_DIR="$BASE_DIR/deploy/maritime-scene-shadow"
RELEASE_NAME="phase1-global-base-v1"
RELEASE_DIR="$BASE_DIR/var/maritime-scene-data/releases/$RELEASE_NAME"
JAR_NAME="ship-risk-data-0.0.1-SNAPSHOT.jar"
JAR_PATH="$BASE_DIR/target/$JAR_NAME"

test -f "$JAR_PATH"
test -f "$RELEASE_DIR/manifest.json"
test "$(jq -r '.validationSummary.releaseStatus' "$RELEASE_DIR/manifest.json")" = "SHADOW_READY"
FINGERPRINT="$(jq -r '.snapshotFingerprint' "$RELEASE_DIR/manifest.json")"
test "${#FINGERPRINT}" = 64

rm -rf "$OUTPUT_DIR"
mkdir -p "$OUTPUT_DIR/app" "$OUTPUT_DIR/scene-release/$RELEASE_NAME" "$OUTPUT_DIR/qa" "$OUTPUT_DIR/jobs"
cp "$JAR_PATH" "$OUTPUT_DIR/app/"
for name in tiles source-manifests validation; do
  cp -R "$RELEASE_DIR/$name" "$OUTPUT_DIR/scene-release/$RELEASE_NAME/"
done
for name in manifest.json checksums.sha256 validation-report.json license-attribution.md; do
  cp "$RELEASE_DIR/$name" "$OUTPUT_DIR/scene-release/$RELEASE_NAME/"
done
cp -R "$TEMPLATE_DIR/config" "$TEMPLATE_DIR/scripts" "$OUTPUT_DIR/"
cp "$TEMPLATE_DIR/jobs/preflight-params.json" "$OUTPUT_DIR/jobs/"
cp "$TEMPLATE_DIR/DEPLOY-MANUAL.md" "$TEMPLATE_DIR/VALIDATION-CHECKLIST.md" "$OUTPUT_DIR/"
cp -R "$RELEASE_DIR/qa/scene-atlas" "$OUTPUT_DIR/qa/"
for name in global-base-review.gpkg review-points.csv QGIS-REVIEW.md; do
  cp "$RELEASE_DIR/qa/$name" "$OUTPUT_DIR/qa/"
done
chmod +x "$OUTPUT_DIR/scripts/"*.sh

sed -i.bak "s#phase1-smoke-shanghai-autocross-v1#$RELEASE_NAME#g; s#<manifest.snapshotFingerprint>#$FINGERPRINT#g" \
  "$OUTPUT_DIR/config/"* "$OUTPUT_DIR/jobs/preflight-params.json"
find "$OUTPUT_DIR" -name '*.bak' -delete

JAR_SHA="$(shasum -a 256 "$JAR_PATH" | awk '{print $1}')"
JAR_SIZE="$(stat -f %z "$JAR_PATH" 2>/dev/null || stat -c %s "$JAR_PATH")"
RELEASE_SIZE="$(du -sk "$RELEASE_DIR" | awk '{print $1 * 1024}')"
GIT_HEAD="$(git -C "$BASE_DIR" rev-parse HEAD)"
TILE_COUNT="$(find "$RELEASE_DIR/tiles" -name '*.ndjson.gz' | wc -l | tr -d ' ')"

jq -n \
  --arg builtAt "$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
  --arg gitHead "$GIT_HEAD" --arg jar "$JAR_NAME" --arg jarSha256 "$JAR_SHA" \
  --argjson jarSize "$JAR_SIZE" --arg release "$RELEASE_NAME" \
  --arg fingerprint "$FINGERPRINT" --argjson releaseSize "$RELEASE_SIZE" \
  --argjson tileCount "$TILE_COUNT" \
  '{schemaVersion:1,builtAt:$builtAt,gitHead:$gitHead,
    jar:{name:$jar,sizeBytes:$jarSize,sha256:$jarSha256},
    sceneRelease:{name:$release,status:"SHADOW_READY",fingerprint:$fingerprint,
      sizeBytes:$releaseSize,tileFileCount:$tileCount,scope:"GLOBAL_VECTOR_BASE",jrcCoverage:"NONE"},
    safety:{mode:"SHADOW",enforceImplemented:false,formalEventMutation:false,
      auditRunnerImplemented:false,onlyPreflightAllowed:true}}' > "$OUTPUT_DIR/manifest.json"

(
  cd "$OUTPUT_DIR"
  find . -type f ! -name checksums.sha256 -print | LC_ALL=C sort | while IFS= read -r file; do
    shasum -a 256 "$file"
  done > checksums.sha256
)

ARCHIVE="$BASE_DIR/target/maritime-scene-global-base-preflight-deploy.tar.gz"
tar -C "$BASE_DIR/target" -czf "$ARCHIVE" maritime-scene-global-base-preflight-deploy
shasum -a 256 "$ARCHIVE" > "$ARCHIVE.sha256"
echo "$OUTPUT_DIR"
echo "$ARCHIVE"
cat "$ARCHIVE.sha256"
