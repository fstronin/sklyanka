#!/usr/bin/env bash
# Снятие результатов прогона с образа (SKL-27).
#
# Идемпотентно: снимает свежую копию поверх прежней, ничего на образе не меняет.
#
#   ./collect.sh [API]
#
# Что снимается: журнал напоминаний (база приложения), строка платформы (НАП-40),
# системный лог, отметки испытаний, независимые подтверждения показа — и разбор
# всего этого в results.json и summary.txt.
set -euo pipefail

SKL_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=config.sh
source "$SKL_DIR/config.sh"
# shellcheck source=lib.sh
source "$SKL_DIR/lib.sh"

api="${1:-${SKL_API_LEVELS[0]}}"
serial="$(skl_serial "$api")"
skl_avd_running "$api" || skl_die "образ API $api не запущен: снимать результаты неоткуда"

results="$(skl_results_dir "$api")"
journal_dir="$results/journal"

skl_log "снимаю базу приложения"
db="$(skl_pull_db "$api" "$journal_dir")"

skl_platform_line "$api" >"$results/platform.txt"
# Системный лог — не источник чисел, а материал для разбора отказа: почему приёмник
# не получил управления, что сказал AlarmManager.
skl_adb -s "$serial" logcat -d >"$results/logcat.txt" 2>/dev/null || true
skl_adb -s "$serial" shell "dumpsys alarm" >"$results/dumpsys-alarm.txt" 2>/dev/null || true
skl_adb -s "$serial" shell "dumpsys deviceidle" >"$results/dumpsys-deviceidle.txt" 2>/dev/null || true

python3 "$SKL_DIR/analyze.py" \
    --db "$db" \
    --marks "$results/trial-marks.tsv" \
    --notifications "$results/notifications.tsv" \
    --platform "$(cat "$results/platform.txt")" \
    --out-dir "$results"

skl_log "результаты: $results"
