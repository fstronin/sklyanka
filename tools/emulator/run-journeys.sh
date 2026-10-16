#!/usr/bin/env bash
# Прогон походов на эмуляторе одной командой (SKL-140, TDD §9).
#
#   ./run-journeys.sh [API] [--keep] [--class <полное имя класса>]
#
#   API      уровень образа (по умолчанию SKL_JOURNEY_API_LEVEL — targetSdk)
#   --keep   не гасить образ после прогона
#   --class  один класс похода вместо всего набора: разбор одной проверки
#
# Порядок прогона, и каждый запуск инструментовки — отдельный запуск приложения:
#
#   1. образ (идемпотентно) → окно → экран в рабочее состояние → установка --fresh
#   2. выдача прав (уведомления, «Не беспокоить»; точные будильники на API 35 выданы
#      установочно, ADR-0003)
#   3. запуск 1: походы человека и враждебный проход
#   4. запуск 2: фаза 1 эстафеты выживания — пишет состояние последней в своём запуске
#   5. запуск 3: фаза 2 эстафеты — свежий процесс утверждает состояние фазы 1
#   6. отзыв прав и запуск 4: поход «права не выданы»
#   7. масштаб шрифта на потолке и чужая локаль, запуск 5: враждебный проход по обстановке,
#      затем возврат значений обратно
#   8. сбор кадров на хост и сводка journey-summary.txt
#
# Настоящее файловое хранилище походов включается аргументом инструментовки
# SKL_JOURNEY_STORAGE_ARGUMENT в каждом запуске: без него класс похода падает на старте,
# а не тихо переходит на память (TDD §5.2).
set -euo pipefail

SKL_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=config.sh
source "$SKL_DIR/config.sh"
# shellcheck source=lib.sh
source "$SKL_DIR/lib.sh"

api="${SKL_JOURNEY_API_LEVEL}"
keep=0
only_class=""
while [[ $# -gt 0 ]]; do
    case "$1" in
        --keep) keep=1 ;;
        --class)
            shift
            only_class="${1:-}"
            [[ -n "$only_class" ]] || skl_die "для --class нужно полное имя класса"
            ;;
        '' | *[!0-9]*) skl_die "неизвестный параметр: $1 ([API] | --keep | --class <имя>)" ;;
        *) api="$1" ;;
    esac
    shift
done

"$SKL_DIR/avd.sh" create "$api"
"$SKL_DIR/start.sh" "$api" --window

serial="$(skl_serial "$api")"
results="$(skl_results_dir "$api")"
mkdir -p "$results"

# Обстановка образа возвращается в исходную до первой проверки (SKL-117).
#
# Фаза «чужая локаль» ставит системе fr-FR и масштаб шрифта с потолка, а снимает их
# в конце прогона. Прерванный прогон (Ctrl-C, потолок времени, отказ задачи) до снятия
# не доходит, а данные образа между прогонами сохраняются намеренно (И-1, И-10) — значит
# следующая прогонка начиналась бы с французского интерфейса, и все проверки, называющие
# русские слова, падали бы таймаутом. Обстановка выглядела бы дефектом продукта, а причина
# была бы в оставленном состоянии образа: чинится это здесь, а не разбором каждого отказа.
#
# Шаг идёт ПЕРВЫМ, до пробуждения экрана и часов: записанную локаль платформа применяет
# только при загрузке, и возврат оставшейся чужой локали — это перезагрузка образа
# (`restore-stand.sh`). После неё экран снова гаснет, а часы образа сбрасываются на часы
# машины, поэтому за ставками пробуждения и часов шаг стоять не может.
"$SKL_DIR/restore-stand.sh" "$api" "$serial"

# Видимому окну нужен живой экран: поход идёт минуты, и прогон не должен упереться в шторку.
skl_adb -s "$serial" shell svc power stayon true >/dev/null 2>&1 || true
skl_adb -s "$serial" shell settings put system screen_off_timeout 2147483647 >/dev/null 2>&1 || true
skl_adb -s "$serial" shell wm dismiss-keyguard >/dev/null 2>&1 || true
skl_adb -s "$serial" shell settings put secure show_ime_with_hard_keyboard 0 >/dev/null 2>&1 || true

