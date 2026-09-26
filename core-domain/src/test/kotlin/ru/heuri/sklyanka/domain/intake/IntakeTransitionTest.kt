package ru.heuri.sklyanka.domain.intake

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import ru.heuri.sklyanka.domain.model.Fixtures
import ru.heuri.sklyanka.domain.model.IntakeSource
import ru.heuri.sklyanka.domain.model.IntakeStatus
import ru.heuri.sklyanka.domain.model.Invariants
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
        val taken = Fixtures.takenIntake(actedAt = eightOhFive)

        val sameMoment = command(MarkResult.TAKEN, eightOhFive, IntakeSource.WATCH)

        val keep = assertKeep(IntakeTransition.decide(taken, sameMoment))

        assertEquals(IntakeSource.PHONE, keep.intake.source)
        assertEquals(IgnoreReason.RACE_LOSER, keep.reason)
    }

    @Test
    fun `an earlier mark delivered later wins the race and does not decrement the stock twice`() {
        val taken = Fixtures.takenIntake(actedAt = eightOhFive)

        val write = assertWrite(
            IntakeTransition.decide(taken, command(MarkResult.TAKEN, eightOhThree, IntakeSource.WATCH)),
        )

        assertTrue(write.superseded)
        assertEquals(eightOhThree, write.intake.actedAt)
        assertEquals(IntakeSource.WATCH, write.intake.source)
        assertEquals(StockEffect.NONE, write.stock)
    }

    @Test
    fun `a skip made earlier on the watch beats a later take on the phone and returns the dose`() {
        val taken = Fixtures.takenIntake(actedAt = eightOhFive)

        val write = assertWrite(
            IntakeTransition.decide(taken, command(MarkResult.SKIPPED, eightOhThree, IntakeSource.WATCH)),
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
            IntakeTransition.decide(missed, command(MarkResult.TAKEN, eightOhFive, IntakeSource.WATCH)),
        )

        assertEquals(IntakeStatus.TAKEN, write.intake.status)
        assertEquals(StockEffect.DECREMENT, write.stock)
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
}
