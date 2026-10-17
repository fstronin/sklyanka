#!/usr/bin/env bash
# Быстрый пробник доставки настройки на Huawei (SKL-89).
#
#   ./probe.sh [--serial S] [--install] [--strategy alarm_clock|exact_idle]
#              [--probes +2,+5,+10] [--wait-min N] [--quick]
#              [--scenario none|freeze|kill|standby|doze|force-stop]
#              [--out DIR] [--csv] [--any-device] [--open-emui] [--help]
#
# Зачем. Пилот SKL-03 идёт 14 суток, а после каждого изменения настроек EMUI нужно
# видеть результат за минуты: инцидент 01.10.2026 (EMUI удержал девять напоминаний
# до вывода приложения на передний план) проверяется коротким прогоном в фоне.
#
# Что делает. Собирает (по --install) отладочную сборку с режимом измерения, ставит её,
# выдаёт разрешения, ставит тестовые срабатывания через настоящий механизм приложения
# (debug-хук SKL-89), воспроизводит сценарий среды (фон, заморозка, Doze, App Standby,
# force-stop), снимает logcat, dumpsys и журнал, считает отклонение каждой пробы
# и кладёт markdown-отчёт (и CSV по --csv) в каталог --out.
#
# Пробник не заменяет гейт НАП-3: он показывает, доставляется ли механизм в среде сейчас,
# а не долю вовремя за 14 суток. Настройки EMUI «Запуск приложений» скрипт не меняет —
# их ставит человек один раз (--open-emui открывает экран am start).
#
# Код возврата: 0 — все пробы вовремя (или force-stop: документированный ожидаемый
# отказ), 1 — отказ прогона или недоставленные пробы.
set -euo pipefail

PROBE_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "$PROBE_DIR/../.." && pwd)"
# shellcheck source=../emulator/lib.sh
source "$PROBE_DIR/../emulator/lib.sh"

APP_ID="ru.stohastika.sklyanka"
APK="$REPO_ROOT/app/build/outputs/apk/free/debug/app-free-debug.apk"
LOG_TAG="SklyankaProbe"
ACTION_SCHEDULE="ru.stohastika.sklyanka.debug.SCHEDULE_PROBE"
ACTION_STRATEGY="ru.stohastika.sklyanka.debug.SET_PROBE_STRATEGY"

serial="${ANDROID_SERIAL:-}"
install=0
strategy=""
probes=""
wait_min=""
quick=0
scenario="none"
out=""
csv=0
any_device=0
open_emui=0

usage() {
    cat <<'EOF'
Быстрый пробник доставки настройки на Huawei (SKL-89)

Использование:
  ./probe.sh [флаги]

Флаги:
  --serial S           последовательный номер устройства (по умолчанию — единственное подключённое)
  --install            собрать :app:assembleFreeDebug с -Psklyanka.reminderMeasurement=true и adb install -r -g
  --strategy ВАРИАНТ   записать вариант измерения: alarm_clock | exact_idle (настройка SKL-85)
  --probes +2,+5,+10   смещения проб в минутах от момента постановки (по умолчанию +2,+5,+10)
  --wait-min N         ждать N минут после последней пробы, прежде чем снимать артефакты (по умолчанию 5)
  --quick              короткий смоук: по умолчанию --probes +1,+2, --wait-min 2, журнал не снимается
  --scenario СЦЕНАРИЙ  none | freeze | kill | standby | doze | force-stop (по умолчанию none)
  --out DIR            каталог артефактов (по умолчанию build/spike/probe-ГГГГММДД-ЧЧММСС)
  --csv                дополнительно выгрузить probes.csv
  --any-device         не требовать Huawei/EMUI (замер механики, а не среды)
  --open-emui          открыть экран EMUI «Запуск приложений» и выйти (настройки ставит человек)
  --help               эта справка

Что кладётся в --out: report.md, probe.log, alarm-scheduled.txt, alarm-after.txt,
notification.txt, platform.txt, probes.csv (по --csv) и каталог journal/ (кроме --quick).

Код возврата: 0 — доставлено всё вовремя (force-stop — ожидаемый отказ); 1 — иначе.
EOF
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --serial) serial="$2"; shift 2 ;;
        --install) install=1; shift ;;
        --strategy) strategy="$2"; shift 2 ;;
        --probes) probes="$2"; shift 2 ;;
        --wait-min) wait_min="$2"; shift 2 ;;
        --quick) quick=1; shift ;;
        --scenario) scenario="$2"; shift 2 ;;
        --out) out="$2"; shift 2 ;;
        --csv) csv=1; shift ;;
        --any-device) any_device=1; shift ;;
        --open-emui) open_emui=1; shift ;;
        --help|-h) usage; exit 0 ;;
        *) skl_die "неизвестный аргумент: $1 (см. --help)" ;;
    esac
