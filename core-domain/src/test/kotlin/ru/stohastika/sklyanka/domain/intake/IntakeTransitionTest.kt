package ru.stohastika.sklyanka.domain.intake

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import ru.stohastika.sklyanka.domain.model.Fixtures
import ru.stohastika.sklyanka.domain.model.IntakeSource
import ru.stohastika.sklyanka.domain.model.IntakeStatus
import ru.stohastika.sklyanka.domain.model.Invariants
import java.time.Instant

@DisplayName("Intake transitions are idempotent by intake id and ordered by the moment of action")
class IntakeTransitionTest {

    private val eightOhThree: Instant = Instant.parse("2026-09-25T05:03:00Z")
    private val eightOhFive: Instant = Instant.parse("2026-09-25T05:05:00Z")
    private val eightTen: Instant = Instant.parse("2026-09-25T05:10:00Z")

    @Test
    fun `marking a pending intake as taken records the moment, the source and a stock decrement`() {
        val decision = IntakeTransition.decide(Fixtures.pendingIntake(), command(MarkResult.TAKEN, eightOhFive))

        val write = assertWrite(decision)
        assertEquals(IntakeStatus.TAKEN, write.intake.status)
        assertEquals(eightOhFive, write.intake.actedAt)
        assertEquals(IntakeSource.PHONE, write.intake.source)
        assertEquals(StockEffect.DECREMENT, write.stock)
        assertEquals(emptyList<String>(), Invariants.violations(write.intake))
    }

    @Test
    fun `a deliberate skip leaves the stock alone`() {
        val decision = IntakeTransition.decide(Fixtures.pendingIntake(), command(MarkResult.SKIPPED, eightOhFive))

        val write = assertWrite(decision)
        assertEquals(IntakeStatus.SKIPPED, write.intake.status)
        assertEquals(StockEffect.NONE, write.stock)
    }

    @Test
    fun `the scheduled moment is never rewritten by a mark`() {
        val pending = Fixtures.pendingIntake()

        val write = assertWrite(IntakeTransition.decide(pending, command(MarkResult.TAKEN, eightTen)))

        assertEquals(pending.scheduledAt, write.intake.scheduledAt)
    }

    @Test
    fun `a repeated mark of the same intake changes nothing`() {
        val taken = Fixtures.takenIntake(actedAt = eightOhFive)

        val decision = IntakeTransition.decide(taken, command(MarkResult.TAKEN, eightTen))

        val keep = assertKeep(decision)
        assertEquals(eightOhFive, keep.intake.actedAt)
        assertEquals(IgnoreReason.ALREADY_MARKED, keep.reason)
    }

    @Test
    fun `a later mark with another decision does not overwrite the first one`() {
        val taken = Fixtures.takenIntake(actedAt = eightOhFive)

        val keep = assertKeep(IntakeTransition.decide(taken, command(MarkResult.SKIPPED, eightTen)))

        assertEquals(IntakeStatus.TAKEN, keep.intake.status)
        assertEquals(eightOhFive, keep.intake.actedAt)
    }

    @Test
    fun `two marks made at the very same moment leave the first one in place`() {
        val missed = IntakeTransition.expire(Fixtures.pendingIntake(), eightOhFive)

        val sameMoment = command(MarkResult.TAKEN, eightOhFive)

        val keep = assertKeep(IntakeTransition.decide(missed, sameMoment))

        assertEquals(IntakeSource.AUTO, keep.intake.source)
        assertEquals(IgnoreReason.RACE_LOSER, keep.reason)
    }

    @Test
    fun `an earlier mark delivered later wins the race and does not decrement the stock twice`() {
        val taken = Fixtures.takenIntake(actedAt = eightOhFive)

        val write = assertWrite(
            IntakeTransition.decide(taken, command(MarkResult.TAKEN, eightOhThree)),
        )

        assertTrue(write.superseded)
        assertEquals(eightOhThree, write.intake.actedAt)
        assertEquals(IntakeSource.PHONE, write.intake.source)
        assertEquals(StockEffect.NONE, write.stock)
    }

    @Test
    fun `a skip made earlier beats a later take on the phone and returns the dose`() {
        val taken = Fixtures.takenIntake(actedAt = eightOhFive)

        val write = assertWrite(
            IntakeTransition.decide(taken, command(MarkResult.SKIPPED, eightOhThree)),
        )

        assertEquals(IntakeStatus.SKIPPED, write.intake.status)
        assertEquals(StockEffect.RESTORE, write.stock)
    }

    @Test
    fun `an expired intake becomes missed, signed by the application and with nobody behind it`() {
        val missed = IntakeTransition.expire(Fixtures.pendingIntake(), eightOhFive)

        assertEquals(IntakeStatus.MISSED, missed.status)
        assertEquals(IntakeSource.AUTO, missed.source)
        assertEquals(eightOhFive, missed.actedAt)
        assertNull(missed.actorProfileId)
        assertEquals(Fixtures.pendingIntake().scheduledAt, missed.scheduledAt)
    }

    @Test
    fun `a mark that arrived before the automatic miss takes the record back`() {
        val missed = IntakeTransition.expire(Fixtures.pendingIntake(), Instant.parse("2026-09-25T21:00:00Z"))

        val write = assertWrite(
            IntakeTransition.decide(missed, command(MarkResult.TAKEN, eightOhFive)),
        )

        assertEquals(IntakeStatus.TAKEN, write.intake.status)
        assertEquals(StockEffect.DECREMENT, write.stock)
    }