# Часы образа выставляются под управляемые часы похода: без этого платформенные пути судят
# по часам образа, а домен — по управляемым, и поход «Проверка напоминаний» видел опоздание
# с отрицательным числом минут, а будильники стенда выглядели для системы прошлым (TDD §10).
skl_adb -s "$serial" shell settings put global auto_time 0 >/dev/null 2>&1 || true
skl_adb -s "$serial" shell settings put global auto_time_zone 0 >/dev/null 2>&1 || true
skl_adb -s "$serial" shell cmd alarm set-timezone "$SKL_JOURNEY_TIMEZONE" >/dev/null 2>&1 ||
    skl_adb -s "$serial" shell setprop persist.sys.timezone "$SKL_JOURNEY_TIMEZONE" >/dev/null 2>&1 || true
# Часы ставит root: на образе (userdebug) оболочка без прав `date` не меняет, и фаза выживания
# видит ожидающий приём просроченным — то есть обстановка выглядит дефектом продукта (SKL-117,
# прогон 16.10.2026). Права просим ровно здесь; отказ не глушим, а называем словами.
skl_adb -s "$serial" root >/dev/null 2>&1 || true
skl_adb -s "$serial" wait-for-device >/dev/null 2>&1 || true
skl_adb -s "$serial" shell cmd alarm set-timezone "$SKL_JOURNEY_TIMEZONE" >/dev/null 2>&1 || true
if ! skl_adb -s "$serial" shell date "$SKL_JOURNEY_CLOCK" >/dev/null 2>&1; then
    skl_warn "часы образа выставить не удалось: пути, судящие по часам платформы, разойдутся с доменом"
fi
skl_log "часы образа: $(skl_adb -s "$serial" shell date | tr -d '\r')"

# Журнал устройства за прогон: гасится в любом исходе — стек упавшего похода живёт только там.
logcat_file="$results/logcat-journey.txt"
logcat_pid=""
stop_logcat() {
    if [[ -n "$logcat_pid" ]]; then
        kill "$logcat_pid" >/dev/null 2>&1 || true
        wait "$logcat_pid" 2>/dev/null || true
        logcat_pid=""
    fi
}
trap stop_logcat EXIT

skl_adb -s "$serial" logcat -c >/dev/null 2>&1 || true
skl_adb -s "$serial" logcat -v time >"$logcat_file" 2>/dev/null &
logcat_pid=$!

# Отчёты прошлого прогона не должны попасть в сводку этого: сводка складывает все каталоги
# фаз, и оставшийся каталог прежнего запуска (например, от прогона `--class`) добавил бы
# чужие проверки в числа.
rm -rf "$results/androidTest-results"
mkdir -p "$results"

skl_log "установка со стиранием данных приложения (--fresh): поход начинается с чистого хранилища"
"$SKL_DIR/install.sh" "$api" --fresh

# Кадры прошлого прогона не должны попасть в опись этого.
device_dir="/data/data/$SKL_APP_ID/files/$SKL_UI_SCREENSHOT_DIR"
skl_adb -s "$serial" shell "rm -rf $device_dir" >/dev/null 2>&1 || true

grant_permissions() {
    # Точные будильники на API 35 выданы установочно (USE_EXACT_ALARM, ADR-0003);
    # выдача скрипта — уведомления и доступ к политике «Не беспокоить».
    skl_adb -s "$serial" shell pm grant "$SKL_APP_ID" android.permission.POST_NOTIFICATIONS >/dev/null 2>&1 || true
    skl_adb -s "$serial" shell appops set "$SKL_APP_ID" ACCESS_NOTIFICATION_POLICY allow >/dev/null 2>&1 || true
}

revoke_permissions() {
    skl_adb -s "$serial" shell pm revoke "$SKL_APP_ID" android.permission.POST_NOTIFICATIONS >/dev/null 2>&1 || true
    skl_adb -s "$serial" shell appops set "$SKL_APP_ID" ACCESS_NOTIFICATION_POLICY deny >/dev/null 2>&1 || true
}

