package ru.stohastika.sklyanka.domain.reminder

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Instant

@DisplayName("Persistence is a property of the repeat, never of the first reminder")
class PersistentRepeatPolicyTest {

    private val now: Instant = Instant.parse("2026-09-25T11:00:00Z")

    private fun state(attempt: Int = 0, pending: Boolean = true, enabled: Boolean = true, minutes: Int = 15) =
        RepeatState(
            shownAttempt = attempt,
            stillPending = pending,
            persistentEnabled = enabled,
            snoozeMinutes = minutes,
        )

    @Test
    fun `the first reminder always uses the regular channel`() {
        assertEquals(ReminderChannel.REGULAR, PersistentRepeatPolicy.channelFor(0))
    }

    @Test
    fun `every repeat uses the persistent channel`() {
        assertEquals(ReminderChannel.PERSISTENT, PersistentRepeatPolicy.channelFor(1))
        assertEquals(ReminderChannel.PERSISTENT, PersistentRepeatPolicy.channelFor(2))
    }

    @Test
    fun `with the setting off there is not a single repeat`() {
        assertEquals(
            RepeatDecision.Stop(RepeatStop.DISABLED),
            PersistentRepeatPolicy.decide(now, state(enabled = false)),
        )
    }

    @Test
    fun `a marked intake cancels the repeat`() {
        assertEquals(
            RepeatDecision.Stop(RepeatStop.MARKED),
            PersistentRepeatPolicy.decide(now, state(pending = false)),
        )
    }

    @Test
    fun `the repeat is planned one snooze interval ahead and is persistent`() {
        val decision = PersistentRepeatPolicy.decide(now, state()) as RepeatDecision.Repeat

        assertEquals(now.plusSeconds(15 * 60), decision.at)
        assertEquals(1, decision.attempt)
        assertEquals(ReminderChannel.PERSISTENT, decision.channel)
    }

    @Test
    fun `the interval comes from the setting and not from a constant`() {
        val decision = PersistentRepeatPolicy.decide(now, state(minutes = 30)) as RepeatDecision.Repeat

        assertEquals(now.plusSeconds(30 * 60), decision.at)
    }

    @Test
    fun `the number of repeats is limited and the intake stays pending afterwards`() {
        assertEquals(2, PersistentRepeatPolicy.MAX_REPEATS)
        val afterLast = PersistentRepeatPolicy.decide(now, state(attempt = PersistentRepeatPolicy.MAX_REPEATS))

        assertEquals(RepeatDecision.Stop(RepeatStop.LIMIT_REACHED), afterLast)
    }
}
