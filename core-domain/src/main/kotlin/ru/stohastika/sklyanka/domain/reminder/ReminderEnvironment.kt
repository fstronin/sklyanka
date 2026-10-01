package ru.stohastika.sklyanka.domain.reminder

/**
 * Факты устройства, которые даёт платформа (НФТ-19, НФТ-46).
 *
 * Идентификаторов профилей, приёмов и лекарств здесь нет и взяться им неоткуда: журнал
 * хранит только служебные коды и версии.
 */
data class DeviceEnvironment(
    val manufacturer: String,
    val brand: String,
    val osRelease: String,
    val buildDisplay: String,
    val batteryUnrestricted: Boolean,
)

/**
 * Среда измерения: действующий механизм будильников и факты устройства.
 *
 * Пишется в журнал записью `ENVIRONMENT` и задаёт границы окон A/B-прогона пилота:
 * строки между двумя записями среды относятся к одному варианту. Формат нагрузки —
 * пары `ключ=значение`, потому что в `core-domain` нет JSON-библиотеки, а в CSV строка
 * обязана читаться глазами.
 */
data class ReminderEnvironment(val alarmKind: AlarmKind, val device: DeviceEnvironment) {

    /**
     * Нагрузка записи: `alarm_kind=alarm_clock;manufacturer=HUAWEI;…`.
     *
     * Порядок ключей фиксирован, чтобы файлы разных устройств сравнивались построчно.
     * Значения экранируются: `%`, `;` и `=` не имеют права разорвать пару или строку.
     */
    fun encode(): String = listOf(
        ALARM_KIND to alarmKind.code,
        MANUFACTURER to device.manufacturer,
        BRAND to device.brand,
        OS_RELEASE to device.osRelease,
        BUILD_DISPLAY to device.buildDisplay,
        BATTERY_UNRESTRICTED to device.batteryUnrestricted.toString(),
    ).joinToString(SEPARATOR.toString()) { (key, value) -> "$key${EQUAL}${escape(value)}" }

    companion object {
        private const val SEPARATOR = ';'
        private const val EQUAL = '='

        private const val ALARM_KIND = "alarm_kind"
        private const val MANUFACTURER = "manufacturer"
        private const val BRAND = "brand"
        private const val OS_RELEASE = "os_release"
        private const val BUILD_DISPLAY = "build_display"
        private const val BATTERY_UNRESTRICTED = "battery_unrestricted"

        private val KEYS = setOf(
            ALARM_KIND,
            MANUFACTURER,
            BRAND,
            OS_RELEASE,
            BUILD_DISPLAY,
            BATTERY_UNRESTRICTED,
        )

        /**
         * Разбор нагрузки; `null` — строка не является записью среды (МГ-6).
         *
         * Требуются все шесть ключей ровно один раз: чужая или подрезанная строка обязана
         * выпасть, а не дать среду с пустым полем.
         */
        @Suppress("ReturnCount")
        fun parse(value: String): ReminderEnvironment? {
            if (value.isEmpty()) return null
            val pairs = value.split(SEPARATOR)
            if (pairs.size != KEYS.size) return null
            val map = mutableMapOf<String, String>()
            pairs.forEach { pair ->
                val separator = pair.indexOf(EQUAL)
                if (separator <= 0) return null
                val key = pair.substring(0, separator)
                if (key !in KEYS || key in map) return null
                map[key] = pair.substring(separator + 1)
            }
            if (map.keys != KEYS) return null
            val kind = AlarmKind.entries.firstOrNull { it.code == map[ALARM_KIND] } ?: return null
            val battery = when (map[BATTERY_UNRESTRICTED]) {
                true.toString() -> true
                false.toString() -> false
                else -> return null
            }
            return ReminderEnvironment(
                alarmKind = kind,
                device = DeviceEnvironment(
                    manufacturer = unescape(map.getValue(MANUFACTURER)),
                    brand = unescape(map.getValue(BRAND)),
                    osRelease = unescape(map.getValue(OS_RELEASE)),
                    buildDisplay = unescape(map.getValue(BUILD_DISPLAY)),
                    batteryUnrestricted = battery,
                ),
            )
        }

        private fun escape(value: String): String = buildString(value.length) {
            value.forEach { symbol ->
                when (symbol) {
                    '%' -> append("%25")
                    ';' -> append("%3B")
                    '=' -> append("%3D")
                    else -> append(symbol)
                }
            }
        }

        /** Длина кода экранирования: `%` плюс две шестнадцатеричные цифры. */
        private const val ESCAPE_CODE_LENGTH = 3

        /** Обратный разбор по символу: `%25` в исходном значении не превращается в `%` дважды. */
        private fun unescape(value: String): String = buildString(value.length) {
            var index = 0
            while (index < value.length) {
                val symbol = value[index]
                if (symbol != '%' || index + ESCAPE_CODE_LENGTH > value.length) {
                    append(symbol)
                    index++
                    continue
                }
                when (value.substring(index, index + ESCAPE_CODE_LENGTH).uppercase()) {
                    "%25" -> append('%')
                    "%3B" -> append(';')
                    "%3D" -> append('=')
                    else -> append(symbol)
                }
                index += ESCAPE_CODE_LENGTH
            }
        }
    }
}
