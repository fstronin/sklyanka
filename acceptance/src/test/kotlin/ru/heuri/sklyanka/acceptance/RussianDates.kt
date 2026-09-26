package ru.heuri.sklyanka.acceptance

import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime

/**
 * Разбор дат, времён и числительных русских сценариев.
 *
 * Сценарии написаны для человека и говорят «с 20 по 24 сентября 2026»: у первой границы
 * нет ни месяца, ни года. Недостающие части берутся из соседней даты, а не угадываются.
 */
internal object RussianDates {

    private val MONTHS = listOf(
        "января", "февраля", "марта", "апреля", "мая", "июня",
        "июля", "августа", "сентября", "октября", "ноября", "декабря",
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
