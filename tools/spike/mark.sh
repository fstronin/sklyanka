#!/usr/bin/env bash
# Отметка сценария выживаемости во времени (SKL-03, TDD.md §7).
#
#   ./mark.sh И-1 начало [--serial S] [--file marks.tsv] [--at "2026-10-03T10:00"]
#
# Зачем отметка. Разбор по журналу отвечает на вопрос «что было», но не на вопрос «что мы
# в это время делали». Отметки «начало … конец» вокруг сценария связывают окно прогона
# с кодом И-1 … И-16, и gate.py считает числа сценария отдельно от основного прогона.
#
# Момент берётся по часам УСТРОЙСТВА (adb), а не машины: сценарии И-4 … И-6 двигают часы
# устройства, и отметка по часам хоста легла бы в журнал под чужое время. Если устройства
# нет, время можно задать ключом --at (ISO-8601, местное время устройства).
set -euo pipefail

SPIKE_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "$SPIKE_DIR/../.." && pwd)"
# shellcheck source=../emulator/lib.sh
source "$SPIKE_DIR/../emulator/lib.sh"

serial="${ANDROID_SERIAL:-}"
file="$REPO_ROOT/build/spike/marks.tsv"
at=""
code=""
state=""

while [[ $# -gt 0 ]]; do
    case "$1" in
        --serial) serial="$2"; shift 2 ;;
        --file) file="$2"; shift 2 ;;
        --at) at="$2"; shift 2 ;;
        -*) skl_die "неизвестный аргумент: $1" ;;
        *)
            if [[ -z "$code" ]]; then code="$1"
            elif [[ -z "$state" ]]; then state="$1"
            else skl_die "лишний аргумент: $1"
            fi
            shift
            ;;
    esac
done

[[ -n "$code" && -n "$state" ]] || skl_die "использование: ./mark.sh КОД СОСТОЯНИЕ [--serial S] [--file FILE] [--at ISO]"

if [[ -z "$at" ]]; then
    if [[ -z "$serial" ]]; then
        devices="$(skl_adb devices 2>/dev/null | awk 'NR>1 && $2=="device" {print $1}')"
        [[ -n "$devices" ]] || skl_die "устройство не найдено: подключите его или задайте --at"
        serial="$(printf '%s\n' "$devices" | head -n 1)"
    fi
    seconds="$(skl_adb -s "$serial" shell date +%s 2>/dev/null | tr -d '\r')"
    [[ "$seconds" =~ ^[0-9]+$ ]] || skl_die "часы устройства не прочитались"
    moment=$((seconds * 1000))
else
    moment="$(python3 - "$at" <<'PY'
import sys
from datetime import datetime
text = sys.argv[1]
if text.endswith("Z"):
    text = text[:-1] + "+00:00"
moment = datetime.fromisoformat(text)
if moment.tzinfo is None:
    moment = moment.astimezone()
print(int(moment.timestamp() * 1000))
PY
)"
fi

mkdir -p "$(dirname -- "$file")"
printf '%s\t%s\t%s\n' "$moment" "$code" "$state" >>"$file"
skl_log "отметка: $code — $state, момент $moment мс, файл $file"