done

case "$scenario" in
    none|freeze|kill|standby|doze|force-stop) ;;
    *) skl_die "неизвестный сценарий: $scenario (none|freeze|kill|standby|doze|force-stop)" ;;
esac
if [[ -n "$strategy" && "$strategy" != "alarm_clock" && "$strategy" != "exact_idle" ]]; then
    skl_die "неизвестный вариант измерения: $strategy (alarm_clock|exact_idle)"
fi

if (( quick )); then
    [[ -n "$probes" ]] || probes="+1,+2"
    [[ -n "$wait_min" ]] || wait_min=2
fi
[[ -n "$probes" ]] || probes="+2,+5,+10"
[[ -n "$wait_min" ]] || wait_min=5
[[ "$wait_min" =~ ^[0-9]+$ ]] || skl_die "--wait-min должен быть целым числом минут"

# Смещения нормализуются в "2,5,10": adb принимает массив целых без знаков.
probe_list="$(printf '%s' "$probes" | tr -d ' ')"
probe_list="${probe_list//+/}"
IFS=',' read -r -a probe_minutes <<<"$probe_list"
probe_count=0
last_offset=0
for minute in "${probe_minutes[@]}"; do
    [[ "$minute" =~ ^[0-9]+$ ]] || skl_die "смещение пробы не целое число минут: $minute"
    (( minute > 0 )) || skl_die "смещение пробы должно быть положительным: $minute"
    if (( minute > last_offset )); then last_offset="$minute"; fi
    probe_count=$(( probe_count + 1 ))
done
(( probe_count >= 1 && probe_count <= 32 )) || skl_die "проб должно быть от 1 до 32: $probe_count"

try_resolve_serial() {
    local devices count
    if [[ -n "$serial" ]]; then
        return 0
    fi
    devices="$(skl_adb devices 2>/dev/null | awk 'NR>1 && $2=="device" {print $1}')"
    [[ -n "$devices" ]] || return 1
    count="$(printf '%s\n' "$devices" | grep -c . || true)"
    serial="$(printf '%s\n' "$devices" | head -n 1)"
    (( count == 1 )) || skl_warn "устройств несколько: выбран первый ($serial); нужное задайте --serial"
    return 0
}

try_resolve_serial || skl_die "подключённых устройств нет: подключите Huawei по USB и включите отладку"

shell() { skl_adb -s "$serial" shell "$@"; }
getprop_value() { shell getprop "$1" 2>/dev/null | tr -d '\r' || true; }

read_platform() {
    model="$(getprop_value ro.product.model)"
    emui="$(getprop_value ro.build.version.emui)"
    release="$(getprop_value ro.build.version.release)"
    sdk="$(getprop_value ro.build.version.sdk)"
    build="$(getprop_value ro.build.display.id)"
    zone="$(getprop_value persist.sys.timezone)"
    platform="$model, EMUI ${emui:-не прочитана}, Android $release (API $sdk), сборка $build, пояс $zone"
}
read_platform

if [[ -z "$emui" && ! "$model" =~ (HUAWEI|HONOR|Huawei|Honor|huawei|honor) ]]; then
    if (( any_device )); then
        skl_warn "устройство не опознано как Huawei/EMUI ($platform): измеряется механика, а не среда EMUI"
    else
        skl_die "устройство не Huawei/EMUI ($platform): для замера среды нужен эталон, для механики — --any-device"
    fi
fi

