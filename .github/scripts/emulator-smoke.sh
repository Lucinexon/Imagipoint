#!/usr/bin/env bash
# Wear OS emulator smoke pipeline for ImagiPoint.
# Expects the debug APK and androidTest APK paths as arguments (defaults: artifacts/).
set -euo pipefail

APK_PATH="${1:-artifacts/app-debug.apk}"
TEST_APK_PATH="${2:-artifacts/app-debug-androidTest.apk}"
APP_ID="${APP_ID:-com.imagipoint.watch.debug}"
TEST_PACKAGE="${TEST_PACKAGE:-com.imagipoint.watch.debug.test}"
TEST_RUNNER="${TEST_RUNNER:-androidx.test.runner.AndroidJUnitRunner}"
LAUNCH_ACTIVITY="${LAUNCH_ACTIVITY:-com.imagipoint.watch.MainActivity}"
OUT_DIR="${OUT_DIR:-emulator-artifacts}"
ADB="${ADB:-adb}"
UI_XML="$OUT_DIR/ui.xml"

mkdir -p "$OUT_DIR/screenshots"

dump_ui() {
    for _ in 1 2 3 4 5; do
        if "$ADB" shell uiautomator dump /sdcard/window.xml >/dev/null 2>&1 &&
            "$ADB" pull /sdcard/window.xml "$UI_XML" >/dev/null 2>&1; then
            return 0
        fi
        sleep 1
    done
    return 1
}

ui_has_text() {
    dump_ui && python3 - "$UI_XML" "$1" <<'PY'
import sys
import xml.etree.ElementTree as ET
root = ET.parse(sys.argv[1]).getroot()
value = sys.argv[2]
sys.exit(0 if any(n.get("text") == value for n in root.iter("node")) else 1)
PY
}

ui_center() {
    python3 - "$UI_XML" "$1" "$2" <<'PY'
import re
import sys
import xml.etree.ElementTree as ET
root = ET.parse(sys.argv[1]).getroot()
attribute, value = sys.argv[2], sys.argv[3]
for node in root.iter("node"):
    if node.get(attribute) != value:
        continue
    match = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.get("bounds", ""))
    if not match:
        continue
    x1, y1, x2, y2 = map(int, match.groups())
    if x2 > x1 and y2 > y1:
        print((x1 + x2) // 2, (y1 + y2) // 2)
        sys.exit(0)
sys.exit(1)
PY
}

tap_text() {
    local coords
    dump_ui
    coords="$(ui_center text "$1")"
    # shellcheck disable=SC2086
    "$ADB" shell input tap $coords
}

tap_desc() {
    local coords
    dump_ui
    coords="$(ui_center content-desc "$1")"
    # shellcheck disable=SC2086
    "$ADB" shell input tap $coords
}

wait_for_text() {
    for _ in 1 2 3 4 5 6 7 8 9 10; do
        if ui_has_text "$1"; then return 0; fi
        sleep 1
    done
    echo "Timed out waiting for text: $1" >&2
    return 1
}

screenshot() {
    "$ADB" exec-out screencap -p > "$OUT_DIR/screenshots/$1"
    python3 - "$OUT_DIR/screenshots/$1" <<'PY'
import struct
import sys
with open(sys.argv[1], "rb") as f:
    header = f.read(24)
width, height = struct.unpack(">II", header[16:24])
if width != height:
    raise SystemExit(f"Screenshot is not square: {width}x{height}")
print(f"Screenshot {sys.argv[1]}: {width}x{height}")
PY
}

run_smoke() {
    "$ADB" shell am force-stop "$APP_ID"
    "$ADB" shell am start -W -n "$APP_ID/$LAUNCH_ACTIVITY"
    wait_for_text "IMAGIPOINT"
    screenshot "01-home.png"

    tap_desc "Settings"
    wait_for_text "Sensitivity"
    screenshot "02-settings.png"
    "$ADB" shell input swipe 227 370 227 110 400
    sleep 1
    screenshot "03-settings-scrolled.png"
    "$ADB" shell input keyevent 4
    wait_for_text "IMAGIPOINT"

    tap_desc "Calibration"
    wait_for_text "Neutral posture"
    screenshot "04-calibration.png"
    "$ADB" shell input keyevent 4
    wait_for_text "IMAGIPOINT"

    tap_text "Set up"
    wait_for_text "Connection"
    screenshot "05-connection.png"
    "$ADB" shell input keyevent 4
    wait_for_text "IMAGIPOINT"
}

