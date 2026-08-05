#!/usr/bin/env bash
set -euo pipefail

BASE_DIR="$(cd "$(dirname "$0")/.." && pwd)"
OUTPUT_DIR="$BASE_DIR/target/behavior-platform-v1-app-deploy"
JAR="$BASE_DIR/target/ship-risk-data-0.0.1-SNAPSHOT.jar"
STAMP="$(date +%Y%m%d-%H%M%S)"
ARCHIVE="$BASE_DIR/target/behavior-platform-v1-app-deploy-$STAMP.tar.gz"

test -f "$JAR"
rm -rf "$OUTPUT_DIR"
mkdir -p "$OUTPUT_DIR/app" "$OUTPUT_DIR/config" "$OUTPUT_DIR/jobs" "$OUTPUT_DIR/sql" "$OUTPUT_DIR/docs" "$OUTPUT_DIR/scripts"
cp "$JAR" "$OUTPUT_DIR/app/"
cp "$BASE_DIR/src/main/resources/application-prod-example.properties" "$OUTPUT_DIR/config/"
cp -R "$BASE_DIR/src/main/resources/behavior-profiles" "$OUTPUT_DIR/config/"
cp -R "$BASE_DIR/src/main/resources/behavior-rule-sets" "$OUTPUT_DIR/config/"
cp "$BASE_DIR/deploy/behavior-platform-v1/jobs/"*.json "$OUTPUT_DIR/jobs/"
cp "$BASE_DIR/src/main/resources/sql/migrations/"*behavior_platform_v1.sql "$OUTPUT_DIR/sql/"
cp "$BASE_DIR/docs/README.md" "$OUTPUT_DIR/docs/"
cp "$BASE_DIR/docs/operations/behavior-service-runbook.md" "$OUTPUT_DIR/docs/"
cp "$BASE_DIR/docs/operations/deploy-and-rollback.md" "$OUTPUT_DIR/docs/"
cp "$BASE_DIR/docs/database/migrations.md" "$OUTPUT_DIR/docs/"
cp "$BASE_DIR/scripts/validate-xxl-job-param" "$OUTPUT_DIR/scripts/"
cp "$BASE_DIR/deploy/behavior-platform-v1/scripts/verify-package.sh" "$OUTPUT_DIR/scripts/"

GIT_HEAD="$(git -C "$BASE_DIR" rev-parse HEAD)"
GIT_BRANCH="$(git -C "$BASE_DIR" branch --show-current)"
JAR_SHA="$(shasum -a 256 "$JAR" | awk '{print $1}')"
JAR_SIZE="$(wc -c < "$JAR" | tr -d ' ')"
jq -n --arg gitHead "$GIT_HEAD" --arg gitBranch "$GIT_BRANCH" \
  --arg builtAt "$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
  --arg jarName "$(basename "$JAR")" --arg jarSha "$JAR_SHA" --argjson jarSize "$JAR_SIZE" \
  '{gitHead:$gitHead,gitBranch:$gitBranch,builtAt:$builtAt,
    jar:{name:$jarName,sizeBytes:$jarSize,sha256:$jarSha},
    safety:{formalWriteEnabled:false,shadowWriteEnabled:false,automaticDdl:false,
      productionMigrationExecuted:false,serverDeploymentExecuted:false}}' > "$OUTPUT_DIR/manifest.json"

(cd "$OUTPUT_DIR" && find . -type f ! -name checksums.sha256 -print | LC_ALL=C sort | while IFS= read -r file; do
  shasum -a 256 "$file"
done > checksums.sha256)
tar -C "$BASE_DIR/target" -czf "$ARCHIVE" behavior-platform-v1-app-deploy
shasum -a 256 "$ARCHIVE" > "$ARCHIVE.sha256"
printf '%s\n' "$ARCHIVE"
cat "$ARCHIVE.sha256"
