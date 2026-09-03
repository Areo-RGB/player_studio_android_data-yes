#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PACKAGE_NAME="com.aistudio.playlistplayer.wvkjzn"
APK_PATH="$ROOT_DIR/app/build/outputs/apk/debug/app-debug.apk"
TABLET_MODEL="${TABLET_MODEL:-2410CRP4CG}"

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
    printf 'Usage: %s [--help]\n' "${BASH_SOURCE[0]}"
    printf 'Builds, installs, and launches the debug APK on the connected Xiaomi Pad 7.\n'
    printf 'Set ADB_SERIAL to select a specific device or TABLET_MODEL to override detection.\n'
    exit 0
fi

if ! command -v adb >/dev/null 2>&1; then
    printf 'Error: adb is not installed or not on PATH.\n' >&2
    exit 1
fi

printf 'Building debug APK...\n'
"$ROOT_DIR/gradlew" -p "$ROOT_DIR" :app:assembleDebug

if [[ ! -f "$APK_PATH" ]]; then
    printf 'Error: APK was not produced at %s\n' "$APK_PATH" >&2
    exit 1
fi

if [[ -n "${ADB_SERIAL:-}" ]]; then
    DEVICE="$ADB_SERIAL"
else
    mapfile -t CONNECTED_DEVICES < <(adb devices | awk '$2 == "device" { print $1 }')
    if [[ "${#CONNECTED_DEVICES[@]}" -eq 0 ]]; then
        printf 'Error: no authorized Android device is connected.\n' >&2
        exit 1
    fi

    if [[ "${#CONNECTED_DEVICES[@]}" -eq 1 ]]; then
        DEVICE="${CONNECTED_DEVICES[0]}"
    else
        mapfile -t TABLET_DEVICES < <(
            adb devices -l | awk -v model="$TABLET_MODEL" \
                '$2 == "device" && index($0, "model:" model) { print $1 }'
        )
        if [[ "${#TABLET_DEVICES[@]}" -ne 1 ]]; then
            printf 'Error: could not identify exactly one tablet.\n' >&2
            printf 'Set ADB_SERIAL to choose the Xiaomi Pad 7 explicitly.\n' >&2
            printf 'Connected devices:\n' >&2
            adb devices -l >&2
            exit 1
        fi
        DEVICE="${TABLET_DEVICES[0]}"
    fi
fi

printf 'Installing on %s...\n' "$DEVICE"
adb -s "$DEVICE" shell am force-stop "$PACKAGE_NAME"
if ! adb -s "$DEVICE" install -r "$APK_PATH"; then
    printf 'Install failed (likely signature mismatch). Uninstalling old version and reinstalling...\n'
    adb -s "$DEVICE" uninstall "$PACKAGE_NAME" || true
    adb -s "$DEVICE" install "$APK_PATH"
fi

printf 'Launching %s...\n' "$PACKAGE_NAME"
adb -s "$DEVICE" shell monkey \
    -p "$PACKAGE_NAME" \
    -c android.intent.category.LAUNCHER \
    1 >/dev/null

printf 'App installed and launched on %s.\n' "$DEVICE"
