#!/bin/bash

APP_NAME="ship-risk-data"
JAR_NAME="ship-risk-data-0.0.1-SNAPSHOT.jar"
SPRING_PROFILE="${SPRING_PROFILE:-prod}"

BASE_DIR="$(cd "$(dirname "$0")" && pwd)"
JAVA_OPTS="${JAVA_OPTS:--Xms512m -Xmx2g -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=${BASE_DIR}/logs/heapdump.hprof -Dfile.encoding=UTF-8 -Duser.timezone=Asia/Shanghai}"
JAR_PATH="${BASE_DIR}/${JAR_NAME}"
PID_FILE="${BASE_DIR}/${APP_NAME}.pid"
LOG_DIR="${BASE_DIR}/logs"
STARTUP_LOG="${LOG_DIR}/startup.log"

if [ ! -f "${JAR_PATH}" ]; then
  echo "jar not found: ${JAR_PATH}" >&2
  exit 1
fi

if [ -f "${PID_FILE}" ]; then
  PID="$(cat "${PID_FILE}")"
  if [ -n "${PID}" ] && kill -0 "${PID}" 2>/dev/null; then
    echo "already running, pid=${PID}" >&2
    exit 1
  fi
  rm -f "${PID_FILE}"
fi

mkdir -p "${LOG_DIR}" "${LOG_DIR}/archive" "${LOG_DIR}/job/jobhandler"

if [ ! -f "${BASE_DIR}/application.properties" ]; then
  echo "application.properties not found in ${BASE_DIR}" >&2
  exit 1
fi
CONFIG_ARG="--spring.config.location=file:${BASE_DIR}/application.properties"

cd "${BASE_DIR}"
nohup java ${JAVA_OPTS} \
  -jar "${JAR_PATH}" \
  --spring.profiles.active="${SPRING_PROFILE}" \
  ${CONFIG_ARG} \
  >> "${STARTUP_LOG}" 2>&1 &

echo $! > "${PID_FILE}"

PID="$(cat "${PID_FILE}")"
for _ in 1 2 3 4 5; do
  if kill -0 "${PID}" 2>/dev/null; then
    exit 0
  fi
  sleep 0.2
done

echo "start failed, see ${STARTUP_LOG}" >&2
rm -f "${PID_FILE}"
exit 1
