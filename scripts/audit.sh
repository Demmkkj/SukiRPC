#!/usr/bin/env bash
set -uo pipefail
cd "$(dirname "$0")/.." || exit 2
root="$PWD"
init="$root/scripts/audit/gradle.init.gradle"
gradle_cmd=("${SUKI_AUDIT_GRADLE:-gradle}" --no-daemon --console=plain)
# shellcheck source=scripts/quality/tools.sh
source "$root/scripts/quality/tools.sh"
quality_tools
quick=(lint detekt ktlint regressions actionlint)
security=(gitleaks osv-scanner semgrep actionlint zizmor codeql)
full=(lint detekt ktlint clang-tidy regressions coverage "${security[@]}")
deep=(paparazzi android-ui maestro sanitizers diagnostics macrobenchmark microbenchmark perfetto profiler)
case "${1:-quick}" in
    quick) set -- "${quick[@]}" ;;
    all|full) set -- "${full[@]}" ;;
    deep) set -- "${deep[@]}" ;;
    complete) set -- "${full[@]}" "${deep[@]}" ;;
    security) set -- "${security[@]}" ;;
esac
for tool in "$@"; do
    case "$tool" in
        lint|detekt|ktlint|clang-tidy|tests|regressions|dependencies|coverage|gitleaks|osv-scanner|semgrep|actionlint|zizmor|codeql|paparazzi|android-ui|maestro|sanitizers|diagnostics|macrobenchmark|microbenchmark|performance-build|perfetto|profiler) ;;
        *) echo "Usage: scripts/audit.sh [quick|full|deep|complete|security|<tools...>]" >&2; exit 2 ;;
    esac
done
mkdir -p build/reports/code-audit || exit 2
out=$(mktemp -d "$root/build/reports/code-audit/run.XXXXXX") || exit 2
scratch=$(mktemp -d) || exit 2
trap 'rm -rf -- "$scratch"' EXIT
printf 'tool\texit_code\n' > "$out/status.tsv"
tool_cache="${XDG_CACHE_HOME:-$HOME/.cache}/sukirpc-audit"

download_jar() {
    local coordinate="$1" digest="$2" file="$tool_cache/${1##*/}" temp
    mkdir -p "$tool_cache" || return 2
    if ! printf '%s  %s\n' "$digest" "$file" | sha256sum -c --status; then
        temp=$(mktemp "$tool_cache/.download.XXXXXX") || return 2
        if ! curl --fail --silent --show-error --retry 2 --proto '=https' \
            "https://repo.maven.apache.org/maven2/$coordinate" -o "$temp" ||
            ! printf '%s  %s\n' "$digest" "$temp" | sha256sum -c --status; then
            rm -f "$temp"
            echo "Cannot download or verify $coordinate" >&2
            return 2
        fi
        mv -f "$temp" "$file" || return 2
    fi
    printf '%s\n' "$file"
}

lint() {
    local rc=0
    "${gradle_cmd[@]}" -I "$init" "-PsukiAuditLint=$out/lint" \
        :app:lintDebug :collector:lintDebug --continue || rc=$?
    [[ -s "$out/lint/app.xml" && -s "$out/lint/collector.xml" ]] || {
        echo "Android Lint did not produce both module reports" >&2
        return 2
    }
    return "$rc"
}

detekt() {
    local jar rc=0
    jar=$(download_jar \
        io/gitlab/arturbosch/detekt/detekt-cli/1.23.8/detekt-cli-1.23.8-all.jar \
        3afe89a11120303c73c9bdda3d8fe558dd9070a6937d27819ddc04b275381245) || return 2
    java -jar "$jar" --build-upon-default-config --base-path "$root" --jvm-target 17 \
        --input app/src,collector/src,shared,tests,quality --excludes '**/build/**' --report "sarif:$out/detekt/report.sarif" || rc=$?
    [[ -s "$out/detekt/report.sarif" ]] || return 2
    return "$rc"
}

ktlint() {
    local jar rc=0
    jar=$(download_jar com/pinterest/ktlint/ktlint-cli/1.7.1/ktlint-cli-1.7.1-all.jar \
        5f3de4ee4014137b05c72ed68eb46fe686aeb11fbdef27faf77f55d51c1b2536) || return 2
    java -jar "$jar" --relative --reporter="sarif,output=$out/ktlint/report.sarif" \
        'app/src/**/*.kt' 'collector/src/**/*.kt' 'shared/**/*.kt' 'tests/**/*.kt' 'quality/**/*.kt' '!quality/**/build/**' || rc=$?
    [[ -s "$out/ktlint/report.sarif" ]] || return 2
    return "$rc"
}

