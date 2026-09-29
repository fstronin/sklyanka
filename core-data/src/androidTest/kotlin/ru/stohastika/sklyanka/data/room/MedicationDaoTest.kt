package ru.stohastika.sklyanka.data.room

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.stohastika.sklyanka.domain.model.ArchiveReason

/**
 * Лекарства: профильный фильтр, архив и бессрочный курс.
 *
 * Прогон требует устройства или эмулятора; в контейнере сборки тест компилируется,
 * но не выполняется.
 */
@RunWith(AndroidJUnit4::class)
class MedicationDaoTest {

    @get:Rule
    val db = DatabaseTestRule()

    @Test
    fun activeMedicationsOfOneProfileNeverIncludeAnother() = runTest {
        db.profiles.insert(TestData.owner())
        db.profiles.insert(TestData.relative())
        db.medications.upsert(TestData.medication())
        db.medications.upsert(
            TestData.medication(id = TestData.RELATIVE_MEDICATION_ID, profileId = TestData.RELATIVE_ID),
        )

        val ownerMedications = db.medications.observeActive(TestData.OWNER_ID).first()

        assertEquals(listOf(TestData.OWNER_MEDICATION_ID), ownerMedications.map { it.id })
    }

    @Test
    fun anArchivedCourseLeavesTheActiveListAndAppearsInTheArchive() = runTest {
        db.profiles.insert(TestData.owner())
        db.medications.upsert(TestData.medication())

        val changed = db.medications.archive(
            medicationId = TestData.OWNER_MEDICATION_ID,
            at = ARCHIVED_AT,
            reason = ArchiveReason.COURSE_FINISHED.name,
        )

        assertEquals(1, changed)
        assertTrue(db.medications.observeActive(TestData.OWNER_ID).first().isEmpty())
        assertEquals(1, db.medications.observeArchived(TestData.OWNER_ID).first().size)
    }

    @Test
    fun archivingTwiceChangesNothingTheSecondTime() = runTest {
        db.profiles.insert(TestData.owner())
        db.medications.upsert(TestData.medication())
        db.medications.archive(TestData.OWNER_MEDICATION_ID, ARCHIVED_AT, ArchiveReason.MANUAL.name)

        val second = db.medications.archive(
            medicationId = TestData.OWNER_MEDICATION_ID,
            at = ARCHIVED_AT + 1,
            reason = ArchiveReason.MANUAL.name,
        )

        assertEquals(0, second)
    }

    @Test
    fun anOpenEndedCourseIsStoredAndReadAsOpenEnded() = runTest {
        db.profiles.insert(TestData.owner())
        db.medications.upsert(TestData.medication(endDate = null))

        val stored = db.medications.getById(TestData.OWNER_MEDICATION_ID)

        assertNull(requireNotNull(stored).endDate)
    }

    @Test
    fun theStockIsWrittenAsTextAndReadBackUnchanged() = runTest {
        db.profiles.insert(TestData.owner())
        db.medications.upsert(TestData.medication())

        db.medications.setStock(TestData.OWNER_MEDICATION_ID, "29.5")

        assertEquals("29.5", db.medications.getById(TestData.OWNER_MEDICATION_ID)?.stockLeft)
    }

    @Test
    fun updatingACourseThatAlreadyHasHistoryKeepsThatHistory() = runTest {
        // Обновление на месте, а не «удалить и вставить»: у лекарства есть дети-приёмы
        // с ON DELETE RESTRICT, и подмена строки стоила бы истории приёма.
        db.profiles.insert(TestData.owner())
        db.medications.upsert(TestData.medication())
        db.intakes.insertAll(listOf(TestData.scheduledIntake(id = INTAKE_ID)))

        db.medications.upsert(TestData.medication(stockLeft = "20"))

        assertEquals("20", db.medications.getById(TestData.OWNER_MEDICATION_ID)?.stockLeft)
        assertEquals(1, db.intakes.countInProfile(TestData.OWNER_ID))
    }

    private companion object {
        const val ARCHIVED_AT = 1_790_000_000_000L
        const val INTAKE_ID = "aaaaaaaa-0000-4000-8000-000000000001"
    }
}
