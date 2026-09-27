package ru.heuri.sklyanka.data.transfer

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.heuri.sklyanka.data.assertRefused
import ru.heuri.sklyanka.data.room.DatabaseTestRule
import ru.heuri.sklyanka.data.room.SklyankaTransactor
import ru.heuri.sklyanka.data.room.TestData
import ru.heuri.sklyanka.domain.model.ArchiveReason
import ru.heuri.sklyanka.domain.model.FoodRelation
import ru.heuri.sklyanka.domain.model.Intake
import ru.heuri.sklyanka.domain.model.IntakeSource
import ru.heuri.sklyanka.domain.model.IntakeStatus
import ru.heuri.sklyanka.domain.model.Medication
import ru.heuri.sklyanka.domain.model.MedicationForm
import ru.heuri.sklyanka.domain.model.Profile
import ru.heuri.sklyanka.domain.model.ScheduleMode
import ru.heuri.sklyanka.domain.transfer.AppStamp
import ru.heuri.sklyanka.domain.transfer.BuildTransferPlan
import ru.heuri.sklyanka.domain.transfer.MatchDecision
import ru.heuri.sklyanka.domain.transfer.TransferIdSource
import ru.heuri.sklyanka.domain.transfer.TransferPlan
import ru.heuri.sklyanka.domain.transfer.TransferSettings
import ru.heuri.sklyanka.domain.transfer.TransferSnapshot
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Исполнение плана переноса одной транзакцией (ПЕР-27, ПЕР-16, ПЕР-9, ИНВ-1).
 *
 * ПРОГОН НЕ ВЫПОЛНЯЛСЯ: эмулятора и устройства в среде задачи нет. Тест обязан
 * компилироваться (`assembleDebugAndroidTest`), прогон — на эталонном устройстве.
 *
 * Именно здесь проверяется главное свойство загрузки: отказ на любом шаге оставляет базу
 * в исходном состоянии. Частично применённая загрузка недопустима не потому, что это
 * некрасиво, а потому, что человек остаётся с историей, о достоверности которой он ничего
 * не знает.
 */
@RunWith(AndroidJUnit4::class)
class RoomTransferStoreTest {

    @get:Rule
    val db = DatabaseTestRule()

    private val store by lazy {
        RoomTransferStore(
            profiles = db.profiles,
            medications = db.medications,
            intakes = db.intakes,
            transfer = db.database.transferDao(),
            transactor = SklyankaTransactor(db.database),
        )
    }

    private val ids = object : TransferIdSource {
        private var next = 0
        override fun newId(): String = "new-${++next}"
    }

    @Before
    fun fillInitialState() = runTest {
        db.profiles.insert(TestData.owner())
        db.medications.upsert(TestData.medication())
        db.intakes.insertAll(listOf(TestData.scheduledIntake(id = DEVICE_INTAKE_ID)))
    }

    @Test
    fun theWholeStateIsReadProfileByProfile() = runTest {
        db.profiles.insert(TestData.relative())
        db.medications.upsert(
            TestData.medication(
                id = TestData.RELATIVE_MEDICATION_ID,
                profileId = TestData.RELATIVE_ID,
                archivedAt = ARCHIVED_AT,
                archiveReason = ArchiveReason.COURSE_FINISHED,
            ),
        )

        val state = store.readAll()

        assertEquals(2, state.profiles.size)
        assertEquals(2, state.medications.size)
        // Архив — состояние записи, а не отдельный раздел (ПЕР-10).
        assertEquals(1, state.medications.count { it.isArchived })
        assertEquals(1, state.intakes.size)
    }

    @Test
    fun replacingEverythingKeepsTheIdentifiersOfTheFile() = runTest {
        val plan = BuildTransferPlan(ids).replaceAll(fileSnapshot())

        store.apply(plan)

        val state = store.readAll()
        assertEquals(listOf(FILE_PROFILE_ID), state.profiles.map { it.id })
        assertEquals(listOf(FILE_MEDICATION_ID), state.medications.map { it.id })
        assertEquals(listOf(FILE_INTAKE_ID), state.intakes.map { it.id })
    }

    @Test
    fun anInterruptedApplyLeavesTheOriginalState() = runTest {
        // План, который база отвергнет: приём ссылается на лекарство, которого в плане нет.
        // Внешний ключ остановит транзакцию на середине — ровно то, чего от него ждут.
        val broken = BuildTransferPlan(ids).replaceAll(fileSnapshot()).copy(
            intakesToInsert = listOf(fileIntake().copy(medicationId = "nowhere")),
        )

        assertRefused<Exception> { store.apply(broken) }

        val state = store.readAll()
        assertEquals(1, state.profiles.size)
        assertEquals(TestData.OWNER_MEDICATION_ID, state.medications.single().id)
        assertEquals(DEVICE_INTAKE_ID, state.intakes.single().id)
    }

