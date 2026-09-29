package ru.stohastika.sklyanka.domain.reminder

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import ru.stohastika.sklyanka.domain.model.Fixtures
import java.time.Instant
import java.time.LocalDateTime

@DisplayName("A break in reminders is recognised by a plan that fired nothing")
class OutageDetectorTest {

    private val now: Instant = Instant.parse("2026-09-25T15:00:00Z")
    private val noon: Instant = Instant.parse("2026-09-25T12:00:00Z")
    private val two: Instant = Instant.parse("2026-09-25T14:00:00Z")

    @Test
    fun `a moment that passed without a fired record is a break`() {
        val outage = OutageDetector.detect(
            now = now,
            expected = listOf(ExpectedFiring("intake-noon", noon), ExpectedFiring("intake-two", two)),
            firedIntakeIds = emptySet(),
        )

        assertNotNull(outage)
        assertEquals(noon, outage?.since)
        assertEquals(2, outage?.missed)
    }

    @Test
    fun `a fired reminder that the person simply ignored is not a break`() {
        val outage = OutageDetector.detect(
            now = now,
            expected = listOf(ExpectedFiring("intake-noon", noon)),
            firedIntakeIds = setOf("intake-noon"),
        )

        assertNull(outage)
    }

    @Test
    fun `a reminder that is still on its way is not a break yet`() {
        val outage = OutageDetector.detect(
            now = now,
            expected = listOf(ExpectedFiring("intake-now", now.minusSeconds(30))),
            firedIntakeIds = emptySet(),
        )

        assertNull(outage)
    }

    @Test
    fun `an empty plan is not a break`() {
        assertNull(OutageDetector.detect(now, emptyList(), emptySet()))
    }
}

/**
 * Запись перерыва в журнал при запуске приложения (НАП-38).
 *
 * Отдельный класс теста рядом с правилом: правило чистое, а запись обращается к журналу
 * и к плану, и проверять их вместе значит потерять из вида, что именно сломалось.
 */
@DisplayName("A detected break leaves exactly one record per break")
class RecordReminderOutageUseCaseTest {

    private val world = ReminderTestWorld()
    private val record = RecordReminderOutageUseCase(world.plan, world.journal, world.clock)

    @Test
    fun `a plan that fired nothing is recorded once`() = runTest {
        world.pending(at = LocalDateTime.of(2026, 9, 25, 12, 0))
        world.moveTo(LocalDateTime.of(2026, 9, 25, 18, 0))

        val first = record()
        val second = record()

        assertEquals(world.momentOf(LocalDateTime.of(2026, 9, 25, 12, 0)), first?.since)
        assertEquals(first, second)
        assertEquals(1, world.journal.of(ReminderEvent.OUTAGE).size)
    }

    @Test
    fun `a reminder that fired leaves no record of a break`() = runTest {
        world.pending(at = LocalDateTime.of(2026, 9, 25, 12, 0))
        world.moveTo(LocalDateTime.of(2026, 9, 25, 12, 0))
        world.fired(Fixtures.INTAKE_ID)

        world.moveTo(LocalDateTime.of(2026, 9, 25, 18, 0))

        assertNull(record())
        assertTrue(world.journal.of(ReminderEvent.OUTAGE).isEmpty())
    }
}
