#!/usr/bin/env bash
# Испытания выживаемости И-1 … И-16 на эмуляторе (SKL-27).
#
# Разбор каждого испытания — в таблице SKL_TRIALS ниже: она единственный источник
# ответа «автоматизируется или нет и чем». Печатается командой ./trials.sh list.
#
#   ./trials.sh list            таблица разбора
#   ./trials.sh i1 [API]        провести испытание
#   ./trials.sh auto [API]      провести все автоматизируемые подряд
#
# Код возврата: 0 — проведено, 1 — отказ, 3 — на эмуляторе не воспроизводится.
#
# Что испытание НЕ делает: оно не судит о гейте. Гейт НАП-3 считается по прогону
# на живом эталонном устройстве Huawei (SKL-03); здесь проверяется механика.
set -euo pipefail

SKL_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=config.sh
source "$SKL_DIR/config.sh"
# shellcheck source=lib.sh
source "$SKL_DIR/lib.sh"

# код | сценарий | автоматизация | чем воспроизводится
SKL_TRIALS=(
    "И-1|Перезагрузка|автоматизируется|adb reboot, ожидание sys.boot_completed, проверка записи rescheduled с причиной boot (НФТ-2)"
    "И-2|Doze штатный|частично|dumpsys battery unplug + deviceidle step до состояния IDLE; фирменных ограничений EMUI не воспроизводит"
    "И-3|Doze принудительный|автоматизируется|dumpsys deviceidle force-idle, проверка get deep, выход через unforce"
    "И-4|Смена пояса вперёд|автоматизируется|cmd alarm set-timezone либо setprop persist.sys.timezone, проверка rescheduled с причиной timezone_changed (НФТ-4)"
    "И-5|Смена пояса назад|автоматизируется|cmd alarm set-timezone в обратную сторону; проверяется отсутствие второго напоминания на тот же intake_id (НАП-9)"
    "И-6|Ручной перевод часов|автоматизируется|settings put global auto_time 0 + cmd alarm set-time (или date как root), проверка rescheduled с причиной time_changed"
    "И-7|Принудительная остановка|автоматизируется|am force-stop, ожидание пропущенного планового момента, проверка записи outage после ручного запуска (НАП-38)"
    "И-8|Смахивание из недавних|частично|am kill — ближайший эквивалент смахивания; настоящий жест по списку недавних на эмуляторе без окна недоступен"
    "И-9|Переход через полночь|автоматизируется|нагрузка с приёмами 23:55 и 00:05 (seed.sh --mode day) плюс перевод часов на 23:50"
    "И-10|Истечение окна на выключенном устройстве|автоматизируется|adb emu kill, cmd alarm set-time за конец суток, повторный запуск, проверка записей missed_sweep без всплеска показов (НАП-34)"
    "И-11|Обновление приложения|автоматизируется|install -r той же подписью, проверка rescheduled с причиной package_replaced"
    "И-12|Энергосбережение EMUI без автозапуска|НЕ ВОСПРОИЗВОДИТСЯ|это прошивка Huawei, а не Android: на образе AOSP нет ни менеджера энергосбережения EMUI, ни настройки автозапуска. Измеряется только на живом устройстве (SKL-03)"
    "И-13|Экстремальное энергосбережение|НЕ ВОСПРОИЗВОДИТСЯ|режим сверхэкономии — фирменный механизм EMUI; штатный battery saver Android ему не эквивалентен и подменять его значило бы измерять не то"
    "И-14|Отказ в разрешении на уведомления|автоматизируется|pm revoke POST_NOTIFICATIONS (API 33+), проверка записи suppressed и предупреждения; на API 26 и 31 разрешения не существует — испытание неприменимо к образу"
    "И-15|Отказ в точных будильниках|частично|appops set SCHEDULE_EXACT_ALARM deny воспроизводит отказ только на API 31 и 32: с API 33 манифест объявляет USE_EXACT_ALARM, и canScheduleExactAlarms() остаётся true независимо от appops"
    "И-16|Длинный прогон 14 суток|НЕ ЗАМЕНЯЕТСЯ|ускоренный прогон (seed.sh --mode fast) проверяет механику на сотнях срабатываний за часы; гейт НАП-3 требует 14 суток непрерывно на живом эталоне и ускоренным прогоном НЕ закрывается"
)

skl_trials_list() {
    printf '%-6s %-42s %-18s %s\n' "КОД" "СЦЕНАРИЙ" "АВТОМАТИЗАЦИЯ" "ЧЕМ"
    local row
    for row in "${SKL_TRIALS[@]}"; do
        IFS='|' read -r code name verdict how <<<"$row"
        printf '%-6s %-42s %-18s %s\n' "$code" "$name" "$verdict" "$how"
    done
}

