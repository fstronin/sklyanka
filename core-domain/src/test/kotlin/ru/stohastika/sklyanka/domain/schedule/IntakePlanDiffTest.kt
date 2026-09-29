package ru.stohastika.sklyanka.domain.schedule

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import ru.stohastika.sklyanka.domain.model.IntakeStatus
import ru.stohastika.sklyanka.domain.time.FixedTimeSource
import java.time.LocalTime

/**
 * Сравнение плана с уже существующими записями.
 *
 * Здесь проверяется главное обещание задачи человеку: правка расписания не стоит ему
 * ни одной отметки и ни одного напоминания, которое можно было не трогать.
 */
@DisplayName("The plan is compared with the existing intakes, not imposed over them")
class IntakePlanDiffTest {

    private val clock: FixedTimeSource = Planning.clock()
    private val differ = IntakePlanDiffer(clock)

    private val morningsOfTheWeek = Planning.window().dates().map { it.atTime(Planning.MORNING) }

    @Test
    fun `a repeated run over untouched data changes nothing`() {
        val existing = morningsOfTheWeek.map { Planning.pending(it) }

        val diff = differ.diff(morningsOfTheWeek, existing)

        assertTrue(diff.isEmpty)
        assertEquals(Planning.DEPTH_DAYS, diff.unchanged)
    }

    @Test
    fun `an empty store means every planned moment is created`() {
        val diff = differ.diff(morningsOfTheWeek, emptyList())

        assertEquals(morningsOfTheWeek, diff.toCreate)
        assertTrue(diff.toDelete.isEmpty())
        assertEquals(0, diff.unchanged)
    }

    @Test
    fun `the tail of the window is filled without touching what is already there`() {
        val existing = morningsOfTheWeek.dropLast(1).map { Planning.pending(it) }

        val diff = differ.diff(morningsOfTheWeek, existing)

        assertEquals(listOf(morningsOfTheWeek.last()), diff.toCreate)
        assertTrue(diff.toDelete.isEmpty())
        assertEquals(Planning.DEPTH_DAYS - 1, diff.unchanged)
    }

    // --- Неприкосновенность отметок ------------------------------------------------------

    @Test
    fun `a marked intake is never deleted even when the schedule no longer holds it`() {
        val marked = Planning.taken(Planning.TODAY.plusDays(2).atTime(Planning.EVENING))

        val diff = differ.diff(morningsOfTheWeek, listOf(marked))

        assertTrue(diff.toDelete.isEmpty())
        assertEquals(morningsOfTheWeek.size, diff.toCreate.size)
    }

    @Test
    fun `a skipped or missed intake is not deleted either`() {
        val skipped = Planning.taken(Planning.TODAY.plusDays(1).atTime(Planning.EVENING), id = "skipped")
            .copy(status = IntakeStatus.SKIPPED)
        val missed = Planning.taken(Planning.TODAY.plusDays(1).atTime(Planning.NOON), id = "missed")
            .copy(status = IntakeStatus.MISSED)

        val diff = differ.diff(emptyList(), listOf(skipped, missed))

        assertTrue(diff.toDelete.isEmpty())
    }

    @Test
    fun `a moment already held by a marked intake is not created a second time`() {
        val taken = Planning.taken(Planning.TODAY.atTime(Planning.MORNING))

        val diff = differ.diff(listOf(taken.scheduledAt), listOf(taken))

        assertTrue(diff.toCreate.isEmpty())
        assertEquals(1, diff.unchanged)
    }

    // --- Лишние ожидающиеся приёмы -------------------------------------------------------

    @Test
    fun `a future pending intake outside the plan is removed`() {
        val stray = Planning.pending(Planning.TODAY.plusDays(1).atTime(LocalTime.of(21, 0)))

        val diff = differ.diff(morningsOfTheWeek, listOf(stray))

        assertEquals(listOf(stray.id), diff.toDelete)
    }

    /**
     * Прошедший ожидающийся приём — не мусор плана, а история: SKL-07 переведёт его
     * в MISSED. Удалить его значит переписать прошлое задним числом.
     */
    @Test
    fun `a past pending intake outside the plan survives the comparison`() {
        val past = Planning.pending(Planning.TODAY.atTime(LocalTime.of(6, 0)))

        val diff = differ.diff(morningsOfTheWeek, listOf(past))

        assertTrue(diff.toDelete.isEmpty())
    }

    @Test
    fun `an intake at this very moment is not swept away`() {
        val now = Planning.pending(clock.nowLocal())

        val diff = differ.diff(emptyList(), listOf(now))

        assertTrue(diff.toDelete.isEmpty())
    }

    // --- Перенос времени приёма -----------------------------------------------------------

    @Test
    fun `moving the morning intake touches only the morning records`() {
        val moved = LocalTime.of(9, 0)
        val existing = Planning.window().dates().flatMap { date ->
            listOf(
                Planning.pending(date.atTime(Planning.MORNING), id = "morning-$date"),
                Planning.pending(date.atTime(Planning.EVENING), id = "evening-$date"),
            )
        }
        val planned = Planning.window().dates().flatMap { listOf(it.atTime(moved), it.atTime(Planning.EVENING)) }

        val diff = differ.diff(planned, existing)

        assertEquals(Planning.DEPTH_DAYS, diff.toCreate.size)
        assertTrue(diff.toCreate.all { it.toLocalTime() == moved })
        assertEquals(Planning.DEPTH_DAYS, diff.toDelete.size)
        assertTrue(diff.toDelete.all { it.startsWith("morning-") })
        // Вечерние записи остаются теми же самыми — вместе со своими будильниками (НАП-9).
        assertEquals(Planning.DEPTH_DAYS, diff.unchanged)
    }

    @Test
    fun `shortening the course removes only what fell out of it`() {
        val existing = morningsOfTheWeek.map { Planning.pending(it, id = "at-$it") }
        val shortened = morningsOfTheWeek.take(3)

        val diff = differ.diff(shortened, existing)

        assertTrue(diff.toCreate.isEmpty())
        assertEquals(Planning.DEPTH_DAYS - 3, diff.toDelete.size)
        assertEquals(3, diff.unchanged)
    }

    @Test
    fun `an empty plan sweeps the future pending intakes and keeps the marked ones`() {
        val marked = Planning.taken(Planning.TODAY.plusDays(1).atTime(Planning.MORNING), id = "taken")
        val pending = Planning.pending(Planning.TODAY.plusDays(2).atTime(Planning.MORNING), id = "pending")

        val diff = differ.diff(emptyList(), listOf(marked, pending))

        assertEquals(listOf("pending"), diff.toDelete)
        assertTrue(diff.toCreate.isEmpty())
    }
}
