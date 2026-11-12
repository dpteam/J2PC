#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# Self test: builds a synthetic MIDlet suite, recompiles it with J2PC, compiles
# the generated sources and packages them into a runnable JAR. No third-party
# JARs are involved, so this runs on a clean checkout and is what CI executes.
#
# Usage: ./tools/selftest.sh
# ---------------------------------------------------------------------------
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

JAR="dist/j2pc.jar"
WORK="build/selftest"

if [ ! -f "$JAR" ]; then
    echo "Building j2pc first..."
    ./build.sh
fi

echo "[1/6] Compiling the synthetic suite..."
rm -rf "$WORK"
mkdir -p "$WORK/classes" "$WORK/out" "$WORK/gen"
find src/test -name '*.java' > "$WORK/sources.txt"
javac -encoding UTF-8 -nowarn -cp "$JAR" -d "$WORK/classes" @"$WORK/sources.txt"

echo "[2/6] Packaging it as a JAR..."
( cd "$WORK/classes" && jar cf "../synthetic.jar" . )
cp src/test/synthetic.jad "$WORK/synthetic.jad"

echo "[3/6] Recompiling..."
java -jar "$JAR" -o "$WORK/out" "$WORK/synthetic.jar" "$WORK/synthetic.jad"

echo "[4/6] Compiling the generated sources..."
find "$WORK/out" -name '*.java' > "$WORK/gen.txt"
javac -encoding UTF-8 -nowarn -cp "$JAR" -d "$WORK/gen" @"$WORK/gen.txt"

echo "[5/6] Checking the launcher..."
if [ ! -f "$WORK/gen/J2PCLauncher.class" ]; then
    echo "FAILED: J2PCLauncher was not generated"
    exit 1
fi
if grep -q 'Path.of("E:' "$WORK/out/J2PCLauncher.java" 2>/dev/null; then
    echo "FAILED: the launcher contains an absolute path"
    exit 1
fi

echo "[6/6] Packaging a runnable JAR..."
java -jar "$JAR" --build -o "$WORK/pkg" "$WORK/synthetic.jar" "$WORK/synthetic.jad"

APPJAR="$(find "$WORK/pkg" -maxdepth 1 -name '*.jar' | head -n 1)"
if [ -z "$APPJAR" ]; then
    echo "FAILED: no JAR was packaged"
    exit 1
fi

# A self-contained archive needs the entry point and the emulation runtime.
for entry in J2PCLauncher.class javax/microedition/midlet/MIDlet.class j2pc/rt/; do
    if ! jar tf "$APPJAR" | grep -qF "$entry"; then
        echo "FAILED: the packaged JAR is missing $entry"
        exit 1
    fi
done

echo
echo "Self test passed:"
echo "  classes   $(find "$WORK/out" -name '*.java' | wc -l) generated"
echo "  output    $WORK/out"
echo "  classes   $WORK/gen"
echo "  package   $APPJAR"
