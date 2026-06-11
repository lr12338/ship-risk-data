#!/bin/bash

APP_NAME="ship-risk-data"
JAR_NAME="ship-risk-data-0.0.1-SNAPSHOT.jar"
BASE_DIR="$(cd "$(dirname "$0")" && pwd)"
PID_FILE="${BASE_DIR}/${APP_NAME}.pid"

if [ -f "${PID_FILE}" ]; then
  PID="$(cat "${PID_FILE}")"
else
  PID="$(pgrep -f "${BASE_DIR}/${JAR_NAME}" | head -n 1)"
fi

if [ -n "${PID}" ] && kill -0 "${PID}" 2>/dev/null; then
  echo "[RUNNING] ${APP_NAME}"
  echo "  dir     : ${BASE_DIR}"
  echo "  pid     : ${PID}"
  echo "  profile : ${SPRING_PROFILE:-prod}"
  echo "  port    : 8090 (HTTP), 30008 (XXL-Job)"
  echo "  logs    : ${BASE_DIR}/logs/"
  ps -p "${PID}" -o pid,etime,rss,cmd --no-headers 2>/dev/null
  exit 0
fi

echo "[STOPPED] ${APP_NAME}"
exit 1
