#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MODE="${1:-quick}"

KAFKA_BOOTSTRAP="${KAFKA_BOOTSTRAP:-localhost:9092}"
KAFKA_TOPICS_BIN="${KAFKA_TOPICS_BIN:-$(command -v kafka-topics || command -v kafka-topics.sh || printf 'kafka-topics.sh')}"
KAFKA_PRODUCER_BIN="${KAFKA_PRODUCER_BIN:-$(command -v kafka-console-producer || command -v kafka-console-producer.sh || printf 'kafka-console-producer.sh')}"
KAFKA_CONSUMER_BIN="${KAFKA_CONSUMER_BIN:-$(command -v kafka-console-consumer || command -v kafka-console-consumer.sh || printf 'kafka-console-consumer.sh')}"
if [ -x /tmp/flink-1.16/flink-1.16.0/bin/flink ]; then
  DEFAULT_FLINK_BIN=/tmp/flink-1.16/flink-1.16.0/bin/flink
else
  DEFAULT_FLINK_BIN=flink
fi
FLINK_BIN="${FLINK_BIN:-$DEFAULT_FLINK_BIN}"
KAFKA_JAVA_HOME="${KAFKA_JAVA_HOME:-${JAVA21_HOME:-/opt/homebrew/opt/openjdk@21}}"
FLINK_JAVA_HOME="${FLINK_JAVA_HOME:-${JAVA8_HOME:-${JAVA_HOME:-/Users/raymondlu/.sdkman/candidates/java/current}}}"
FLINK_RUN_TARGET="${FLINK_RUN_TARGET:-}"
FLINK_LOCAL_LIB_CLASSPATH="${FLINK_LOCAL_LIB_CLASSPATH:-/tmp/flink-1.16/flink-1.16.0/lib/*}"
ALONGSIDE_ENABLED="${ALONGSIDE_ENABLED:-true}"

AIS_TOPIC="${AIS_TOPIC:-AIS_ALONGSIDE_TEST}"
ALONGSIDE_TOPIC="${ALONGSIDE_TOPIC:-RELIABLE_ALONGSIDE_EVENTS_TEST}"
STOP_TOPIC="${STOP_TOPIC:-RELIABLE_STOP_POINTS_TEST}"
GROUP_ID="${GROUP_ID:-alongside-local-group-$(date +%Y%m%d%H%M%S)}"
CLIENT_ID="${CLIENT_ID:-alongside-local-consumer}"
SAMPLE_FILE="${SAMPLE_FILE:-$ROOT_DIR/src/test/resources/alongside/local-alongside-sample.jsonl}"
OUTPUT_FILE="${OUTPUT_FILE:-$ROOT_DIR/target/alongside-e2e-output.jsonl}"
DEFAULT_LIVE_EVENTS_FILE="$ROOT_DIR/target/alongside-live-events.jsonl"
LIVE_EVENTS_FILE="${LIVE_EVENTS_FILE:-$DEFAULT_LIVE_EVENTS_FILE}"
JOB_JAR="$ROOT_DIR/target/ai-flink-detect-ship-stop-0.1.0-SNAPSHOT.jar"
LOCAL_STATE_BACKEND="${LOCAL_STATE_BACKEND:-hashmap}"
LOCAL_CHECKPOINT_INTERVAL_MS="${LOCAL_CHECKPOINT_INTERVAL_MS:-5000}"
LOCAL_CHECKPOINT_MIN_PAUSE_MS="${LOCAL_CHECKPOINT_MIN_PAUSE_MS:-1000}"
LOCAL_REORDER_LATENESS_SECONDS="${LOCAL_REORDER_LATENESS_SECONDS:-30}"
KAFKA_STARTING_OFFSETS="${KAFKA_STARTING_OFFSETS:-}"
RUNNING_JOB_ID=""

