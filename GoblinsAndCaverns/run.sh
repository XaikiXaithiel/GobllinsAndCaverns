#!/bin/sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
BUILD_DIR="$ROOT/.build"

if ! command -v javac >/dev/null 2>&1 || ! command -v java >/dev/null 2>&1; then
    echo "A Java JDK is required. Install one and run this script again." >&2
    exit 1
fi

mkdir -p "$BUILD_DIR"
cd "$ROOT"
javac -encoding UTF-8 -d "$BUILD_DIR" GoblinDoorsGUI.java GameAudio.java GameAssets.java
exec java -cp "$BUILD_DIR" GoblinDoorsGUI
