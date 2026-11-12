#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# J2PC test runner for Linux and macOS.
#
# Recompiles every JAR in the sample folder, compiles the generated Java against
# the J2PC runtime and reports the result.
#
# Usage: ./test.sh [path-to-folder-with-jars]
# ---------------------------------------------------------------------------
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT"

SAMPLES="${1:-$ROOT/samples}"
JAR="dist/j2pc.jar"
WORK="build/test"

if [ ! -f "$JAR" ]; then
    echo "Building first..."
    ./build.sh
fi

if [ ! -d "$SAMPLES" ]; then
    echo "No sample folder at $SAMPLES"
    exit 1
fi

mkdir -p "$WORK"

passed=0
failed=0
skipped=0

shopt -s nullglob
for f in "$SAMPLES"/*.jar; do
    name="$(basename "$f" .jar)"
    out="$WORK/$name"
    echo
    echo "=== $name ==="

    rm -rf "$out"
    if ! java -jar "$JAR" -q -o "$out" "$f"; then
        echo "  [FAIL] recompilation"
        failed=$((failed + 1))
        continue
    fi

    mapfile -t sources < <(find "$out" -name '*.java' 2>/dev/null)
    if [ "${#sources[@]}" -eq 0 ]; then
        echo "  [SKIP] no classes"
        skipped=$((skipped + 1))
        continue
    fi

    printf '%s\n' "${sources[@]}" > "$WORK/gen.txt"
    rm -rf "$WORK/classes/$name"
    mkdir -p "$WORK/classes/$name"
    if javac -encoding UTF-8 -nowarn -cp "$JAR" -d "$WORK/classes/$name" @"$WORK/gen.txt" \
            > "$WORK/javac-$name.log" 2>&1; then
        echo "  [ OK ] recompiled and compiled"
        passed=$((passed + 1))
    else
        echo "  [FAIL] generated code does not compile - see $WORK/javac-$name.log"
        failed=$((failed + 1))
    fi
done

echo
echo "Passed: $passed  Failed: $failed  Skipped: $skipped"
[ "$failed" -eq 0 ]
