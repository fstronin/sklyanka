#!/usr/bin/env bash
# Снятие журнала напоминаний и расчёт чисел гейта (SKL-03).
#
#   ./collect.sh [--serial S] [--csv FILE] [--out DIR] [--platform ТЕКСТ] [--marks FILE]
#                [--zone IANA] [--times "ЧЧ:ММ,…"] [--per-slot N] [--run-kind ТЕКСТ]
#
# --times и --per-slot по умолчанию берутся из config.sh (нагрузка И-16: пять лекарств,
# восемь времён); переопределение нужно ускоренным самопроверкам с другим расписанием.
#
# Основной путь заказчика — выгрузить журнал из приложения (Диагностика → «Выгрузить
# журнал») и передать CSV сюда ключом --csv: ни компьютер, ни adb для этого не нужны.
# Резервный путь — снять копию базы с подключённого устройства этой командой; он работает
# только с отладочной сборкой (run-as) или на эмуляторе (adb root).
#
# Что делает: кладёт журнал и строку платформы в каталог результатов, запускает gate.py
# и печатает человеческую сводку. Числа считает gate.py — этот скрипт только снимает файлы.
set -euo pipefail

SPIKE_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "$SPIKE_DIR/../.." && pwd)"
# shellcheck source=config.sh
source "$SPIKE_DIR/config.sh"
# shellcheck source=../emulator/lib.sh
source "$SPIKE_DIR/../emulator/lib.sh"

APP_ID="ru.stohastika.sklyanka"
APP_DB="sklyanka.db"

serial="${ANDROID_SERIAL:-}"
csv=""
out=""
platform=""
marks=""
zone=""
times="$SPIKE_TIMES"
per_slot="$SPIKE_MEDICATIONS"
run_kind="эталонный Huawei, EMUI 15 (SKL-03)"
while [[ $# -gt 0 ]]; do
    case "$1" in
        --serial) serial="$2"; shift 2 ;;
        --csv) csv="$2"; shift 2 ;;
        --out) out="$2"; shift 2 ;;
        --platform) platform="$2"; shift 2 ;;
        --marks) marks="$2"; shift 2 ;;
        --zone) zone="$2"; shift 2 ;;
        --times) times="$2"; shift 2 ;;
        --per-slot) per_slot="$2"; shift 2 ;;
        --run-kind) run_kind="$2"; shift 2 ;;
        *) skl_die "неизвестный аргумент: $1" ;;
    esac
done

[[ -n "$out" ]] || out="$REPO_ROOT/build/spike"
mkdir -p "$out"

# Последовательный номер: явный аргумент, переменная среды, единственное подключённое.
try_resolve_serial() {
    local devices count
    if [[ -n "$serial" ]]; then
        return 0
    fi
    devices="$(skl_adb devices 2>/dev/null | awk 'NR>1 && $2=="device" {print $1}')"
    [[ -n "$devices" ]] || return 1
    count="$(printf '%s\n' "$devices" | grep -c . || true)"
    serial="$(printf '%s\n' "$devices" | head -n 1)"
    (( count == 1 )) || skl_warn "устройств несколько: выбран первый ($serial); нужное задайте --serial"
    return 0
}

pull_db() {
    local work="$out/journal" suffix
    mkdir -p "$work"
    try_resolve_serial || skl_die "подключённых устройств нет: передайте --csv или подключите устройство"
    skl_log "снимаю копию базы приложения с $serial"
    # Остановка на миг и запуск обратно: база с открытым WAL иначе читается «на записи назад».
    skl_adb -s "$serial" shell "am force-stop $APP_ID" >/dev/null 2>&1 || true
    for suffix in "" "-wal" "-shm"; do
        skl_adb -s "$serial" exec-out "run-as $APP_ID cat databases/${APP_DB}${suffix}" \
            >"$work/${APP_DB}${suffix}" 2>/dev/null || true
    done
    if [[ ! -s "$work/$APP_DB" ]]; then
        skl_warn "run-as не сработал (сборка не отладочная?): пробую adb root"
        skl_adb -s "$serial" root >/dev/null 2>&1 || true
        skl_adb -s "$serial" wait-for-device >/dev/null 2>&1 || true
        skl_adb -s "$serial" shell "am force-stop $APP_ID" >/dev/null 2>&1 || true
        for suffix in "" "-wal" "-shm"; do
            skl_adb -s "$serial" pull "/data/data/$APP_ID/databases/${APP_DB}${suffix}" \
                "$work/" >/dev/null 2>&1 || true
        done
    fi
    [[ -s "$work/$APP_DB" ]] ||
        skl_die "базу снять не удалось: выгрузите журнал в приложении и передайте его ключом --csv"
    skl_adb -s "$serial" shell "monkey -p $APP_ID -c android.intent.category.LAUNCHER 1" >/dev/null 2>&1 || true
    echo "$work/$APP_DB"
}

# Что читает gate.py: CSV (основной путь) или снятую базу (резервный).
journal_kind="csv"
if [[ -z "$csv" ]]; then
    csv="$(pull_db)"
    journal_kind="db"
    skl_log "копия базы: $csv"
fi

if [[ -z "$platform" ]]; then
    if try_resolve_serial; then
        model="$(skl_adb -s "$serial" shell getprop ro.product.model 2>/dev/null | tr -d '\r')"
        emui="$(skl_adb -s "$serial" shell getprop ro.build.version.emui 2>/dev/null | tr -d '\r')"
        release="$(skl_adb -s "$serial" shell getprop ro.build.version.release 2>/dev/null | tr -d '\r')"
        sdk="$(skl_adb -s "$serial" shell getprop ro.build.version.sdk 2>/dev/null | tr -d '\r')"
        build="$(skl_adb -s "$serial" shell getprop ro.build.display.id 2>/dev/null | tr -d '\r')"
        platform="$model, EMUI ${emui:-не прочитана}, Android $release (API $sdk), сборка $build"
    fi
fi
[[ -n "$platform" ]] || platform="не записана (впишите вручную в отчёт: НАП-40)"

# Пояс устройства нужен расчёту, чтобы собрать сетку ожидаемых моментов по местным
# временам суток; без устройства его можно задать ключом --zone.
if [[ -z "$zone" ]] && try_resolve_serial; then
    zone="$(skl_adb -s "$serial" shell getprop persist.sys.timezone 2>/dev/null | tr -d '\r')"
fi

printf '%s\n' "$platform" >"$out/platform.txt"

[[ -n "$marks" ]] || marks="$out/marks.tsv"

set -- --platform "$platform" --run-kind "$run_kind" --out-dir "$out" \
    --times "$times" --per-slot "$per_slot"
if [[ -n "$zone" ]]; then
    set -- "$@" --zone "$zone"
fi
if [[ "$journal_kind" == "csv" ]]; then
    set -- "$@" --csv "$csv"
else
    set -- "$@" --db "$csv"
fi
if [[ -f "$marks" ]]; then
    skl_log "отметки сценариев: $marks"
    set -- "$@" --marks "$marks"
else
    skl_warn "отметок сценариев нет ($marks): разбивки по И-1 … И-16 в разборе не будет"
fi

python3 "$SPIKE_DIR/gate.py" "$@"