usage() {
  cat <<EOF
Usage:
  bash scripts/verify-alongside-local.sh quick
  bash scripts/verify-alongside-local.sh e2e
  bash scripts/verify-alongside-local.sh e2e-db
  bash scripts/verify-alongside-local.sh e2e-disabled
  bash scripts/verify-alongside-local.sh e2e-disabled-db
  bash scripts/verify-alongside-local.sh live
  bash scripts/verify-alongside-local.sh live-event
  bash scripts/verify-alongside-local.sh replay

Environment overrides:
  KAFKA_BOOTSTRAP=$KAFKA_BOOTSTRAP
  AIS_TOPIC=$AIS_TOPIC
  ALONGSIDE_TOPIC=$ALONGSIDE_TOPIC
  STOP_TOPIC=$STOP_TOPIC
  SAMPLE_FILE=$SAMPLE_FILE
  FLINK_BIN=$FLINK_BIN
  KAFKA_JAVA_HOME=$KAFKA_JAVA_HOME
  FLINK_JAVA_HOME=$FLINK_JAVA_HOME
  FLINK_RUN_TARGET=$FLINK_RUN_TARGET
  FLINK_LOCAL_LIB_CLASSPATH=$FLINK_LOCAL_LIB_CLASSPATH
  LOCAL_STATE_BACKEND=$LOCAL_STATE_BACKEND
  LOCAL_CHECKPOINT_INTERVAL_MS=$LOCAL_CHECKPOINT_INTERVAL_MS
  LOCAL_REORDER_LATENESS_SECONDS=$LOCAL_REORDER_LATENESS_SECONDS
  KAFKA_STARTING_OFFSETS=$KAFKA_STARTING_OFFSETS
  LIVE_SOURCE_TOPIC=${LIVE_SOURCE_TOPIC:-LIVE_RECENTSHIPS_DATA}
  LIVE_SECONDS=${LIVE_SECONDS:-120}
  SCOPE_MMSI_WHITELIST=${SCOPE_MMSI_WHITELIST:-}
  SCOPE_MIN_LON=${SCOPE_MIN_LON:-}
  SCOPE_MAX_LON=${SCOPE_MAX_LON:-}
  SCOPE_MIN_LAT=${SCOPE_MIN_LAT:-}
  SCOPE_MAX_LAT=${SCOPE_MAX_LAT:-}
  LOCAL_ALONGSIDE_CANDIDATE_MIN_DURATION_MINUTES=${LOCAL_ALONGSIDE_CANDIDATE_MIN_DURATION_MINUTES:-}
  LOCAL_ALONGSIDE_CANDIDATE_MIN_POINTS=${LOCAL_ALONGSIDE_CANDIDATE_MIN_POINTS:-}
  LOCAL_ALONGSIDE_CONFIRM_MIN_DURATION_MINUTES=${LOCAL_ALONGSIDE_CONFIRM_MIN_DURATION_MINUTES:-}
  LOCAL_ALONGSIDE_CONFIRM_MIN_POINTS=${LOCAL_ALONGSIDE_CONFIRM_MIN_POINTS:-}
  ALONGSIDE_ENABLED=${ALONGSIDE_ENABLED:-true}
  ALONGSIDE_DB_SINK_ENABLED=${ALONGSIDE_DB_SINK_ENABLED:-false}
  ALONGSIDE_DB_URL=${ALONGSIDE_DB_URL:-}
  ALONGSIDE_DB_USERNAME=${ALONGSIDE_DB_USERNAME:-}
  ALONGSIDE_DB_TABLE_EVENT=${ALONGSIDE_DB_TABLE_EVENT:-ship_realtime_alongside_event}
  ALONGSIDE_DB_TABLE_DETAIL=${ALONGSIDE_DB_TABLE_DETAIL:-ship_realtime_alongside_event_detail}
EOF
}

require_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Missing command: $1" >&2
    exit 1
  fi
}

quick() {
  cd "$ROOT_DIR"
  mvn test
  mvn -q -DskipTests package
}

create_topics() {
  kafka_env "$KAFKA_TOPICS_BIN" --bootstrap-server "$KAFKA_BOOTSTRAP" --create --if-not-exists \
    --topic "$AIS_TOPIC" --partitions 1 --replication-factor 1
  create_output_topics
}

create_output_topics() {
  kafka_env "$KAFKA_TOPICS_BIN" --bootstrap-server "$KAFKA_BOOTSTRAP" --create --if-not-exists \
    --topic "$ALONGSIDE_TOPIC" --partitions 1 --replication-factor 1
  kafka_env "$KAFKA_TOPICS_BIN" --bootstrap-server "$KAFKA_BOOTSTRAP" --create --if-not-exists \
    --topic "$STOP_TOPIC" --partitions 1 --replication-factor 1
}

