package ru.stohastika.sklyanka.domain.stock

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.stohastika.sklyanka.domain.model.Fixtures
import ru.stohastika.sklyanka.domain.model.Medication
import java.math.BigDecimal
import java.time.LocalTime

/**
 * Блок «Остаток» в карточке лекарства: что именно экран получает от домена.
 *
 * Сценарии группы `@ui` из `docs/tasks/SKL-12/acceptance.feature` в части чисел
 * и признаков; тексты и склонения — ресурсы SKL-08 и SKL-10.
 */
class StockPanelTest {

    @Test
    fun `a medication without stock tracking has no block at all`() {
        val medication = Fixtures.scheduledMedication().copy(stockLeft = null)

        assertEquals(StockPanel.Hidden, StockPanel.of(medication))
    }

    @Test
    fun `a fired threshold shows the value and the caption`() {
        val panel = StockPanel.of(twiceADay(stock = "9", threshold = FIVE_DAYS))

        assertEquals(StockPanel.Visible(BigDecimal("9"), FOUR_DAYS, warning = true), panel)
        assertEquals(FOUR_DAYS, (panel as StockPanel.Visible).captionDays)
    }

    @Test
    fun `a threshold that has not fired shows the value without the caption`() {
        val panel = StockPanel.of(twiceADay(stock = "30", threshold = FIVE_DAYS)) as StockPanel.Visible

        assertEquals(BigDecimal("30"), panel.unitsLeft)
        assertTrue(!panel.warning, "fifteen days against a threshold of five is not a warning")
        assertNull(panel.captionDays, "the caption belongs to the warning, not to the block")
    }

    @Test
    fun `an on-demand medication shows the stock without promising days`() {
        // ОВ-39: остаток виден и списывается, число дней не обещается.
        val medication = Fixtures.prnMedication().copy(
            stockLeft = BigDecimal("2"),
            stockThresholdDays = FIVE_DAYS,
        )

        val panel = StockPanel.of(medication) as StockPanel.Visible

        assertEquals(BigDecimal("2"), panel.unitsLeft)
        assertNull(panel.daysLeft)
        assertTrue(!panel.warning)
        assertNull(panel.captionDays)
    }

    @Test
    fun `stock tracking without a threshold keeps the block and drops the warning`() {
        val medication = twiceADay(stock = "1", threshold = FIVE_DAYS).copy(stockThresholdDays = null)

        val panel = StockPanel.of(medication) as StockPanel.Visible

        assertEquals(BigDecimal.ONE, panel.unitsLeft)
        assertTrue(!panel.warning)
    }

    // --- Доля от размера упаковки (ОВ-45) ---------------------------------------------------

    @Test
    fun `the share of the package is the stock over its size`() {
        val panel = StockPanel.of(
            twiceADay(stock = "9", threshold = FIVE_DAYS).copy(packageSize = BigDecimal("60")),
        ) as StockPanel.Visible

        assertEquals(BigDecimal("60"), panel.packageSize)
        assertEquals(0.15f, panel.packageFraction!!, FRACTION_TOLERANCE)
    }

    @Test
    fun `an unknown package size means no share and the plain stock`() {
        val panel = StockPanel.of(twiceADay(stock = "9", threshold = FIVE_DAYS)) as StockPanel.Visible

        assertNull(panel.packageSize)
        assertNull(panel.packageFraction)
    }

    @Test
    fun `stock above the package size has no share`() {
        // Две упаковки — законный запас; доля больше целого не рисуется.
        val panel = StockPanel.of(
            twiceADay(stock = "90", threshold = FIVE_DAYS).copy(packageSize = BigDecimal("60")),
        ) as StockPanel.Visible

        assertNull(panel.packageFraction)
    }

    @Test
    fun `a package size of zero or below has no share and does not divide by zero`() {
        listOf("0", "-1").forEach { size ->
            val panel = StockPanel.of(
                twiceADay(stock = "9", threshold = FIVE_DAYS).copy(packageSize = BigDecimal(size)),
            ) as StockPanel.Visible

            assertNull(panel.packageFraction, "size $size must not produce a fraction")
        }
    }

    @Test
    fun `an empty package has a zero share and still draws the bar`() {
        val panel = StockPanel.of(
            twiceADay(stock = "0", threshold = FIVE_DAYS).copy(packageSize = BigDecimal("60")),
        ) as StockPanel.Visible

        assertEquals(0f, panel.packageFraction)
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

        /** Float-доля рисуется, а не считается: 0,15 при делении float может не совпасть бит в бит. */
        const val FRACTION_TOLERANCE = 0.0001f
    }
}
