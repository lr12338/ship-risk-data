#!/bin/bash

APP_NAME="ship-risk-data"
BASE_DIR="$(cd "$(dirname "$0")/.." && pwd)"
PID_FILE="${BASE_DIR}/${APP_NAME}.pid"
TIMEOUT=30

stop_pid() {
  local pid="$1"
  if ! kill -0 "${pid}" 2>/dev/null; then
    return 0
  fi

  echo "[INFO] stopping ${APP_NAME}, pid=${pid} ..."
  kill "${pid}"

  local waited=0
  while kill -0 "${pid}" 2>/dev/null; do
    if [ "${waited}" -ge "${TIMEOUT}" ]; then
      echo "[WARN] graceful stop timeout, force kill pid=${pid}"
      kill -9 "${pid}"
      break
    fi
    sleep 1
    waited=$((waited + 1))
  done
}

if [ ! -f "${PID_FILE}" ]; then
  PID="$(pgrep -f "ship-risk-data-0.0.1-SNAPSHOT.jar" | head -n 1)"
  if [ -z "${PID}" ]; then
    echo "[WARN] ${APP_NAME} is not running"
    exit 0
  fi
  echo "[WARN] pid file not found, use process pid=${PID}"
else
  PID="$(cat "${PID_FILE}")"
fi

if [ -z "${PID}" ]; then
  echo "[WARN] ${APP_NAME} is not running"
  rm -f "${PID_FILE}"
  exit 0
fi

if ! kill -0 "${PID}" 2>/dev/null; then
  echo "[WARN] process not found, pid=${PID}"
  rm -f "${PID_FILE}"
  exit 0
fi

stop_pid "${PID}"
rm -f "${PID_FILE}"
echo "[OK] ${APP_NAME} stopped"
