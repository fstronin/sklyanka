#!/usr/bin/env bash
# Сборка и установка отладочного приложения на образ (SKL-27).
#
# Идемпотентно: установка идёт поверх (-r), подпись отладочная и не меняется, данные
# приложения сохраняются — ровно так воспроизводится испытание И-11.
#
#   ./install.sh [API] [--fresh]
#
# --fresh удаляет приложение перед установкой: нужен, когда прогон начинается с чистого
# состояния, и вреден, когда идёт длинное испытание.
set -euo pipefail

SKL_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=config.sh
source "$SKL_DIR/config.sh"
# shellcheck source=lib.sh
source "$SKL_DIR/lib.sh"

api="${1:-${SKL_API_LEVELS[0]}}"
mode="${2:-}"
serial="$(skl_serial "$api")"

skl_avd_running "$api" || skl_die "образ API $api не запущен: сначала ./start.sh $api"

skl_log "сборка отладочного артефакта"
skl_gradle "$api" ":app:assembleDebug"

apk="$(find "$SKL_REPO_ROOT/app/build/outputs/apk/debug" -name '*.apk' -print -quit)"
[[ -n "$apk" ]] || skl_die "отладочный apk не найден после сборки"

if [[ "$mode" == "--fresh" ]]; then
    skl_log "удаляю прежнюю установку (--fresh)"
    skl_adb -s "$serial" uninstall "$SKL_APP_ID" >/dev/null 2>&1 || true
fi

skl_log "установка $apk на $serial"
# -r — поверх, -g — сразу выдать опасные разрешения времени выполнения
# (POST_NOTIFICATIONS на API 33+): онбординг разрешений — предмет SKL-14, а не условие
# прогона механики. Испытание И-14 разрешение потом отзывает явно.
skl_adb -s "$serial" install -r -g "$apk" >/dev/null || skl_die "установка не прошла"

# Точные будильники: на API 31 и 32 доступ особый и по умолчанию не выдан. Разрешение
# USE_EXACT_ALARM (манифест app) закрывает вопрос на 33+, но не на 31–32.
skl_adb -s "$serial" shell appops set "$SKL_APP_ID" SCHEDULE_EXACT_ALARM allow >/dev/null 2>&1 || true
# Полноэкранные намерения (НАП-29): на API 34+ доступ тоже особый.
skl_adb -s "$serial" shell appops set "$SKL_APP_ID" USE_FULL_SCREEN_INTENT allow >/dev/null 2>&1 || true

skl_app_installed "$api" || skl_die "приложение не видно диспетчеру пакетов после установки"
skl_log "установлено: $SKL_APP_ID на $(skl_platform_line "$api")"
