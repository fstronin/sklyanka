package ru.heuri.sklyanka.domain.intake

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import ru.heuri.sklyanka.domain.model.Fixtures
import ru.heuri.sklyanka.domain.model.IntakeSource
import ru.heuri.sklyanka.domain.model.IntakeStatus
import ru.heuri.sklyanka.domain.model.Invariants
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.ZoneId

@DisplayName("Marking an intake is one entry point for the phone, the reminder card and the watch")
class MarkIntakeUseCaseTest {

    private val world = IntakeTestWorld()

    @Test
    fun `a taken mark decrements the stock by one dose`() = runTest {
        world.pending()
        world.moveTo(LocalDateTime.of(2026, 9, 25, 8, 5))

        val outcome = world.mark(MarkRequest(Fixtures.INTAKE_ID, MarkResult.TAKEN, IntakeSource.PHONE))

        assertTrue(outcome is MarkOutcome.Applied)
        assertEquals(IntakeStatus.TAKEN, world.stored().status)
        assertEquals(world.momentOf(LocalDateTime.of(2026, 9, 25, 8, 5)), world.stored().actedAt)
        assertEquals(BigDecimal("29"), world.stock())
    }

    @Test
    fun `a deliberate skip keeps the stock`() = runTest {
        world.pending()
        world.moveTo(LocalDateTime.of(2026, 9, 25, 8, 40))

        world.mark(MarkRequest(Fixtures.INTAKE_ID, MarkResult.SKIPPED, IntakeSource.PHONE))

        assertEquals(IntakeStatus.SKIPPED, world.stored().status)
        assertEquals(BigDecimal("30"), world.stock())
    }

    @Test
    fun `a second mark neither changes the record nor decrements the stock again`() = runTest {
        world.pending()
        world.moveTo(LocalDateTime.of(2026, 9, 25, 8, 5))
        world.mark(MarkRequest(Fixtures.INTAKE_ID, MarkResult.TAKEN, IntakeSource.PHONE))

        world.moveTo(LocalDateTime.of(2026, 9, 25, 8, 6))
        val outcome = world.mark(MarkRequest(Fixtures.INTAKE_ID, MarkResult.TAKEN, IntakeSource.PHONE))

        assertTrue(outcome is MarkOutcome.Ignored)
        assertEquals(world.momentOf(LocalDateTime.of(2026, 9, 25, 8, 5)), world.stored().actedAt)
        assertEquals(BigDecimal("29"), world.stock())
    }

    @Test
    fun `a watch press delivered late wins by its moment of action and leaves one decrement`() = runTest {
        world.pending()
        world.moveTo(LocalDateTime.of(2026, 9, 25, 8, 5))
        world.mark(MarkRequest(Fixtures.INTAKE_ID, MarkResult.TAKEN, IntakeSource.PHONE))

        world.moveTo(LocalDateTime.of(2026, 9, 25, 8, 6))
        val outcome = world.mark(
            MarkRequest(
                intakeId = Fixtures.INTAKE_ID,
                result = MarkResult.TAKEN,
                source = IntakeSource.WATCH,
                actedAt = world.momentOf(LocalDateTime.of(2026, 9, 25, 8, 3)),
            ),
        )

        assertTrue(outcome is MarkOutcome.Superseded)
        assertEquals(IntakeSource.WATCH, world.stored().source)
        assertEquals(world.momentOf(LocalDateTime.of(2026, 9, 25, 8, 3)), world.stored().actedAt)
        assertEquals(BigDecimal("29"), world.stock())
    }

    @Test
    fun `an intake of another profile is signed by the owner of the phone`() = runTest {
        val foreign = world.foreignProfile()
        world.pending()
        world.moveTo(LocalDateTime.of(2026, 9, 25, 9, 10))

        world.mark(MarkRequest(Fixtures.INTAKE_ID, MarkResult.TAKEN, IntakeSource.PHONE))

        assertEquals(Fixtures.PROFILE_ID, world.stored().actorProfileId)
        assertEquals(
            emptyList<String>(),
            Invariants.violations(world.stored(), world.medication(), world.zone),
            "the actor must differ from the profile of the medication (INV-18): ${foreign.id}",
        )
    }

    @Test
    fun `an intake of the own profile carries no signature`() = runTest {
        world.pending()
        world.moveTo(LocalDateTime.of(2026, 9, 25, 8, 5))

        world.mark(MarkRequest(Fixtures.INTAKE_ID, MarkResult.TAKEN, IntakeSource.PHONE))

        assertNull(world.stored().actorProfileId)
    }

    @Test
    fun `an intake that no longer exists is reported and not created anew`() = runTest {
        val outcome = world.mark(MarkRequest("gone", MarkResult.TAKEN, IntakeSource.PHONE))

        assertEquals(MarkOutcome.NotFound, outcome)
        assertTrue(world.intakes.stored.isEmpty())
    }

    /**
     * Перелёт не трогает ни момент отметки, ни назначенное время: первое — факт в UTC,
     * второе — правило в местном времени (НФТ-34, сценарий `@timezone`).
     */
    @Test
    fun `a flight changes neither the moment of the mark nor the scheduled time`() = runTest {
        world.pending()
        world.moveTo(LocalDateTime.of(2026, 9, 25, 8, 5))
        world.mark(MarkRequest(Fixtures.INTAKE_ID, MarkResult.TAKEN, IntakeSource.PHONE))
        val before = world.stored()

        world.moveTo(LocalDateTime.of(2026, 9, 25, 12, 0))
        world.clock.moveTo(ZoneId.of("Asia/Yekaterinburg"))

        assertEquals(before, world.stored())
    }
}
