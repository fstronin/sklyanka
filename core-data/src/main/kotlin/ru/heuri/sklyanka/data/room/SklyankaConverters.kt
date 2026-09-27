package ru.heuri.sklyanka.data.room

import java.math.BigDecimal
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
}
