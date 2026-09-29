package ru.stohastika.sklyanka.data.repository

import android.database.sqlite.SQLiteConstraintException
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.stohastika.sklyanka.data.assertRefused
import ru.stohastika.sklyanka.data.room.DatabaseTestRule
import ru.stohastika.sklyanka.data.room.SklyankaTransactor
import ru.stohastika.sklyanka.data.room.TestData
import ru.stohastika.sklyanka.data.room.entity.DeferredReminderEntity
import ru.stohastika.sklyanka.data.room.entity.OpLogEntity
import ru.stohastika.sklyanka.domain.log.OpEvent

/**
 * Стирание профиля одной транзакцией (SKL-36, ОВ-7).
 *
 * Проверяется то, что принадлежит базе: порядок «приёмы → лекарства → профиль»,
 * каскад отложенных срабатываний, неприкосновенность владельца и выживание журнала.
 * «Свой профиль нельзя» домена проверяет `DeleteProfileUseCaseTest`; здесь — что
 * и на уровне данных владелец не остаётся без лекарств и истории.
 *
 * Прогон требует устройства или эмулятора; в контейнере сборки тест компилируется,
 * но не выполняется — как соседние проверки `core-data` (МГ-3).
 */
@RunWith(AndroidJUnit4::class)
class ProfileDeletionTest {

    @get:Rule
    val db = DatabaseTestRule()

    private val eraser by lazy {
        RoomProfileEraser(
            profiles = db.profiles,
            medications = db.medications,
            intakes = db.intakes,
            transactor = SklyankaTransactor(db.database),
        )
    }

    @Test
    fun theErasureTakesTheProfileWithItsMedicationsHistoryAndDeferredReminders() = runTest {
        seedBothProfiles()

        eraser.erase(TestData.RELATIVE_ID)

        assertEquals(1, db.profiles.count())
        assertEquals(0, db.medications.countInProfile(TestData.RELATIVE_ID))
        assertEquals(0, db.intakes.countInProfile(TestData.RELATIVE_ID))
        assertTrue(db.database.deferredReminderDao().all().isEmpty())
        // Данные владельца не тронуты: стирание одного профиля не задевает соседа.
        assertEquals(1, db.medications.countInProfile(TestData.OWNER_ID))
        assertEquals(1, db.intakes.countInProfile(TestData.OWNER_ID))
    }

    @Test
    fun theOwnerKeepsEverything() = runTest {
        seedBothProfiles()

        eraser.erase(TestData.OWNER_ID)

        assertEquals(2, db.profiles.count())
        assertEquals(1, db.medications.countInProfile(TestData.OWNER_ID))
        assertEquals(1, db.intakes.countInProfile(TestData.OWNER_ID))
    }

    @Test
    fun anUnknownProfileErasesNothing() = runTest {
        seedBothProfiles()

        eraser.erase(MISSING_ID)

        assertEquals(2, db.profiles.count())
        assertEquals(1, db.medications.countInProfile(TestData.RELATIVE_ID))
        assertEquals(2, db.intakes.countInProfile(TestData.RELATIVE_ID))
    }

    @Test
    fun theOperationLogSurvivesTheErasure() = runTest {
        seedBothProfiles()
        db.opLog.insert(
            OpLogEntity(
                at = MOMENT,
                event = OpEvent.PROFILE_DELETED.name,
                profileId = TestData.RELATIVE_ID,
                medicationId = null,
                intakeId = null,
                valueBefore = null,
                valueAfter = null,
                source = null,
                detail = null,
            ),
        )

        eraser.erase(TestData.RELATIVE_ID)

        assertEquals(1, db.opLog.recent(RECENT_LIMIT).size)
    }

    /**
     * Порядок обязателен: лекарство с приёмами не удаляется (RESTRICT), и попытка
     * стирания в обход порядка — громкий отказ, а не тихая потеря истории (НФТ-13).
     */
    @Test
    fun erasingMedicationsBeforeIntakesIsRefused() = runTest {
        seedBothProfiles()

        assertRefused<SQLiteConstraintException> { db.medications.deleteInProfile(TestData.RELATIVE_ID) }

        assertEquals(2, db.intakes.countInProfile(TestData.RELATIVE_ID))
    }

    private fun seedBothProfiles() = runTest {
        db.profiles.insert(TestData.owner())
        db.profiles.insert(TestData.relative())
        db.medications.upsert(TestData.medication(id = TestData.OWNER_MEDICATION_ID))
        db.medications.upsert(
            TestData.medication(id = TestData.RELATIVE_MEDICATION_ID, profileId = TestData.RELATIVE_ID),
        )
        db.medications.upsert(
            TestData.medication(
                id = RELATIVE_ARCHIVED_MEDICATION_ID,
                profileId = TestData.RELATIVE_ID,
                archivedAt = ARCHIVED_AT,
            ),
        )
        db.intakes.insertAll(listOf(TestData.scheduledIntake(id = OWNER_INTAKE_ID)))
        db.intakes.insertAll(
            listOf(
                TestData.scheduledIntake(id = RELATIVE_INTAKE_ID, medicationId = TestData.RELATIVE_MEDICATION_ID),
                TestData.scheduledIntake(
                    id = RELATIVE_ARCHIVED_INTAKE_ID,
                    medicationId = RELATIVE_ARCHIVED_MEDICATION_ID,
                ),
            ),
        )
        db.database.deferredReminderDao().put(
            DeferredReminderEntity(intakeId = RELATIVE_INTAKE_ID, fireAt = FIRE_AT, attempt = 1),
        )
    }

    private companion object {
        const val MISSING_ID = "ffffffff-0000-4000-8000-000000000000"
        const val RELATIVE_ARCHIVED_MEDICATION_ID = "66666666-6666-4666-8666-666666666666"
        const val OWNER_INTAKE_ID = "aaaaaaaa-0000-4000-8000-000000000001"
        const val RELATIVE_INTAKE_ID = "aaaaaaaa-0000-4000-8000-000000000002"
        const val RELATIVE_ARCHIVED_INTAKE_ID = "aaaaaaaa-0000-4000-8000-000000000003"
        const val ARCHIVED_AT = 1_790_000_000_000L
        const val FIRE_AT = 1_790_000_100_000L
        const val MOMENT = 1_790_000_000_000L
        const val RECENT_LIMIT = 50
    }
}
