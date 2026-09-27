#!/usr/bin/env bash
# Синтетическая нагрузка прогона: файл переноса данных на образе (SKL-27).
#
# Идемпотентно: файл перезаписывается, состояние приложения не трогается.
#
#   ./seed.sh [API] [--mode day|fast|direct]
#
# Три режима. day и fast кладут на образ файл переноса: его выбирает человек в приложении
# («Настройки → Перенос данных → Загрузить»), потому что продуктовой точки входа для стенда
# нет, и заводить её ради прогона — работа SKL-19, а не этой задачи.
#
# Режим direct пишет нагрузку прямо в базу приложения и обходится без рук. Он появился
# не для удобства: на образе aosp_atd системного выбора файлов нет вовсе (DocumentsUI
# в урезанный образ не входит), и путь через файл там невозможен в принципе. Будильники
# режим direct не ставит — их ставит сам продукт, пересобирая окно при запуске.
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

# --- Нагрузка прямо в базу приложения ------------------------------------------
skl_seed_direct() {
    local work="$results/seed-direct" suffix source_path now zone uid
    skl_app_installed "$api" || skl_die "приложение не установлено: ./install.sh $api"
    skl_adb_root "$api"
    # Базу нельзя править под работающим приложением: часть записей живёт в журнале
    # упреждающей записи, и открытая копия перезапишет их обратно.
    skl_adb -s "$serial" shell "am force-stop $SKL_APP_ID"
    rm -rf "$work"
    mkdir -p "$work"
    for suffix in "" "-wal" "-shm"; do
        source_path="/data/data/$SKL_APP_ID/databases/${SKL_APP_DB}${suffix}"
        skl_adb -s "$serial" pull "$source_path" "$work/" >/dev/null 2>&1 || true
    done
    [[ -s "$work/$SKL_APP_DB" ]] ||
        skl_die "базы приложения нет: запустите приложение один раз, затем повторите"

    now="$(skl_adb -s "$serial" shell date +%s | tr -d '\r')"
    zone="$(skl_adb -s "$serial" shell getprop persist.sys.timezone | tr -d '\r')"
    python3 "$SKL_DIR/seed-direct.py" \
        --db "$work/$SKL_APP_DB" \
        --now "$now" \
        --zone "$zone" \
        --medications "$SKL_LOAD_MEDICATIONS" \
        --times "$SKL_LOAD_TIMES_PER_DAY" \
        --step-minutes "$SKL_FAST_STEP_MINUTES"

    for suffix in "" "-wal" "-shm"; do
        [[ -f "$work/${SKL_APP_DB}${suffix}" ]] || continue
        skl_adb -s "$serial" push "$work/${SKL_APP_DB}${suffix}" \
            "/data/data/$SKL_APP_ID/databases/${SKL_APP_DB}${suffix}" >/dev/null
    done
    # Файлы, положенные от root, приложению недоступны — ни по владельцу, ни по метке
    # SELinux. Без обоих действий приложение молча не открывает свою базу.
    uid="$(skl_adb -s "$serial" shell "stat -c %u /data/data/$SKL_APP_ID" | tr -d '\r')"
    skl_adb -s "$serial" shell "chown $uid:$uid /data/data/$SKL_APP_ID/databases/${SKL_APP_DB}*"
    skl_adb -s "$serial" shell "restorecon -R /data/data/$SKL_APP_ID/databases" >/dev/null 2>&1 || true

    # Запуск приложения: окно пересобирает продукт, и будильники ставит он.
    skl_adb -s "$serial" shell "am start -n $SKL_APP_ID/$SKL_APP_ID.MainActivity" >/dev/null
    sleep 8
    skl_log "нагрузка записана в базу; будильники поставил сам продукт при пересборке окна"
    skl_log "проверить: adb -s $serial shell dumpsys alarm | grep REMINDER_INTAKE"
}

if [[ "$mode" == "direct" ]]; then
    skl_seed_direct
    exit 0
fi

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
