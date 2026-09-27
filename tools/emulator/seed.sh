#!/usr/bin/env bash
# Синтетическая нагрузка прогона: файл переноса данных на образе (SKL-27).
#
# Идемпотентно: файл перезаписывается, состояние приложения не трогается.
#
#   ./seed.sh [API] [--mode day|fast]
#
# Ограничение, названное прямо: загрузка файла — единственный путь завести курс без
# продуктовой точки входа для стенда. Сам файл кладётся на образ скриптом, а нажатие
# «Загрузить данные» в настройках приложения делает человек: экрана-приёмника намерения
# у приложения нет, и заводить его ради прогона — работа SKL-19, а не этой задачи.
set -euo pipefail

SKL_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=config.sh
source "$SKL_DIR/config.sh"
# shellcheck source=lib.sh
source "$SKL_DIR/lib.sh"

api="${1:-${SKL_API_LEVELS[0]}}"
mode="day"
if [[ "${2:-}" == "--mode" ]]; then mode="${3:-day}"; fi

skl_avd_running "$api" || skl_die "образ API $api не запущен: ./start.sh $api"
serial="$(skl_serial "$api")"
results="$(skl_results_dir "$api")"
file="$results/seed-$mode.json"

python3 "$SKL_DIR/seed-load.py" \
    --mode "$mode" \
    --medications "$SKL_LOAD_MEDICATIONS" \
    --times "$SKL_LOAD_TIMES_PER_DAY" \
    --step-minutes "$SKL_FAST_STEP_MINUTES" \
    --out "$file" >/dev/null

skl_adb -s "$serial" push "$file" "/sdcard/Download/sklyanka-seed.json" >/dev/null
# Файл, о котором не знает медиатека, в системном выборе файлов не виден.
skl_adb -s "$serial" shell "am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file:///sdcard/Download/sklyanka-seed.json" >/dev/null 2>&1 || true

skl_log "нагрузка положена на образ: /sdcard/Download/sklyanka-seed.json (режим $mode)"
if [[ "$mode" == "fast" ]]; then
    skl_log "режим fast: ${SKL_LOAD_TIMES_PER_DAY} приёмов с шагом ${SKL_FAST_STEP_MINUTES} мин — механика, а НЕ гейт НАП-3"
else
    skl_log "режим day: приёмы включают 23:55 и 00:05 — под испытание И-9"
fi
skl_log "остался один шаг руками: в приложении «Настройки → Перенос данных → Загрузить» выбрать этот файл"
skl_adb -s "$serial" shell "am start -n $SKL_APP_ID/$SKL_PERMISSIONS_ACTIVITY" >/dev/null 2>&1 || true
