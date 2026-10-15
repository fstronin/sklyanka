package ru.stohastika.sklyanka.data.room

import ru.stohastika.sklyanka.domain.model.ScheduleRule
import ru.stohastika.sklyanka.domain.model.Stage
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

/**
 * Преобразование доменных типов в колонки хранения и обратно.
 *
 * Это не Room-конвертеры (`@TypeConverters`), а обычный объект, которым пользуются
 * мапперы. Разница существенная: конвертер Room обязан вернуть значение для ненулевой
 * колонки и не может сказать «я это не понял», а МГ-6 требует ровно этого — незнакомое
 * значение перечисления не роняет чтение, а превращается в отказ с понятной причиной.
 *
 * Сквозное правило: назначенное время (расписание, даты курса, `scheduled_at`) хранится
 * локальным текстом без пояса, момент факта (`acted_at`, `archived_at`, время журнала) —
 * числом epoch millis в UTC. Ошибка здесь проявляется поздно и стоит дорого: при хранении
 * назначенного времени моментом приём «в 08:00» после перелёта приходит в 11:00.
 */
object SklyankaConverters {

    /** `HH:mm` — время суток из расписания. */
    private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)

    /** `YYYY-MM-DD` — календарная граница курса. */
    private val DATE: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE.withLocale(Locale.ROOT)

    /** `YYYY-MM-DDTHH:mm` — назначенное время приёма, с точностью до минуты. */
    private val DATE_TIME: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm", Locale.ROOT)

    private const val TIMES_SEPARATOR = ","

    fun decimalToText(value: BigDecimal?): String? = value?.toPlainString()

    fun textToDecimal(value: String?): BigDecimal? = value?.let(::BigDecimal)

    fun dateToText(value: LocalDate): String = value.format(DATE)

    fun textToDate(value: String): LocalDate = LocalDate.parse(value, DATE)

    fun dateTimeToText(value: LocalDateTime): String = value.format(DATE_TIME)

    fun textToDateTime(value: String): LocalDateTime = LocalDateTime.parse(value, DATE_TIME)

    fun instantToMillis(value: Instant?): Long? = value?.toEpochMilli()

    fun millisToInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)

    /**
     * Список времён приёма — одна отсортированная строка.
     *
     * Отдельная таблица здесь не нужна: список времён есть часть описания лекарства,
     * он не запрашивается отдельно и не изменяется по одному элементу. Пустая строка
     * у курса «по потребности» предпочтительнее NULL — конвертер всегда возвращает
     * список, и INV-6 проверяется одним правилом, а не двумя.
     */
    fun timesToText(value: List<LocalTime>): String = value.sorted().joinToString(TIMES_SEPARATOR) { it.format(TIME) }

    fun textToTimes(value: String): List<LocalTime> = if (value.isBlank()) {
        emptyList()
    } else {
        value.split(TIMES_SEPARATOR).map { LocalTime.parse(it.trim(), TIME) }
    }

    /**
     * Чтение перечисления по имени константы.
     *
     * Незнакомое значение даёт null, а не исключение: запись, сделанная более новой
     * сборкой или пришедшая из файла переноса, не имеет права уронить приложение (МГ-6).
     * Что делать дальше — решает вызывающий слой.
     */
    inline fun <reified T : Enum<T>> textToEnum(value: String?): T? =
        value?.let { name -> enumValues<T>().firstOrNull { it.name == name } }

    fun enumToText(value: Enum<*>?): String? = value?.name

    /** Разбор текста времени, устойчивый к мусору в базе: испорченная строка даёт null. */
    fun textToDateTimeOrNull(value: String): LocalDateTime? = try {
        textToDateTime(value)
    } catch (_: DateTimeParseException) {
        null
    }

    /**
     * Вид расписания — одной строкой (ПР-15, `TDD.md §3`).
     *
     * Отдельная таблица виду не нужна: параметров у него мало, они никогда не спрашиваются
     * по отдельности и меняются только вместе с лекарством — как и `times`, ради которого
     * этот способ хранения уже выбран.
     *
     * Канонический вид строки один и тот же при записи и после чтения: дни недели
     * сортируются по календарю, лишние пробелы не пишутся, этапы идут в порядке формы.
     * Поэтому round-trip сравним целиком, а не «по смыслу».
     */
    fun scheduleRuleToText(value: ScheduleRule): String = when (value) {
        ScheduleRule.Daily -> DAILY

        is ScheduleRule.Weekdays ->
            WEEKDAYS_PREFIX + PARAMETER_SEPARATOR +
                value.days.sorted().joinToString(WEEKDAYS_SEPARATOR) { day -> WEEKDAY_CODES.getValue(day) }

        is ScheduleRule.EveryNDays -> "$EVERY_PREFIX$PARAMETER_SEPARATOR${value.days}"

        is ScheduleRule.Cycles ->
            CYCLES_PREFIX + PARAMETER_SEPARATOR +
                "${value.onDays}$CYCLE_LENGTH_SEPARATOR${value.offDays}"

        is ScheduleRule.Staged ->
            STAGED_PREFIX + PARAMETER_SEPARATOR +
                value.stages.joinToString(STAGE_SEPARATOR) { stage ->
                    "${stage.doseAmount.toPlainString()}$STAGE_DOSE_SEPARATOR${stage.days}"
                }
    }

    /**
     * Чтение вида расписания.
     *
     * Непонятая или испорченная строка читается как **ежедневное** расписание, а не как
     * ошибка чтения: расписание не имеет права исчезнуть из-за формата — так же ведёт себя
     * кэш подписки, а строка может прийти от более новой сборки или из файла переноса,
     * написанного руками (МГ-6). `NULL` — то же самое: так выглядят курсы, заведённые
     * до появления видов.
     *
     * Значения, нарушающие ИНВ-23…ИНВ-24 (пустой набор дней, интервал меньше двух, нулевая
     * длина цикла, этап без дозы), тоже читаются как ежедневные: держать в базе вид,
     * которого не существует, — значит показывать его человеку и однажды сохранить обратно.
     */
    fun textToScheduleRule(value: String?): ScheduleRule {
        val text = value?.trim().orEmpty()
        if (text.isEmpty()) return ScheduleRule.Daily
        val parts = text.split(PARAMETER_SEPARATOR, limit = 2)
        val kind = parts[0]
        val parameters = parts.getOrNull(1)
        return when (kind) {
            DAILY -> ScheduleRule.Daily
            WEEKDAYS_PREFIX -> parameters?.let(::parseWeekdays) ?: ScheduleRule.Daily
            EVERY_PREFIX -> parameters?.toIntOrNull()
                ?.takeIf { it >= MIN_INTERVAL_DAYS }
                ?.let { days -> ScheduleRule.EveryNDays(days) }
                ?: ScheduleRule.Daily

            CYCLES_PREFIX -> parameters?.let(::parseCycles) ?: ScheduleRule.Daily
            STAGED_PREFIX -> parameters?.let(::parseStages) ?: ScheduleRule.Daily
            else -> ScheduleRule.Daily
        }
    }

    private fun parseWeekdays(parameters: String): ScheduleRule {
        val days = parameters.split(WEEKDAYS_SEPARATOR)
            .filter { it.isNotBlank() }
            .mapNotNull { code -> WEEKDAY_CODES.entries.firstOrNull { it.value == code.trim() }?.key }
        return if (days.isEmpty()) ScheduleRule.Daily else ScheduleRule.Weekdays(days.toSet())
    }

    private fun parseCycles(parameters: String): ScheduleRule {
        val parts = parameters.split(CYCLE_LENGTH_SEPARATOR)
        val onDays = parts.getOrNull(0)?.trim()?.toIntOrNull()
        val offDays = parts.getOrNull(1)?.trim()?.toIntOrNull()
        val lengths = listOfNotNull(onDays, offDays).filter { length -> length >= MIN_CYCLE_DAYS }
        return if (lengths.size == CYCLE_LENGTHS) {
            ScheduleRule.Cycles(onDays = lengths[0], offDays = lengths[1])
        } else {
            ScheduleRule.Daily
        }
    }

    private fun parseStages(parameters: String): ScheduleRule {
        val stages = parameters.split(STAGE_SEPARATOR).map(::parseStage)
        return if (stages.isEmpty() || stages.any { it == null }) {
            ScheduleRule.Daily
        } else {
            ScheduleRule.Staged(stages.filterNotNull())
        }
    }

    /** Этап строки вида: доза и длительность обязаны быть положительными (ИНВ-24). */
    private fun parseStage(text: String): Stage? {
        val fields = text.split(STAGE_DOSE_SEPARATOR)
        val dose = fields.getOrNull(0)?.trim()?.toBigDecimalOrNull()
        val days = fields.getOrNull(1)?.trim()?.toIntOrNull()
        if (dose == null || days == null) return null
        return Stage(dose, days).takeIf { dose > BigDecimal.ZERO && days >= MIN_STAGE_DAYS }
    }

    private const val DAILY = "daily"
    private const val WEEKDAYS_PREFIX = "weekdays"
    private const val EVERY_PREFIX = "every"
    private const val CYCLES_PREFIX = "cycles"
    private const val STAGED_PREFIX = "staged"

    /** Разделяет вид и его параметры: `weekdays:MO,WE`. */
    private const val PARAMETER_SEPARATOR = ":"

    /** Разделяет дни недели: `MO,WE,FR`. */
    private const val WEEKDAYS_SEPARATOR = ","

    /** Разделяет длины цикла: `21/7`. */
    private const val CYCLE_LENGTH_SEPARATOR = "/"

    /** Разделяет этапы: `0.5x7;1x24`. */
    private const val STAGE_SEPARATOR = ";"

    /** Разделяет дозу и длительность этапа: `0.5x7`. */
    private const val STAGE_DOSE_SEPARATOR = "x"

    /** ПР-17: «через N дней» начинается с двух; меньше — это уже ежедневное. */
    private const val MIN_INTERVAL_DAYS = 2

    /**
     * Двухбуквенные обозначения дней недели — формат хранения, а не язык интерфейса.
     *
     * Не `DayOfWeek.name` и не локализованное сокращение: имя константы длинно («MONDAY»),
     * а локализованное сокращение менялось бы вместе с языком телефона и уехало в базу —
     * тогда курс, заведённый по-русски, не читался бы после смены языка (НФТ-37).
     */
    private val WEEKDAY_CODES: Map<DayOfWeek, String> = mapOf(
        DayOfWeek.MONDAY to "MO",
        DayOfWeek.TUESDAY to "TU",
        DayOfWeek.WEDNESDAY to "WE",
        DayOfWeek.THURSDAY to "TH",
        DayOfWeek.FRIDAY to "FR",
        DayOfWeek.SATURDAY to "SA",
        DayOfWeek.SUNDAY to "SU",
    )

    /** ПР-18: и приём, и перерыв в цикле длятся хотя бы сутки. */
    private const val MIN_CYCLE_DAYS = 1

    /** В строке `cycles:N/M` ровно две длины — приёма и перерыва. */
    private const val CYCLE_LENGTHS = 2

    /** ИНВ-24: этап длится хотя бы сутки. */
    private const val MIN_STAGE_DAYS = 1
}