# Один запуск инструментовки: фильтры группы, аргумент хранилища, отчёт и лог по фазе.
# APK остаётся установленным: фаза 2 обязана увидеть состояние фазы 1, а кадры лежат
# во внутреннем каталоге приложения и забираются screenshots.sh уже после прогона.
run_phase() {
    local label="$1"; shift
    local log_file="$results/tests-journey-$label.log"
    local status=0
    : >"$log_file"
    # Скобки у имени обязательны: в локали, где bash считает многобайтовый «»
    # частью имени переменной, "$label»" разбирается как переменная «label»» и падает
    # под `set -u`. Тот же разбор ломал прогон группы целиком (SKL-122).
    skl_log "запуск «${label}»; лог: $log_file"
    skl_gradle "$api" ":app:connectedDebugAndroidTest" \
        ${1+"$@"} \
        "-Pandroid.testInstrumentationRunnerArguments.$SKL_JOURNEY_STORAGE_ARGUMENT" \
        "-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true" 2>&1 |
        tee -a "$log_file" || status=$?

    local xml_dir="$SKL_REPO_ROOT/app/build/outputs/androidTest-results/connected"
    if [[ -d "$xml_dir" ]]; then
        mkdir -p "$results/androidTest-results/journey-$label"
        cp -R "$xml_dir/." "$results/androidTest-results/journey-$label/" 2>/dev/null || true
    fi
    return "$status"
}

started=$SECONDS
tests_status=0

# Код возврата прогона — первая неудача, а не последняя: по нему судят, что чинить.
keep_first_status() {
    if (( tests_status == 0 )); then
        tests_status=$1
    fi
}

if [[ -n "$only_class" ]]; then
    grant_permissions
    run_phase single "-Pandroid.testInstrumentationRunnerArguments.class=$only_class" || keep_first_status $?
else
    grant_permissions

    run_phase paths \
        "-Pandroid.testInstrumentationRunnerArguments.package=$SKL_GROUP_JOURNEY_PACKAGES" \
        "-Pandroid.testInstrumentationRunnerArguments.notClass=$SKL_JOURNEY_SURVIVAL_WRITE_CLASSES,$SKL_JOURNEY_SURVIVAL_CHECK_CLASSES,$SKL_JOURNEY_NOT_GRANTED_CLASSES,$SKL_JOURNEY_SETTING_CLASSES" ||
        keep_first_status $?

    # Эстафета выживания: фаза 1 пишет состояние последней в своём запуске, фаза 2 идёт
    # отдельным запуском — только так она видит свежий процесс и уцелевшие файлы.
    run_phase survival-write \
        "-Pandroid.testInstrumentationRunnerArguments.class=$SKL_JOURNEY_SURVIVAL_WRITE_CLASSES" ||
        keep_first_status $?

    run_phase survival \
        "-Pandroid.testInstrumentationRunnerArguments.class=$SKL_JOURNEY_SURVIVAL_CHECK_CLASSES" ||
        keep_first_status $?

    revoke_permissions
    run_phase not-granted \
        "-Pandroid.testInstrumentationRunnerArguments.class=$SKL_JOURNEY_NOT_GRANTED_CLASSES" ||
        keep_first_status $?

    skl_adb -s "$serial" shell settings put system font_scale "$SKL_JOURNEY_FONT_SCALE" >/dev/null 2>&1 || true
    # Локаль ставится системе, а не приложению: приложение объявляет `localeConfig` из шести
    # языков, и необъявленная локаль приложения отбрасывается платформой — фаза «чужая локаль»
    # проверяла бы русскую. `cmd locale set-system-locales` есть не на всех образах, поэтому
    # пишется сама настройка (то же место, куда пишет команда), а отказ не глушится.
    #
    # Записанную локаль платформа применяет только при следующей загрузке (SKL-119, 16.10.2026,
    # `HostileLocaleAndFontScaleTest`: «запись настройки `system_locales` применяется только при
    # следующей загрузке — проверено на API 35»), поэтому сама фаза идёт под той локалью, что
    # стоит на образе: класс и утверждает то, что видно при любой локали, — один объявленный язык
    # на всех экранах и подписи, не режущиеся на потолке масштаба. Здесь локаль записывается
    # как обстановка фазы; возврат её — строками ниже, а за по-настоящему чужую локаль образа
    # отвечает `restore-stand.sh` (он же чинит оставшуюся запись перед следующим прогоном).
    if ! skl_write_system_locale "$serial" "$SKL_JOURNEY_UNKNOWN_LOCALE"; then
        skl_warn "системную локаль $SKL_JOURNEY_UNKNOWN_LOCALE выставить не удалось: фаза обстановки проверит то, что есть"
    fi
    run_phase setting "-Pandroid.testInstrumentationRunnerArguments.class=$SKL_JOURNEY_SETTING_CLASSES" ||
        keep_first_status $?
    skl_adb -s "$serial" shell settings put system font_scale "$SKL_JOURNEY_FONT_SCALE_DEFAULT" >/dev/null 2>&1 || true
    skl_write_system_locale "$serial" "$SKL_JOURNEY_DEFAULT_LOCALE"
