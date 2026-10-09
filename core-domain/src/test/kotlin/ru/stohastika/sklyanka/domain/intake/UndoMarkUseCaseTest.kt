package ru.stohastika.sklyanka.domain.intake

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import ru.stohastika.sklyanka.domain.log.OpEvent
import ru.stohastika.sklyanka.domain.model.Fixtures
import ru.stohastika.sklyanka.domain.model.IntakeSource
import ru.stohastika.sklyanka.domain.model.IntakeStatus
import java.math.BigDecimal
import java.time.LocalDateTime

@DisplayName("Undo is one explicit operation of a person, not a superseding mark")
class UndoMarkUseCaseTest {

    private val world = IntakeTestWorld()
    private val morning = LocalDateTime.of(2026, 9, 25, 8, 5)
    private val afternoon = LocalDateTime.of(2026, 9, 25, 14, 5)

    @Test
    fun `undoing a taken mark returns the intake to pending and restores one dose`() = runTest {
        world.pending()
        world.moveTo(morning)
        world.mark(MarkRequest(Fixtures.INTAKE_ID, MarkResult.TAKEN, IntakeSource.PHONE))
        assertEquals(BigDecimal("29"), world.stock())

        world.moveTo(LocalDateTime.of(2026, 9, 25, 8, 6))
        val outcome = world.undo(UndoRequest(listOf(Fixtures.INTAKE_ID)))

        assertEquals(UndoOutcome.Applied(undone = 1), outcome)
        assertEquals(IntakeStatus.PENDING, world.stored().status)
        assertNull(world.stored().actedAt)
        assertNull(world.stored().source)
        assertNull(world.stored().actorProfileId)
        assertEquals(BigDecimal("30"), world.stock())
    }

    @Test
    fun `the second undo of the same mark returns nothing`() = runTest {
        world.pending()
        world.moveTo(morning)
        world.mark(MarkRequest(Fixtures.INTAKE_ID, MarkResult.TAKEN, IntakeSource.PHONE))
        world.undo(UndoRequest(listOf(Fixtures.INTAKE_ID)))

        val second = world.undo(UndoRequest(listOf(Fixtures.INTAKE_ID)))

        assertEquals(UndoOutcome.Applied(undone = 0), second)
        assertEquals(IntakeStatus.PENDING, world.stored().status)
        assertEquals(BigDecimal("30"), world.stock())
        assertEquals(1, world.log.records.count { it.event == OpEvent.INTAKE_MARK_UNDONE })
    }

    @Test
    fun `undoing a deliberate skip leaves the stock alone`() = runTest {
        world.pending()
        world.moveTo(morning)
        world.mark(MarkRequest(Fixtures.INTAKE_ID, MarkResult.SKIPPED, IntakeSource.PHONE))

        world.undo(UndoRequest(listOf(Fixtures.INTAKE_ID)))

        assertEquals(IntakeStatus.PENDING, world.stored().status)
        assertEquals(BigDecimal("30"), world.stock())
    }

    @Test
    fun `a new mark after an undo follows the ordinary first-wins rule`() = runTest {
        world.pending()
        world.moveTo(morning)
        world.mark(MarkRequest(Fixtures.INTAKE_ID, MarkResult.TAKEN, IntakeSource.PHONE))
        world.undo(UndoRequest(listOf(Fixtures.INTAKE_ID)))

        world.moveTo(LocalDateTime.of(2026, 9, 25, 8, 7))
        world.mark(MarkRequest(Fixtures.INTAKE_ID, MarkResult.SKIPPED, IntakeSource.PHONE))

        assertEquals(IntakeStatus.SKIPPED, world.stored().status)
        assertEquals(world.momentOf(LocalDateTime.of(2026, 9, 25, 8, 7)), world.stored().actedAt)
        assertEquals(BigDecimal("30"), world.stock())
    }

    @Test
    fun `the undo of a foreign profile carries the owner of the phone as its actor`() = runTest {
        world.foreignProfile()
        world.pending()
        world.moveTo(morning)
        world.mark(MarkRequest(Fixtures.INTAKE_ID, MarkResult.TAKEN, IntakeSource.PHONE))

        world.undo(UndoRequest(listOf(Fixtures.INTAKE_ID)))

        val record = world.log.records.last { it.event == OpEvent.INTAKE_MARK_UNDONE }
        assertEquals(Fixtures.PROFILE_ID, record.profileId)
    }

