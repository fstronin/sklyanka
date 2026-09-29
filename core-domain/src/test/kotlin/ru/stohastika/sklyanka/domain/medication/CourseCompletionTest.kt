package ru.stohastika.sklyanka.domain.medication

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.stohastika.sklyanka.domain.model.ArchiveReason

/** Предложение убрать законченный курс в архив (ОВ-42). */
class CourseCompletionTest {

    @Test
    fun `a course whose end has passed is offered for the archive`() {
        assertTrue(
            CourseCompletion.isFinished(Cards.scheduled(endDate = Cards.TODAY.minusDays(1)), Cards.TODAY),
        )
    }

    @Test
    fun `the last day of the course is not yet finished`() {
        // ИНВ-15: последний день входит в курс целиком, и приёмы на него остаются.
        assertFalse(CourseCompletion.isFinished(Cards.scheduled(endDate = Cards.TODAY), Cards.TODAY))
    }

    @Test
    fun `an open course is never finished`() {
        assertFalse(CourseCompletion.isFinished(Cards.scheduled(endDate = null), Cards.TODAY))
    }

    @Test
    fun `a medication taken as needed is never finished`() {
        assertFalse(CourseCompletion.isFinished(Cards.asNeeded(), Cards.TODAY))
    }

    @Test
    fun `a course already in the archive is not offered again`() {
        val archived = Cards.scheduled(
            endDate = Cards.TODAY.minusDays(1),
            archivedAt = Cards.instant(Cards.TODAY.atTime(Cards.MORNING)),
            archiveReason = ArchiveReason.COURSE_FINISHED,
        )

        assertFalse(CourseCompletion.isFinished(archived, Cards.TODAY))
    }
}
