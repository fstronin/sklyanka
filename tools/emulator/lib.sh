#!/usr/bin/env bash
# Общие функции оснастки прогона на эмуляторе (SKL-27).
#
# Подключается через source после config.sh. Ничего не выполняет при подключении.

# shellcheck disable=SC2034  # часть значений читают только скрипты-потребители

SKL_TOOLS_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
SKL_REPO_ROOT="$(cd -- "$SKL_TOOLS_DIR/../.." && pwd)"

skl_log() { printf '[skl] %s\n' "$*" >&2; }
skl_warn() { printf '[skl] ВНИМАНИЕ: %s\n' "$*" >&2; }
skl_die() { printf '[skl] ОТКАЗ: %s\n' "$*" >&2; exit 1; }

# Испытание, которое на эмуляторе не воспроизводится: это не ошибка прогона,
# и отличать его от отказа обязан код возврата, а не глаза оператора.
SKL_EXIT_NOT_APPLICABLE=3
skl_not_applicable() {
    printf '[skl] НЕ ВОСПРОИЗВОДИТСЯ НА ЭМУЛЯТОРЕ: %s\n' "$*" >&2
    exit "$SKL_EXIT_NOT_APPLICABLE"
}

# --- SDK -----------------------------------------------------------------------

# Корень SDK: переменные среды, затем local.properties, затем обычные места.
skl_sdk_root() {
    if [[ -n "${ANDROID_SDK_ROOT:-}" ]]; then echo "$ANDROID_SDK_ROOT"; return; fi
    if [[ -n "${ANDROID_HOME:-}" ]]; then echo "$ANDROID_HOME"; return; fi
    local properties="$SKL_REPO_ROOT/local.properties"
    if [[ -f "$properties" ]]; then
        local dir
        dir="$(sed -n 's/^sdk\.dir=//p' "$properties" | tail -n 1)"
        if [[ -n "$dir" ]]; then echo "$dir"; return; fi
    fi
    for candidate in "$HOME/Android/Sdk" "$HOME/Library/Android/sdk" /opt/android-sdk; do
        if [[ -d "$candidate" ]]; then echo "$candidate"; return; fi
    done
    return 1
}

skl_sdk_tool() {
    local name="$1" root
    root="$(skl_sdk_root)" || skl_die "SDK не найден: задайте ANDROID_SDK_ROOT или sdk.dir в local.properties"
    for path in \
        "$root/platform-tools/$name" \
        "$root/emulator/$name" \
        "$root/cmdline-tools/latest/bin/$name" \
        "$root/tools/bin/$name"; do
        if [[ -x "$path" ]]; then echo "$path"; return; fi
    done
    command -v "$name" 2>/dev/null && return
    return 1
}

skl_require_tool() {
    local name="$1" path
    path="$(skl_sdk_tool "$name")" || skl_die "в SDK нет $name — установите пакет SDK (см. README.md §1)"
    echo "$path"
}

skl_adb() { "$(skl_require_tool adb)" "$@"; }

# --- Образы --------------------------------------------------------------------

skl_avd_name() { echo "${SKL_AVD_PREFIX}${1}"; }

# Порт консоли образа: по порядку уровня API в матрице, шагом 2.
skl_avd_port() {
    local api="$1" index=0 level
    for level in "${SKL_API_LEVELS[@]}"; do
        if [[ "$level" == "$api" ]]; then echo $((SKL_FIRST_PORT + index * 2)); return; fi
        index=$((index + 1))
    done
    skl_die "уровень API $api не описан в матрице config.sh"
}

skl_serial() { echo "emulator-$(skl_avd_port "$1")"; }

skl_avd_exists() {
    local name
    name="$(skl_avd_name "$1")"
    "$(skl_require_tool avdmanager)" list avd -c 2>/dev/null | grep -Fxq "$name"
}

# Запущен ли образ: спрашивается у adb, а не у списка процессов — процесс может
# жить, не отвечая, и тогда прогон всё равно невозможен.
skl_avd_running() {
    local serial
    serial="$(skl_serial "$1")"
    skl_adb devices | awk '{print $1}' | grep -Fxq "$serial"
}

# --- Ожидание ------------------------------------------------------------------

