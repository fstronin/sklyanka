package ru.stohastika.sklyanka.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.stohastika.sklyanka.data.assertRefused
import ru.stohastika.sklyanka.data.log.RoomOperationLog
import ru.stohastika.sklyanka.data.room.DatabaseTestRule
import ru.stohastika.sklyanka.data.room.SklyankaTransactor
import ru.stohastika.sklyanka.data.room.TestData
import ru.stohastika.sklyanka.domain.log.OpEvent
import ru.stohastika.sklyanka.domain.model.IntakeSource
import ru.stohastika.sklyanka.domain.model.IntakeStatus
import ru.stohastika.sklyanka.domain.time.FixedTimeSource
import java.time.Instant
import java.time.ZoneId

/**
 * Отметка приёма: идемпотентность, списание остатка и след в журнале.
 *
 * Отметка и списание применяются вместе или не применяются вовсе (критерий 10):
 * не должно остаться ни отметки без списания, ни списания без отметки.
 *
 * Прогон требует устройства или эмулятора; в контейнере сборки тест компилируется,
 * но не выполняется.
 */
@RunWith(AndroidJUnit4::class)
class IntakeMarkingTest {

    @get:Rule
    val db = DatabaseTestRule()

    private val clock = FixedTimeSource(MOMENT, ZoneId.of("Europe/Moscow"))

    private val log by lazy { RoomOperationLog(db.opLog, clock) }
    private val transactor by lazy { SklyankaTransactor(db.database) }
    private val intakes by lazy {
        IntakeRepositoryImpl(db.intakes, db.medications, transactor, clock, log)
    }

    @Before
    fun fillProfileAndMedication() = runTest {
        db.profiles.insert(TestData.owner())
        db.medications.upsert(TestData.medication())
        db.intakes.insertAll(listOf(TestData.scheduledIntake(id = INTAKE_ID)))
    }

    @Test
    fun theFirstMarkingTakesEffectAndDecrementsTheStockOnce() = runTest {
        val applied = intakes.markIfPending(INTAKE_ID, IntakeStatus.TAKEN, IntakeSource.PHONE)

        assertTrue(applied)
        assertEquals("29", db.medications.getById(TestData.OWNER_MEDICATION_ID)?.stockLeft)
    }

    @Test
    fun theSecondMarkingChangesNothingAndDecrementsNothing() = runTest {
        intakes.markIfPending(INTAKE_ID, IntakeStatus.TAKEN, IntakeSource.PHONE)

        val second = intakes.markIfPending(INTAKE_ID, IntakeStatus.SKIPPED, IntakeSource.WATCH)

        assertFalse(second)
        assertEquals("29", db.medications.getById(TestData.OWNER_MEDICATION_ID)?.stockLeft)
        assertEquals(IntakeStatus.TAKEN.name, db.intakes.getById(INTAKE_ID)?.status)
    }

    @Test
    fun anIgnoredMarkingLeavesItsOwnTraceInTheLog() = runTest {
        intakes.markIfPending(INTAKE_ID, IntakeStatus.TAKEN, IntakeSource.PHONE)
        intakes.markIfPending(INTAKE_ID, IntakeStatus.TAKEN, IntakeSource.WATCH)

        val events = log.recent(RECENT_LIMIT).map { it.event }

        assertTrue(OpEvent.INTAKE_MARKED in events)
        assertTrue(OpEvent.STOCK_DECREMENTED in events)
        assertTrue(OpEvent.INTAKE_MARK_IGNORED in events)
    }

    @Test
    fun skippingAnIntakeDoesNotTouchTheStock() = runTest {
        intakes.markIfPending(INTAKE_ID, IntakeStatus.SKIPPED, IntakeSource.PHONE)

        assertEquals("30", db.medications.getById(TestData.OWNER_MEDICATION_ID)?.stockLeft)
    }

    @Test
    fun aFailureInTheMiddleLeavesNeitherTheMarkingNorTheDecrement() = runTest {
        // Отказ хранилища (НФТ-11) в контейнере не воспроизвести — SQLiteFullException
        // требует реально кончившегося места. Проверяется то же свойство транзакции:
        // исключение на любом шаге откатывает всё, что было записано до него.
        assertRefused<IllegalStateException> {
            transactor.inTransaction {
                intakes.markIfPending(INTAKE_ID, IntakeStatus.TAKEN, IntakeSource.PHONE)
                error("storage refused in the middle")
            }
        }

        assertEquals(IntakeStatus.PENDING.name, db.intakes.getById(INTAKE_ID)?.status)
        assertEquals("30", db.medications.getById(TestData.OWNER_MEDICATION_ID)?.stockLeft)
    }

    @Test
    fun theStockNeverGoesBelowZero() = runTest {
        db.medications.setStock(TestData.OWNER_MEDICATION_ID, "0.5")

        intakes.markIfPending(INTAKE_ID, IntakeStatus.TAKEN, IntakeSource.PHONE)

        assertEquals("0", db.medications.getById(TestData.OWNER_MEDICATION_ID)?.stockLeft)
    }

    @Test
    fun theLogCarriesIdentifiersAndNoMedicalData() = runTest {
        intakes.markIfPending(INTAKE_ID, IntakeStatus.TAKEN, IntakeSource.PHONE)

        val records = log.recent(RECENT_LIMIT)
        val written = records.flatMap {
            listOfNotNull(it.profileId, it.medicationId, it.intakeId, it.before, it.after, it.source, it.detail)
        }

        assertTrue(records.isNotEmpty())
        assertTrue("the log must not carry medication names", written.none { it.contains("med-a") })
        assertTrue("the log must not carry profile names", written.none { it.contains("owner") })
        assertTrue("the log must not carry units", written.none { it.contains("unit") })
    }

    private companion object {
        const val INTAKE_ID = "aaaaaaaa-0000-4000-8000-000000000001"
        const val RECENT_LIMIT = 50
        val MOMENT: Instant = Instant.parse("2026-09-25T05:05:00Z")
    }
}
