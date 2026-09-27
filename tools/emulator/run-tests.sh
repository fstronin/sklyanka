#!/usr/bin/env bash
# Прогон инструментальных проверок на образе (SKL-27).
#
# Все 22 файла проверок устройства живут в одном модуле — core-data (SKL-24 §5.2),
# поэтому прогон адресуется модулю, а фильтр задаёт группу.
#
#   ./run-tests.sh [API] [группа]
#
# Группы:
#   fast       репозитории поверх транзакций — самая быстрая и самая содержательная часть
#   data       DAO, журнал, перенос, история — настоящий движок базы
#   heavy      объём и гонки: тысяча повторов в двух потоках, база годового объёма
#   migration  миграции из экспортированных схем (МГ-3) — отдельно от всего
#   all        всё подряд, одной задачей (по умолчанию)
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
        )
        ;;
    heavy) filter=("-Pandroid.testInstrumentationRunnerArguments.class=$SKL_GROUP_HEAVY_CLASSES") ;;
    migration) filter=("-Pandroid.testInstrumentationRunnerArguments.class=$SKL_GROUP_MIGRATION_CLASSES") ;;
    all) filter=() ;;
    *) skl_die "неизвестная группа: $group (fast|data|heavy|migration|all)" ;;
esac

results="$(skl_results_dir "$api")"
log_file="$results/tests-$group.log"
skl_log "прогон группы «$group» на $(skl_serial "$api"); лог: $log_file"

status=0
skl_gradle "$api" "$SKL_TEST_MODULE:$SKL_TEST_TASK" "${filter[@]}" 2>&1 | tee "$log_file" || status=$?

# Отчёты AGP — машинно читаемые XML; они и есть результат прогона проверок,
# а не то, что осталось в терминале.
xml_dir="$SKL_REPO_ROOT/core-data/build/outputs/androidTest-results/connected"
if [[ -d "$xml_dir" ]]; then
    mkdir -p "$results/androidTest-results/$group"
    cp -R "$xml_dir/." "$results/androidTest-results/$group/" 2>/dev/null || true
    skl_log "отчёты проверок: $results/androidTest-results/$group"
fi

if (( status != 0 )); then
    skl_warn "группа «$group» не прошла; разбор — в отчётах выше"
fi
exit "$status"
