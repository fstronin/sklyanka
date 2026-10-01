package ru.stohastika.sklyanka.domain.reminder

import java.time.Duration

/**
 * Сводка «сколько напоминаний сработало вовремя за последние 7 дней» (НФТ-51).
 *
 * Строка, а не проценты из головы: [fired] — сколько срабатываний было, [onTime] — сколько
 * из них уложилось в НАП-2. Доля считается только когда считать есть что: «0 %» при
 * отсутствии срабатываний — ложь ровно того сорта, за которую человек перестаёт верить
 * диагностике, а «нет данных» — правда, отличающая «не работает» от «ещё не пробовало».
 */
data class ReminderOnTimeSummary(val fired: Int, val onTime: Int, val days: Long) {

    /** Есть ли что показывать: без срабатываний доли не существует, и её не будет. */
    val hasData: Boolean get() = fired > 0

    /** Целые проценты для строки диагностики; null, когда срабатываний не было. */
    val percent: Int? get() = if (fired == 0) null else onTime * PERCENT / fired

    private companion object {
        const val PERCENT = 100
    }
}

/**
 * Сводка за окно: сколько напоминаний сработало и сколько приёмов пропущено (ОВ-49, SKL-79).
 *
 * Отвечает на вопрос «работает ли оно вообще» простыми словами, а не долей: пропущенные
 * приёмы — вторая половина ответа, и без неё «100 %» на одном срабатывании читалось бы
 * как доказательство надёжности.
 */
data class ReminderWeekSummary(val fired: Int, val missed: Int) {

    /** Есть ли что показывать: без срабатываний и пропусков фразы не существует. */
    val hasData: Boolean get() = fired > 0 || missed > 0
}

/**
 * Правило недельной сводки (ОВ-49).
 *
 * Срабатывания считаются по `SHOWN` — по предъявлению уведомления человеку, как и
 * «вовремя»; пропущенные — по `MISSED_SWEEP` за то же окно (НАП-34).
 */
object ReminderWeekSummaryRule {

    /**
     * @param days глубина окна — та же, что у «вовремя» (НФТ-51); срез уже сделан вызывающим,
     *   параметр держит подписи двух сводок одинаковыми и документирует окно.
     */
    @Suppress("UnusedParameter")
    fun summarize(
        entries: List<ReminderLogEntry>,
        days: Long = ReminderOnTimeRule.WINDOW.toDays(),
    ): ReminderWeekSummary = ReminderWeekSummary(
        fired = entries.count { it.event == ReminderEvent.SHOWN },
        missed = entries.count { it.event == ReminderEvent.MISSED_SWEEP },
    )
}

/**
 * Правило «сработало вовремя» (НАП-2) и глубина сводки (НФТ-51).
 *
 * Отдельно от [ru.stohastika.sklyanka.domain.history.OnTimeRule] намеренно: там мерой служит
 * опоздание **человека** с отметкой, и порог для неё не согласован (ОВ-4). Здесь мерой
 * служит опоздание **приложения**, и оно задано требованием числом.
 */
object ReminderOnTimeRule {

    /** Диапазон отклонения, при котором напоминание считается сработавшим вовремя (НАП-2). */
    val TOLERANCE: Duration = Duration.ofSeconds(120)

    /** Глубина сводки: последние 7 суток (НФТ-51). */
    val WINDOW: Duration = Duration.ofDays(7)

    /**
     * Отклонение в пределах НАП-2.
     *
     * Отрицательное отклонение вовремя **не** считается: срабатывание раньше планового
     * времени — дефект планирования, а не удача, и прятать его в долю «вовремя» значит
     * прятать ошибку (`ReminderLogEntry.deviationSeconds`).
     */
    fun isOnTime(deviationSeconds: Long?): Boolean =
        deviationSeconds != null && deviationSeconds >= 0 && deviationSeconds <= TOLERANCE.seconds

    /**
     * Сводка по записям журнала.
     *
     * Считается по записям `SHOWN` — по предъявлению уведомления человеку. Это не выбор
     * из двух равных: «сработало» определено моментом предъявления, и получение колбэка
     * без последующего показа срабатыванием не считается вовсе (`04-reminders.md` §1,
     * SKL-03 `TDD.md §4.1`). Счёт по `FIRED` отвечал бы на другой вопрос — доходил ли
     * будильник до приложения, — и обещанную долю завышал бы ровно на тихие отказы показа,
     * то есть на самый опасный класс отказов.
     *
     * Повторы (`REPEATED`) в счёт не идут: настойчивое напоминание измеряет отклонение
     * от того же планового времени и опаздывает по построению (НАП-25). Их учёт занижал бы
     * долю тем сильнее, чем настойчивее приложение напоминает.
     */
    fun summarize(entries: List<ReminderLogEntry>, days: Long = WINDOW.toDays()): ReminderOnTimeSummary {
        val shown = entries.filter { it.event == ReminderEvent.SHOWN }
        return ReminderOnTimeSummary(
            fired = shown.size,
            onTime = shown.count { isOnTime(it.deviationSeconds) },
            days = days,
        )
    }
}