clang_tidy() {
    local sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}" bin tidy resource db rc=0
    local abi="${SUKI_AUDIT_ABI:-arm64-v8a}"
    if [[ -z "$sdk" || ! -f app/libs/discord_partner_sdk.aar ]]; then
        echo "Clang-Tidy needs Android SDK/NDK and the authorized Discord SDK AAR"
        return 2
    fi
    bin="$sdk/ndk/27.3.13750724/toolchains/llvm/prebuilt/linux-x86_64/bin"
    tidy="${SUKI_AUDIT_CLANG_TIDY:-$bin/clang-tidy}"
    [[ -x "$tidy" ]] || tidy=$(command -v clang-tidy-18) || return 2
    resource=$("$bin/clang++" -print-resource-dir) || return 2
    if ! "${gradle_cmd[@]}" -I "$init" -PsukiAuditNative "-PsukiAbis=$abi" \
            ":app:configureCMakeDebug[$abi]" > "$scratch/native-config.log" 2>&1; then
        grep -E '^(> Task .*FAILED|\* What went wrong:|Execution failed|> \[CXX|BUILD FAILED)' "$scratch/native-config.log" || true
        return 2
    fi
    db=$(find app/.cxx/audit -type f -path "*/$abi/compile_commands.json" -printf '%T@ %p\n' \
        | sort -nr | sed -n '1{s/^[^ ]* //;p;}')
    [[ -n "$db" && -s "$db" ]] || { echo "CMake compilation database missing"; return 2; }
    python3 - "$db" "$root/app/src/main/cpp/rpc.cpp" "$bin" <<'PY' || return 2
import json
import sys
from pathlib import Path
db, source, bin_dir = sys.argv[1:]
entries = json.loads(Path(db).read_text())
assert any((Path(e["directory"]) / e["file"]).resolve() == Path(source) and
           bin_dir in (e.get("command") or " ".join(e["arguments"])) for e in entries), "Expected real NDK command for rpc.cpp"
PY
    "$tidy" -p "$(dirname "$db")" --extra-arg="-resource-dir=$resource" \
        --checks='clang-analyzer-*,bugprone-*,performance-*,portability-*' \
        --warnings-as-errors='*' "$root/app/src/main/cpp/rpc.cpp" > "$scratch/clang-tidy.log" 2>&1 || rc=$?
    echo "Clang-Tidy executed against Debug/$abi (NDK 27.3.13750724), exit $rc"
    return "$rc"
}

tests() {
    local test main rc=0
    local source=app/src/main/java/dev/nuvio/presence
    for test in Chronicle Horizon; do
        case "$test" in
            Chronicle) main="$source/chronicle/SessionClock.kt" ;;
            Horizon) main="$source/horizon/Horizon.kt" ;;
        esac
        if kotlinc "$main" "tests/${test}Test.kt" -include-runtime -d "$scratch/$test.jar"; then
            java -jar "$scratch/$test.jar" || rc=1
        else
            rc=1
        fi
    done
    python3 tests/run_core_tests.py || rc=1
    python3 tests/run_aurora_tests.py || rc=1
    if "${CXX:-c++}" -std=c++17 -Wall -Wextra -Werror -pthread -I app/src/main/cpp \
        tests/RpcOutcomeTest.cpp -o "$scratch/rpc-outcome-test" && "$scratch/rpc-outcome-test"; then
        echo "PASS: late and duplicate JNI callbacks cannot replace the current outcome"
    else
        rc=1
    fi
    # A local JVM-only run stays usable without Android SDK/Gradle; CI always requests both.
    if [[ "${SUKI_AUDIT_JVM_ONLY:-0}" != 1 ]]; then
        "${gradle_cmd[@]}" :app:testDebugUnitTest :collector:testDebugUnitTest --continue || rc=1
        for test in app collector; do
            if [[ -d "$test/build/test-results/testDebugUnitTest" ]]; then
                cp -R "$test/build/test-results/testDebugUnitTest" "$out/tests/$test" || rc=1
            fi
        done
    else
        echo "Gradle Android unit tests omitted (explicit SUKI_AUDIT_JVM_ONLY=1)"
    fi
    return "$rc"
}

dependencies() {
    "${gradle_cmd[@]}" -I "$init" "-PsukiAuditOutput=$out/dependencies" \
        :app:auditDependencies :collector:auditDependencies || return 2
    python3 scripts/audit/dependencies.py "$out/dependencies"
}

failed=0
for tool in "$@"; do
    mkdir -p "$out/$tool" || exit 2
    echo "== $tool =="
    rc=0
    runner="${tool//-/_}"
    "$runner" > "$out/$tool/run.log" 2>&1 || rc=$?
    if [[ "$rc" != 0 && "$rc" != 77 ]]; then failed=1; fi
    if [[ "$rc" == 77 && "${SUKI_AUDIT_REQUIRE_TOOLS:-0}" == 1 && "$tool" != codeql && "$tool" != profiler ]]; then
        failed=1
    fi
    printf '%s\t%s\n' "$tool" "$rc" >> "$out/status.tsv"
    tail -n 12 "$out/$tool/run.log"
done
python3 scripts/audit/reports.py "$out" "$scratch/clang-tidy.log" || failed=1
[[ ! -s "$out/summary.md" ]] || cat "$out/summary.md"
if [[ -n "${GITHUB_STEP_SUMMARY:-}" && -s "$out/summary.md" ]]; then
    cat "$out/summary.md" >> "$GITHUB_STEP_SUMMARY"
fi
echo "Reports: $out"
exit "$failed"
