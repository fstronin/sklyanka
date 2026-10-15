package ru.stohastika.sklyanka.domain.medication

import ru.stohastika.sklyanka.domain.model.Medication
import ru.stohastika.sklyanka.domain.model.ScheduleMode
import ru.stohastika.sklyanka.domain.model.ScheduleRule
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.temporal.ChronoUnit

/**
 * Суточный расход упаковки по виду расписания (ПР-13, ПМ-13).
 *
 * Расход — доля дней приёма в периоде, умноженная на дозу дня и на число времён:
 *
 *     расход = число времён × доза дня × доля дней приёма
 *
 * Вид расписания меняет именно долю дней: «по понедельникам, средам и пятницам» —
 * это три дня из семи, «через два дня» — каждый второй, «21 день приёма, 7 перерыва» —
 * двадцать один из двадцати восьми. Считать расход как «доза на число времён» (как было
 * до появления видов) значило бы для недельного курса завысить расход в семь раз и объявить
 * «пора покупать» в разы раньше срока, а для цикличного и ступенчатого — занизить
 * и опоздать с предупреждением (SKL-117, критика F2).
 *
 * **Одно определение на весь продукт.** Расход читают прогноз запаса (`StockForecast`)
 * и порог покупки (`MedicationListRules.stockThresholdReached`), и это тот же расход,
 * что видит экран лекарства: вторая формула разошлась бы с первой, а расхождение здесь
 * читается человеком как ложь о его упаковке.
 *
 * Отдельной функции на весь продукт одного правила мало — оно обязано быть и у пробы: тесты
 * недельного, цикличного и ступенчатого курсов живут рядом с ним (`MedicationDailyUseTest`).
 */
object MedicationDailyUse {

    /**
     * Суточный расход или `null`, когда ответа нет.
     *
     * `null` — это не «ноль», а «расход неизвестен»: приём «по потребности» (ОВ-39) и
     * расписание без единого времени приёма его не определяют. Делить на такой расход
     * нечем, и число дней в этих случаях не существует, а не равно бесконечности.
     *
     * Нулевая доля дней (испорченный цикл без дней приёма) даёт тот же ответ по той же
     * причине: приёмов не бывает, и обещать «хватит навсегда» приложение не вправе.
     */
    fun of(medication: Medication): BigDecimal? {
        if (medication.scheduleMode != ScheduleMode.SCHEDULED || medication.times.isEmpty()) return null
        return BigDecimal(medication.times.size)
            .multiply(doseOfAnIntakeDay(medication))
            .multiply(intakeDayShare(medication.schedule))
            .takeIf { it > BigDecimal.ZERO }
    }

    /**
     * Доля дней приёма в периоде вида: у ежедневного — единица, у остальных — своя.
     *
     * Период берётся у самого вида: неделя у «по дням недели» (Р-24, ПР-16), N суток
     * у «через N дней» (ПР-17), N + M у циклов (ПР-18). Доля, а не число дней, потому
     * что расход считается на **календарные** сутки курса.
     */
    fun intakeDayShare(rule: ScheduleRule): BigDecimal = when (rule) {
        ScheduleRule.Daily -> ONE
        is ScheduleRule.Weekdays -> if (rule.days.isEmpty()) {
            ZERO
        } else {
            BigDecimal(rule.days.size).divide(BigDecimal(DAYS_IN_WEEK), SHARE_SCALE, RoundingMode.HALF_UP)
        }

        // «Через каждый день» (N ≤ 1) — ежедневное расписание: вырожденный вид не наказывается.
        is ScheduleRule.EveryNDays -> if (rule.days <= 1) {
            ONE
        } else {
            ONE.divide(BigDecimal(rule.days), SHARE_SCALE, RoundingMode.HALF_UP)
        }

        is ScheduleRule.Cycles -> {
            val period = rule.onDays.toLong() + rule.offDays.toLong()
            if (period <= 0L || rule.onDays <= 0) {
                ZERO
            } else {
                BigDecimal(rule.onDays).divide(BigDecimal(period), SHARE_SCALE, RoundingMode.HALF_UP)
            }
        }

        // Ступени дозу меняют, но не дни: приём идёт каждый день курса (ПР-19).
        is ScheduleRule.Staged -> ONE
    }

    /**
     * Доза дня приёма: у ступенчатого вида — средняя по этапам, у остальных — доза лекарства.
     *
     * Ступени идут подряд от даты начала курса, и доза курса — доза **первого** этапа
     * (`FieldApplicability.effectiveDoseAmount`): она верна для карточки и превью, но
     * расходом управляет весь курс. Среднее взвешено днями этапов, а этапы за концом курса
     * в него не входят — их там не бывает (ИНВ-24).
     *
     * У испорченного значения без этапов ответа нет вовсе: делить не на что.
     */
    fun doseOfAnIntakeDay(medication: Medication): BigDecimal {
        val rule = medication.schedule
        if (rule !is ScheduleRule.Staged) return medication.doseAmount
        val span = courseSpan(medication, rule)
        val weighted = rule.stages.fold(BigDecimal.ZERO to 0L) { (sum, days), stage ->
            val taken = stage.days.toLong().coerceAtMost((span - days).coerceAtLeast(0L))
            (sum.add(stage.doseAmount.multiply(BigDecimal(taken))) to days + taken)
        }
        val (sum, days) = weighted
        return if (days <= 0L) {
            BigDecimal.ZERO
        } else {
            sum.divide(BigDecimal(days), DOSE_SCALE, RoundingMode.HALF_UP)
        }
    }

    /**
     * Сколько суток идут этапы внутри курса.
     *
     * Бессрочный курс (ПР-6) не обрезает этапы вовсе: среднее считается по назначенным,
     * а продолжение последней ступенью за их концом — тот же предел, что у разворачивателя
     * плана, и оценка расхода остаётся оценкой.
     */
    private fun courseSpan(medication: Medication, rule: ScheduleRule.Staged): Long {
        val end = medication.endDate ?: return rule.stages.sumOf { it.days.toLong() }
        val days = ChronoUnit.DAYS.between(medication.startDate, end) + 1
        return days.coerceAtLeast(0L)
    }

    /** Дней в неделе — период вида «по дням недели» (ПР-16). */
    private const val DAYS_IN_WEEK = 7

    /** Доля округляется до миллионных: расход — оценка, а не измерение. */
    private const val SHARE_SCALE = 6

    /** Средняя доза этапов — тоже оценка; знаков заведомо больше, чем нужно делению. */
    private const val DOSE_SCALE = 8

    private val ONE = BigDecimal.ONE
    private val ZERO = BigDecimal.ZERO
}
