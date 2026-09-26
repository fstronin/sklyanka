package ru.heuri.sklyanka.domain.medication

import ru.heuri.sklyanka.domain.model.Medication
import ru.heuri.sklyanka.domain.model.ScheduleMode
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

/**
 * Правая часть строки расписания в карточке списка (ЭКР-4).
 *
 * Запечатанный тип, а не готовая строка с условием в разметке: правило «при сработавшем
 * пороге остатка показываем остаток, иначе период приёма» предметное и проверяется тестом
 * домена, а не разглядыванием экрана. Остаток и период в этой строке одновременно
 * не показываются никогда.
 */
sealed interface TrailingLine {

    /** «осталось 9 таблеток» — порог остатка сработал. */
    data class Stock(val left: BigDecimal, val unit: DoseUnit) : TrailingLine

    /** «с 12 марта · бессрочно» или «до 30 сентября». */
    data class Period(val startDate: LocalDate, val endDate: LocalDate?) : TrailingLine
}

/**
 * Правила списка лекарств.
 *
 * Живут в домене, а не в экране, потому что тем же счётчиком активных курсов
 * пользуется подпись прогресса на экране «Сегодня» (SKL-08, ЭКР-2): два независимых
 * подсчёта того же числа разойдутся.
 */
object MedicationListRules {

    /**
     * Счётчик «Активные» (ЭКР-3).
     *
     * Считаются только курсы по расписанию. Лекарство «по потребности» в списке
     * показывается, но в счётчик не входит: у него нет ни одного запланированного приёма,
     * и «активным курсом» в смысле расписания оно не является. Правило выведено из
     * расхождения в макете `design/screens/Meds.dc.html` — «Активные · 5» при шести
     * нарисованных карточках.
     */
    fun activeCount(medications: List<Medication>): Int =
        medications.count { !it.isArchived && it.scheduleMode == ScheduleMode.SCHEDULED }

    /** Правая часть строки расписания: остаток при сработавшем пороге, иначе период (ЭКР-4). */
    fun trailingLineOf(medication: Medication): TrailingLine {
        val left = medication.stockLeft
        return if (left != null && stockThresholdReached(medication)) {
            TrailingLine.Stock(left = left, unit = DoseUnit.ofCode(medication.doseUnit) ?: medication.form.doseUnit())
        } else {
            TrailingLine.Period(startDate = medication.startDate, endDate = medication.endDate)
        }
    }

    /**
     * Порог остатка сработал (ПР-13).
     *
     * Условие полное: заданы и остаток, и порог, и запаса хватает менее чем на пороговое
     * число дней при текущем расписании. Выключенный переключатель «Напомнить о покупке» —
     * это пустой порог, и тогда предупреждение не показывается вовсе.
     *
     * Для режима «по потребности» суточный расход не определён (`docs/spec/02-data-model.md`
     * §3, открытый вопрос), поэтому порог не срабатывает никогда: показать предупреждение,
     * посчитанное неизвестно как, хуже, чем не показать его.
     */
    fun stockThresholdReached(medication: Medication): Boolean {
        val days = daysOfStockLeft(medication)
        val threshold = medication.stockThresholdDays
        return days != null && threshold != null && days < threshold
    }

    /**
     * На сколько целых дней хватит остатка при текущем расписании.
     *
     * `null`, когда ответа нет: остаток не ведётся, расписания нет или суточный расход
     * равен нулю. Деление округляется вниз: «хватит на 2,7 дня» — это два дня, а не три.
     */
    fun daysOfStockLeft(medication: Medication): Int? {
        val perDay = dailyConsumption(medication)?.takeIf { it > BigDecimal.ZERO }
        return medication.stockLeft?.let { left ->
            perDay?.let { left.divide(it, 0, RoundingMode.FLOOR).toInt() }
        }
    }

    /** Суточный расход: доза на приём, умноженная на число приёмов в сутки. */
    private fun dailyConsumption(medication: Medication): BigDecimal? = medication.times
        .takeIf { medication.scheduleMode == ScheduleMode.SCHEDULED && it.isNotEmpty() }
        ?.let { times -> medication.doseAmount.multiply(BigDecimal(times.size)) }
}
