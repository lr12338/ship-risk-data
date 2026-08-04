#!/bin/bash
set -euo pipefail

BASE_DIR="$(cd "$(dirname "$0")/.." && pwd)"
TEMPLATE_DIR="$BASE_DIR/deploy/region-scene-rule-shadow-v2-app-deploy"
OUTPUT_DIR="$BASE_DIR/target/region-scene-rule-shadow-v2-app-deploy"
JAR_NAME="ship-risk-data-0.0.1-SNAPSHOT.jar"
JAR_PATH="$BASE_DIR/target/$JAR_NAME"
FINGERPRINT="5c35fd542d492c5857e546a635f5a71790f2bf321b1590b84eca39c2052dec6e"

test -f "$JAR_PATH"
rm -rf "$OUTPUT_DIR"
mkdir -p "$OUTPUT_DIR/app"
cp -R "$TEMPLATE_DIR/." "$OUTPUT_DIR/"
cp "$JAR_PATH" "$OUTPUT_DIR/app/"

JAR_SHA="$(shasum -a 256 "$JAR_PATH" | awk '{print $1}')"
JAR_SIZE="$(stat -f %z "$JAR_PATH" 2>/dev/null || stat -c %s "$JAR_PATH")"
GIT_HEAD="$(git -C "$BASE_DIR" rev-parse HEAD)"
GIT_BRANCH="$(git -C "$BASE_DIR" branch --show-current)"
BUILD_TIME="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
jq -n --arg gitHead "$GIT_HEAD" --arg gitBranch "$GIT_BRANCH" \
  --arg buildTime "$BUILD_TIME" --arg jarName "$JAR_NAME" \
  --argjson jarSize "$JAR_SIZE" --arg jarSha256 "$JAR_SHA" \
  --arg sceneFingerprint "$FINGERPRINT" \
  '{gitHead:$gitHead,gitBranch:$gitBranch,buildTime:$buildTime,
    jarName:$jarName,jarSize:$jarSize,jarSha256:$jarSha256,
    sceneReleaseName:"phase1-global-base-v1",sceneFingerprint:$sceneFingerprint,
    sceneTiles:30645,coastTiles:5893,totalTiles:36538,
    scenePolicyVersion:"MARITIME_SCENE_SHADOW_V2",
    alongsideBehaviorRuleVersion:"ALONGSIDE_SIDE_GEOMETRY_SHADOW_V1",
    alongsideCombinedRuleVersion:"ALONGSIDE_V3_SCENE_GEOMETRY_SHADOW_V1",
    loiteringBehaviorRuleVersion:"LOITERING_SHAPE_SHADOW_V1",
    loiteringCombinedRuleVersion:"LOITERING_V2_SCENE_SHAPE_SHADOW_V1",
    databaseWrites:0,formalEventMutation:false,enforceEnabled:false,
    shadowAuditRunnerImplemented:false}' > "$OUTPUT_DIR/manifest.json"
(
  cd "$OUTPUT_DIR"
  find . -type f ! -name checksums.sha256 -print | LC_ALL=C sort | while IFS= read -r file; do
    shasum -a 256 "$file"
  done > checksums.sha256
)

STAMP="$(date +%Y%m%d-%H%M%S)"
ARCHIVE="$BASE_DIR/target/region-scene-rule-shadow-v2-app-deploy-$STAMP.tar.gz"
tar -C "$BASE_DIR/target" -czf "$ARCHIVE" region-scene-rule-shadow-v2-app-deploy
shasum -a 256 "$ARCHIVE" > "$ARCHIVE.sha256"
echo "$OUTPUT_DIR"
echo "$ARCHIVE"
cat "$ARCHIVE.sha256"
