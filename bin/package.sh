#!/bin/bash

set -e

APP_NAME="ship-risk-data"
VERSION="0.0.1-SNAPSHOT"
JAR_NAME="${APP_NAME}-${VERSION}.jar"
BASE_DIR="$(cd "$(dirname "$0")/.." && pwd)"
DIST_DIR="${BASE_DIR}/dist"
PACKAGE_NAME="${APP_NAME}-${VERSION}-deploy.tar.gz"

echo "[INFO] building project ..."
cd "${BASE_DIR}"
mvn clean package -DskipTests -q

if [ ! -f "${BASE_DIR}/target/${JAR_NAME}" ]; then
  echo "[ERROR] build failed, jar not found"
  exit 1
fi

rm -rf "${DIST_DIR}"
mkdir -p "${DIST_DIR}/bin" "${DIST_DIR}/logs/job/jobhandler"

cp "${BASE_DIR}/target/${JAR_NAME}" "${DIST_DIR}/"
cp "${BASE_DIR}/bin/start.sh" "${DIST_DIR}/bin/"
cp "${BASE_DIR}/bin/stop.sh" "${DIST_DIR}/bin/"
cp "${BASE_DIR}/bin/status.sh" "${DIST_DIR}/bin/"
cp "${BASE_DIR}/bin/restart.sh" "${DIST_DIR}/bin/"
chmod +x "${DIST_DIR}/bin/"*.sh

tar -czf "${BASE_DIR}/${PACKAGE_NAME}" -C "${DIST_DIR}" .

echo "[OK] package created: ${BASE_DIR}/${PACKAGE_NAME}"
echo "[OK] deploy dir preview:"
ls -lh "${DIST_DIR}"
