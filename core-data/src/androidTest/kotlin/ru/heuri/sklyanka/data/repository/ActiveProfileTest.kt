package ru.heuri.sklyanka.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.heuri.sklyanka.data.log.RoomOperationLog
import ru.heuri.sklyanka.data.prefs.ActiveProfileDataStore
import ru.heuri.sklyanka.data.room.DatabaseTestRule
import ru.heuri.sklyanka.data.room.TestData
import ru.heuri.sklyanka.domain.log.OpEvent
import ru.heuri.sklyanka.domain.time.FixedTimeSource
import java.io.File
import java.time.Instant
import java.time.ZoneId

/**
 * Активный профиль и первый запуск.
 *
 * Значение живёт в постоянных настройках, поэтому переживает пересоздание объекта —
 * то же, что и перезапуск процесса (ПРФ-17, ПРФ-18, критерий 3).
 *
 * Прогон требует устройства или эмулятора; в контейнере сборки тест компилируется,
 * но не выполняется.
 */
@RunWith(AndroidJUnit4::class)
class ActiveProfileTest {

    @get:Rule
    val db = DatabaseTestRule()

    private val clock = FixedTimeSource(Instant.parse("2026-09-25T05:00:00Z"), ZoneId.of("Europe/Moscow"))
    private lateinit var settingsFile: File
    private lateinit var preferences: DataStore<Preferences>

    @Before
    fun createSettings() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        settingsFile = context.preferencesDataStoreFile(SETTINGS_NAME)
        settingsFile.delete()
        preferences = PreferenceDataStoreFactory.create { settingsFile }
    }

    @After
    fun removeSettings() {
        settingsFile.delete()
    }

    @Test
    fun theFirstRunCreatesExactlyOneOwnerProfile() = runTest {
        val bootstrap = ProfileBootstrap(db.profiles, ActiveProfileDataStore(preferences))

        val owner = bootstrap.ensureOwnerProfile(OWNER_NAME, TestData.OWNER_COLOR)
        bootstrap.ensureOwnerProfile(OWNER_NAME, TestData.OWNER_COLOR)

        assertEquals(1, db.profiles.count())
        assertEquals(owner.id, db.profiles.getSelf()?.id)
    }

    @Test
    fun theFirstRunPointsTheActiveProfileAtTheOwner() = runTest {
        val store = ActiveProfileDataStore(preferences)
        val bootstrap = ProfileBootstrap(db.profiles, store)

        val owner = bootstrap.ensureOwnerProfile(OWNER_NAME, TestData.OWNER_COLOR)

        assertEquals(owner.id, store.read())
    }

    @Test
    fun theActiveProfileSurvivesARestartOfTheProcess() = runTest {
        db.profiles.insert(TestData.owner())
        db.profiles.insert(TestData.relative())
        val log = RoomOperationLog(db.opLog, clock)
        ActiveProfileRepositoryImpl(ActiveProfileDataStore(preferences), db.profiles, log)
            .switchTo(TestData.RELATIVE_ID)

        // Новый объект поверх того же файла настроек — то же, что новый запуск процесса.
        val afterRestart = ActiveProfileRepositoryImpl(
            ActiveProfileDataStore(PreferenceDataStoreFactory.create { settingsFile }),
            db.profiles,
            log,
        )

        assertEquals(TestData.RELATIVE_ID, afterRestart.current())
    }

    @Test
    fun anActiveProfileThatNoLongerExistsFallsBackToTheOwner() = runTest {
        db.profiles.insert(TestData.owner())
        val store = ActiveProfileDataStore(preferences)
        store.write(MISSING_ID)

        val active = ActiveProfileRepositoryImpl(store, db.profiles, RoomOperationLog(db.opLog, clock))

        assertEquals(TestData.OWNER_ID, active.current())
        assertEquals(TestData.OWNER_ID, active.activeProfileId.first())
    }

    /**
     * ПРФ-18, критерий приёмки 13: молчаливого возврата в свой профиль не происходит.
     *
     * Проверяется именно **отсутствие** действия: приложение, открывшееся не там, где его
     * закрыли, — это то же зло, что и отсутствие индикации, только с другой стороны.
     */
    @Test
    fun afterARestartTheForeignProfileStaysActiveAndNothingIsLogged() = runTest {
        db.profiles.insert(TestData.owner())
        db.profiles.insert(TestData.relative())
        val log = RoomOperationLog(db.opLog, clock)
        val store = ActiveProfileDataStore(preferences)
        ActiveProfileRepositoryImpl(store, db.profiles, log).switchTo(TestData.RELATIVE_ID)
        val switchesBefore = log.recent(RECENT).count { it.event == OpEvent.PROFILE_SWITCHED }

        val afterRestart = ActiveProfileRepositoryImpl(
            ActiveProfileDataStore(PreferenceDataStoreFactory.create { settingsFile }),
            db.profiles,
            log,
        )

        assertEquals(TestData.RELATIVE_ID, afterRestart.current())
        assertEquals(switchesBefore, log.recent(RECENT).count { it.event == OpEvent.PROFILE_SWITCHED })
        assertTrue(log.recent(RECENT).none { it.event == OpEvent.PROFILE_ACTIVE_RESTORED })
    }

    @Test
    fun withoutAnyProfileTheActiveProfileIsAnError() = runTest {
        val active = ActiveProfileRepositoryImpl(
            ActiveProfileDataStore(preferences),
            db.profiles,
            RoomOperationLog(db.opLog, clock),
        )

        assertThrows(IllegalStateException::class.java) { runTest { active.current() } }
    }

    private companion object {
        /** Сколько записей журнала читает проверка: событий в сценарии единицы. */
        const val RECENT = 50
        const val SETTINGS_NAME = "sklyanka_settings_test"
        const val OWNER_NAME = "owner"
        const val MISSING_ID = "ffffffff-0000-4000-8000-000000000000"
    }
}
