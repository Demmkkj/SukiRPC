#!/usr/bin/env bash
set -euo pipefail
output="$1"
trace="/data/misc/perfetto-traces/sukirpc-quality-$$.perfetto-trace"
trap 'adb shell rm -f "$trace" >/dev/null 2>&1 || true' EXIT
adb shell am force-stop dev.nuvio.presence.benchmark
adb shell perfetto --txt -c - -o "$trace" <quality/perfetto.pbtxt >"$output/capture.log" 2>&1 &
recorder=$!
sleep 1
adb shell am start -W -n dev.nuvio.presence.benchmark/dev.nuvio.presence.MainActivity
wait "$recorder"
adb pull "$trace" "$output/genesis-startup.perfetto-trace"
test -s "$output/genesis-startup.perfetto-trace"