submit_flink_job() {
  mkdir -p /tmp/flink-alongside/checkpoints /tmp/flink-alongside/savepoints /tmp/flink-alongside/rocksdb
  if [ "$FLINK_RUN_TARGET" = "local-java" ]; then
    local log_file="$ROOT_DIR/target/alongside-local-java-job.log"
    flink_env java $(app_jvm_args) -cp "$JOB_JAR:$FLINK_LOCAL_LIB_CLASSPATH" \
      com.hifleet.analysis.portcall.flink.FlinkPortCallJob > "$log_file" 2>&1 &
    RUNNING_JOB_ID="pid:$!"
    echo "Started local Java Flink job pid=${RUNNING_JOB_ID#pid:}, log=$log_file"
    return
  fi
  local output
  set +e
  if [ -n "$FLINK_RUN_TARGET" ]; then
    output="$(flink_env "$FLINK_BIN" run -d \
      -t "$FLINK_RUN_TARGET" \
      -c com.hifleet.analysis.portcall.flink.FlinkPortCallJob \
      "$JOB_JAR" 2>&1)"
  else
    output="$(flink_env "$FLINK_BIN" run -d \
      -c com.hifleet.analysis.portcall.flink.FlinkPortCallJob \
      "$JOB_JAR" 2>&1)"
  fi
  local code=$?
  set -e
  printf '%s\n' "$output"
  if [ "$code" -ne 0 ]; then
    exit "$code"
  fi
  RUNNING_JOB_ID="$(printf '%s\n' "$output" | sed -n 's/.*JobID //p' | tail -n 1)"
  if [ -z "$RUNNING_JOB_ID" ]; then
    echo "Flink job submission did not return a JobID." >&2
    exit 1
  fi
}

cleanup_flink_job() {
  if [ -n "$RUNNING_JOB_ID" ]; then
    if [[ "$RUNNING_JOB_ID" == pid:* ]]; then
      kill "${RUNNING_JOB_ID#pid:}" >/dev/null 2>&1 || true
      wait "${RUNNING_JOB_ID#pid:}" >/dev/null 2>&1 || true
    else
      flink_env "$FLINK_BIN" cancel "$RUNNING_JOB_ID" >/dev/null 2>&1 || true
    fi
  fi
}

produce_sample() {
  kafka_env "$KAFKA_PRODUCER_BIN" --bootstrap-server "$KAFKA_BOOTSTRAP" --topic "$AIS_TOPIC" < "$SAMPLE_FILE"
}

consume_alongside_output() {
  mkdir -p "$(dirname "$OUTPUT_FILE")"
  rm -f "$OUTPUT_FILE"
  set +e
  kafka_env "$KAFKA_CONSUMER_BIN" \
    --bootstrap-server "$KAFKA_BOOTSTRAP" \
    --topic "$ALONGSIDE_TOPIC" \
    --from-beginning \
    --timeout-ms 20000 \
    --max-messages 5 > "$OUTPUT_FILE"
  local code=$?
  set -e
  if [ "$code" -ne 0 ] && [ ! -s "$OUTPUT_FILE" ]; then
    echo "No alongside event consumed from $ALONGSIDE_TOPIC within timeout." >&2
    exit 1
  fi
}

assert_no_alongside_output() {
  mkdir -p "$(dirname "$OUTPUT_FILE")"
  rm -f "$OUTPUT_FILE"
  set +e
  kafka_env "$KAFKA_CONSUMER_BIN" \
    --bootstrap-server "$KAFKA_BOOTSTRAP" \
    --topic "$ALONGSIDE_TOPIC" \
    --from-beginning \
    --timeout-ms 8000 \
    --max-messages 1 > "$OUTPUT_FILE"
  local code=$?
  set -e
  if [ -s "$OUTPUT_FILE" ]; then
    echo "Unexpected alongside event consumed while alongside.enabled=false: $OUTPUT_FILE" >&2
    exit 1
  fi
  echo "No alongside output verified for alongside.enabled=false"
}

assert_output() {
  grep -q '"pair_id":"413193000-413433610"' "$OUTPUT_FILE"
  grep -q '"event_type":"ALONGSIDE_CANDIDATE"' "$OUTPUT_FILE"
  grep -q '"event_sub_type":"ALONGSIDE_CONFIRMED_CANDIDATE"' "$OUTPUT_FILE"
  grep -q '"source_mode":"REALTIME_AUTO_BUCKET"' "$OUTPUT_FILE"
  grep -q '"rule_version":"ALONGSIDE_V2_INSURANCE"' "$OUTPUT_FILE"
  if grep -q '"pair_id":"413433610-413193000"' "$OUTPUT_FILE"; then
    echo "Unexpected reversed pair id found in $OUTPUT_FILE" >&2
    exit 1
  fi
  echo "Alongside e2e output verified: $OUTPUT_FILE"
}

parse_jdbc_url() {
  local raw="${ALONGSIDE_DB_URL#jdbc:mysql://}"
  raw="${raw%%\?*}"
  DB_HOST="${raw%%:*}"
  local rest="${raw#*:}"
  DB_PORT="${rest%%/*}"
  DB_NAME="${rest#*/}"
}

