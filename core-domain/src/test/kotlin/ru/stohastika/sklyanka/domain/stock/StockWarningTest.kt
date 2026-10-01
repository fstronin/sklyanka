package ru.stohastika.sklyanka.domain.stock

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import ru.stohastika.sklyanka.domain.model.ArchiveReason
import ru.stohastika.sklyanka.domain.model.Fixtures
import ru.stohastika.sklyanka.domain.model.Medication
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalTime

/**
 * Правило предупреждения о покупке (ПР-13) и выбор самого срочного из нескольких.
 *
 * Таблица `Примеры` структуры сценария «Когда появляется предупреждение о покупке»
 * из `docs/tasks/SKL-12/acceptance.feature` целиком.
 */
class StockWarningTest {

    @ParameterizedTest(name = "stock {0} against a threshold of {1} days warns: {2}")
    @CsvSource(
        "30, 5,  false",
        "12, 5,  false",
        "10, 5,  false",
        "9,  5,  true",
        "2,  5,  true",
        "0,  5,  true",
        "30, 14, false",
        "26, 14, true",
    )
    fun `the threshold is compared strictly`(stock: String, threshold: Int, expected: Boolean) {
        val warning = StockWarning.of(twiceADay(stock = stock, threshold = threshold))

        assertEquals(expected, warning != null, "stock $stock, threshold $threshold")
    }

    @Test
    fun `exactly enough for the threshold is not yet a warning`() {
        // Ровно пять дней при пороге пять — ещё не повод предупреждать (ПР-13).
        assertNull(StockWarning.of(twiceADay(stock = "10", threshold = FIVE_DAYS)))
        assertNotNull(StockWarning.of(twiceADay(stock = "9", threshold = FIVE_DAYS)))
    }

    @Test
    fun `the default threshold of seven days warns strictly below it`() {
        // Таблица примеров «Граница порога» SKL-74: запас 6 дней при пороге 7
        // предупреждает, ровно 7 и 8 — нет (ПР-13).
        assertNotNull(StockWarning.of(twiceADay(stock = "12", threshold = StockThresholds.DEFAULT_DAYS)))
        assertNull(StockWarning.of(twiceADay(stock = "14", threshold = StockThresholds.DEFAULT_DAYS)))
        assertNull(StockWarning.of(twiceADay(stock = "16", threshold = StockThresholds.DEFAULT_DAYS)))
    }

    @Test
    fun `an empty package always warns`() {
        val warning = StockWarning.of(twiceADay(stock = "0", threshold = FIVE_DAYS))

        assertEquals(0, warning?.daysLeft)
        assertEquals(BigDecimal.ZERO, warning?.unitsLeft)
    }

    @Test
    fun `a medication without stock tracking never warns`() {
        val medication = twiceADay(stock = "0", threshold = FIVE_DAYS).copy(stockLeft = null)

        assertNull(StockWarning.of(medication))
    }

    @Test
    fun `stock tracking without a threshold never warns`() {
        val medication = twiceADay(stock = "1", threshold = FIVE_DAYS).copy(stockThresholdDays = null)

        assertNull(StockWarning.of(medication))
    }

    @Test
    fun `an on-demand medication never warns until the open question is closed`() {
        // ОВ-39: число дней для приёма по потребности не определено, а предупреждение
        // без числа дней ничего человеку не сообщает.
        val medication = Fixtures.prnMedication().copy(stockLeft = BigDecimal("2"), stockThresholdDays = FIVE_DAYS)

        assertNull(StockWarning.of(medication))
    }

    @Test
    fun `an archived course never warns`() {
        val medication = twiceADay(stock = "0", threshold = FIVE_DAYS).copy(
            archivedAt = Instant.EPOCH,
            archiveReason = ArchiveReason.COURSE_FINISHED,
        )

        assertNull(StockWarning.of(medication))
    }

    @Test
    fun `the warning carries the numbers the plate shows`() {
        val warning = StockWarning.of(twiceADay(stock = "9", threshold = FIVE_DAYS))

        assertEquals(BigDecimal("9"), warning?.unitsLeft)
        assertEquals(FOUR_DAYS, warning?.daysLeft)
        assertEquals(FIVE_DAYS, warning?.thresholdDays)
        assertEquals(Fixtures.MEDICATION_ID, warning?.medicationId)
    }

    @Test
    fun `the most urgent of several warnings is the one with fewer days`() {
        val magnesium = twiceADay(stock = "9", threshold = FIVE_DAYS)
        val calcium = twiceADay(stock = "2", threshold = FIVE_DAYS).copy(
            id = "calcium",
            name = "calcium",
            times = listOf(Fixtures.MORNING),
        )

        val warning = StockWarning.mostUrgent(listOf(magnesium, calcium))

        assertEquals("calcium", warning?.medicationId)
        assertEquals(2, warning?.daysLeft)
    }

    @Test
    fun `equal urgency is resolved the same way every time`() {
        // Без устойчивого порядка два одинаково срочных лекарства менялись бы местами
        // от запроса к запросу, и плашка мигала бы между ними.
        val first = twiceADay(stock = "2", threshold = FIVE_DAYS).copy(id = "b", name = "b")
        val second = twiceADay(stock = "2", threshold = FIVE_DAYS).copy(id = "a", name = "a")

        assertEquals("a", StockWarning.mostUrgent(listOf(first, second))?.medicationId)
        assertEquals("a", StockWarning.mostUrgent(listOf(second, first))?.medicationId)
    }

    @Test
    fun `nothing to warn about gives no warning at all`() {
        assertNull(StockWarning.mostUrgent(listOf(twiceADay(stock = "30", threshold = FIVE_DAYS))))
        assertNull(StockWarning.mostUrgent(emptyList()))
    }

    private fun twiceADay(stock: String, threshold: Int): Medication = Fixtures.scheduledMedication().copy(
        doseAmount = BigDecimal.ONE,
        times = listOf(Fixtures.MORNING, EVENING),
        stockLeft = BigDecimal(stock),
        stockThresholdDays = threshold,
    )

    private companion object {
        val EVENING: LocalTime = LocalTime.of(20, 0)
        const val FIVE_DAYS = 5
        const val FOUR_DAYS = 4
    }
}
