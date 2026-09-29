package ru.stohastika.sklyanka.domain.intake

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import ru.stohastika.sklyanka.domain.time.FixedTimeSource
import java.time.Instant

/**
 * Носитель «последнего действия» (SKL-30): одна ячейка, срок 5 секунд, замена.
 *
 * Время управляемое: окно измеряется моментами UTC, и без подменяемых часов ни его
 * граница, ни замена не проверяемы (НФТ-48).
 */
class UndoableMarkStoreTest {

    private val start: Instant = Instant.parse("2026-09-25T11:05:00Z")
    private val clock = FixedTimeSource(start)
    private val store = UndoableMarkStore(clock)

    @Test
    fun `a published mark is live for five seconds and gone at the fifth`() {
        store.publish(listOf("i-1"), MarkResult.TAKEN, listOf("Magnesium B6"))
        assertNotNull(store.live())

        clock.advanceTo(start.plusMillis(4_999))
        assertNotNull(store.live(), "the window must still be open at 4,999 ms")

        clock.advanceTo(start.plusMillis(5_000))
        assertNull(store.live(), "the window must be closed at 5,000 ms")
        assertNotNull(store.current.value, "the expired cell is cleared by the screen, not by time alone")
    }

    @Test
    fun `a second action replaces the first and restarts the window`() {
        store.publish(listOf("i-1"), MarkResult.TAKEN, listOf("Magnesium B6"))
        clock.advanceTo(start.plusSeconds(3))
        store.publish(listOf("i-2"), MarkResult.SKIPPED, listOf("Eutirox"))

        val live = requireNotNull(store.live())
        assertEquals(listOf("i-2"), live.intakeIds)
        assertEquals(MarkResult.SKIPPED, live.result)

        clock.advanceTo(start.plusMillis(7_999))
        assertNotNull(store.live(), "the window of the second action runs from its own moment")

        clock.advanceTo(start.plusMillis(8_000))
        assertNull(store.live())
    }

    @Test
    fun `the expiry of an old mark does not clear its replacement`() {
        store.publish(listOf("i-1"), MarkResult.TAKEN, listOf("Magnesium B6"))
        val first = requireNotNull(store.live())
        clock.advanceTo(start.plusSeconds(3))
        store.publish(listOf("i-2"), MarkResult.SKIPPED, listOf("Eutirox"))
        val second = requireNotNull(store.live())

        store.clearIf(first)

        assertEquals(second, store.live(), "the replacement must survive the timer of the old action")
    }

    @Test
    fun `clear closes the window and publishing after it opens a new one`() {
        store.publish(listOf("i-1"), MarkResult.TAKEN, listOf("Magnesium B6"))

        store.clear()
        assertNull(store.current.value)
        assertNull(store.live())

        store.publish(listOf("i-2"), MarkResult.TAKEN, listOf("Magnesium B6"))
        assertNotNull(store.live())
    }
}
