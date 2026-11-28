# Sourced by scripts/audit.sh. Uses its resolved Gradle command and unique report directory.
quality_tools() {
    local dir="${SUKI_QUALITY_TOOLS:-$root/build/quality-tools}"
    PATH="$dir/bin:$dir/venv/bin:$PATH"
    export PATH
}

needs_tool() {
    type -P "$1" >/dev/null || { echo "NOT RUN: '$1' unavailable; see scripts/quality/install-tools.sh"; return 77; }
}

needs_device() {
    if [[ "${SUKI_QUALITY_DEVICE:-0}" != 1 ]]; then
        echo "NOT RUN: requires an isolated emulator/device and SUKI_QUALITY_DEVICE=1"
        return 77
    fi
    adb get-state | grep -qx device || { echo "ADB needs exactly one authorized device"; return 2; }
}

coverage() {
    local rc=0 project report_dir
    "${gradle_cmd[@]}" -PsukiCoverage :app:testDebugUnitTest :collector:testDebugUnitTest \
        :app:koverXmlReportDebug :collector:collectorCoverageReport --continue || rc=$?
    for project in app collector; do
        mkdir -p "$out/coverage/$project"
        report_dir=kover
        if [[ "$project" == collector ]]; then report_dir=jacoco; fi
        find "$project/build/test-results/testDebugUnitTest" -maxdepth 1 -name 'TEST-*.xml' \
            -exec cp -- {} "$out/coverage/$project/" \; 2>/dev/null || true
        find "$project/build/reports/$report_dir" -type f -name '*.xml' \
            -exec cp -- {} "$out/coverage/$project/" \; 2>/dev/null || true
        compgen -G "$out/coverage/$project/TEST-*.xml" >/dev/null || rc=2
        find "$out/coverage/$project" -type f -name '*.xml' ! -name 'TEST-*' | grep -q . || rc=2
    done
    return "$rc"
}

gitleaks() {
    needs_tool gitleaks || return $?
    command gitleaks git --redact=100 --report-format sarif --report-path "$out/gitleaks/report.sarif" .
}

semgrep() {
    needs_tool semgrep || return $?
    command semgrep scan --metrics=off --disable-version-check --error --config quality/semgrep.yml \
        --sarif --output "$out/semgrep/report.sarif" app/src collector/src shared
}

osv_scanner() {
    needs_tool osv-scanner || return $?
    "${gradle_cmd[@]}" -I "$init" "-PsukiAuditOutput=$out/osv-scanner" \
        :app:auditDependencies :collector:auditDependencies || return 2
    python3 scripts/quality/sbom.py "$out/osv-scanner" || return 2
    timeout --kill-after=15s 4m osv-scanner scan source --format sarif -L "$out/osv-scanner/runtime.cdx.json" \
        > "$out/osv-scanner/report.sarif"
}

actionlint() {
    needs_tool actionlint || return $?
    local rc=0
    timeout --kill-after=10s 2m actionlint -no-color -format '{{json .}}' > "$out/actionlint/report.json" || rc=$?
    if [[ "$rc" == 0 && ! -s "$out/actionlint/report.json" ]]; then
        printf '[]\n' > "$out/actionlint/report.json"
    fi
    return "$rc"
}

zizmor() {
    needs_tool zizmor || return $?
    command zizmor --offline --format=sarif .github/workflows > "$out/zizmor/report.sarif" || return $?
    python3 - "$out/zizmor/report.sarif" <<'PY_CHECK'
import json
import sys
with open(sys.argv[1]) as report:
    findings = sum(len(run.get("results", [])) for run in json.load(report)["runs"])
print(f"Zizmor: {findings} finding(s)")
raise SystemExit(bool(findings))
PY_CHECK
}

paparazzi() {
    local goal=verifyPaparazziDebug rc=0
    if [[ "${SUKI_RECORD_GOLDENS:-0}" == 1 ]]; then
        goal=recordPaparazziDebug
    fi
    "${gradle_cmd[@]}" -PsukiVisual ":quality:visual:$goal" || rc=$?
    if [[ -d quality/visual/build/reports/paparazzi ]]; then
        cp -R quality/visual/build/reports/paparazzi "$out/paparazzi/rendered" || rc=2
    fi
    if [[ -d quality/visual/src/test/snapshots ]]; then
        cp -R quality/visual/src/test/snapshots "$out/paparazzi/snapshots" || rc=2
    fi
    return "$rc"
}

