package ru.stohastika.sklyanka.domain.reminder

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import ru.stohastika.sklyanka.domain.model.Fixtures
import ru.stohastika.sklyanka.domain.reminder.port.WatchSendResult
import java.time.LocalDateTime

@DisplayName("A fired alarm shows the card, writes the journal and plans the repeat")
class HandleFiredReminderUseCaseTest {

    private val world = ReminderTestWorld()

    private suspend fun fireAtFourteen(attempt: Int = 0): FiredOutcome {
        world.moveTo(LocalDateTime.of(2026, 9, 25, 14, 0))
        return world.fired(Fixtures.INTAKE_ID, attempt)
    }

    @Test
    fun `the first reminder goes to the regular channel even with persistence on`() = runTest {
        world.pending()
        world.settings.setPersistentEnabled(true)

        val outcome = fireAtFourteen() as FiredOutcome.Shown

        assertEquals(ReminderChannel.REGULAR, outcome.channel)
        assertEquals(ReminderChannel.REGULAR, world.notifications.shown.single().second)
    }

    @Test
    fun `a repeat goes to the persistent channel`() = runTest {
        world.pending()

        val outcome = fireAtFourteen(attempt = 1) as FiredOutcome.Shown

        assertEquals(ReminderChannel.PERSISTENT, outcome.channel)
        assertEquals(1, world.journal.of(ReminderEvent.REPEATED).size)
        assertTrue(world.journal.of(ReminderEvent.SHOWN).isEmpty())
    }

    @Test
    fun `the card carries the intake of the slot and its deviation lands in the journal`() = runTest {
        world.pending()
        world.moveTo(LocalDateTime.of(2026, 9, 25, 14, 0).plusSeconds(37))

        world.fired(Fixtures.INTAKE_ID)

        val shown = world.journal.of(ReminderEvent.SHOWN).single()
        assertEquals(37L, shown.deviationSeconds)
        assertEquals(world.momentOf(LocalDateTime.of(2026, 9, 25, 14, 0)), shown.scheduledAt)
        assertEquals(true, shown.delivered)
    }

    @Test
    fun `two intakes on one time are shown as a single card with two rows`() = runTest {
        world.pending()
        world.secondMedication()

        val outcome = fireAtFourteen() as FiredOutcome.Shown

        assertEquals(2, outcome.card.items.size)
        // Второго полноэкранного напоминания подряд не появляется: слот один,
        // уведомление одно (НАП-36).
        assertEquals(1, world.notifications.shown.size)
        assertEquals(
            setOf(Fixtures.INTAKE_ID, ReminderTestWorld.SECOND_INTAKE_ID),
            outcome.card.items.map { it.intakeId }.toSet(),
        )
    }

    @Test
    fun `a second alarm of the same slot updates the same card`() = runTest {
        world.pending()
        world.secondMedication()

        fireAtFourteen()
        world.fired(ReminderTestWorld.SECOND_INTAKE_ID)

        assertEquals(1, world.notifications.shown.map { it.first.slot }.distinct().size)
    }

    @Test
    fun `an intake marked before the show is not shown at all`() = runTest {
        world.pending()
        world.take()

        val outcome = fireAtFourteen()

        assertEquals(FiredOutcome.Suppressed(SuppressReason.ALREADY_MARKED), outcome)
        assertTrue(world.notifications.shown.isEmpty())
        val suppressed = world.journal.of(ReminderEvent.SUPPRESSED).last()
        assertEquals(SuppressReason.ALREADY_MARKED.code, suppressed.reason)
        assertEquals(false, suppressed.delivered)
    }

    @Test
    fun `an intake whose window has expired is not shown`() = runTest {
        world.pending()
        world.moveTo(LocalDateTime.of(2026, 9, 26, 0, 0))

        val outcome = world.fired(Fixtures.INTAKE_ID)

        assertEquals(FiredOutcome.Suppressed(SuppressReason.WINDOW_EXPIRED), outcome)
        assertTrue(world.notifications.shown.isEmpty())
    }

    @Test
    fun `a revoked notification permission is recorded as a suppressed show`() = runTest {
        world.pending()
        world.notifications.enabled = false

        val outcome = fireAtFourteen()

        assertEquals(FiredOutcome.Suppressed(SuppressReason.NOTIFICATIONS_DISABLED), outcome)
        assertEquals(
            SuppressReason.NOTIFICATIONS_DISABLED.code,
            world.journal.of(ReminderEvent.SUPPRESSED).single().reason,
        )
    }

