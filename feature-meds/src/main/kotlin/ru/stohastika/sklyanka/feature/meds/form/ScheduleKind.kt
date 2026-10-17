package ru.stohastika.sklyanka.feature.meds.form

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import ru.stohastika.sklyanka.domain.model.ScheduleRule
import ru.stohastika.sklyanka.feature.meds.R

/**
 * Вид расписания, выбранный в строке вида (`docs/norm/06-design.md` §5.10, ПР-15…ПР-19).
 *
 * Пять значений и ровно пять: «Каждый день» по умолчанию и четыре сложных вида.
 * Порядок объявления — порядок нормы, а не цены (ПР-15): список не сортируется
 * по платности, иначе «от простого к сложному» читалось бы подсовыванием (П-7).
 *
 * [proMarked] — признак **платного варианта** для платного контура (`pro`, ADR-0016).
 * В бесплатной сборке он ни на что не влияет: метки «Про» в списке нет (Р-86), а доступ
 * открыт всем. Свойство сохранено вместе с платным путём формы — точкой выбора витрины
 * в `MedicationFormViewModel`; снятие «По дням недели» с метки (Р-24) там же: первый
 * день недели не открывает витрину.
 */
enum class ScheduleKind(private val label: Int, val proMarked: Boolean) {
    DAILY(R.string.schedule_kind_daily, proMarked = false),
    WEEKDAYS(R.string.schedule_kind_weekdays, proMarked = false),
    INTERVAL(R.string.schedule_kind_interval, proMarked = true),
    CYCLES(R.string.schedule_kind_cycle, proMarked = true),
    STAGES(R.string.schedule_kind_steps, proMarked = true),
    ;

    /** Имя вида в строке и в списке (`schedule_kind_*`, §10). */
    @Composable
    fun label(): String = stringResource(label)

    companion object {

        /**
         * Вид, которым представлено правило.
         *
         * Вырожденные значения читаются буквально, как их читает генератор: «через каждый
         * день» — ежедневное (ПР-15, ПР-17), и строка вида не имеет права называть его
         * сложным.
         */
        fun of(rule: ScheduleRule): ScheduleKind = when (rule) {
            ScheduleRule.Daily -> DAILY
            is ScheduleRule.EveryNDays -> if (rule.days <= 1) DAILY else INTERVAL
            is ScheduleRule.Weekdays -> WEEKDAYS
            is ScheduleRule.Cycles -> CYCLES
            is ScheduleRule.Staged -> STAGES
        }
    }
}
