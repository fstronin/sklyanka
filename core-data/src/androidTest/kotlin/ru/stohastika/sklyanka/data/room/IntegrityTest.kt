package ru.stohastika.sklyanka.data.room

import android.database.sqlite.SQLiteConstraintException
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.stohastika.sklyanka.data.assertRefused

/**
 * Ссылочная целостность и запрет удаления.
 *
 * Внешние ключи стоят с RESTRICT: единственная копия истории не должна исчезать по
 * одному нажатию, а случайное удаление обязано быть громким отказом на этапе
 * разработки, а не тихой потерей у человека (ОВ-7 открыт).
 *
 * Прогон требует устройства или эмулятора; в контейнере сборки тест компилируется,
 * но не выполняется.
 */
@RunWith(AndroidJUnit4::class)
class IntegrityTest {

    @get:Rule
    val db = DatabaseTestRule()

    @Test
    fun aMedicationWithoutItsProfileIsRejected() = runTest {
        assertRefused<SQLiteConstraintException> { db.medications.upsert(TestData.medication(profileId = MISSING_ID)) }
    }

    @Test
    fun anIntakeWithoutItsMedicationIsRejected() = runTest {
        db.profiles.insert(TestData.owner())

        assertRefused<SQLiteConstraintException> {
            db.intakes.insertAll(listOf(TestData.scheduledIntake(id = INTAKE_ID, medicationId = MISSING_ID)))
        }
    }

    @Test
    fun anUnknownActorProfileIsRejected() = runTest {
        db.profiles.insert(TestData.owner())
        db.medications.upsert(TestData.medication())

        assertRefused<SQLiteConstraintException> {
            db.intakes.insertAll(
                listOf(TestData.scheduledIntake(id = INTAKE_ID).copy(actorProfileId = MISSING_ID)),
            )
        }
    }

    @Test
    fun aSecondOwnerProfileIsRejectedByTheUniqueSlot() = runTest {
        db.profiles.insert(TestData.owner())

        assertRefused<SQLiteConstraintException> {
            db.profiles.insert(TestData.relative().copy(isSelf = true, selfSlot = "self"))
        }
        assertEquals(1, db.profiles.count())
    }

    @Test
    fun deletingAProfileThatStillOwnsMedicationsIsRefused() = runTest {
        db.profiles.insert(TestData.owner())
        db.medications.upsert(TestData.medication())

        assertRefused<SQLiteConstraintException> { db.profiles.deleteAll() }
        assertEquals(1, db.profiles.count())
    }

    @Test
    fun deletingAMedicationThatStillHasIntakesIsRefused() = runTest {
        db.profiles.insert(TestData.owner())
        db.medications.upsert(TestData.medication())
        db.intakes.insertAll(listOf(TestData.scheduledIntake(id = INTAKE_ID)))

        assertRefused<SQLiteConstraintException> { db.medications.deleteAll() }
        assertEquals(1, db.medications.countInProfile(TestData.OWNER_ID))
    }

    private companion object {
        const val MISSING_ID = "ffffffff-0000-4000-8000-000000000000"
        const val INTAKE_ID = "aaaaaaaa-0000-4000-8000-000000000001"
    }
}