skl_wait_boot() {
    local api="$1" serial deadline
    serial="$(skl_serial "$api")"
    deadline=$((SECONDS + SKL_BOOT_TIMEOUT))
    skl_log "ожидание готовности $serial (до ${SKL_BOOT_TIMEOUT} с)"
    while (( SECONDS < deadline )); do
        if [[ "$(skl_adb -s "$serial" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == "1" ]]; then
            # Загрузка завершена ещё не значит, что диспетчер пакетов принимает установку.
            skl_adb -s "$serial" shell 'pm path android' >/dev/null 2>&1 || true
            skl_log "$serial готов: $(skl_platform_line "$api")"
            return 0
        fi
        sleep 3
    done
    return 1
}

# Строка платформы для отчёта: НАП-40 требует фиксировать её вместе с результатом.
skl_platform_line() {
    local serial
    serial="$(skl_serial "$1")"
    local release sdk build
    release="$(skl_adb -s "$serial" shell getprop ro.build.version.release 2>/dev/null | tr -d '\r')"
    sdk="$(skl_adb -s "$serial" shell getprop ro.build.version.sdk 2>/dev/null | tr -d '\r')"
    build="$(skl_adb -s "$serial" shell getprop ro.build.fingerprint 2>/dev/null | tr -d '\r')"
    echo "Android $release (API $sdk), образ $build"
}

# --- Обстановка образа: системная локаль ---------------------------------------

# Действующая системная локаль образа в виде `ru-RU`; пустая строка — образ
# конфигурацию не сообщил.
#
# Читается КОНФИГУРАЦИЯ, а не настройка `system_locales`: запись настройки платформа
# применяет только при следующей загрузке (SKL-119, 16.10.2026: проверено на API 35 и
# повторено опытом — `settings put` в любую сторону конфигурацию не меняет), поэтому
# настройка говорит о намерении, а человек и проверки видят конфигурацию. Проверка по
# одной настройке пропускала бы образ, оставшийся в чужой локали прошлого прогона:
# настройка уже возвращена, а интерфейс приложения и ожидания проверок — французские.
skl_system_locale() {
    local serial="$1" config
    config="$(skl_adb -s "$serial" shell am get-config 2>/dev/null | tr -d '\r' |
        sed -n 's/^config: //p' | head -n 1)"
    [[ -n "$config" ]] || return 0
    # `mcc310-mnc260-ru-rRU-ldltr-…`: язык и страна стоят сразу за mcc и mnc.
    printf '%s\n' "$config" | awk -F- '
        {
            start = 0
            for (i = 1; i <= NF; i++) {
                if ($i ~ /^mnc[0-9]+$/) { start = i + 1; break }
                if ($i ~ /^mcc[0-9]+$/) { start = i + 1 }
            }
            if (start == 0 || $start !~ /^[a-z][a-z][a-z]?$/) { print ""; exit }
            locale = $start
            if ($(start + 1) ~ /^r[A-Z][A-Z]$/) { locale = locale "-" substr($(start + 1), 2) }
            print locale
        }'
}

# Записать системную локаль образа.
#
# Пишется и настройка (то место, куда пишет сама платформа), и свойство `persist.sys.locale`
# (хранилище системной локали до API 33): образов в матрице три, и читает каждый своё.
# Запись применяется при следующей загрузке — см. [skl_system_locale]. Ненулевой код
# возврата — запись не легла; применять её тогда нечего, и решает это вызывающий.
skl_write_system_locale() {
    local serial="$1" locale="$2" status=0
    skl_adb -s "$serial" shell settings put system system_locales "$locale" >/dev/null 2>&1 || status=1
    skl_adb -s "$serial" shell setprop persist.sys.locale "$locale" >/dev/null 2>&1 || true
    return "$status"
}

# Перезагрузить образ и дождаться готовности: единственный способ применить записанную
# системную локаль ([skl_system_locale]) и заодно единственный способ вернуть её назад.
skl_reboot_image() {
    local api="$1" serial
    serial="$(skl_serial "$api")"
    skl_log "перезагрузка $serial: записанная локаль применяется при загрузке"
    skl_adb -s "$serial" reboot >/dev/null 2>&1 || skl_die "перезагрузить $serial не удалось"
    skl_adb -s "$serial" wait-for-device >/dev/null 2>&1 || true
    skl_wait_boot "$api" || skl_die "$serial не загрузился после перезагрузки"
}

# Root на образе AOSP разрешён и нужен испытаниям: смена пояса, перевод часов,
# чтение базы приложения. На образе с Google APIs его нет — ещё одна причина
# держать матрицу без GMS.
skl_adb_root() {
    local serial
    serial="$(skl_serial "$1")"
    skl_adb -s "$serial" root >/dev/null 2>&1 || true
    skl_adb -s "$serial" wait-for-device >/dev/null 2>&1 || true
}

skl_app_installed() {
    local serial
    serial="$(skl_serial "$1")"
    skl_adb -s "$serial" shell pm path "$SKL_APP_ID" >/dev/null 2>&1
}

skl_results_dir() {
    local api="$1" dir
    dir="$SKL_REPO_ROOT/$SKL_RESULTS_DIR/api$api"
    mkdir -p "$dir"
    echo "$dir"
}

# Единая точка вызова Gradle: тот же серийный номер, что у остальных шагов.
#
# Каталог сборки задаётся явно, а не наследуется: скрипты оснастки запускаются из
# tools/emulator, а обёртка считает проектом текущий каталог — и Gradle отказывается
# работать, потому что tools/emulator не входит ни в одну сборку.
skl_gradle() {
    local api="$1"; shift
    ANDROID_SERIAL="$(skl_serial "$api")" "$SKL_REPO_ROOT/gradlew" \
        --project-dir "$SKL_REPO_ROOT" --console=plain "$@"
}

# --- Журнал напоминаний --------------------------------------------------------
#
# Источник чисел прогона — таблица reminder_log приложения (НАП-4, НФТ-50), а не разбор
# системного лога: в журнале уже есть плановое время, фактическое и отклонение, и он
# устроен так, что медицинских данных в нём нет ни в одной колонке (НФТ-19).

# Снять файлы базы с образа. Вместе с -wal и -shm: Room ведёт журнал упреждающей
# записи, и база без него читается «на несколько последних записей назад».
skl_pull_db() {
    local api="$1" out_dir="$2" serial suffix source_path
    serial="$(skl_serial "$api")"
    mkdir -p "$out_dir"
    skl_adb_root "$api"
    for suffix in "" "-wal" "-shm"; do
        source_path="/data/data/$SKL_APP_ID/databases/${SKL_APP_DB}${suffix}"
        if skl_adb -s "$serial" shell "test -f $source_path" >/dev/null 2>&1; then
            skl_adb -s "$serial" pull "$source_path" "$out_dir/" >/dev/null 2>&1 ||
                skl_adb -s "$serial" exec-out "run-as $SKL_APP_ID cat databases/${SKL_APP_DB}${suffix}" \
                    >"$out_dir/${SKL_APP_DB}${suffix}"
        fi
    done
    [[ -s "$out_dir/$SKL_APP_DB" ]] || skl_die "базу приложения снять не удалось: приложение не запускалось?"
    echo "$out_dir/$SKL_APP_DB"
}

# Один запрос к журналу. Читается на хосте, по снятой копии: sqlite3 есть не на каждом
# образе, а python3 нужен прогону всё равно — разбором результатов.
skl_journal_query() {
    local api="$1" sql="$2" work db
    work="$(skl_results_dir "$api")/journal"
    db="$(skl_pull_db "$api" "$work")"
    python3 - "$db" "$sql" <<'PY'
import sqlite3, sys
db, sql = sys.argv[1], sys.argv[2]
connection = sqlite3.connect(f"file:{db}?mode=ro", uri=True)
for row in connection.execute(sql):
    print("\t".join("" if value is None else str(value) for value in row))
PY
}

# Момент «сейчас» в миллисекундах UTC по часам образа, а не по часам машины:
# испытания И-4 … И-6 эти часы двигают, и сравнение с хостом дало бы чушь.
skl_device_now_ms() {
    local serial seconds
    serial="$(skl_serial "$1")"
    seconds="$(skl_adb -s "$serial" shell date +%s 2>/dev/null | tr -d '\r')"
    [[ "$seconds" =~ ^[0-9]+$ ]] || skl_die "часы образа не прочитались: образ отвечает?"
    echo $(( seconds * 1000 ))
}