open_emui_screen() {
    local component
    for component in \
        "com.huawei.systemmanager/com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity" \
        "com.huawei.systemmanager/com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity" \
        "com.huawei.systemmanager/com.huawei.systemmanager.optimize.process.ProtectActivity"; do
        if shell am start -n "$component" 2>/dev/null | grep -q "Starting"; then
            skl_log "открыт экран запуска приложений: $component"
            skl_log "выставьте «Управлять вручную» и все три переключателя для $APP_ID"
            return 0
        fi
    done
    skl_warn "экран EMUI не открылся: Настройки → Батарея → Запуск приложений → $APP_ID → Управлять вручную"
}

if (( open_emui )); then
    open_emui_screen
    exit 0
fi

if (( install )); then
    skl_log "сборка отладочного APK с режимом измерения (SKL-85)"
    ANDROID_SERIAL="$serial" "$REPO_ROOT/gradlew" --project-dir "$REPO_ROOT" --console=plain \
        :app:assembleFreeDebug -Psklyanka.reminderMeasurement=true
    [[ -f "$APK" ]] || skl_die "APK не найден после сборки: $APK"
    skl_adb -s "$serial" install -r -g "$APK"
else
    skl_adb -s "$serial" shell pm path "$APP_ID" >/dev/null 2>&1 ||
        skl_die "приложение не установлено: добавьте --install"
fi

skl_adb -s "$serial" shell pm grant "$APP_ID" android.permission.POST_NOTIFICATIONS >/dev/null 2>&1 || true
skl_adb -s "$serial" shell appops set "$APP_ID" SCHEDULE_EXACT_ALARM allow >/dev/null 2>&1 || true
exact="$(shell appops get "$APP_ID" SCHEDULE_EXACT_ALARM 2>/dev/null | tr -d '\r' || true)"
if [[ "$exact" != *allow* ]]; then
    skl_warn "точные будильники не разрешены ($exact): вариант alarm_clock может не поставиться"
fi

skl_adb -s "$serial" shell monkey -p "$APP_ID" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1 || true
sleep 5
pid="$(shell pidof -s "$APP_ID" 2>/dev/null | tr -d '\r' || true)"
[[ -n "$pid" ]] || skl_die "приложение не запустилось: проверьте установку и разрешения"

if [[ -n "$out" ]]; then
    mkdir -p "$out"
else
    out="$REPO_ROOT/build/spike/probe-$(date +%Y%m%d-%H%M%S)"
    mkdir -p "$out"
fi
out="$(cd "$out" && pwd)"
skl_log "артефакты прогона: $out"

send_broadcast() {
    local result
    result="$(shell am broadcast "$@" 2>&1 || true)"
    [[ "$result" == *"Broadcast completed"* ]] || skl_die "команда стенду не доставлена: $result"
}

skl_adb -s "$serial" logcat -c >/dev/null 2>&1 || true

if [[ -n "$strategy" ]]; then
    send_broadcast -a "$ACTION_STRATEGY" --es strategy "$strategy" -p "$APP_ID"
    sleep 2
    skl_log "вариант измерения: $strategy"
fi

send_broadcast -a "$ACTION_SCHEDULE" --eia probe_minutes "$probe_list" -p "$APP_ID"
scheduled=0
for _ in 1 2 3 4 5 6 7 8 9 10; do
    skl_adb -s "$serial" logcat -d -s "$LOG_TAG" >"$out/probe.log" 2>/dev/null || true
    scheduled="$(grep -c 'event=scheduled' "$out/probe.log" 2>/dev/null || true)"
    if [[ "${scheduled:-0}" -eq "$probe_count" ]]; then break; fi
    sleep 1
done
if [[ "${scheduled:-0}" -ne "$probe_count" ]]; then
    skl_die "поставлено проб ${scheduled:-0} из $probe_count: debug-хук не отвечает (сборка без src/debug?)"
fi
skl_log "поставлено проб: $probe_count (смещения: $probe_list мин)"

shell dumpsys alarm >"$out/alarm-scheduled.txt" 2>/dev/null || true
if grep -q "PROBE_FIRE" "$out/alarm-scheduled.txt"; then
    skl_log "dumpsys alarm: адреса проб видны"