# --- Общая обвязка испытания ---------------------------------------------------

skl_trial_begin() {
    SKL_TRIAL_CODE="$1"
    SKL_TRIAL_API="$2"
    SKL_TRIAL_SERIAL="$(skl_serial "$SKL_TRIAL_API")"
    skl_avd_running "$SKL_TRIAL_API" || skl_die "образ API $SKL_TRIAL_API не запущен: ./start.sh $SKL_TRIAL_API"
    skl_app_installed "$SKL_TRIAL_API" || skl_die "приложение не установлено: ./install.sh $SKL_TRIAL_API"
    SKL_TRIAL_MARK="$(skl_device_now_ms "$SKL_TRIAL_API")"
    skl_log "$SKL_TRIAL_CODE начато на $SKL_TRIAL_SERIAL, отметка $SKL_TRIAL_MARK"
    # Отметка испытания в результатах прогона: без неё при разборе невозможно отличить
    # «напоминание не пришло, потому что мы выключили устройство» от настоящего отказа
    # (SKL-03/TDD §6.2).
    printf '%s\t%s\t%s\n' "$SKL_TRIAL_MARK" "$SKL_TRIAL_CODE" "начато" \
        >>"$(skl_results_dir "$SKL_TRIAL_API")/trial-marks.tsv"
}

skl_trial_end() {
    local verdict="$1"
    printf '%s\t%s\t%s\n' "$(skl_device_now_ms "$SKL_TRIAL_API")" "$SKL_TRIAL_CODE" "$verdict" \
        >>"$(skl_results_dir "$SKL_TRIAL_API")/trial-marks.tsv"
    skl_log "$SKL_TRIAL_CODE: $verdict"
}

skl_shell() { skl_adb -s "$SKL_TRIAL_SERIAL" shell "$@"; }

# Сколько записей журнала данного вида появилось после отметки начала испытания.
skl_journal_since() {
    local event="$1" reason="${2:-}" sql
    sql="SELECT COUNT(*) FROM reminder_log WHERE occurred_at >= $SKL_TRIAL_MARK AND event = '$event'"
    [[ -n "$reason" ]] && sql="$sql AND reason = '$reason'"
    skl_journal_query "$SKL_TRIAL_API" "$sql" | head -n 1
}

skl_expect_journal() {
    local event="$1" reason="${2:-}" count
    count="$(skl_journal_since "$event" "$reason")"
    if [[ "${count:-0}" -gt 0 ]]; then
        skl_log "в журнале есть $event${reason:+ (причина $reason)}: $count"
        return 0
    fi
    skl_warn "в журнале НЕТ записи $event${reason:+ (причина $reason)} после отметки испытания"
    return 1
}

skl_launch_app() {
    skl_shell "monkey -p $SKL_APP_ID -c android.intent.category.LAUNCHER 1" >/dev/null 2>&1 || true
    sleep 5
}

# Ожидание планового момента: испытание не угадывает время, а ждёт следующего
# запланированного приёма из базы приложения.
skl_next_scheduled_ms() {
    skl_journal_query "$SKL_TRIAL_API" \
        "SELECT MIN(scheduled_at) FROM reminder_log WHERE scheduled_at IS NOT NULL AND scheduled_at > $SKL_TRIAL_MARK" |
        head -n 1
}

# --- Испытания -----------------------------------------------------------------

skl_trial_i1() { # Перезагрузка (НФТ-2)
    skl_trial_begin "И-1" "$1"
    skl_launch_app
    skl_log "перезагрузка образа"
    skl_adb -s "$SKL_TRIAL_SERIAL" reboot
    sleep 5
    skl_wait_boot "$SKL_TRIAL_API" || skl_die "образ не вернулся из перезагрузки"
    # НФТ-2 требует восстановления не позднее 60 секунд после BOOT_COMPLETED.
    sleep 60
    if skl_expect_journal "rescheduled" "boot"; then
        skl_trial_end "проведено"
    else
        skl_trial_end "отказ: пересборки по причине boot нет"
        return 1
    fi
}

skl_trial_i2() { # Doze штатный — воспроизводится частично
    skl_trial_begin "И-2" "$1"
    skl_warn "штатный Doze на эмуляторе воспроизводится частично: движения и питания нет по определению, фирменных ограничений EMUI нет вовсе"
    skl_shell "dumpsys battery unplug" >/dev/null
    skl_shell "dumpsys deviceidle enable" >/dev/null || true
    local state attempt
    for attempt in 1 2 3 4 5 6 7 8 9 10; do
        state="$(skl_shell "dumpsys deviceidle step" | tr -d '\r')"
        skl_log "шаг $attempt: $state"
        [[ "$state" == *IDLE* && "$state" != *IDLE_PENDING* ]] && break
        sleep 5
    done
    skl_trial_end "проведено частично: состояние $state"
}

