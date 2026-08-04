#!/bin/sh
set -eu
PACKAGE_ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$PACKAGE_ROOT"
if command -v sha256sum >/dev/null 2>&1; then
  sha256sum -c checksums.sha256
else
  shasum -a 256 -c checksums.sha256
fi
jq -e '.releaseStatus == "SHADOW_READY"' scene-release/phase1-smoke-shanghai-autocross-v1/validation-report.json >/dev/null
jq -e '.snapshotFingerprint == "<manifest.snapshotFingerprint>"' scene-release/phase1-smoke-shanghai-autocross-v1/manifest.json >/dev/null
test "$(find scene-release/phase1-smoke-shanghai-autocross-v1/tiles -name '*.ndjson.gz' | wc -l | tr -d ' ')" = "<manifest.tileFileCount>"
echo "PASS: package checksums, Release status, fingerprint and <manifest.tileFileCount> runtime tiles"
