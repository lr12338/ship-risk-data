#!/bin/sh
set -eu
PACKAGE_ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$PACKAGE_ROOT"
if command -v sha256sum >/dev/null 2>&1; then
  sha256sum -c checksums.sha256
  JAR_SHA=$(sha256sum app/ship-risk-data-0.0.1-SNAPSHOT.jar | awk '{print $1}')
else
  shasum -a 256 -c checksums.sha256
  JAR_SHA=$(shasum -a 256 app/ship-risk-data-0.0.1-SNAPSHOT.jar | awk '{print $1}')
fi
test "$JAR_SHA" = "$(jq -r '.jar.sha256' manifest.json)"
test "$(jq -r '.sceneRelease.status' manifest.json)" = "SHADOW_READY"
test "$(jq -r '.safety.formalEventMutation' manifest.json)" = "false"
test "$(jq -r '.safety.shadowAuditRunnerImplemented' manifest.json)" = "false"
for file in jobs/*.json; do jq -e . "$file" >/dev/null; done
echo "PASS: app package checksums, manifest safety gates and five job JSON files"