    // --- Отмена отметки (SKL-30) --------------------------------------------------

    @Test
    fun `undoing a taken intake returns it to pending with a stock restore and no mark fields`() {
        val taken = Fixtures.takenIntake(actedAt = eightOhFive)
            .copy(actorProfileId = Fixtures.OTHER_PROFILE_ID)

        val revert = assertRevert(IntakeTransition.undo(taken))

        assertEquals(IntakeStatus.PENDING, revert.intake.status)
        assertNull(revert.intake.actedAt)
        assertNull(revert.intake.source)
        assertNull(revert.intake.actorProfileId)
        assertEquals(StockEffect.RESTORE, revert.stock)
        assertEquals(emptyList<String>(), Invariants.violations(revert.intake))
    }

    @Test
    fun `undoing a deliberate skip returns it to pending and leaves the stock alone`() {
        val skipped = Fixtures.pendingIntake().copy(
            status = IntakeStatus.SKIPPED,
            actedAt = eightOhFive,
            source = IntakeSource.PHONE,
        )

        val revert = assertRevert(IntakeTransition.undo(skipped))

        assertEquals(IntakeStatus.PENDING, revert.intake.status)
        assertEquals(StockEffect.NONE, revert.stock)
        assertEquals(emptyList<String>(), Invariants.violations(revert.intake))
    }

    @Test
    fun `undoing never rewrites the scheduled moment`() {
        val taken = Fixtures.takenIntake(actedAt = eightOhFive)

        val revert = assertRevert(IntakeTransition.undo(taken))

        assertEquals(taken.scheduledAt, revert.intake.scheduledAt)
    }

    @Test
    fun `a pending intake has nothing to undo`() {
        val pending = Fixtures.pendingIntake()

        assertEquals(UndoDecision.Keep(pending), IntakeTransition.undo(pending))
    }

    // --- Снятие записи «по потребности» (Р-46, SKL-127) ---------------------------------------

    @Test
    fun `revoking a taken intake marks the moment and returns the dose`() {
        val taken = Fixtures.takenIntake(actedAt = eightOhFive)

        val revoked = assertRevoked(IntakeTransition.revoke(taken, eightTen))

        assertEquals(eightTen, revoked.intake.revokedAt)
        // Статус не подменяется: снятие не переписывает решение человека (Р-46).
        assertEquals(IntakeStatus.TAKEN, revoked.intake.status)
        assertEquals(eightOhFive, revoked.intake.actedAt)
        assertEquals(StockEffect.RESTORE, revoked.stock)
        assertEquals(emptyList<String>(), Invariants.violations(revoked.intake))
    }

    @Test
    fun `revoking never rewrites the scheduled moment`() {
        val taken = Fixtures.takenIntake(actedAt = eightOhFive)

        val revoked = assertRevoked(IntakeTransition.revoke(taken, eightTen))

        assertEquals(taken.scheduledAt, revoked.intake.scheduledAt)
    }

    @Test
    fun `an already revoked intake is silent the second time`() {
        val revoked = Fixtures.takenIntake(actedAt = eightOhFive).copy(revokedAt = eightTen)

        assertEquals(RevokeDecision.Keep(revoked), IntakeTransition.revoke(revoked, eightTen))
    }

    @Test
    fun `a pending intake has nothing to revoke`() {
        val pending = Fixtures.pendingIntake()

        assertEquals(RevokeDecision.Keep(pending), IntakeTransition.revoke(pending, eightTen))
    }

    @Test
    fun `a deliberate skip is not revoked because only a taken record is`() {
        // Снятие — про запись, появившуюся нажатием «Принять»; осознанный пропуск
        // снимается отменой отметки, а не снятием записи (Р-46).
        val skipped = Fixtures.pendingIntake().copy(
            status = IntakeStatus.SKIPPED,
            actedAt = eightOhFive,
            source = IntakeSource.PHONE,
        )

        assertEquals(RevokeDecision.Keep(skipped), IntakeTransition.revoke(skipped, eightTen))
    }

    @Test
    fun `an automatically missed intake is not resurrected by an undo`() {
        val missed = IntakeTransition.expire(Fixtures.pendingIntake(), eightTen)

        assertEquals(UndoDecision.Keep(missed), IntakeTransition.undo(missed))
    }

    private fun command(
        result: MarkResult,
        actedAt: Instant,
        source: IntakeSource = IntakeSource.PHONE,
        actorProfileId: String? = null,
    ) = MarkCommand(
        intakeId = Fixtures.INTAKE_ID,
        result = result,
        source = source,
        actedAt = actedAt,
        actorProfileId = actorProfileId,
    )

    private fun assertWrite(decision: MarkDecision): MarkDecision.Write {
        assertTrue(decision is MarkDecision.Write, "expected a write, got $decision")
        return decision as MarkDecision.Write
    }

    private fun assertKeep(decision: MarkDecision): MarkDecision.Keep {
        assertTrue(decision is MarkDecision.Keep, "expected the record to stay, got $decision")
        return decision as MarkDecision.Keep
    }

    private fun assertRevert(decision: UndoDecision): UndoDecision.Revert {
        assertTrue(decision is UndoDecision.Revert, "expected a revert, got $decision")
        return decision as UndoDecision.Revert
    }

    private fun assertRevoked(decision: RevokeDecision): RevokeDecision.Revoked {
        assertTrue(decision is RevokeDecision.Revoked, "expected a revocation, got $decision")
        return decision as RevokeDecision.Revoked
    }
}