require_db_env() {
  if [ -z "${ALONGSIDE_DB_URL:-}" ] || [ -z "${ALONGSIDE_DB_USERNAME:-}" ] || [ -z "${ALONGSIDE_DB_PASSWORD:-}" ]; then
    echo "ALONGSIDE_DB_URL, ALONGSIDE_DB_USERNAME and ALONGSIDE_DB_PASSWORD are required for DB verification." >&2
    exit 1
  fi
  parse_jdbc_url
}

db_query() {
  java -cp "$JOB_JAR" com.hifleet.analysis.alongside.output.db.AlongsideDbLocalVerifier query "$1"
}

db_scalar() {
  db_query "$1" | awk 'NR==2 {print $1}'
}

db_execute() {
  java -cp "$JOB_JAR" com.hifleet.analysis.alongside.output.db.AlongsideDbLocalVerifier execute "$1" >/dev/null
}

cleanup_alongside_db_rows() {
  require_db_env
  db_execute "DELETE FROM ${ALONGSIDE_DB_TABLE_DETAIL:-ship_realtime_alongside_event_detail} WHERE pair_id='413193000-413433610'"
  db_execute "DELETE FROM ${ALONGSIDE_DB_TABLE_EVENT:-ship_realtime_alongside_event} WHERE pair_id='413193000-413433610'"
}

assert_db_output() {
  require_db_env
  local event_table="${ALONGSIDE_DB_TABLE_EVENT:-ship_realtime_alongside_event}"
  local detail_table="${ALONGSIDE_DB_TABLE_DETAIL:-ship_realtime_alongside_event_detail}"
  local cnt weak confirmed duplicate_count json_invalid detail_roles
  cnt="$(db_scalar "SELECT COUNT(*) AS cnt FROM $event_table WHERE pair_id='413193000-413433610'")"
  weak="$(db_scalar "SELECT COUNT(*) AS cnt FROM $event_table WHERE pair_id='413193000-413433610' AND event_sub_type='WEAK_ALONGSIDE_CANDIDATE'")"
  confirmed="$(db_scalar "SELECT COUNT(*) AS cnt FROM $event_table WHERE pair_id='413193000-413433610' AND event_sub_type='ALONGSIDE_CONFIRMED_CANDIDATE'")"
  duplicate_count="$(db_scalar "SELECT COUNT(*) AS cnt FROM (SELECT event_id FROM $event_table WHERE pair_id='413193000-413433610' GROUP BY event_id HAVING COUNT(*) > 1) t")"
  json_invalid="$(db_scalar "SELECT COUNT(*) AS cnt FROM $event_table WHERE pair_id='413193000-413433610' AND JSON_VALID(score_detail_json) <> 1")"
  detail_roles="$(db_scalar "SELECT COUNT(DISTINCT point_role) AS cnt FROM $detail_table WHERE pair_id='413193000-413433610' AND point_role IN ('OWN','TARGET')")"
  if [ "$cnt" -lt 2 ] || [ "$weak" -lt 1 ] || [ "$confirmed" -lt 1 ] || [ "$duplicate_count" -ne 0 ] || [ "$json_invalid" -ne 0 ]; then
    echo "Unexpected DB verification result: cnt=$cnt weak=$weak confirmed=$confirmed duplicate=$duplicate_count json_invalid=$json_invalid" >&2
    exit 1
  fi
  if [ "${ALONGSIDE_DB_DETAIL_ENABLED:-true}" = "true" ] && [ "$detail_roles" -lt 2 ]; then
    echo "Expected OWN and TARGET detail rows, found distinct roles=$detail_roles" >&2
    exit 1
  fi
  echo "Alongside DB output verified: cnt=$cnt weak=$weak confirmed=$confirmed duplicate=$duplicate_count json_invalid=$json_invalid detail_roles=$detail_roles"
  db_query "SELECT COUNT(*) AS cnt FROM $event_table"
  db_query "SELECT event_id,pair_id,event_sub_type,rule_version,mmsi_a,mmsi_b,start_time,end_time,duration_minutes,point_count,min_distance_nm,max_distance_nm,avg_relative_speed,heading_parallel_ratio,severity_level,event_score,confidence_level,exclusion_flag,exclusion_reason,JSON_VALID(score_detail_json) AS score_json_valid FROM $event_table ORDER BY created_at DESC LIMIT 10"
  db_query "SELECT event_id,point_role,COUNT(*) AS point_count FROM $detail_table GROUP BY event_id,point_role ORDER BY event_id,point_role"
}

