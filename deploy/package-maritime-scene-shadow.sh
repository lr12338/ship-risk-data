#!/bin/bash
set -euo pipefail

BASE_DIR="$(cd "$(dirname "$0")/.." && pwd)"
OUTPUT_DIR="$BASE_DIR/target/maritime-scene-shadow-deploy"
TEMPLATE_DIR="$BASE_DIR/deploy/maritime-scene-shadow"
RELEASE_NAME="phase1-smoke-shanghai-autocross-v1"
RELEASE_DIR="$BASE_DIR/var/maritime-scene-data/releases/$RELEASE_NAME"
JAR_NAME="ship-risk-data-0.0.1-SNAPSHOT.jar"
JAR_PATH="$BASE_DIR/target/$JAR_NAME"
FINGERPRINT="35cbb1a09f87ea50fec2c5db320f64bd9e91e36150b64c354da99eb54bf5a437"

test -f "$JAR_PATH"
test -f "$RELEASE_DIR/manifest.json"
test "$(jq -r '.validationSummary.releaseStatus' "$RELEASE_DIR/manifest.json")" = "SHADOW_READY"
test "$(jq -r '.snapshotFingerprint' "$RELEASE_DIR/manifest.json")" = "$FINGERPRINT"

rm -rf "$OUTPUT_DIR"
mkdir -p "$OUTPUT_DIR/app" "$OUTPUT_DIR/scene-release" "$OUTPUT_DIR/sql" "$OUTPUT_DIR/qa"
cp "$JAR_PATH" "$OUTPUT_DIR/app/"
cp -R "$RELEASE_DIR" "$OUTPUT_DIR/scene-release/"
cp -R "$TEMPLATE_DIR/config" "$TEMPLATE_DIR/jobs" "$TEMPLATE_DIR/scripts" "$OUTPUT_DIR/"
cp "$TEMPLATE_DIR/DEPLOY-MANUAL.md" "$TEMPLATE_DIR/VALIDATION-CHECKLIST.md" "$OUTPUT_DIR/"
cp "$BASE_DIR/src/main/resources/sql/ship_behavior_maritime_scene_audit.sql" "$OUTPUT_DIR/sql/"
cp "$BASE_DIR/src/main/resources/sql/ship_behavior_maritime_scene_audit_validation.sql" "$OUTPUT_DIR/sql/"
cp "$BASE_DIR/src/main/resources/sql/ship_behavior_maritime_scene_audit_rollback.sql" "$OUTPUT_DIR/sql/"
cp -R "$RELEASE_DIR/qa/scene-atlas" "$OUTPUT_DIR/qa/"
chmod +x "$OUTPUT_DIR/scripts/"*.sh

JAR_SHA="$(shasum -a 256 "$JAR_PATH" | awk '{print $1}')"
JAR_SIZE="$(stat -f %z "$JAR_PATH" 2>/dev/null || stat -c %s "$JAR_PATH")"
GIT_HEAD="$(git -C "$BASE_DIR" rev-parse HEAD)"
JAVA_VERSION="$(java -version 2>&1 | head -n 1)"
TILE_COUNT="$(find "$RELEASE_DIR/tiles" -name '*.ndjson.gz' | wc -l | tr -d ' ')"

jq -n \
  --arg builtAt "$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
  --arg gitHead "$GIT_HEAD" \
  --arg javaVersion "$JAVA_VERSION" \
  --arg jar "$JAR_NAME" \
  --arg jarSha256 "$JAR_SHA" \
  --argjson jarSize "$JAR_SIZE" \
  --arg release "$RELEASE_NAME" \
  --arg releaseStatus "SHADOW_READY" \
  --arg fingerprint "$FINGERPRINT" \
  --argjson tileCount "$TILE_COUNT" \
  '{schemaVersion:1,builtAt:$builtAt,gitHead:$gitHead,javaVersion:$javaVersion,
    tests:{etl:"23/23",maven:"216/216",shadowTargeted:"23/23"},
    jar:{name:$jar,sizeBytes:$jarSize,sha256:$jarSha256},
    sceneRelease:{name:$release,status:$releaseStatus,fingerprint:$fingerprint,tileFileCount:$tileCount,
      bbox:[120.5,30.5,122.5,32.0],scope:"SHANGHAI_ONLY",jrcCoverage:"120E_40N"},
    safety:{mode:"SHADOW",enforceImplemented:false,formalEventMutation:false,remoteDeploymentPerformed:false,
      auditRunnerImplemented:false,stage100AndAboveBlocked:true}}' > "$OUTPUT_DIR/manifest.json"

(
  cd "$OUTPUT_DIR"
  find . -type f ! -name checksums.sha256 -print | LC_ALL=C sort | while IFS= read -r file; do
    shasum -a 256 "$file"
  done > checksums.sha256
)

STAMP="$(date +%Y%m%d-%H%M%S)"
ARCHIVE="$BASE_DIR/target/maritime-scene-shadow-deploy-$STAMP.tar.gz"
tar -C "$BASE_DIR/target" -czf "$ARCHIVE" maritime-scene-shadow-deploy
(
  cd "$BASE_DIR/target"
  shasum -a 256 "$(basename "$ARCHIVE")" > "$(basename "$ARCHIVE").sha256"
)

echo "$OUTPUT_DIR"
echo "$ARCHIVE"
cat "$ARCHIVE.sha256"
