package ru.stohastika.sklyanka.domain.intake

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.stohastika.sklyanka.domain.model.Fixtures
import ru.stohastika.sklyanka.domain.model.IntakeStatus

/**
 * Общие предикаты исходов отметки (SKL-30).
 *
 * `isCompleted` решает, открывается ли окно отмены: его используют обе точки публикации
 * — «Сегодня» и карточка напоминания, — и расходиться между ними он не имеет права.
 */
class MarkOutcomeTest {

    @Test
    fun `a written mark counts as a completed action`() {
        assertTrue(MarkOutcome.Applied(Fixtures.takenIntake()).isCompleted)
        assertTrue(MarkOutcome.Superseded(Fixtures.takenIntake(), Fixtures.takenIntake()).isCompleted)
    }

    @Test
    fun `a dropped or missing mark is not an action`() {
        assertFalse(
            MarkOutcome.Ignored(Fixtures.takenIntake(), IgnoreReason.ALREADY_MARKED).isCompleted,
        )
        assertFalse(MarkOutcome.NotFound.isCompleted)
    }

    @Test
    fun `a recorded taken or skipped status maps back to its result`() {
        assertEquals(MarkResult.TAKEN, MarkResult.of(IntakeStatus.TAKEN))
        assertEquals(MarkResult.SKIPPED, MarkResult.of(IntakeStatus.SKIPPED))
    }

    @Test
    fun `a status no button can choose has no result`() {
        assertNull(MarkResult.of(IntakeStatus.PENDING))
        assertNull(MarkResult.of(IntakeStatus.MISSED))
    }
}
