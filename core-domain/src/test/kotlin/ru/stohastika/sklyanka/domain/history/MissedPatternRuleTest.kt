package ru.stohastika.sklyanka.domain.history

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.LocalTime

/**
 * Строка-вывод о самом пропускаемом приёме.
 *
 * Единственное место продукта, где приложение обобщает за человека, поэтому проверок
 * на «вывода нет» здесь больше, чем на «вывод есть»: ложный вывод хуже отсутствующего.
 */
class MissedPatternRuleTest {

    private val rule = MissedPatternRule()

    /** Сценарий приёмки: из 12 пропусков 9 приходятся на приём в 20:00. */
    @Test
    fun `a clear leader is named`() {
        val leader = rule.dominantMissedTime(
            missesByTime = mapOf(Months.EVENING to 9, Months.MORNING to 3),
            scheduledTimes = BOTH_TIMES,
        )

        assertEquals(Months.EVENING, leader)
    }

    /** Сценарий приёмки: пропуски распределены поровну — вывода нет. */
    @Test
    fun `an even split names nobody`() {
        assertNull(
            rule.dominantMissedTime(
                missesByTime = mapOf(Months.EVENING to 6, Months.MORNING to 6),
                scheduledTimes = BOTH_TIMES,
            ),
        )
    }

    /** Сценарий приёмки: один пропуск за месяц закономерностью не является. */
    @Test
    fun `a single miss in the month is not a pattern`() {
        assertNull(rule.dominantMissedTime(mapOf(Months.EVENING to 1), BOTH_TIMES))
    }

    @Test
    fun `a slight difference is not a leader either`() {
        assertNull(rule.dominantMissedTime(mapOf(Months.EVENING to 7, Months.MORNING to 5), BOTH_TIMES))
    }

    /**
     * Приём в сутках один — сравнивать не с чем.
     *
     * «Чаще всего пропускается приём в 08:00» при единственном времени приёма сообщает
     * человеку только то, что других приёмов у него нет.
     */
    @Test
    fun `one time of day in the whole period tells nothing`() {
        assertNull(rule.dominantMissedTime(mapOf(Months.MORNING to 9), setOf(Months.MORNING)))
    }

    @Test
    fun `a month without misses has nothing to say`() {
        assertNull(rule.dominantMissedTime(emptyMap(), BOTH_TIMES))
    }

    /** Три времени: лидер обязан собрать долю, а не просто быть первым. */
    @Test
    fun `being first among three is not enough`() {
        val misses = mapOf(Months.MORNING to 5, LocalTime.of(14, 0) to 4, Months.EVENING to 4)

        assertNull(rule.dominantMissedTime(misses, misses.keys))
    }

    @Test
    fun `the threshold is a parameter and not a constant in the code`() {
        val lenient = MissedPatternRule(dominantShare = 0.4, minimumMisses = 2)

        assertEquals(
            Months.EVENING,
            lenient.dominantMissedTime(mapOf(Months.EVENING to 7, Months.MORNING to 5), BOTH_TIMES),
        )
    }

    /** Тот же вывод по готовой сводке периода: второго расчёта у экрана нет. */
    @Test
    fun `the same answer is given for a calculated period`() {
        val period = AdherencePeriod.empty(Months.PROFILE_ID, Months.WINDOW.from, Months.WINDOW.to).copy(
            missesByTime = mapOf(Months.EVENING to 9, Months.MORNING to 3),
            scheduledTimes = BOTH_TIMES,
        )

        assertEquals(Months.EVENING, rule.dominantMissedTime(period))
    }

    private companion object {
        val BOTH_TIMES: Set<LocalTime> = setOf(Months.MORNING, Months.EVENING)
    }
}
