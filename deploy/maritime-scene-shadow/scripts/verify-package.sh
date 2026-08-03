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
jq -e '.snapshotFingerprint == "35cbb1a09f87ea50fec2c5db320f64bd9e91e36150b64c354da99eb54bf5a437"' scene-release/phase1-smoke-shanghai-autocross-v1/manifest.json >/dev/null
test "$(find scene-release/phase1-smoke-shanghai-autocross-v1/tiles -name '*.ndjson.gz' | wc -l | tr -d ' ')" = "17"
echo "PASS: package checksums, Release status, fingerprint and 17 runtime tiles"
