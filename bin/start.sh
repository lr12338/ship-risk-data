#!/bin/bash

APP_NAME="ship-risk-data"
JAR_NAME="ship-risk-data-0.0.1-SNAPSHOT.jar"
SPRING_PROFILE="${SPRING_PROFILE:-prod}"

BASE_DIR="$(cd "$(dirname "$0")/.." && pwd)"
JAVA_OPTS="${JAVA_OPTS:--Xms512m -Xmx2g -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=${BASE_DIR}/logs/heapdump.hprof -Dfile.encoding=UTF-8}"
JAR_PATH="${BASE_DIR}/target/${JAR_NAME}"
PID_FILE="${BASE_DIR}/${APP_NAME}.pid"
LOG_DIR="${BASE_DIR}/logs"
STARTUP_LOG="${LOG_DIR}/startup.log"

if [ ! -f "${JAR_PATH}" ]; then
  JAR_PATH="${BASE_DIR}/${JAR_NAME}"
fi

if [ ! -f "${JAR_PATH}" ]; then
  echo "[ERROR] jar not found: ${JAR_NAME}"
  echo "        place jar in ${BASE_DIR}/ or run mvn package first"
  exit 1
fi

if [ -f "${PID_FILE}" ]; then
  PID="$(cat "${PID_FILE}")"
  if [ -n "${PID}" ] && kill -0 "${PID}" 2>/dev/null; then
    echo "[WARN] ${APP_NAME} is already running, pid=${PID}"
    exit 1
  fi
  rm -f "${PID_FILE}"
fi

mkdir -p "${LOG_DIR}" "${LOG_DIR}/archive" "${LOG_DIR}/job/jobhandler"

echo "[INFO] starting ${APP_NAME} ..."
echo "[INFO] profile=${SPRING_PROFILE}"
echo "[INFO] jar=${JAR_PATH}"

nohup java ${JAVA_OPTS} \
  -jar "${JAR_PATH}" \
  --spring.profiles.active="${SPRING_PROFILE}" \
  >> "${STARTUP_LOG}" 2>&1 &

echo $! > "${PID_FILE}"
sleep 2

PID="$(cat "${PID_FILE}")"
if kill -0 "${PID}" 2>/dev/null; then
  echo "[OK] ${APP_NAME} started, pid=${PID}"
  echo "[OK] log: ${STARTUP_LOG}"
else
  echo "[ERROR] start failed, check ${STARTUP_LOG}"
  rm -f "${PID_FILE}"
  exit 1
fi
