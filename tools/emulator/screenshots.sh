#!/usr/bin/env bash
# Сбор кадров экранного прогона с образа (SKL-96).
#
# Идемпотентно: снимает свежую копию поверх прежней и ничего на образе не меняет.
#
#   ./screenshots.sh [API]
#
# Кадры пишут сами проверки устройства во внутренний каталог приложения
# (/data/data/<id>/files/<SKL_UI_SCREENSHOT_DIR>): оттуда их одинаково надёжно
# забирают и `adb root` (образ AOSP), и `run-as` отладочной сборки — тем же способом,
# что база в lib.sh. Собранное ложится в каталог результатов прогона рядом с отчётами
# (build/emulator/api<API>/<SKL_UI_SCREENSHOT_DIR>) и сопровождается описью manifest.txt.
#
# Кадр — свидетельство, а не эталон: ни хешей, ни порогов, ни сверки (TDD §6).
set -euo pipefail

SKL_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=config.sh
source "$SKL_DIR/config.sh"
# shellcheck source=lib.sh
source "$SKL_DIR/lib.sh"

api="${1:-${SKL_UI_API_LEVEL}}"
serial="$(skl_serial "$api")"
skl_avd_running "$api" || skl_die "образ API $api не запущен: сначала поднимите его (./start.sh $api — с окном или без)"

results="$(skl_results_dir "$api")"
out="$results/$SKL_UI_SCREENSHOT_DIR"
device_dir="/data/data/$SKL_APP_ID/files/$SKL_UI_SCREENSHOT_DIR"

rm -rf "$out"
mkdir -p "$out"

# root перезапускает adbd; после него каталог приложения читается напрямую. Если root
# на образе недоступен (не наш AOSP), кадры тянутся по одному через run-as.
skl_adb_root "$api"

pull_through_root() {
    skl_adb -s "$serial" shell "test -d $device_dir" >/dev/null 2>&1 || return 1
    skl_adb -s "$serial" pull "$device_dir/." "$out/" >/dev/null 2>&1
}

pull_through_run_as() {
    local listing file
    listing="$(skl_adb -s "$serial" shell "run-as $SKL_APP_ID ls $SKL_UI_SCREENSHOT_DIR" 2>/dev/null | tr -d '\r')" || return 1
    [[ -n "$listing" ]] || return 1
    while IFS= read -r file; do
        [[ "$file" == *.png ]] || continue
        skl_adb -s "$serial" exec-out "run-as $SKL_APP_ID cat $SKL_UI_SCREENSHOT_DIR/$file" >"$out/$file" || return 1
    done <<<"$listing"
}

if ! pull_through_root; then
    # root не сработал: начинаем заново, чтобы частично вытянутое не смешалось с целым.
    rm -rf "$out"
    mkdir -p "$out"
    pull_through_run_as || true
fi

count="$(find "$out" -type f -name '*.png' | wc -l | tr -d ' ')"
if (( count == 0 )); then
    skl_die "кадров нет: проверки не запускались или не писали кадры (на образе пусто: $device_dir)"
fi

# Опись собранного: относительный путь и размер. Это список для человека и для отчёта,
# а не манифест эталона: сравнивать кадры не с чем и не нужно (TDD §6).
(
    cd "$out"
    find . -type f -name '*.png' -print | LC_ALL=C sort | while IFS= read -r path; do
        rel="${path#./}"
        printf '%s\t%s\n' "$rel" "$(wc -c <"$rel" | tr -d ' ')"
    done
) >"$out/manifest.txt"

skl_log "кадров собрано: $count; каталог: $out"
echo "$out"
