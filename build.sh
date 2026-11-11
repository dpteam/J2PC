#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# J2PC build script for Linux and macOS.
# Requires a JDK 17+ on the PATH; no Maven or Gradle needed.
# ---------------------------------------------------------------------------
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT"

CLASSES="build/classes"
DIST="dist"

mkdir -p "$CLASSES" "$DIST"

echo "[1/3] Compiling sources..."
find src -name '*.java' > build/sources.txt
javac -encoding UTF-8 -d "$CLASSES" @build/sources.txt

echo "[2/3] Creating manifest..."
printf 'Main-Class: j2pc.Main\n' > build/manifest.txt

echo "[3/3] Packaging $DIST/j2pc.jar..."
jar cfm "$DIST/j2pc.jar" build/manifest.txt -C "$CLASSES" .

echo
echo "Build finished: $DIST/j2pc.jar"
echo "Try: java -jar $DIST/j2pc.jar --help"
