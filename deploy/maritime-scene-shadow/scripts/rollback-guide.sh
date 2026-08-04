#!/bin/sh
set -eu
APP_HOME=${APP_HOME:-/data/zhengyi}
echo "Manual rollback only; no command is executed by this guide."
echo "1. Set ship.behavior.maritime-scene.mode=OFF in $APP_HOME/application.properties"
echo "2. Set ship.behavior.maritime-scene.audit.store-enabled=false"
echo "3. Run: $APP_HOME/stop.sh"
echo "4. Restore the backed-up JAR and application.properties"
echo "5. Run: $APP_HOME/start.sh && $APP_HOME/status.sh"
echo "6. Keep the audit table by default; use the rollback SQL only after explicit approval."
