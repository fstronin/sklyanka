#!/usr/bin/env bash
# Параметры скриптового визуального прогона (SKL-92) — единственное место, где они заданы.
#
# Файл только объявляет переменные и функции и подключается через source.
# Отсюда отключённое правило «переменная не используется»: её читают скрипты-потребители.
# shellcheck disable=SC2034

SKL_VISUAL_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
SKL_REPO_ROOT="$(cd -- "$SKL_VISUAL_DIR/../.." && pwd)"

SKL_RUNS_DIR="$SKL_VISUAL_DIR/runs"
SKL_BASELINES_DIR="$SKL_VISUAL_DIR/baselines"

# Тест-генератор контактных листов SKL-91 — единственное место, где он назван.
# Копия фильтра разошлась бы с тестом молча, как разошлись бы параметры эмулятора.
SKL_CAPTURE_TASK=":app:testDebugUnitTest"
SKL_CAPTURE_TEST_FILTER="*LocalizationContactSheetTest*"
SKL_CAPTURE_TEST_FILE="app/src/testDebug/kotlin/ru/stohastika/sklyanka/acceptance/locale/matrix/LocalizationContactSheetTest.kt"
SKL_VISUAL_REVIEW_PROPERTY="sklyanka.visualReview"
SKL_VISUAL_REVIEW_DIR="$SKL_REPO_ROOT/app/build/visual-review"

# Порог попиксельного сравнения: максимальная разница канала, которая считается шумом
# рендера, а не изменением. 8 из 255 отсекает антиалиасинг и округление трекинга.
SKL_PIXEL_TOLERANCE="${SKL_PIXEL_TOLERANCE:-8}"

skl_java_major() {
    local java_home="$1"
    "$java_home/bin/java" -version 2>&1 | sed -n '1s/.*version "\([0-9][0-9]*\).*/\1/p'
}

# Java 17 или новее: сначала JAVA_HOME, затем системный поиск и обычные места установки.
# Лучше отказать до запуска Gradle, чем упасть на середине сборки.
skl_java_home() {
    local candidate
    if [[ -n "${JAVA_HOME:-}" && -x "$JAVA_HOME/bin/java" ]]; then
        echo "$JAVA_HOME"
        return 0
    fi
    if [[ "$(uname -s)" == "Darwin" && -x /usr/libexec/java_home ]]; then
        candidate="$(/usr/libexec/java_home -v 17 2>/dev/null || true)"
        if [[ -n "$candidate" && -x "$candidate/bin/java" ]]; then
            echo "$candidate"
            return 0
        fi
    fi
    for candidate in \
        /opt/homebrew/opt/openjdk@17 \
        /opt/homebrew/opt/openjdk@21 \
        /opt/homebrew/opt/openjdk \
        /usr/local/opt/openjdk@17 \
        /usr/local/opt/openjdk@21 \
        /usr/local/opt/openjdk; do
        if [[ -x "$candidate/bin/java" ]]; then
            echo "$candidate"
            return 0
        fi
    done
    if command -v java >/dev/null 2>&1; then
        candidate="$(java -XshowSettings:properties -version 2>&1 | sed -n 's/^ *java\.home = //p' | tail -n 1)"
        if [[ -n "$candidate" && -x "$candidate/bin/java" ]]; then
            echo "$candidate"
            return 0
        fi
    fi
    return 1
}