skl_trial_i3() { # Doze принудительный
    skl_trial_begin "И-3" "$1"
    skl_shell "dumpsys battery unplug" >/dev/null
    skl_shell "dumpsys deviceidle enable" >/dev/null || true
    skl_shell "dumpsys deviceidle force-idle" >/dev/null
    skl_log "состояние глубокого idle: $(skl_shell "dumpsys deviceidle get deep" | tr -d '\r')"
    local next now wait_seconds
    next="$(skl_next_scheduled_ms)"
    if [[ -n "$next" ]]; then
        now="$(skl_device_now_ms "$SKL_TRIAL_API")"
        wait_seconds=$(( (next - now) / 1000 + 180 ))
        (( wait_seconds > 0 )) && { skl_log "жду планового момента: ${wait_seconds} с"; sleep "$wait_seconds"; }
    else
        skl_warn "в журнале нет будущего планового момента: нагрузка не заведена (./seed.sh)"
    fi
    skl_shell "dumpsys deviceidle unforce" >/dev/null || true
    skl_shell "dumpsys battery reset" >/dev/null || true
    if skl_expect_journal "shown"; then
        skl_trial_end "проведено"
    else
        skl_trial_end "отказ: показа уведомления в Doze нет"
        return 1
    fi
}

skl_set_timezone() {
    local zone="$1"
    skl_adb_root "$SKL_TRIAL_API"
    skl_shell "cmd alarm set-timezone $zone" >/dev/null 2>&1 ||
        skl_shell "setprop persist.sys.timezone $zone" >/dev/null 2>&1 ||
        skl_die "сменить часовой пояс не удалось ни cmd alarm, ни setprop"
    sleep 30 # НФТ-4: пересборка обязана уложиться в 30 секунд после события
}

skl_trial_i4() { # Смена пояса вперёд
    skl_trial_begin "И-4" "$1"
    skl_set_timezone "Asia/Yekaterinburg"
    if skl_expect_journal "rescheduled" "timezone_changed"; then
        skl_trial_end "проведено"
    else
        skl_trial_end "отказ: пересборки по смене пояса нет"
        return 1
    fi
}

skl_trial_i5() { # Смена пояса назад + идемпотентность
    skl_trial_begin "И-5" "$1"
    skl_set_timezone "Europe/Kaliningrad"
    local duplicates
    duplicates="$(skl_journal_query "$SKL_TRIAL_API" \
        "SELECT COUNT(*) FROM (SELECT intake_id FROM reminder_log WHERE event = 'shown' AND intake_id IS NOT NULL GROUP BY intake_id, attempt HAVING COUNT(*) > 1)" | head -n 1)"
    skl_expect_journal "rescheduled" "timezone_changed" || { skl_trial_end "отказ: пересборки нет"; return 1; }
    if [[ "${duplicates:-0}" -gt 0 ]]; then
        skl_trial_end "отказ: $duplicates приёмов показаны повторно — идемпотентность нарушена (НАП-9)"
        return 1
    fi
    skl_trial_end "проведено"
}

skl_trial_i6() { # Ручной перевод часов
    skl_trial_begin "И-6" "$1"
    skl_adb_root "$SKL_TRIAL_API"
    skl_shell "settings put global auto_time 0" >/dev/null
    local forward
    forward=$(( $(skl_device_now_ms "$SKL_TRIAL_API") + 3600000 ))
    skl_shell "cmd alarm set-time $forward" >/dev/null 2>&1 ||
        skl_shell "date $(date -u -d @$((forward / 1000)) +%m%d%H%M%Y.%S 2>/dev/null || echo)" >/dev/null 2>&1 ||
        skl_die "перевести часы не удалось: нужен root на образе (adb root) либо cmd alarm set-time"
    sleep 30
    if skl_expect_journal "rescheduled" "time_changed"; then
        skl_trial_end "проведено"
    else
        skl_trial_end "отказ: пересборки по переводу часов нет"
        return 1
    fi
}

