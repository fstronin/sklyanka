package ru.stohastika.sklyanka.domain.intake

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import ru.stohastika.sklyanka.domain.model.Fixtures
import ru.stohastika.sklyanka.domain.model.IntakeSource
import ru.stohastika.sklyanka.domain.model.IntakeStatus
import ru.stohastika.sklyanka.domain.model.Invariants
import java.math.BigDecimal
import java.time.LocalDateTime

@DisplayName("An on-demand intake is born already marked and respects the daily limit")
class CreatePrnIntakeUseCaseTest {

    private val world = IntakeTestWorld()

    @Test
    fun `pressing take creates a taken intake whose scheduled time equals the moment of the mark`() = runTest {
        world.useOnDemand()
        world.moveTo(LocalDateTime.of(2026, 9, 25, 13, 20))

        val outcome = world.createPrn(Fixtures.MEDICATION_ID, IntakeSource.PHONE)

        assertTrue(outcome is PrnOutcome.Created, "expected a created intake, got $outcome")
        val created = (outcome as PrnOutcome.Created).intake
        assertEquals(IntakeStatus.TAKEN, created.status)
        assertEquals(LocalDateTime.of(2026, 9, 25, 13, 20), created.scheduledAt)
        assertEquals(world.momentOf(LocalDateTime.of(2026, 9, 25, 13, 20)), created.actedAt)
        assertEquals(BigDecimal("29"), world.stock())
        assertEquals(emptyList<String>(), Invariants.violations(created, world.medication(), world.zone))
    }

    @ParameterizedTest(name = "limit {0}, already {1} intakes today: becomes {2}")
    @CsvSource("3, 0, 1", "3, 2, 3", "3, 3, 3", "1, 1, 1")
    fun `the daily limit caps the number of on-demand intakes`(limit: Int, already: Int, becomes: Int) = runTest {
        world.useOnDemand(maxPerDay = limit)
        repeat(already) { index ->
            world.moveTo(LocalDateTime.of(2026, 9, 25, 9 + index, 0))
            world.createPrn(Fixtures.MEDICATION_ID, IntakeSource.PHONE)
        }
        world.moveTo(LocalDateTime.of(2026, 9, 25, 21, 0))

        world.createPrn(Fixtures.MEDICATION_ID, IntakeSource.PHONE)

        assertEquals(becomes, world.intakes.stored.size)
    }

    @Test
    fun `a rejected press reports the limit and the count of the day`() = runTest {
        world.useOnDemand(maxPerDay = 1)
        world.moveTo(LocalDateTime.of(2026, 9, 25, 9, 0))
        world.createPrn(Fixtures.MEDICATION_ID, IntakeSource.PHONE)

        world.moveTo(LocalDateTime.of(2026, 9, 25, 21, 0))
        val outcome = world.createPrn(Fixtures.MEDICATION_ID, IntakeSource.PHONE)

        assertEquals(PrnOutcome.RejectedDailyLimit(limit = 1, countToday = 1), outcome)
        assertEquals(BigDecimal("29"), world.stock())
    }

    @Test
    fun `the limit resets with the local midnight`() = runTest {
        world.useOnDemand(maxPerDay = 3)
        repeat(3) { index ->
            world.moveTo(LocalDateTime.of(2026, 9, 25, 20 + index, 0))
            world.createPrn(Fixtures.MEDICATION_ID, IntakeSource.PHONE)
        }

        world.moveTo(LocalDateTime.of(2026, 9, 26, 0, 5))
        val outcome = world.createPrn(Fixtures.MEDICATION_ID, IntakeSource.PHONE)

        assertTrue(outcome is PrnOutcome.Created)
        assertEquals(LocalDateTime.of(2026, 9, 26, 0, 5), (outcome as PrnOutcome.Created).intake.scheduledAt)
    }

    @Test
    fun `two presses in a row do not slip past the limit`() = runTest {
        world.useOnDemand(maxPerDay = 3)
        repeat(2) { index ->
            world.moveTo(LocalDateTime.of(2026, 9, 25, 9 + index, 0))
            world.createPrn(Fixtures.MEDICATION_ID, IntakeSource.PHONE)
        }

        world.moveTo(LocalDateTime.of(2026, 9, 25, 21, 0))
        world.createPrn(Fixtures.MEDICATION_ID, IntakeSource.PHONE)
        world.createPrn(Fixtures.MEDICATION_ID, IntakeSource.PHONE)

        assertEquals(3, world.intakes.stored.size)
    }

    @Test
    fun `an on-demand intake is never pending and never expires`() = runTest {
        world.useOnDemand()
        world.moveTo(LocalDateTime.of(2026, 9, 25, 13, 20))
        world.createPrn(Fixtures.MEDICATION_ID, IntakeSource.PHONE)

        world.moveTo(LocalDateTime.of(2026, 9, 26, 0, 0))
        val swept = world.sweep(SweepReason.TIMER)

        assertEquals(0, swept.expired)
        assertTrue(world.intakes.stored.values.none { it.isPending })
    }

    @Test
    fun `a scheduled medication is not taken on demand`() = runTest {
        val outcome = world.createPrn(Fixtures.MEDICATION_ID, IntakeSource.PHONE)

        assertEquals(PrnOutcome.NotOnDemand, outcome)
    }
}
