package ru.stohastika.sklyanka.feature.meds.form

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import ru.stohastika.sklyanka.domain.model.ScheduleRule
import ru.stohastika.sklyanka.feature.meds.R

/**
 * Вид расписания, выбранный в строке вида (`docs/norm/06-design.md` §5.10, ПР-15…ПР-19).
 *
 * Пять значений и ровно пять: «Каждый день» по умолчанию и четыре сложных вида.
 * Порядок объявления — порядок нормы (ПР-15).
 */
enum class ScheduleKind(private val label: Int) {
    DAILY(R.string.schedule_kind_daily),
    WEEKDAYS(R.string.schedule_kind_weekdays),
    INTERVAL(R.string.schedule_kind_interval),
    CYCLES(R.string.schedule_kind_cycle),
    STAGES(R.string.schedule_kind_steps),
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