else
    skl_warn "dumpsys alarm: строка PROBE_FIRE не найдена (формат прошивки); срабатывания проверяются по logcat"
fi

read_platform
printf '%s\n' "$platform" >"$out/platform.txt"

screen_state_before="$(shell dumpsys power 2>/dev/null | grep -m1 'mWakefulness=' | tr -d '\r' || true)"
screen_forced_off=0
battery_unplugged=0
doze_active=0
standby_before=""

background_and_dark() {
    shell input keyevent 3 >/dev/null 2>&1 || true
    if [[ "$screen_state_before" == *Awake* ]]; then
        shell input keyevent 26 >/dev/null 2>&1 || true
        screen_forced_off=1
    fi
    shell dumpsys battery unplug >/dev/null 2>&1 || true
    battery_unplugged=1
}

restore_device() {
    if (( doze_active )); then
        shell dumpsys deviceidle unforce >/dev/null 2>&1 || true
    fi
    if (( battery_unplugged )); then
        shell dumpsys battery reset >/dev/null 2>&1 || true
    fi
    if [[ -n "$standby_before" ]]; then
        local bucket
        case "$standby_before" in
            10) bucket=active ;;
            20) bucket=working_set ;;
            30) bucket=frequent ;;
            40) bucket=rare ;;
            45) bucket=restricted ;;
            50) bucket=never ;;
            active|working_set|frequent|rare|restricted|never) bucket="$standby_before" ;;
            *) bucket=active ;;
        esac
        shell am set-standby-bucket "$APP_ID" "$bucket" >/dev/null 2>&1 ||
            skl_warn "вернуть корзину App Standby ($bucket) не удалось"
    fi
    if (( screen_forced_off )); then
        shell input keyevent 26 >/dev/null 2>&1 || true
    fi
}

skl_log "сценарий: $scenario"
case "$scenario" in
    none)
        background_and_dark
        ;;
    freeze)
        background_and_dark
        skl_warn "EMUI-заморозка из adb не управляется: сценарий воспроизводит фон и погашенный экран; фирменную заморозку измеряет пилот"
        ;;
    kill)
        shell input keyevent 3 >/dev/null 2>&1 || true
        shell am kill "$APP_ID" >/dev/null 2>&1 || true
        if [[ "$screen_state_before" == *Awake* ]]; then
            shell input keyevent 26 >/dev/null 2>&1 || true
            screen_forced_off=1
        fi
        shell dumpsys battery unplug >/dev/null 2>&1 || true
        battery_unplugged=1
        ;;
    standby)
        standby_before="$(shell am get-standby-bucket "$APP_ID" 2>/dev/null | tr -d '\r' || true)"
        shell am set-standby-bucket "$APP_ID" restricted >/dev/null 2>&1 ||
            skl_warn "перевести приложение в корзину restricted не удалось"
        background_and_dark
        ;;
    doze)
        shell dumpsys deviceidle enable >/dev/null 2>&1 || true
        shell dumpsys battery unplug >/dev/null 2>&1 || true
        shell input keyevent 3 >/dev/null 2>&1 || true
        shell dumpsys deviceidle force-idle >/dev/null 2>&1 ||
            skl_warn "форсировать Doze не удалось"
        if [[ "$screen_state_before" == *Awake* ]]; then
            shell input keyevent 26 >/dev/null 2>&1 || true
            screen_forced_off=1
        fi
        battery_unplugged=1
        doze_active=1
        ;;
    force-stop)
        shell am force-stop "$APP_ID" >/dev/null 2>&1 || true
        skl_warn "force-stop снимает будильники и переводит приложение в stopped: пробы не придут — это документированный ожидаемый отказ (И-7)"
        ;;
esac

wait_seconds=$(( (last_offset + wait_min) * 60 ))
skl_log "ожидание доставки: $wait_seconds с (последняя проба +$last_offset мин, запас $wait_min мин)"
waited=0
while (( waited < wait_seconds )); do
    step=30
    if (( wait_seconds - waited < step )); then step=$(( wait_seconds - waited )); fi
    sleep "$step"
    waited=$(( waited + step ))
    skl_log "ожидание: $waited из $wait_seconds с"
