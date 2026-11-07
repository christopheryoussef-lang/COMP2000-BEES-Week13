#!/usr/bin/env bash
set -euo pipefail
cd -- "$(dirname -- "$0")"
bash ./build.sh
mkdir -p build/test-classes
javac --release 17 -encoding UTF-8 -proc:none -Xlint:all -Werror -cp build/classes -d build/test-classes tests/SimulationTests.java tests/Week13Tests.java
java -ea -cp 'build/classes:build/test-classes' SimulationTests
java -ea -cp 'build/classes:build/test-classes' Week13Tests