fi

stop_logcat

shots_status=0
"$SKL_DIR/screenshots.sh" "$api" || shots_status=$?

elapsed=$((SECONDS - started))

# Сводка прогона — по машинно читаемым отчётам AGP каждой фазы, а не по выводу терминала.
# Без python3 сводка не собирается, но прогон продолжается: гашение образа и коды возврата
# стоят ниже и выполняются всегда (SKL-98).
summary_file="$results/journey-summary.txt"
if command -v python3 >/dev/null 2>&1; then
    if ! python3 - "$results/androidTest-results" "$elapsed" "$SKL_JOURNEY_BUDGET_SECONDS" >"$summary_file" <<'PY'
import glob
import os
import sys
import xml.etree.ElementTree as ET

root, elapsed, budget = sys.argv[1], int(sys.argv[2]), int(sys.argv[3])
tests = failures = errors = skipped = 0
seconds = 0.0
phases = []
for directory in sorted(glob.glob(os.path.join(root, "journey-*"))):
    phase_tests = phase_failures = 0
    for path in glob.glob(os.path.join(directory, "**", "*.xml"), recursive=True):
        try:
            suite = ET.parse(path).getroot()
        except ET.ParseError:
            continue
        phase_tests += int(suite.get("tests", 0))
        phase_failures += int(suite.get("failures", 0)) + int(suite.get("errors", 0))
        skipped += int(suite.get("skipped", 0))
        seconds += float(suite.get("time", 0.0))
    tests += phase_tests
    failures += phase_failures
    phases.append(f"{os.path.basename(directory)[len('journey-'):]}={phase_tests}/{phase_failures}")
print(f"проверок: {tests}; падений: {failures}; пропущено: {skipped}; время проверок: {seconds:.1f} с")
print(f"фазы (проверок/падений): {', '.join(phases) if phases else 'нет'}")
print(f"время прогона: {elapsed} с (потолок {budget} с)")
PY
    then
        skl_warn "сводку собрать не удалось: прогон продолжается (файл: $summary_file)"
    fi
    summary="$(cat "$summary_file")"
    printf '%s\n' "$summary" | while IFS= read -r line; do skl_log "сводка: $line"; done
    skl_log "сводка файлом: $summary_file"
    if grep -q '^проверок: 0;' "$summary_file"; then
        skl_warn "отчётов проверок нет: прогон не дошёл до исполнения"
    fi
else
    printf 'сводка недоступна: python3 не найден в PATH\n' >"$summary_file"
    skl_warn "python3 не найден: сводка не собрана, прогон продолжается (файл: $summary_file)"
fi

if (( elapsed > SKL_JOURNEY_BUDGET_SECONDS )); then
    skl_warn "прогон длиннее потолка (${elapsed} с > ${SKL_JOURNEY_BUDGET_SECONDS} с)"
fi

skl_log "проверки: статус $tests_status; отчёты: $results/androidTest-results/journey-*"
skl_log "журнал устройства: $logcat_file"
if (( shots_status == 0 )); then
    skl_log "кадры: $results/$SKL_UI_SCREENSHOT_DIR (опись — manifest.txt)"
else
    skl_warn "кадры не собраны (статус $shots_status): смотрите сообщение screenshots.sh выше"
fi

if (( keep == 0 )); then
    "$SKL_DIR/stop.sh" "$api"
else
    skl_log "образ API $api оставлен запущенным (--keep)"
fi

if (( tests_status != 0 )); then
    exit "$tests_status"
fi
exit "$shots_status"
