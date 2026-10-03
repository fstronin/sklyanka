package ru.stohastika.sklyanka.domain.undo

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.stohastika.sklyanka.domain.intake.MarkResult
import ru.stohastika.sklyanka.domain.time.FixedTimeSource
import java.time.Instant

/**
 * Носитель «последнего действия» (SKL-30/99): одна ячейка на отметку и удаление,
 * срок 5 секунд, замена в обе стороны.
 *
 * Время управляемое: окно измеряется моментами UTC, и без подменяемых часов ни его
 * граница, ни замена не проверяемы (НФТ-48).
 */
class UndoableActionStoreTest {

    private val start: Instant = Instant.parse("2026-09-25T11:05:00Z")
    private val clock = FixedTimeSource(start)
    private val store = UndoableActionStore(clock)

    @Test
    fun `a published mark is live for five seconds and gone at the fifth`() {
        store.publishMark(listOf("i-1"), MarkResult.TAKEN, listOf("Magnesium B6"))
        assertNotNull(store.live())

        clock.advanceTo(start.plusMillis(4_999))
        assertNotNull(store.live(), "the window must still be open at 4,999 ms")

        clock.advanceTo(start.plusMillis(5_000))
        assertNull(store.live(), "the window must be closed at 5,000 ms")
        assertNotNull(store.current.value, "the expired cell is cleared by the screen, not by time alone")
    }

    @Test
    fun `a second action replaces the first and restarts the window`() {
        store.publishMark(listOf("i-1"), MarkResult.TAKEN, listOf("Magnesium B6"))
        clock.advanceTo(start.plusSeconds(3))
        store.publishMark(listOf("i-2"), MarkResult.SKIPPED, listOf("Eutirox"))

        val live = requireNotNull(store.live()) as UndoableMark
        assertEquals(listOf("i-2"), live.intakeIds)
        assertEquals(MarkResult.SKIPPED, live.result)

        clock.advanceTo(start.plusMillis(7_999))
        assertNotNull(store.live(), "the window of the second action runs from its own moment")

        clock.advanceTo(start.plusMillis(8_000))
        assertNull(store.live())
    }

    @Test
    fun `the expiry of an old mark does not clear its replacement`() {
        store.publishMark(listOf("i-1"), MarkResult.TAKEN, listOf("Magnesium B6"))
        val first = requireNotNull(store.live())
        clock.advanceTo(start.plusSeconds(3))
        store.publishMark(listOf("i-2"), MarkResult.SKIPPED, listOf("Eutirox"))
        val second = requireNotNull(store.live())

        store.clearIf(first)

        assertEquals(second, store.live(), "the replacement must survive the timer of the old action")
    }

    @Test
    fun `clear closes the window and publishing after it opens a new one`() {
        store.publishMark(listOf("i-1"), MarkResult.TAKEN, listOf("Magnesium B6"))

        store.clear()
        assertNull(store.current.value)
        assertNull(store.live())

        store.publishMark(listOf("i-2"), MarkResult.TAKEN, listOf("Magnesium B6"))
        assertNotNull(store.live())
    }

    @Test
    fun `a deletion replaces a mark and the other way round`() {
        // ОВ-124: одна ячейка «последнее действие» — сообщения не выстраиваются в очередь.
        store.publishMark(listOf("i-1"), MarkResult.TAKEN, listOf("Magnesium B6"))
        clock.advanceTo(start.plusSeconds(1))
        store.publishDeletion(medicationId = "med-1", medicationName = "Magnesium B6")

        val deletion = requireNotNull(store.live()) as UndoableDeletion
        assertEquals("med-1", deletion.medicationId)
        assertEquals("Magnesium B6", deletion.medicationName)
        assertNull(store.current.value as? UndoableMark, "the mark must be gone with the replacement")

        clock.advanceTo(start.plusSeconds(2))
        store.publishMark(listOf("i-2"), MarkResult.SKIPPED, listOf("Eutirox"))
        assertTrue(store.live() is UndoableMark, "a new mark must replace the deletion window")
    }

    @Test
    fun `the expiry of a deletion does not clear its replacement`() {
        store.publishDeletion(medicationId = "med-1", medicationName = "Magnesium B6")
        val deletion = requireNotNull(store.live())
        clock.advanceTo(start.plusSeconds(3))
        store.publishMark(listOf("i-1"), MarkResult.TAKEN, listOf("Eutirox"))

        store.clearIf(deletion)

        assertTrue(store.live() is UndoableMark, "the mark must survive the timer of the deletion")
    }
}
