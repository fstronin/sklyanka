package ru.stohastika.sklyanka.domain.intake

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import ru.stohastika.sklyanka.domain.log.OpEvent
import ru.stohastika.sklyanka.domain.model.Fixtures
import ru.stohastika.sklyanka.domain.model.IntakeSource
import ru.stohastika.sklyanka.domain.model.IntakeStatus
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.LocalTime

@DisplayName("The sweep over expired intakes is idempotent and silent")
class SweepMissedIntakesUseCaseTest {

    private val world = IntakeTestWorld()

    @Test
    fun `an unmarked intake becomes missed right after the end of its day`() = runTest {
        world.pending()

        world.moveTo(LocalDateTime.of(2026, 9, 25, 23, 59))
        assertEquals(0, world.sweep(SweepReason.TIMER).expired)
        assertEquals(IntakeStatus.PENDING, world.stored().status)

        world.moveTo(LocalDateTime.of(2026, 9, 26, 0, 0))
        assertEquals(1, world.sweep(SweepReason.TIMER).expired)
        assertEquals(IntakeStatus.MISSED, world.stored().status)
        assertEquals(IntakeSource.AUTO, world.stored().source)
        assertEquals(world.momentOf(LocalDateTime.of(2026, 9, 26, 0, 0)), world.stored().actedAt)
        assertEquals(BigDecimal("30"), world.stock())
    }

    @Test
    fun `a second pass over the same data changes nothing`() = runTest {
        world.pending()
        world.moveTo(LocalDateTime.of(2026, 9, 26, 0, 0))
        world.sweep(SweepReason.TIMER)
        val afterFirstPass = world.stored()

        world.moveTo(LocalDateTime.of(2026, 9, 26, 9, 0))
        val second = world.sweep(SweepReason.APP_START)

        assertEquals(0, second.expired)
        assertEquals(afterFirstPass, world.stored())
    }

    @Test
    fun `a day spent with the device switched off gives four misses and no notifications`() = runTest {
        listOf(LocalTime.of(8, 0), LocalTime.of(12, 0), LocalTime.of(16, 0), LocalTime.of(20, 0))
            .forEachIndexed { index, time ->
                world.pending(at = LocalDateTime.of(2026, 9, 25, time.hour, time.minute), id = "intake-$index")
            }

        world.moveTo(LocalDateTime.of(2026, 9, 26, 9, 0))
        val swept = world.sweep(SweepReason.APP_START)

        assertEquals(4, swept.expired)
        assertTrue(world.intakes.stored.values.all { it.status == IntakeStatus.MISSED })
        // Порта уведомлений у прохода нет вовсе: в журнале одна запись о переводе,
        // и ни одной попытки что-то показать человеку (НАП-34).
        assertEquals(listOf(OpEvent.INTAKE_AUTO_MISSED), world.log.events())
    }

    @Test
    fun `a deliberate skip is not turned into a missed intake`() = runTest {
        world.pending()
        world.moveTo(LocalDateTime.of(2026, 9, 25, 8, 40))
        world.mark(MarkRequest(Fixtures.INTAKE_ID, MarkResult.SKIPPED, IntakeSource.PHONE))

        world.moveTo(LocalDateTime.of(2026, 9, 26, 0, 0))
        world.sweep(SweepReason.TIMER)

        assertEquals(IntakeStatus.SKIPPED, world.stored().status)
        assertEquals(IntakeSource.PHONE, world.stored().source)
    }

    @Test
    fun `moving the system clock forward does not miss an intake whose window still runs`() = runTest {
        world.pending(at = LocalDateTime.of(2026, 9, 25, 20, 0))

        world.moveTo(LocalDateTime.of(2026, 9, 25, 12, 0))
        world.sweep(SweepReason.REPLAN)

        assertEquals(IntakeStatus.PENDING, world.stored().status)
    }

    @Test
    fun `moving the system clock back does not revive a missed intake`() = runTest {
        world.pending()
        world.moveTo(LocalDateTime.of(2026, 9, 26, 0, 0))
        world.sweep(SweepReason.TIMER)
        val missed = world.stored()

        world.moveTo(LocalDateTime.of(2026, 9, 25, 23, 0))
        world.sweep(SweepReason.REPLAN)

        assertEquals(missed, world.stored())
        assertFalse(world.stored().isPending)
    }

    @Test
    fun `an intake of another profile expires while the own profile is on the screen`() = runTest {
        world.foreignProfile()
        world.pending()

        world.moveTo(LocalDateTime.of(2026, 9, 26, 0, 0))

        assertEquals(1, world.sweep(SweepReason.APP_START).expired)
    }

    @Test
    fun `a mark made before midnight and delivered after it takes the record back from the sweep`() = runTest {
        world.pending(at = LocalDateTime.of(2026, 9, 25, 20, 0))
        world.moveTo(LocalDateTime.of(2026, 9, 26, 0, 0))
        world.sweep(SweepReason.TIMER)

        world.moveTo(LocalDateTime.of(2026, 9, 26, 0, 2))
        val outcome = world.mark(
            MarkRequest(
                intakeId = Fixtures.INTAKE_ID,
                result = MarkResult.TAKEN,
                source = IntakeSource.PHONE,
                actedAt = world.momentOf(LocalDateTime.of(2026, 9, 25, 23, 50)),
            ),
        )

        assertTrue(outcome is MarkOutcome.Superseded)
        assertEquals(IntakeStatus.TAKEN, world.stored().status)
        assertEquals(BigDecimal("29"), world.stock())
    }
}
