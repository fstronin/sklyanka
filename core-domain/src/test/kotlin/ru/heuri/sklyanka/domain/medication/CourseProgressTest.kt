package ru.heuri.sklyanka.domain.medication

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

/** Ход курса: остаток дней, доля пройденного и бессрочный курс (ПР-6, ЭКР-6). */
class CourseProgressTest {

    @Test
    fun `a bounded course reports the days left after today`() {
        val progress = CourseProgress.of(Cards.scheduled(), Cards.TODAY) as CourseProgress.Bounded

        // «осталось 17 дней» из макета MedCard.dc.html: 25 сентября уже идёт и в остаток
        // не входит — то же правило, что в недельной полосе (ЭКР-6).
        assertEquals(17, progress.daysLeft)
        assertEquals(Cards.COURSE_START, progress.start)
        assertEquals(Cards.COURSE_END, progress.end)
    }

    @Test
    fun `the fill of a bounded course counts finished days only`() {
        val progress = CourseProgress.of(Cards.scheduled(), Cards.TODAY) as CourseProgress.Bounded

        // Курс 12 сентября — 12 октября: 31 день, из них к 25 сентября завершились 13.
        assertEquals(13f / 31f, progress.fraction, 0.001f)
    }

    @Test
    fun `the last day of the course leaves no full day behind`() {
        val progress = CourseProgress.of(
            Cards.scheduled(endDate = Cards.TODAY),
            Cards.TODAY,
        ) as CourseProgress.Bounded

        assertEquals(0, progress.daysLeft)
        assertTrue(progress.isLastDay, "today is the last day of the course")
        assertTrue(!progress.isOver, "the course is not over while its last day runs")
    }

    @Test
    fun `a one day course is its own last day`() {
        val progress = CourseProgress.of(
            Cards.scheduled(startDate = Cards.TODAY, endDate = Cards.TODAY),
            Cards.TODAY,
        ) as CourseProgress.Bounded

        assertEquals(0, progress.daysLeft)
        assertEquals(0f, progress.fraction, 0f)
        assertTrue(progress.isLastDay)
    }

    @Test
    fun `a course whose end has passed is over`() {
        val progress = CourseProgress.of(
            Cards.scheduled(endDate = Cards.TODAY.minusDays(3)),
            Cards.TODAY,
        ) as CourseProgress.Bounded

        assertEquals(0, progress.daysLeft)
        assertEquals(1f, progress.fraction, 0f)
        assertTrue(progress.isOver)
    }

    @Test
    fun `an open course has neither a bar nor days left`() {
        val progress = CourseProgress.of(Cards.scheduled(endDate = null), Cards.TODAY)

        assertEquals(CourseProgress.Open(Cards.COURSE_START), progress)
    }

    @Test
    fun `a medication taken as needed has no course at all`() {
        assertNull(CourseProgress.of(Cards.asNeeded(), Cards.TODAY))
    }

    @Test
    fun `the course crossing a daylight saving switch keeps its calendar length`() {
        // Переход на летнее время в Европе — последнее воскресенье марта; курс пересекает его.
        val progress = CourseProgress.of(
            Cards.scheduled(
                startDate = LocalDate.of(2026, 3, 20),
                endDate = LocalDate.of(2026, 4, 3),
            ),
            LocalDate.of(2026, 3, 30),
        ) as CourseProgress.Bounded

        assertEquals(4, progress.daysLeft)
        assertEquals(10f / 15f, progress.fraction, 0.001f)
    }
}
