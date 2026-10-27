#!/bin/sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
BUILD_DIR="$ROOT/.build"
DEPS_DIR="$ROOT/.deps"
JNA_JAR="$DEPS_DIR/jna-5.14.0.jar"

if ! command -v javac >/dev/null 2>&1 || ! command -v java >/dev/null 2>&1; then
    echo "A Java JDK is required. Install one and run this script again." >&2
    exit 1
fi

mkdir -p "$BUILD_DIR"
mkdir -p "$DEPS_DIR"
cd "$ROOT"
if [ ! -f "$JNA_JAR" ]; then
    if ! command -v curl >/dev/null 2>&1; then
        echo "curl is required to download JNA for the first run." >&2
        exit 1
    fi
    echo "Downloading JNA for XInput controller support..."
    if ! curl -fL --retry 3 -o "$JNA_JAR" \
        "https://repo.maven.apache.org/maven2/net/java/dev/jna/jna/5.14.0/jna-5.14.0.jar"; then
        rm -f "$JNA_JAR"
        echo "Could not download JNA. Check your internet connection and try again." >&2
        exit 1
    fi
fi
javac -encoding UTF-8 -cp "$JNA_JAR" -d "$BUILD_DIR" GoblinDoorsGUI.java GameAudio.java GameAssets.java XInputController.java
exec java -cp "$BUILD_DIR:$JNA_JAR" GoblinDoorsGUI
