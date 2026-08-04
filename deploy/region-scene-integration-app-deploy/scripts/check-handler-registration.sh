#!/bin/sh
set -eu
APP_HOME=${APP_HOME:-/data/zhengyi}
LOG_FILE=${LOG_FILE:-$APP_HOME/logs/log.log}
test -f "$LOG_FILE"
for handler in shipMaritimeScenePreflightJobHandler shipMaritimeSceneShadowAuditJobHandler shipBehaviorRegionPreflightJobHandler shipBehaviorRegionDryRunJobHandler; do
  grep -F "$handler" "$LOG_FILE" >/dev/null || {
    echo "MISSING_HANDLER: $handler" >&2
    exit 1
  }
done
echo "PASS: required XXL-JOB handlers found in $LOG_FILE"
