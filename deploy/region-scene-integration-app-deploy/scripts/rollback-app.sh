#!/bin/sh
set -eu
APP_HOME=${APP_HOME:-/data/zhengyi}
BACKUP_ROOT=${BACKUP_ROOT:-/data/zhengyi/backups}
STAMP=${1:?usage: rollback-app.sh BACKUP_STAMP}
BACKUP="$BACKUP_ROOT/$STAMP"
test -f "$BACKUP/ship-risk-data-0.0.1-SNAPSHOT.jar"
test -f "$BACKUP/application.properties"
cd "$APP_HOME"
./stop.sh
test ! -f ship-risk-data-0.0.1-SNAPSHOT.jar || mv ship-risk-data-0.0.1-SNAPSHOT.jar "ship-risk-data-0.0.1-SNAPSHOT.jar.failed-$STAMP"
cp "$BACKUP/ship-risk-data-0.0.1-SNAPSHOT.jar" ./ship-risk-data-0.0.1-SNAPSHOT.jar
cp "$BACKUP/application.properties" ./application.properties
./start.sh
./status.sh
echo "PASS: app JAR and configuration restored; Scene Release retained"
