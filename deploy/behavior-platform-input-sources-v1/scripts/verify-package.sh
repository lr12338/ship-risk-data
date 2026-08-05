#!/usr/bin/env bash
set -euo pipefail
root=$(cd "$(dirname "$0")/.." && pwd)
cd "$root"
sha256sum -c checksums.sha256
python3 - <<'PY'
import hashlib, json, pathlib
root = pathlib.Path('.')
m = json.loads((root / 'manifest.json').read_text())
assert m['formalWriteEnabled'] is False
assert m['shadowWriteEnabled'] is False
assert m['automaticDdl'] is False
assert m['databaseMigrationExecuted'] is False
jar = root / 'app/ship-risk-data-0.0.1-SNAPSHOT.jar'
assert hashlib.sha256(jar.read_bytes()).hexdigest() == m['jarSha256']
print('PASS package manifest safety and JAR SHA')
PY
