package ru.stohastika.sklyanka.domain.transfer

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDateTime

/**
 * План применения: четыре решения по совпадению и судьба истории при каждом (ПЕР-21).
 *
 * Структура тестов повторяет структуру сценария приёмки «Каждое решение по совпадению
 * имеет свой исход»: лекарство, время приёма записи и судьба приёма за 5 октября.
 */
class BuildTransferPlanTest {

    private val ids = FakeIds()
    private val build = BuildTransferPlan(ids)

    /** Устройство: «Магний B6» 48 мг в 08:00 и 20:00. Файл: то же лекарство в 09:00 и 21:00. */
    private val device = Transfers.deviceState(
        profiles = listOf(Transfers.self()),
        medications = listOf(Transfers.magnesium(id = "device-med")),
        intakes = emptyList(),
    )
    private val fileMedication = Transfers.magnesium(
        id = "file-med",
        times = listOf(Transfers.LATE_MORNING, Transfers.LATE_EVENING),
    )
    private val fileIntake = Transfers.taken(
        id = "file-intake",
        medicationId = "file-med",
        at = LocalDateTime.of(2026, 10, 5, 9, 0),
    )
    private val snapshot = Transfers.snapshot(
        profiles = listOf(Transfers.self()),
        medications = listOf(fileMedication),
        intakes = listOf(fileIntake),
    )

    @Test
    @DisplayName("An unmatched medication is added with its schedule and history, without questions")
    fun unmatchedMedicationIsAddedSilently() {
        val file = Transfers.snapshot(
            profiles = listOf(Transfers.self()),
            medications = listOf(Transfers.thyroxine(id = "file-thyroxine")),
            intakes = listOf(Transfers.taken("file-intake", medicationId = "file-thyroxine")),
        )

        val plan = build.merge(device, file, decisions = emptyMap())

        assertEquals(1, plan.medicationsToInsert.size)
        assertEquals(Transfers.THYROXINE, plan.medicationsToInsert.single().name)
        assertEquals(1, plan.intakesToInsert.size)
        assertEquals(0, plan.report.matchesResolved)
    }

    @Test
    @DisplayName("Keep mine: the device record stays, the file history joins it")
    fun keepMineKeepsTheDeviceRecord() {
        val plan = merge(MatchDecision.KEEP_MINE)

        assertTrue(plan.medicationsToInsert.isEmpty())
        assertTrue(plan.medicationsToUpdate.isEmpty())
        assertEquals("device-med", plan.intakesToInsert.single().medicationId)
        assertEquals(mapOf(MatchDecision.KEEP_MINE to 1), plan.report.decisions)
    }

    @Test
    @DisplayName("Keep mine is the decision in force before the person has chosen")
    fun keepMineIsTheDefault() {
        val plan = build.merge(device, snapshot, decisions = emptyMap())

        assertTrue(plan.medicationsToUpdate.isEmpty())
        assertEquals("device-med", plan.intakesToInsert.single().medicationId)
    }

    @Test
    @DisplayName("Take from file: the device record takes the file fields, keeping its id and profile")
    fun takeFromFileUpdatesInPlace() {
        val plan = merge(MatchDecision.TAKE_FROM_FILE)

        val updated = plan.medicationsToUpdate.single()
        assertEquals("device-med", updated.id)
        assertEquals(Transfers.SELF_ID, updated.profileId)
        assertEquals(listOf(Transfers.LATE_MORNING, Transfers.LATE_EVENING), updated.times)
        assertEquals("device-med", plan.intakesToInsert.single().medicationId)
    }

    @Test
    @DisplayName("Add separately: a second medication appears and the file history goes to it")
    fun addSeparatelyCreatesASecondRecord() {
        val plan = merge(MatchDecision.ADD_SEPARATELY)

        val added = plan.medicationsToInsert.single()
        assertEquals("new-1", added.id)
        assertEquals(listOf(Transfers.LATE_MORNING, Transfers.LATE_EVENING), added.times)
        assertTrue(plan.medicationsToUpdate.isEmpty())
        assertEquals("new-1", plan.intakesToInsert.single().medicationId)
    }

    @Test
    @DisplayName("Do not load: nothing changes and the file history is dropped entirely")
    fun doNotLoadDropsTheHistory() {
        val plan = merge(MatchDecision.DO_NOT_LOAD)

        assertTrue(plan.medicationsToInsert.isEmpty())
        assertTrue(plan.medicationsToUpdate.isEmpty())
        assertTrue(plan.intakesToInsert.isEmpty())
        assertEquals(1, plan.report.intakesSkipped)
    }

    @Test
    @DisplayName("Merge reissues ids: a colliding file id never overwrites a device record")
    fun mergeReissuesIdentifiers() {
        val collidingDevice = Transfers.deviceState(
            profiles = listOf(Transfers.self()),
            medications = listOf(Transfers.thyroxine(id = "same-id").copy(name = "Aspirin")),
            intakes = emptyList(),
        )
        val file = Transfers.snapshot(
            profiles = listOf(Transfers.self()),
            medications = listOf(Transfers.thyroxine(id = "same-id")),
            intakes = listOf(Transfers.taken("i1", medicationId = "same-id")),
        )

        val plan = build.merge(collidingDevice, file, decisions = emptyMap())

        val added = plan.medicationsToInsert.single()
        assertTrue(added.id != "same-id", "the device record must keep its own id")
        assertEquals(added.id, plan.intakesToInsert.single().medicationId)
        assertTrue(plan.medicationsToUpdate.isEmpty())
    }

