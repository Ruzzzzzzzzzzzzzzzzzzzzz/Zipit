#!/usr/bin/env bash
# Build Zipit without requiring a system-wide Gradle installation.
set -e
ROOT="$(cd "$(dirname "$0")" && pwd)"

# The checked-in wrapper is the portable path: invoke it through bash so it
# also works when a Windows checkout loses the executable bit.
if [ -f "$ROOT/gradlew" ]; then
  exec bash "$ROOT/gradlew" -p "$ROOT" "$@"
fi

GRADLE_BIN=""
if command -v gradle >/dev/null 2>&1; then
  GRADLE_BIN="gradle"
else
  for candidate in "$HOME"/.gradle/wrapper/dists/gradle-8.14-bin/*/gradle-8.14/bin/gradle; do
    if [ -x "$candidate" ]; then
      GRADLE_BIN="$candidate"
      break
    fi
  done
fi

if [ -z "$GRADLE_BIN" ]; then
  echo "Gradle 8.x was not found. Install Gradle or add it to PATH." >&2
  exit 1
fi

exec "$GRADLE_BIN" -p "$ROOT" "$@"
