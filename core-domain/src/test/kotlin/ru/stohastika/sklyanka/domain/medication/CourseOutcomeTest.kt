package ru.stohastika.sklyanka.domain.medication

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import ru.stohastika.sklyanka.domain.model.ArchiveReason
import ru.stohastika.sklyanka.domain.model.Medication
import java.time.LocalDate

/** Итог завершённого курса: «7 дней · принято 13 из 14» (ЭКР-8, ОВ-44). */
class CourseOutcomeTest {

    @Test
    fun `a course that reached its end date is as long as its period`() {
        val outcome = CourseOutcome.of(
            course(start = AUGUST_15, end = AUGUST_21, archivedOn = AUGUST_22),
            week(taken = 14),
            Cards.ZONE,
        )

        assertEquals(ArchiveCaption.Outcome(days = 7, taken = 14, planned = 14), outcome)
    }

    @Test
    fun `a course interrupted early is as long as it actually ran`() {
        val outcome = CourseOutcome.of(
            course(start = AUGUST_15, end = AUGUST_15.plusDays(13), archivedOn = AUGUST_21),
            week(taken = 14),
            Cards.ZONE,
        )

        // Обещать «14 дней» курсу, прерванному на седьмой, нельзя.
        assertEquals(7, outcome.days)
    }

    @Test
    fun `the default denominator counts only the intakes that came due before archiving`() {
        val intakes = week(taken = 14) + Cards.day(AUGUST_22, taken = 0)

        val outcome = CourseOutcome.of(
            course(start = AUGUST_15, end = AUGUST_21, archivedOn = AUGUST_21),
            intakes,
            Cards.ZONE,
        )

        // Приёмы, до которых курс не дожил, в знаменатель не идут: «принято 14 из 16»
        // сообщило бы провал, которого не было (ОВ-44).
        assertEquals(14, outcome.planned)
        assertEquals(14, outcome.taken)
    }

    @Test
    fun `the whole course denominator counts every record of the course`() {
        val intakes = week(taken = 14) + Cards.day(AUGUST_22, taken = 0)

        val outcome = CourseOutcome.of(
            course(start = AUGUST_15, end = AUGUST_21, archivedOn = AUGUST_21),
            intakes,
            Cards.ZONE,
            OutcomeDenominator.WHOLE_COURSE,
        )

        assertEquals(16, outcome.planned)
    }

    @Test
    fun `an open course is as long as the time until it was archived`() {
        val outcome = CourseOutcome.of(
            course(start = AUGUST_15, end = null, archivedOn = AUGUST_21),
            week(taken = 10),
            Cards.ZONE,
        )

        assertEquals(7, outcome.days)
        assertEquals(10, outcome.taken)
    }

    @Test
    fun `a course crossing a daylight saving switch keeps its calendar length`() {
        val start = LocalDate.of(2026, 3, 25)
        val outcome = CourseOutcome.of(
            course(start = start, end = start.plusDays(13), archivedOn = start.plusDays(13)),
            emptyList(),
            Cards.ZONE,
        )

        // Календарные даты, а не вычитание меток времени: иначе курс теряет либо
        // приобретает день (НФТ-37).
        assertEquals(14, outcome.days)
    }

    @Test
    fun `a one day course is one day long`() {
        val outcome = CourseOutcome.of(
            course(start = AUGUST_15, end = AUGUST_15, archivedOn = AUGUST_15),
            Cards.day(AUGUST_15, taken = 2),
            Cards.ZONE,
        )

        assertEquals(1, outcome.days)
    }

    @Test
    fun `a record whose archiving precedes its start never reports a negative length`() {
        val outcome = CourseOutcome.of(
            course(start = AUGUST_21, end = AUGUST_21.plusDays(6), archivedOn = AUGUST_15),
            emptyList(),
            Cards.ZONE,
        )

        assertEquals(1, outcome.days)
    }

    private fun course(start: LocalDate, end: LocalDate?, archivedOn: LocalDate): Medication = Cards.scheduled(
        startDate = start,
        endDate = end,
        archivedAt = Cards.instant(archivedOn.atTime(Cards.EVENING)),
        archiveReason = ArchiveReason.COURSE_FINISHED,
    )

    /** Семь дней по два приёма, из которых принято [taken] первых. */
    private fun week(taken: Int) = (0..6).flatMap { day ->
        val before = day * 2
        Cards.day(AUGUST_15.plusDays(day.toLong()), taken = (taken - before).coerceIn(0, 2))
    }

    private companion object {
        val AUGUST_15: LocalDate = LocalDate.of(2026, 8, 15)
        val AUGUST_21: LocalDate = LocalDate.of(2026, 8, 21)
        val AUGUST_22: LocalDate = LocalDate.of(2026, 8, 22)
    }
}
