package ru.heuri.sklyanka.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.heuri.sklyanka.data.log.RoomOperationLog
import ru.heuri.sklyanka.data.room.DatabaseTestRule
import ru.heuri.sklyanka.data.room.TestData
import ru.heuri.sklyanka.domain.log.OpEvent
import ru.heuri.sklyanka.domain.model.IntakeStatus
import ru.heuri.sklyanka.domain.stock.RefillOutcome
import ru.heuri.sklyanka.domain.stock.RefillStockUseCase
import ru.heuri.sklyanka.domain.stock.StockWarningForProfileUseCase
import ru.heuri.sklyanka.domain.time.FixedTimeSource
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId

/**
 * Пополнение остатка и предупреждение о покупке на настоящей базе (SKL-12).
 *
 * Правило проверяется модульными тестами домена; здесь проверяется то, чего в домене
 * не видно: что пополнение действительно долетает до строки лекарства, не трогает
 * историю приёмов и что выборка предупреждения ограничена активным профилем.
 *
 * Прогон требует устройства или эмулятора; в контейнере сборки тест компилируется,
 * но не выполняется.
 */
@RunWith(AndroidJUnit4::class)
class StockRefillTest {

    @get:Rule
    val db = DatabaseTestRule()

    private val clock = FixedTimeSource(MOMENT, ZoneId.of("Europe/Moscow"))

    private val log by lazy { RoomOperationLog(db.opLog, clock) }
    private val medications by lazy { MedicationRepositoryImpl(db.medications, clock, log) }
    private val refill by lazy { RefillStockUseCase(medications, log) }
    private val warnings by lazy { StockWarningForProfileUseCase(medications, clock, log) }

    @Before
    fun fillProfilesAndMedications() = runTest {
        db.profiles.insert(TestData.owner())
        db.profiles.insert(TestData.relative())
        db.medications.upsert(
            TestData.medication(stockLeft = "9").copy(times = "08:00,20:00", stockThresholdDays = THRESHOLD_DAYS),
        )
    }

    @Test
    fun refillingReplacesTheValueInTheRow() = runTest {
        val outcome = refill(TestData.OWNER_MEDICATION_ID, BigDecimal("60"))

        assertEquals(RefillOutcome.Applied(BigDecimal("9"), BigDecimal("60")), outcome)
        assertEquals("60", db.medications.getById(TestData.OWNER_MEDICATION_ID)?.stockLeft)
    }

    @Test
    fun refillingLeavesTheHistoryOfIntakesUntouched() = runTest {
        db.intakes.insertAll(
            listOf(TestData.scheduledIntake(id = INTAKE_ID, status = IntakeStatus.TAKEN, actedAt = MOMENT.toEpochMilli())),
        )
        val before = db.intakes.getById(INTAKE_ID)

        refill(TestData.OWNER_MEDICATION_ID, BigDecimal("60"))

        assertEquals(before, db.intakes.getById(INTAKE_ID))
    }

    @Test
    fun refillingLeavesATraceWithTheValuesBeforeAndAfter() = runTest {
        refill(TestData.OWNER_MEDICATION_ID, BigDecimal("60"))

        val record = log.recent(RECENT).first { it.event == OpEvent.STOCK_REFILLED }
        assertEquals("9", record.before)
        assertEquals("60", record.after)
        assertEquals(TestData.OWNER_ID, record.profileId)
    }

    @Test
    fun aRefusedRefillChangesNothing() = runTest {
        val outcome = refill(TestData.OWNER_MEDICATION_ID, BigDecimal("-1"))

        assertEquals(RefillOutcome.NegativeValue, outcome)
        assertEquals("9", db.medications.getById(TestData.OWNER_MEDICATION_ID)?.stockLeft)
    }

    @Test
    fun theWarningOfTheProfileIsTheMostUrgentOne() = runTest {
        db.medications.upsert(
            TestData.medication(id = SECOND_MEDICATION_ID, name = "med-b", stockLeft = "2")
                .copy(times = "08:00", stockThresholdDays = THRESHOLD_DAYS),
        )

        val warning = warnings(TestData.OWNER_ID).first()

        assertEquals(SECOND_MEDICATION_ID, warning?.medicationId)
        assertEquals(2, warning?.daysLeft)
    }

    @Test
    fun theWarningOfAnotherProfileNeverLeaksIntoThisOne() = runTest {
        db.medications.upsert(
            TestData.medication(
                id = TestData.RELATIVE_MEDICATION_ID,
                profileId = TestData.RELATIVE_ID,
                name = "med-c",
                stockLeft = "1",
            ).copy(times = "08:00", stockThresholdDays = THRESHOLD_DAYS),
        )

        assertEquals(TestData.OWNER_MEDICATION_ID, warnings(TestData.OWNER_ID).first()?.medicationId)
        assertEquals(TestData.RELATIVE_MEDICATION_ID, warnings(TestData.RELATIVE_ID).first()?.medicationId)
    }

    @Test
    fun aRefillAboveTheThresholdRemovesTheWarning() = runTest {
        assertNotNull(warnings(TestData.OWNER_ID).first())

        refill(TestData.OWNER_MEDICATION_ID, BigDecimal("60"))

        assertNull(warnings(TestData.OWNER_ID).first())
    }

    @Test
    fun aMedicationWithoutStockTrackingNeverWarns() = runTest {
        db.medications.setStock(TestData.OWNER_MEDICATION_ID, null)

        assertNull(warnings(TestData.OWNER_ID).first())
        assertTrue(db.medications.getById(TestData.OWNER_MEDICATION_ID)?.stockLeft == null)
    }

    private companion object {
        val MOMENT: Instant = Instant.parse("2026-09-25T16:00:00Z")
        const val INTAKE_ID = "66666666-6666-4666-8666-666666666666"
        const val SECOND_MEDICATION_ID = "77777777-7777-4777-8777-777777777777"
        const val THRESHOLD_DAYS = 5
        const val RECENT = 20
    }
}
