package ru.heuri.sklyanka.domain.history

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import ru.heuri.sklyanka.domain.model.Intake
import ru.heuri.sklyanka.domain.model.IntakeStatus
import java.time.LocalDate

/** Состояние дня в календаре месяца: все шесть исходов и границы, на которых их путают. */
class DayOutcomeRuleTest {

    @Test
    fun `a day with every intake taken reads as all taken`() {
        assertEquals(DayState.ALL_TAKEN, state(day(taken = 2, missed = 0)))
    }

    @Test
    fun `a day with some intakes taken and some missed reads as partial`() {
        assertEquals(DayState.PARTIAL, state(day(taken = 1, missed = 1)))
    }

    @Test
    fun `a day without a single taken intake reads as a miss`() {
        assertEquals(DayState.MISSED, state(day(taken = 0, missed = 2)))
    }

    @Test
    fun `a day the schedule never touched reads as a day without intakes`() {
        assertEquals(DayState.NO_INTAKES, state(emptyList()))
    }

    /**
     * Дни до начала ведения истории нейтральны и пропусками не считаются.
     *
     * Проверка отдельная, потому что снаружи такой день выглядит как день без приёмов,
     * а в сводке ведёт себя иначе: он не участвует ни в числителе, ни в знаменателе
     * (`docs/spec/03-screens.md`, состояние «Истории меньше месяца»).
     */
    @Test
    fun `a day before the history began is not a miss`() {
        val date = LocalDate.of(2026, 9, 3)

        val outcome = DayOutcomeRule.state(
            intakesOfDay = emptyList(),
            date = date,
            today = Months.TODAY,
            historyStartsAt = LocalDate.of(2026, 9, 12),
        )

        assertEquals(DayState.BEFORE_HISTORY, outcome)
    }

    @Test
    fun `a profile without a single record has no day with an outcome`() {
        val outcome = DayOutcomeRule.state(
            intakesOfDay = emptyList(),
            date = Months.TODAY,
            today = Months.TODAY,
            historyStartsAt = null,
        )

        assertEquals(DayState.BEFORE_HISTORY, outcome)
    }

    /**
     * ЭКР-6: сутки, которые ещё не кончились, исхода не имеют.
     *
     * Иначе каждое утро календарь показывал бы пропуск там, где приём человеку только
     * предстоит, — и та же ошибка появилась бы в плитке «дней без пропусков».
     */
    @Test
    fun `today and the days ahead carry no outcome yet`() {
        val pendingToday = listOf(Months.intake(Months.TODAY.atTime(Months.EVENING), IntakeStatus.PENDING))

        assertEquals(DayState.AHEAD, state(pendingToday, date = Months.TODAY))
        assertEquals(
            DayState.AHEAD,
            DayOutcomeRule.state(
                intakesOfDay = listOf(
                    Months.intake(Months.TODAY.plusDays(1).atTime(Months.MORNING), IntakeStatus.PENDING),
                ),
                date = Months.TODAY.plusDays(1),
                today = Months.TODAY,
                historyStartsAt = LocalDate.of(2026, 9, 1),
            ),
        )
    }

    /**
     * Пропуск, отмеченный человеком, и пропуск автоматический в календаре не различаются.
     *
     * Для картины врача приём не состоялся в обоих случаях; различие сохраняется
     * в отчёте, а не в цвете клетки (`TDD.md §5.3`).
     */
    @Test
    fun `a day skipped by the person looks the same as a day missed automatically`() {
        val date = LocalDate.of(2026, 9, 15)
        val skipped = listOf(Months.intake(date.atTime(Months.MORNING), IntakeStatus.SKIPPED))
        val missed = listOf(Months.intake(date.atTime(Months.MORNING), IntakeStatus.MISSED))

        assertEquals(state(skipped, date), state(missed, date))
        assertEquals(DayState.MISSED, state(skipped, date))
    }

    private fun day(taken: Int, missed: Int, date: LocalDate = LocalDate.of(2026, 9, 15)) =
        List(taken) { Months.intake(date.atTime(Months.MORNING), IntakeStatus.TAKEN) } +
            List(missed) { Months.intake(date.atTime(Months.EVENING), IntakeStatus.MISSED) }

    private fun state(intakesOfDay: List<Intake>, date: LocalDate = LocalDate.of(2026, 9, 15)) = DayOutcomeRule.state(
        intakesOfDay = intakesOfDay,
        date = date,
        today = Months.TODAY,
        historyStartsAt = LocalDate.of(2026, 9, 1),
    )
}
