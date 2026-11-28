#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "$0")/../.." && pwd)"
install_dir="${SUKI_QUALITY_TOOLS:-$root/build/quality-tools}"
# A released Actionlint can deadlock on long workflow steps before starting ShellCheck.
actionlint_ref=fd33e9f582a01a02885c93ae3775e1190cf63653
mkdir -p "$install_dir/bin"
case "${1:-security}" in
    security)
        export GOBIN="$install_dir/bin"
        go install "github.com/rhysd/actionlint/cmd/actionlint@$actionlint_ref"
        go install github.com/zricethezav/gitleaks/v8@v8.26.0
        go install github.com/google/osv-scanner/v2/cmd/osv-scanner@v2.1.0
        python3 -m venv "$install_dir/venv"
        "$install_dir/venv/bin/python" -m pip install --disable-pip-version-check \
            'setuptools==80.9.0' 'semgrep==1.125.0' 'zizmor==1.16.0'
        ;;
    actions)
        export GOBIN="$install_dir/bin"
        go install "github.com/rhysd/actionlint/cmd/actionlint@$actionlint_ref"
        ;;
    maestro)
        archive=$(mktemp)
        trap 'rm -f -- "$archive"' EXIT
        curl -fLsS --retry 2 --proto '=https' \
            https://github.com/mobile-dev-inc/maestro/releases/download/cli-1.40.3/maestro.zip -o "$archive"
        unzip -oq "$archive" -d "$install_dir"
        test -x "$install_dir/maestro/bin/maestro"
        ln -sfn "$install_dir/maestro/bin/maestro" "$install_dir/bin/maestro"
        ;;
    *) echo "Usage: $0 [security|actions|maestro]" >&2; exit 2 ;;
esac
if [[ -n "${GITHUB_PATH:-}" ]]; then
    printf '%s\n' "$install_dir/bin" "$install_dir/venv/bin" >> "$GITHUB_PATH"
else
    printf 'Tools are in %s and %s\n' "$install_dir/bin" "$install_dir/venv/bin"
fi