e2e() {
  trap cleanup_flink_job EXIT
  require_cmd "$KAFKA_TOPICS_BIN"
  require_cmd "$KAFKA_PRODUCER_BIN"
  require_cmd "$KAFKA_CONSUMER_BIN"
  require_cmd "$FLINK_BIN"
  KAFKA_STARTING_OFFSETS="${KAFKA_STARTING_OFFSETS:-earliest}"
  local suffix
  suffix="$(date +%Y%m%d%H%M%S)"
  if [ "$AIS_TOPIC" = "AIS_ALONGSIDE_TEST" ]; then
    AIS_TOPIC="AIS_ALONGSIDE_TEST_$suffix"
  fi
  if [ "$ALONGSIDE_TOPIC" = "RELIABLE_ALONGSIDE_EVENTS_TEST" ]; then
    ALONGSIDE_TOPIC="RELIABLE_ALONGSIDE_EVENTS_TEST_$suffix"
  fi
  if [ "$STOP_TOPIC" = "RELIABLE_STOP_POINTS_TEST" ]; then
    STOP_TOPIC="RELIABLE_STOP_POINTS_TEST_$suffix"
  fi
  if [[ "$GROUP_ID" == alongside-local-group-* ]]; then
    GROUP_ID="alongside-local-group-$suffix"
  fi
  quick
  create_topics
  submit_flink_job
  sleep 8
  produce_sample
  sleep 10
  consume_alongside_output
  assert_output
  cleanup_flink_job
  RUNNING_JOB_ID=""
}

e2e_disabled() {
  trap cleanup_flink_job EXIT
  require_cmd "$KAFKA_TOPICS_BIN"
  require_cmd "$KAFKA_PRODUCER_BIN"
  require_cmd "$KAFKA_CONSUMER_BIN"
  require_cmd "$FLINK_BIN"
  KAFKA_STARTING_OFFSETS="${KAFKA_STARTING_OFFSETS:-earliest}"
  ALONGSIDE_ENABLED=false
  local suffix
  suffix="$(date +%Y%m%d%H%M%S)"
  if [ "$AIS_TOPIC" = "AIS_ALONGSIDE_TEST" ]; then
    AIS_TOPIC="AIS_ALONGSIDE_DISABLED_TEST_$suffix"
  fi
  if [ "$ALONGSIDE_TOPIC" = "RELIABLE_ALONGSIDE_EVENTS_TEST" ]; then
    ALONGSIDE_TOPIC="RELIABLE_ALONGSIDE_EVENTS_DISABLED_TEST_$suffix"
  fi
  if [ "$STOP_TOPIC" = "RELIABLE_STOP_POINTS_TEST" ]; then
    STOP_TOPIC="RELIABLE_STOP_POINTS_DISABLED_TEST_$suffix"
  fi
  if [[ "$GROUP_ID" == alongside-local-group-* ]]; then
    GROUP_ID="alongside-local-disabled-group-$suffix"
  fi
  quick
  create_topics
  submit_flink_job
  sleep 8
  produce_sample
  sleep 10
  assert_no_alongside_output
  cleanup_flink_job
  RUNNING_JOB_ID=""
}

e2e_db() {
  local suffix
  suffix="$(date +%Y%m%d%H%M%S)"
  ALONGSIDE_DB_SINK_ENABLED=true
  AIS_TOPIC="AIS_ALONGSIDE_DB_TEST_$suffix"
  ALONGSIDE_TOPIC="RELIABLE_ALONGSIDE_EVENTS_DB_TEST_$suffix"
  STOP_TOPIC="RELIABLE_STOP_POINTS_DB_TEST_$suffix"
  GROUP_ID="alongside-local-db-group-$suffix"
  bash "$ROOT_DIR/scripts/verify-alongside-db-local.sh"
  cleanup_alongside_db_rows
  e2e
  assert_db_output
  e2e
  assert_db_output
}

e2e_disabled_db() {
  local suffix
  suffix="$(date +%Y%m%d%H%M%S)"
  ALONGSIDE_DB_SINK_ENABLED=true
  AIS_TOPIC="AIS_ALONGSIDE_DISABLED_DB_TEST_$suffix"
  ALONGSIDE_TOPIC="RELIABLE_ALONGSIDE_EVENTS_DISABLED_DB_TEST_$suffix"
  STOP_TOPIC="RELIABLE_STOP_POINTS_DISABLED_DB_TEST_$suffix"
  GROUP_ID="alongside-local-disabled-db-group-$suffix"
  bash "$ROOT_DIR/scripts/verify-alongside-db-local.sh"
  cleanup_alongside_db_rows
  e2e_disabled
  require_db_env
  local cnt
  cnt="$(db_scalar "SELECT COUNT(*) AS cnt FROM ${ALONGSIDE_DB_TABLE_EVENT:-ship_realtime_alongside_event} WHERE pair_id='413193000-413433610'")"
  if [ "$cnt" -ne 0 ]; then
    echo "Unexpected DB rows while alongside.enabled=false: $cnt" >&2
    exit 1
  fi
  echo "No alongside DB output verified for alongside.enabled=false"
}

