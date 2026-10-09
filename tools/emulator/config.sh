#!/usr/bin/env bash
# Параметры прогона на эмуляторе — единственное место, где они заданы (SKL-27).
#
# Ни один другой скрипт оснастки не содержит уровней API, имён образов, портов
# и параметров запуска: разъехавшиеся копии этих значений означают, что прогон
# на «том же» образе на самом деле идёт на другом.
#
# Файл только объявляет переменные и ничего не делает: он подключается через source.
# Отсюда и отключённое правило: «переменная не используется» — норма для файла настроек,
# её используют скрипты-потребители.
# shellcheck disable=SC2034

# --- Матрица образов -----------------------------------------------------------
#
# Три уровня API, и каждый стоит в матрице по своей причине:
#
#   26 — minSdk (ПЛ-1, НФТ-38). Ровно та версия, где деградирует ускоренная фоновая
#        задача: expedited ставится только на Android 12+
#        (app/.../reminder/ReminderWork.kt), а ниже работа идёт обычной очередью.
#   31 — граница, с которой expedited существует, и граница особого доступа
#        к точным будильникам (ПЛ-8): поведение планировщика здесь меняется.
#   35 — targetSdk и compileSdk (НФТ-38). Здесь действуют POST_NOTIFICATIONS
#        и ограничения полноэкранных намерений — то есть здесь воспроизводятся
#        И-14 и часть И-15.
#
# Образы — БЕЗ Google APIs: продукт живёт без GMS (ПЛ-4, НФТ-39), и образ
# с Play Services проверял бы не тот продукт. Кандидаты перечислены по приоритету:
# aosp_atd — тот же AOSP без сервисов Google, но урезанный под автотесты и заметно
# быстрее; default — обычный AOSP. Первый доступный в SDK и берётся (avd.sh).
#
# Матрица НФТ-40 этими образами НЕ покрывается: эмулятор — AOSP, а не EMUI.
SKL_API_LEVELS=(26 31 35)

# Архитектура гостя определяется машиной прогона, а не выбором автора матрицы.
#
# Эмулятор исполняет только того гостя, для которого в его сборке есть qemu:
# сборка darwin-aarch64 несёт qemu-system-aarch64 и не несёт qemu-system-x86_64,
# поэтому на машине Apple Silicon образ x86_64 не поднимается вовсе — не «медленно»,
# а никак. Уровни API от этого не зависят: у всех трёх есть образ без сервисов Google
# и в arm64-v8a, и в x86_64, и матрица остаётся той же самой.
skl_host_abi() {
    case "$(uname -m)" in
        arm64 | aarch64) echo "arm64-v8a" ;;
        x86_64 | amd64) echo "x86_64" ;;
        *) return 1 ;;
    esac
}

# Кандидаты пакетов системного образа по уровню API, через пробел, по приоритету.
#
# aosp_atd для API 26 не существует ни в одной архитектуре — отсюда единственный
# кандидат `default` на минимальном уровне.
skl_image_candidates() {
    local abi
    abi="$(skl_host_abi)" || return 1
    case "$1" in
        26) echo "system-images;android-26;default;$abi" ;;
        31) echo "system-images;android-31;aosp_atd;$abi system-images;android-31;default;$abi" ;;
        35) echo "system-images;android-35;aosp_atd;$abi system-images;android-35;default;$abi" ;;
        *) return 1 ;;
    esac
}

# Что проверяет образ — печатается в отчёте прогона (НАП-40: результат без версии
# платформы не переносится ни на какую другую).
skl_image_purpose() {
    case "$1" in
        26) echo "minSdk (ПЛ-1): деградация expedited, каналы уведомлений, отсутствие особого доступа к точным будильникам" ;;
        31) echo "граница expedited и особого доступа к точным будильникам (ПЛ-8)" ;;
        35) echo "targetSdk (НФТ-38): POST_NOTIFICATIONS, ограничения полноэкранных намерений" ;;
        *) return 1 ;;
    esac
}

# --- Имена и порты -------------------------------------------------------------
#
# Имя образа содержит уровень API: два образа одного проекта на одной машине —
# норма, а не исключение.
SKL_AVD_PREFIX="sklyanka-api"

# Консольный порт первого образа. Каждый следующий занимает +2: эмулятор так устроен.
SKL_FIRST_PORT=5560

# --- Параметры запуска ---------------------------------------------------------
#
# Без звука: прогон идёт в терминале. swiftshader_indirect — программная отрисовка:
# у машины сборки может не быть ни дисплея, ни GPU.
SKL_EMULATOR_FLAGS=(
    -no-audio
    -no-boot-anim
    -no-snapshot-save
    -gpu swiftshader_indirect
    -camera-back none
    -camera-front none
)

# Безоконный режим — поведение по умолчанию (SKL-27): окно, которого никто не видит,
# только отнимает кадры у отрисовки. Отдельной строкой, а не в общем наборе, потому что
# видимое окно — осознанное исключение экранного прогона (SKL-96): start.sh добавляет
# этот признак всем запускам, кроме запуска с окном.
SKL_HEADLESS_FLAG="-no-window"

