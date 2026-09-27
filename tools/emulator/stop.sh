#!/usr/bin/env bash
# Гашение образа (SKL-27). Идемпотентно: незапущенный образ гасить нечего.
#
#   ./stop.sh [API|--all]
set -euo pipefail

SKL_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=config.sh
source "$SKL_DIR/config.sh"
# shellcheck source=lib.sh
source "$SKL_DIR/lib.sh"

skl_stop_one() {
    local api="$1" serial deadline
    serial="$(skl_serial "$api")"
    if ! skl_avd_running "$api"; then
        skl_log "образ API $api не запущен — гасить нечего"
        return 0
    fi
    skl_log "гашу $serial"
    skl_adb -s "$serial" emu kill >/dev/null 2>&1 || true
    deadline=$((SECONDS + SKL_SHUTDOWN_TIMEOUT))
    while (( SECONDS < deadline )); do
        skl_avd_running "$api" || { skl_log "$serial погашен"; return 0; }
        sleep 2
    done
    local pid_file
    pid_file="$(skl_results_dir "$api")/emulator.pid"
    if [[ -f "$pid_file" ]]; then
        skl_warn "$serial не погас за ${SKL_SHUTDOWN_TIMEOUT} с — снимаю процесс"
        kill "$(cat "$pid_file")" 2>/dev/null || true
    fi
}

target="${1:-${SKL_API_LEVELS[0]}}"
if [[ "$target" == "--all" ]]; then
    for api in "${SKL_API_LEVELS[@]}"; do skl_stop_one "$api"; done
else
    skl_stop_one "$target"
fi