    @Test
    fun mergingNeverOverwritesADeviceRecordWithAFileRecordOfTheSameIdentifier() = runTest {
        val colliding = fileSnapshot(
            medicationId = TestData.OWNER_MEDICATION_ID,
            medicationName = "med-b",
        )
        val plan = BuildTransferPlan(ids).merge(store.readAll(), colliding, decisions = emptyMap())

        store.apply(plan)

        val state = store.readAll()
        assertEquals(2, state.medications.size)
        assertTrue(
            "the device record must keep its own name",
            state.medications.any { it.id == TestData.OWNER_MEDICATION_ID && it.name == "med-a" },
        )
    }

    @Test
    fun takingAMedicationFromTheFileUpdatesTheDeviceRowInPlace() = runTest {
        val file = fileSnapshot(medicationId = "file-med", medicationName = "med-a", times = listOf(EVENING))
        val device = store.readAll()
        val plan = BuildTransferPlan(ids).merge(
            device,
            file,
            decisions = mapOf("file-med" to MatchDecision.TAKE_FROM_FILE),
        )

        store.apply(plan)

        val state = store.readAll()
        assertEquals(1, state.medications.size)
        assertEquals(TestData.OWNER_MEDICATION_ID, state.medications.single().id)
        assertEquals(listOf(EVENING), state.medications.single().times)
    }

    @Test
    fun aPlanLeavingNoSelfProfileIsRefusedAndRolledBack() = runTest {
        val withoutSelf = fileSnapshot().let { snapshot ->
            snapshot.copy(profiles = snapshot.profiles.map { it.copy(isSelf = false) })
        }
        val plan: TransferPlan = BuildTransferPlan(ids).replaceAll(withoutSelf)

        assertRefused<Exception> { store.apply(plan) }

        assertNotNull("INV-1 must hold after a rollback", db.profiles.getSelf())
    }

    @Test
    fun theCountsBeforeAndAfterAreReportedByTheStore() = runTest {
        val applied = store.apply(BuildTransferPlan(ids).replaceAll(fileSnapshot()))

        assertEquals(1, applied.profilesBefore)
        assertEquals(1, applied.medicationsBefore)
        assertEquals(1, applied.intakesBefore)
        assertEquals(1, applied.profilesAfter)
        assertEquals(1, applied.medicationsAfter)
        assertEquals(1, applied.intakesAfter)
    }

    private fun fileSnapshot(
        medicationId: String = FILE_MEDICATION_ID,
        medicationName: String = "file-med-a",
        times: List<LocalTime> = listOf(MORNING),
    ): TransferSnapshot = TransferSnapshot.of(
        exportedAt = EXPORTED_AT,
        app = AppStamp(versionName = "1.0", versionCode = 10),
        profiles = listOf(
            Profile(id = FILE_PROFILE_ID, name = "file-owner", color = 1, isSelf = true, mirrorReminders = false),
        ),
        medications = listOf(fileMedication(medicationId, medicationName, times)),
        intakes = listOf(fileIntake().copy(medicationId = medicationId)),
        settings = TransferSettings.DEFAULT,
    )

    private fun fileMedication(id: String, name: String, times: List<LocalTime>) = Medication(
        id = id,
        profileId = FILE_PROFILE_ID,
        name = name,
        form = MedicationForm.TABLET,
        shape = null,
        doseAmount = BigDecimal("1"),
        doseUnit = "unit",
        strength = null,
        strengthUnit = null,
        foodRelation = FoodRelation.ANY,
        scheduleMode = ScheduleMode.SCHEDULED,
        maxPerDay = null,
        times = times,
        startDate = LocalDate.of(2026, 9, 1),
        endDate = null,
        stockLeft = null,
        stockThresholdDays = null,
        archivedAt = null,
        archiveReason = null,
    )

    private fun fileIntake() = Intake(
        id = FILE_INTAKE_ID,
        medicationId = FILE_MEDICATION_ID,
        scheduledAt = LocalDateTime.of(2026, 9, 24, 8, 0),
        status = IntakeStatus.TAKEN,
        actedAt = EXPORTED_AT,
        source = IntakeSource.PHONE,
        actorProfileId = null,
    )

    private companion object {
        const val DEVICE_INTAKE_ID = "aaaaaaaa-0000-4000-8000-000000000001"
        const val FILE_PROFILE_ID = "file-profile"
        const val FILE_MEDICATION_ID = "file-medication"
        const val FILE_INTAKE_ID = "file-intake"
        const val ARCHIVED_AT = 1_790_000_000_000L

        val EXPORTED_AT: Instant = Instant.parse("2026-09-25T08:05:00Z")
        val MORNING: LocalTime = LocalTime.of(8, 0)
        val EVENING: LocalTime = LocalTime.of(20, 0)
    }
}