live() {
  trap cleanup_flink_job EXIT
  require_cmd "$KAFKA_TOPICS_BIN"
  require_cmd "$FLINK_BIN"
  quick
  AIS_TOPIC="${LIVE_SOURCE_TOPIC:-LIVE_RECENTSHIPS_DATA}"
  if [ "$ALONGSIDE_TOPIC" = "RELIABLE_ALONGSIDE_EVENTS_TEST" ]; then
    ALONGSIDE_TOPIC="RELIABLE_ALONGSIDE_EVENTS_LIVE_TEST"
  fi
  if [ "$STOP_TOPIC" = "RELIABLE_STOP_POINTS_TEST" ]; then
    STOP_TOPIC="RELIABLE_STOP_POINTS_LIVE_TEST"
  fi
  GROUP_ID="${GROUP_ID:-alongside-live-local-$(date +%Y%m%d%H%M%S)}"
  KAFKA_STARTING_OFFSETS="${KAFKA_STARTING_OFFSETS:-latest}"
  create_output_topics
  mkdir -p "$ROOT_DIR/target"
  : > "$ROOT_DIR/target/alongside-live-taskmanager.out"
  truncate_taskmanager_stdout || true
  submit_flink_job
  sleep "${LIVE_SECONDS:-120}"
  copy_taskmanager_stdout "$ROOT_DIR/target/alongside-live-taskmanager.out" || true
  assert_live_logs "$ROOT_DIR/target/alongside-live-taskmanager.out"
  extract_live_events "$ROOT_DIR/target/alongside-live-taskmanager.out" "$LIVE_EVENTS_FILE"
  echo "Live validation log: $ROOT_DIR/target/alongside-live-taskmanager.out"
  echo "Live alongside events: $LIVE_EVENTS_FILE"
}

live_event() {
  local suffix
  suffix="$(date +%Y%m%d%H%M%S)"
  if [ "$ALONGSIDE_TOPIC" = "RELIABLE_ALONGSIDE_EVENTS_TEST" ]; then
    ALONGSIDE_TOPIC="RELIABLE_ALONGSIDE_EVENTS_LIVE_EVENT_TEST_$suffix"
  fi
  if [ "$STOP_TOPIC" = "RELIABLE_STOP_POINTS_TEST" ]; then
    STOP_TOPIC="RELIABLE_STOP_POINTS_LIVE_EVENT_TEST_$suffix"
  fi
  if [ "$LIVE_EVENTS_FILE" = "$DEFAULT_LIVE_EVENTS_FILE" ]; then
    LIVE_EVENTS_FILE="$ROOT_DIR/target/alongside-live-event-output.jsonl"
  fi
  LIVE_SECONDS="${LIVE_SECONDS:-180}"
  REQUIRE_ALONGSIDE_EVENT="${REQUIRE_ALONGSIDE_EVENT:-true}"
  LOCAL_ALONGSIDE_CANDIDATE_MIN_DURATION_MINUTES="${LOCAL_ALONGSIDE_CANDIDATE_MIN_DURATION_MINUTES:-1}"
  LOCAL_ALONGSIDE_CANDIDATE_MIN_POINTS="${LOCAL_ALONGSIDE_CANDIDATE_MIN_POINTS:-2}"
  LOCAL_ALONGSIDE_CONFIRM_MIN_DURATION_MINUTES="${LOCAL_ALONGSIDE_CONFIRM_MIN_DURATION_MINUTES:-2}"
  LOCAL_ALONGSIDE_CONFIRM_MIN_POINTS="${LOCAL_ALONGSIDE_CONFIRM_MIN_POINTS:-3}"
  echo "Running live-event with local validation thresholds only:"
  echo "  candidate=${LOCAL_ALONGSIDE_CANDIDATE_MIN_DURATION_MINUTES}min/${LOCAL_ALONGSIDE_CANDIDATE_MIN_POINTS}points"
  echo "  confirm=${LOCAL_ALONGSIDE_CONFIRM_MIN_DURATION_MINUTES}min/${LOCAL_ALONGSIDE_CONFIRM_MIN_POINTS}points"
  echo "  output_topic=$ALONGSIDE_TOPIC"
  live
  consume_live_event_output
}

