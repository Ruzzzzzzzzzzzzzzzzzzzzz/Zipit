#!/usr/bin/env bash
set -e
ROOT="$(cd "$(dirname "$0")" && pwd)"
JAR="$ROOT/dist/zipit-license-server-1.0.0-all.jar"

if [ ! -f "$JAR" ]; then
  echo "License server jar not found: $JAR" >&2
  echo "Run: ./build.sh build dist" >&2
  exit 1
fi

exec java -jar "$JAR" --data "$ROOT/license-data" --open "$@"
