#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
JOB_JAR="$ROOT_DIR/target/ai-flink-detect-ship-stop-0.1.0-SNAPSHOT.jar"
JAVA_BIN="${JAVA_BIN:-java}"
EVENT_TABLE="${ALONGSIDE_DB_TABLE_EVENT:-ship_realtime_alongside_event}"
DETAIL_TABLE="${ALONGSIDE_DB_TABLE_DETAIL:-ship_realtime_alongside_event_detail}"

require_env() {
  local name="$1"
  if [ -z "${!name:-}" ]; then
    echo "Missing env: $name" >&2
    exit 1
  fi
}

parse_jdbc_url() {
  local raw="${ALONGSIDE_DB_URL#jdbc:mysql://}"
  raw="${raw%%\?*}"
  DB_HOST="${raw%%:*}"
  local rest="${raw#*:}"
  DB_PORT="${rest%%/*}"
  DB_NAME="${rest#*/}"
}

require_env ALONGSIDE_DB_URL
require_env ALONGSIDE_DB_USERNAME
require_env ALONGSIDE_DB_PASSWORD
parse_jdbc_url

if [ ! -f "$JOB_JAR" ]; then
  (cd "$ROOT_DIR" && mvn -q -DskipTests package)
fi

echo "Alongside DB local verification"
echo "  host=$DB_HOST"
echo "  port=$DB_PORT"
echo "  database=$DB_NAME"
echo "  event_table=$EVENT_TABLE"
echo "  detail_table=$DETAIL_TABLE"

"$JAVA_BIN" -cp "$JOB_JAR" com.hifleet.analysis.alongside.output.db.AlongsideDbLocalVerifier init
