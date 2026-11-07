#!/bin/sh
# Runs the instrumented UI tests isolated from other projects on the same machine (docs/PLAN_2.0.md I1).
#
# - Uses a private adb server on port 5038 (ANDROID_ADB_SERVER_PORT), so another tool restarting or killing the
#   default adb server (5037) can't cut this run's connection (that is what broke the final 1.1.0 UI run).
# - Targets one device by serial and refuses to start if it isn't there.
# - Writes the Gradle log to build/ui-tests/<name>.log and the exit code to build/ui-tests/<name>.done, so it can run
#   detached (closing the editor doesn't lose the run).
#
# Usage:   sh tools/scripts/ui_tests.sh [serial] [test classes, comma-separated] [name]
# Example: sh tools/scripts/ui_tests.sh emulator-5554 com.arnavgames.tycoon.CrownFlowTest crown
# Detached: nohup sh tools/scripts/ui_tests.sh emulator-5554 "" all >/dev/null 2>&1 &
set -u
SERIAL="${1:-emulator-5554}"
CLASSES="${2:-}"
NAME="${3:-all}"
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
OUT="$ROOT/build/ui-tests"
mkdir -p "$OUT"
rm -f "$OUT/$NAME.done"

export ANDROID_ADB_SERVER_PORT=5038
export ANDROID_SERIAL="$SERIAL"
export JAVA_HOME="${JAVA_HOME:-/c/Program Files/Android/Android Studio/jbr}"
ADB="${LOCALAPPDATA:-$HOME/AppData/Local}/Android/Sdk/platform-tools/adb"

"$ADB" start-server >/dev/null 2>&1
if ! "$ADB" devices | grep -q "^$SERIAL[[:space:]]*device"; then
    echo "Device $SERIAL is not connected to the private adb server (port 5038). Start the emulator first." | tee "$OUT/$NAME.log"
    echo 2 > "$OUT/$NAME.done"
    exit 2
fi

ARGS=":app:connectedDebugAndroidTest -q"
[ -n "$CLASSES" ] && ARGS="$ARGS -Pandroid.testInstrumentationRunnerArguments.class=$CLASSES"
echo "UI tests on $SERIAL via adb port $ANDROID_ADB_SERVER_PORT: ${CLASSES:-all}" > "$OUT/$NAME.log"
cd "$ROOT" && ./gradlew $ARGS >> "$OUT/$NAME.log" 2>&1
CODE=$?
echo $CODE > "$OUT/$NAME.done"
exit $CODE
