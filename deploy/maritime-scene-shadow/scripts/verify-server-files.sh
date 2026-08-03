#!/bin/sh
set -eu
APP_HOME=${APP_HOME:-/data/zhengyi}
SCENE_HOME=${SCENE_HOME:-/data/zhengyi/maritime-scene}
JAR="$APP_HOME/ship-risk-data-0.0.1-SNAPSHOT.jar"
RELEASE="$SCENE_HOME/releases/phase1-smoke-shanghai-autocross-v1"
test -r "$JAR"
test -r "$RELEASE/manifest.json"
jq -e '.snapshotFingerprint == "35cbb1a09f87ea50fec2c5db320f64bd9e91e36150b64c354da99eb54bf5a437"' "$RELEASE/manifest.json" >/dev/null
test "$(find "$RELEASE/tiles" -name '*.ndjson.gz' | wc -l | tr -d ' ')" = "17"
echo "PASS: server JAR and Shanghai Release files are readable"