echo "== Connected device =="
"$ADB" devices
"$ADB" shell wm size | tee "$OUT_DIR/device-size.txt"
"$ADB" shell wm density | tee -a "$OUT_DIR/device-size.txt"
"$ADB" shell getprop ro.build.version.sdk | tee -a "$OUT_DIR/device-size.txt"
"$ADB" shell getprop ro.build.version.release | tee -a "$OUT_DIR/device-size.txt"
grep -q "454x454" "$OUT_DIR/device-size.txt"
grep -q "320" "$OUT_DIR/device-size.txt"
# Fresh GitHub runner AVDs may start with the screen off or keyguard showing.
"$ADB" shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1 || true
"$ADB" shell wm dismiss-keyguard >/dev/null 2>&1 || true
"$ADB" shell svc power stayon true >/dev/null 2>&1 || true
"$ADB" shell settings put system screen_off_timeout 2147483647 >/dev/null 2>&1 || true

echo "== Install APKs =="
"$ADB" install -r "$APK_PATH"
"$ADB" shell pm clear "$APP_ID" >/dev/null
"$ADB" install -r "$TEST_APK_PATH"
for permission in BLUETOOTH_CONNECT BLUETOOTH_ADVERTISE POST_NOTIFICATIONS; do
    "$ADB" shell pm grant "$APP_ID" "android.permission.$permission" >/dev/null 2>&1 || true
done

echo "== Instrumentation tests =="
"$ADB" logcat -c
set +e
"$ADB" shell am instrument -w "$TEST_PACKAGE/$TEST_RUNNER" 2>&1 | tee "$OUT_DIR/instrumentation.txt"
INSTRUMENTATION_EXIT=${PIPESTATUS[0]}
set -e
"$ADB" logcat -d -v threadtime -t 5000 > "$OUT_DIR/logcat-instrumentation.txt" || true

INSTRUMENTATION_OK=1
if [ "$INSTRUMENTATION_EXIT" -eq 0 ] &&
    grep -qE "^OK \([1-9][0-9]* tests?\)" "$OUT_DIR/instrumentation.txt" &&
    ! grep -qE "FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed" "$OUT_DIR/instrumentation.txt"; then
    INSTRUMENTATION_OK=0
fi

echo "== UI smoke and screenshots =="
"$ADB" logcat -c
SMOKE_OK=1
if run_smoke; then
    SMOKE_OK=0
fi
"$ADB" logcat -d -v threadtime -t 5000 > "$OUT_DIR/logcat-smoke.txt" || true

CRASH_OK=1
if ! grep -q "FATAL EXCEPTION" "$OUT_DIR/logcat-smoke.txt"; then
    CRASH_OK=0
elif ! grep -A25 "FATAL EXCEPTION" "$OUT_DIR/logcat-smoke.txt" | grep -q "com.imagipoint"; then
    echo "FATAL EXCEPTION found, but not from ImagiPoint; preserving logcat." >&2
    CRASH_OK=0
fi

if [ "$INSTRUMENTATION_OK" -ne 0 ]; then
    echo "Instrumentation tests failed. See $OUT_DIR/instrumentation.txt" >&2
fi
if [ "$SMOKE_OK" -ne 0 ]; then
    echo "UI smoke navigation or screenshots failed. See $OUT_DIR/." >&2
fi
if [ "$CRASH_OK" -ne 0 ]; then
    echo "ImagiPoint crashed during the smoke flow. See $OUT_DIR/logcat-smoke.txt" >&2
fi
if [ "$INSTRUMENTATION_OK" -ne 0 ] || [ "$SMOKE_OK" -ne 0 ] || [ "$CRASH_OK" -ne 0 ]; then
    exit 1
fi
echo "Emulator instrumentation and UI smoke checks passed."
