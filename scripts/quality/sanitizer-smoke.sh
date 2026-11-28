#!/usr/bin/env bash
set -euo pipefail
output="$1"
api=$(adb shell getprop ro.build.version.sdk | tr -d '\r')
abi=$(adb shell getprop ro.product.cpu.abi | tr -d '\r')
[[ "$abi" == arm64-v8a && "$api" -ge 34 ]] || {
    echo "Sanitizer runtime requires ARM64 Android 14+"
    exit 2
}
for type in hwasan ubsan; do
    app="dev.nuvio.presence.$type"
    adb install -r "app/build/outputs/apk/$type/app-$type.apk"
    adb shell am force-stop "$app"
    adb shell am start -W -n "$app/dev.nuvio.presence.MainActivity"
    pid=$(adb shell pidof -s "$app" | tr -d '\r')
    [[ "$pid" =~ ^[0-9]+$ ]]
    # Retain only matching diagnostic headers from this QA process.
    adb logcat -d --pid="$pid" | grep -E 'ERROR: HWAddressSanitizer|runtime error:' > "$output/$type.log" || true
    [[ ! -s "$output/$type.log" ]]
    echo "$type: initial activity startup completed"
done