truncate_taskmanager_stdout() {
  local file
  file="$(latest_taskmanager_stdout)"
  if [ -n "$file" ]; then
    : > "$file"
  fi
}

copy_taskmanager_stdout() {
  local target="$1"
  local file
  file="$(latest_taskmanager_stdout)"
  if [ -n "$file" ]; then
    cp "$file" "$target"
  fi
}

latest_taskmanager_stdout() {
  ls -t /tmp/flink-1.16/flink-1.16.0/log/flink-*-taskexecutor-*.out 2>/dev/null | head -n 1
}

assert_live_logs() {
  local log_file="$1"
  if ! grep -q '\[debug-clean-ais\]' "$log_file"; then
    echo "No realtime AIS point reached keyed preprocess. Check Kafka topic, scope filter, and parser compatibility." >&2
    exit 1
  fi
  local clean_count
  clean_count="$(grep -c '\[debug-clean-ais\]' "$log_file" || true)"
  local evidence_count
  evidence_count="$(grep -c '\[debug-pair-evidence\]' "$log_file" || true)"
  local event_count
  event_count="$(grep -c '"event_type":"ALONGSIDE_CANDIDATE"' "$log_file" || true)"
  echo "Live validation observed clean_ais=$clean_count, pair_evidence=$evidence_count, alongside_events=$event_count"
  if [ "${REQUIRE_ALONGSIDE_EVENT:-false}" = "true" ] && [ "$event_count" -eq 0 ]; then
    echo "Expected at least one alongside event, but none was observed." >&2
    exit 1
  fi
}

extract_live_events() {
  local log_file="$1"
  local output_file="$2"
  mkdir -p "$(dirname "$output_file")"
  python3 - "$log_file" "$output_file" <<'PY'
import sys

log_file, output_file = sys.argv[1], sys.argv[2]
with open(log_file, "rb") as src, open(output_file, "w", encoding="utf-8") as out:
    for raw in src:
        line = raw.replace(b"\x00", b"").decode("utf-8", "ignore").strip()
        if '"event_type":"ALONGSIDE_CANDIDATE"' not in line:
            continue
        start = line.find("{")
        out.write(line[start:] if start >= 0 else line)
        out.write("\n")
PY
}

consume_live_event_output() {
  require_cmd "$KAFKA_CONSUMER_BIN"
  mkdir -p "$ROOT_DIR/target"
  local kafka_output="$ROOT_DIR/target/alongside-live-event-kafka-output.jsonl"
  rm -f "$kafka_output"
  set +e
  kafka_env "$KAFKA_CONSUMER_BIN" \
    --bootstrap-server "$KAFKA_BOOTSTRAP" \
    --topic "$ALONGSIDE_TOPIC" \
    --from-beginning \
    --timeout-ms 20000 \
    --max-messages 5 > "$kafka_output"
  local code=$?
  set -e
  if [ "$code" -ne 0 ] && [ ! -s "$kafka_output" ]; then
    echo "No alongside event consumed from $ALONGSIDE_TOPIC within timeout." >&2
    exit 1
  fi
  grep -q '"event_type":"ALONGSIDE_CANDIDATE"' "$kafka_output"
  echo "Kafka alongside event output: $kafka_output"
}

replay() {
  if [ ! -f "$JOB_JAR" ]; then
    quick
  fi
  kafka_env java \
    -Dkafka.bootstrap.servers="$KAFKA_BOOTSTRAP" \
    -Dreplay.input.dir="${REPLAY_INPUT_DIR:-data/ais-history}" \
    -Dreplay.target.topic="$AIS_TOPIC" \
    -Dreplay.start-time="${REPLAY_START_TIME:-2026-06-29 00:00:00}" \
    -Dreplay.end-time="${REPLAY_END_TIME:-2026-06-29 02:00:00}" \
    -Dreplay.max-records-per-second="${REPLAY_MAX_RECORDS_PER_SECOND:-200}" \
    -cp "$JOB_JAR" \
    com.hifleet.analysis.portcall.history.AisReplayApplication
}

