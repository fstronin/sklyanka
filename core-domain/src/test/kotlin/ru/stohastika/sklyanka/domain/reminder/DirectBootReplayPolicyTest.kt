package ru.stohastika.sklyanka.domain.reminder

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Instant

@DisplayName("DirectBootReplayPolicy restores only future, whole records and rejects a broken snapshot")
class DirectBootReplayPolicyTest {

    private val now: Instant = Instant.parse("2026-09-27T02:05:00Z")

    private fun alarm(id: String = "intake-1", at: Instant = now.plusSeconds(3600), attempt: Int = 0) =
        DirectBootAlarm(id, at, attempt)

    private fun snapshot(
        version: Int = DirectBootReplayPolicy.FORMAT_VERSION,
        updatedAt: Instant = now,
        alarms: List<DirectBootAlarm> = listOf(alarm()),
    ) = DirectBootAlarms(version, updatedAt, alarms)

    @Test
    fun `no snapshot means a missing decision`() {
        assertEquals(
            DirectBootReplayDecision.Skip(DirectBootTraceReason.MISSING),
            DirectBootReplayPolicy.decide(null, now),
        )
    }

    @Test
    fun `a foreign version is unreadable`() {
        val decision = DirectBootReplayPolicy.decide(snapshot(version = 2), now)

        assertEquals(DirectBootReplayDecision.Skip(DirectBootTraceReason.UNREADABLE), decision)
    }

    @Test
    fun `exactly the window size is fine and one more record is unreadable`() {
        val atLimit = List(DirectBootReplayPolicy.MAX_ALARMS) { alarm(id = "intake-$it") }
        val overLimit = atLimit + alarm(id = "intake-over")

        assertTrue(DirectBootReplayPolicy.decide(snapshot(alarms = atLimit), now) is DirectBootReplayDecision.Restore)
        assertEquals(
            DirectBootReplayDecision.Skip(DirectBootTraceReason.UNREADABLE),
            DirectBootReplayPolicy.decide(snapshot(alarms = overLimit), now),
        )
    }

    @Test
    fun `a snapshot exactly at the horizon is accepted and one second older is stale`() {
        val atHorizon = now.minus(DirectBootReplayPolicy.MAX_AGE)
        val overHorizon = atHorizon.minusSeconds(1)

        assertTrue(
            DirectBootReplayPolicy.decide(snapshot(updatedAt = atHorizon), now) is DirectBootReplayDecision.Restore,
        )
        assertEquals(
            DirectBootReplayDecision.Skip(DirectBootTraceReason.STALE),
            DirectBootReplayPolicy.decide(snapshot(updatedAt = overHorizon), now),
        )
    }

    @Test
    fun `an updatedAt within five minutes ahead is tolerated and beyond that is unreadable`() {
        val tolerated = now.plusSeconds(5 * 60)
        val corrupted = tolerated.plusSeconds(1)

        assertTrue(
            DirectBootReplayPolicy.decide(snapshot(updatedAt = tolerated), now) is DirectBootReplayDecision.Restore,
        )
        assertEquals(
            DirectBootReplayDecision.Skip(DirectBootTraceReason.UNREADABLE),
            DirectBootReplayPolicy.decide(snapshot(updatedAt = corrupted), now),
        )
    }

    @Test
    fun `a record with a past moment is skipped but the rest is restored`() {
        val past = alarm(id = "intake-past", at = now.minusSeconds(1))
        val future = alarm(id = "intake-future", at = now.plusSeconds(3600))

        val decision = DirectBootReplayPolicy.decide(snapshot(alarms = listOf(past, future)), now) as
            DirectBootReplayDecision.Restore

        assertEquals(listOf(future), decision.alarms)
        assertEquals(1, decision.skipped)
    }

    @Test
    fun `an empty intake id and an out-of-range attempt are skipped`() {
        val emptyId = alarm(id = "", at = now.plusSeconds(3600))
        val tooManyAttempts = alarm(id = "intake-repeats", at = now.plusSeconds(3600), attempt = 3)
        val valid = alarm(id = "intake-valid", at = now.plusSeconds(3600), attempt = 2)

        val decision = DirectBootReplayPolicy.decide(
            snapshot(alarms = listOf(emptyId, tooManyAttempts, valid)),
            now,
        ) as DirectBootReplayDecision.Restore

        assertEquals(listOf(valid), decision.alarms)
        assertEquals(2, decision.skipped)
    }

    @Test
    fun `attempt zero and the repeat limit are both accepted`() {
        val first = alarm(id = "intake-first", attempt = 0)
        val lastRepeat = alarm(id = "intake-last", attempt = PersistentRepeatPolicy.MAX_REPEATS)

        val decision = DirectBootReplayPolicy.decide(
            snapshot(alarms = listOf(first, lastRepeat)),
            now,
        ) as DirectBootReplayDecision.Restore

        assertEquals(listOf(first, lastRepeat), decision.alarms)
        assertEquals(0, decision.skipped)
    }

    @Test
    fun `a wholly valid snapshot restores everything with nothing skipped`() {
        val alarms = listOf(
            alarm(id = "intake-a", at = now.plusSeconds(60)),
            alarm(id = "intake-b", at = now.plusSeconds(120), attempt = 1),
        )

        val decision = DirectBootReplayPolicy.decide(snapshot(alarms = alarms), now) as
            DirectBootReplayDecision.Restore

        assertEquals(alarms, decision.alarms)
        assertEquals(0, decision.skipped)
        assertFalse(decision.alarms.isEmpty())
    }
}
