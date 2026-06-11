#!/bin/bash

BASE_DIR="$(cd "$(dirname "$0")" && pwd)"

"${BASE_DIR}/stop.sh"
sleep 2
"${BASE_DIR}/start.sh"
