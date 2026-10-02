#!/usr/bin/env bash
# Сравнить прогон с эталоном и записать отчёт о различиях (SKL-92).
#
#   ./diff.sh [<прогон>]
#
# Без аргумента берётся последний снятый прогон. Коды возврата:
#   0 — различий нет; 1 — есть changed/new/missing; 2 — отказ (нет эталона, нет прогонов).
#
# В stdout печатается путь отчёта, человеку сводка идёт в stderr.
set -euo pipefail

SKL_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=config.sh
source "$SKL_DIR/config.sh"
# shellcheck source=lib.sh
source "$SKL_DIR/lib.sh"

refuse() {
    printf '[visual] ОТКАЗ: %s\n' "$*" >&2
    exit 2
}

if (( $# > 1 )); then
    refuse "использование: diff.sh [<прогон>]"
fi

baseline_png="$(find "$SKL_BASELINES_DIR" -type f -name '*.png' -print -quit 2>/dev/null || true)"
if [[ ! -d "$SKL_BASELINES_DIR" || -z "$baseline_png" ]]; then
    refuse "эталон не утверждён: сначала ./approve.sh <прогон>"
fi

if (( $# == 1 )); then
    run_dir="$(skl_resolve_run "$1" 2>/dev/null)" || refuse "прогон не найден: $1 (смотрите $SKL_RUNS_DIR)"
else
    run_dir="$(skl_latest_run)" || refuse "прогонов нет: сначала ./capture.sh"
fi
tmp_dir="$(mktemp -d "${TMPDIR:-/tmp}/skl-visual-diff.XXXXXX")"
trap 'rm -rf "$tmp_dir"' EXIT

skl_write_manifest "$run_dir" "$tmp_dir/current.sha256"
skl_write_manifest "$SKL_BASELINES_DIR" "$tmp_dir/baseline.sha256"

run_drift=0
baseline_drift=0
if [[ -f "$run_dir/manifest.sha256" ]] && ! cmp -s "$run_dir/manifest.sha256" "$tmp_dir/current.sha256"; then
    run_drift=1
    skl_warn "манифест прогона расходится с файлами: прогон правился после снятия"
fi
if [[ -f "$SKL_BASELINES_DIR/manifest.sha256" ]] && ! cmp -s "$SKL_BASELINES_DIR/manifest.sha256" "$tmp_dir/baseline.sha256"; then
    baseline_drift=1
    skl_warn "манифест эталона расходится с файлами: эталон правился в обход approve.sh"
fi

: > "$tmp_dir/changed.tsv"
: > "$tmp_dir/new.tsv"
: > "$tmp_dir/missing.tsv"
while read -r hash path; do
    base_hash=""
    if base_hash="$(skl_manifest_hash "$tmp_dir/baseline.sha256" "$path")"; then
        if [[ "$base_hash" != "$hash" ]]; then
            printf '%s\t%s\t%s\n' "$path" "$base_hash" "$hash" >> "$tmp_dir/changed.tsv"
        fi
    else
        printf '%s\t%s\n' "$path" "$hash" >> "$tmp_dir/new.tsv"
    fi
done < "$tmp_dir/current.sha256"
while read -r hash path; do
    if ! skl_manifest_hash "$tmp_dir/current.sha256" "$path" >/dev/null; then
        printf '%s\t%s\n' "$path" "$hash" >> "$tmp_dir/missing.tsv"
    fi
done < "$tmp_dir/baseline.sha256"

changed_count="$(skl_count_lines "$tmp_dir/changed.tsv")"
new_count="$(skl_count_lines "$tmp_dir/new.tsv")"
missing_count="$(skl_count_lines "$tmp_dir/missing.tsv")"

diff_root="$run_dir/diff"
rm -rf "$diff_root"
mkdir -p "$diff_root"
pixel_tool="$(skl_pixel_tool)"
significant_count=0
tolerated_count=0

: > "$tmp_dir/changed-rows.tsv"
if (( changed_count > 0 )) && [[ -n "$pixel_tool" ]]; then
    while IFS=$'\t' read -r path base_hash run_hash; do
        baseline_file="$SKL_BASELINES_DIR/$path"
        run_file="$run_dir/$path"
        image="$diff_root/${path%.png}.diff.png"
        pixels=""
        pixel_code=0
        if [[ "$pixel_tool" == "python3-pil" ]]; then
            pixels="$(python3 "$SKL_DIR/pixel_diff.py" \
                --baseline "$baseline_file" --current "$run_file" \
                --out "$image" --threshold "$SKL_PIXEL_TOLERANCE" 2>&1)" || pixel_code=$?
        else
            fuzz="$(awk -v threshold="$SKL_PIXEL_TOLERANCE" 'BEGIN { printf "%.4f", threshold * 100 / 255 }')"
            if [[ "$pixel_tool" == "magick" ]]; then
                pixels="$(magick compare -metric AE -fuzz "${fuzz}%" "$baseline_file" "$run_file" "$image" 2>&1)" || pixel_code=$?
            else
                pixels="$(compare -metric AE -fuzz "${fuzz}%" "$baseline_file" "$run_file" "$image" 2>&1)" || pixel_code=$?
            fi
            if [[ "$pixels" =~ ^[0-9]+$ ]]; then
                if [[ "$pixels" == "0" ]]; then
                    pixels="changed_pixels=0 significant=no"
                else
                    pixels="changed_pixels=$pixels significant=yes"
                fi
            else
                pixel_code=2
            fi
        fi
        significant="yes"
        case "$pixels" in
            *significant=no*) significant="no" ;;
        esac
        if (( pixel_code == 2 )); then
            skl_warn "попиксельное сравнение не удалось для $path: $pixels"
            significant="yes"
            pixels="ошибка инструмента: $pixels"
        fi
        if [[ "$significant" == "yes" ]]; then
            significant_count=$((significant_count + 1))
        else
            tolerated_count=$((tolerated_count + 1))
        fi
        image_relative="-"
        if [[ -f "$image" ]]; then
            image_relative="${image#"$run_dir"/}"
        fi
        printf '%s\t%s\t%s\t%s\t%s\t%s\n' \
            "$path" "$base_hash" "$run_hash" "$significant" "$pixels" "$image_relative" >> "$tmp_dir/changed-rows.tsv"
    done < "$tmp_dir/changed.tsv"
elif (( changed_count > 0 )); then
    while IFS=$'\t' read -r path base_hash run_hash; do
        printf '%s\t%s\t%s\tunknown\t-\t-\n' "$path" "$base_hash" "$run_hash" >> "$tmp_dir/changed-rows.tsv"
    done < "$tmp_dir/changed.tsv"
fi

report="$diff_root/report.md"
{
    printf '# Diff прогона %s с эталоном\n\n' "$(basename "$run_dir")"
    printf '| | |\n|---|---|\n'
    printf '| Прогон | `%s` |\n' "$run_dir"
    printf '| Время сравнения | %s |\n' "$(date '+%Y-%m-%d %H:%M:%S %z')"
    printf '| Эталон | `%s` |\n' "$SKL_BASELINES_DIR"
    printf '| Листов в прогоне | %s |\n' "$(skl_count_lines "$tmp_dir/current.sha256")"
    printf '| Листов в эталоне | %s |\n' "$(skl_count_lines "$tmp_dir/baseline.sha256")"
    if [[ -n "$pixel_tool" ]]; then
        printf '| Инструмент допуска | %s, порог %s/255 |\n' "$pixel_tool" "$SKL_PIXEL_TOLERANCE"
    else
        printf '| Инструмент допуска | нет — сравнение хеш-единственное |\n'
    fi
    printf '\n## Сводка\n\n'
    printf -- '- changed: %s\n' "$changed_count"
    printf -- '- new: %s\n' "$new_count"
    printf -- '- missing: %s\n' "$missing_count"
    if [[ -n "$pixel_tool" ]]; then
        printf -- '- значимых различий: %s\n' "$significant_count"
        printf -- '- различий ниже допуска: %s\n' "$tolerated_count"
    else
        printf -- '- значимость не определена: инструмента допуска нет\n'
    fi
    printf '\n## changed\n\n'
    if (( changed_count == 0 )); then
        printf 'Изменённых листов нет.\n'
    else
        printf '| Лист | Хеш | Пиксели | Diff |\n|---|---|---|---|\n'
        while IFS=$'\t' read -r path base_hash run_hash significant pixels image_relative; do
            [[ -n "$path" ]] || continue
            pixel_note="$pixels"
            if [[ "$significant" == "no" ]]; then
                pixel_note="ниже допуска: $pixels"
            elif [[ "$significant" == "unknown" ]]; then
                pixel_note="инструмента допуска нет"
            elif [[ "$significant" == "yes" && "$pixels" != "ошибка инструмента"* ]]; then
                pixel_note="значимо: $pixels"
            fi
            printf '| `%s` | `%s` → `%s` | %s | `%s` |\n' \
                "$path" "${base_hash:0:12}" "${run_hash:0:12}" "$pixel_note" "$image_relative"
        done < "$tmp_dir/changed-rows.tsv"
    fi
    printf '\n## new\n\n'
    if (( new_count == 0 )); then
        printf 'Новых листов нет.\n'
    else
        while IFS=$'\t' read -r path _hash; do
            [[ -n "$path" ]] || continue
            printf -- '- `%s`\n' "$path"
        done < "$tmp_dir/new.tsv"
    fi
    printf '\n## missing\n\n'
    if (( missing_count == 0 )); then
        printf 'Пропавших листов нет.\n'
    else
        while IFS=$'\t' read -r path _hash; do
            [[ -n "$path" ]] || continue
            printf -- '- `%s`\n' "$path"
        done < "$tmp_dir/missing.tsv"
    fi
    printf '\n## Замечания\n\n'
    if (( run_drift == 0 && baseline_drift == 0 )); then
        printf 'Нет.\n'
    else
        if (( run_drift == 1 )); then
            printf -- '- манифест прогона расходится с файлами: прогон правился после снятия.\n'
        fi
        if (( baseline_drift == 1 )); then
            printf -- '- манифест эталона расходится с файлами: эталон правился в обход `approve.sh`.\n'
        fi
    fi
    printf '\n## Ограничения\n\n'
    if [[ -z "$pixel_tool" ]]; then
        printf -- '- Инструмент попиксельного сравнения недоступен: сравнение только по sha256, допуск не применён.\n'
    else
        printf -- '- Попиксельное сравнение: %s, порог %s/255; пиксели с меньшей разницей каналов считаются шумом рендера.\n' "$pixel_tool" "$SKL_PIXEL_TOLERANCE"
    fi
    printf -- '- Robolectric — имитация Android, а не EMUI: листы не доказывают вид на устройстве.\n'
    printf -- '- Совпадение с эталоном не значит «верно»: эталон фиксирует изменение, а не правильность.\n'
} > "$report"

skl_log "прогон: $run_dir"
if [[ -n "$pixel_tool" ]]; then
    skl_log "changed: $changed_count (значимых $significant_count, ниже допуска $tolerated_count), new: $new_count, missing: $missing_count"
else
    skl_log "changed: $changed_count, new: $new_count, missing: $missing_count (значимость не определена: нет инструмента допуска)"
fi
skl_log "эталон: $SKL_BASELINES_DIR"
skl_log "отчёт: $report"
printf '%s\n' "$report"

if (( changed_count + new_count + missing_count > 0 )); then
    exit 1
fi
exit 0
