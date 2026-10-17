#!/usr/bin/env bash
# Прогон инструментальных проверок на образе (SKL-27).
#
# Проверки устройства живут в двух модулях (SKL-24 §5.2): core-data — настоящая база,
# feature-history — байты отчёта врачу (SKL-41). Прогон адресуется модулям из
# SKL_TEST_MODULES, а фильтр задаёт группу.
#
#   ./run-tests.sh [API] [группа]
#
# Группы:
#   fast       репозитории поверх транзакций — самая быстрая и самая содержательная часть
#   data       DAO, журнал, перенос, история — настоящий движок базы
#   heavy      объём и гонки: тысяча повторов в двух потоках, база годового объёма
#   migration  миграции из экспортированных схем (МГ-3) — отдельно от всего
#   journey    сквозные походы человека на настоящем файловом хранилище (SKL-140)
#   ui         Compose-проверки экранов на настоящей платформе (SKL-96): модуль app
#   all        всё подряд, одной задачей (по умолчанию)
#
# Группы fast/heavy/migration описывают базу и адресуются core-data; data и all идут
# по всем модулям: отчёту врачу тоже нужна настоящая платформа, а не JVM. Группа ui
# идёт по :app — единственному модулю, видящему настоящий MainActivity и весь граф.
set -euo pipefail

SKL_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=config.sh
source "$SKL_DIR/config.sh"
# shellcheck source=lib.sh
source "$SKL_DIR/lib.sh"

api="${1:-${SKL_API_LEVELS[0]}}"
group="${2:-all}"

skl_avd_running "$api" || skl_die "образ API $api не запущен: сначала ./start.sh $api"

declare -a filter=()
case "$group" in
    fast) filter=("-Pandroid.testInstrumentationRunnerArguments.package=$SKL_GROUP_FAST_PACKAGES") ;;
    data)
        filter=(
            "-Pandroid.testInstrumentationRunnerArguments.package=$SKL_GROUP_DATA_PACKAGES"
            "-Pandroid.testInstrumentationRunnerArguments.notClass=$SKL_GROUP_HEAVY_CLASSES,$SKL_GROUP_MIGRATION_CLASSES"
            "-Pandroid.testInstrumentationRunnerArguments.notPackage=$SKL_GROUP_JOURNEY_PACKAGES"
        )
        ;;
    heavy) filter=("-Pandroid.testInstrumentationRunnerArguments.class=$SKL_GROUP_HEAVY_CLASSES") ;;
    migration) filter=("-Pandroid.testInstrumentationRunnerArguments.class=$SKL_GROUP_MIGRATION_CLASSES") ;;
    journey)
        # Пакет походов — подпакет пакета экранных проверок, поэтому в группах ui и data
        # он исключается явно (notPackage): иначе экранная группа тянула бы за собой
        # сорок минут походов.
        filter=(
            "-Pandroid.testInstrumentationRunnerArguments.package=$SKL_GROUP_JOURNEY_PACKAGES"
            "-Pandroid.testInstrumentationRunnerArguments.$SKL_JOURNEY_STORAGE_ARGUMENT"
            "-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true"
        )
        ;;
    ui)
        filter=(
            "-Pandroid.testInstrumentationRunnerArguments.package=$SKL_GROUP_UI_PACKAGES"
            "-Pandroid.testInstrumentationRunnerArguments.notPackage=$SKL_GROUP_JOURNEY_PACKAGES"
            # AGP удаляет APK после connected-прогона, а кадры экранного прогона лежат
            # во внутреннем каталоге приложения и забираются screenshots.sh уже после
            # прогона: без этого AGP-свойства снимать было бы нечего.
            "-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true"
        )
        ;;
    all) filter=() ;;
    *) skl_die "неизвестная группа: $group (fast|data|heavy|migration|journey|ui|all)" ;;
esac

# Какие модули исполняют группу: heavy, migration и fast — предмет core-data,
# data и all — все модули проверок, ui и journey — модуль приложения (SKL-96, SKL-140).
case "$group" in
    data | all) modules=("${SKL_TEST_MODULES[@]}") ;;
    ui | journey) modules=(":app") ;;
    *) modules=("${SKL_TEST_MODULES[0]}") ;;
esac

results="$(skl_results_dir "$api")"
log_file="$results/tests-$group.log"
skl_log "прогон группы «${group}» на $(skl_serial "$api"); лог: $log_file"
: >"$log_file"

status=0
for module in "${modules[@]}"; do
    skl_log "модуль $module"
    # Пустой фильтр (группа all) разворачивается через ${name[@]+...}: bash 3.2 — а это
    # системный bash macOS — считает разворот пустого массива обращением к неназначенной
    # переменной и с set -u прекращает работу. Прогон «всё подряд» на такой машине
    # не запускался вовсе.
    # У app задача варианта контура (`connectedFreeDebugAndroidTest`), у остальных
    # модулей измерения нет и имя прежнее (Р-86, ADR-0016).
    task="$SKL_TEST_TASK"
    if [[ "$module" == ":app" ]]; then task="$SKL_APP_TEST_TASK"; fi
    skl_gradle "$api" "$module:$task" ${filter[@]+"${filter[@]}"} 2>&1 |
        tee -a "$log_file" || status=$?

    # Отчёты AGP — машинно читаемые XML; они и есть результат прогона проверок,
    # а не то, что осталось в терминале. У каждого модуля свой каталог отчётов,
    # и они складываются рядом, а не затирают друг друга.
    xml_dir="$SKL_REPO_ROOT/${module#:}/build/outputs/androidTest-results/connected"
    if [[ -d "$xml_dir" ]]; then
        mkdir -p "$results/androidTest-results/$group"
        cp -R "$xml_dir/." "$results/androidTest-results/$group/" 2>/dev/null || true
    fi
done
if [[ -d "$results/androidTest-results/$group" ]]; then
    skl_log "отчёты проверок: $results/androidTest-results/$group"
fi

if (( status != 0 )); then
    skl_warn "группа «${group}» не прошла; разбор — в отчётах выше"
fi
exit "$status"