done

shell dumpsys notification --noredact >"$out/notification.txt" 2>/dev/null || true
shell dumpsys alarm >"$out/alarm-after.txt" 2>/dev/null || true
if (( doze_active )); then
    shell dumpsys deviceidle >"$out/deviceidle-after.txt" 2>/dev/null || true
fi
skl_adb -s "$serial" logcat -d -s "$LOG_TAG" >"$out/probe.log" 2>/dev/null || true
skl_log "артефакты сняты: probe.log, alarm-*.txt, notification.txt"

restore_device

if (( ! quick )); then
    if "$PROBE_DIR/collect.sh" --serial "$serial" --out "$out"; then
        skl_log "журнал напоминаний снят: $out/journal"
    else
        skl_warn "collect.sh не смог снять журнал — на исход проб это не влияет"
    fi
else
    skl_log "--quick: журнал напоминаний не снимается"
fi

set +e
python3 - "$out/probe.log" "$out" "$csv" "$scenario" "$strategy" "$platform" <<'PY'
import csv
import os
import sys
from datetime import datetime, timezone

log_path, out_dir, want_csv, scenario, requested, platform = sys.argv[1:7]

scheduled = {}
fired = {}
with open(log_path, encoding="utf-8", errors="replace") as handle:
    for line in handle:
        if "SklyankaProbe:" not in line:
            continue
        payload = line.split("SklyankaProbe:", 1)[1].strip()
        fields = {}
        for part in payload.split(";"):
            if "=" in part:
                key, value = part.split("=", 1)
                fields[key] = value
        probe = fields.get("probe", "")
        if not probe.isdigit():
            continue
        if fields.get("event") == "scheduled":
            scheduled[int(probe)] = fields
        elif fields.get("event") == "fired":
            fired[int(probe)] = fields

rows = []
for index in sorted(scheduled):
    entry = scheduled[index]
    planned_ms = int(entry.get("scheduled_at", "0"))
    actual = fired.get(index)
    if actual is None:
        rows.append({
            "index": index,
            "planned_ms": planned_ms,
            "fired_ms": None,
            "deviation": None,
            "verdict": "не дошло",
            "strategy": entry.get("strategy", ""),
            "placed": entry.get("placed", "false"),
        })
        continue
    actual_ms = int(actual.get("fired_at", str(planned_ms)))
    deviation = round((actual_ms - planned_ms) / 1000)
    if -120 <= deviation <= 120:
        verdict = "вовремя"
    elif deviation < -120:
        verdict = "рано на {} с".format(abs(deviation))
    else:
        verdict = "поздно на {} с".format(deviation)
    rows.append({
        "index": index,
        "planned_ms": planned_ms,
        "fired_ms": actual_ms,
        "deviation": deviation,
        "verdict": verdict,
        "strategy": actual.get("strategy", entry.get("strategy", "")),
        "placed": entry.get("placed", "false"),
    })

batches = []
for i in range(len(rows)):
    for j in range(i + 1, len(rows)):
        first, second = rows[i], rows[j]
        if first["fired_ms"] is None or second["fired_ms"] is None:
            continue
        if abs(first["fired_ms"] - second["fired_ms"]) <= 5000 and \
                abs(first["planned_ms"] - second["planned_ms"]) >= 300000:
            batches.append((first["index"], second["index"]))

observed = sorted({entry.get("strategy", "") for entry in scheduled.values()})
warnings = []
if requested and any(kind != requested for kind in observed):
    warnings.append(
        "запрошен вариант {}, а действует {}: сборка, вероятно, без "
        "-Psklyanka.reminderMeasurement=true — сохранённая настройка инертна".format(
            requested, ", ".join(observed) or "неизвестно"
        )
    )
if any(row["placed"] != "true" for row in rows):
    warnings.append("часть проб система не поставила: проверьте право на точные будильники")
if batches:
    warnings.append(
        "пачечная доставка: пробы {} сработали в пределах 5 с при плановой разнице больше 5 мин".format(
            ", ".join("{}→{}".format(pair[0], pair[1]) for pair in batches)
        )
    )

