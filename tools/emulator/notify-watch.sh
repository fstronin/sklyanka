#!/usr/bin/env bash
# Независимое подтверждение факта показа уведомления (SKL-27, SKL-03/TDD §4.2).
#
# Зачем отдельный наблюдатель. Приложение теперь подтверждает предъявление само:
# отметка «показано» ставится по факту появления уведомления среди активных, а не сразу
# после вызова показа (`HandleFiredReminderUseCase`, SKL-03/TDD §4.2). Но подтверждает
# его приложение — о себе. Наблюдатель опрашивает системный список уведомлений со стороны
# и даёт отметку, независимую от приложения: расхождение между двумя отметками само
# по себе находка, и ради неё наблюдатель и существует.
#
#   ./notify-watch.sh [API] [интервал_секунд]
#
# Пишет TSV: момент_образа_мс, ключ_уведомления. Останавливается по Ctrl+C или сигналу.
set -euo pipefail

SKL_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=config.sh
source "$SKL_DIR/config.sh"
# shellcheck source=lib.sh
source "$SKL_DIR/lib.sh"

api="${1:-${SKL_API_LEVELS[0]}}"
interval="${2:-1}"
serial="$(skl_serial "$api")"
out="$(skl_results_dir "$api")/notifications.tsv"

skl_avd_running "$api" || skl_die "образ API $api не запущен"
skl_log "наблюдатель уведомлений пишет в $out, интервал ${interval} с"

seen_file="$(mktemp)"
trap 'rm -f "$seen_file"' EXIT

while true; do
    now="$(skl_device_now_ms "$api")"
    # --noredact нужен, чтобы список не прятал содержимое; имена лекарств отсюда
    # НЕ берутся (НФТ-19) — записывается только ключ уведомления.
    keys="$(skl_adb -s "$serial" shell "dumpsys notification --noredact" 2>/dev/null |
        grep -oE "NotificationRecord\(0x[0-9a-f]+: pkg=${SKL_APP_ID}[^)]*" |
        grep -oE 'id=[0-9]+[^ ]*' || true)"
    while read -r key; do
        [[ -z "$key" ]] && continue
        if ! grep -Fxq "$key" "$seen_file" 2>/dev/null; then
            echo "$key" >>"$seen_file"
            printf '%s\t%s\n' "$now" "$key" >>"$out"
        fi
    done <<<"$keys"
    sleep "$interval"
done
