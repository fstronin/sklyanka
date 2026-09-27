#!/usr/bin/env bash
# Создание образов эмулятора по матрице config.sh (SKL-27).
#
# Идемпотентно: существующий образ не создаётся заново и не стирается. Пакеты SDK
# докачиваются только те, которых нет.
#
#   ./avd.sh create [API...]   создать образы (по умолчанию — вся матрица)
#   ./avd.sh delete [API...]   удалить образы
#   ./avd.sh list              что есть сейчас
set -euo pipefail

SKL_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=config.sh
source "$SKL_DIR/config.sh"
# shellcheck source=lib.sh
source "$SKL_DIR/lib.sh"

# Первый из кандидатов, который SDK вообще знает: состав образов между версиями
# cmdline-tools меняется, и «aosp_atd для API 31» существует не везде.
# Координата пакета в том виде, в котором её печатает список: sdkmanager новых
# cmdline-tools разделяет части пути косой чертой, старый — точкой с запятой, а на вход
# avdmanager принимает только точку с запятой. Сравнивать поэтому приходится
# по приведённому виду, иначе «образа нет в SDK» означает лишь «список другой версии».
# Принимает координату аргументом либо поток целиком — приведение нужно и тому,
# и другому, а две функции об одном отличались бы только способом чтения.
skl_image_path_form() {
    if (( $# > 0 )); then tr ';' '/' <<<"$1"; else tr ';' '/'; fi
}

skl_resolve_image() {
    local api="$1" candidates available candidate
    candidates="$(skl_image_candidates "$api")" || skl_die "уровень API $api не описан в config.sh"
    available="$("$(skl_require_tool sdkmanager)" --list 2>/dev/null | skl_image_path_form || true)"
    for candidate in $candidates; do
        if grep -Fq "$(skl_image_path_form "$candidate")" <<<"$available"; then echo "$candidate"; return 0; fi
    done
    # Список мог не прочитаться (нет сети, нет лицензий): берём первого кандидата
    # и даём sdkmanager сказать своё слово сам — его отказ понятнее нашей догадки.
    skl_warn "список пакетов SDK не прочитан, беру первого кандидата для API $api"
    echo "${candidates%% *}"
}

skl_install_package() {
    local package="$1" sdkmanager
    sdkmanager="$(skl_require_tool sdkmanager)"
    if "$sdkmanager" --list_installed 2>/dev/null | skl_image_path_form | grep -Fq "$(skl_image_path_form "$package")"; then
        skl_log "пакет уже установлен: $package"
        return 0
    fi
    skl_log "установка пакета SDK: $package"
    yes | "$sdkmanager" "$package" >/dev/null || skl_die "не удалось установить $package"
}

skl_create_one() {
    local api="$1" name image
    name="$(skl_avd_name "$api")"
    if skl_avd_exists "$api"; then
        skl_log "образ $name уже есть — пропускаю (создание идемпотентно)"
        return 0
    fi
    image="$(skl_resolve_image "$api")"
    if grep -q "google" <<<"$image"; then
        skl_die "образ $image содержит сервисы Google — ПЛ-4 запрещает такой прогон"
    fi
    skl_install_package "platform-tools"
    skl_install_package "emulator"
    skl_install_package "$image"
    skl_log "создание образа $name из $image"
    printf 'no\n' | "$(skl_require_tool avdmanager)" create avd \
        --name "$name" \
        --package "$image" \
        --device "pixel_5" \
        --sdcard "${SKL_AVD_SDCARD_MB}M" \
        >/dev/null || skl_die "не удалось создать образ $name"
    # Место под объёмные проверки базы и явный запрет анимаций: см. config.sh.
    local config
    config="${ANDROID_AVD_HOME:-$HOME/.android/avd}/$name.avd/config.ini"
    if [[ -f "$config" ]]; then
        {
            echo "disk.dataPartition.size=${SKL_AVD_PARTITION_MB}M"
            echo "hw.keyboard=yes"
        } >>"$config"
    fi
    skl_log "образ $name создан; что проверяет: $(skl_image_purpose "$api")"
}

skl_delete_one() {
    local api="$1" name
    name="$(skl_avd_name "$api")"
    if ! skl_avd_exists "$api"; then
        skl_log "образа $name нет — удалять нечего"
        return 0
    fi
    "$(skl_require_tool avdmanager)" delete avd --name "$name" >/dev/null
    skl_log "образ $name удалён"
}

command="${1:-create}"
shift || true
levels=("$@")
if [[ ${#levels[@]} -eq 0 ]]; then levels=("${SKL_API_LEVELS[@]}"); fi

case "$command" in
    create) for api in "${levels[@]}"; do skl_create_one "$api"; done ;;
    delete) for api in "${levels[@]}"; do skl_delete_one "$api"; done ;;
    list)
        for api in "${SKL_API_LEVELS[@]}"; do
            state="нет"
            skl_avd_exists "$api" && state="есть"
            running="не запущен"
            skl_avd_running "$api" && running="запущен ($(skl_serial "$api"))"
            printf 'API %-3s %-12s %-24s %s\n' "$api" "$state" "$running" "$(skl_image_purpose "$api")"
        done
        ;;
    *) skl_die "неизвестная команда: $command (create|delete|list)" ;;
esac