missing = [row for row in rows if row["fired_ms"] is None]
on_time = all(row["verdict"] == "вовремя" for row in rows)
if scenario == "force-stop":
    ok = True
    conclusion = "документированный ожидаемый отказ: force-stop снимает будильники и переводит в stopped"
elif not scheduled:
    ok = False
    conclusion = "пробы не поставлены: команда стенду не дошла или отклонена"
elif any(row["placed"] != "true" for row in rows):
    ok = False
    conclusion = "часть проб не поставлена системой — прогон недействителен"
elif missing:
    ok = False
    conclusion = "не доставлено проб: {}".format(len(missing))
else:
    ok = on_time
    conclusion = "все пробы вовремя" if on_time else "есть опоздавшие пробы"


def moment(ms):
    if ms is None:
        return "—"
    return datetime.fromtimestamp(ms / 1000, tz=timezone.utc).strftime("%Y-%m-%d %H:%M:%S UTC")


report = []
report.append("# Пробник доставки — отчёт (SKL-89)")
report.append("")
report.append("- Время отчёта: {}".format(datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M:%S UTC")))
report.append("- Устройство: {}".format(platform))
report.append("- Сценарий: {}".format(scenario))
report.append("- Вариант измерения: запрошен {}, действует {}".format(requested or "не менялся", ", ".join(observed) or "—"))
report.append("- Проб: {}".format(len(rows)))
report.append("")
report.append("| Проба | План | Факт | Отклонение | Исход | Вариант |")
report.append("|---|---|---|---|---|---|")
for row in rows:
    deviation = "—" if row["deviation"] is None else "{} с".format(row["deviation"])
    report.append("| {} | {} | {} | {} | {} | {} |".format(
        row["index"] + 1, moment(row["planned_ms"]), moment(row["fired_ms"]), deviation,
        row["verdict"], row["strategy"],
    ))
report.append("")
if warnings:
    report.append("## Предупреждения")
    report.append("")
    for warning in warnings:
        report.append("- {}".format(warning))
    report.append("")
report.append("## Вывод")
report.append("")
report.append(conclusion)
report.append("")
report.append("Окно «вовремя» — отклонение в пределах ±120 с (гейт НАП-3 считает 0…+120 с).")
report.append("Прогон не заменяет четырнадцатисуточный гейт: он показывает доставку механизма здесь и сейчас.")
report.append("Медицинских данных в отчёте и логе нет; журнал приёмов пробами не затрагивается.")
report.append("")
report.append("Артефакты: `probe.log`, `alarm-scheduled.txt`, `alarm-after.txt`, `notification.txt`, `platform.txt`.")

with open(os.path.join(out_dir, "report.md"), "w", encoding="utf-8") as handle:
    handle.write("\n".join(report) + "\n")

if want_csv == "1":
    with open(os.path.join(out_dir, "probes.csv"), "w", encoding="utf-8", newline="") as handle:
        writer = csv.writer(handle)
        writer.writerow(["probe", "scheduled_at_epoch_ms", "fired_at_epoch_ms", "deviation_seconds", "outcome", "strategy", "placed"])
        for row in rows:
            writer.writerow([
                row["index"] + 1, row["planned_ms"], row["fired_ms"] if row["fired_ms"] is not None else "",
                row["deviation"] if row["deviation"] is not None else "", row["verdict"], row["strategy"], row["placed"],
            ])

print("проба | план | отклонение | исход")
for row in rows:
    deviation = "—" if row["deviation"] is None else "{} с".format(row["deviation"])
    print("  {} | {} | {} | {}".format(row["index"] + 1, moment(row["planned_ms"]), deviation, row["verdict"]))
print("")
print("вывод: {}".format(conclusion))
for warning in warnings:
    print("предупреждение: {}".format(warning))
sys.exit(0 if ok else 1)
PY
verdict=$?
set -e

if (( verdict == 0 )); then
    skl_log "прогон зачтён: $out/report.md"
else
    skl_warn "прогон не зачтён: см. $out/report.md"
fi
skl_log "артефакты: $out"
exit "$verdict"
