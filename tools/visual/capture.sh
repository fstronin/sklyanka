#!/usr/bin/env bash
# Снять прогон контактных листов и записать его в tools/visual/runs (SKL-92).
#
#   ./capture.sh [--run <имя>] [--force]
#
# Без --run имя прогона — отметка времени. Существующий прогон без --force
# не затирается: прогон мог быть просмотрен и утверждён.
#
# В stdout печатается каталог прогона, человеку сводка идёт в stderr.
set -euo pipefail

SKL_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=config.sh
source "$SKL_DIR/config.sh"
# shellcheck source=lib.sh
source "$SKL_DIR/lib.sh"

usage() {
    printf 'использование: capture.sh [--run <имя>] [--force]\n' >&2
}

run_name=""
force=0
while (( $# > 0 )); do
    case "$1" in
        --run)
            if (( $# < 2 )); then
                skl_die "--run требует имя прогона"
            fi
            run_name="$2"
            shift 2
            ;;
        --force)
            force=1
            shift
            ;;
        -h | --help)
            usage
            exit 0
            ;;
        *)
            skl_die "неизвестный аргумент: $1"
            ;;
    esac
done

if [[ -n "$run_name" && ! "$run_name" =~ ^[A-Za-z0-9._-]+$ ]]; then
    skl_die "имя прогона: только латиница, цифры, точка, дефис и подчёркивание — «$run_name»"
fi

if [[ -z "$run_name" ]]; then
    run_name="$(date '+%Y%m%d-%H%M%S')"
fi
run_dir="$SKL_RUNS_DIR/$run_name"
if [[ -d "$run_dir" ]]; then
    if (( force == 1 )); then
        skl_warn "прогон $run_name уже есть — заменяю (--force)"
        rm -rf "$run_dir"
    else
        skl_die "прогон $run_name уже есть: выберите другое имя или повторите с --force"
    fi
fi

skl_require_java
if [[ ! -f "$SKL_REPO_ROOT/$SKL_CAPTURE_TEST_FILE" ]]; then
    skl_die "тест-генератор не найден: $SKL_CAPTURE_TEST_FILE"
fi

skl_log "снятие контактных листов: $SKL_CAPTURE_TASK --tests '$SKL_CAPTURE_TEST_FILTER'"
rm -rf "$SKL_VISUAL_REVIEW_DIR"
gradle_status=0
"$SKL_REPO_ROOT/gradlew" \
    --project-dir "$SKL_REPO_ROOT" \
    --console=plain \
    "$SKL_CAPTURE_TASK" \
    --tests "$SKL_CAPTURE_TEST_FILTER" \
    "-P$SKL_VISUAL_REVIEW_PROPERTY=true" \
    --rerun || gradle_status=$?
if (( gradle_status != 0 )); then
    skl_die "Gradle завершился с кодом $gradle_status: тест-генератор не нашёлся или упал (вывод выше)"
fi

if [[ ! -d "$SKL_VISUAL_REVIEW_DIR" ]]; then
    skl_die "тест не оставил каталог $SKL_VISUAL_REVIEW_DIR: тест-генератор не выполнился"
fi
png_count="$(skl_count_png "$SKL_VISUAL_REVIEW_DIR")"
if (( png_count == 0 )); then
    skl_die "каталог листов пуст: флаг $SKL_VISUAL_REVIEW_PROPERTY не дошёл до теста"
fi

mkdir -p "$run_dir"
cp -R "$SKL_VISUAL_REVIEW_DIR/." "$run_dir/"
skl_write_manifest "$run_dir" "$run_dir/manifest.sha256"
manifest_lines="$(skl_count_lines "$run_dir/manifest.sha256")"
if [[ "$manifest_lines" != "$png_count" ]]; then
    skl_die "манифест не сходится с листами: строк $manifest_lines, PNG $png_count"
fi

locales="$(find "$run_dir" -mindepth 1 -maxdepth 1 -type d -exec basename {} \; | LC_ALL=C sort | paste -sd, -)"
java_line="$("$SKL_JAVA_HOME/bin/java" -version 2>&1 | head -n 1)"
git_revision="$(git -C "$SKL_REPO_ROOT" rev-parse --short HEAD 2>/dev/null || echo '-')"
{
    printf 'run: %s\n' "$run_name"
    printf 'captured: %s\n' "$(date '+%Y-%m-%d %H:%M:%S %z')"
    printf 'git: %s\n' "$git_revision"
    printf 'java: %s\n' "$java_line"
    printf 'sheets: %s\n' "$png_count"
    printf 'locales: %s\n' "$locales"
} > "$run_dir/meta.txt"

skl_log "готово: $png_count PNG, языки: $locales"
skl_log "прогон: $run_dir"
skl_log "дальше: ./approve.sh $run_name — утвердить эталон; ./diff.sh — сравнить с эталоном"
printf '%s\n' "$run_dir"
