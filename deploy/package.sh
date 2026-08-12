#!/bin/bash

set -e

APP_NAME="ship-risk-data"
VERSION="0.0.1-SNAPSHOT"
JAR_NAME="${APP_NAME}-${VERSION}.jar"
BASE_DIR="$(cd "$(dirname "$0")/.." && pwd)"
DEPLOY_DIR="${BASE_DIR}/deploy"
DIST_DIR="${BASE_DIR}/dist"

echo "[INFO] building project ..."
cd "${BASE_DIR}"
mvn clean package -DskipTests -q

if [ ! -f "${BASE_DIR}/target/${JAR_NAME}" ]; then
  echo "[ERROR] build failed, jar not found"
  exit 1
fi

rm -rf "${DIST_DIR}"
mkdir -p "${DIST_DIR}/logs/archive" "${DIST_DIR}/logs/job/jobhandler"

cp "${BASE_DIR}/target/${JAR_NAME}" "${DIST_DIR}/"
cp "${DEPLOY_DIR}/start.sh" "${DIST_DIR}/"
cp "${DEPLOY_DIR}/stop.sh" "${DIST_DIR}/"
cp "${DEPLOY_DIR}/status.sh" "${DIST_DIR}/"
cp "${DEPLOY_DIR}/restart.sh" "${DIST_DIR}/"
cp "${DEPLOY_DIR}/application.properties" "${DIST_DIR}/application.properties.example"
cp -R "${DEPLOY_DIR}/behavior-insurance-rules-v1" "${DIST_DIR}/"
(cd "${DIST_DIR}" && shasum -a 256 "${JAR_NAME}" > "${JAR_NAME}.sha256")
chmod +x "${DIST_DIR}/"*.sh

echo ""
echo "[OK] deploy files ready: ${DIST_DIR}/"
echo ""
ls -lh "${DIST_DIR}"
echo ""
echo "[NEXT] upload to server /data/zhengyi (no extract needed):"
echo "  scp dist/${JAR_NAME} user@39.96.38.242:/data/zhengyi/${JAR_NAME}.uploading"
echo ""
echo "[NEXT] on server:"
echo "  follow dist/behavior-insurance-rules-v1/manual-deployment.md"
echo ""
echo "[NOTE] prod config lives in /data/zhengyi/application.properties on server."
echo "       update jar only; do NOT overwrite server application.properties."