app_jvm_args() {
  printf '%s' "-Dkafka.bootstrap.servers=$KAFKA_BOOTSTRAP \
-Dkafka.group.id=$GROUP_ID \
-Dkafka.client.id=$CLIENT_ID \
-Dkafka.topic=$AIS_TOPIC \
-Dkafka.starting-offsets=$KAFKA_STARTING_OFFSETS \
-Dkafka.stop-point.topic=$STOP_TOPIC \
-Dkafka.alongside.topic=$ALONGSIDE_TOPIC \
-Dalongside.enabled=$ALONGSIDE_ENABLED \
-Dalongside.output.topic=$ALONGSIDE_TOPIC \
-Dpipeline.reorder.max-lateness-seconds=$LOCAL_REORDER_LATENESS_SECONDS \
-Dpipeline.scope.mmsi-whitelist=${SCOPE_MMSI_WHITELIST:-} \
-Dpipeline.scope.min-lon=${SCOPE_MIN_LON:-} \
-Dpipeline.scope.max-lon=${SCOPE_MAX_LON:-} \
-Dpipeline.scope.min-lat=${SCOPE_MIN_LAT:-} \
-Dpipeline.scope.max-lat=${SCOPE_MAX_LAT:-} \
-Dflink.parallelism=1 \
-Dflink.state.backend=$LOCAL_STATE_BACKEND \
-Dflink.checkpoint.interval-ms=$LOCAL_CHECKPOINT_INTERVAL_MS \
-Dflink.checkpoint.min-pause-ms=$LOCAL_CHECKPOINT_MIN_PAUSE_MS \
-Dflink.checkpoint.dir=file:///tmp/flink-alongside/checkpoints \
-Dflink.savepoint.dir=file:///tmp/flink-alongside/savepoints \
-Dflink.rocksdb.localdir=/tmp/flink-alongside/rocksdb \
-Dflink.debug.print=true \
-Dalongside.db-sink.enabled=${ALONGSIDE_DB_SINK_ENABLED:-false} \
-Dalongside.db-sink.url=${ALONGSIDE_DB_URL:-} \
-Dalongside.db-sink.username=${ALONGSIDE_DB_USERNAME:-} \
-Dalongside.db-sink.password=${ALONGSIDE_DB_PASSWORD:-} \
-Dalongside.db-sink.detail-enabled=${ALONGSIDE_DB_DETAIL_ENABLED:-true} \
-Dalongside.db-sink.batch-size=${ALONGSIDE_DB_BATCH_SIZE:-100} \
-Dalongside.db-sink.flush-interval-ms=${ALONGSIDE_DB_FLUSH_INTERVAL_MS:-3000} \
-Dalongside.db-sink.table-event=${ALONGSIDE_DB_TABLE_EVENT:-ship_realtime_alongside_event} \
-Dalongside.db-sink.table-detail=${ALONGSIDE_DB_TABLE_DETAIL:-ship_realtime_alongside_event_detail} \
-Dalongside.db-sink.max-detail-points-per-event=${ALONGSIDE_DB_MAX_DETAIL_POINTS_PER_EVENT:-200}$(optional_alongside_rule_args)"
}

optional_alongside_rule_args() {
  optional_prop "alongside.candidate.min-duration-minutes" "${LOCAL_ALONGSIDE_CANDIDATE_MIN_DURATION_MINUTES:-}"
  optional_prop "alongside.candidate.min-points" "${LOCAL_ALONGSIDE_CANDIDATE_MIN_POINTS:-}"
  optional_prop "alongside.confirm.min-duration-minutes" "${LOCAL_ALONGSIDE_CONFIRM_MIN_DURATION_MINUTES:-}"
  optional_prop "alongside.confirm.min-points" "${LOCAL_ALONGSIDE_CONFIRM_MIN_POINTS:-}"
}

optional_prop() {
  local key="$1"
  local value="$2"
  if [ -n "$value" ]; then
    printf ' -D%s=%s' "$key" "$value"
  fi
}

kafka_env() {
  JAVA_HOME="$KAFKA_JAVA_HOME" PATH="$KAFKA_JAVA_HOME/bin:$PATH" "$@"
}

flink_env() {
  JAVA_HOME="$FLINK_JAVA_HOME" PATH="$FLINK_JAVA_HOME/bin:$PATH" JVM_ARGS="$(app_jvm_args)" "$@"
}

case "$MODE" in
  quick)
    quick
    ;;
  e2e)
    e2e
    ;;
  e2e-db)
    e2e_db
    ;;
  e2e-disabled)
    e2e_disabled
    ;;
  e2e-disabled-db)
    e2e_disabled_db
    ;;
  live)
    live
    ;;
  live-event)
    live_event
    ;;
  replay)
    replay
    ;;
  -h|--help|help)
    usage
    ;;
  *)
    usage
    exit 1
    ;;
esac
