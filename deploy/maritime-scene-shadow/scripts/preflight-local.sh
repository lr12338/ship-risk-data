#!/bin/sh
set -eu
PACKAGE_ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
"$PACKAGE_ROOT/scripts/verify-package.sh"
echo "Handler: shipMaritimeScenePreflightJobHandler"
cat "$PACKAGE_ROOT/jobs/preflight-params.json"
echo "Run the Handler manually after deployment; this script does not connect to a server."
