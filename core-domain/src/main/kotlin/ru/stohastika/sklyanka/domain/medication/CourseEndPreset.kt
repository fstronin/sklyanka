package ru.stohastika.sklyanka.domain.medication

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Пресет срока окончания курса (ПМ-12, SKL-75).
 *
 * Человек задавал дату окончания вручную и ошибался в счёте. Пресет — это готовый
 * срок от даты начала: недели, месяцы и год. Живёт в домене, потому что это правило
 * предметной области («сколько длится курс»), а не разметка и не строка; форма лишь
 * раскладывает [Options] по чипам.
 *
 * «Месяц» и «год» — календарные: [LocalDate.plus] с [ChronoUnit.MONTHS]/[ChronoUnit.YEARS]
 * поджимает конец месяца по календарю (31 января + 1 месяц → 28 февраля;
 * 29 февраля 2028 + 1 год → 28 февраля 2029), чего требует ПМ-12. Часовой пояс и полночь
 * к дате окончания не применяются: это календарная граница, а не момент (НФТ-34).
 */
@Suppress("MagicNumber")
enum class CourseEndPreset(val amount: Long, val unit: ChronoUnit) {

    WEEK_1(1, ChronoUnit.WEEKS),
    WEEKS_2(2, ChronoUnit.WEEKS),
    MONTH_1(1, ChronoUnit.MONTHS),
    MONTHS_2(2, ChronoUnit.MONTHS),
    MONTHS_3(3, ChronoUnit.MONTHS),
    MONTHS_6(6, ChronoUnit.MONTHS),
    YEAR_1(1, ChronoUnit.YEARS),
    ;

    /** Дата окончания курса, начатого в [start]; «месяц» и «год» — календарные. */
    fun endDateFrom(start: LocalDate): LocalDate = start.plus(amount, unit)

    companion object {

        /**
         * Единственный список пресетов (ПМ-12).
         *
         * Форма читает его, а не перечисляет пресеты иначе: состав меняется здесь, и
         * изменение списка не требует переделки формы.
         */
        val Options: List<CourseEndPreset> = entries
    }
}
