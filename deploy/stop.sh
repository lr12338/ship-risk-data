#!/bin/bash

APP_NAME="ship-risk-data"
JAR_NAME="ship-risk-data-0.0.1-SNAPSHOT.jar"
BASE_DIR="$(cd "$(dirname "$0")" && pwd)"
PID_FILE="${BASE_DIR}/${APP_NAME}.pid"
JAR_PATH="${BASE_DIR}/${JAR_NAME}"

find_pids() {
  local pids=""
  if [ -f "${PID_FILE}" ]; then
    local file_pid
    file_pid="$(cat "${PID_FILE}" 2>/dev/null)"
    if [ -n "${file_pid}" ] && kill -0 "${file_pid}" 2>/dev/null; then
      pids="${file_pid}"
    fi
  fi
  if [ -z "${pids}" ]; then
    pids="$(pgrep -f "${JAR_PATH}" 2>/dev/null || true)"
  fi
  if [ -z "${pids}" ]; then
    pids="$(pgrep -f "${JAR_NAME}" 2>/dev/null || true)"
  fi
  echo "${pids}" | tr ' ' '\n' | awk 'NF' | sort -u | tr '\n' ' '
}

PIDS="$(find_pids)"
if [ -z "${PIDS// }" ]; then
  rm -f "${PID_FILE}"
  exit 0
fi

for PID in ${PIDS}; do
  kill -9 "${PID}" 2>/dev/null || true
  pkill -9 -P "${PID}" 2>/dev/null || true
done

pkill -9 -f "${JAR_PATH}" 2>/dev/null || true
pkill -9 -f "${JAR_NAME}" 2>/dev/null || true
rm -f "${PID_FILE}"
