#!/usr/bin/env bash
# Запуск образа без окна и ожидание готовности (SKL-27).
#
# Идемпотентно: уже запущенный образ вторым не поднимается.
#
#   ./start.sh [API] [--wipe]
#
# --wipe стирает данные образа. По умолчанию данные СОХРАНЯЮТСЯ: испытания И-1 и И-10
# живут между перезагрузками, и стирающий запуск уничтожал бы предмет измерения.
set -euo pipefail

SKL_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=config.sh
source "$SKL_DIR/config.sh"
# shellcheck source=lib.sh
source "$SKL_DIR/lib.sh"

api="${1:-${SKL_API_LEVELS[0]}}"
wipe="${2:-}"

skl_avd_exists "$api" || skl_die "образа для API $api нет: сначала ./avd.sh create $api"

if skl_avd_running "$api"; then
    skl_log "образ API $api уже запущен как $(skl_serial "$api") — второй не поднимаю"
    skl_wait_boot "$api" || skl_die "запущенный образ не отвечает; погасите его ./stop.sh $api"
    exit 0
fi

name="$(skl_avd_name "$api")"
port="$(skl_avd_port "$api")"
log_file="$(skl_results_dir "$api")/emulator.log"
flags=("${SKL_EMULATOR_FLAGS[@]}")
if [[ "$wipe" == "--wipe" ]]; then
    skl_warn "данные образа $name будут стёрты (--wipe)"
    flags+=("$SKL_WIPE_FLAG")
fi

# Аппаратное ускорение — не требование, а разница в разы. Сказать о его отсутствии
# сразу дешевле, чем через десять минут ожидания загрузки.
if [[ "$(uname -s)" == "Linux" && ! -w /dev/kvm ]]; then
    skl_warn "/dev/kvm недоступен: загрузка и прогон будут в разы медленнее (README.md §1)"
fi

skl_log "запуск $name на порту $port без окна; лог: $log_file"
nohup "$(skl_require_tool emulator)" -avd "$name" -port "$port" "${flags[@]}" \
    >"$log_file" 2>&1 &
emulator_pid=$!
echo "$emulator_pid" >"$(skl_results_dir "$api")/emulator.pid"

if ! skl_wait_boot "$api"; then
    skl_warn "последние строки лога эмулятора:"
    tail -n 20 "$log_file" >&2 || true
    skl_die "образ API $api не загрузился за ${SKL_BOOT_TIMEOUT} с"
fi

serial="$(skl_serial "$api")"
# Анимации мешают только экранным проверкам, но выключаются здесь: это свойство
# стенда, а не отдельного прогона.
skl_adb -s "$serial" shell settings put global window_animation_scale 0 || true
skl_adb -s "$serial" shell settings put global transition_animation_scale 0 || true
skl_adb -s "$serial" shell settings put global animator_duration_scale 0 || true
# Автосинхронизация времени: испытания И-4 … И-6 переводят часы вручную, и система,
# возвращающая время обратно, превращает измерение в шум.
skl_adb -s "$serial" shell settings put global auto_time 0 || true
skl_adb -s "$serial" shell settings put global auto_time_zone 0 || true
skl_platform_line "$api" >"$(skl_results_dir "$api")/platform.txt"
skl_log "готово: $serial"
