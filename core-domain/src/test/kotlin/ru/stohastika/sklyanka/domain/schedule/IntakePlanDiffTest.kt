package ru.stohastika.sklyanka.domain.schedule

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import ru.stohastika.sklyanka.domain.model.IntakeStatus
import ru.stohastika.sklyanka.domain.time.FixedTimeSource
import java.math.BigDecimal
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

    private val morningsOfTheWeek = Planning.window().dates().map { PlannedIntake(it.atTime(Planning.MORNING), null) }

    @Test
    fun `a repeated run over untouched data changes nothing`() {
        val existing = morningsOfTheWeek.map { Planning.pending(it.scheduledAt) }

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
        val existing = morningsOfTheWeek.dropLast(1).map { Planning.pending(it.scheduledAt) }

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

        val diff = differ.diff(listOf(PlannedIntake(taken.scheduledAt, null)), listOf(taken))

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
        val planned = Planning.window().dates().flatMap { date ->
            listOf(PlannedIntake(date.atTime(moved), null), PlannedIntake(date.atTime(Planning.EVENING), null))
        }

        val diff = differ.diff(planned, existing)

        assertEquals(Planning.DEPTH_DAYS, diff.toCreate.size)
        assertTrue(diff.toCreate.all { it.scheduledAt.toLocalTime() == moved })
        assertEquals(Planning.DEPTH_DAYS, diff.toDelete.size)
        assertTrue(diff.toDelete.all { it.startsWith("morning-") })
        // Вечерние записи остаются теми же самыми — вместе со своими будильниками (НАП-9).
        assertEquals(Planning.DEPTH_DAYS, diff.unchanged)
    }

    /**
     * Сравнение идёт по моменту и только по нему: доза дня — свойство плана, а не причина
     * пересоздавать запись. Иначе смена ступени переписывала бы уже отмеченные приёмы,
     * и история показывала бы не ту дозу, которую человек принял (ПР-19).
     *
     * Снимок дозы у **ожидающего** приёма при этом обновляется (SKL-117, F7): запись та же,
     * будильник тот же, меняется только доза. Отмеченный приём не трогается никогда —
     * на нём стоит списание остатка.
     */
    @Test
    fun `a dose of the plan refreshes the pending intake and never recreates the moment`() {
        val moment = Planning.TODAY.atTime(Planning.MORNING)
        val planned = listOf(PlannedIntake(moment, BigDecimal("0.5")))
        val existing = listOf(Planning.pending(moment))

        val diff = differ.diff(planned, existing)

        assertTrue(diff.toCreate.isEmpty())
        assertTrue(diff.toDelete.isEmpty())
        assertEquals(1, diff.unchanged)
        assertEquals(existing.single().id, diff.toRefreshDose.single().intakeId)
        assertEquals(0, BigDecimal("0.5").compareTo(requireNotNull(diff.toRefreshDose.single().doseAmount)))
    }

    /** Повторный запуск на уже обновлённом снимке не трогает ничего (НАП-9). */
    @Test
    fun `a repeated run over the refreshed dose changes nothing`() {
        val moment = Planning.TODAY.atTime(Planning.MORNING)
        val planned = listOf(PlannedIntake(moment, BigDecimal("0.5")))
        val existing = listOf(Planning.pending(moment).copy(doseAmount = BigDecimal("0.5")))

        val diff = differ.diff(planned, existing)

        assertTrue(diff.isEmpty)
        assertTrue(diff.toRefreshDose.isEmpty())
    }

    /** План без дозы у ступенчатого курса сняли: снимок очищается, доза снова у лекарства. */
    @Test
    fun `a plan without a dose clears the snapshot of the pending intake`() {
        val moment = Planning.TODAY.atTime(Planning.MORNING)
        val existing = listOf(Planning.pending(moment).copy(doseAmount = BigDecimal("0.5")))

        val diff = differ.diff(listOf(PlannedIntake(moment, null)), existing)

        assertEquals(1, diff.toRefreshDose.size)
        assertNull(diff.toRefreshDose.single().doseAmount)
    }

    /** Отмеченный приём сохраняет дозу, которой принят: списание уже прошло по ней (ПР-11). */
    @Test
    fun `a marked intake keeps the dose it was taken with`() {
        val moment = Planning.TODAY.atTime(Planning.MORNING)
        val taken = Planning.taken(moment).copy(doseAmount = BigDecimal("1"))

        val diff = differ.diff(listOf(PlannedIntake(moment, BigDecimal("0.5"))), listOf(taken))

        assertTrue(diff.toRefreshDose.isEmpty())
        assertTrue(diff.toCreate.isEmpty())
    }

    /** Создаваемый приём несёт дозу дня вместе с моментом: снимок рождается здесь. */
    @Test
    fun `a created intake carries the dose of its day`() {
        val half = BigDecimal("0.5")

        val diff = differ.diff(listOf(PlannedIntake(Planning.TODAY.atTime(Planning.MORNING), half)), emptyList())

        assertEquals(listOf(half), diff.toCreate.map { it.doseAmount })
    }

    @Test
    fun `shortening the course removes only what fell out of it`() {
        val existing = morningsOfTheWeek.map { Planning.pending(it.scheduledAt, id = "at-${it.scheduledAt}") }
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
