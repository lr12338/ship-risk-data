#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."
shasum -a 256 -c checksums.sha256
JAR_SHA="$(shasum -a 256 app/ship-risk-data-0.0.1-SNAPSHOT.jar | awk '{print $1}')"
test "$JAR_SHA" = "$(jq -r '.jar.sha256' manifest.json)"
test "$(jq -r '.safety.formalWriteEnabled' manifest.json)" = false
test "$(jq -r '.safety.automaticDdl' manifest.json)" = false
echo "PASS: Behavior Platform package checksums and safety manifest"
