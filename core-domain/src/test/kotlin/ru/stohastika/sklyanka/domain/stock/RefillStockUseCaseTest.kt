package ru.stohastika.sklyanka.domain.stock

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.stohastika.sklyanka.domain.log.OpEvent
import ru.stohastika.sklyanka.domain.model.Fixtures
import ru.stohastika.sklyanka.domain.model.Medication
import ru.stohastika.sklyanka.domain.schedule.FakeMedicationRepository
import ru.stohastika.sklyanka.domain.schedule.RecordingOperationLog
import ru.stohastika.sklyanka.domain.time.FixedTimeSource
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/**
 * Пополнение остатка человеком — группа `@refill` из `docs/tasks/SKL-12/acceptance.feature`.
 *
 * Поведение временное (ОВ-13): «Пополнить» заменяет значение остатка.
 */
class RefillStockUseCaseTest {

    private val clock = FixedTimeSource(Instant.parse("2026-09-25T16:00:00Z"), ZoneId.of("Europe/Moscow"))
    private val medications = FakeMedicationRepository(clock)
    private val log = RecordingOperationLog()
    private val refill = RefillStockUseCase(medications, log)

    @Test
    fun `refilling replaces the value and clears the warning`() = runTest {
        medications.put(twiceADay(stock = "9"))

        val outcome = refill(Fixtures.MEDICATION_ID, BigDecimal("60"))

        assertEquals(RefillOutcome.Applied(BigDecimal("9"), BigDecimal("60")), outcome)
        assertEquals(BigDecimal("60"), medications.stored.getValue(Fixtures.MEDICATION_ID).stockLeft)
        assertNull(StockWarning.of(medications.stored.getValue(Fixtures.MEDICATION_ID)))
    }

    @Test
    fun `refilling below the threshold keeps the warning`() = runTest {
        medications.put(twiceADay(stock = "0"))

        refill(Fixtures.MEDICATION_ID, BigDecimal("6"))

        val warning = StockWarning.of(medications.stored.getValue(Fixtures.MEDICATION_ID))
        assertNotNull(warning)
        assertEquals(BigDecimal("6"), warning?.unitsLeft)
        assertEquals(3, warning?.daysLeft)
    }

    @Test
    fun `refilling to zero is a legitimate value`() = runTest {
        // Ноль означает «кончилось» и предупреждает; это не то же самое, что «учёт не ведётся».
        medications.put(twiceADay(stock = "9"))

        val outcome = refill(Fixtures.MEDICATION_ID, BigDecimal.ZERO)

        assertTrue(outcome is RefillOutcome.Applied)
        assertNotNull(StockWarning.of(medications.stored.getValue(Fixtures.MEDICATION_ID)))
    }

    @Test
    fun `a negative value is refused and nothing is written`() = runTest {
        medications.put(twiceADay(stock = "9"))

        val outcome = refill(Fixtures.MEDICATION_ID, BigDecimal("-1"))

        assertEquals(RefillOutcome.NegativeValue, outcome)
        assertEquals(BigDecimal("9"), medications.stored.getValue(Fixtures.MEDICATION_ID).stockLeft)
        assertTrue(log.events().isEmpty(), "a refused refill is not a change of the stock")
    }

    @Test
    fun `a missing medication is not an exception`() = runTest {
        assertEquals(RefillOutcome.UnknownMedication, refill("gone", BigDecimal("60")))
    }

    @Test
    fun `the refill leaves a trace with the values before and after`() = runTest {
        medications.put(twiceADay(stock = "9"))

        refill(Fixtures.MEDICATION_ID, BigDecimal("60"))

        val record = log.records.single()
        assertEquals(OpEvent.STOCK_REFILLED, record.event)
        assertEquals("9", record.before)
        assertEquals("60", record.after)
        assertEquals(Fixtures.PROFILE_ID, record.profileId)
        assertNull(record.detail)
        // Названий лекарств и дозировок в журнале нет — только идентификаторы (НФТ-19).
        val fields = listOf(record.profileId, record.medicationId, record.intakeId, record.before, record.after)
        assertTrue(fields.none { it?.contains(Fixtures.scheduledMedication().name) == true }, "fields: $fields")
    }

    @Test
    fun `switching stock tracking on from an untracked medication is a refill too`() = runTest {
        medications.put(twiceADay(stock = "9").copy(stockLeft = null))

        val outcome = refill(Fixtures.MEDICATION_ID, BigDecimal("60"))

        assertEquals(RefillOutcome.Applied(null, BigDecimal("60")), outcome)
    }

    private fun twiceADay(stock: String): Medication = Fixtures.scheduledMedication().copy(
        doseAmount = BigDecimal.ONE,
        times = listOf(Fixtures.MORNING, EVENING),
        stockLeft = BigDecimal(stock),
        stockThresholdDays = FIVE_DAYS,
    )

    private companion object {
        val EVENING: LocalTime = LocalTime.of(20, 0)
        const val FIVE_DAYS = 5
    }
}