    @Test
    fun `an undo of one action returns every intake of the action and touches no other mark`() = runTest {
        world.pending()
        world.pending(at = LocalDateTime.of(2026, 9, 25, 14, 0), id = SECOND_INTAKE_ID)
        world.pending(at = LocalDateTime.of(2026, 9, 25, 21, 0), id = THIRD_INTAKE_ID)
        world.moveTo(morning)
        world.mark(MarkRequest(Fixtures.INTAKE_ID, MarkResult.TAKEN, IntakeSource.PHONE))
        world.moveTo(afternoon)
        world.mark(MarkRequest(SECOND_INTAKE_ID, MarkResult.TAKEN, IntakeSource.PHONE))
        world.mark(MarkRequest(THIRD_INTAKE_ID, MarkResult.TAKEN, IntakeSource.PHONE))

        val outcome = world.undo(UndoRequest(listOf(SECOND_INTAKE_ID, THIRD_INTAKE_ID)))

        assertEquals(UndoOutcome.Applied(undone = 2), outcome)
        assertEquals(IntakeStatus.TAKEN, world.stored(Fixtures.INTAKE_ID).status)
        assertEquals(IntakeStatus.PENDING, world.stored(SECOND_INTAKE_ID).status)
        assertEquals(IntakeStatus.PENDING, world.stored(THIRD_INTAKE_ID).status)
        assertEquals(BigDecimal("29"), world.stock())
    }

    @Test
    fun `an on-demand record is revoked instead of returning to pending`() = runTest {
        // Было отказом: «у кнопки Принять отмены нет». Решение Р-46 (SKL-127) отмену
        // разрешает, но другой: запись снимается, а не возвращается в «ожидается» —
        // ожидающихся приёмов у режима «по потребности» не существует (ПР-4).
        world.useOnDemand()
        world.pending()
        world.moveTo(morning)
        world.mark(MarkRequest(Fixtures.INTAKE_ID, MarkResult.TAKEN, IntakeSource.PHONE))

        val outcome = world.undo(UndoRequest(listOf(Fixtures.INTAKE_ID)))

        assertEquals(UndoOutcome.Applied(undone = 1), outcome)
        val revoked = world.stored()
        assertEquals(IntakeStatus.TAKEN, revoked.status)
        assertNotNull(revoked.revokedAt, "the record must be marked as revoked")
        assertEquals(BigDecimal("30"), world.stock())
    }

    @Test
    fun `an intake that no longer exists brings nothing but a report`() = runTest {
        world.pending()
        world.moveTo(morning)
        world.mark(MarkRequest(Fixtures.INTAKE_ID, MarkResult.TAKEN, IntakeSource.PHONE))

        val outcome = world.undo(UndoRequest(listOf(Fixtures.INTAKE_ID, "gone")))

        assertEquals(UndoOutcome.NotFound, outcome)
        assertEquals(IntakeStatus.TAKEN, world.stored().status)
        assertEquals(BigDecimal("29"), world.stock())
    }

    @Test
    fun `an empty request cancels nothing and reports no error`() = runTest {
        assertEquals(UndoOutcome.Applied(undone = 0), world.undo(UndoRequest(emptyList())))
    }

    @Test
    fun `the log names the undo with identifiers and no medical data`() = runTest {
        world.pending()
        world.moveTo(morning)
        world.mark(MarkRequest(Fixtures.INTAKE_ID, MarkResult.TAKEN, IntakeSource.PHONE))

        world.undo(UndoRequest(listOf(Fixtures.INTAKE_ID)))

        val record = world.log.records.single { it.event == OpEvent.INTAKE_MARK_UNDONE }
        assertEquals(Fixtures.INTAKE_ID, record.intakeId)
        assertEquals(Fixtures.MEDICATION_ID, record.medicationId)
        assertEquals(IntakeStatus.TAKEN.name, record.before)
        assertEquals(IntakeStatus.PENDING.name, record.after)
        val written = listOfNotNull(
            record.profileId,
            record.medicationId,
            record.intakeId,
            record.before,
            record.after,
            record.source,
            record.detail,
        )
        assertTrue(written.none { it.contains("magnesium") }, "the log must not carry the medication name")
        assertTrue(written.none { it.contains("tablet") }, "the log must not carry the dose unit")
    }

    private companion object {
        const val SECOND_INTAKE_ID = "i-second"
        const val THIRD_INTAKE_ID = "i-third"
    }
}
