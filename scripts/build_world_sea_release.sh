#!/usr/bin/env bash
set -euo pipefail

expected_branch="${WORLD_SEA_RELEASE_BRANCH:-codex/world-sea-mask-v1-hardening}"
remote_name="${WORLD_SEA_RELEASE_REMOTE:-github}"

verify_source() {
  local source_repo="$1"
  local source_remote="$2"
  local source_branch="$3"
  git -C "$source_repo" rev-parse --is-inside-work-tree >/dev/null
  if [ -n "$(git -C "$source_repo" status --porcelain)" ]; then
    echo "WORLD_SEA_RELEASE rejected: source worktree is dirty" >&2
    return 20
  fi
  git -C "$source_repo" fetch "$source_remote" "$source_branch" >/dev/null
  local_sha="$(git -C "$source_repo" rev-parse HEAD)"
  remote_sha="$(git -C "$source_repo" rev-parse "$source_remote/$source_branch")"
  if [ "$local_sha" != "$remote_sha" ]; then
    echo "WORLD_SEA_RELEASE rejected: local HEAD differs from remote branch" >&2
    return 21
  fi
  if git -C "$source_repo" ls-files --error-unmatch world_sea.sql >/dev/null 2>&1; then
    echo "WORLD_SEA_RELEASE rejected: world_sea.sql is tracked" >&2
    return 22
  fi
  printf 'branch=%s\ngitCommit=%s\nremoteGitCommit=%s\ngitDirty=false\n' \
    "$source_branch" "$local_sha" "$remote_sha"
}

if [ "${1:-}" = "--verify-source-only" ]; then
  [ "$#" -eq 4 ] || { echo "usage: $0 --verify-source-only REPO REMOTE BRANCH" >&2; exit 2; }
  verify_source "$2" "$3" "$4"
  exit 0
fi

repo_root="$(git rev-parse --show-toplevel)"
[ "$PWD" = "$repo_root" ] || { echo "run from repository root" >&2; exit 2; }
source_state="$(verify_source "$repo_root" "$remote_name" "$expected_branch")"
release_sha="$(git rev-parse HEAD)"
remote_sha="$(git rev-parse "$remote_name/$expected_branch")"

build_parent="$(mktemp -d "${TMPDIR:-/tmp}/world-sea-release-worktree.XXXXXX")"
build_worktree="$build_parent/source"
staging_dir=""
cleanup() {
  git -C "$repo_root" worktree remove --force "$build_worktree" >/dev/null 2>&1 || true
  [ ! -d "$build_parent" ] || rm -r "$build_parent"
  [ -z "$staging_dir" ] || [ ! -d "$staging_dir" ] || rm -r "$staging_dir"
}
trap cleanup EXIT
git -C "$repo_root" worktree add --detach "$build_worktree" "$release_sha" >/dev/null
[ -z "$(git -C "$build_worktree" status --porcelain)" ] || { echo "detached build worktree is dirty" >&2; exit 23; }

test_log="$(mktemp "${TMPDIR:-/tmp}/world-sea-test.XXXXXX")"
package_log="$(mktemp "${TMPDIR:-/tmp}/world-sea-package.XXXXXX")"
trap 'rm -f "$test_log" "$package_log"; cleanup' EXIT

(cd "$build_worktree" && mvn clean test) | tee "$test_log"
test_counts="$(awk -F'[:,]' '/Tests run:/{run+=$2; failures+=$4; errors+=$6; skipped+=$8} END{printf "%d %d %d %d",run,failures,errors,skipped}' \
  "$build_worktree"/target/surefire-reports/*.txt)"
read -r tests_run test_failures test_errors test_skipped <<<"$test_counts"
[ "$test_failures" -eq 0 ] && [ "$test_errors" -eq 0 ] || { echo "tests failed" >&2; exit 24; }

