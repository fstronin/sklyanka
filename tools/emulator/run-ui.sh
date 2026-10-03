#!/usr/bin/env bash
# Экранный прогон на эмуляторе одной командой (SKL-96; журнал и сводка — SKL-97).
#
#   ./run-ui.sh [API] [--keep] [--reuse]
#
#   API      уровень матрицы (по умолчанию SKL_UI_API_LEVEL — targetSdk)
#   --keep   не гасить образ после прогона: окно остаётся человеку
#   --reuse  не стирать данные приложения (по умолчанию install.sh --fresh)
#
# Порядок: образ (идемпотентно) → запуск с видимым окном → экран в рабочее состояние →
# установка отладочной сборки → проверки группы ui на настоящей платформе → сбор кадров
# на хост → сводка. Журнал устройства пишется потоком весь прогон: стек падения и дерево
# семантики живут только в logcat, а буфер за прогон проворачивается (SKL-97, TDD §10).
# Код возврата: код проверок, если он ненулевой, иначе код сбора кадров.
#
# Механический прогон SKL-27 эта команда не заменяет и не меняет: run-all.sh остаётся
# безоконным, данные длинных испытаний не стираются (--fresh касается только этой команды).
set -euo pipefail

SKL_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=config.sh
source "$SKL_DIR/config.sh"
# shellcheck source=lib.sh
source "$SKL_DIR/lib.sh"

api="${SKL_UI_API_LEVEL}"
keep=0
reuse=0
for arg in "$@"; do
    case "$arg" in
        --keep) keep=1 ;;
        --reuse) reuse=1 ;;
        '' | *[!0-9]*) skl_die "неизвестный параметр: $arg ([API] | --keep | --reuse)" ;;
        *) api="$arg" ;;
    esac
done

"$SKL_DIR/avd.sh" create "$api"
"$SKL_DIR/start.sh" "$api" --window

serial="$(skl_serial "$api")"
results="$(skl_results_dir "$api")"

# Видимому окну нужен живой экран: прогон не должен гаснуть или упереться в шторку,
# а программная клавиатура не должна закрывать нижние кнопки форм (TDD §4.5, §5.3).
skl_adb -s "$serial" shell svc power stayon true >/dev/null 2>&1 || true
skl_adb -s "$serial" shell settings put system screen_off_timeout 2147483647 >/dev/null 2>&1 || true
skl_adb -s "$serial" shell wm dismiss-keyguard >/dev/null 2>&1 || true
skl_adb -s "$serial" shell settings put secure show_ime_with_hard_keyboard 0 >/dev/null 2>&1 || true

# Журнал устройства за прогон: гасится в любом исходе, включая падение проверок, —
# иначе разбирать стек упавшего сценария будет нечем (SKL-97, TDD §10).
logcat_file="$results/logcat-ui.txt"
logcat_pid=""
stop_logcat() {
    if [[ -n "$logcat_pid" ]]; then
        kill "$logcat_pid" >/dev/null 2>&1 || true
        wait "$logcat_pid" 2>/dev/null || true
        logcat_pid=""
    fi
}
trap stop_logcat EXIT

skl_adb -s "$serial" logcat -c >/dev/null 2>&1 || true
skl_adb -s "$serial" logcat -v time >"$logcat_file" 2>/dev/null &
logcat_pid=$!

if (( reuse == 0 )); then
    skl_log "установка со стиранием данных приложения (--fresh): кадры не увидят чужих записей"
    "$SKL_DIR/install.sh" "$api" --fresh
else
    "$SKL_DIR/install.sh" "$api"
fi

# Кадры прошлого прогона не должны попасть в опись этого.
device_dir="/data/data/$SKL_APP_ID/files/$SKL_UI_SCREENSHOT_DIR"
skl_adb -s "$serial" shell "rm -rf $device_dir" >/dev/null 2>&1 || true

tests_status=0
"$SKL_DIR/run-tests.sh" "$api" ui || tests_status=$?
stop_logcat

shots_status=0
"$SKL_DIR/screenshots.sh" "$api" || shots_status=$?

# Сводка прогона — по машинно читаемым отчётам AGP, а не по выводу терминала.
# Без python3 сводка не собирается, но прогон продолжается: гашение образа и коды
# возврата стоят ниже и выполняются всегда (SKL-98).
summary_file="$results/ui-summary.txt"
if command -v python3 >/dev/null 2>&1; then
    python3 - "$results/androidTest-results/ui" >"$summary_file" <<'PY'
import glob
import os
import sys
import xml.etree.ElementTree as ET

root = sys.argv[1]
tests = failures = errors = skipped = 0
seconds = 0.0
for path in glob.glob(os.path.join(root, "**", "*.xml"), recursive=True):
    try:
        suite = ET.parse(path).getroot()
    except ET.ParseError:
        continue
    tests += int(suite.get("tests", 0))
    failures += int(suite.get("failures", 0))
    errors += int(suite.get("errors", 0))
    skipped += int(suite.get("skipped", 0))
    seconds += float(suite.get("time", 0.0))
print(f"проверок: {tests}; падений: {failures + errors}; пропущено: {skipped}; время: {seconds:.1f} с")
PY
    summary="$(cat "$summary_file")"
    skl_log "сводка: $summary"
    skl_log "сводка файлом: $summary_file"
    if [[ "$summary" == "проверок: 0;"* ]]; then
        skl_warn "отчётов проверок нет: прогон не дошёл до исполнения"
    fi
else
    printf 'сводка недоступна: python3 не найден в PATH\n' >"$summary_file"
    skl_warn "python3 не найден: сводка не собрана, прогон продолжается (файл: $summary_file)"
fi

skl_log "проверки: статус $tests_status; отчёты: $results/androidTest-results/ui"
skl_log "журнал устройства: $logcat_file"
if (( shots_status == 0 )); then
    skl_log "кадры: $results/$SKL_UI_SCREENSHOT_DIR (опись — manifest.txt)"
else
    skl_warn "кадры не собраны (статус $shots_status): смотрите сообщение screenshots.sh выше"
fi

if (( keep == 0 )); then
    "$SKL_DIR/stop.sh" "$api"
else
    skl_log "образ API $api оставлен запущенным (--keep)"
fi

if (( tests_status != 0 )); then
    exit "$tests_status"
fi
exit "$shots_status"
