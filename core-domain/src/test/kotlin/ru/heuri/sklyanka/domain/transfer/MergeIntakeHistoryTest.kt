package ru.heuri.sklyanka.domain.transfer

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import ru.heuri.sklyanka.domain.model.Intake
import ru.heuri.sklyanka.domain.model.IntakeStatus
import java.time.Instant
import java.time.LocalDateTime

/**
 * Разрешение дублей истории (ПЕР-23, критерий 9).
 *
 * Правило проверяется на всех парах статусов, а не на одной: пропущенная пара означает
 * либо удвоенную запись в истории, либо потерянную отметку, и то и другое человек увидит
 * как неверную историю приёма лекарства.
 */
class MergeIntakeHistoryTest {

    private val merge = MergeIntakeHistory()

    @Test
    @DisplayName("A record the device does not have is inserted")
    fun newHistoryIsInserted() {
        val result = merge(existing = emptyList(), incoming = listOf(Transfers.taken("f1")))

        assertEquals(1, result.inserted.size)
        assertTrue(result.updated.isEmpty())
        assertEquals(0, result.skipped)
    }

    @Test
    @DisplayName("The same slot is never doubled: the device mark wins over the file pending")
    fun deviceMarkWinsOverFilePending() {
        val mine = Transfers.taken("device-1", actedAt = ACTED_AT_0805)
        val theirs = Transfers.pending("file-1")

        val result = merge(existing = listOf(mine), incoming = listOf(theirs))

        assertTrue(result.inserted.isEmpty())
        assertTrue(result.updated.isEmpty())
        assertEquals(1, result.skipped)
    }

    @Test
    @DisplayName("A definite outcome from the file replaces the device pending, keeping the device id")
    fun fileMarkReplacesDevicePending() {
        val mine = Transfers.pending("device-1")
        val theirs = Transfers.taken("file-1", actedAt = ACTED_AT_0805)

        val result = merge(existing = listOf(mine), incoming = listOf(theirs))

        val updated = result.updated.single()
        assertEquals("device-1", updated.id)
        assertEquals(IntakeStatus.TAKEN, updated.status)
        assertEquals(ACTED_AT_0805, updated.actedAt)
    }

    @Test
    @DisplayName("On equal outcomes the earlier mark wins")
    fun earlierMarkWins() {
        val mine = Transfers.taken("device-1", actedAt = ACTED_AT_0805)
        val theirs = Transfers.taken("file-1", actedAt = ACTED_AT_0803)

        val result = merge(existing = listOf(mine), incoming = listOf(theirs))

        assertEquals(ACTED_AT_0803, result.updated.single().actedAt)
    }

    @Test
    @DisplayName("Equal outcome and equal mark change nothing")
    fun equalRecordsChangeNothing() {
        val mine = Transfers.taken("device-1", actedAt = ACTED_AT_0805)
        val theirs = Transfers.taken("file-1", actedAt = ACTED_AT_0805)

        val result = merge(existing = listOf(mine), incoming = listOf(theirs))

        assertTrue(result.updated.isEmpty())
        assertEquals(1, result.skipped)
    }

    @Test
    @DisplayName("A marked record beats an unmarked one even when both outcomes are indefinite")
    fun missedBeatsPending() {
        val mine = Transfers.pending("device-1")
        val theirs = Transfers.missed("file-1")

        val result = merge(existing = listOf(mine), incoming = listOf(theirs))

        assertEquals(IntakeStatus.MISSED, result.updated.single().status)
    }

    @ParameterizedTest(name = "{0} on the device against {1} in the file wins: {2}")
    @CsvSource(
        "TAKEN, MISSED, TAKEN",
        "SKIPPED, MISSED, SKIPPED",
        "MISSED, TAKEN, TAKEN",
        "MISSED, SKIPPED, SKIPPED",
        "PENDING, TAKEN, TAKEN",
        "PENDING, SKIPPED, SKIPPED",
        "PENDING, MISSED, MISSED",
        "TAKEN, PENDING, TAKEN",
        "SKIPPED, PENDING, SKIPPED",
        "MISSED, PENDING, MISSED",
    )
    fun everyPairOfStatusesResolvesToTheDefiniteOutcome(mine: String, theirs: String, winner: String) {
        val device = withStatus("device-1", IntakeStatus.valueOf(mine))
        val file = withStatus("file-1", IntakeStatus.valueOf(theirs))

        val result = merge(existing = listOf(device), incoming = listOf(file))

        val resolved = result.updated.singleOrNull()?.status ?: device.status
        assertEquals(IntakeStatus.valueOf(winner), resolved)
        assertTrue(result.inserted.isEmpty(), "the slot must not be doubled")
    }

    @Test
    @DisplayName("Two records of the same slot inside one file are resolved between themselves")
    fun duplicatesInsideOneFileAreResolved() {
        val pending = Transfers.pending("file-1")
        val taken = Transfers.taken("file-2", actedAt = ACTED_AT_0805)

        val result = merge(existing = emptyList(), incoming = listOf(pending, taken))

        val inserted = result.inserted.single()
        assertEquals(IntakeStatus.TAKEN, inserted.status)
        assertEquals("file-1", inserted.id)
        assertEquals(1, result.skipped)
    }

    @Test
    @DisplayName("Different slots of the same medication are separate records")
    fun differentSlotsStaySeparate() {
        val morning = Transfers.taken("f1", at = LocalDateTime.of(2026, 10, 5, 8, 0))
        val evening = Transfers.taken("f2", at = LocalDateTime.of(2026, 10, 5, 20, 0))

        val result = merge(existing = emptyList(), incoming = listOf(morning, evening))

        assertEquals(2, result.inserted.size)
    }

    private fun withStatus(id: String, status: IntakeStatus): Intake = when (status) {
        IntakeStatus.PENDING -> Transfers.pending(id)
        IntakeStatus.TAKEN -> Transfers.taken(id, actedAt = ACTED_AT_0805)
        IntakeStatus.SKIPPED -> Transfers.skipped(id, actedAt = ACTED_AT_0805)
        IntakeStatus.MISSED -> Transfers.missed(id, actedAt = ACTED_AT_0805)
    }

    private companion object {
        val ACTED_AT_0805: Instant = Instant.parse("2026-10-05T05:05:00Z")
        val ACTED_AT_0803: Instant = Instant.parse("2026-10-05T05:03:00Z")
    }
}
