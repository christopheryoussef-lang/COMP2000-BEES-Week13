#!/usr/bin/env bash
set -euo pipefail
cd -- "$(dirname -- "$0")"
bash ./build.sh
exec java -cp build/classes App "$@"