# Отдельно от общих флагов: очистка данных образа НЕ входит в обычный запуск.
# Прогон испытаний живёт между перезагрузками эмулятора (И-1, И-10), и запуск,
# стирающий данные, уничтожал бы предмет измерения. Стирание — только явной командой
# start.sh --wipe.
SKL_WIPE_FLAG="-wipe-data"


# Размер внутренней памяти образа: объёмные проверки базы (VolumeTest) пишут
# десятки тысяч строк, и образ по умолчанию упирается в место.
SKL_AVD_SDCARD_MB=512
SKL_AVD_PARTITION_MB=4096

# --- Таймауты (секунды) --------------------------------------------------------
#
# Загрузка без аппаратного ускорения идёт в разы дольше: таймаут рассчитан
# на худший случай, а не на лучший.
SKL_BOOT_TIMEOUT=600
# Сколько испытание согласно ждать ближайшего планового момента. Половина часа взята
# не из головы: ускоренная нагрузка идёт шагом в минуты, и всё, что дальше, означает
# не медленное напоминание, а отсутствие нагрузки — либо съехавшие часы образа после
# испытаний И-4 … И-6. Без предела `trials.sh auto` уходил в сон на двадцать часов,
# и прогон выглядел работающим, оставаясь стоять.
SKL_TRIAL_WAIT_LIMIT=1800
SKL_SHUTDOWN_TIMEOUT=120
SKL_ADB_TIMEOUT=60

# --- Прогон проверок -----------------------------------------------------------
#
# Группы прогона (TDD §6): быстрое отдельно от объёмного, миграции отдельно.
# Проверок устройства два модуля (SKL-24 §5.2): core-data — настоящая база,
# feature-history — байты отчёта врачу (SKL-41, PdfDocument живёт только на устройстве).
SKL_TEST_MODULES=(":core-data" ":feature-history")
SKL_TEST_TASK="connectedDebugAndroidTest"

SKL_GROUP_FAST_PACKAGES="ru.stohastika.sklyanka.data.repository"
SKL_GROUP_DATA_PACKAGES="ru.stohastika.sklyanka.data.room,ru.stohastika.sklyanka.data.transfer,ru.stohastika.sklyanka.data.reminder,ru.stohastika.sklyanka.data.history,ru.stohastika.sklyanka.feature.history,ru.stohastika.sklyanka.screen"
# Объёмные и гоночные проверки: тысяча повторов в двух потоках и база годового объёма.
SKL_GROUP_HEAVY_CLASSES="ru.stohastika.sklyanka.data.room.VolumeTest,ru.stohastika.sklyanka.data.room.ConcurrencyTest,ru.stohastika.sklyanka.data.history.HistoryQueriesTest,ru.stohastika.sklyanka.data.room.MigrationVolumeTest"
# Миграции — отдельной группой: они поднимают базу предыдущей версии из экспортированных
# схем, и падение здесь означает совсем другое, чем падение запроса (МГ-3). Цепочка
# (SKL-94) идёт сюда же: она поднимает базу каждой выпущенной версии.
SKL_GROUP_MIGRATION_CLASSES="ru.stohastika.sklyanka.data.room.MigrationTest,ru.stohastika.sklyanka.data.reminder.ReminderLogMigrationTest,ru.stohastika.sklyanka.data.room.MigrationChainTest,ru.stohastika.sklyanka.data.room.MigrationCopyStoreTest,ru.stohastika.sklyanka.data.room.DatabaseUpgradeTest"

# --- Экранный прогон (SKL-96) --------------------------------------------------
#
# Compose-проверки на настоящей платформе: модуль app (настоящий MainActivity и весь
# граф), своя группа ui. Пакет проверок дописан в SKL_GROUP_DATA_PACKAGES выше,
# потому что приёмка SKL-27 читает ровно четыре ключа групп и по ним сверяет, что ни
# один файл проверок не выпал; пятый ключ она не увидит, и файлы выглядели бы
# непокрытыми. Исполняет их группа ui (run-tests.sh), а не группа data.
SKL_UI_API_LEVEL=35
SKL_GROUP_UI_PACKAGES="ru.stohastika.sklyanka.screen"
SKL_UI_SCREENSHOT_DIR="ui-screens"

# --- Приложение ----------------------------------------------------------------
SKL_APP_ID="ru.stohastika.sklyanka"
SKL_APP_DB="sklyanka.db"
SKL_DIAGNOSTICS_ACTIVITY="ru.stohastika.sklyanka.feature.reminder.DiagnosticsActivity"
SKL_PERMISSIONS_ACTIVITY="ru.stohastika.sklyanka.feature.onboarding.PermissionsActivity"

# --- Нагрузка синтетического курса (И-16) --------------------------------------
#
# Пять лекарств, восемь приёмов в сутки — нагрузка И-16 из docs/v2/10-behavior.md.
SKL_LOAD_MEDICATIONS=5
SKL_LOAD_TIMES_PER_DAY=8
# Ускоренный прогон: те же восемь приёмов, но с шагом в минутах, а не в часах.
# Это проверка механики, а НЕ гейт НАП-3 (14 суток непрерывно на живом Huawei).
SKL_FAST_STEP_MINUTES=6

# --- Куда кладутся результаты --------------------------------------------------
SKL_RESULTS_DIR="build/emulator"