android_ui() {
    needs_device || return $?
    local rc=0
    "${gradle_cmd[@]}" -PsukiDeviceTests "-PsukiAbis=${SUKI_QUALITY_ABI:-x86_64}" \
        :app:connectedDebugAndroidTest || rc=$?
    if [[ -d app/build/outputs/androidTest-results/connected ]]; then
        cp -R app/build/outputs/androidTest-results/connected "$out/android-ui/results" || rc=2
    fi
    return "$rc"
}

maestro() {
    needs_device || return $?
    needs_tool maestro || return $?
    # connectedDebugAndroidTest installs this same isolated QA variant; avoid reassembling when it ran.
    if ! adb shell pm path dev.nuvio.presence.quality | grep -q '^package:'; then
        "${gradle_cmd[@]}" -PsukiDeviceTests "-PsukiAbis=${SUKI_QUALITY_ABI:-x86_64}" :app:installDebug || return 2
    fi
    MAESTRO_CLI_NO_ANALYTICS=1 command maestro test --format junit --output "$out/maestro/results.xml" quality/maestro
}

sanitizers() {
    "${gradle_cmd[@]}" -PsukiSanitizers -PsukiAbis=arm64-v8a :app:assembleHwasan :app:assembleUbsan --continue ||
        return 2
    python3 scripts/quality/verify-native.py "$out/sanitizers/build.json" || return 2
    if [[ "${SUKI_QUALITY_SANITIZER_DEVICE:-0}" != 1 ]]; then
        echo "BUILT ONLY: HWASan/UBSan runtime smoke requires an ARM64 API 34+ isolated test device"
        return 0
    fi
    needs_device || return $?
    bash scripts/quality/sanitizer-smoke.sh "$out/sanitizers"
}

diagnostics() {
    "${gradle_cmd[@]}" -PsukiDiagnostics "-PsukiAbis=${SUKI_QUALITY_ABI:-x86_64}" :app:assembleDebug || return 2
    echo "BUILT ONLY: opt-in Debug APK with LeakCanary + StrictMode. Reproduce a lifecycle on a device to collect findings."
}

copy_benchmark_results() {
    local module="$1"
    local directory="quality/$module/build/outputs"
    if [[ -d "$directory" ]]; then
        while IFS= read -r -d '' report; do
            cp --parents "$report" "$out/$module/" || return 2
        done < <(find "$directory" -type f \( -name '*.json' -o -name '*.xml' -o -name '*.perfetto-trace' \) -print0)
    fi
}

macrobenchmark() {
    needs_device || return $?
    local rc=0
    "${gradle_cmd[@]}" -PsukiBenchmarks "-PsukiAbis=${SUKI_QUALITY_ABI:-arm64-v8a}" \
        :quality:macrobenchmark:connectedBenchmarkAndroidTest || rc=$?
    copy_benchmark_results macrobenchmark || rc=2
    return "$rc"
}

microbenchmark() {
    needs_device || return $?
    local rc=0
    "${gradle_cmd[@]}" -PsukiBenchmarks :quality:microbenchmark:connectedReleaseAndroidTest || rc=$?
    copy_benchmark_results microbenchmark || rc=2
    return "$rc"
}

perfetto() {
    needs_device || return $?
    if [[ "${SUKI_QUALITY_TRACE:-0}" != 1 ]]; then
        echo "NOT RUN: explicit SUKI_QUALITY_TRACE=1 is required on an isolated profiling device"
        return 77
    fi
    if ! adb shell pm path dev.nuvio.presence.benchmark | grep -q '^package:'; then
        "${gradle_cmd[@]}" -PsukiBenchmarks "-PsukiAbis=${SUKI_QUALITY_ABI:-arm64-v8a}" :app:installBenchmark || return 2
    fi
    bash scripts/quality/perfetto.sh "$out/perfetto"
}

profiler() {
    echo "MANUAL: Android Studio Profiler, target dev.nuvio.presence.benchmark or .quality. See quality/README.md."
    return 77
}

codeql() {
    echo "NOT RUN: GitHub Code Security entitlement and code-scanning permission are unverified on this private repository"
    return 77
}

performance_build() {
    "${gradle_cmd[@]}" -PsukiBenchmarks -PsukiAbis=arm64-v8a \
        :app:assembleBenchmark :quality:macrobenchmark:assembleBenchmark \
        :quality:microbenchmark:assembleReleaseAndroidTest --continue
}

regressions() {
    SUKI_AUDIT_JVM_ONLY=1 tests
}
