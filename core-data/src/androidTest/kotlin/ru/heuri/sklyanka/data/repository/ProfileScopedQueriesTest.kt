package ru.heuri.sklyanka.data.repository

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.heuri.sklyanka.data.log.RoomOperationLog
import ru.heuri.sklyanka.data.prefs.ActiveProfileDataStore
import ru.heuri.sklyanka.data.room.DatabaseTestRule
import ru.heuri.sklyanka.data.room.SklyankaTransactor
import ru.heuri.sklyanka.data.room.TestData
import ru.heuri.sklyanka.domain.time.FixedTimeSource
import java.io.File
import java.time.Instant
import java.time.ZoneId

/**
 * Смена активного профиля меняет состав выборки целиком (критерий 2).
 *
 * Промежуточного состояния, в котором видны приёмы двух профилей, не существует —
 * не потому, что его фильтруют, а потому, что второй запрос не выполняется.
 *
 * Прогон требует устройства или эмулятора; в контейнере сборки тест компилируется,
 * но не выполняется.
 */
@RunWith(AndroidJUnit4::class)
class ProfileScopedQueriesTest {

    @get:Rule
    val db = DatabaseTestRule()

    private val clock = FixedTimeSource(Instant.parse("2026-09-25T05:00:00Z"), ZoneId.of("Europe/Moscow"))
    private lateinit var settingsFile: File
    private lateinit var scoped: ProfileScopedQueries
    private lateinit var active: ActiveProfileRepositoryImpl

    @Before
    fun buildQueries() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        settingsFile = context.preferencesDataStoreFile(SETTINGS_NAME)
        settingsFile.delete()
        val store = ActiveProfileDataStore(PreferenceDataStoreFactory.create { settingsFile })
        val log = RoomOperationLog(db.opLog, clock)

        db.profiles.insert(TestData.owner())
        db.profiles.insert(TestData.relative())
        db.medications.upsert(TestData.medication())
        db.medications.upsert(
            TestData.medication(id = TestData.RELATIVE_MEDICATION_ID, profileId = TestData.RELATIVE_ID),
        )
        db.intakes.insertAll(
            listOf(
                TestData.scheduledIntake(id = OWN_INTAKE),
                TestData.scheduledIntake(id = FOREIGN_INTAKE, medicationId = TestData.RELATIVE_MEDICATION_ID),
            ),
        )

        active = ActiveProfileRepositoryImpl(store, db.profiles, log)
        scoped = ProfileScopedQueries(
            active = active,
            medications = MedicationRepositoryImpl(db.medications, clock, log),
            intakes = IntakeRepositoryImpl(
                db.intakes,
                db.medications,
                SklyankaTransactor(db.database),
                clock,
                log,
            ),
        )
    }

    @After
    fun removeSettings() {
        settingsFile.delete()
    }

    @Test
    fun theOwnerSeesOnlyTheirOwnIntakes() = runTest {
        assertEquals(listOf(OWN_INTAKE), scoped.observeDay(TestData.TODAY).first().map { it.id })
    }

    @Test
    fun switchingTheProfileReplacesTheWholeSelection() = runTest {
        active.switchTo(TestData.RELATIVE_ID)

        assertEquals(listOf(FOREIGN_INTAKE), scoped.observeDay(TestData.TODAY).first().map { it.id })
    }

    @Test
    fun theMedicationListFollowsTheActiveProfileToo() = runTest {
        active.switchTo(TestData.RELATIVE_ID)

        assertEquals(
            listOf(TestData.RELATIVE_MEDICATION_ID),
            scoped.observeActiveMedications().first().map { it.id },
        )
    }

    private companion object {
        const val SETTINGS_NAME = "sklyanka_scoped_test"
        const val OWN_INTAKE = "aaaaaaaa-0000-4000-8000-000000000001"
        const val FOREIGN_INTAKE = "aaaaaaaa-0000-4000-8000-000000000002"
    }
}
