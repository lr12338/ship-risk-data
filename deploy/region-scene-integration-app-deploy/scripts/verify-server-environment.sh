#!/bin/sh
set -eu
APP_HOME=${APP_HOME:-/data/zhengyi}
SCENE_HOME=${SCENE_HOME:-/data/zhengyi/maritime-scene}
RELEASE="$SCENE_HOME/releases/phase1-global-base-v1"
for command_name in java jq tar sha256sum; do command -v "$command_name" >/dev/null; done
test -d "$APP_HOME"
test -f "$APP_HOME/application.properties"
test -x "$APP_HOME/start.sh"
test -x "$APP_HOME/stop.sh"
df -h "$APP_HOME" "$SCENE_HOME" 2>/dev/null || df -h "$APP_HOME"
java -version
if test -d "$RELEASE"; then
  test "$(jq -r '.validationSummary.releaseStatus' "$RELEASE/manifest.json")" = "SHADOW_READY"
  test "$(jq -r '.snapshotFingerprint' "$RELEASE/manifest.json")" = "<SCENE_FINGERPRINT>"
  test "$(find "$RELEASE/tiles" -type f -name '*.ndjson.gz' | wc -l | tr -d ' ')" = "<SCENE_TILE_COUNT>"
fi
echo "PASS: server prerequisites; no SQL or application task executed"
