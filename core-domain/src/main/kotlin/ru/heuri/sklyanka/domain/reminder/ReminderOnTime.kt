package ru.heuri.sklyanka.domain.reminder

import java.time.Duration

/**
 * Сводка «сколько напоминаний сработало вовремя за последние 7 дней» (НФТ-51).
 *
 * Строка, а не проценты из головы: [fired] — сколько срабатываний было, [onTime] — сколько
 * из них уложилось в НАП-2. Доля считается только когда считать есть что: «0 %» при
 * отсутствии срабатываний — ложь ровно того сорта, за которую человек перестаёт верить
 * диагностике, а «нет данных» — правда, отличающая «не работает» от «ещё не пробовало».
 *
 * Пробные напоминания (`ReminderEvent.TEST`) в сводку не входят: они не относятся
 * ни к одному приёму, и их отклонение измеряет нажатие кнопки, а не надёжность плана.
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
 * Правило «сработало вовремя» (НАП-2) и глубина сводки (НФТ-51).
 *
 * Отдельно от [ru.heuri.sklyanka.domain.history.OnTimeRule] намеренно: там мерой служит
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
     * Считается по записям `FIRED`: это момент, когда система отдала управление
     * приложению, и именно он измеряет надёжность доставки. Записи `SHOWN` несут то же
     * отклонение, и счёт по обоим видам удваивал бы каждое срабатывание.
     */
    fun summarize(entries: List<ReminderLogEntry>, days: Long = WINDOW.toDays()): ReminderOnTimeSummary {
        val fired = entries.filter { it.event == ReminderEvent.FIRED }
        return ReminderOnTimeSummary(
            fired = fired.size,
            onTime = fired.count { isOnTime(it.deviationSeconds) },
            days = days,
        )
    }
}
