#!/usr/bin/env bash
# Полный прогон одной командой (SKL-27).
#
#   ./run-all.sh                  полный прогон на всей матрице образов
#   ./run-all.sh --fast           быстрый прогон: первый образ, только группа fast
#   ./run-all.sh --api 35         один образ
#   ./run-all.sh --keep           не гасить образы после прогона
#
# Идемпотентно целиком: существующие образы не создаются заново, запущенные не поднимаются
# вторыми, установка идёт поверх.
set -euo pipefail

SKL_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=config.sh
source "$SKL_DIR/config.sh"
# shellcheck source=lib.sh
source "$SKL_DIR/lib.sh"

fast=0
keep=0
levels=()
while (( $# > 0 )); do
    case "$1" in
        --fast) fast=1 ;;
        --keep) keep=1 ;;
        --api) shift; levels+=("$1") ;;
        *) skl_die "неизвестный параметр: $1" ;;
    esac
    shift
done
if [[ ${#levels[@]} -eq 0 ]]; then
    if (( fast == 1 )); then levels=("${SKL_API_LEVELS[0]}"); else levels=("${SKL_API_LEVELS[@]}"); fi
fi

status=0
for api in "${levels[@]}"; do
    skl_log "=== API $api: $(skl_image_purpose "$api") ==="
    "$SKL_DIR/avd.sh" create "$api"
    "$SKL_DIR/start.sh" "$api"
    "$SKL_DIR/install.sh" "$api"
    if (( fast == 1 )); then
        "$SKL_DIR/run-tests.sh" "$api" fast || status=$?
    else
        for group in fast data heavy migration; do
            "$SKL_DIR/run-tests.sh" "$api" "$group" || status=$?
        done
    fi
    # Журнал напоминаний снимается всегда: даже прогон без нагрузки показывает, что
    # приложение запускалось, ставило будильники и пересобирало окно.
    "$SKL_DIR/collect.sh" "$api" || skl_warn "результаты API $api снять не удалось"
    (( keep == 0 )) && "$SKL_DIR/stop.sh" "$api"
done

skl_log "прогон завершён, код $status; результаты в $SKL_RESULTS_DIR/api*"
exit "$status"
