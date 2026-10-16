package ru.stohastika.sklyanka.domain.history

import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

/**
 * Числа одного периода для раздела сравнения (`docs/norm/06-design.md` §5.15, SKL-119).
 *
 * Те же четыре показателя, что в сводке документа, и те же построители чисел: второй
 * формулировки у одного показателя в продукте быть не может — врач читает один документ,
 * и «принято» в сводке обязано значить то же, что «принято» в сравнении.
 *
 * [due] — сколько приёмов периода **наступило** (время прошло, исход известен или нет).
 * Этим числом решается, есть ли чем сравнивать: период, целиком лежащий в будущем,
 * сравнения не даёт (Р-33).
 */
data class PeriodSummary(
    /** Границы периода: их называет строка меты раздела сравнения. */
    val from: LocalDate,
    val to: LocalDate,
    val taken: Int,
    val planned: Int,
    val onTimeShare: OnTimeShare,
    val daysWithoutMisses: Int,
    val misses: Int,
    val due: Int,
) {

    /** Доля принятого в процентах — тем же округлением, что у сводки и разбивки. */
    val takenPercent: Int
        get() = if (planned == 0) 0 else Math.round(taken.toDouble() / planned * PERCENT).toInt()

    private companion object {
        const val PERCENT = 100
    }
}

/**
 * Сравнение периодов: отчётный период и такой же по длине, идущий непосредственно перед ним.
 *
 * Правым столбцом раздела стоит [previous], и называет его одна строка меты: таблица
 * из двух колонок не заводит заголовка, потому что вторых чисел в разделе нет.
 */
data class PeriodComparison(val current: PeriodSummary, val previous: PeriodSummary)

/**
 * Месяц периода в разделе динамики.
 *
 * [planned] — наступившие приёмы месяца вместе с сегодняшними (то же правило знаменателя,
 * что у разбивки по лекарствам, SKL-10): иначе динамика спорила бы с экраном о том же месяце.
 */
data class MonthAdherence(val month: YearMonth, val taken: Int, val planned: Int) {

    val takenPercent: Int
        get() = if (planned == 0) 0 else Math.round(taken.toDouble() / planned * PERCENT).toInt()

    /**
     * Доля посчитана, но при округлении к целому проценту даёт ноль (Р-27).
     *
     * Показывается словами «< 1 %», как на экране: «0 %» при состоявшихся приёмах — ложь,
     * из-за которой метрике перестают верить (`history_percent_less_than_one`).
     */
    val lessThanOnePercent: Boolean get() = taken > 0 && takenPercent == 0

    private companion object {
        const val PERCENT = 100
    }
}

/** Динамика по месяцам периода: месяцы по порядку, месяцы без наступивших приёмов не входят. */
data class MonthDynamics(val months: List<MonthAdherence>)

/**
 * Паттерн пропусков — второй выход того же правила, что печатает строку-вывод «Истории»
 * (Р-85 п. 4): время-лидер, даты пропусков этого времени и разделение по природе.
 *
 * Разрез здесь **по времени**, а не по лекарству: те же даты, что в базовом документе,
 * но собранные иначе — ради этого разреза раздел и печатается. Природа пропуска сохраняется
 * по той же причине, что у лекарства: отметка человека говорит о поведении, автоматический
 * перевод — о том, что телефон мог быть выключен (ПР-1).
 *
 * Числом [misses] правило-лидер себя не повторяет: сколько всего пропусков за период,
 * знает сводка (`DoctorReport.period`), и второго счёта того же числа здесь нет.
 */
data class MissedPatternBlock(
    val time: LocalTime,
    /** Даты пропусков этого времени — перечень, а не число: документ их печатает. */
    val missedDates: List<LocalDate>,
    /** Сколько из них отмечено человеком. */
    val skippedByPerson: Int,
    /** Сколько переведено приложением без отметки. */
    val missedWithoutMark: Int,
) {

    /** Пропусков в это время за период. */
    val misses: Int get() = skippedByPerson + missedWithoutMark
}

/**
 * Разделы «Про» расширенного отчёта — и то, чего в документе не будет.
 *
 * **Пустой раздел — `null`** (Р-33): раздела без данных не существует, и прочерка на его
 * месте тоже — композер печатает только непустые. Поэтому пустой период вовсе не даёт
 * ни одного раздела, а не три пустых: документ остаётся базовым и несёт одну фразу.
 */
data class ExtendedReportSections(
    /** `null` — сравнивать не с чем: в предыдущем периоде нет наступивших приёмов. */
    val comparison: PeriodComparison? = null,
    /** `null` — динамики нет: в периоде меньше двух месяцев с наступившими приёмами. */
    val dynamics: MonthDynamics? = null,
    /** `null` — правило о самом пропускаемом приёме молчит (пороги 0,6 / 4 / 2, лидер один). */
    val pattern: MissedPatternBlock? = null,
) {

    val isEmpty: Boolean get() = comparison == null && dynamics == null && pattern == null

    companion object {

        /** Ни одного раздела: с этим значением собирается базовый документ. */
        val NONE = ExtendedReportSections()
    }
}
