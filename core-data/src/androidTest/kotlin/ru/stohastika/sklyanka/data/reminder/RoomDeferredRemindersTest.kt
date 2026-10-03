package ru.stohastika.sklyanka.data.reminder

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import ru.stohastika.sklyanka.data.room.SklyankaDatabase
import ru.stohastika.sklyanka.data.room.TestData
import ru.stohastika.sklyanka.domain.reminder.port.DeferredReminder
import java.time.Instant

/**
 * Отложенные срабатывания на настоящей базе (SKL-35, ОВ-75).
 *
 * Прогон требует устройства или эмулятора; в контейнере сборки тест компилируется,
 * но не выполняется — поэтому хранилище остаётся проверенным до прогона на устройстве.
 */
@RunWith(AndroidJUnit4::class)
class RoomDeferredRemindersTest {

    private lateinit var database: SklyankaDatabase
    private lateinit var deferred: RoomDeferredReminders

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            SklyankaDatabase::class.java,
        ).build()
        deferred = RoomDeferredReminders(database.deferredReminderDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun aDeferredReminderSurvivesTheRoundTrip() = runTest {
        seedIntake(INTAKE_ID)

        deferred.put(DeferredReminder(INTAKE_ID, FIRE_AT, attempt = 1))

        assertEquals(listOf(DeferredReminder(INTAKE_ID, FIRE_AT, 1)), deferred.all())
    }

    @Test
    fun puttingTheSameIntakeAgainReplacesTheRecord() = runTest {
        seedIntake(INTAKE_ID)

        deferred.put(DeferredReminder(INTAKE_ID, FIRE_AT, attempt = 0))
        deferred.put(DeferredReminder(INTAKE_ID, FIRE_AT.plusSeconds(60), attempt = 1))

        assertEquals(listOf(DeferredReminder(INTAKE_ID, FIRE_AT.plusSeconds(60), 1)), deferred.all())
    }

    /**
     * Чтение одной записи (SKL-88): обработке срабатывания нужен ответ «это срабатывание
     * отложенное?» — по нему она отличает обычное от ручного «Отложить» и повтора.
     */
    @Test
    fun findReturnsTheRecordOfThatIntakeOnly() = runTest {
        seedIntake(INTAKE_ID)
        val secondInserted = database.intakeDao()
            .insertAll(listOf(TestData.scheduledIntake(SECOND_INTAKE_ID, scheduledAt = TestData.LATE_EVENING)))

        assertTrue("the second intake must be inserted", secondInserted.none { it == -1L })

        assertNull("nothing was deferred yet", deferred.find(INTAKE_ID))

        deferred.put(DeferredReminder(SECOND_INTAKE_ID, FIRE_AT, attempt = 0))

        assertNull("another intake has no record", deferred.find(INTAKE_ID))
        assertEquals(DeferredReminder(SECOND_INTAKE_ID, FIRE_AT, 0), deferred.find(SECOND_INTAKE_ID))
    }

    @Test
    fun removeTellsWhetherThereWasARecord() = runTest {
        seedIntake(INTAKE_ID)

        assertFalse("nothing was deferred yet", deferred.remove(INTAKE_ID))

        deferred.put(DeferredReminder(INTAKE_ID, FIRE_AT, attempt = 0))
        assertTrue("the record must be found and removed", deferred.remove(INTAKE_ID))
        assertTrue("the record must be gone", deferred.all().isEmpty())
    }

    /** Приём — родитель записи: без него вставка обязана упасть по внешнему ключу. */
    private suspend fun seedIntake(intakeId: String) {
        database.profileDao().insert(TestData.owner())
        database.medicationDao().upsert(TestData.medication())
        database.intakeDao().insertAll(listOf(TestData.scheduledIntake(intakeId)))
    }

    private companion object {
        const val INTAKE_ID = "intake-1"
        const val SECOND_INTAKE_ID = "intake-2"

        val FIRE_AT: Instant = Instant.parse("2026-09-25T11:16:00Z")
    }
}