    @Test
    @DisplayName("Merge leaves the active profile alone and never erases")
    fun mergeNeitherErasesNorSwitchesProfile() {
        val plan = build.merge(device, snapshot, decisions = emptyMap())

        assertEquals(false, plan.erase)
        assertNull(plan.activeProfileId)
    }

    @Test
    @DisplayName("A profile absent on the device is added and its medications follow it")
    fun newProfileBringsItsMedications() {
        val father = Transfers.mother(id = "file-father").copy(name = "Father")
        val file = Transfers.snapshot(
            profiles = listOf(Transfers.self(), father),
            medications = listOf(
                Transfers.thyroxine(id = "f1", profileId = "file-father"),
                Transfers.magnesium(id = "f2", profileId = "file-father").copy(name = "Vitamin D"),
            ),
            intakes = emptyList(),
        )

        val plan = build.merge(device, file, decisions = emptyMap())

        val added = plan.profilesToInsert.single()
        assertEquals("Father", added.name)
        assertEquals(2, plan.medicationsToInsert.size)
        assertTrue(plan.medicationsToInsert.all { it.profileId == added.id })
    }

    @Test
    @DisplayName("Replace all keeps the file identifiers and makes the file self profile active")
    fun replaceAllKeepsIdentifiers() {
        val file = Transfers.snapshot(
            profiles = listOf(Transfers.self(id = "file-self"), Transfers.mother(id = "file-mother")),
            medications = listOf(Transfers.magnesium(id = "file-med", profileId = "file-self")),
            intakes = listOf(Transfers.taken("file-intake", medicationId = "file-med")),
        )

        val plan = build.replaceAll(file)

        assertTrue(plan.erase)
        assertEquals("file-self", plan.activeProfileId)
        assertEquals(listOf("file-self", "file-mother"), plan.profilesToInsert.map { it.id })
        assertEquals(listOf("file-med"), plan.medicationsToInsert.map { it.id })
        assertEquals(listOf("file-intake"), plan.intakesToInsert.map { it.id })
    }

    @Test
    @DisplayName("Loaded archived courses are listed for a separate planner pass")
    fun archivedCoursesAreListedForReplanning() {
        val file = Transfers.snapshot(
            profiles = listOf(Transfers.self()),
            medications = listOf(Transfers.magnesium(), Transfers.archivedCourse(id = "archived-1")),
            intakes = emptyList(),
        )

        assertEquals(listOf("archived-1"), build.replaceAll(file).archivedMedicationIds)
    }

    @Test
    @DisplayName("Duplicate history from the file is counted for the report, not silently dropped")
    fun duplicateHistoryIsReported() {
        val withHistory = device.copy(
            intakes = listOf(
                Transfers.taken("device-intake", medicationId = "device-med", at = FILE_SLOT),
            ),
        )
        val plan = build.merge(withHistory, snapshot, mapOf("file-med" to MatchDecision.KEEP_MINE))

        assertEquals(1, plan.report.intakesSkipped)
        assertTrue(plan.report.notices.contains(TransferNotice.DuplicateIntakes(1)))
    }

    @Test
    @DisplayName("An existing profile keeps its colour and mirroring while its data is transferred")
    fun theSettingsOfAnExistingProfileAreNotOverwritten() {
        val deviceMother = Transfers.mother(id = "device-mother", mirrorReminders = true)
        val fileMother = Transfers.mother(id = "file-mother", mirrorReminders = false).copy(color = 1)
        val withMother = Transfers.deviceState(
            profiles = listOf(Transfers.self(), deviceMother),
            medications = emptyList(),
            intakes = emptyList(),
        )
        val file = Transfers.snapshot(
            profiles = listOf(Transfers.self(id = "file-self"), fileMother),
            medications = listOf(Transfers.thyroxine(id = "f1", profileId = "file-mother")),
            intakes = listOf(Transfers.taken("i1", medicationId = "f1")),
        )

        val plan = build.merge(withMother, file, decisions = emptyMap())

        // Существующий профиль в план не попадает вовсе: перетирать его настройки нечем.
        assertTrue(plan.profilesToInsert.isEmpty())
        assertEquals("device-mother", plan.medicationsToInsert.single().profileId)
        assertEquals(1, plan.intakesToInsert.size)
    }

    @Test
    @DisplayName("Keep mine leaves one record of the slot and keeps the device mark")
    fun keepMineLeavesOneRecordOfTheSlot() {
        val deviceMark = Transfers.taken(
            id = "device-intake",
            medicationId = "device-med",
            at = FILE_SLOT,
            actedAt = DEVICE_ACTED_AT,
        )
        val withHistory = device.copy(intakes = listOf(deviceMark))
        val filePending = Transfers.pending("file-intake", medicationId = "file-med", at = FILE_SLOT)
        val file = Transfers.snapshot(
            profiles = listOf(Transfers.self()),
            medications = listOf(fileMedication),
            intakes = listOf(filePending),
        )

        val plan = build.merge(withHistory, file, mapOf("file-med" to MatchDecision.KEEP_MINE))

        assertTrue(plan.intakesToInsert.isEmpty(), "the slot must not be doubled")
        assertTrue(plan.intakesToUpdate.isEmpty(), "the device mark must not be rewritten")
        assertEquals(1, plan.report.intakesSkipped)
    }

    private fun merge(decision: MatchDecision): TransferPlan =
        build.merge(device, snapshot, mapOf("file-med" to decision))

    private companion object {
        val FILE_SLOT: LocalDateTime = LocalDateTime.of(2026, 10, 5, 9, 0)
        val DEVICE_ACTED_AT: Instant = Instant.parse("2026-10-05T06:05:00Z")
    }
}
