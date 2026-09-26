package ru.heuri.sklyanka.domain.medication

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.heuri.sklyanka.domain.model.IntakeStatus

/** Недельная полоса приёмов: счётчик, сегодняшний день и дни до начала курса (ЭКР-6). */
class WeekAdherenceTest {

    @Test
    fun `the strip always holds seven days ending with today`() {
        val strip = WeekAdherence.of(Cards.scheduled(), emptyList(), Cards.TODAY)

        assertEquals(DayCell.WEEK_LENGTH, strip.days.size)
        assertEquals(Cards.TODAY.minusDays(6), strip.days.first().date)
        assertEquals(Cards.TODAY, strip.days.last().date)
        assertTrue(strip.days.last() is DayCell.Today, "the last cell is today")
    }

    @Test
    fun `six finished days of two intakes and a partly marked today read twelve of fourteen`() {
        val strip = WeekAdherence.of(Cards.scheduled(), fullWeek(), Cards.TODAY)

        // Ровно счётчик макета «12 из 14 приёмов»: шесть завершившихся дней по два приёма
        // в числителе, план всей недели в знаменателе.
        assertEquals(12, strip.taken)
        assertEquals(14, strip.planned)
    }

    @Test
    fun `the intake taken today stays out of the counter`() {
        val withTodayTaken = WeekAdherence.of(Cards.scheduled(), fullWeek(), Cards.TODAY)
        val withTodayPending = WeekAdherence.of(
            Cards.scheduled(),
            finishedDays() + listOf(
                Cards.intake(at = Cards.TODAY.atTime(Cards.MORNING), status = IntakeStatus.PENDING),
                Cards.intake(at = Cards.TODAY.atTime(Cards.EVENING), status = IntakeStatus.PENDING),
            ),
            Cards.TODAY,
        )

        // Иначе каждое утро недельная сводка показывала бы провал, которого нет.
        assertEquals(withTodayPending.taken, withTodayTaken.taken)
        assertEquals(withTodayPending.planned, withTodayTaken.planned)
    }

    @Test
    fun `a finished day joins the counter once the day is over`() {
        val week = finishedDays() + Cards.day(Cards.TODAY, taken = 2)

        val onTheDay = WeekAdherence.of(Cards.scheduled(), week, Cards.TODAY)
        val nextMorning = WeekAdherence.of(Cards.scheduled(), week, Cards.TODAY.plusDays(1))

        // 25 сентября уходит из «сегодня» в завершившиеся сутки, и его приёмы попадают
        // в числитель — граница локальной полуночи (НФТ-37).
        assertEquals(12, onTheDay.taken)
        assertEquals(
            DayCell.Completed(Cards.TODAY, taken = 2, planned = 2),
            nextMorning.days.single { it.date == Cards.TODAY },
        )
        assertEquals(12, nextMorning.taken)
    }

    @Test
    fun `days before the course start are empty and out of the counter`() {
        val start = Cards.TODAY.minusDays(3)
        val strip = WeekAdherence.of(
            Cards.scheduled(startDate = start),
            Cards.day(start, taken = 2) +
                Cards.day(start.plusDays(1), taken = 2) +
                Cards.day(start.plusDays(2), taken = 2),
            Cards.TODAY,
        )

        val before = strip.days.filterIsInstance<DayCell.BeforeCourse>()
        assertEquals(3, before.size)
        assertEquals(6, strip.taken)
        assertEquals(6, strip.planned)
    }

    @Test
    fun `a day without a single mark counts as missed, not as absent`() {
        val date = Cards.TODAY.minusDays(1)
        val strip = WeekAdherence.of(Cards.scheduled(), Cards.day(date, taken = 0), Cards.TODAY)

        assertEquals(DayCell.Completed(date, taken = 0, planned = 2), strip.days.single { it.date == date })
        assertEquals(0, strip.taken)
        assertEquals(2, strip.planned)
    }

    @Test
    fun `a partly taken finished day keeps both numbers`() {
        val date = Cards.TODAY.minusDays(2)
        val strip = WeekAdherence.of(Cards.scheduled(), Cards.day(date, taken = 1), Cards.TODAY)

        assertEquals(DayCell.Completed(date, taken = 1, planned = 2), strip.days.single { it.date == date })
    }

    @Test
    fun `a medication taken as needed shows facts and never a miss`() {
        val strip = WeekAdherence.of(
            Cards.asNeeded(),
            listOf(
                Cards.intake(at = Cards.TODAY.minusDays(2).atTime(Cards.MORNING)),
                Cards.intake(at = Cards.TODAY.minusDays(1).atTime(Cards.EVENING)),
            ),
            Cards.TODAY,
        )

        val completed = strip.days.filterIsInstance<DayCell.Completed>()
        assertTrue(
            completed.all { it.taken == it.planned },
            "a medication taken as needed has no plan, so it cannot miss one",
        )
        assertEquals(2, strip.taken)
        assertEquals(2, strip.planned)
    }

    @Test
    fun `intakes of another medication never leak into the strip`() {
        val strip = WeekAdherence.of(
            Cards.scheduled(),
            Cards.day(Cards.TODAY.minusDays(1), taken = 2, medicationId = Cards.OTHER_MEDICATION_ID),
            Cards.TODAY,
        )

        assertEquals(0, strip.taken)
        assertEquals(0, strip.planned)
    }

    /** Шесть завершившихся дней по два принятых приёма и сегодняшний день с одним принятым. */
    private fun fullWeek() = finishedDays() +
        listOf(
            Cards.intake(at = Cards.TODAY.atTime(Cards.MORNING)),
            Cards.intake(at = Cards.TODAY.atTime(Cards.EVENING), status = IntakeStatus.PENDING),
        )

    private fun finishedDays() = (1..6).flatMap { back ->
        Cards.day(Cards.TODAY.minusDays(back.toLong()), taken = 2)
    }
}
