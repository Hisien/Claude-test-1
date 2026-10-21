#!/usr/bin/env bash
# Compiles and tests :core WITHOUT network access, using the Kotlin compiler and JUnit 4 jars
# that ship inside a local Gradle distribution. Only needed when Maven Central is unreachable;
# the normal way is: ./gradlew :core:test
#
# Usage: tools/verify-core-offline.sh [GRADLE_HOME]   (default: /opt/gradle)
set -euo pipefail

GRADLE_HOME="${1:-/opt/gradle}"
LIB="$GRADLE_HOME/lib"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="${TMPDIR:-/tmp}/scrollmate-core-verify"
rm -rf "$OUT" && mkdir -p "$OUT/main" "$OUT/test"

jar() { find "$LIB" -maxdepth 2 -name "$1" 2>/dev/null | sort | head -1; }

KOTLIN_CP="$(jar 'kotlin-compiler-embeddable-*.jar'):$(jar 'kotlin-stdlib-[0-9]*.jar'):$(jar 'kotlin-script-runtime-*.jar'):$(jar 'kotlin-reflect-*.jar'):$(jar 'kotlin-daemon-embeddable-*.jar'):$(jar 'kotlinx-coroutines-core-jvm-*.jar'):$(jar 'annotations-*.jar'):$(jar 'trove4j-*.jar')"
STDLIB="$(jar 'kotlin-stdlib-[0-9]*.jar')"
JUNIT="$(jar 'junit-4*.jar'):$(jar 'hamcrest-core-*.jar')"

kotlinc() {
  java -Xmx1g -cp "$KOTLIN_CP" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-reflect -no-stdlib -jvm-target 17 "$@"
}

echo "> Compiling :core main"
kotlinc -classpath "$STDLIB" -d "$OUT/main" $(find "$ROOT/core/src/main/kotlin" -name '*.kt')

echo "> Compiling :core tests"
kotlinc -classpath "$STDLIB:$JUNIT:$OUT/main" -d "$OUT/test" $(find "$ROOT/core/src/test/kotlin" -name '*.kt')

CLASSES=$(cd "$OUT/test" && find . -name '*Test.class' ! -name '*$*' | sed 's|^\./||; s|\.class$||; s|/|.|g' | sort)
echo "> Running JUnit 4: $(echo "$CLASSES" | wc -l) test classes"
java -cp "$STDLIB:$JUNIT:$OUT/main:$OUT/test" org.junit.runner.JUnitCore $CLASSES
