package ru.heuri.sklyanka.acceptance

import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

/**
 * Разбор дат, времён и числительных русских сценариев.
 *
 * Сценарии написаны для человека и говорят «с 20 по 24 сентября 2026»: у первой границы
 * нет ни месяца, ни года. Недостающие части берутся из соседней даты, а не угадываются.
 */
object RussianDates {

    private val MONTHS = listOf(
        "января", "февраля", "марта", "апреля", "мая", "июня",
        "июля", "августа", "сентября", "октября", "ноября", "декабря",
    )

    /** Названия месяцев в именительном падеже: «за сентябрь 2026». */
    private val MONTHS_NOMINATIVE = listOf(
        "январь", "февраль", "март", "апрель", "май", "июнь",
        "июль", "август", "сентябрь", "октябрь", "ноябрь", "декабрь",
    )

    private val NUMERALS = mapOf(
        "один" to 1,
        "одного" to 1,
        "два" to 2,
        "три" to 3,
        "четыре" to 4,
        "пять" to 5,
        "шесть" to 6,
        "семь" to 7,
        "восемь" to 8,
        "восьми" to 8,
        "девять" to 9,
        "десять" to 10,
        "одиннадцать" to 11,
        "двенадцать" to 12,
        "тринадцать" to 13,
        "тринадцати" to 13,
        // Творительный падеж: «профиль с четырьмя лекарствами». Числительное в сценарии
        // стоит в том падеже, которого требует фраза, и разбор обязан это выдерживать —
        // иначе шаг заставляет переписать фразу под себя.
        "одним" to 1,
        "двумя" to 2,
        "тремя" to 3,
        "четырьмя" to 4,
        "пятью" to 5,
        "шестью" to 6,
        "семью" to 7,
    )

    /** Дата из текста вида «1 октября 2026», «26 сентября» или «20». Недостающее берётся из [reference]. */
    fun date(text: String, reference: LocalDate): LocalDate {
        val parts = text.trim().split(WHITESPACE)
        val day = parts[0].toInt()
        val month = parts.getOrNull(1)?.let { MONTHS.indexOf(it) + 1 } ?: reference.monthValue
        val year = parts.getOrNull(2)?.toInt() ?: reference.year
        return LocalDate.of(year, month, day)
    }

    /** Перечисление дат: «25, 26 и 27 сентября 2026». */
    fun dates(text: String, reference: LocalDate): List<LocalDate> {
        val chunks = text.replace(" и ", ", ").split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val last = date(chunks.last(), reference)
        return chunks.dropLast(1).map { date(it, last) } + last
    }

    /** Отрезок дат: у левой границы месяц и год берутся у правой. */
    fun range(fromText: String, toText: String, reference: LocalDate): List<LocalDate> {
        val to = date(toText, reference)
        val from = date(fromText, to)
        return generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }.toList()
    }

    /**
     * Месяц и год из текста «сентябрь 2026» или «сентября 2026».
     *
     * Сценарий называет период и именительным падежом («за сентябрь»), и родительным;
     * год может отсутствовать и берётся из текущего дня сценария.
     */
    fun month(text: String, reference: LocalDate): YearMonth {
        val parts = text.trim().split(WHITESPACE)
        val name = parts[0].lowercase()
        val index = MONTHS.indexOf(name).takeIf { it >= 0 }
            ?: MONTHS_NOMINATIVE.indexOf(name).takeIf { it >= 0 }
        val month = requireNotNull(index) { "unknown month: ${parts[0]}" } + 1
        return YearMonth.of(parts.getOrNull(1)?.toInt() ?: reference.year, month)
    }

    /** Времена приёма: «08:00, 14:00 и 20:00». */
    fun times(text: String): List<LocalTime> =
        text.split(",", " и ").map { it.trim() }.filter { it.isNotEmpty() }.map(LocalTime::parse)

    /** Число словом или цифрой: «три», «3». */
    fun count(text: String): Int = NUMERALS[text.trim()] ?: text.trim().toInt()

    /**
     * Количество из текста сценария: «1», «0,5».
     *
     * Дробь в русском тексте пишется через запятую, и разбирать её как число
     * с точкой нечем — [BigDecimal] такую запись не принимает.
     */
    fun amount(text: String): BigDecimal = BigDecimal(text.trim().replace(',', '.'))

    private val WHITESPACE = Regex("\\s+")
}
