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
 * [proMarked] — признак того, что **вариант в списке** несёт метку «Про» (§5.9, Р-67 п. 5).
 * Метка — свойство элемента, а не подписки: она стоит у платного варианта и тогда, когда
 * подписка активна. «По дням недели» метки не несёт: один день недели — база (Р-24),
 * и метка обещала бы плату там, где базовое разрешено; платная часть отмечена на строке
 * дней словами «Больше одного дня».
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
