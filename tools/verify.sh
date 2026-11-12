#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# Behavioural equivalence check.
#
# Builds the synthetic suite, runs the Verifier against the ORIGINAL classes,
# recompiles the suite with J2PC, runs the same Verifier against the RECOMPILED
# classes, and diffs the two outputs. A difference means the translation changed
# behaviour.
#
# Usage: ./tools/verify.sh
# ---------------------------------------------------------------------------
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

JAR="dist/j2pc.jar"
WORK="build/verify"
CLASSES="synthetic.Arithmetic,synthetic.ControlFlow,synthetic.Exceptions,synthetic.Objects,synthetic.Modern,synthetic.Hierarchy"

if [ ! -f "$JAR" ]; then
    ./build.sh
fi

echo "[1/5] Compiling the synthetic suite..."
rm -rf "$WORK"
mkdir -p "$WORK/original" "$WORK/out" "$WORK/gen"
find src/test -name '*.java' > "$WORK/sources.txt"
javac -encoding UTF-8 -nowarn -cp "$JAR" -d "$WORK/original" @"$WORK/sources.txt"

echo "[2/5] Packaging and recompiling..."
( cd "$WORK/original" && jar cf "../synthetic.jar" . )
java -jar "$JAR" -q -o "$WORK/out" "$WORK/synthetic.jar"

echo "[3/5] Compiling the generated sources..."
find "$WORK/out" -name '*.java' > "$WORK/gen.txt"
javac -encoding UTF-8 -nowarn -cp "$JAR" -d "$WORK/gen" @"$WORK/gen.txt"

echo "[4/5] Running the verifier against both versions..."
java -cp "$WORK/original:$JAR" j2pc.tools.Verifier --classes="$CLASSES" \
    > "$WORK/original.txt" 2>&1 || true
java -cp "$WORK/gen:$JAR" j2pc.tools.Verifier --classes="$CLASSES" \
    > "$WORK/recompiled.txt" 2>&1 || true

echo "[5/5] Comparing..."
if grep -q "Could not find or load main class" "$WORK/original.txt"; then
    echo "VERIFIER DID NOT RUN - see $WORK/original.txt"
    exit 1
fi
# A comparison of two empty outputs would trivially "pass".
results=$(grep -c '  .* = ' "$WORK/original.txt" || true)
if [ "${results:-0}" -lt 60 ]; then
    echo "TOO FEW RESULTS ($results) - the verifier did not exercise the suite"
    exit 1
fi
if grep -qE '<not loadable|<cannot instantiate' "$WORK/original.txt"; then
    echo "SOME CLASSES WERE NOT LOADABLE - see $WORK/original.txt"
    exit 1
fi
if diff -u "$WORK/original.txt" "$WORK/recompiled.txt" > "$WORK/diff.txt"; then
    echo
    echo "Identical behaviour ($(grep -c '=' "$WORK/original.txt") results compared)"
    exit 0
fi

echo
echo "DIFFERENCES FOUND:"
head -60 "$WORK/diff.txt"
echo
echo "Full diff: $WORK/diff.txt"
exit 1
