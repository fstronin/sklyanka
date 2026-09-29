package ru.stohastika.sklyanka.domain.medication

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.stohastika.sklyanka.domain.model.ArchiveReason
import ru.stohastika.sklyanka.domain.schedule.FakeMedicationRepository
import ru.stohastika.sklyanka.domain.schedule.RecordingOperationLog

/** Тумблер «Напоминать на часах» (ЧАС-15, ЧАС-17, ЧАС-19). */
class ToggleWatchMirrorUseCaseTest {

    private val clock = Cards.clock()
    private val medications = FakeMedicationRepository(clock)
    private val log = RecordingOperationLog()
    private val toggle = ToggleWatchMirrorUseCase(medications, log)

    @Test
    fun `turning the mirror off writes the value`() = runTest {
        medications.put(Cards.scheduled(mirrorToWatch = true))

        val outcome = toggle(Cards.MEDICATION_ID, enabled = false)

        assertEquals(WatchMirrorOutcome.Applied(before = true, after = false), outcome)
        assertEquals(false, medications.stored.getValue(Cards.MEDICATION_ID).mirrorToWatch)
    }

    @Test
    fun `turning the mirror on writes the value`() = runTest {
        medications.put(Cards.scheduled(mirrorToWatch = false))

        val outcome = toggle(Cards.MEDICATION_ID, enabled = true)

        assertEquals(WatchMirrorOutcome.Applied(before = false, after = true), outcome)
        assertEquals(true, medications.stored.getValue(Cards.MEDICATION_ID).mirrorToWatch)
    }

    @Test
    fun `the switch of one medication leaves the others alone`() = runTest {
        medications.put(Cards.scheduled(mirrorToWatch = true))
        medications.put(Cards.scheduled(id = Cards.OTHER_MEDICATION_ID, mirrorToWatch = true))

        toggle(Cards.MEDICATION_ID, enabled = false)

        assertEquals(true, medications.stored.getValue(Cards.OTHER_MEDICATION_ID).mirrorToWatch)
    }

    @Test
    fun `setting the value it already has writes nothing`() = runTest {
        medications.put(Cards.scheduled(mirrorToWatch = true))

        val outcome = toggle(Cards.MEDICATION_ID, enabled = true)

        assertEquals(WatchMirrorOutcome.Applied(before = true, after = true), outcome)
        assertTrue(log.records.isEmpty(), "an unchanged value is not an event")
    }

    @Test
    fun `a medication taken as needed has nothing to mirror`() = runTest {
        medications.put(Cards.asNeeded())

        // ЧАС-17: у приёма по потребности нет запланированного времени, отправлять нечего.
        assertEquals(WatchMirrorOutcome.NotApplicable, toggle(Cards.MEDICATION_ID, enabled = true))
    }

    @Test
    fun `an archived course has no reminders to mirror`() = runTest {
        medications.put(
            Cards.scheduled(
                archivedAt = Cards.instant(Cards.TODAY.atTime(Cards.MORNING)),
                archiveReason = ArchiveReason.MANUAL,
            ),
        )

        assertEquals(WatchMirrorOutcome.Archived, toggle(Cards.MEDICATION_ID, enabled = true))
    }

    @Test
    fun `an unknown medication is reported`() = runTest {
        assertEquals(WatchMirrorOutcome.UnknownMedication, toggle("no-such-id", enabled = true))
    }

    @Test
    fun `the log records the field name and both values, never the medication name`() = runTest {
        medications.put(Cards.scheduled(name = "med-secret", mirrorToWatch = true))

        toggle(Cards.MEDICATION_ID, enabled = false)

        val record = log.records.single()
        assertEquals("mirror_to_watch", record.detail)
        assertEquals("true", record.before)
        assertEquals("false", record.after)
        assertTrue(
            log.records.none { entry ->
                listOfNotNull(entry.before, entry.after, entry.detail).any { value -> "med-secret" in value }
            },
            "the log carries identifiers only (NFR-19)",
        )
    }
}
