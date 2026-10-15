#!/usr/bin/env bash
# Возврат обстановки образа в исходную перед прогоном походов (SKL-117).
#
#   ./restore-stand.sh [API] [serial]
#
# Зачем отдельный шаг. Фаза «чужая локаль» ставит системе fr-FR и масштаб шрифта с потолка
# (`run-journeys.sh`, шаг 7), а снимает их последними строками прогона. Прерванный прогон
# до снятия не доходит, а данные образа между прогонами сохраняются намеренно (И-1, И-10) —
# и следующая прогонка начинается с французского интерфейса. Приложение объявляет шесть
# языков (`localeConfig`), французского среди них нет, и платформа отдаёт ему русский:
# проверка, называющая день недели по локали образа, слова не находит и падает таймаутом.
# Отказ выглядит как дефект продукта, хотя причина — оставленное состояние образа.
#
# Что делает шаг: сравнивает локаль и масштаб шрифта с исходными значениями прогона
# (`SKL_JOURNEY_DEFAULT_LOCALE`, `SKL_JOURNEY_FONT_SCALE_DEFAULT`), возвращает их и
# **отказывает**, если вернуть не удалось: прогон на чужой обстановке называл бы отказы
# продукта, которых нет.
#
# Имена переменных подставляются в фигурных скобках: рядом с кириллицей bash без UTF-8
# в локали дописывает многобайтный символ к имени (`$locale_now»` читалось как имя
# с хвостом и падало «unbound variable»). Скобки закрывают вопрос границы имени.
set -euo pipefail

SKL_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=config.sh
source "${SKL_DIR}/config.sh"
# shellcheck source=lib.sh
source "${SKL_DIR}/lib.sh"

api="${1:-${SKL_JOURNEY_API_LEVEL}}"
serial="${2:-$(skl_serial "${api}")}"

locale_now="$(skl_adb -s "${serial}" shell settings get system system_locales 2>/dev/null | tr -d '\r' || true)"
scale_now="$(skl_adb -s "${serial}" shell settings get system font_scale 2>/dev/null | tr -d '\r' || true)"

if [[ "${locale_now}" == "${SKL_JOURNEY_DEFAULT_LOCALE}" && "${scale_now}" == "${SKL_JOURNEY_FONT_SCALE_DEFAULT}" ]]; then
    skl_log "обстановка образа исходная: локаль ${locale_now}, масштаб шрифта ${scale_now}"
    exit 0
fi

skl_warn "образ остался в обстановке прошлого прогона: локаль ${locale_now}, масштаб шрифта ${scale_now} — возвращаю исходную"
skl_adb -s "${serial}" shell settings put system system_locales "${SKL_JOURNEY_DEFAULT_LOCALE}" >/dev/null 2>&1 || true
skl_adb -s "${serial}" shell settings put system font_scale "${SKL_JOURNEY_FONT_SCALE_DEFAULT}" >/dev/null 2>&1 || true

locale_now="$(skl_adb -s "${serial}" shell settings get system system_locales 2>/dev/null | tr -d '\r' || true)"
scale_now="$(skl_adb -s "${serial}" shell settings get system font_scale 2>/dev/null | tr -d '\r' || true)"

if [[ "${locale_now}" != "${SKL_JOURNEY_DEFAULT_LOCALE}" || "${scale_now}" != "${SKL_JOURNEY_FONT_SCALE_DEFAULT}" ]]; then
    skl_die "обстановку образа вернуть не удалось: локаль ${locale_now}, масштаб шрифта ${scale_now}. Прогон на такой обстановке называл бы отказы продукта, которых нет: перезапусти образ с гашением данных — start.sh ${api} --wipe"
fi

skl_log "обстановка образа возвращена: локаль ${locale_now}, масштаб шрифта ${scale_now}"