(cd "$build_worktree" && mvn clean package -DskipTests) | tee "$package_log"
[ -z "$(git -C "$build_worktree" status --porcelain)" ] || { echo "build changed tracked source" >&2; exit 25; }
jar_source="$build_worktree/target/ship-risk-data-0.0.1-SNAPSHOT.jar"
[ -f "$jar_source" ] || { echo "release JAR missing" >&2; exit 26; }

output_dir="${WORLD_SEA_RELEASE_OUTPUT_DIR:-$repo_root/dist/world-sea-mask-v1-hardening}"
[ ! -e "$output_dir" ] || { echo "release output already exists: $output_dir" >&2; exit 27; }
mkdir -p "$(dirname "$output_dir")"
staging_dir="$(mktemp -d "$(dirname "$output_dir")/.world-sea-mask-v1-hardening.XXXXXX")"
cp "$jar_source" "$staging_dir/ship-risk-data-0.0.1-SNAPSHOT.jar"
for artifact in README.md application.properties.delta migration.sql query.sql preflight.sql \
  rollback.md release-state-machine.md failure-recovery-matrix.md xxl-job-params.json; do
  cp "$build_worktree/deploy/world-sea-mask-v1/$artifact" "$staging_dir/$artifact"
done
jar_sha="$(shasum -a 256 "$staging_dir/ship-risk-data-0.0.1-SNAPSHOT.jar" | awk '{print $1}')"
printf '%s  %s\n' "$jar_sha" ship-risk-data-0.0.1-SNAPSHOT.jar \
  >"$staging_dir/ship-risk-data-0.0.1-SNAPSHOT.jar.sha256"
printf '%s\n' "$source_state" >"$staging_dir/source-state.txt"
printf 'mvn clean test\nTests run: %s, Failures: %s, Errors: %s, Skipped: %s\n\nmvn clean package -DskipTests\nBUILD SUCCESS\n' \
  "$tests_run" "$test_failures" "$test_errors" "$test_skipped" >"$staging_dir/test-summary.txt"

java_version="$(java -version 2>&1 | sed -n '1p' | sed 's/"/\\"/g')"
maven_version="$(mvn -version 2>/dev/null | sed -n '1p' | sed $'s/\033\[[0-9;]*m//g; s/"/\\"/g')"
build_time="$(date -u '+%Y-%m-%dT%H:%M:%SZ')"
cat >"$staging_dir/release-manifest.json" <<EOF
{
  "repository": "lr12338/ship-risk-data",
  "branch": "$expected_branch",
  "gitCommit": "$release_sha",
  "remoteGitCommit": "$remote_sha",
  "gitDirty": false,
  "buildWorktree": "detached-clean-worktree",
  "javaVersion": "$java_version",
  "mavenVersion": "$maven_version",
  "jarSha256": "$jar_sha",
  "testsRun": $tests_run,
  "testFailures": $test_failures,
  "testErrors": $test_errors,
  "testSkipped": $test_skipped,
  "datasetVersion": "WORLD_SEA_102_V1",
  "datasetStatus": "BLOCKED_INVALID_GEOMETRY",
  "sourceFileSha256": "de7d39f8e652721796d64ce8697bb036da30280500940de8ece70ece4914266b",
  "stageGeometryFingerprint": "81c810a9db7d319fdfbf9a17805a61493d3beda23b09154958ce2a2b3a31fd1e",
  "buildTimeUtc": "$build_time"
}
EOF

grep -q '"gitDirty": false' "$staging_dir/release-manifest.json"
grep -q "\"gitCommit\": \"$release_sha\"" "$staging_dir/release-manifest.json"
(cd "$staging_dir" && shasum -a 256 -c ship-risk-data-0.0.1-SNAPSHOT.jar.sha256)
mv "$staging_dir" "$output_dir"
staging_dir=""
echo "WORLD_SEA_RELEASE PASS output=$output_dir gitCommit=$release_sha jarSha256=$jar_sha testsRun=$tests_run"