skl_trial_i7() { # Принудительная остановка (НАП-38)
    skl_trial_begin "И-7" "$1"
    skl_launch_app
    skl_shell "am force-stop $SKL_APP_ID" >/dev/null
    skl_log "приложение остановлено принудительно; жду плановый момент"
    local next now wait_seconds
    next="$(skl_next_scheduled_ms)"
    if [[ -n "$next" ]]; then
        now="$(skl_device_now_ms "$SKL_TRIAL_API")"
        wait_seconds=$(( (next - now) / 1000 + 120 ))
        (( wait_seconds > 0 )) && sleep "$wait_seconds"
    fi
    skl_launch_app
    sleep 15
    if skl_expect_journal "outage"; then
        skl_trial_end "проведено: перерыв обнаружен приложением"
    else
        skl_trial_end "отказ: приложение не заметило перерыва (НАП-38)"
        return 1
    fi
}

skl_trial_i8() { # Смахивание из недавних — эквивалент
    skl_trial_begin "И-8" "$1"
    skl_launch_app
    skl_warn "настоящий жест смахивания на образе без окна недоступен: am kill — ближайший эквивалент (процесс снимается, будильники остаются)"
    skl_shell "am kill $SKL_APP_ID" >/dev/null
    local next now wait_seconds
    next="$(skl_next_scheduled_ms)"
    if [[ -n "$next" ]]; then
        now="$(skl_device_now_ms "$SKL_TRIAL_API")"
        wait_seconds=$(( (next - now) / 1000 + 120 ))
        (( wait_seconds > 0 )) && sleep "$wait_seconds"
    fi
    if skl_expect_journal "shown"; then
        skl_trial_end "проведено частично"
    else
        skl_trial_end "отказ: после снятия процесса напоминание не пришло"
        return 1
    fi
}

skl_trial_i9() { # Переход через полночь
    skl_trial_begin "И-9" "$1"
    skl_adb_root "$SKL_TRIAL_API"
    skl_warn "испытание требует нагрузки с приёмами 23:55 и 00:05: ./seed.sh $SKL_TRIAL_API --mode day"
    local target
    target="$(python3 -c 'import time,datetime; print(int(datetime.datetime.combine(datetime.date.today(), datetime.time(23,50)).timestamp()*1000))')"
    skl_shell "cmd alarm set-time $target" >/dev/null 2>&1 || skl_die "перевести часы к 23:50 не удалось"
    sleep 900 # 23:50 → 00:05 плюс запас
    local across
    across="$(skl_journal_query "$SKL_TRIAL_API" \
        "SELECT COUNT(*) FROM reminder_log WHERE event = 'shown' AND occurred_at >= $SKL_TRIAL_MARK" | head -n 1)"
    if [[ "${across:-0}" -ge 2 ]]; then
        skl_trial_end "проведено: показов через полночь $across"
    else
        skl_trial_end "отказ: показов через полночь $across, ожидалось не менее двух"
        return 1
    fi
}

skl_trial_i10() { # Истечение окна на выключенном устройстве (НАП-34)
    skl_trial_begin "И-10" "$1"
    local api="$SKL_TRIAL_API"
    skl_log "гашу образ на «ночь»"
    "$SKL_DIR/stop.sh" "$api"
    "$SKL_DIR/start.sh" "$api"
    SKL_TRIAL_SERIAL="$(skl_serial "$api")"
    skl_adb_root "$api"
    local tomorrow
    tomorrow="$(python3 -c 'import datetime; print(int((datetime.datetime.now()+datetime.timedelta(days=1)).replace(hour=9,minute=0,second=0).timestamp()*1000))')"
    skl_shell "cmd alarm set-time $tomorrow" >/dev/null 2>&1 || skl_warn "часы вперёд перевести не удалось: испытание неполное"
    skl_launch_app
    sleep 30
    if skl_expect_journal "missed_sweep"; then
        skl_trial_end "проведено"
    else
        skl_trial_end "отказ: непринятые приёмы не переведены в «пропущено»"
        return 1
    fi
}

skl_trial_i11() { # Обновление приложения
    skl_trial_begin "И-11" "$1"
    "$SKL_DIR/install.sh" "$SKL_TRIAL_API"
    sleep 30
    if skl_expect_journal "rescheduled" "package_replaced"; then
        skl_trial_end "проведено"
    else
        skl_trial_end "отказ: пересборки по установке поверх нет"
        return 1
    fi
}

skl_trial_i12() { # Энергосбережение EMUI
    skl_not_applicable "И-12: автозапуск и энергосбережение EMUI — прошивка Huawei, а не Android. На образе AOSP этих механизмов нет, и подменять их штатным battery saver значило бы измерять другое. Измеряется только на живом устройстве — SKL-03, критерий приёмки 8"
}

