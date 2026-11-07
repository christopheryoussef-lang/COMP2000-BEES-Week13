#!/usr/bin/env bash
set -euo pipefail
cd -- "$(dirname -- "$0")"
if ! command -v javac >/dev/null 2>&1; then
    echo "A JDK 17 or newer is required. Install a JDK and put javac on PATH." >&2
    exit 1
fi
mkdir -p build/classes
find src -name '*.java' -print | sort > build/sources.txt
javac --release 17 -encoding UTF-8 -proc:none -Xlint:all -Werror -cp '' -d build/classes @build/sources.txt
echo "Build passed (Java 17)." >&2
