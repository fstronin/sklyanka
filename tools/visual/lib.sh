#!/usr/bin/env bash
# Общие функции скриптового визуального прогона (SKL-92).
#
# Подключается через source после config.sh. Ничего не выполняет при подключении.

skl_log() { printf '[visual] %s\n' "$*" >&2; }
skl_warn() { printf '[visual] ВНИМАНИЕ: %s\n' "$*" >&2; }
skl_die() {
    printf '[visual] ОТКАЗ: %s\n' "$*" >&2
    exit 1
}

SKL_JAVA_HOME=""

skl_require_java() {
    if [[ -n "$SKL_JAVA_HOME" ]]; then
        export JAVA_HOME="$SKL_JAVA_HOME"
        export PATH="$SKL_JAVA_HOME/bin:$PATH"
        return 0
    fi
    local home major
    home="$(skl_java_home)" || skl_die "Java не найдена: задайте JAVA_HOME (нужна Java 17 или новее)"
    major="$(skl_java_major "$home")"
    [[ -n "$major" ]] || skl_die "не удалось определить версию Java в $home"
    if (( major < 17 )); then
        skl_die "нужна Java 17 или новее, а в $home — $major"
    fi
    SKL_JAVA_HOME="$home"
    export JAVA_HOME="$SKL_JAVA_HOME"
    export PATH="$SKL_JAVA_HOME/bin:$PATH"
}

SKL_SHA_TOOL=""

skl_sha_tool() {
    if [[ -z "$SKL_SHA_TOOL" ]]; then
        if command -v sha256sum >/dev/null 2>&1; then
            SKL_SHA_TOOL="sha256sum"
        elif command -v shasum >/dev/null 2>&1; then
            SKL_SHA_TOOL="shasum"
        else
            skl_die "нет sha256sum или shasum — нечем считать хеши листов"
        fi
    fi
    echo "$SKL_SHA_TOOL"
}

skl_sha256_hex() {
    if [[ "$(skl_sha_tool)" == "sha256sum" ]]; then
        sha256sum "$1" | awk '{print $1}'
    else
        shasum -a 256 "$1" | awk '{print $1}'
    fi
}

# Манифест — предмет ревью и основа сравнения: sha256 и путь относительно каталога,
# сортировка по пути. Картинки различий живут в diff/ и в манифест не входят.
skl_write_manifest() {
    local directory="$1" manifest="$2" file relative
    while IFS= read -r file; do
        relative="${file#"$directory"/}"
        printf '%s  %s\n' "$(skl_sha256_hex "$file")" "$relative"
    done < <(find "$directory" -type f -name '*.png' -not -path "$directory/diff/*" | LC_ALL=C sort) > "$manifest"
}

skl_manifest_hash() {
    local manifest="$1" path="$2"
    awk -v wanted="$path" '$2 == wanted { print $1; found = 1 } END { exit found ? 0 : 1 }' "$manifest"
}

skl_count_png() {
    find "$1" -type f -name '*.png' -not -path "$1/diff/*" | wc -l | tr -d ' '
}

skl_count_lines() {
    if [[ -f "$1" ]]; then
        wc -l < "$1" | tr -d ' '
    else
        echo 0
    fi
}

# Время снятия из meta.txt, а не mtime каталога: diff.sh пересоздаёт diff/ и двигает
# mtime старого прогона, поэтому выбор «последнего» по каталогу зависел бы от сравнений.
skl_timestamp_epoch() {
    local stamp="$1"
    if date -j -f '%Y-%m-%d %H:%M:%S %z' "$stamp" '+%s' >/dev/null 2>&1; then
        date -j -f '%Y-%m-%d %H:%M:%S %z' "$stamp" '+%s'
        return 0
    fi
    if date -d "$stamp" '+%s' >/dev/null 2>&1; then
        date -d "$stamp" '+%s'
        return 0
    fi
    return 1
}

skl_path_mtime() {
    if stat -f '%m' "$1" >/dev/null 2>&1; then
        stat -f '%m' "$1"
    else
        stat -c '%Y' "$1" 2>/dev/null || echo 0
    fi
}

skl_latest_run() {
    local dir entry captured epoch
    local best="" best_epoch=-1
    local fallback="" fallback_epoch=-1
    local without_meta=""
    for dir in "$SKL_RUNS_DIR"/*/; do
        [[ -d "$dir" ]] || continue
        entry="${dir%/}"
        captured=""
        if [[ -f "$entry/meta.txt" ]]; then
            captured="$(sed -n 's/^captured: //p' "$entry/meta.txt" | head -n 1)"
        fi
        if [[ -n "$captured" ]] && epoch="$(skl_timestamp_epoch "$captured")"; then
            if (( epoch > best_epoch )) || { (( epoch == best_epoch )) && [[ -n "$best" && "$entry" > "$best" ]]; }; then
                best="$entry"
                best_epoch="$epoch"
            fi
        else
            without_meta="$without_meta ${entry##*/}"
            epoch="$(skl_path_mtime "$entry")"
            if (( epoch > fallback_epoch )); then
                fallback="$entry"
                fallback_epoch="$epoch"
            fi
        fi
    done
    if [[ -n "$best" ]]; then
        if [[ -n "$without_meta" ]]; then
            skl_warn "у прогонов нет или нечитаем meta.txt:$without_meta — в выборе последнего они не участвуют"
        fi
        printf '%s\n' "$best"
        return 0
    fi
    if [[ -n "$fallback" ]]; then
        skl_warn "пригодного meta.txt нет ни у одного прогона: последний выбран по времени каталога, а не по времени снятия"
        printf '%s\n' "$fallback"
        return 0
    fi
    return 1
}

# Прогон задаётся каталогом, путём или именем в runs/; пусто — последний снятый.
skl_resolve_run() {
    local argument="${1:-}"
    if [[ -z "$argument" ]]; then
        skl_latest_run || skl_die "прогонов нет: сначала ./capture.sh"
        return 0
    fi
    if [[ -d "$argument" ]]; then
        (cd -- "$argument" && pwd)
        return 0
    fi
    if [[ -d "$SKL_RUNS_DIR/$argument" ]]; then
        (cd -- "$SKL_RUNS_DIR/$argument" && pwd)
        return 0
    fi
    skl_die "прогон не найден: $argument (смотрите $SKL_RUNS_DIR)"
}

# Инструмент допуска: PIL точнее и даёт картинку различий; ImageMagick — запасной путь.
skl_pixel_tool() {
    if command -v python3 >/dev/null 2>&1 && python3 -c 'import PIL' >/dev/null 2>&1; then
        echo "python3-pil"
        return 0
    fi
    if command -v magick >/dev/null 2>&1; then
        echo "magick"
        return 0
    fi
    if command -v compare >/dev/null 2>&1; then
        echo "compare"
        return 0
    fi
    echo ""
}