skl_trial_i13() { # Экстремальное энергосбережение EMUI
    skl_not_applicable "И-13: режим сверхэкономии EMUI на образе AOSP отсутствует. Именно из-за И-12 и И-13 гейт НАП-3 остаётся за живым Huawei: эмулятор его не закрывает"
}

skl_trial_i14() { # Отказ в разрешении на уведомления
    skl_trial_begin "И-14" "$1"
    local sdk
    sdk="$(skl_shell "getprop ro.build.version.sdk" | tr -d '\r')"
    if (( sdk < 33 )); then
        skl_trial_end "неприменимо к этому образу: POST_NOTIFICATIONS появилось на API 33, здесь API $sdk"
        return 0
    fi
    skl_shell "pm revoke $SKL_APP_ID android.permission.POST_NOTIFICATIONS" >/dev/null
    local next now wait_seconds
    next="$(skl_next_scheduled_ms)"
    if [[ -n "$next" ]]; then
        now="$(skl_device_now_ms "$SKL_TRIAL_API")"
        wait_seconds=$(( (next - now) / 1000 + 120 ))
        (( wait_seconds > 0 )) && sleep "$wait_seconds"
    fi
    local outcome=0
    skl_expect_journal "suppressed" || outcome=1
    skl_shell "pm grant $SKL_APP_ID android.permission.POST_NOTIFICATIONS" >/dev/null || true
    if (( outcome == 0 )); then
        skl_trial_end "проведено: отказ показа зафиксирован, а не проигнорирован"
    else
        skl_trial_end "отказ: приложение промолчало об отозванном разрешении"
        return 1
    fi
}

skl_trial_i15() { # Отказ в точных будильниках
    skl_trial_begin "И-15" "$1"
    local sdk
    sdk="$(skl_shell "getprop ro.build.version.sdk" | tr -d '\r')"
    if (( sdk < 31 )); then
        skl_trial_end "неприменимо к этому образу: особого доступа к точным будильникам до API 31 нет, здесь API $sdk"
        return 0
    fi
    if (( sdk >= 33 )); then
        skl_warn "на API $sdk манифест объявляет USE_EXACT_ALARM, и canScheduleExactAlarms() остаётся true независимо от appops: отказ воспроизводится только на API 31 и 32 либо сборкой без этого разрешения"
    fi
    skl_shell "appops set $SKL_APP_ID SCHEDULE_EXACT_ALARM deny" >/dev/null
    skl_launch_app
    sleep 20
    local denied
    denied="$(skl_journal_query "$SKL_TRIAL_API" \
        "SELECT COUNT(*) FROM reminder_log WHERE occurred_at >= $SKL_TRIAL_MARK AND event = 'rescheduled' AND alarms_scheduled = 0" | head -n 1)"
    skl_shell "appops set $SKL_APP_ID SCHEDULE_EXACT_ALARM allow" >/dev/null || true
    skl_trial_end "проведено частично: пересборок без поставленных будильников ${denied:-0}"
}

skl_trial_i16() { # Длинный прогон
    skl_trial_begin "И-16" "$1"
    skl_warn "ускоренный прогон проверяет механику на сотнях срабатываний за часы; гейт НАП-3 (95 % за 14 суток непрерывно на живом эталоне) им НЕ закрывается"
    local hours="${2:-4}"
    skl_log "ускоренный прогон: ${hours} ч, шаг приёмов ${SKL_FAST_STEP_MINUTES} мин, нагрузка ${SKL_LOAD_MEDICATIONS}×${SKL_LOAD_TIMES_PER_DAY}"
    sleep $(( hours * 3600 ))
    "$SKL_DIR/collect.sh" "$SKL_TRIAL_API"
    skl_trial_end "проведено как ускоренный прогон, не как гейт"
}

# --- Разбор команды ------------------------------------------------------------

command="${1:-list}"
api="${2:-${SKL_API_LEVELS[0]}}"

case "$command" in
    list) skl_trials_list ;;
    i1|i2|i3|i4|i5|i6|i7|i8|i9|i10|i11|i12|i13|i14|i15|i16) "skl_trial_$command" "$api" "${3:-}" ;;
    auto)
        # Всё, что воспроизводится без ожидания суток: И-12, И-13 и И-16 сюда не входят
        # осознанно — первые два не воспроизводятся вовсе, третий длится часами.
        for trial in i1 i3 i4 i5 i6 i11 i14 i15; do
            skl_log "=== $trial ==="
            "skl_trial_$trial" "$api" || skl_warn "$trial не прошло"
        done
        ;;
    *) skl_die "неизвестная команда: $command (list|i1..i16|auto)" ;;
esac