    @Test
    fun `an unknown intake is recorded and shows nothing`() = runTest {
        val outcome = world.fired("nobody")

        assertEquals(FiredOutcome.Suppressed(SuppressReason.NOT_FOUND), outcome)
    }

    @Test
    fun `a connected watch gets the card and the chip appears`() = runTest {
        world.pending()
        world.watch.connect()

        val outcome = fireAtFourteen() as FiredOutcome.Shown

        assertEquals(WatchSendResult.SENT, outcome.watch)
        assertTrue(outcome.card.mirroredToWatch)
        assertEquals(1, world.watch.sent.size)
    }

    @Test
    fun `without a watch the chip is absent and the show is not an error`() = runTest {
        world.pending()

        val outcome = fireAtFourteen() as FiredOutcome.Shown

        assertEquals(WatchSendResult.NOT_CONFIGURED, outcome.watch)
        assertFalse(outcome.card.mirroredToWatch)
        assertEquals(
            WatchSendResult.NOT_CONFIGURED.code,
            world.journal.of(ReminderEvent.SHOWN).single().watchResult,
        )
    }

    @Test
    fun `with persistence on the repeat alarm is planned one interval ahead`() = runTest {
        world.pending()
        world.settings.setPersistentEnabled(true)

        fireAtFourteen()

        val repeat = world.alarms.repeats.single()
        assertEquals(Fixtures.INTAKE_ID, repeat.first)
        assertEquals(world.momentOf(LocalDateTime.of(2026, 9, 25, 14, 15)), repeat.second)
        assertEquals(1, repeat.third)
    }

    @Test
    fun `with persistence off no repeat is planned`() = runTest {
        world.pending()

        val outcome = fireAtFourteen() as FiredOutcome.Shown

        assertEquals(RepeatDecision.Stop(RepeatStop.DISABLED), outcome.repeat)
        assertTrue(world.alarms.repeats.isEmpty())
    }

    @Test
    fun `after the last allowed repeat nothing is planned any more`() = runTest {
        world.pending()
        world.settings.setPersistentEnabled(true)

        val outcome = fireAtFourteen(attempt = PersistentRepeatPolicy.MAX_REPEATS) as FiredOutcome.Shown

        assertEquals(RepeatDecision.Stop(RepeatStop.LIMIT_REACHED), outcome.repeat)
        assertTrue(world.alarms.repeats.isEmpty())
        assertTrue(world.stored().isPending, "the intake stays unmarked (REM-26)")
    }

    @Test
    fun `the deviation of a show is counted from the moment the notification appeared`() = runTest {
        // Шторка наполняется не мгновенно: уведомление обнаруживается третьим опросом,
        // и каждый опрос стоит секунду по часам устройства.
        world.pending()
        world.notifications.appearsAfterPolls = 2
        world.notifications.onPoll = { world.clock.advanceTo(world.clock.now().plusSeconds(1)) }
        world.moveTo(LocalDateTime.of(2026, 9, 25, 14, 0).plusSeconds(5))

        world.fired(Fixtures.INTAKE_ID)

        // Получение управления — пять секунд, предъявление — восемь: гейт считается
        // по второму числу (НАП-2, SKL-03 TDD §4.1), и в журнале они не совпадают.
        assertEquals(5L, world.journal.of(ReminderEvent.FIRED).single().deviationSeconds)
        assertEquals(8L, world.journal.of(ReminderEvent.SHOWN).single().deviationSeconds)
    }

    @Test
    fun `a show that never reached the shade is not written down as shown`() = runTest {
        world.pending()
        world.settings.setPersistentEnabled(true)
        world.notifications.reachesTheShade = false

        val outcome = fireAtFourteen()

        assertEquals(FiredOutcome.Suppressed(SuppressReason.NOT_PRESENTED), outcome)
        assertTrue(
            world.journal.of(ReminderEvent.SHOWN).isEmpty(),
            "a reminder nobody saw must not enter the on time share",
        )
        assertEquals(1, world.journal.of(ReminderEvent.FIRED).size, "the alarm did reach the application")
        val suppressed = world.journal.of(ReminderEvent.SUPPRESSED).single()
        assertEquals(SuppressReason.NOT_PRESENTED.code, suppressed.reason)
        assertEquals(false, suppressed.delivered)
        assertEquals(
            1,
            world.alarms.repeats.size,
            "a reminder that failed to appear is exactly when the next attempt matters (REM-1)",
        )
    }
}
